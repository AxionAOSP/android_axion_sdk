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

package com.android.server.axdragonite;

import android.os.Process;
import android.util.Slog;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @hide
 */
public final class AxNamedThreadAffinityFeature {
    private static final String TAG = "AxNamedThreadAffinity";

    public static final String COMM_RENDER_THREAD = "RenderThread";
    public static final String COMM_CR_RENDERER_MAIN = "CrRendererMain";
    public static final String COMM_UNITY_MAIN = "UnityMain";
    public static final String COMM_GL_THREAD = "GLThread";
    public static final String COMM_MAIN_THREAD = "MainThread";
    public static final String COMM_AUDIO_TRACK = "AudioTrack";
    public static final String COMM_WMSHELL_MAIN = "wmshell.main";
    public static final String COMM_WMSHELL_ANIM = "wmshell.anim";
    public static final String COMM_SPLASH_SCREEN = "ll.splashscreen";
    public static final String COMM_ANDROID_ANIM = "android.anim";
    public static final String COMM_ANDROID_DISPLAY = "android.display";
    public static final String COMM_UI_THREAD_HELPER = "UiThreadHelper";
    public static final String KEYWORD_WMSHELL = "wmshell";
    public static final String KEYWORD_SPLASH = "splashscreen";
    public static final String KEYWORD_THUMBNAIL = "TaskThumbnail";
    public static final String KEYWORD_PRIMES = "Primes";
    public static final String KEYWORD_LOWPOOL = "lowpool";
    public static final String KEYWORD_HIGHPOOL = "highpool";
    public static final String KEYWORD_LAUNCHER_BG = "LauncherBg";
    public static final String KEYWORD_LAUNCHER_LOADER = "launcher-loader";
    public static final String KEYWORD_SYSUI_BG = "SystemUIBg";
    public static final String KEYWORD_SYS_UI_BG = "SysUiBg";
    public static final String KEYWORD_SHADE_GC = "ShadeGC";
    public static final String KEYWORD_HWUI_TASK = "hwuiTask";
    public static final String KEYWORD_DAEMON_LOWER = "daemon";
    public static final String KEYWORD_DAEMON_UPPER = "Daemon";

    public static final String PREFIX_POOL = "pool-";
    public static final String PREFIX_COROUTINES_DEFAULT = "DefaultDispatch";
    public static final String PREFIX_SCRIM_UTILS = "ScrimUtils-bg";
    public static final String PREFIX_RX = "Rx";
    public static final String PREFIX_MEDIA_CODEC = "NDK MediaCodec_";
    public static final String PREFIX_BINDER = "binder:";
    public static final String PREFIX_AUDIO_OUT = "AudioOut_";
    public static final String PREFIX_EXOPLAYER = "ExoPlayer:";
    public static final String PREFIX_AUDIO_TRACK = "AudioTrack";

    public static final String COMM_FAST_MIXER = "FastMixer";
    public static final String COMM_JIT_THREAD_POOL = "Jit thread pool";
    public static final String COMM_HEAP_TASK_DAEMON = "HeapTaskDaemon";
    public static final String COMM_SIGNAL_CATCHER = "Signal Catcher";
    public static final String COMM_REFERENCE_QUEUE_SHORT = "ReferenceQueueD";
    public static final String COMM_REFERENCE_QUEUE_DAEMON = "ReferenceQueueDaemon";
    public static final String COMM_FINALIZER_DAEMON = "FinalizerDaemon";
    public static final String COMM_FINALIZER_WATCHDOG_SHORT = "FinalizerWatchd";
    public static final String COMM_FINALIZER_WATCHDOG_DAEMON = "FinalizerWatchdogDaemon";
    public static final String COMM_TRACING_MUXER = "TracingMuxer";
    public static final String COMM_QUEUED_WORK_SHORT = "queued-work-loo";
    public static final String COMM_QUEUED_WORK_LOOP = "queued-work-loop";
    public static final String COMM_WIFI_PICKER = "WifiPickerTrack";
    public static final String COMM_CALLBACK_HANDLER = "callbackHandler";
    public static final String COMM_WMSHELL_DESKTOP = "wmshell.desktop";
    public static final String COMM_HWUI_TASK_0 = "hwuiTask0";
    public static final String COMM_HWUI_TASK_1 = "hwuiTask1";
    public static final String COMM_COMPOSER_SERVICE = "composer-servic";
    public static final String COMM_SDM_EVENT = "SDM_EventThread";
    public static final String PREFIX_HWBINDER = "HwBinder:";

    private static final String[] LITTLE_AFFINITY_PREFIXES = {
        KEYWORD_LAUNCHER_BG,
        KEYWORD_LAUNCHER_LOADER,
        KEYWORD_SYSUI_BG,
        KEYWORD_SYS_UI_BG,
        PREFIX_POOL,
        PREFIX_COROUTINES_DEFAULT,
        PREFIX_SCRIM_UTILS,
        PREFIX_RX,
        PREFIX_MEDIA_CODEC,
        PREFIX_AUDIO_OUT,
        PREFIX_EXOPLAYER,
        PREFIX_AUDIO_TRACK
    };

    private static final String[] LITTLE_AFFINITY_KEYWORDS = {
        KEYWORD_PRIMES,
        KEYWORD_LOWPOOL,
        KEYWORD_HIGHPOOL,
        KEYWORD_DAEMON_LOWER,
        KEYWORD_DAEMON_UPPER,
        COMM_FAST_MIXER
    };

    private static final String[] BOOST_AFFINITY_KEYWORDS = {
        KEYWORD_WMSHELL,
        KEYWORD_SPLASH,
        KEYWORD_THUMBNAIL,
        KEYWORD_HWUI_TASK,
        COMM_COMPOSER_SERVICE,
        COMM_SDM_EVENT,
        PREFIX_HWBINDER
    };

    public static final int PID_BUFFER_CAPACITY = 1024;
    public static final int COMM_BUFFER_SIZE = 32;

    public static final String PATH_PROC_PREFIX = "/proc/";
    public static final String PATH_TASK_SUFFIX = "/task";
    public static final String PATH_COMM_SUFFIX = "/comm";

    public static final String PATH_NTA_PID = "/proc/ax_named_thread_affinity/pid";
    public static final String PATH_NTA_AFFINITY = "/proc/ax_named_thread_affinity/named_thread_affinity";
    public static final String PATH_NTA_RESET = "/proc/ax_named_thread_affinity/reset";

    public static final String VALUE_RESET_TRIGGER = "1";
    public static final int INVALID_PID = 0;

    private final AxCpuClusterManager mClusterManager;
    private final AxProcessTracker mProcessTracker;
    private final Map<String, Long> mDefaultCommRules = new HashMap<>();
    private final Map<Integer, Map<Integer, Long>> mPidThreadAffinityCache = new HashMap<>();
    private final Map<Integer, Integer> mPidTaskCountMap = new HashMap<>();

    public AxNamedThreadAffinityFeature(AxCpuClusterManager clusterManager, AxProcessTracker processTracker) {
        this.mClusterManager = clusterManager;
        this.mProcessTracker = processTracker;
        initDefaultRules();
    }

    public AxNamedThreadAffinityFeature(AxCpuClusterManager clusterManager) {
        this(clusterManager, null);
    }

    private void initDefaultRules() {
        mDefaultCommRules.put(COMM_RENDER_THREAD, mClusterManager.getBigMask());
        mDefaultCommRules.put(COMM_CR_RENDERER_MAIN, mClusterManager.getBigMask());
        mDefaultCommRules.put(COMM_UNITY_MAIN, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_GL_THREAD, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_MAIN_THREAD, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_AUDIO_TRACK, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_WMSHELL_MAIN, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_WMSHELL_ANIM, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_SPLASH_SCREEN, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_ANDROID_ANIM, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_ANDROID_DISPLAY, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_UI_THREAD_HELPER, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_JIT_THREAD_POOL, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_HEAP_TASK_DAEMON, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_FAST_MIXER, mClusterManager.getLittleMask());
        mDefaultCommRules.put(KEYWORD_PRIMES, mClusterManager.getLittleMask());
        mDefaultCommRules.put(KEYWORD_LOWPOOL, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_SIGNAL_CATCHER, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_REFERENCE_QUEUE_SHORT, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_REFERENCE_QUEUE_DAEMON, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_FINALIZER_DAEMON, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_FINALIZER_WATCHDOG_SHORT, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_FINALIZER_WATCHDOG_DAEMON, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_TRACING_MUXER, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_QUEUED_WORK_SHORT, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_QUEUED_WORK_LOOP, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_WIFI_PICKER, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_CALLBACK_HANDLER, mClusterManager.getLittleMask());
        mDefaultCommRules.put(COMM_WMSHELL_DESKTOP, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_HWUI_TASK_0, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_HWUI_TASK_1, mClusterManager.getBoostMask());
    }

    public void applyNamedAffinityForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }

        int[] tids = Process.getPids(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX, new int[PID_BUFFER_CAPACITY]);
        if (tids == null) {
            return;
        }

        int activeCount = 0;
        while (activeCount < tids.length && tids[activeCount] > 0) {
            activeCount++;
        }

        Map<Integer, Long> cachedRules = mPidThreadAffinityCache.get(pid);
        Integer prevTaskCount = mPidTaskCountMap.get(pid);
        boolean isSystemServer = (pid == Process.myPid());
        if (cachedRules != null && cachedRules.size() > 1 && (!isSystemServer || (prevTaskCount != null && prevTaskCount == activeCount))) {
            boolean hasDeadThread = false;
            for (int tid : cachedRules.keySet()) {
                if (tid <= INVALID_PID || !new File(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX + "/" + tid).exists()) {
                    hasDeadThread = true;
                    break;
                }
            }
            if (!hasDeadThread) {
                for (Map.Entry<Integer, Long> entry : cachedRules.entrySet()) {
                    setThreadAffinity(entry.getKey(), entry.getValue());
                }
                return;
            }
            mPidThreadAffinityCache.remove(pid);
            mPidTaskCountMap.remove(pid);
        }

        boolean isUiSystemProc = (mProcessTracker != null
                && (pid == mProcessTracker.getLauncherPid() || pid == mProcessTracker.getSystemUiPid() || pid == mProcessTracker.getComposerPid()));
        Map<Integer, Long> rulesToCache = new HashMap<>();
        for (int tid : tids) {
            if (tid <= 0) {
                break;
            }
            if (tid == pid) {
                long mask = isUiSystemProc ? mClusterManager.getBigMask() : mClusterManager.getBoostMask();
                rulesToCache.put(tid, mask);
                setThreadAffinity(tid, mask);
                continue;
            }
            String comm = readComm(tid);
            Long mask = resolveThreadMask(comm);
            if (mask == null) {
                if ((isSystemServer || isUiSystemProc) && comm != null && comm.startsWith(PREFIX_BINDER)) {
                    mask = mClusterManager.getBoostMask();
                } else {
                    continue;
                }
            }
            rulesToCache.put(tid, mask);
            setThreadAffinity(tid, mask);
            if (COMM_ANDROID_ANIM.equals(comm)
                    || COMM_ANDROID_DISPLAY.equals(comm)
                    || COMM_UI_THREAD_HELPER.equals(comm)
                    || comm.contains(KEYWORD_THUMBNAIL)) {
                elevateThreadPriority(tid);
            }
        }
        if (rulesToCache.size() > 1) {
            mPidThreadAffinityCache.put(pid, rulesToCache);
            mPidTaskCountMap.put(pid, activeCount);
        }

        if (cachedRules == null) {
            AxPerfEnhancer.writeNode(PATH_NTA_PID, String.valueOf(pid));
            for (Map.Entry<String, Long> entry : mDefaultCommRules.entrySet()) {
                String rule = entry.getKey() + " 0x" + Long.toHexString(entry.getValue());
                AxPerfEnhancer.writeNode(PATH_NTA_AFFINITY, rule);
            }
        }
    }

    private Long resolveThreadMask(String comm) {
        if (comm == null || comm.isEmpty()) {
            return null;
        }
        Long mask = mDefaultCommRules.get(comm);
        if (mask != null) {
            return mask;
        }
        if (comm.equals(KEYWORD_SHADE_GC)) {
            return mClusterManager.getLittleMask();
        }
        for (int i = 0; i < LITTLE_AFFINITY_PREFIXES.length; i++) {
            if (comm.startsWith(LITTLE_AFFINITY_PREFIXES[i])) {
                return mClusterManager.getLittleMask();
            }
        }
        for (int i = 0; i < LITTLE_AFFINITY_KEYWORDS.length; i++) {
            if (comm.contains(LITTLE_AFFINITY_KEYWORDS[i])) {
                return mClusterManager.getLittleMask();
            }
        }
        for (int i = 0; i < BOOST_AFFINITY_KEYWORDS.length; i++) {
            if (comm.contains(BOOST_AFFINITY_KEYWORDS[i])) {
                return mClusterManager.getBoostMask();
            }
        }
        return null;
    }

    private void elevateThreadPriority(int tid) {
        if (tid <= INVALID_PID) {
            return;
        }
        try {
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_URGENT_DISPLAY);
        } catch (Throwable ignored) {
        }
    }

    public void yieldPidToLittleCores(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
        mPidThreadAffinityCache.remove(pid);
        mPidTaskCountMap.remove(pid);
        long littleMask = mClusterManager.getLittleMask();
        setThreadAffinity(pid, littleMask);
        int[] tids = Process.getPids(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX, new int[PID_BUFFER_CAPACITY]);
        if (tids == null) {
            return;
        }
        for (int tid : tids) {
            if (tid <= 0) {
                break;
            }
            setThreadAffinity(tid, littleMask);
        }
    }

    public void promoteLoaderThreadsForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
        int[] tids = Process.getPids(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX, new int[PID_BUFFER_CAPACITY]);
        if (tids == null) {
            return;
        }
        long bigMask = mClusterManager.getBigMask();
        for (int tid : tids) {
            if (tid <= 0) {
                break;
            }
            String comm = readComm(tid);
            if (comm != null && (comm.startsWith(KEYWORD_LAUNCHER_LOADER) || comm.startsWith(KEYWORD_LAUNCHER_BG))) {
                setThreadAffinity(tid, bigMask);
                elevateThreadPriority(tid);
            }
        }
    }

    public void restoreLoaderThreadsForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
        int[] tids = Process.getPids(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX, new int[PID_BUFFER_CAPACITY]);
        if (tids == null) {
            return;
        }
        long littleMask = mClusterManager.getLittleMask();
        for (int tid : tids) {
            if (tid <= 0) {
                break;
            }
            String comm = readComm(tid);
            if (comm != null && (comm.startsWith(KEYWORD_LAUNCHER_LOADER) || comm.startsWith(KEYWORD_LAUNCHER_BG))) {
                setThreadAffinity(tid, littleMask);
                resetThreadPriority(tid);
            }
        }
    }

    private void resetThreadPriority(int tid) {
        if (tid <= INVALID_PID) {
            return;
        }
        try {
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_BACKGROUND);
        } catch (Throwable ignored) {
        }
    }

    public void boostDisplayComposer() {
        if (mProcessTracker == null) {
            return;
        }
        int composerPid = mProcessTracker.getComposerPid();
        if (composerPid > INVALID_PID) {
            applyNamedAffinityForPid(composerPid);
        }
    }

    public void restoreDisplayComposer() {
        if (mProcessTracker == null) {
            return;
        }
        int composerPid = mProcessTracker.getComposerPid();
        if (composerPid > INVALID_PID) {
            resetAffinityForPid(composerPid);
        }
    }

    public void resetAffinityForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
        mPidThreadAffinityCache.remove(pid);
        mPidTaskCountMap.remove(pid);
        AxPerfEnhancer.writeNode(PATH_NTA_PID, String.valueOf(pid));
        AxPerfEnhancer.writeNode(PATH_NTA_RESET, VALUE_RESET_TRIGGER);

        File taskDir = new File(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX);
        if (!taskDir.exists() || !taskDir.isDirectory()) {
            return;
        }
        File[] threads = taskDir.listFiles();
        if (threads == null) {
            return;
        }
        long allMask = mClusterManager.getAllMask();
        for (File threadDir : threads) {
            try {
                int tid = Integer.parseInt(threadDir.getName());
                if (tid <= INVALID_PID) {
                    continue;
                }
                setThreadAffinity(tid, allMask);
            } catch (Exception ignored) {
            }
        }
    }

    public void setThreadAffinity(int tid, long mask) {
        if (tid <= INVALID_PID) {
            return;
        }
        try {
            Process.setThreadAffinity(tid, (int) mask);
        } catch (IllegalArgumentException ignored) {
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to set thread affinity for " + tid + ": " + t.getMessage());
        }
    }

    private String readComm(int tid) {
        if (tid <= INVALID_PID) {
            return null;
        }
        File file = new File(PATH_PROC_PREFIX + tid + PATH_COMM_SUFFIX);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[COMM_BUFFER_SIZE];
            int len = fis.read(buf);
            if (len > 0) {
                if (buf[len - 1] == '\n') len--;
                return new String(buf, 0, len, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
