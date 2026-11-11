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
import android.util.Slog
import com.android.internal.dragonite.AxDragoniteConstants
import com.android.internal.os.BackgroundThread
import java.util.ArrayList
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max

open class AxSceneBoostSession(
    protected val mHost: AxDragoniteImpl,
    val sessionType: Int,
    val handle: Int,
    val cmd: AxSceneTable.ScenarioCommand,
    var boostAction: Runnable? = null,
    var restoreAction: Runnable? = null,
) {
    companion object {
        const val TYPE_PLATFORM = 1
        const val TYPE_THREAD_AFFINITY = 2
        const val TYPE_PROCESS_AFFINITY = 3
        const val TYPE_ANIMATION = 4
        const val TYPE_THREAD_BOOST = 5
        const val TYPE_BACKGROUND_FREEZE = 6
        const val TYPE_CPUSET = 7
        const val TYPE_CPUCTL = 8
        const val TYPE_CPUSET_TASK = 9
        const val TYPE_KSWAPD = 10
        const val TYPE_SF = 11
        const val TYPE_NAMED_THREAD_AFFINITY = 12
    }

    var startTime: Long = 0L
    var endTime: Long = 0L

    protected fun hasRecord(action: String): Boolean {
        mHost.logDebug(AxDragoniteImpl.TAG, "$action ${cmd.command?.name}")
        if (mHost.hasBoostRecord(handle)) return true
        mHost.logDebug(AxDragoniteImpl.TAG, "can't get $handle ActiveSession, return!")
        return false
    }

    protected fun scheduleActions(holdTime: Long = cmd.holdTime) {
        val delay = max(0L, cmd.delayTime)
        val boost = boostAction
        if (boost != null) {
            if (delay > 0) mHost.mDragoniteHandler.postDelayed(boost, delay)
            else mHost.mDragoniteHandler.post(boost)
        }
        startTime = SystemClock.elapsedRealtime() + delay
        val restore = restoreAction
        if (restore != null && holdTime > 0) {
            mHost.mDragoniteHandler.postDelayed(restore, holdTime + delay)
            endTime = startTime + holdTime
        }
        mHost.attachSession(handle, this)
    }

    open fun execute() {
        if (hasRecord("execute")) scheduleActions()
    }

    fun restore() {
        mHost.logDebug(AxDragoniteImpl.TAG, "restore ${cmd.command?.name}")
        boostAction?.let { mHost.mDragoniteHandler.removeCallbacks(it) }
        val restore = restoreAction ?: return
        mHost.mDragoniteHandler.removeCallbacks(restore)
        mHost.mDragoniteHandler.post(restore)
    }

    override fun toString(): String = "cmd id: ${cmd.command?.id}, cmd name: ${cmd.command?.name}"
}

internal class BackgroundFreezeBoostSession(
    host: AxDragoniteImpl,
    handle: Int,
    cmd: AxSceneTable.ScenarioCommand,
) : AxSceneBoostSession(host, AxSceneBoostSession.TYPE_BACKGROUND_FREEZE, handle, cmd) {
    private var frozenApps: ArrayList<AxProcessInfo>? = null

    companion object {
        private const val DEFAULT_BG_FREEZE_HOLD_TIME = 600L
        private const val MAX_BG_FREEZE_HOLD_TIME = 1000L
    }

    override fun execute() {
        if (!hasRecord("execute")) return
        boostAction = Runnable {
            frozenApps = mHost.frozenAppList
            mHost.setFrozenForAll(frozenApps, true)
        }
        restoreAction = Runnable {
            mHost.setFrozenForAll(frozenApps, false)
            frozenApps = null
        }
        scheduleActions(
            cmd.holdTime.takeIf { it in 0..MAX_BG_FREEZE_HOLD_TIME } ?: DEFAULT_BG_FREEZE_HOLD_TIME
        )
    }
}

internal class SceneReleaseRunnable(private val mHost: AxDragoniteImpl) : Runnable {
    companion object {
        const val CHECK_INTERVAL_MS = 30000L
    }

    override fun run() {
        try {
            mHost.checkSessionStatus()
        } catch (e: Exception) {
            Slog.e(AxDragoniteImpl.TAG, "Error checking session status", e)
        }
        BackgroundThread.getHandler().postDelayed(this, CHECK_INTERVAL_MS)
    }
}

internal class InputBoostResetRunnable(private val mHost: AxDragoniteImpl) : Runnable {
    override fun run() {
        mHost.sfBindControlInternal(false)
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
    val handle: Int,
    val sceneId: Int,
    bundle: Bundle?,
) {
    @Volatile
    private var mBundle: Bundle? =
        bundle?.deepCopy()?.apply { putInt(AxDragoniteConstants.KEY_HANDLE, handle) }
    private val mTasks = ArrayList<AxSceneBoostSession>()
    val createTime: Long = SystemClock.elapsedRealtime()

    fun addTask(task: AxSceneBoostSession) {
        synchronized(mTasks) { mTasks.add(task) }
    }

    @Synchronized fun getBundle(): Bundle? = mBundle?.deepCopy()

    @Synchronized
    fun setResolvedPid(pid: Int) {
        val copy = mBundle?.deepCopy() ?: Bundle()
        copy.putInt(AxDragoniteConstants.KEY_PID, pid)
        copy.putInt(AxDragoniteConstants.KEY_HANDLE, handle)
        mBundle = copy
    }

    @Synchronized
    fun updateBundle(bundle: Bundle?) {
        mBundle = bundle?.deepCopy()?.apply { putInt(AxDragoniteConstants.KEY_HANDLE, handle) }
    }

    fun getTasks(): List<AxSceneBoostSession> = synchronized(mTasks) { ArrayList(mTasks) }

    fun hasActiveTasks(): Boolean = synchronized(mTasks) { mTasks.isNotEmpty() }

    fun restoreAll() {
        val tasksToRestore = synchronized(mTasks) { ArrayList(mTasks).also { mTasks.clear() } }
        val now = SystemClock.elapsedRealtime()
        for (session in tasksToRestore) {
            val endTime = session.endTime
            if (endTime <= 0L || endTime > now) {
                session.restore()
            }
        }
    }

    override fun toString(): String =
        "mHandle = $handle, mSceneId = $sceneId, mData = $mBundle, mTasks = $mTasks"
}

internal class SessionRecords(private val mHost: AxDragoniteImpl) {
    companion object {
        const val MAX_HANDLES = 100
        private const val INVALID_HANDLE = -1
    }

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
        return mAvailableHandles.removeAt(0)
    }

    private fun findRecord(handle: Int): ActiveSession? =
        mActiveRecords.firstOrNull { it.handle == handle }

    @Synchronized
    fun getActiveRecords(): CopyOnWriteArrayList<ActiveSession> =
        CopyOnWriteArrayList(mActiveRecords)

    val lastActiveRecord: ActiveSession?
        @Synchronized get() = mActiveRecords.lastOrNull()

    @Synchronized
    fun getAvailableHandlesCount(): Int = mAvailableHandles.size

    @Synchronized
    fun resetAll() {
        mActiveRecords.clear()
        mAvailableHandles.clear()
        for (i in 0 until MAX_HANDLES) {
            mAvailableHandles.add(i)
        }
    }

    @Synchronized
    fun acquireRecord(sceneId: Int, bundle: Bundle?): Int {
        val handle = allocateHandle()
        if (handle == INVALID_HANDLE) {
            mHost.logDebug(AxDragoniteImpl.TAG, "can't get handle, return!")
            return handle
        }
        bundle?.putInt(AxDragoniteConstants.KEY_HANDLE, handle)
        mActiveRecords.add(ActiveSession(handle, sceneId, bundle))
        return handle
    }

    @Synchronized
    fun releaseRecord(handle: Int) {
        if (handle < 0 || handle >= MAX_HANDLES) {
            mHost.logDebug(AxDragoniteImpl.TAG, "invalid handle: $handle, ignore!")
            return
        }
        val record = findRecord(handle)
        if (record == null) {
            mHost.logDebug(AxDragoniteImpl.TAG, "handle $handle has been removed!")
            return
        }
        mActiveRecords.remove(record)
        mAvailableHandles.add(handle)
    }

    @Synchronized fun getRecord(handle: Int): ActiveSession? = findRecord(handle)

    fun addSession(handle: Int, session: AxSceneBoostSession) {
        val record = getRecord(handle)
        if (record != null) {
            record.addTask(session)
        } else {
            mHost.logDebug(AxDragoniteImpl.TAG, "can't get ActiveSession: $handle")
            session.restore()
        }
    }

    @Synchronized
    fun bringToFront(handle: Int) {
        val found = findRecord(handle) ?: return
        mActiveRecords.remove(found)
        mActiveRecords.add(found)
    }
}
