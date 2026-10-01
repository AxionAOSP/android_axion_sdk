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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.ax.shared.model.AxQsSpan

@Immutable
enum class AxQsMediaLayoutVariant {
    AxCompact,
    AxStudio4x2,
    AxSquare2x2,
    AxHalfRow2x1,
}

@Immutable
data class AxQsMediaGridLayoutConfig(
    val variant: AxQsMediaLayoutVariant,
    val albumSize: Dp,
    val albumCornerRadius: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val showRouteButton: Boolean,
    val showNextButton: Boolean,
    val maxActions: Int,
) {
    companion object {
        fun resolve(span: AxQsSpan, maxHeight: Dp): AxQsMediaGridLayoutConfig {
            return when {
                span.columns >= 3 && span.rows >= 2 && maxHeight >= 110.dp ->
                    AxQsMediaGridLayoutConfig(
                        variant = AxQsMediaLayoutVariant.AxStudio4x2,
                        albumSize = 96.dp,
                        albumCornerRadius = 14.dp,
                        horizontalPadding = 14.dp,
                        verticalPadding = 12.dp,
                        showRouteButton = true,
                        showNextButton = true,
                        maxActions = 3,
                    )
                span.columns >= 3 -> AxQsMediaGridLayoutConfig(
                    variant = AxQsMediaLayoutVariant.AxCompact,
                    albumSize = if (maxHeight < 72.dp) 44.dp else 50.dp,
                    albumCornerRadius = 12.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 6.dp,
                    showRouteButton = true,
                    showNextButton = true,
                    maxActions = 3,
                )
                span.columns == 2 && span.rows >= 2 -> AxQsMediaGridLayoutConfig(
                    variant = AxQsMediaLayoutVariant.AxSquare2x2,
                    albumSize = 38.dp,
                    albumCornerRadius = 10.dp,
                    horizontalPadding = 14.dp,
                    verticalPadding = 12.dp,
                    showRouteButton = true,
                    showNextButton = true,
                    maxActions = 3,
                )
                else -> AxQsMediaGridLayoutConfig(
                    variant = AxQsMediaLayoutVariant.AxHalfRow2x1,
                    albumSize = (maxHeight - 16.dp).coerceIn(38.dp, 44.dp),
                    albumCornerRadius = 10.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 6.dp,
                    showRouteButton = false,
                    showNextButton = true,
                    maxActions = 2,
                )
            }
        }
    }
}
