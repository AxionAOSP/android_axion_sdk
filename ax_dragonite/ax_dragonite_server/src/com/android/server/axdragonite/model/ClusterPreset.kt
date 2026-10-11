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

package com.android.server.axdragonite.model

data class ClusterPreset(
    val type: ClusterTopologyType,
    val uiMask: Long,
    val restrictedBgMask: Long,
    val boostMask: Long,
    val restrictedFgMask: Long? = null,
) {
    companion object {
        const val DEFAULT_UI_SMALL = 2
        const val DEFAULT_UI_BIG = 2
        const val DEFAULT_REST_SMALL = 2
        const val DEFAULT_REST_BIG = 1

        fun single(allMask: Long, restBgMask: Long, boostMask: Long): ClusterPreset =
            ClusterPreset(ClusterTopologyType.SINGLE_CLUSTER, allMask, restBgMask, boostMask)

        fun of(
            type: ClusterTopologyType,
            smallMask: Long,
            bigMask: Long,
            boostMask: Long,
            restrictedFgMask: Long? = null,
            uiSmall: Int = DEFAULT_UI_SMALL,
            uiBig: Int = DEFAULT_UI_BIG,
            restSmall: Int = DEFAULT_REST_SMALL,
            restBig: Int = DEFAULT_REST_BIG,
        ): ClusterPreset =
            ClusterPreset(
                type = type,
                uiMask = takeLowestBits(smallMask, uiSmall) or takeLowestBits(bigMask, uiBig),
                restrictedBgMask = takeLowestBits(smallMask, restSmall) or takeLowestBits(bigMask, restBig),
                boostMask = boostMask,
                restrictedFgMask = restrictedFgMask,
            )

        fun takeLowestBits(mask: Long, count: Int): Long {
            if (count <= 0 || mask == 0L) return 0L
            var remaining = count
            var temp = mask
            var result = 0L
            while (temp != 0L && remaining > 0) {
                val lsb = temp and -temp
                result = result or lsb
                temp = temp xor lsb
                remaining--
            }
            return result
        }
    }
}
