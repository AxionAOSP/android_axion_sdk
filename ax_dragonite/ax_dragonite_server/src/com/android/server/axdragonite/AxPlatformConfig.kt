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

import android.os.Process
import android.util.Slog
import java.io.File
import java.util.HashMap

class AxPlatformConfig {
    companion object {
        const val KEY_TOP_APP = "ta"
        const val KEY_FOREGROUND = "fg"
        const val KEY_BACKGROUND = "bg"
        const val KEY_UI = "ui"
        const val KEY_SYSTEM_BACKGROUND = "sbg"
        const val KEY_RESTRICTED = "rest"
        const val KEY_FOREGROUND_INPUT = "axfg_input"
        const val KEY_DEX_INPUT = "dex_input"
        const val KEY_BACKGROUND_INPUT = "bg_input"
        const val KEY_DURATION_INPUT = "duration_input"
        const val KEY_DEX_RESTORE_THREADS = "dex_restore_threads"
        const val KEY_DEX_THREADS = "dex_threads"
        const val KEY_INSPECT_DURATION = "inspect_duration"
        const val KEY_SURFACE_AFFINITY = "sf_affinity"
        const val KEY_SWAP_AFFINITY_BOOST = "kswapd_affinity_boost"
        const val KEY_AXFG = "axfg"
        const val KEY_DEX = "DEX"

        const val KEY_CPUCTL_CPU_SHARES_SYS = "cpuctl_cpu_shares_sys"
        const val KEY_CPUCTL_CPU_SHARES_SBG = "cpuctl_cpu_shares_sbg"
        const val KEY_CPUCTL_CPU_SHARES_TA = "cpuctl_cpu_shares_ta"
        const val KEY_CPUCTL_CPU_SHARES_DEX = "cpuctl_cpu_shares_dex"
        const val KEY_CPUCTL_CPU_SHARES_FG = "cpuctl_cpu_shares_fg"
        const val KEY_CPUCTL_CPU_SHARES_RT = "cpuctl_cpu_shares_rt"
        const val KEY_CPUCTL_CPU_SHARES_BG = "cpuctl_cpu_shares_bg"
        const val KEY_CPUCTL_CPU_SHARES_REST = "cpuctl_cpu_shares_rest"
        const val KEY_CPUCTL_CPU_SHARES_AXFG = "cpuctl_cpu_shares_axfg"
        const val KEY_CPUCTL_CPU_SHARES_FW = "cpuctl_cpu_shares_fw"

        const val DEFAULT_DURATION_INPUT = 800
        const val DEFAULT_INSPECT_DURATION = 900000
        val DEFAULT_CPU_AFFINITY = AxCpuSets.CPUSET_ALL

        const val GROUP_ROOT = "root"
        const val GROUP_DEX = "dex"

        private const val TAG = "AxPlatformConfig"
        private val PROC_FILE_BUFFER = intArrayOf(4096)
        private const val DEFAULT_CPU_SHARES = 1024
        private const val BOOSTED_CPU_SHARES = 2048
        const val UCLAMP_RESTORE_MIN = "0"
        const val UCLAMP_RESTORE_MAX = "100"
        private const val PROC_DIR = "/proc/"
        private const val FILE_CMDLINE = "cmdline"
        private const val FILE_COMM = "comm"
        private val FALLBACK_BG_CPUS = AxCpuSets.CPUSET_BACKGROUND
        private val FALLBACK_SBG_CPUS = AxCpuSets.CPUSET_RESTRICTED_BG

        private val cpusetGroups =
            mapOf(
                KEY_TOP_APP to AxCmdTable.GROUP_TOP_APP,
                KEY_FOREGROUND to AxCmdTable.GROUP_FOREGROUND,
                KEY_BACKGROUND to AxCmdTable.GROUP_BACKGROUND,
                KEY_SYSTEM_BACKGROUND to AxCmdTable.GROUP_SYSTEM_BACKGROUND,
                KEY_RESTRICTED to AxCmdTable.GROUP_RESTRICTED,
                GROUP_DEX to AxCmdTable.GROUP_DEX2OAT,
            )

        private val cpusharesKeys =
            mapOf(
                AxCmdTable.CMD_CPUCTL_SYS_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_SYS,
                AxCmdTable.CMD_CPUCTL_SBG_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_SBG,
                AxCmdTable.CMD_CPUCTL_TA_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_TA,
                AxCmdTable.CMD_CPUCTL_DEX_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_DEX,
                AxCmdTable.CMD_CPUCTL_FG_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_FG,
                AxCmdTable.CMD_CPUCTL_RT_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_RT,
                AxCmdTable.CMD_CPUCTL_BG_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_BG,
                AxCmdTable.CMD_CPUCTL_RESTRICTED_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_REST,
                AxCmdTable.CMD_CPUCTL_AXFG_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_AXFG,
                AxCmdTable.CMD_CPUCTL_FW_CPU_SHARES to KEY_CPUCTL_CPU_SHARES_FW,
            )

        private val paramCmds =
            setOf(
                AxCmdTable.CMD_THREAD_BOOST,
                AxCmdTable.CMD_THREAD_AFFINITY,
                AxCmdTable.CMD_CPUCTL_SYS_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_SBG_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_TA_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_DEX_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_FG_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_RT_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_BG_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_RESTRICTED_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_AXFG_CPU_SHARES,
                AxCmdTable.CMD_CPUCTL_FW_CPU_SHARES,
            )

        private fun cgroupFile(baseDir: String, group: String?, fileName: String): File? {
            if (group == GROUP_ROOT) return File("$baseDir${AxCmdTable.PATH_SEP}$fileName")
            val folder = cpusetGroups[group] ?: return null
            return File("$baseDir${AxCmdTable.PATH_SEP}$folder${AxCmdTable.PATH_SEP}$fileName")
        }

        @JvmStatic
        fun getCpuctlProcs(group: String?): File? =
            cgroupFile(AxCmdTable.CPUCTL_DIR, group, AxCmdTable.FILE_CGROUP_PROCS)

        @JvmStatic
        fun getCpusetProcs(group: String?): File? =
            cgroupFile(AxCmdTable.CPUSET_DIR, group, AxCmdTable.FILE_CGROUP_PROCS)

        @JvmStatic
        fun getCpusetTasks(group: String?): File? =
            cgroupFile(AxCmdTable.CPUSET_DIR, group, AxCmdTable.FILE_TASKS)

        @JvmStatic
        fun parseAffinityCores(affinity: String?): IntArray? = AxCpuSets.resolveCores(affinity)

        private fun readProcField(pid: Int, field: String): String {
            val out = arrayOfNulls<String>(1)
            val path = "$PROC_DIR$pid${AxCmdTable.PATH_SEP}$field"
            if (!Process.readProcFile(path, PROC_FILE_BUFFER, out, null, null)) return ""
            return out[0]?.trim() ?: ""
        }

        @JvmStatic fun getProcessCmdLine(pid: Int): String = readProcField(pid, FILE_CMDLINE)

        @JvmStatic fun getProcessComm(pid: Int): String = readProcField(pid, FILE_COMM)

        @JvmStatic
        fun paramsFromUser(params: String?, cmdId: Int): String? {
            if (params.isNullOrEmpty()) return null
            if (cmdId in paramCmds) return params
            Slog.d(TAG, "no need params, return!")
            return null
        }

        @JvmStatic
        fun getDefaultUclampRestoreValue(cmdId: Int): String {
            if (
                cmdId in
                    AxCmdTable.CMD_CPUCTL_SYS_UCLAMP_MIN..AxCmdTable
                            .CMD_CPUCTL_RESTRICTED_UCLAMP_MAX
            ) {
                return if (cmdId % 2 == 0) UCLAMP_RESTORE_MIN else UCLAMP_RESTORE_MAX
            }
            return UCLAMP_RESTORE_MIN
        }
    }

    private val configMap = HashMap<String, String>()
    private var durationInput = DEFAULT_DURATION_INPUT
    private var inspectDuration = DEFAULT_INSPECT_DURATION

    init {
        initializeDefaults()
    }

    private fun initializeDefaults() {
        configMap.putAll(
            mapOf(
                KEY_TOP_APP to AxCpuSets.CPUSET_ALL,
                KEY_FOREGROUND to AxCpuSets.CPUSET_ALL,
                KEY_BACKGROUND to AxCpuSets.CPUSET_BACKGROUND,
                KEY_UI to AxCpuSets.CPUSET_UI,
                KEY_SYSTEM_BACKGROUND to AxCpuSets.CPUSET_BACKGROUND,
                KEY_RESTRICTED to AxCpuSets.CPUSET_ALL,
                KEY_AXFG to AxCpuSets.CPUSET_FOREGROUND,
                KEY_DEX to AxCpuSets.CPUSET_FOREGROUND,
                KEY_FOREGROUND_INPUT to AxCpuSets.CPUSET_BACKGROUND,
                KEY_DEX_INPUT to AxCpuSets.CPUSET_BACKGROUND,
                KEY_BACKGROUND_INPUT to AxCpuSets.CPUSET_RESTRICTED_BG,
                KEY_DURATION_INPUT to DEFAULT_DURATION_INPUT.toString(),
                KEY_DEX_RESTORE_THREADS to AxCpuSets.dexRestoreThreads(),
                KEY_DEX_THREADS to AxCpuSets.CPUSET_ALL,
                KEY_INSPECT_DURATION to DEFAULT_INSPECT_DURATION.toString(),
                KEY_SURFACE_AFFINITY to AxCpuSets.CPUSET_BOOST_MASK_DEC,
                KEY_SWAP_AFFINITY_BOOST to AxCpuSets.CPUSET_ALL,
            )
        )
        updateParsedValues()
    }

    private fun updateParsedValues() {
        durationInput = configMap[KEY_DURATION_INPUT]?.toIntOrNull() ?: DEFAULT_DURATION_INPUT
        inspectDuration = configMap[KEY_INSPECT_DURATION]?.toIntOrNull() ?: DEFAULT_INSPECT_DURATION
    }

    fun put(name: String, value: String) {
        configMap[name] = value
        if (name == KEY_DURATION_INPUT || name == KEY_INSPECT_DURATION) updateParsedValues()
    }

    fun get(name: String): String? = configMap[name]

    fun getDurationInput(): Int = durationInput

    fun getInspectDuration(): Int = inspectDuration

    private fun resolveConfig(key: String, fallback: String): String {
        val value = configMap[key]
        if (value.isNullOrBlank()) {
            return AxCpuSets.resolve(fallback.takeIf { it.isNotEmpty() } ?: DEFAULT_CPU_AFFINITY)
        }
        return AxCpuSets.resolve(value)
    }

    fun getSwapAffinityBoost(): String =
        resolveConfig(KEY_SWAP_AFFINITY_BOOST, DEFAULT_CPU_AFFINITY)

    fun getSurfaceAffinity(): String {
        val value = configMap[KEY_SURFACE_AFFINITY]
        if (value.isNullOrBlank()) return AxCpuSets.resolveMask(DEFAULT_CPU_AFFINITY)
        return AxCpuSets.resolveMask(value)
    }

    fun getOrDefault(key: String, fallback: String): String = configMap[key] ?: fallback

    fun getCpushares(cmd: Int): String {
        val key = cpusharesKeys[cmd] ?: return DEFAULT_CPU_SHARES.toString()
        return getOrDefault(key, BOOSTED_CPU_SHARES.toString())
    }

    fun getRestoreCpusetCpus(cmdId: Int): String {
        val key =
            when (cmdId) {
                AxCmdTable.CMD_CPUSET_TA_CPUS -> KEY_TOP_APP
                AxCmdTable.CMD_CPUSET_FG_CPUS -> KEY_FOREGROUND
                AxCmdTable.CMD_CPUSET_BG_CPUS -> KEY_BACKGROUND
                AxCmdTable.CMD_CPUSET_SBG_CPUS -> KEY_SYSTEM_BACKGROUND
                AxCmdTable.CMD_CPUSET_RESTRICTED_CPUS -> KEY_RESTRICTED
                else -> return ""
            }
        val fallback =
            if (cmdId == AxCmdTable.CMD_CPUSET_BG_CPUS) {
                FALLBACK_BG_CPUS
            } else if (cmdId == AxCmdTable.CMD_CPUSET_SBG_CPUS) {
                FALLBACK_SBG_CPUS
            } else {
                DEFAULT_CPU_AFFINITY
            }
        return AxCpuSets.resolve(configMap[key] ?: fallback)
    }
}
