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

data class Cpuset(val mask: Long) {
    val cores: IntArray
        get() {
            val count = mask.countOneBits()
            if (count == 0) return intArrayOf()
            val result = IntArray(count)
            var idx = 0
            for (i in 0 until 64) {
                if ((mask and (1L shl i)) != 0L) {
                    result[idx++] = i
                }
            }
            return result
        }

    override fun toString(): String {
        if (mask == 0L) return "0"
        val ranges = ArrayList<String>()
        var start = -1
        var prev = -1
        for (i in 0 until 64) {
            if ((mask and (1L shl i)) != 0L) {
                if (start == -1) start = i
                prev = i
            } else if (start != -1) {
                ranges.add(if (start == prev) "$start" else "$start-$prev")
                start = -1
            }
        }
        if (start != -1) {
            ranges.add(if (start == prev) "$start" else "$start-$prev")
        }
        return ranges.joinToString(",")
    }
}
