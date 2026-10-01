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

package com.android.systemui.qs.ax.ui.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.android.internal.R as InternalR
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig

@Immutable
internal data class AxMediaColors(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val foreground: Color
)

@Immutable
internal data class AxMediaTheme(
    val colors: AxMediaColors,
    val containerBackground: Color,
    val overlayColor: Color
)

@Composable
internal fun rememberAxMediaTheme(
    session: MediaSessionModel?,
    surface: AxMediaSurface = AxMediaSurface.CONTROL
): AxMediaTheme {
    val context = LocalContext.current
    val colorScheme = session?.colorScheme
    val isQsGrid = surface == AxMediaSurface.CONTROL
    val tileBackground =
        if (!isQsGrid) {
            Color.Transparent
        } else {
            LocalAxQsCellConfig.current.backgroundColor()
        }
    val tileForeground =
        if (!isQsGrid) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val aospDefaultBackground = remember(context) {
        Color(context.getColor(InternalR.color.system_on_surface_light))
    }
    val sessionBackground = colorScheme?.background ?: aospDefaultBackground
    val overlayColor by animateColorAsState(
        targetValue = sessionBackground,
        label = "AxMediaOverlayColor"
    )
    val containerBackground by animateColorAsState(
        targetValue = tileBackground,
        label = "AxMediaContainerBackground"
    )
    val primary by animateColorAsState(
        targetValue = if (session != null) {
            colorScheme?.primary ?: MaterialTheme.colorScheme.primaryFixed
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "AxMediaPrimary"
    )
    val onPrimary by animateColorAsState(
        targetValue = if (session != null) {
            colorScheme?.onPrimary ?: MaterialTheme.colorScheme.onPrimaryFixed
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        label = "AxMediaOnPrimary"
    )
    val foreground by animateColorAsState(
        targetValue = tileForeground,
        label = "AxMediaForeground"
    )
    val colors = remember(primary, onPrimary, containerBackground, foreground) {
        AxMediaColors(
            primary = primary,
            onPrimary = onPrimary,
            background = containerBackground,
            foreground = foreground
        )
    }
    return AxMediaTheme(colors, containerBackground, overlayColor)
}
