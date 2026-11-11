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

import kotlin.math.max

data class AxCpuProfile(
    val name: String,
    val littleCores: Int,
    val totalCores: Int,
    val ui: Int,
    val background: Int,
    val restrictedBackground: Int,
    val inputForeground: Int,
    val inputBackground: Int,
    val dexRestoreThreads: Int?,
    val dexLimitThreads: Int?,
    val restrictForegroundToLittle: Boolean = false,
) {
    fun scaledTo(little: Int, cores: Int): AxCpuProfile {
        fun byLittle(count: Int): Int = max(1, little * count / littleCores)
        fun byCores(count: Int): Int = cores * count / totalCores
        return copy(
            littleCores = little,
            totalCores = cores,
            ui = byLittle(ui),
            background = byLittle(background),
            restrictedBackground = byLittle(restrictedBackground),
            inputForeground = byLittle(inputForeground),
            inputBackground = byLittle(inputBackground),
            dexRestoreThreads = dexRestoreThreads?.let(::byCores),
            dexLimitThreads = dexLimitThreads?.let { max(1, byCores(it)) },
        )
    }

    companion object {
        val PROFILE_SINGLE =
            AxCpuProfile(
                name = "single",
                littleCores = 4,
                totalCores = 4,
                ui = 4,
                background = 4,
                restrictedBackground = 1,
                inputForeground = 4,
                inputBackground = 1,
                dexRestoreThreads = null,
                dexLimitThreads = null,
            )

        val PROFILE_4_4 =
            AxCpuProfile(
                name = "4_4",
                littleCores = 4,
                totalCores = 8,
                ui = 4,
                background = 4,
                restrictedBackground = 3,
                inputForeground = 4,
                inputBackground = 2,
                dexRestoreThreads = 3,
                dexLimitThreads = 1,
            )

        val PROFILE_6_2 =
            AxCpuProfile(
                name = "6_2",
                littleCores = 6,
                totalCores = 8,
                ui = 5,
                background = 4,
                restrictedBackground = 2,
                inputForeground = 6,
                inputBackground = 3,
                dexRestoreThreads = 8,
                dexLimitThreads = 1,
                restrictForegroundToLittle = true,
            )

        fun forLayout(little: Int, cores: Int): AxCpuProfile =
            when {
                little == cores -> PROFILE_SINGLE
                little == PROFILE_6_2.littleCores && cores == PROFILE_6_2.totalCores -> PROFILE_6_2
                else -> PROFILE_4_4
            }
    }
}
