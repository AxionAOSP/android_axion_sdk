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

import android.os.FileUtils
import android.os.Process
import android.os.SystemProperties
import android.util.Slog
import java.io.File
import java.io.IOException
import java.util.HashMap

class AxNamedThreadAffinityFeature {
    companion object {
        private const val TAG = "AxNamedThreadAffinityFeature"
        private const val PROP_DEBUG = "persist.sys.ax_named_thread_affinity.debug.log"
        private val DEBUG_LOG = SystemProperties.getBoolean(PROP_DEBUG, false)

        private const val PROC_DIR = "/proc/ax_named_thread_affinity/"
        private const val NODE_PID = "pid"
        private const val NODE_AFFINITY = "named_thread_affinity"
        private const val NODE_RESET = "reset"
        private const val RESET_TRIGGER_VALUE = "1"

        private const val PKG_PUBG_MOBILE = "com.tencent.ig"
        private const val PKG_HONKAI_STAR_RAIL = "com.HoYoverse.hkrpgoversea"
        private const val THREAD_GAME = "Thread"
        private const val THREAD_RENDER = "RenderThread"
        private const val THREAD_UNITY_MAIN = "UnityMain"
        private const val THREAD_UNITY_GFX = "UnityGfxDeviceW"

        private val PACKAGE_ACTIONS: Map<String, List<NamedAffinity>> =
            mapOf(
                PKG_PUBG_MOBILE to
                    listOf(
                        NamedAffinity(THREAD_GAME, AxCpuSets.CPUSET_UI),
                        NamedAffinity(THREAD_RENDER, AxCpuSets.CPUSET_PERF_MID),
                    ),
                PKG_HONKAI_STAR_RAIL to
                    listOf(
                        NamedAffinity(THREAD_UNITY_MAIN, AxCpuSets.CPUSET_PERF_MID),
                        NamedAffinity(THREAD_UNITY_GFX, AxCpuSets.CPUSET_PERF_HIGH),
                    ),
            )

        @Volatile private var sInstance: AxNamedThreadAffinityFeature? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): AxNamedThreadAffinityFeature {
            return sInstance ?: AxNamedThreadAffinityFeature().also { sInstance = it }
        }
    }

    private data class NamedAffinity(val threadName: String, val affinity: String)

    private val isSupported: Boolean = hasKernelNodes()

    private fun hasKernelNodes(): Boolean =
        File("$PROC_DIR$NODE_PID").exists() &&
            File("$PROC_DIR$NODE_AFFINITY").exists() &&
            File("$PROC_DIR$NODE_RESET").exists()

    private fun writeNode(node: String, value: String, what: String): Boolean {
        if (!isSupported) {
            if (DEBUG_LOG) Slog.d(TAG, "Failed to set $what, due to not support")
            return false
        }
        return try {
            FileUtils.stringToFile(File("$PROC_DIR$node"), value)
            if (DEBUG_LOG) Slog.d(TAG, "Set $what: $value")
            true
        } catch (e: IOException) {
            Slog.e(TAG, "Failed to set $what $value: ${e.message}")
            false
        }
    }

    fun applyNamedAffinityForPid(pid: Int) {
        if (pid <= 0) {
            Slog.w(TAG, "Invalid pid: $pid")
            return
        }
        val packageName = AxPlatformConfig.getProcessCmdLine(pid)
        val actions = PACKAGE_ACTIONS[packageName] ?: return
        if (isSupported) {
            for (action in actions) {
                writeNode(
                    NODE_AFFINITY,
                    "${action.threadName} ${AxCpuSets.resolve(action.affinity)}",
                    "policy",
                )
                writeNode(NODE_PID, pid.toString(), "pid")
            }
            return
        }
        applyUserspaceAffinity(pid, actions)
    }

    private fun collectTargetAffinities(actions: List<NamedAffinity>): Map<String, IntArray> {
        val targets = HashMap<String, IntArray>()
        for (action in actions) {
            AxCpuSets.resolveCores(action.affinity)?.let { targets[action.threadName] = it }
        }
        return targets
    }

    private fun applyUserspaceAffinity(pid: Int, actions: List<NamedAffinity>) {
        if (pid <= 0) return
        val targets = collectTargetAffinities(actions)
        if (targets.isEmpty()) return
        val taskFiles = File("/proc/$pid/task").listFiles() ?: return
        for (taskFile in taskFiles) {
            val tid = taskFile.name.toIntOrNull() ?: continue
            val cores = targets[AxPlatformConfig.getProcessComm(tid)] ?: continue
            try {
                Process.setThreadAffinity(tid, cores)
                if (DEBUG_LOG) {
                    Slog.d(TAG, "Userspace set affinity for tid $tid to ${cores.contentToString()}")
                }
            } catch (e: Exception) {
                Slog.w(TAG, "Failed to set affinity for tid $tid: ${e.message}")
            }
        }
    }

    fun resetAffinity() {
        writeNode(NODE_RESET, RESET_TRIGGER_VALUE, "reset")
    }
}
