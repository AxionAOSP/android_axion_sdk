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

import android.util.Slog
import com.android.server.axdragonite.model.AxCpuProfile
import com.android.server.axdragonite.model.ClusterInfo
import com.android.server.axdragonite.model.Cpuset
import com.android.server.axdragonite.model.TopologyData
import java.io.File
import kotlin.math.max
import kotlin.math.min

object AxCpuClusterManager {
    private const val TAG = "AxCpuClusterManager"

    const val PATH_CPU_POSSIBLE = "/sys/devices/system/cpu/possible"
    const val PATH_CPU_SYSFS_PREFIX = "/sys/devices/system/cpu/cpu"
    const val PATH_CPUINFO_MAX_FREQ_SUFFIX = "/cpufreq/cpuinfo_max_freq"
    const val RANGE_DELIMITER = "-"

    const val DEFAULT_CORE_COUNT = 8
    const val FALLBACK_FREQ_BASE = 100000L

    const val CORES_DEX2OAT_RESTRICTED = 1
    const val CORES_LITTLE_MIN_DEFAULT = 2
    const val CORES_DIVISOR_QUARTER = 4

    val topology: TopologyData = detectTopology()

    private fun detectTopology(): TopologyData {
        val cores = detectCoreCount()
        val topo = resolveTopology(cores, buildClusters(cores))
        logTopology(topo)
        return topo
    }

    private fun buildClusters(cores: Int): List<ClusterInfo> =
        (0 until cores)
            .groupBy(::readCpuMaxFreq)
            .entries
            .sortedBy { it.key }
            .mapIndexed { index, (freq, cpus) ->
                ClusterInfo(index, freq, cpus, cpus.fold(0L) { mask, cpu -> mask or (1L shl cpu) })
            }

    private fun resolveTopology(cores: Int, clusters: List<ClusterInfo>): TopologyData {
        val allMask = clusters.fold(0L) { mask, cluster -> mask or cluster.mask }
        val littleMask = clusters.firstOrNull()?.mask ?: 0L
        val little = littleMask.countOneBits()
        val profile = AxCpuProfile.forLayout(little, cores)
        val counts = profile.scaledTo(little, cores)
        fun littleCpus(count: Int) = Cpuset(takeLowestBits(littleMask, count))

        return TopologyData(
            numCores = cores,
            profile = profile,
            clusters = clusters,
            all = Cpuset(allMask),
            foreground = Cpuset(allMask),
            ui = littleCpus(counts.ui),
            restrictedBackground = littleCpus(counts.restrictedBackground),
            restrictedDex2oat = littleCpus(CORES_DEX2OAT_RESTRICTED),
            littleMin =
                littleCpus(max(1, min(CORES_LITTLE_MIN_DEFAULT, cores / CORES_DIVISOR_QUARTER))),
            boost = Cpuset((allMask and littleMask.inv()).takeIf { it != 0L } ?: allMask),
            background = littleCpus(counts.background),
            inputForeground = littleCpus(counts.inputForeground),
            inputBackground = littleCpus(counts.inputBackground),
            restrictedForeground = Cpuset(littleMask).takeIf { profile.restrictForegroundToLittle },
            dexRestoreThreads = counts.dexRestoreThreads,
            dexLimitThreads = counts.dexLimitThreads,
        )
    }

    private fun takeLowestBits(mask: Long, count: Int): Long {
        var taken = 0L
        repeat(count) { taken = taken or (mask and taken.inv()).takeLowestOneBit() }
        return taken
    }

    private fun logTopology(t: TopologyData) {
        val clusterSizes = t.clusters.joinToString("+") { it.cpus.size.toString() }
        Slog.i(
            TAG,
            "CPU Topology detected: ${t.numCores} cores, clusters $clusterSizes, profile: ${t.profile.name}. " +
                "Boost: 0x${t.boost.mask.toString(16)}, " +
                "Ui: 0x${t.ui.mask.toString(16)}, " +
                "RestrictedBg: 0x${t.restrictedBackground.mask.toString(16)}",
        )
    }

    private fun detectCoreCount(): Int {
        val runtimeCores = Runtime.getRuntime().availableProcessors().takeIf { it > 0 } ?: DEFAULT_CORE_COUNT
        val line = readFirstLine(File(PATH_CPU_POSSIBLE)) ?: return runtimeCores
        if (!line.contains(RANGE_DELIMITER)) return runtimeCores
        return runCatching { line.trim().substringAfter(RANGE_DELIMITER).toInt() + 1 }.getOrDefault(runtimeCores)
    }

    private fun readCpuMaxFreq(cpu: Int): Long =
        readFirstLine(File("$PATH_CPU_SYSFS_PREFIX$cpu$PATH_CPUINFO_MAX_FREQ_SUFFIX"))
            ?.trim()
            ?.toLongOrNull()
            ?: ((cpu + 1) * FALLBACK_FREQ_BASE)

    private fun readFirstLine(file: File): String? =
        runCatching { file.bufferedReader().use { it.readLine() } }.getOrNull()
}
