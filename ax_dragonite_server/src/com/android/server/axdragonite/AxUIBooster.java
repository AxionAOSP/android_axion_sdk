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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @hide
 */
public final class AxUIBooster {
    private static final String TAG = "AxUIBooster";

    public static final String THREAD_NAME_RENDER_THREAD = "RenderThread";
    public static final String PATH_PROC_PREFIX = "/proc/";
    public static final String PATH_TASK_SUFFIX = "/task";
    public static final String FILE_NAME_COMM = "comm";

    public static final int INVALID_TID = -1;
    public static final int INVALID_PID = 0;
    public static final int SCHED_REALTIME_PRIORITY = 1;
    public static final int SCHED_DEFAULT_PRIORITY = 0;
    public static final int BOOST_LEVEL_HEAVY_THRESHOLD = 2;
    public static final int BOOST_LEVEL_RESTORE = 0;
    public static final int INITIAL_REF_COUNT = 1;
    public static final int MIN_REF_COUNT = 0;

    private static final int SCHED_NORMAL = 0;
    private static final int SCHED_RR_RESET_ON_FORK = Process.SCHED_RR | Process.SCHED_RESET_ON_FORK;

    private final AxPerfEnhancer mPerfEnhancer;
    private final AxCpuClusterManager mClusterManager;
    private final Map<Integer, Integer> mBoostedThreads = new HashMap<>();
    private final Map<Integer, Integer> mBoostPidCountMap = new HashMap<>();

    public AxUIBooster(AxPerfEnhancer perfEnhancer, AxCpuClusterManager clusterManager) {
        this.mPerfEnhancer = perfEnhancer;
        this.mClusterManager = clusterManager;
    }

    public synchronized void boostProcess(int pid, int boostLevel) {
        if (pid <= INVALID_PID) {
            return;
        }

        int currentCount = mBoostPidCountMap.getOrDefault(pid, MIN_REF_COUNT) + 1;
        mBoostPidCountMap.put(pid, currentCount);

        if (currentCount == INITIAL_REF_COUNT) {
            mPerfEnhancer.migrateToTopAppCgroup(pid);
            mPerfEnhancer.setTaskBoost(pid, boostLevel);

            List<Integer> hwuiTids = findHwuiThreadTids(pid);
            for (int tid : hwuiTids) {
                boostThread(tid, boostLevel);
            }
            boostThread(pid, boostLevel);
        }
    }

    public synchronized void boostThread(int tid, int boostLevel) {
        if (tid <= INVALID_PID) {
            return;
        }

        try {
            if (!mBoostedThreads.containsKey(tid)) {
                mBoostedThreads.put(tid, Process.getThreadPriority(tid));
            }
            if (boostLevel >= BOOST_LEVEL_HEAVY_THRESHOLD) {
                Process.setThreadScheduler(tid, SCHED_RR_RESET_ON_FORK, SCHED_REALTIME_PRIORITY);
            } else {
                Process.setThreadPriority(tid, Process.THREAD_PRIORITY_TOP_APP_BOOST);
            }
            Process.setThreadAffinity(tid, (int) mClusterManager.getBoostMask());
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to boost thread " + tid + ": " + t.getMessage());
        }
    }

    public synchronized void restoreProcess(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }

        int currentCount = mBoostPidCountMap.getOrDefault(pid, MIN_REF_COUNT) - 1;
        if (currentCount <= MIN_REF_COUNT) {
            mBoostPidCountMap.remove(pid);
            mPerfEnhancer.setTaskBoost(pid, BOOST_LEVEL_RESTORE);

            List<Integer> hwuiTids = findHwuiThreadTids(pid);
            for (int tid : hwuiTids) {
                restoreThread(tid);
            }
            restoreThread(pid);
        } else {
            mBoostPidCountMap.put(pid, currentCount);
        }
    }

    public synchronized void restoreThread(int tid) {
        if (tid <= INVALID_PID) {
            return;
        }

        Integer origPrio = mBoostedThreads.remove(tid);
        if (origPrio != null) {
            try {
                Process.setThreadScheduler(tid, Process.SCHED_OTHER, SCHED_DEFAULT_PRIORITY);
                Process.setThreadPriority(tid, origPrio);
                Process.setThreadAffinity(tid, (int) mClusterManager.getAllMask());
            } catch (Throwable t) {
                Slog.w(TAG, "Failed to restore thread " + tid + ": " + t.getMessage());
            }
        }
    }

    private List<Integer> findHwuiThreadTids(int pid) {
        List<Integer> result = new ArrayList<>();
        File taskDir = new File(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX);
        if (!taskDir.exists() || !taskDir.isDirectory()) {
            return result;
        }
        File[] threads = taskDir.listFiles();
        if (threads == null) {
            return result;
        }
        for (File t : threads) {
            try {
                int tid = Integer.parseInt(t.getName());
                File commFile = new File(t, FILE_NAME_COMM);
                if (commFile.exists()) {
                    try (BufferedReader r = new BufferedReader(new FileReader(commFile))) {
                        String comm = r.readLine();
                        if (comm != null) {
                            String trimmed = comm.trim();
                            if (trimmed.equals(THREAD_NAME_RENDER_THREAD) || trimmed.startsWith("hwuiTask") || trimmed.startsWith("HwuiTask")) {
                                result.add(tid);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
