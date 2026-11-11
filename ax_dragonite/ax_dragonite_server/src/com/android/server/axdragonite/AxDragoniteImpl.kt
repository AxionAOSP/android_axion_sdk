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
import android.os.FileUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.os.ServiceManager
import android.os.SystemClock
import android.os.SystemProperties
import android.os.Trace
import android.util.Slog
import com.android.internal.dragonite.AxDragoniteConstants
import com.android.internal.os.BackgroundThread
import com.android.server.axdragonite.AxCmdTable.Command
import com.android.server.axdragonite.AxCmdTable.Type
import com.android.server.am.ProcessList
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.PrintWriter
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private typealias SceneHandler =
    (
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) -> Unit

class AxDragoniteImpl : IAxDragonite {
    companion object {
        const val TAG = "AxDragonite"

        private const val SF_SERVICE = "SurfaceFlinger"
        private const val ISURFACE_COMPOSER = "android.ui.ISurfaceComposer"
        private const val PROC_DIR = "/proc"
        private const val MAX_PID_QUERY = 1024
        private const val HANDLE_MESSAGE_OFFSET = 100
        private const val MISSING_DURATION = -1
        private const val TRACE_COOKIE = 64L
        private const val WORKER_THREAD_PRIORITY = -2
        private const val DRAGONITE_THREAD_NAME = "DragoniteThread"
        private const val DRAGONITE_MANAGER_THREAD_NAME = "DragoniteManagerThread"
        private const val TRACE_STATE_FROZEN = "frozen"
        private const val TRACE_STATE_THAWED = "unfrozen"
        private const val TRACE_PROCESSES_SUFFIX = "Processes"
        private const val RESTORE_DISABLED_FLAG = "false"
        private const val KSWAPD_COMM = "kswapd"
        private const val RESTORE_SCHED_POLICY = 0
        private const val RESTORE_SCHED_PRIORITY = 0
        private const val RESTORE_THREAD_PRIORITY = -10
        private const val INVALID_TID = -1
        private const val THREAD_RENDER = "RenderThread"
        private const val THREAD_HWUI_PREFIX = "hwui"
        private const val MAX_TASK_QUERY = 256
        private const val TASK_DIR_SUFFIX = "/task"
        private const val BACKGROUND_LIMIT_ENABLED = 1
        private const val BACKGROUND_LIMIT_DISABLED = 0
        private const val BOOST_MODE_ON = true
        private const val BOOST_MODE_OFF = false
        private const val RESTORE_HWUI_THREAD_PRIORITY = -2
        private const val BIND_ENABLED = 1
        private const val BIND_DISABLED = 0

        private const val CPUSET_CGROUP_PROCS_PATH =
            "${AxCmdTable.CPUSET_DIR}${AxCmdTable.PATH_SEP}${AxCmdTable.FILE_CGROUP_PROCS}"
        private const val CPUSET_TASKS_PATH =
            "${AxCmdTable.CPUSET_DIR}${AxCmdTable.PATH_SEP}${AxCmdTable.FILE_TASKS}"
        private const val CPUCTL_CGROUP_PROCS_PATH =
            "${AxCmdTable.CPUCTL_DIR}${AxCmdTable.PATH_SEP}${AxCmdTable.FILE_CGROUP_PROCS}"

        private const val GROUP_AX_FOREGROUND = "ax_foreground"

        private fun cpusetPath(group: String, file: String): String =
            "${AxCmdTable.CPUSET_DIR}${AxCmdTable.PATH_SEP}$group${AxCmdTable.PATH_SEP}$file"

        private val CPUSET_AX_FOREGROUND_CPUS =
            cpusetPath(GROUP_AX_FOREGROUND, AxCmdTable.FILE_CPUS)
        private val CPUSET_DEX2OAT_CPUS = cpusetPath(AxCmdTable.GROUP_DEX2OAT, AxCmdTable.FILE_CPUS)
        private val CPUSET_BACKGROUND_CPUS =
            cpusetPath(AxCmdTable.GROUP_BACKGROUND, AxCmdTable.FILE_CPUS)
        private val CPUSET_RESTRICTED_CPUS =
            cpusetPath(AxCmdTable.GROUP_RESTRICTED, AxCmdTable.FILE_CPUS)
        private const val PROP_DEX2OAT_THREADS = "dalvik.vm.dex2oat-threads"

        private const val SF_AFFINITY_BINDER_CODE = 2007
        private const val DEBUG_PROP = "persist.dragonite.debug"
        private const val GRACE_PERIOD_MS = 5000L
        private const val BOOT_RESTRICTED_CPUS_DELAY_MS = 5000L
        private const val COLD_START_TIMEOUT_MS = 10000L
        private const val BINDER_DUMP_TIMEOUT_SEC = 2L
    }

    private val debug get() = SystemProperties.getBoolean(DEBUG_PROP, false)
    private val processPidCache = ConcurrentHashMap<String, Int>()
    @Volatile private var currentActiveHandle = AxDragoniteConstants.INVALID_HANDLE
    @Volatile private var mBackgroundLoadLimit = BACKGROUND_LIMIT_ENABLED
    @Volatile var mLimitBoostDisabled = true

    private lateinit var mDragoniteThread: HandlerThread
    private lateinit var mManagerThread: HandlerThread
    lateinit var mManagerHandler: Handler
    lateinit var mDragoniteHandler: DragoniteHandler
    @Volatile private var mProcessList: ProcessList? = null

    private var mKswapdPids: IntArray? = null
    private var mKswapdAffinityBoost: IntArray? = null
    private var mDefaultKswapdAffinity: IntArray? = null

    val mDragoniteExt: AxDragoniteExt = AxDragoniteExt(this)
    val mPlatformConfig: AxPlatformConfig = AxPlatformConfig()
    private val mSessionRecords: SessionRecords = SessionRecords(this)
    private val mSceneReleaseRunnable: SceneReleaseRunnable = SceneReleaseRunnable(this)
    private val mInputBoostResetRunnable: InputBoostResetRunnable = InputBoostResetRunnable(this)

    private var sfBinder: IBinder? = null

    private val mCommandsMap = HashMap<Command, AxCommandExec>()
    private var mGameModeEnabled = false
    private var mGameModeHandle = AxDragoniteConstants.INVALID_HANDLE

    init {
        logDebug(TAG, "create AxDragonite")
        mKswapdAffinityBoost =
            AxCpuSets.resolveCores(mPlatformConfig.get(AxPlatformConfig.KEY_SWAP_AFFINITY_BOOST))
        mDefaultKswapdAffinity = AxCpuSets.resolveCores(AxCpuSets.CPUSET_ALL)
        loadCommands()
        initHandlerThreads()
    }

    fun onSystemReady() {
        Process.setThreadScheduler(
            mDragoniteThread.threadId,
            AxDragoniteConstants.BOOST_SCHED_POLICY,
            AxDragoniteConstants.BOOST_SCHED_PRIORITY,
        )
        Process.setThreadScheduler(
            mManagerThread.threadId,
            AxDragoniteConstants.BOOST_SCHED_POLICY,
            AxDragoniteConstants.BOOST_SCHED_PRIORITY,
        )
        mDragoniteExt.init()
        BackgroundThread.getHandler().removeCallbacks(mSceneReleaseRunnable)
        BackgroundThread.getHandler().post(mSceneReleaseRunnable)
        initKswapdPids()
    }

    fun onBootCompleted() {
        mManagerHandler.postDelayed(
            {
                writeCpusetCpus(CPUSET_RESTRICTED_CPUS, AxPlatformConfig.KEY_RESTRICTED)
            },
            BOOT_RESTRICTED_CPUS_DELAY_MS,
        )
    }

    private fun initHandlerThreads() {
        mDragoniteThread =
            HandlerThread(DRAGONITE_THREAD_NAME, WORKER_THREAD_PRIORITY).also { it.start() }
        mDragoniteHandler = DragoniteHandler(mDragoniteThread.looper)
        mManagerThread =
            HandlerThread(DRAGONITE_MANAGER_THREAD_NAME, WORKER_THREAD_PRIORITY).also { it.start() }
        mManagerHandler = Handler(mManagerThread.looper)
    }

    inner class DragoniteHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val handle = msg.what - HANDLE_MESSAGE_OFFSET
            logDebug(TAG, "handleMessage: what = ${msg.what}, handle = $handle")
            release(handle)
        }
    }

    private fun registerCommands(commands: List<Command>, handler: SceneHandler) {
        for (command in commands) {
            mCommandsMap[command] = AxCommandExec(handler)
        }
    }

    private fun loadCommands() {
        registerCommands(AxCmdTable.cmdOf(Type.CPUSET_PROCS), ::adjustCpusetProcs)
        registerCommands(AxCmdTable.cmdOf(Type.CPUSET_CPUS), ::adjustCpusetCpus)
        registerCommands(AxCmdTable.cmdOf(Type.CPUSET_TASKS), ::adjustCpusetTasks)
        registerCommands(AxCmdTable.cmdOf(Type.CPUCTL_PROCS), ::adjustCpuctlProcs)
        registerCommands(AxCmdTable.cmdOf(Type.CPUCTL_SHARES), ::adjustCpuctlShares)
        registerCommands(AxCmdTable.cmdOf(Type.CPUCTL_UCLAMP_MIN), ::adjustCpuctlUclamp)
        registerCommands(AxCmdTable.cmdOf(Type.CPUCTL_UCLAMP_MAX), ::adjustCpuctlUclamp)
        registerCommands(listOf(Command.CMD_THREAD_AFFINITY), ::threadAffinity)
        registerCommands(listOf(Command.CMD_PROCESS_AFFINITY), ::processAffinity)
        registerCommands(listOf(Command.CMD_AX_NAMED_THREAD_AFFINITY), ::setAxNameThreadAffinity)
        registerCommands(listOf(Command.CMD_SF_CONTROL_AFFINITY), ::sfBindCoreControl)
        registerCommands(listOf(Command.CMD_BACKGROUND_FREEZE), ::backgroundProcessFreeze)
        registerCommands(listOf(Command.CMD_KSWAPD_CONTROLL_AFFINITY), ::kswapdBindCoreControl)
        registerCommands(listOf(Command.CMD_ANIMATION_BOOST), ::animationBoost)
        registerCommands(listOf(Command.CMD_BACKGROUND_LOAD_LIMIT), ::backgroundLoadLimit)
        registerCommands(listOf(Command.CMD_PLATFORM_CONTROL), ::platformResourceControl)
        registerCommands(listOf(Command.CMD_THREAD_BOOST), ::threadBoost)
    }

    fun logDebug(tag: String, msg: String) {
        if (debug) Slog.d(tag, msg)
    }

    fun hasBoostRecord(handle: Int): Boolean = mSessionRecords.getRecord(handle) != null

    fun attachSession(handle: Int, session: AxSceneBoostSession) {
        mSessionRecords.addSession(handle, session)
    }

    private fun bundlePid(bundle: Bundle?): Int = bundle?.getInt(AxDragoniteConstants.KEY_PID) ?: 0

    private fun bundlePackage(bundle: Bundle?): String? =
        bundle?.getString(AxDragoniteConstants.KEY_PACKAGE_NAME)
            ?: bundle?.getString(AxDragoniteConstants.KEY_PACKAGE)

    fun dispatchSceneCommands(sceneId: Int, bundle: Bundle?) {
        val pid = bundlePid(bundle)
        val pkg = bundlePackage(bundle)
        val params = bundle?.getString(AxDragoniteConstants.KEY_PARAMS)
        if (bundle != null) {
            Slog.d(
                TAG,
                "sceneId = $sceneId, pid = $pid, packageName = $pkg, " +
                    "duration = ${bundle.getInt(AxDragoniteConstants.KEY_DURATION)}, " +
                    "params = $params",
            )
        }
        val scenario = AxSceneTable.getScenario(sceneId) ?: return
        val handle =
            bundle?.getInt(AxDragoniteConstants.KEY_HANDLE) ?: AxDragoniteConstants.INVALID_HANDLE
        for (scenarioCmd in scenario.commandList) {
            try {
                mCommandsMap[scenarioCmd.command]?.execute(handle, pid, pkg, scenarioCmd, params)
            } catch (e: Exception) {
                Slog.e(TAG, "dispatchSceneCommands failed for scene=$sceneId", e)
            }
        }
    }

    internal fun isSessionDead(
        session: ActiveSession,
        resolveMissingPid: Boolean = true,
    ): Boolean {
        val sessionBundle = session.getBundle()
        val pkg = bundlePackage(sessionBundle)
        val pid = bundlePid(sessionBundle)
        val duration =
            sessionBundle?.getInt(AxDragoniteConstants.KEY_DURATION, MISSING_DURATION)
                ?: MISSING_DURATION
        val ageMs = SystemClock.elapsedRealtime() - session.createTime

        if (pkg.isNullOrEmpty()) {
            return if (pid > 0) {
                val cmdline = AxPlatformConfig.getProcessCmdLine(pid)
                cmdline.isEmpty()
            } else {
                if (duration > 0) ageMs > (duration + GRACE_PERIOD_MS) else false
            }
        }

        if (pid > 0) {
            val cmdline = AxPlatformConfig.getProcessCmdLine(pid)
            return cmdline.isEmpty() || !cmdline.startsWith(pkg)
        } else {
            if (resolveMissingPid) {
                val currentPid = getPidByProcessName(pkg)
                if (currentPid > 0) {
                    session.setResolvedPid(currentPid)
                    return false
                }
            }
            val timeout = if (duration > 0) (duration + GRACE_PERIOD_MS) else COLD_START_TIMEOUT_MS
            return ageMs > timeout
        }
    }

    fun checkSessionStatus(): Int {
        val activeRecords = mSessionRecords.getActiveRecords()
        logDebug(TAG, "checkSessionStatus: records count = ${activeRecords.size}")
        if (activeRecords.isEmpty()) return 0
        var cleanedCount = 0
        for (session in activeRecords) {
            if (isSessionDead(session)) {
                val pkg = bundlePackage(session.getBundle())
                val pid = bundlePid(session.getBundle())
                Slog.w(TAG, "process = $pkg, pid = $pid died or cleared, release stuck handle ${session.handle}")
                release(session.handle)
                cleanedCount++
            }
        }
        return cleanedCount
    }

    private fun backgroundLoadLimit(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(TAG, "backgroundLoadLimit")
        mBackgroundLoadLimit = cmd.params1?.toIntOrNull() ?: 1
    }

    private fun platformResourceControl(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(TAG, "platformResourceControl")
        val resource = PlatformResource(this, cmd.params1)
        AxSceneBoostSession(
                this,
                AxSceneBoostSession.TYPE_PLATFORM,
                handle,
                cmd,
                resource,
                resource.release,
            )
            .execute()
    }

    fun setFrozenForAll(processes: ArrayList<AxProcessInfo>?, frozen: Boolean) {
        if (processes == null) {
            logDebug(TAG, "frozenlist is null!")
            return
        }
        val action = if (frozen) TRACE_STATE_FROZEN else TRACE_STATE_THAWED
        logDebug(TAG, "$action processes start")
        trace("$action$TRACE_PROCESSES_SUFFIX") {
            for (info in processes) setProcessFrozen(info.pid, info.uid, frozen)
        }
        logDebug(TAG, "$action processes end")
    }

    private fun processAffinity(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(
            TAG,
            "processAffinity: pid=$adjustPid pkg=$packageName params=$params",
        )
        trace("processAffinity") {
            val pid = targetPid(cmd, adjustPid, packageName)
            if (pid <= 0) return
            val renderTid = getRenderThreadTidByPid(pid)
            val bindcores = AxCpuSets.resolveCores(cmd.params1)
            val cores = bindcores?.contentToString()
            logDebug(TAG, "processAffinity: pid = $pid, renderTid = $renderTid, cores = $cores")
            AxSceneBoostSession(
                    this,
                    AxSceneBoostSession.TYPE_PROCESS_AFFINITY,
                    handle,
                    cmd,
                    ProcessAffinity(this, pid, renderTid, bindcores),
                    ProcessAffinity(this, pid, renderTid, null),
                )
                .execute()
        }
    }

    private fun setAxNameThreadAffinity(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        val pid = if (adjustPid > 0) adjustPid else (mProcessList?.axGetTopAppPid() ?: -1)
        logDebug(TAG, "setAxNameThreadAffinity: pid = $pid")
        AxSceneBoostSession(
                this,
                AxSceneBoostSession.TYPE_NAMED_THREAD_AFFINITY,
                handle,
                cmd,
                Runnable { AxNamedThreadAffinityFeature.applyNamedAffinityForPid(pid) },
                Runnable { AxNamedThreadAffinityFeature.resetAffinity() },
            )
            .execute()
    }

    private fun setProcessFrozen(pid: Int, uid: Int, frozen: Boolean) {
        logDebug(TAG, "setProcessFrozen: pid = $pid, uid = $uid, frozen = $frozen")
        try {
            Process.setProcessFrozen(pid, uid, frozen)
        } catch (e: Exception) {
            logDebug(TAG, "frozen: uid = $uid, pid = $pid, frozen = $frozen")
            logDebug(TAG, e.toString())
        }
    }

    private fun getSfBinder(): IBinder? {
        sfBinder?.let {
            return it
        }
        val binder = ServiceManager.getService(SF_SERVICE)
        if (binder == null) {
            Slog.e(TAG, "get sf service failed, can't set int array")
            return null
        }
        sfBinder = binder
        return binder
    }

    fun sfBindControlInternal(enabled: Boolean) {
        val binder = getSfBinder() ?: return
        val configuredAffinity = mPlatformConfig.get(AxPlatformConfig.KEY_SURFACE_AFFINITY)
        val bindEnable = if (enabled) BIND_ENABLED else BIND_DISABLED
        val bindAffinity =
            if (enabled) parseSfAffinity(configuredAffinity) else AxCpuSets.sfRestoreAffinity()
        val payload = intArrayOf(bindEnable, bindAffinity)
        val parcel = Parcel.obtain()
        parcel.writeInterfaceToken(ISURFACE_COMPOSER)
        for (value in payload) {
            parcel.writeInt(value)
        }
        try {
            binder.transact(SF_AFFINITY_BINDER_CODE, parcel, null, 0)
        } catch (e: RemoteException) {
            Slog.e(
                TAG,
                "SF transact $SF_AFFINITY_BINDER_CODE failed: ${payload.contentToString()}",
                e,
            )
        } finally {
            parcel.recycle()
        }
    }

    private fun sfBindCoreControl(
        handle: Int,
        callerPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(TAG, "sfBindCoreControl")
        AxSceneBoostSession(
                this,
                AxSceneBoostSession.TYPE_SF,
                handle,
                cmd,
                Runnable { sfBindControlInternal(true) },
                Runnable { sfBindControlInternal(false) },
            )
            .execute()
    }

    val frozenAppList: ArrayList<AxProcessInfo>?
        get() {
            logDebug(TAG, "get frozen app list and frozen start")
            val pl =
                mProcessList
                    ?: run {
                        logDebug(TAG, "mProcess is null, ignore!")
                        return null
                    }
            return pl.axGetFrozenProcesses(
                ProcessList.PERCEPTIBLE_LOW_APP_ADJ,
                ProcessList.HOME_APP_ADJ,
                ProcessList.CACHED_APP_MIN_ADJ,
                ProcessList.PREVIOUS_APP_ADJ,
            )
        }

    private fun listPids(dir: String, max: Int): List<Int> =
        Process.getPids(dir, IntArray(max))?.takeWhile { it > 0 } ?: emptyList()

    private fun processPids(): List<Int> = listPids(PROC_DIR, MAX_PID_QUERY)

    private fun threadTids(pid: Int): List<Int> =
        listPids("$PROC_DIR${AxCmdTable.PATH_SEP}$pid$TASK_DIR_SUFFIX", MAX_TASK_QUERY)

    private fun collectPidsByComm(comm: String): IntArray =
        processPids().filter { AxPlatformConfig.getProcessComm(it).startsWith(comm) }.toIntArray()

    private fun getKswapdPids(): IntArray = collectPidsByComm(KSWAPD_COMM)

    private fun userParamOrReturn(params: String?, cmd: AxSceneTable.ScenarioCommand): String? {
        return AxPlatformConfig.paramsFromUser(params, cmd.command)
            ?: run {
                logDebug(TAG, "need params, return!")
                null
            }
    }

    private fun threadAffinity(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        trace("threadAffinity") {
            val paramsFromUser = userParamOrReturn(params, cmd) ?: return
            val tid = paramsFromUser.split(AxCommandExec.COMMA)[0].toIntOrNull() ?: return
            val array = AxCpuSets.resolveCores(cmd.params1)
            logDebug(TAG, "threadAffinity: tid = $tid, bindcores = ${array?.contentToString()}")
            AxSceneBoostSession(
                    this,
                    AxSceneBoostSession.TYPE_THREAD_AFFINITY,
                    handle,
                    cmd,
                    ThreadAffinity(this, tid, array),
                    ThreadAffinity(this, tid, null),
                )
                .execute()
        }
    }

    private fun threadBoost(
        handle: Int,
        pid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        trace("threadBoost") {
            val paramsFromUser = userParamOrReturn(params, cmd) ?: return
            val tids =
                paramsFromUser
                    .split(AxCommandExec.COMMA)
                    .mapNotNull { it.toIntOrNull() }
                    .toIntArray()
            val isRestoreDisabled = RESTORE_DISABLED_FLAG.equals(cmd.restoreTag, ignoreCase = true)
            val restoreAction =
                if (!isRestoreDisabled) Runnable { threadBoostAll(tids, BOOST_MODE_OFF) } else null
            AxSceneBoostSession(
                    this,
                    AxSceneBoostSession.TYPE_THREAD_BOOST,
                    handle,
                    cmd,
                    Runnable { threadBoostAll(tids, BOOST_MODE_ON) },
                    restoreAction,
                )
                .execute()
        }
    }

    private fun writeNodeValue(
        handle: Int,
        cmd: AxSceneTable.ScenarioCommand,
        sessionType: Int,
        defaultRestore: (Command) -> String,
        resolve: (String) -> String = { it },
    ) {
        val path = cmd.command.pathFile ?: return
        val value = cmd.params1 ?: return
        logDebug(TAG, "path = $path, value = $value")
        val restore = cmd.restoreTag.ifEmpty { defaultRestore(cmd.command) }
        AxSceneBoostSession(
                this,
                sessionType,
                handle,
                cmd,
                FileWrite(this, path, resolve(value)),
                FileWrite(this, path, resolve(restore)),
            )
            .execute()
    }

    private fun writePidNode(
        handle: Int,
        cmd: AxSceneTable.ScenarioCommand,
        sessionType: Int,
        pid: Int,
        restoreFile: File,
    ) {
        val path = cmd.command.pathFile ?: return
        logDebug(TAG, "path = $path, pid = $pid")
        if (pid <= 0) return
        AxSceneBoostSession(
                this,
                sessionType,
                handle,
                cmd,
                FileWrite(this, path, pid.toString()),
                FileWrite(this, restoreFile, pid.toString()),
            )
            .execute()
    }

    private fun adjustCpuctlUclamp(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writeNodeValue(handle, cmd, AxSceneBoostSession.TYPE_CPUCTL, {
            AxPlatformConfig.getDefaultUclampRestoreValue(it)
        })
    }

    private fun boostThreadScheduler(tid: Int) {
        Process.setThreadScheduler(
            tid,
            AxDragoniteConstants.BOOST_SCHED_POLICY,
            AxDragoniteConstants.BOOST_SCHED_PRIORITY,
        )
    }

    private fun restoreThreadScheduler(tid: Int, priority: Int) {
        Process.setThreadScheduler(tid, RESTORE_SCHED_POLICY, RESTORE_SCHED_PRIORITY)
        Process.setThreadPriority(tid, priority)
    }

    private fun threadBoostAll(tids: IntArray, boost: Boolean) {
        for (tid in tids) threadBoostInternal(tid, boost)
    }

    private fun threadBoostInternal(tid: Int, boost: Boolean) {
        logDebug(TAG, "threadboostinternal: tid = $tid, boost = $boost")
        trace("thread_boost:${boost}_tid:$tid") {
            try {
                val threadPriority = Process.getThreadPriority(tid)
                if (boost) {
                    boostThreadScheduler(tid)
                } else {
                    restoreThreadScheduler(tid, threadPriority)
                }
            } catch (e: Exception) {
                logDebug(TAG, "thread boost pid: $tid, boost: $boost failed!")
            }
        }
    }

    internal inline fun <T> trace(name: String, block: () -> T): T {
        traceBegin(name)
        try {
            return block()
        } finally {
            traceEnd()
        }
    }

    fun traceBegin(traceName: String) {
        if (debug) Trace.traceBegin(TRACE_COOKIE, traceName)
    }

    private fun getProcessPids(): HashMap<String, Int> {
        val map = HashMap<String, Int>()
        for (pid in processPids()) {
            val cmdLine = AxPlatformConfig.getProcessCmdLine(pid)
            if (cmdLine.isNotEmpty()) map[cmdLine.substringAfterLast(AxCommandExec.SLASH)] = pid
        }
        return map
    }

    fun traceEnd() {
        if (debug) Trace.traceEnd(TRACE_COOKIE)
    }

    private fun findThreadTid(pid: Int, match: (String) -> Boolean): Int {
        if (pid <= 0) return INVALID_TID
        return threadTids(pid).firstOrNull { match(AxPlatformConfig.getProcessComm(it)) }
            ?: INVALID_TID
    }

    private fun getRenderThreadTidByPid(pid: Int): Int = findThreadTid(pid) { it == THREAD_RENDER }

    private fun getHwuiTaskTidsByPid(pid: Int): IntArray? {
        if (pid <= 0) return null
        return threadTids(pid)
            .filter { AxPlatformConfig.getProcessComm(it).contains(THREAD_HWUI_PREFIX) }
            .takeIf { it.isNotEmpty() }
            ?.toIntArray()
    }

    private fun adjustCpusetCpus(
        handle: Int,
        callerPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writeNodeValue(
            handle,
            cmd,
            AxSceneBoostSession.TYPE_CPUSET,
            { mPlatformConfig.getRestoreCpusetCpus(it) },
            { AxCpuSets.resolve(it) },
        )
    }

    private fun writeCpusetCpus(path: String, configKey: String) {
        val file = File(path)
        if (!file.exists()) return
        val resolved = AxCpuSets.resolve(mPlatformConfig.get(configKey))
        try {
            FileUtils.stringToFile(file, resolved)
        } catch (e: IOException) {
            logDebug(TAG, "writeCpusetCpus failed for $path -> $resolved: ${e.message}")
        }
    }

    private fun writeBackgroundCpus(
        foregroundKey: String,
        dexKey: String,
        backgroundKey: String,
        threadsKey: String,
    ) {
        writeCpusetCpus(CPUSET_AX_FOREGROUND_CPUS, foregroundKey)
        writeCpusetCpus(CPUSET_DEX2OAT_CPUS, dexKey)
        writeCpusetCpus(CPUSET_BACKGROUND_CPUS, backgroundKey)
        SystemProperties.set(PROP_DEX2OAT_THREADS, mPlatformConfig.get(threadsKey))
    }

    fun adjustBackgroundLimit(limit: Boolean) {
        if (!limit) {
            writeBackgroundCpus(
                AxPlatformConfig.KEY_AXFG,
                AxPlatformConfig.KEY_DEX,
                AxPlatformConfig.KEY_BACKGROUND,
                AxPlatformConfig.KEY_DEX_RESTORE_THREADS,
            )
            return
        }
        if (mBackgroundLoadLimit == BACKGROUND_LIMIT_DISABLED) {
            Slog.d(TAG, "disable BackgroundLimit!")
            return
        }
        writeBackgroundCpus(
            AxPlatformConfig.KEY_FOREGROUND_INPUT,
            AxPlatformConfig.KEY_DEX_INPUT,
            AxPlatformConfig.KEY_BACKGROUND_INPUT,
            AxPlatformConfig.KEY_DEX_THREADS,
        )
    }

    private fun adjustCpuctlShares(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writeNodeValue(handle, cmd, AxSceneBoostSession.TYPE_CPUCTL, {
            mPlatformConfig.getCpushares(it)
        })
    }

    private fun initKswapdPids() {
        mKswapdPids = getKswapdPids()
        logDebug(TAG, "initKswapdPids: pids = ${mKswapdPids?.contentToString()}")
    }

    private fun adjustCpusetProcs(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writePidNode(
            handle,
            cmd,
            AxSceneBoostSession.TYPE_CPUSET,
            targetPid(cmd, adjustPid, packageName),
            AxPlatformConfig.getCpusetProcs(cmd.restoreTag) ?: File(CPUSET_CGROUP_PROCS_PATH),
        )
    }

    private fun kswapdBindCoreControl(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        val bindCoreStr = cmd.params1
        val bindcores = AxCpuSets.resolveCores(bindCoreStr) ?: mKswapdAffinityBoost
        val cores = bindcores?.contentToString()
        logDebug(TAG, "kswapdBind: $bindCoreStr -> $cores")
        AxSceneBoostSession(
                this,
                AxSceneBoostSession.TYPE_KSWAPD,
                handle,
                cmd,
                Runnable { adjustKswapdAffinityInternal(bindcores) },
                Runnable { adjustKswapdAffinityInternal(mDefaultKswapdAffinity) },
            )
            .execute()
    }

    private fun adjustCpusetTasks(
        handle: Int,
        adjustTid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writePidNode(
            handle,
            cmd,
            AxSceneBoostSession.TYPE_CPUSET_TASK,
            AxPlatformConfig.paramsFromUser(params, cmd.command)?.toIntOrNull() ?: adjustTid,
            AxPlatformConfig.getCpusetTasks(cmd.restoreTag) ?: File(CPUSET_TASKS_PATH),
        )
    }

    private fun adjustCpuctlProcs(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        writePidNode(
            handle,
            cmd,
            AxSceneBoostSession.TYPE_CPUCTL,
            targetPid(cmd, adjustPid, packageName),
            AxPlatformConfig.getCpuctlProcs(cmd.restoreTag) ?: File(CPUCTL_CGROUP_PROCS_PATH),
        )
    }

    fun adjustKswapdAffinity(boost: Boolean) {
        adjustKswapdAffinityInternal(if (boost) mKswapdAffinityBoost else mDefaultKswapdAffinity)
    }

    private fun adjustKswapdAffinityInternal(affinity: IntArray?) {
        val targetAffinity = affinity ?: mDefaultKswapdAffinity ?: return
        val kswapdPids = mKswapdPids
        if (targetAffinity.isEmpty() || kswapdPids == null || kswapdPids.isEmpty()) return
        val affinityStr = targetAffinity.contentToString()
        val pidsStr = kswapdPids.contentToString()
        logDebug(TAG, "adjustKswapdAffinity: $affinityStr, pids = $pidsStr")
        for (pid in kswapdPids.filter { it > 0 }) {
            try {
                logDebug(TAG, "adjustKswapdAffinity: set $pid -> $affinityStr")
                Process.setThreadAffinity(pid, targetAffinity)
            } catch (e: Exception) {
                logDebug(TAG, "set kswapd affinity failed for $pid: ${e.message}")
            }
        }
    }

    private fun backgroundProcessFreeze(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(TAG, "backgroundProcessFreeze")
        BackgroundFreezeBoostSession(this, handle, cmd).execute()
    }

    private fun animationBoost(
        handle: Int,
        adjustPid: Int,
        packageName: String?,
        cmd: AxSceneTable.ScenarioCommand,
        params: String?,
    ) {
        logDebug(TAG, "animationBoost")
        val pid = targetPid(cmd, adjustPid, packageName)
        if (pid <= 0) return
        val renderThread = getRenderThreadTidByPid(pid)
        AxSceneBoostSession(
                this,
                AxSceneBoostSession.TYPE_ANIMATION,
                handle,
                cmd,
                Runnable { animationBoostInternal(pid, renderThread, BOOST_MODE_ON) },
                Runnable { animationBoostInternal(pid, renderThread, BOOST_MODE_OFF) },
            )
            .execute()
    }

    private fun boostAnimationThread(tid: Int) {
        if (tid > 0) boostThreadScheduler(tid)
    }

    private fun restoreAnimationThread(tid: Int, priority: Int) {
        if (tid <= 0) return
        restoreThreadScheduler(tid, priority)
    }

    private fun animationBoostInternal(pid: Int, renderTid: Int, boost: Boolean) {
        logDebug(TAG, "animationboost: pid = $pid, renderTid = $renderTid, boost = $boost")
        trace("animationBoost_boost:${boost}_pid:${pid}_renderTid:$renderTid") {
            try {
                val hwuiTaskTidsByPid = getHwuiTaskTidsByPid(pid)
                if (boost) {
                    boostAnimationThread(pid)
                    boostAnimationThread(renderTid)
                    hwuiTaskTidsByPid?.forEach(::boostAnimationThread)
                } else {
                    if (pid > 0) {
                        restoreThreadScheduler(pid, RESTORE_THREAD_PRIORITY)
                    }
                    restoreAnimationThread(renderTid, RESTORE_THREAD_PRIORITY)
                    hwuiTaskTidsByPid?.forEach {
                        restoreAnimationThread(it, RESTORE_HWUI_THREAD_PRIORITY)
                    }
                }
            } catch (unused: Exception) {
                logDebug(
                    TAG,
                    "animation boost pid: $pid, renderTid: $renderTid, boost: $boost failed!",
                )
            }
        }
    }

    private fun activateScene(handle: Int, sceneId: Int, bundle: Bundle?, duration: Int) {
        Slog.d(TAG, "acquire handle $handle")
        val sceneBundle =
            bundle?.deepCopy()?.apply { putInt(AxDragoniteConstants.KEY_HANDLE, handle) }
        if (duration > 0) {
            scheduleTimeout(handle, duration)
        }
        mManagerHandler.post {
            if (!hasBoostRecord(handle)) return@post
            val prevHandle = currentActiveHandle
            if (prevHandle != handle) {
                restoreHandle(prevHandle)
            }
            currentActiveHandle = handle
            dispatchSceneCommands(sceneId, sceneBundle)
        }
    }

    private fun scheduleTimeout(handle: Int, duration: Int) {
        val messageId = handle + HANDLE_MESSAGE_OFFSET
        mDragoniteHandler.removeMessages(messageId)
        mDragoniteHandler.sendEmptyMessageDelayed(messageId, duration.toLong())
    }

    override fun sceneBoostAcquire(sceneId: Int, bundle: Bundle?): Int =
        trace("sceneBoostAcquire") { acquireScene(sceneId, bundle) }

    private fun acquireScene(sceneId: Int, bundle: Bundle?): Int {
        if (AxSceneTable.getScenario(sceneId) == null) {
            Slog.d(TAG, "Scene id: $sceneId is not exist!")
            return AxDragoniteConstants.INVALID_HANDLE
        }
        val actualBundle = bundle?.deepCopy() ?: Bundle()
        var handle =
            actualBundle.getInt(
                AxDragoniteConstants.KEY_HANDLE,
                AxDragoniteConstants.INVALID_HANDLE,
            )
        var duration = actualBundle.getInt(AxDragoniteConstants.KEY_DURATION, MISSING_DURATION)
        if (duration <= 0) {
            duration =
                when (sceneId) {
                    AxDragoniteConstants.SCENE_APP_START ->
                        AxDragoniteConstants.DURATION_APP_LAUNCH_MS
                    AxDragoniteConstants.SCENE_FLING -> AxDragoniteConstants.DURATION_FLING_MS
                    else -> MISSING_DURATION
                }
            if (duration > 0) {
                actualBundle.putInt(AxDragoniteConstants.KEY_DURATION, duration)
            }
        }
        val existingRecord =
            if (handle > AxDragoniteConstants.INVALID_HANDLE) {
                mSessionRecords.getRecord(handle)
            } else {
                null
            }
        if (existingRecord == null || existingRecord.sceneId != sceneId) {
            handle = mSessionRecords.acquireRecord(sceneId, actualBundle)
            if (handle == AxDragoniteConstants.INVALID_HANDLE) {
                return handle
            }
            activateScene(handle, sceneId, actualBundle, duration)
            return handle
        }
        Slog.d(TAG, "update handle $handle")
        actualBundle.putInt(AxDragoniteConstants.KEY_HANDLE, handle)
        mSessionRecords.bringToFront(handle)
        mSessionRecords.getRecord(handle)?.updateBundle(actualBundle)
        if (duration > 0) {
            scheduleTimeout(handle, duration)
        }
        val sceneBundle = actualBundle.deepCopy()
        mManagerHandler.post {
            val record = mSessionRecords.getRecord(handle) ?: return@post
            val prevHandle = currentActiveHandle
            if (prevHandle != handle || !record.hasActiveTasks()) {
                if (prevHandle != handle) {
                    restoreHandle(prevHandle)
                }
                currentActiveHandle = handle
                record.restoreAll()
                dispatchSceneCommands(sceneId, sceneBundle)
            }
        }
        return handle
    }

    override fun sceneBoostRelease(handle: Int) {
        release(handle)
    }

    fun release(handle: Int) {
        Slog.d(TAG, "release $handle")
        if (
            handle <= AxDragoniteConstants.INVALID_HANDLE ||
                !hasBoostRecord(handle)
        ) {
            Slog.d(TAG, "invalid handle, ignore!")
            return
        }
        mDragoniteHandler.removeMessages(handle + HANDLE_MESSAGE_OFFSET)
        mManagerHandler.post(SceneRelease(this, handle))
    }

    override fun inputBoost() {
        if (mGameModeEnabled) return
        mDragoniteHandler.removeCallbacks(mInputBoostResetRunnable)
        if (mLimitBoostDisabled) {
            mLimitBoostDisabled = false
            mDragoniteHandler.post {
                adjustBackgroundLimit(true)
                adjustKswapdAffinity(true)
            }
        }
        mDragoniteHandler.postDelayed(
            mInputBoostResetRunnable,
            AxPlatformConfig.DURATION_INPUT_MS,
        )
    }

    private fun targetPid(
        cmd: AxSceneTable.ScenarioCommand,
        adjustPid: Int,
        packageName: String?,
    ): Int {
        val targetProc = cmd.targetProcess
        if (targetProc.isNullOrEmpty() || (adjustPid > 0 && targetProc == packageName)) {
            return adjustPid
        }
        return getPidByProcessName(targetProc)
    }

    fun getPidByProcessName(processName: String?): Int {
        if (processName.isNullOrEmpty()) return -1
        try {
            logDebug(TAG, "get process pid by name, processName = $processName")
            processPidCache[processName]?.let { procPid ->
                val procCmdLine = AxPlatformConfig.getProcessCmdLine(procPid)
                if (procCmdLine.isNotEmpty()) {
                    val procName = procCmdLine.split(AxCommandExec.SLASH)
                    if (procName[procName.size - 1] == processName) {
                        return procPid
                    }
                }
                processPidCache.remove(processName)
            }
            val rawPid = trace("getPidByProcessName") { getProcessPids()[processName] }
            if (rawPid != null && rawPid > 0) {
                processPidCache[processName] = rawPid
                logDebug(TAG, "process pid is $rawPid")
                return rawPid
            }
            return -1
        } catch (unused: Exception) {
            return -1
        }
    }

    private fun restoreHandle(handle: Int) {
        if (handle <= AxDragoniteConstants.INVALID_HANDLE) return
        mSessionRecords.getRecord(handle)?.restoreAll()
    }

    fun releaseScene(handle: Int) {
        restoreHandle(handle)
        mSessionRecords.releaseRecord(handle)
        if (handle != currentActiveHandle) return
        val activeRecord = mSessionRecords.lastActiveRecord
        if (activeRecord == null) {
            currentActiveHandle = AxDragoniteConstants.INVALID_HANDLE
            return
        }
        currentActiveHandle = activeRecord.handle
        dispatchSceneCommands(activeRecord.sceneId, activeRecord.getBundle())
    }

    private fun parseSfAffinity(configuredAffinity: String?): Int {
        if (configuredAffinity.isNullOrEmpty()) return AxCpuSets.sfBoostAffinity()
        return runCatching { AxCpuSets.resolveMask(configuredAffinity).toInt() }
            .getOrDefault(AxCpuSets.sfBoostAffinity())
    }

    override fun isSceneIdExist(sceneId: Int): Boolean = AxSceneTable.getScenario(sceneId) != null

    private fun benchSceneId(pkg: String?, component: String?): Int? {
        if (pkg == "com.antutu.benchmark.full") {
            return if (component?.contains("GameActivity") == true) {
                AxDragoniteConstants.SCENE_BENCH_GPU
            } else {
                AxDragoniteConstants.SCENE_BENCH_CPU
            }
        }
        if (pkg == "com.antutu.ABenchMark") return AxDragoniteConstants.SCENE_BENCH_CPU
        if (
            pkg == "com.google.android.apps.nbu.files" ||
                pkg == "com.google.android.packageinstaller"
        ) {
            return AxDragoniteConstants.SCENE_DISABLE_INPUT_BOOST
        }
        return null
    }

    override fun onActivityStart(
        pkg: String?,
        component: String?,
        uid: Int,
        isCold: Boolean,
        pid: Int,
    ) {
        val bundle = Bundle()
        bundle.putString(AxDragoniteConstants.KEY_PACKAGE_NAME, pkg)
        bundle.putBoolean(AxDragoniteConstants.KEY_IS_COLD, isCold)
        bundle.putInt(AxDragoniteConstants.KEY_UID, uid)
        bundle.putInt(AxDragoniteConstants.KEY_PID, pid)
        val duration = if (isCold) {
            AxDragoniteConstants.DURATION_APP_LAUNCH_COLD_MS
        } else {
            AxDragoniteConstants.DURATION_APP_LAUNCH_WARM_MS
        }
        bundle.putInt(AxDragoniteConstants.KEY_DURATION, duration)
        benchSceneId(pkg, component)?.let { sceneBoostAcquire(it, bundle.deepCopy()) }
        sceneBoostAcquire(AxDragoniteConstants.SCENE_APP_START, bundle)
    }

    override fun onSystemFling(duration: Int) {
        val holdMs = if (duration > 0) duration else AxDragoniteConstants.DURATION_FLING_MS
        val resource = PlatformResource(this, AxSceneTable.NAME_FLING)
        mDragoniteHandler.post(resource)
        mDragoniteHandler.postDelayed(resource.release, holdMs.toLong())
        mManagerHandler.post { mBackgroundLoadLimit = BACKGROUND_LIMIT_ENABLED }
    }

    override fun updateGameModeBoost(enabled: Boolean) {
        mGameModeEnabled = enabled
        if (enabled) {
            mGameModeHandle = sceneBoostAcquire(AxDragoniteConstants.SCENE_GAME_1, null)
        } else {
            if (mGameModeHandle != AxDragoniteConstants.INVALID_HANDLE) {
                sceneBoostRelease(mGameModeHandle)
                mGameModeHandle = AxDragoniteConstants.INVALID_HANDLE
            }
        }
    }

    override fun setProcessList(processList: ProcessList) {
        mProcessList = processList
    }

    override fun adjustCpusetCpus(group: String, cpus: String?, durationMs: Long) {
        if (AxCmdTable.GROUP_DEX2OAT != group || cpus != null) return
        val targetCpus =
            if (durationMs == 0L) {
                AxCpuClusterManager.topology.restrictedDex2oat.toString()
            } else {
                AxCpuClusterManager.topology.ui.toString()
            }
        try {
            FileUtils.stringToFile(File(CPUSET_DEX2OAT_CPUS), targetCpus)
        } catch (e: IOException) {
            logDebug(TAG, "Failed to adjust dex2oat cpuset")
        }
    }

    fun resetAll() {
        Slog.w(TAG, "resetAll: releasing all boost handles and resetting pools")
        for (i in 0 until SessionRecords.MAX_HANDLES) {
            mDragoniteHandler.removeMessages(i + HANDLE_MESSAGE_OFFSET)
        }
        val activeRecords = mSessionRecords.getActiveRecords()
        for (session in activeRecords) {
            session.restoreAll()
        }
        mSessionRecords.resetAll()
        currentActiveHandle = AxDragoniteConstants.INVALID_HANDLE
        sfBindControlInternal(false)
        adjustBackgroundLimit(false)
        adjustKswapdAffinity(false)
        mLimitBoostDisabled = true
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<out String>?) {
        val safeArgs = args ?: emptyArray()
        val cmd = safeArgs.firstOrNull()?.lowercase()
        when (cmd) {
            null, "", "status", "--status" -> dumpStatus(pw)
            "-h", "--help", "help" -> dumpHelp(pw)
            "--handles", "handles" -> dumpHandles(pw)
            "--scenes", "scenes" -> dumpScenes(pw)
            "--cleanup", "cleanup" -> dumpCleanup(pw)
            "--release", "release" -> dumpRelease(pw, safeArgs.getOrNull(1))
            "--reset", "reset" -> dumpReset(pw)
            "--acquire", "acquire" -> dumpAcquire(pw, safeArgs.getOrNull(1), safeArgs.getOrNull(2))
            "--debug", "debug" -> dumpDebug(pw, safeArgs.getOrNull(1))
            else -> {
                pw.println("Unknown command: $cmd")
                pw.println()
                dumpHelp(pw)
            }
        }
    }

    private fun dumpHelp(pw: PrintWriter) {
        pw.println("Axion Dragonite Subsystem (dumpsys activity dragonite)")
        pw.println("Usage: adb shell dumpsys activity dragonite [command]")
        pw.println()
        pw.println("Commands:")
        pw.println("  (no args)                          Dump overall status, active handles, and session health")
        pw.println("  handles, --handles                 Dump active handles and session details")
        pw.println("  scenes, --scenes                   Dump registered boost scenarios and commands")
        pw.println("  cleanup, --cleanup                 Detect and release stuck handles from dead processes")
        pw.println("  release, --release <handle>        Force-release a specific active handle")
        pw.println("  reset, --reset                     Emergency reset: release all handles & reset pool")
        pw.println("  acquire, --acquire <id> [dur_ms]   Trigger test boost acquisition for a scene")
        pw.println("  debug, --debug <on|off>            Enable or disable verbose debug logging")
        pw.println("  help, -h, --help                   Display this help message")
    }

    private fun dumpStatus(pw: PrintWriter) {
        pw.println("===============================================================================")
        pw.println("                    Axion Dragonite Subsystem Status")
        pw.println("===============================================================================")
        pw.println("Command: adb shell dumpsys activity dragonite")
        pw.println("Debug Logging Enabled: $debug ($DEBUG_PROP)")
        pw.println("Game Mode Active: $mGameModeEnabled")
        pw.println("Current Active Handle: $currentActiveHandle")
        pw.println("Background Load Limit: $mBackgroundLoadLimit")
        pw.println("Limit Boost Disabled: $mLimitBoostDisabled")
        pw.println("Kswapd PIDs: ${mKswapdPids?.contentToString() ?: "none"}")
        pw.println("Kswapd Boost Affinity: ${mKswapdAffinityBoost?.contentToString() ?: "none"}")
        pw.println("Default Kswapd Affinity: ${mDefaultKswapdAffinity?.contentToString() ?: "none"}")
        pw.println()
        dumpHandles(pw)
        pw.println()
        pw.println("Run 'adb shell dumpsys activity dragonite --help' to see diagnostic commands.")
        pw.println("===============================================================================")
    }

    private fun dumpHandles(pw: PrintWriter) {
        val availableCount = mSessionRecords.getAvailableHandlesCount()
        val activeRecords = mSessionRecords.getActiveRecords()
        val allocatedHandles = activeRecords.map { it.handle }
        val now = SystemClock.elapsedRealtime()

        pw.println("-------------------------------------------------------------------------------")
        pw.println("Handles & Active Boost Sessions:")
        pw.println("-------------------------------------------------------------------------------")
        pw.println("Capacity: ${SessionRecords.MAX_HANDLES} handles (0..${SessionRecords.MAX_HANDLES - 1})")
        pw.println("Allocated Handles Count: ${allocatedHandles.size} (Allocated: $allocatedHandles)")
        pw.println("Available Handles Count: $availableCount")
        pw.println("Active Records Count: ${activeRecords.size}")

        if (allocatedHandles.size >= SessionRecords.MAX_HANDLES) {
            pw.println("!!! WARNING: HANDLE POOL EXHAUSTED (100/100)! ALL FUTURE BOOSTS WILL FAIL! !!!")
            pw.println("!!! Use 'dumpsys activity dragonite cleanup' or 'dumpsys activity dragonite reset' to recover.  !!!")
        } else if (allocatedHandles.size >= SessionRecords.MAX_HANDLES * 0.8) {
            pw.println("! WARNING: Handle pool high usage (${allocatedHandles.size}/${SessionRecords.MAX_HANDLES}). Potential leak!")
        }

        if (activeRecords.isEmpty()) {
            pw.println("No active boost sessions.")
            return
        }

        var deadProcessCount = 0
        for (session in activeRecords) {
            val handle = session.handle
            val isCurrent = handle == currentActiveHandle
            val bundle = session.getBundle()
            val pid = bundlePid(bundle)
            val pkg = bundlePackage(bundle) ?: "unknown"
            val duration = bundle?.getInt(AxDragoniteConstants.KEY_DURATION, MISSING_DURATION) ?: MISSING_DURATION
            val scenario = AxSceneTable.getScenario(session.sceneId)
            val scenarioName = scenario?.scenarioName ?: "UNKNOWN"
            val ageMs = now - session.createTime
            val processDead = isSessionDead(session, resolveMissingPid = false)

            if (processDead) deadProcessCount++

            val statusFlag = when {
                processDead -> " [DEAD PROCESS - STUCK BOOST!]"
                isCurrent -> " [CURRENT ACTIVE]"
                else -> ""
            }

            pw.println()
            pw.println("  [Handle #$handle]$statusFlag")
            pw.println("    Scene: ${session.sceneId} ($scenarioName)")
            pw.println("    Client: $pkg (PID: $pid) -> ${if (processDead) "DEAD / TERMINATED" else "ALIVE"}")
            pw.println("    Age: ${ageMs}ms (${ageMs / 1000}s)")
            pw.println("    Duration: ${if (duration > 0) "${duration}ms" else "No duration (manual release required)"}")
            val tasks = session.getTasks()
            pw.println("    Attached Tasks (${tasks.size}):")
            for (task in tasks) {
                val cmd = task.cmd
                pw.println("      - [Type ${task.sessionType}] cmd: ${cmd.command.name} (id: ${cmd.command.id}), hold: ${cmd.holdTime}ms, delay: ${cmd.delayTime}ms")
            }
        }

        if (deadProcessCount > 0) {
            pw.println()
            pw.println("-------------------------------------------------------------------------------")
            pw.println("!!! DETECTED $deadProcessCount STUCK HANDLE(S) WITH DEAD CLIENT PROCESSES! !!!")
            pw.println("Run 'adb shell dumpsys activity dragonite cleanup' to automatically release them.")
            pw.println("-------------------------------------------------------------------------------")
        }
    }

    private fun dumpScenes(pw: PrintWriter) {
        pw.println("-------------------------------------------------------------------------------")
        pw.println("Registered Dragonite Scenarios:")
        pw.println("-------------------------------------------------------------------------------")
        val scenarios = AxSceneTable.getAllScenarios().sortedBy { it.scenarioId }
        for (scenario in scenarios) {
            pw.println("Scene ID: ${scenario.scenarioId} -> \"${scenario.scenarioName}\" (${scenario.commandList.size} commands)")
            for (cmd in scenario.commandList) {
                val command = cmd.command
                pw.println("  Command: ${command.name} (id=${command.id}), holdTime=${cmd.holdTime}ms, delayTime=${cmd.delayTime}ms, process=${cmd.targetProcess ?: "any"}")
            }
        }
    }

    private fun dumpCleanup(pw: PrintWriter) {
        pw.println("Running dead process scan and cleanup...")
        val cleaned = checkSessionStatus()
        if (cleaned > 0) {
            pw.println("Cleanup complete: released $cleaned stuck handle(s) from dead processes.")
        } else {
            pw.println("Cleanup complete: no dead processes found holding active handles.")
        }
    }

    private fun dumpRelease(pw: PrintWriter, handleStr: String?) {
        if (handleStr.isNullOrEmpty()) {
            pw.println("Error: missing handle argument. Usage: dumpsys activity dragonite release <handle>")
            return
        }
        val handle = handleStr.toIntOrNull()
        if (handle == null) {
            pw.println("Error: invalid handle format '$handleStr'. Must be an integer.")
            return
        }
        if (!hasBoostRecord(handle)) {
            pw.println("Error: handle $handle is not currently allocated.")
            return
        }
        val latch = CountDownLatch(1)
        mDragoniteHandler.removeMessages(handle + HANDLE_MESSAGE_OFFSET)
        mManagerHandler.post {
            try {
                releaseScene(handle)
            } finally {
                latch.countDown()
            }
        }
        val completed = latch.await(BINDER_DUMP_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (completed) {
            pw.println("Successfully released handle $handle.")
        } else {
            pw.println("Release initiated for handle $handle (waiting for manager thread).")
        }
    }

    private fun dumpReset(pw: PrintWriter) {
        val latch = CountDownLatch(1)
        mManagerHandler.post {
            try {
                resetAll()
            } finally {
                latch.countDown()
            }
        }
        val completed = latch.await(BINDER_DUMP_TIMEOUT_SEC, TimeUnit.SECONDS)
        if (completed) {
            pw.println("Dragonite emergency reset executed:")
            pw.println("  - All active sessions released and restored")
            pw.println("  - All pending timers cancelled")
            pw.println("  - Handle pools reset to full capacity (${SessionRecords.MAX_HANDLES})")
            pw.println("  - Affinities and background limits restored to default")
        } else {
            pw.println("Warning: Dragonite emergency reset timed out waiting for manager thread.")
        }
    }

    private fun dumpAcquire(pw: PrintWriter, sceneStr: String?, durationStr: String?) {
        if (sceneStr.isNullOrEmpty()) {
            pw.println("Error: missing sceneId argument. Usage: dumpsys activity dragonite acquire <sceneId> [durationMs]")
            return
        }
        val sceneId = sceneStr.toIntOrNull() ?: run {
            val match = AxSceneTable.getAllScenarios().firstOrNull {
                it.scenarioName.equals(sceneStr, ignoreCase = true)
            }
            match?.scenarioId
        }
        if (sceneId == null || AxSceneTable.getScenario(sceneId) == null) {
            pw.println("Error: unknown scene '$sceneStr'. Run 'dumpsys activity dragonite scenes' for a list.")
            return
        }
        val duration = durationStr?.toIntOrNull() ?: 1000
        val bundle = Bundle().apply {
            putInt(AxDragoniteConstants.KEY_DURATION, duration)
            putString(AxDragoniteConstants.KEY_PACKAGE_NAME, "com.android.shell")
            putInt(AxDragoniteConstants.KEY_PID, Process.myPid())
        }
        val handle = sceneBoostAcquire(sceneId, bundle)
        val scenarioName = AxSceneTable.getScenario(sceneId)?.scenarioName ?: "UNKNOWN"
        if (handle <= AxDragoniteConstants.INVALID_HANDLE) {
            pw.println("Failed to acquire boost for scene $sceneId ($scenarioName)! Handle allocation failed (all ${SessionRecords.MAX_HANDLES} handles may be in use).")
        } else {
            pw.println("Acquired scene $sceneId ($scenarioName) -> handle=$handle, duration=${duration}ms")
        }
    }

    private fun dumpDebug(pw: PrintWriter, modeStr: String?) {
        when (modeStr?.lowercase()) {
            "on", "true", "1" -> {
                try {
                    SystemProperties.set(DEBUG_PROP, "true")
                    pw.println("Dragonite debug logging enabled.")
                } catch (e: Exception) {
                    pw.println("Failed to set property $DEBUG_PROP: ${e.message}")
                }
            }
            "off", "false", "0" -> {
                try {
                    SystemProperties.set(DEBUG_PROP, "false")
                    pw.println("Dragonite debug logging disabled.")
                } catch (e: Exception) {
                    pw.println("Failed to set property $DEBUG_PROP: ${e.message}")
                }
            }
            else -> {
                pw.println("Current debug state: $debug ($DEBUG_PROP)")
                pw.println("Usage: dumpsys activity dragonite debug <on|off>")
            }
        }
    }
}
