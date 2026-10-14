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

        private val KNOWN_GAME_PACKAGES =
            setOf(
                "com.tencent.ig",
                "com.pubg.imobile",
                "com.pubg.krmobile",
                "com.vng.pubgmobile",
                "com.tencent.tmgp.pubgmhd",
                "com.miHoYo.GenshinImpact",
                "com.miHoYo.Yuanshen",
                "com.HoYoverse.hkrpgoversea",
                "com.miHoYo.hkrpg",
                "com.HoYoverse.Nap",
                "com.miHoYo.Nap",
                "com.activision.callofduty.shooter",
                "com.garena.game.codm",
                "com.activision.callofduty.warzone",
                "com.dts.freefireth",
                "com.dts.freefiremax",
                "com.mobile.legends",
                "com.riotgames.league.wildrift",
                "com.riotgames.league.wildrifttw",
                "com.levelinfinite.sgameGlobal",
                "com.tencent.tmgp.sgame",
                "com.roblox.client",
                "com.mojang.minecraftpe",
                "com.blizzard.diablo.immortal",
                "com.ea.gp.apexmobile",
                "com.gameloft.android.ANMP.GloftA9HM",
                "com.epicgames.fortnite",
            )

        @Volatile private var sInstance: AxNamedThreadAffinityFeature? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): AxNamedThreadAffinityFeature {
            return sInstance ?: AxNamedThreadAffinityFeature().also { sInstance = it }
        }
    }

    private val isSupported: Boolean = hasKernelNodes()
    private var lastPid: Int = -1
    private val modifiedTids = ArrayList<Int>()

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

    private fun isRenderThread(comm: String): Boolean =
        comm.startsWith("RenderThread", ignoreCase = true) ||
            comm.startsWith("UnityMain", ignoreCase = true) ||
            comm.startsWith("UnityGfx", ignoreCase = true) ||
            comm.startsWith("RHIThread", ignoreCase = true) ||
            comm.startsWith("GLThread", ignoreCase = true) ||
            comm.startsWith("GameThread", ignoreCase = true) ||
            comm.startsWith("MainThread", ignoreCase = true) ||
            comm.startsWith("Godot", ignoreCase = true)

    private fun isWorkerThread(comm: String): Boolean =
        comm.startsWith("Job.Worker", ignoreCase = true) ||
            comm.startsWith("TaskGraph", ignoreCase = true) ||
            comm == "Thread"

    fun applyNamedAffinityForPid(pid: Int) {
        if (pid <= 0) {
            Slog.w(TAG, "Invalid pid: $pid")
            return
        }

        val taskDir = File("/proc/$pid/task")
        val taskFiles = taskDir.listFiles() ?: return
        val rawCmdLine = AxPlatformConfig.getProcessCmdLine(pid)
        val packageName = rawCmdLine.substringBefore(':')
        val isKnownGame = KNOWN_GAME_PACKAGES.contains(packageName)

        val assignments = ArrayList<Pair<Int, String>>()
        var hasEngineThreads = false

        for (taskFile in taskFiles) {
            val tid = taskFile.name.toIntOrNull() ?: continue
            val comm = AxPlatformConfig.getProcessComm(tid)
            if (isRenderThread(comm)) {
                hasEngineThreads = true
                assignments.add(tid to comm)
            } else if (isWorkerThread(comm)) {
                hasEngineThreads = true
                assignments.add(tid to comm)
            }
        }

        if (!hasEngineThreads && !isKnownGame) return

        lastPid = pid
        modifiedTids.clear()

        val boostCores = AxCpuSets.resolveCores(AxCpuSets.CPUSET_BOOST)
        val uiCores = AxCpuSets.resolveCores(AxCpuSets.CPUSET_UI)
        val boostCpuset = AxCpuSets.resolve(AxCpuSets.CPUSET_BOOST)
        val uiCpuset = AxCpuSets.resolve(AxCpuSets.CPUSET_UI)

        if (isSupported) {
            writeNode(NODE_PID, pid.toString(), "pid")
            for ((_, comm) in assignments) {
                val targetCpuset = if (isRenderThread(comm)) boostCpuset else uiCpuset
                writeNode(NODE_AFFINITY, "$comm $targetCpuset", "policy")
            }
            return
        }

        for ((tid, comm) in assignments) {
            val targetCores = if (isRenderThread(comm)) boostCores else uiCores
            if (targetCores != null) {
                try {
                    Process.setThreadAffinity(tid, targetCores)
                    modifiedTids.add(tid)
                    if (DEBUG_LOG) {
                        Slog.d(TAG, "Set affinity for tid $tid ($comm) to ${targetCores.contentToString()}")
                    }
                } catch (e: Exception) {
                    Slog.w(TAG, "Failed to set affinity for tid $tid: ${e.message}")
                }
            }
        }

        if (boostCores != null) {
            try {
                Process.setThreadAffinity(pid, boostCores)
                modifiedTids.add(pid)
            } catch (e: Exception) {
                Slog.w(TAG, "Failed to set affinity for main thread $pid: ${e.message}")
            }
        }
    }

    fun resetAffinity() {
        if (isSupported) {
            writeNode(NODE_RESET, RESET_TRIGGER_VALUE, "reset")
            return
        }

        val allCores = AxCpuSets.resolveCores(AxCpuSets.CPUSET_ALL) ?: return
        for (tid in modifiedTids) {
            try {
                Process.setThreadAffinity(tid, allCores)
            } catch (e: Exception) {
                Slog.w(TAG, "Failed to reset affinity for tid $tid: ${e.message}")
            }
        }
        modifiedTids.clear()
        lastPid = -1
    }
}
