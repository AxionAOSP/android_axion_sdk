/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.systemui.qs.ax.ui.grid

import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun DensityScopeProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density =
        remember(configuration.densityDpi, configuration.fontScale) {
            Density(
                density = configuration.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT,
                fontScale = configuration.fontScale,
            )
        }
    val configContext =
        remember(context, configuration.densityDpi, configuration.fontScale) {
            if (context.resources.configuration.densityDpi != configuration.densityDpi) {
                val overrideConfig =
                    Configuration(configuration).apply {
                        densityDpi = configuration.densityDpi
                        fontScale = configuration.fontScale
                    }
                context.createConfigurationContext(overrideConfig)
            } else {
                context
            }
        }
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalContext provides configContext,
        content = content,
    )
}
