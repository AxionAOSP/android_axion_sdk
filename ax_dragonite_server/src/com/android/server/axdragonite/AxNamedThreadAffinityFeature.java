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
    public static final String COMM_JIT_THREAD_POOL = "Jit thread pool";
    public static final String COMM_HEAP_TASK_DAEMON = "HeapTaskDaemon";
    public static final String PREFIX_BINDER = "binder:";
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
        mDefaultCommRules.put(KEYWORD_PRIMES, mClusterManager.getLittleMask());
        mDefaultCommRules.put(KEYWORD_LOWPOOL, mClusterManager.getLittleMask());
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
            for (Map.Entry<Integer, Long> entry : cachedRules.entrySet()) {
                setThreadAffinity(entry.getKey(), entry.getValue());
            }
            return;
        }

        Map<Integer, Long> rulesToCache = new HashMap<>();
        for (int tid : tids) {
            if (tid <= 0) {
                break;
            }
            if (tid == pid) {
                boolean isUiSystemProc = (mProcessTracker != null
                        && (pid == mProcessTracker.getLauncherPid() || pid == mProcessTracker.getSystemUiPid()));
                long mask = isUiSystemProc ? mClusterManager.getBigMask() : mClusterManager.getBoostMask();
                rulesToCache.put(tid, mask);
                setThreadAffinity(tid, mask);
                continue;
            }
            String comm = readComm(tid);
            Long mask = resolveThreadMask(comm);
            if (mask == null) {
                if (isSystemServer && comm != null && comm.startsWith(PREFIX_BINDER)) {
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
        if (comm.contains(KEYWORD_PRIMES)
                || comm.contains(KEYWORD_LOWPOOL)
                || comm.contains(KEYWORD_HIGHPOOL)
                || comm.startsWith(KEYWORD_LAUNCHER_BG)
                || comm.startsWith(KEYWORD_LAUNCHER_LOADER)
                || comm.startsWith(KEYWORD_SYSUI_BG)
                || comm.startsWith(KEYWORD_SYS_UI_BG)
                || comm.equals(KEYWORD_SHADE_GC)) {
            return mClusterManager.getLittleMask();
        }
        if (comm.contains(KEYWORD_WMSHELL)
                || comm.contains(KEYWORD_SPLASH)
                || comm.contains(KEYWORD_THUMBNAIL)) {
            return mClusterManager.getBoostMask();
        }
        return null;
    }

    private void elevateThreadPriority(int tid) {
        try {
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_URGENT_DISPLAY);
        } catch (Throwable ignored) {
        }
    }

    public void yieldPidToLittleCores(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
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
                setThreadAffinity(tid, allMask);
            } catch (Exception ignored) {
            }
        }
    }

    public void setThreadAffinity(int tid, long mask) {
        try {
            Process.setThreadAffinity(tid, (int) mask);
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to set thread affinity for " + tid + ": " + t.getMessage());
        }
    }

    private String readComm(int tid) {
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
