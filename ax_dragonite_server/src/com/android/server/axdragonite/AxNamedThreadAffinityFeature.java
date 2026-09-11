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

    public static final String PATH_PROC_PREFIX = "/proc/";
    public static final String PATH_TASK_SUFFIX = "/task";
    public static final String PATH_COMM_SUFFIX = "/comm";

    public static final String PATH_NTA_PID = "/proc/ax_named_thread_affinity/pid";
    public static final String PATH_NTA_AFFINITY = "/proc/ax_named_thread_affinity/named_thread_affinity";
    public static final String PATH_NTA_RESET = "/proc/ax_named_thread_affinity/reset";

    public static final String VALUE_RESET_TRIGGER = "1";
    public static final int INVALID_PID = 0;

    private final AxCpuClusterManager mClusterManager;
    private final Map<String, Long> mDefaultCommRules = new HashMap<>();

    public AxNamedThreadAffinityFeature(AxCpuClusterManager clusterManager) {
        this.mClusterManager = clusterManager;
        initDefaultRules();
    }

    private void initDefaultRules() {
        mDefaultCommRules.put(COMM_RENDER_THREAD, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_CR_RENDERER_MAIN, mClusterManager.getBigMask());
        mDefaultCommRules.put(COMM_UNITY_MAIN, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_GL_THREAD, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_MAIN_THREAD, mClusterManager.getBoostMask());
        mDefaultCommRules.put(COMM_AUDIO_TRACK, mClusterManager.getLittleMask());
    }

    public void applyNamedAffinityForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }

        File taskDir = new File(PATH_PROC_PREFIX + pid + PATH_TASK_SUFFIX);
        if (!taskDir.exists() || !taskDir.isDirectory()) {
            return;
        }

        File[] threads = taskDir.listFiles();
        if (threads == null) {
            return;
        }

        for (File threadDir : threads) {
            if (!threadDir.isDirectory()) {
                continue;
            }
            try {
                int tid = Integer.parseInt(threadDir.getName());
                String comm = readComm(tid);
                if (comm != null) {
                    Long mask = mDefaultCommRules.get(comm.trim());
                    if (mask != null) {
                        setThreadAffinity(tid, mask);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        AxPerfEnhancer.writeNode(PATH_NTA_PID, String.valueOf(pid));
        for (Map.Entry<String, Long> entry : mDefaultCommRules.entrySet()) {
            String rule = entry.getKey() + " 0x" + Long.toHexString(entry.getValue());
            AxPerfEnhancer.writeNode(PATH_NTA_AFFINITY, rule);
        }
    }

    public void resetAffinityForPid(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
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
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}
