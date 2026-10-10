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
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.max
import kotlin.math.min

object AxCpuClusterManager {
    private const val TAG = "AxCpuClusterManager"

    const val PATH_CPU_POSSIBLE = "/sys/devices/system/cpu/possible"
    const val PATH_CPU_SYSFS_PREFIX = "/sys/devices/system/cpu/cpu"
    const val PATH_CPUINFO_MAX_FREQ_SUFFIX = "/cpufreq/cpuinfo_max_freq"
    const val RANGE_DELIMITER = "-"

    const val DEFAULT_CORE_COUNT = 8
    const val SINGLE_CLUSTER = 1
    const val DUAL_CLUSTER = 2
    const val TRI_CLUSTER = 3
    const val CLUSTER_INDEX_LITTLE = 0
    const val CLUSTER_INDEX_BIG = 1
    const val CLUSTER_INDEX_PRIME = 2
    const val FALLBACK_FREQ_BASE = 100000L

    const val AFFINITY_LITTLE = 1
    const val AFFINITY_BIG = 2
    const val AFFINITY_PRIME = 3
    const val AFFINITY_BOOST = 4
    const val AFFINITY_ALL = 5
    const val AFFINITY_MID = 6

    data class ClusterInfo(
        val clusterId: Int,
        val maxFreq: Long,
        val cpus: List<Int>,
        val mask: Long,
    )

    private data class TopologyData(
        val numCores: Int,
        val clusters: List<ClusterInfo>,
        val littleMask: Long,
        val midMask: Long,
        val bigMask: Long,
        val primeMask: Long,
        val boostMask: Long,
        val allMask: Long,
        val efficiencyPoolMask: Long,
        val performancePoolMask: Long,
        val uiMask: Long,
    )

    private data class MaskSet(
        val little: Long,
        val mid: Long,
        val prime: Long,
        val big: Long,
        val boost: Long,
        val effPool: Long,
        val perfPool: Long,
        val ui: Long,
    )

    private val topology: TopologyData = detectTopology()

    val numCores: Int
        get() = topology.numCores

    val clusters: List<ClusterInfo>
        get() = topology.clusters

    val littleMask: Long
        get() = topology.littleMask

    val midMask: Long
        get() = topology.midMask

    val bigMask: Long
        get() = topology.bigMask

    val primeMask: Long
        get() = topology.primeMask

    val boostMask: Long
        get() = topology.boostMask

    val allMask: Long
        get() = topology.allMask

    val efficiencyPoolMask: Long
        get() = topology.efficiencyPoolMask

    val performancePoolMask: Long
        get() = topology.performancePoolMask

    val uiMask: Long
        get() = topology.uiMask

    private fun detectTopology(): TopologyData {
        val cores = detectCoreCount()
        val clusters = buildClusters(cores)
        val allMask = clusters.fold(0L) { acc, c -> acc or c.mask }
        val masks = resolveMasks(clusters, allMask)
        logTopology(cores, clusters.size, masks)
        return TopologyData(
            cores,
            clusters,
            masks.little,
            masks.mid,
            masks.big,
            masks.prime,
            masks.boost,
            allMask,
            masks.effPool,
            masks.perfPool,
            masks.ui,
        )
    }

    private fun buildClusters(cores: Int): List<ClusterInfo> {
        val freqMap = HashMap<Long, MutableList<Int>>()
        for (cpu in 0 until cores) {
            freqMap.getOrPut(readCpuMaxFreq(cpu)) { ArrayList() }.add(cpu)
        }
        return freqMap.entries
            .sortedBy { it.key }
            .mapIndexed { index, entry ->
                val mask = entry.value.fold(0L) { acc, cpu -> acc or (1L shl cpu) }
                ClusterInfo(index, entry.key, entry.value.toList(), mask)
            }
    }

    private fun resolveMasks(clusters: List<ClusterInfo>, allMask: Long): MaskSet {
        val ui = resolveUi(clusters, allMask)
        when (clusters.size) {
            SINGLE_CLUSTER -> {
                val m = clusters[CLUSTER_INDEX_LITTLE].mask
                return MaskSet(m, m, m, m, m, allMask, allMask, ui)
            }
            DUAL_CLUSTER -> {
                val l = clusters[CLUSTER_INDEX_LITTLE].mask
                val b = clusters[CLUSTER_INDEX_BIG].mask
                return MaskSet(l, b, b, b, b, l, b, ui)
            }
            TRI_CLUSTER -> {
                val l = clusters[CLUSTER_INDEX_LITTLE].mask
                val m = clusters[CLUSTER_INDEX_BIG].mask
                val p = clusters[CLUSTER_INDEX_PRIME].mask
                return MaskSet(
                    l,
                    m,
                    p,
                    m or p,
                    m or p,
                    resolveEffPool(l, m),
                    allMask and resolveEffPool(l, m).inv(),
                    ui,
                )
            }
            else -> {
                val l = clusters[CLUSTER_INDEX_LITTLE].mask
                val m = clusters[1].mask
                val p = clusters[clusters.size - 1].mask
                val low = clusters[0].mask or clusters[1].mask
                val high = clusters.drop(2).fold(0L) { acc, c -> acc or c.mask }
                return MaskSet(l, m, p, high, high, low, high, ui)
            }
        }
    }

    private fun resolveUi(clusters: List<ClusterInfo>, allMask: Long): Long {
        when {
            clusters.size >= 4 -> {
                return clusters[0].mask or clusters[1].mask
            }
            clusters.size == TRI_CLUSTER -> {
                val little = clusters[CLUSTER_INDEX_LITTLE].mask
                val mid = clusters[CLUSTER_INDEX_BIG].mask
                val smallCpus = getLowestNBits(little, min(2, little.countOneBits()))
                val bigCpus = getLowestNBits(mid, min(2, mid.countOneBits()))
                return smallCpus or bigCpus
            }
            clusters.size == DUAL_CLUSTER -> {
                val little = clusters[CLUSTER_INDEX_LITTLE].mask
                val big = clusters[CLUSTER_INDEX_BIG].mask
                val smallCpus = getLowestNBits(little, min(2, little.countOneBits()))
                val bigCpus = getLowestNBits(big, min(2, big.countOneBits()))
                return smallCpus or bigCpus
            }
            else -> return allMask
        }
    }

    private fun resolveEffPool(little: Long, mid: Long): Long {
        if (little.countOneBits() >= 3) return little
        return little or getLowestNBits(mid, max(1, mid.countOneBits() / 2))
    }

    private fun logTopology(cores: Int, clusterCount: Int, masks: MaskSet) {
        Slog.i(
            TAG,
            "CPU Topology detected: $cores cores, $clusterCount clusters. " +
                "Little: 0x${masks.little.toString(16)}, " +
                "Mid: 0x${masks.mid.toString(16)}, " +
                "Big: 0x${masks.big.toString(16)}, " +
                "Prime: 0x${masks.prime.toString(16)}, " +
                "Boost: 0x${masks.boost.toString(16)}, " +
                "EffPool: 0x${masks.effPool.toString(16)}, " +
                "PerfPool: 0x${masks.perfPool.toString(16)}, " +
                "Ui: 0x${masks.ui.toString(16)}",
        )
    }

    private fun detectCoreCount(): Int {
        val runtimeCores = Runtime.getRuntime().availableProcessors()
        val fallback = if (runtimeCores > 0) runtimeCores else DEFAULT_CORE_COUNT
        val line = readFirstLine(File(PATH_CPU_POSSIBLE)) ?: return fallback
        if (!line.contains(RANGE_DELIMITER)) return fallback
        return runCatching { line.trim().split(RANGE_DELIMITER)[1].toInt() + 1 }
            .getOrDefault(fallback)
    }

    private fun readCpuMaxFreq(cpu: Int): Long {
        val value =
            readFirstLine(File("$PATH_CPU_SYSFS_PREFIX$cpu$PATH_CPUINFO_MAX_FREQ_SUFFIX"))
                ?: return (cpu + 1) * FALLBACK_FREQ_BASE
        return runCatching { value.trim().toLong() }.getOrDefault((cpu + 1) * FALLBACK_FREQ_BASE)
    }

    private fun readFirstLine(file: File): String? =
        try {
            BufferedReader(FileReader(file)).use { it.readLine() }
        } catch (e: Exception) {
            null
        }

    @JvmStatic fun getNumClusters(): Int = topology.clusters.size

    @JvmStatic
    fun getMaskForType(affinityType: Int): Long {
        if (affinityType == AFFINITY_LITTLE) return littleMask.takeIf { it != 0L } ?: allMask
        if (affinityType == AFFINITY_PRIME) {
            return primeMask.takeIf { it != 0L } ?: bigMask.takeIf { it != 0L } ?: allMask
        }
        if (affinityType == AFFINITY_MID) {
            return midMask.takeIf { it != 0L } ?: bigMask.takeIf { it != 0L } ?: allMask
        }
        if (affinityType == 0 || affinityType == AFFINITY_BIG || affinityType == AFFINITY_BOOST) {
            return boostMask.takeIf { it != 0L } ?: allMask
        }
        return allMask
    }

    @JvmStatic
    fun getLowestNBits(mask: Long, count: Int): Long {
        if (count <= 0 || mask == 0L) return 0L
        val result =
            (0 until 64)
                .asSequence()
                .filter { (mask shr it) and 1L != 0L }
                .take(count)
                .fold(0L) { acc, bit -> acc or (1L shl bit) }
        return if (result != 0L) result else mask
    }

    @JvmStatic
    fun toCpusetString(mask: Long): String {
        if (mask == 0L) return "0"
        val bits = (0 until 64).filter { (mask shr it) and 1L != 0L }
        if (bits.isEmpty()) return "0"
        val ranges = ArrayList<String>()
        var start = bits[0]
        var prev = bits[0]
        for (bit in bits.drop(1)) {
            if (bit != prev + 1) {
                ranges.add(if (start == prev) "$start" else "$start-$prev")
                start = bit
            }
            prev = bit
        }
        ranges.add(if (start == prev) "$start" else "$start-$prev")
        return ranges.joinToString(",")
    }

    @JvmStatic fun getAllCpusString(): String = toCpusetString(allMask)

    @JvmStatic fun getLittleCpusString(): String = toCpusetString(littleMask)

    @JvmStatic fun getBigCpusString(): String = toCpusetString(bigMask)

    @JvmStatic fun getPrimeCpusString(): String = toCpusetString(primeMask)

    @JvmStatic
    fun getBoostCpusString(): String =
        toCpusetString(if (performancePoolMask != 0L) performancePoolMask else boostMask)

    @JvmStatic
    fun getRestrictedBackgroundCpusString(): String {
        val poolCount = efficiencyPoolMask.countOneBits()
        if (poolCount <= 0) return toCpusetString(getLowestNBits(allMask, max(1, numCores / 4)))
        return toCpusetString(getLowestNBits(efficiencyPoolMask, max(1, poolCount * 3 / 4)))
    }

    @JvmStatic
    fun getRestrictedDex2oatCpusString(): String {
        val poolCount = efficiencyPoolMask.countOneBits()
        if (poolCount <= 0) return toCpusetString(getLowestNBits(allMask, 1))
        return toCpusetString(getLowestNBits(efficiencyPoolMask, max(1, poolCount / 3)))
    }

    @JvmStatic fun getSystemBackgroundCpusString(): String = toCpusetString(allMask)

    @JvmStatic fun getRestrictedSystemBgCpusString(): String = toCpusetString(allMask)

    @JvmStatic
    fun getBackgroundCpusString(): String =
        toCpusetString(if (efficiencyPoolMask != 0L) efficiencyPoolMask else allMask)

    @JvmStatic
    fun getUiCpusString(): String =
        toCpusetString(if (uiMask != 0L) uiMask else allMask)

    @JvmStatic
    fun getForegroundCpusString(): String {
        if (clusters.size >= 3 && primeMask.countOneBits() == 1) {
            return toCpusetString(allMask and primeMask.inv())
        }
        return toCpusetString(allMask)
    }

    @JvmStatic fun getTopAppCpusString(): String = toCpusetString(allMask)

    @JvmStatic
    fun getAxForegroundCpusString(): String =
        toCpusetString(if (performancePoolMask != 0L) performancePoolMask else boostMask)

    @JvmStatic
    fun getAxForegroundInputCpusString(): String =
        toCpusetString(if (efficiencyPoolMask != 0L) efficiencyPoolMask else allMask)

    @JvmStatic
    fun getLittleMinCpusString(): String =
        toCpusetString(getLowestNBits(littleMask, max(1, min(2, numCores / 4))))

    @JvmStatic fun getMidPerformanceCpusString(): String = toCpusetString(resolveMidPerfMask())

    private fun resolveMidPerfMask(): Long {
        if (clusters.size >= 3 && midMask != 0L) return midMask
        if (clusters.size == DUAL_CLUSTER) {
            return getLowestNBits(bigMask, max(1, bigMask.countOneBits() / 2))
        }
        return getLowestNBits(allMask, max(1, numCores / 2))
    }

    @JvmStatic fun getHighPerformanceCpusString(): String = toCpusetString(resolveHighPerfMask())

    private fun resolveHighPerfMask(): Long {
        if (clusters.size >= 3) {
            val topBig = bigMask and primeMask.inv()
            return if (topBig != 0L) topBig else bigMask
        }
        if (clusters.size == DUAL_CLUSTER) {
            val bigCount = bigMask.countOneBits()
            val shift = bigCount - max(1, (bigCount + 1) / 2)
            return (bigMask shr shift) shl shift
        }
        return allMask
    }

    @JvmStatic
    fun maskToCores(mask: Long): IntArray =
        (0 until 64).filter { (mask shr it) and 1L != 0L }.toIntArray()
}
