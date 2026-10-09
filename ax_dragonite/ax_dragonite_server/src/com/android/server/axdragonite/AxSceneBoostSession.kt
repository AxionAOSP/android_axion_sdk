/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.axdragonite

import android.os.Bundle
import android.os.SystemClock
import com.android.internal.dragonite.AxDragoniteConstants
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList

open class AxSceneBoostSession(
    protected val mHost: AxDragoniteImpl,
    var sessionType: Int = 0,
    var handle: Int = 0,
    var cmd: AxSceneTable.ScenarioCommand? = null,
    var boostAction: Runnable? = null,
    var restoreAction: Runnable? = null,
) {
    var startTime: Long = 0L
    var endTime: Long = 0L

    protected fun readyCommand(action: String): AxSceneTable.ScenarioCommand? {
        val command = cmd ?: return null
        mHost.logDebug(AxDragoniteImpl.TAG, "$action ${command.command?.name}")
        if (!mHost.hasBoostRecord(handle)) {
            mHost.logDebug(AxDragoniteImpl.TAG, "can't get $handle ActiveSession, return!")
            return null
        }
        return command
    }

    protected fun scheduleActions(command: AxSceneTable.ScenarioCommand) {
        boostAction?.let { mHost.mDragoniteHandler.post(it) }
        startTime = SystemClock.elapsedRealtime()
        val restore = restoreAction
        if (restore != null && command.holdTime > 0) {
            mHost.mDragoniteHandler.postDelayed(restore, command.holdTime)
            endTime = startTime + command.holdTime
        }
        mHost.attachSession(handle, this)
    }

    open fun execute() {
        scheduleActions(readyCommand("execute") ?: return)
    }

    open fun restore() {
        readyCommand("restore") ?: return
        val restore = restoreAction ?: return
        mHost.mDragoniteHandler.removeCallbacks(restore)
        mHost.mDragoniteHandler.post(restore)
    }

    override fun toString(): String = "cmd id: ${cmd?.command?.id}, cmd name: ${cmd?.command?.name}"
}

internal class AxNameThreadAffinitySession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 12, handle, cmd, runnable, runnable2)

internal class AnimationBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 4, handle, cmd, runnable, runnable2)

internal class BackgroundFreezeBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
) : AxSceneBoostSession(host) {
    companion object {
        private const val DEFAULT_BG_FREEZE_HOLD_TIME = 600L
        private const val MAX_BG_FREEZE_HOLD_TIME = 1000L
    }

    init {
        this.handle = handle
        this.cmd = cmd
        if (cmd.holdTime < 0 || cmd.holdTime > MAX_BG_FREEZE_HOLD_TIME) {
            cmd.setHoldTime(DEFAULT_BG_FREEZE_HOLD_TIME)
        }
        sessionType = 6
    }

    override fun execute() {
        val command = readyCommand("execute") ?: return
        val frozenApps = mHost.frozenAppList
        boostAction = Runnable { mHost.setFrozenForAll(frozenApps, true) }
        restoreAction = Runnable { mHost.setFrozenForAll(frozenApps, false) }
        scheduleActions(command)
    }

    override fun restore() {
        readyCommand("restore") ?: return
    }
}

internal class PlatformResourceBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    resource: PlatformResource,
    handler: PlatformHandler,
) : AxSceneBoostSession(host, 1, handle, cmd, resource, handler)

internal class ProcessAffinityBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 3, handle, cmd, runnable, runnable2)

internal class SfBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 11, handle, cmd, runnable, runnable2)

internal class CpuctlBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 8, handle, cmd, runnable, runnable2)

internal class CpusetBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 7, handle, cmd, runnable, runnable2)

internal class ThreadAffinityBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 2, handle, cmd, runnable, runnable2)

internal class CpusetTaskBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 9, handle, cmd, runnable, runnable2)

internal class ThreadBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable?,
) : AxSceneBoostSession(host, 5, handle, cmd, runnable, runnable2) {
    override fun restore() {
        readyCommand("restore") ?: return
        val restore = restoreAction
        if (restore != null) {
            mHost.mDragoniteHandler.removeCallbacks(restore)
            mHost.mDragoniteHandler.post(restore)
            return
        }
        mHost.logDebug(AxDragoniteImpl.TAG, "restore skipped, restore tag disabled")
    }
}

internal class KswapdBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
    runnable: Runnable,
    runnable2: Runnable,
) : AxSceneBoostSession(host, 10, handle, cmd, runnable, runnable2)

internal class SceneBoostRunnable(
    private val mHost: AxDragoniteImpl,
    val mSceneId: Int,
    val mBundle: Bundle?,
) : Runnable {
    override fun run() {
        mHost.dispatchSceneCommands(mSceneId, mBundle)
    }
}

internal class SceneReleaseRunnable(private val mHost: AxDragoniteImpl) : Runnable {
    override fun run() {
        mHost.checkSessionStatus()
    }
}

internal class InputBoostResetRunnable(private val mHost: AxDragoniteImpl) : Runnable {
    override fun run() {
        mHost.sfBindControllInternal(false)
        mHost.adjustBackgroundLimit(false)
        mHost.adjustKswapdAffinity(false)
        mHost.mLimitBoostDisabled = true
    }
}

internal class SceneRelease(private val mHost: AxDragoniteImpl, val mHandle: Int) : Runnable {
    override fun run() {
        mHost.releaseScene(mHandle)
    }
}

internal class ActiveSession(
    val host: AxDragoniteImpl,
    val handle: Int,
    val sceneId: Int,
    bundle: Bundle?,
) {
    private var mBundle: Bundle? = bundle?.deepCopy()
    private val mTasks = ArrayList<AxSceneBoostSession>()

    fun addTask(task: AxSceneBoostSession) {
        mTasks.add(task)
    }

    fun getBundle(): Bundle? = mBundle

    fun restoreAll() {
        for (session in mTasks) {
            session.restore()
        }
    }

    override fun toString(): String =
        "mHandle = $handle, mSceneId = $sceneId, mData = $mBundle, mTasks = $mTasks"
}

internal class SessionRecords(private val mHost: AxDragoniteImpl) {
    companion object {
        private const val MAX_HANDLES = 100
        private const val INVALID_HANDLE = -1
    }

    private val mAllocatedHandles = ArrayList<Int>()
    private val mAvailableHandles = ArrayList<Int>()
    private val mActiveRecords = CopyOnWriteArrayList<ActiveSession>()

    init {
        for (i in 0 until MAX_HANDLES) {
            mAvailableHandles.add(i)
        }
    }

    private fun allocateHandle(): Int {
        if (mAvailableHandles.isEmpty()) {
            mHost.logDebug(AxDragoniteImpl.TAG, "warning: handle leak!")
            return INVALID_HANDLE
        }
        val handle = mAvailableHandles.removeAt(0)
        mAllocatedHandles.add(handle)
        return handle
    }

    private fun findRecord(handle: Int): ActiveSession? =
        mActiveRecords.firstOrNull { it.handle == handle }

    @Synchronized
    fun getActiveRecords(): CopyOnWriteArrayList<ActiveSession> =
        CopyOnWriteArrayList(mActiveRecords)

    private fun lastRecord(): ActiveSession? = mActiveRecords.lastOrNull()

    val lastActiveHandle: Int
        @Synchronized get() = lastRecord()?.handle ?: INVALID_HANDLE

    val lastActiveRecord: ActiveSession?
        @Synchronized get() = lastRecord()

    @Synchronized fun isHandleAllocated(handle: Int): Boolean = mAllocatedHandles.contains(handle)

    @Synchronized
    fun acquireRecord(sceneId: Int, bundle: Bundle?): Int {
        val handle = allocateHandle()
        if (handle == INVALID_HANDLE) {
            mHost.logDebug(AxDragoniteImpl.TAG, "can't get handle, return!")
            return handle
        }
        bundle?.putInt(AxDragoniteConstants.KEY_HANDLE, handle)
        mActiveRecords.add(ActiveSession(mHost, handle, sceneId, bundle))
        return handle
    }

    @Synchronized
    fun releaseRecord(handle: Int) {
        if (handle < 0 || handle >= MAX_HANDLES) {
            mHost.logDebug(AxDragoniteImpl.TAG, "invalid handle: $handle, ignore!")
            return
        }
        val index = mAllocatedHandles.indexOf(handle)
        if (index < 0) {
            mHost.logDebug(AxDragoniteImpl.TAG, "handle $handle has been removed!")
            return
        }
        mAllocatedHandles.removeAt(index)
        mAvailableHandles.add(handle)
        findRecord(handle)?.let(mActiveRecords::remove)
    }

    @Synchronized fun getRecord(handle: Int): ActiveSession? = findRecord(handle)

    @Synchronized
    fun addSession(handle: Int, session: AxSceneBoostSession) {
        findRecord(handle)?.addTask(session)
            ?: mHost.logDebug(AxDragoniteImpl.TAG, "can't get ActiveSession: $handle")
    }

    @Synchronized
    fun bringToFront(handle: Int) {
        val found = findRecord(handle) ?: return
        mActiveRecords.remove(found)
        mActiveRecords.add(found)
    }
}
