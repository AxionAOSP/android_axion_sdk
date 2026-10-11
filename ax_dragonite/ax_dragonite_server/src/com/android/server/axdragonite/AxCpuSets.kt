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

import kotlin.math.max
import kotlin.math.min

object AxCpuSets {
    const val CPUSET_ALL = "all"
    const val CPUSET_FOREGROUND = "foreground"
    const val CPUSET_UI = "ui"
    const val CPUSET_RESTRICTED_BG = "restricted_bg"
    const val CPUSET_RESTRICTED_FG = "restricted_fg"
    const val CPUSET_LITTLE_MIN = "little_min"
    const val CPUSET_BOOST = "boost"
    const val CPUSET_BOOST_MASK_DEC = "boost_mask_dec"

    const val DEFAULT_SF_BOOST_AFFINITY = 156
    const val DEFAULT_SF_RESTORE_AFFINITY = 255
    private const val MASK_32_BIT = 0xFFFFFFFFL
    private const val DEX_RESTORE_MIN_THREADS = 2
    private const val DEX_RESTORE_MAX_THREADS = 8
    private const val DEX_LIMIT_MIN_THREADS = 1
    private const val DEX_LIMIT_MAX_THREADS = 4
    private const val CORES_DIVISOR_RESTORE = 2
    private const val CORES_DIVISOR_LIMIT = 4

    @JvmStatic
    fun resolve(cpuset: String?): String {
        val topo = AxCpuClusterManager.topology
        if (cpuset.isNullOrEmpty()) return topo.all.toString()
        return when (cpuset) {
            CPUSET_ALL -> topo.all.toString()
            CPUSET_FOREGROUND -> topo.foreground.toString()
            CPUSET_UI -> topo.ui.toString()
            CPUSET_RESTRICTED_BG -> topo.restrictedBackground.toString()
            CPUSET_RESTRICTED_FG -> (topo.restrictedForeground ?: topo.foreground).toString()
            CPUSET_LITTLE_MIN -> topo.littleMin.toString()
            CPUSET_BOOST -> topo.boost.toString()
            CPUSET_BOOST_MASK_DEC -> topo.boost.mask.toString()
            else -> cpuset
        }
    }

    @JvmStatic
    fun resolveCores(cpuset: String?): IntArray? {
        if (cpuset.isNullOrEmpty()) return null
        if (cpuset == CPUSET_BOOST_MASK_DEC || cpuset == CPUSET_BOOST) {
            return AxCpuClusterManager.topology.boost.cores
        }
        return parseCores(resolve(cpuset))
    }

    private fun parseCores(cpuset: String): IntArray? {
        val maxCore = AxCpuClusterManager.topology.numCores
        val cores = ArrayList<Int>()
        for (range in cpuset.split(",")) {
            collectRange(range.trim(), maxCore, cores)
        }
        return cores.takeIf { it.isNotEmpty() }?.toIntArray()
    }

    private fun collectRange(range: String, maxCore: Int, out: MutableList<Int>) {
        if (range.isEmpty()) return
        if (!range.contains(AxCpuClusterManager.RANGE_DELIMITER)) {
            range.toIntOrNull()?.takeIf { it in 0 until maxCore }?.let(out::add)
            return
        }
        val bounds = range.split(AxCpuClusterManager.RANGE_DELIMITER)
        if (bounds.size != 2) return
        val start = bounds[0].trim().toIntOrNull() ?: return
        val end = bounds[1].trim().toIntOrNull() ?: return
        for (cpu in start..end) {
            if (cpu in 0 until maxCore) out.add(cpu)
        }
    }

    private fun clampThreads(cores: Int, divisor: Int, minThreads: Int, maxThreads: Int): String =
        max(minThreads, min(maxThreads, cores / divisor)).toString()

    @JvmStatic
    fun dexRestoreThreads(): String =
        clampThreads(
            AxCpuClusterManager.topology.numCores,
            CORES_DIVISOR_RESTORE,
            DEX_RESTORE_MIN_THREADS,
            DEX_RESTORE_MAX_THREADS,
        )

    @JvmStatic
    fun dexLimitThreads(): String =
        clampThreads(
            AxCpuClusterManager.topology.numCores,
            CORES_DIVISOR_LIMIT,
            DEX_LIMIT_MIN_THREADS,
            DEX_LIMIT_MAX_THREADS,
        )

    private fun maskToAffinity(mask: Long, fallback: Int): Int =
        if (mask == 0L) fallback else (mask and MASK_32_BIT).toInt()

    @JvmStatic
    fun sfBoostAffinity(): Int =
        maskToAffinity(AxCpuClusterManager.topology.boost.mask, DEFAULT_SF_BOOST_AFFINITY)

    @JvmStatic
    fun sfRestoreAffinity(): Int =
        maskToAffinity(AxCpuClusterManager.topology.all.mask, DEFAULT_SF_RESTORE_AFFINITY)

    @JvmStatic
    fun resolveMask(decimalMask: String?): String {
        if (CPUSET_BOOST_MASK_DEC == decimalMask) return AxCpuClusterManager.topology.boost.mask.toString()
        return decimalMask ?: ""
    }
}
