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

package com.android.systemui.qs.ax.ui.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.volume.dialog.sliders.ui.compose.SliderTrack
import com.android.systemui.volume.ui.compose.slider.SliderIcon

private val AxSliderTrackInsideCornerRadius = AxQsTokens.Slider.TrackInsideCornerRadius
private val AxSliderThumbTrackGap = AxQsTokens.Slider.ThumbTrackGap
private val VerticalSliderShape = AxQsTokens.CornerRadius.VerticalSliderShape
private const val VERTICAL_SLIDER_CORNER_DIVISOR = AxQsTokens.Slider.CORNER_DIVISOR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AxExpressiveVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    sliderHeight: Dp,
    trackBackgroundColor: Color = Color.Transparent,
    icon: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val inactiveTrackColor = cellConfig.backgroundColor()
    val sliderScale = sliderHeight / cellConfig.sliderTrackHeight
    val iconSize = cellConfig.iconSize * sliderScale

    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            activeTickColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTrackColor = inactiveTrackColor,
            inactiveTickColor = MaterialTheme.colorScheme.onSurface,
            disabledThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledActiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledActiveTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
            disabledInactiveTrackColor = inactiveTrackColor.copy(alpha = 0.38f),
            disabledInactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    val thumbSize =
        DpSize(
            width = cellConfig.sliderThumbWidth * sliderScale,
            height = cellConfig.rowHeight * sliderScale,
        )
    val trackCornerSize = sliderHeight / VERTICAL_SLIDER_CORNER_DIVISOR
    val trackShape = VerticalSliderShape
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        modifier = modifier,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = enabled,
                colors = colors,
                thumbSize = thumbSize,
            )
        },
        track = { sliderState ->
            SliderTrack(
                sliderState = sliderState,
                isEnabled = enabled,
                colors = colors,
                trackCornerSize = trackCornerSize,
                trackInsideCornerSize = AxSliderTrackInsideCornerRadius,
                thumbTrackGapSize = AxSliderThumbTrackGap,
                trackSize = sliderHeight,
                modifier = Modifier.background(trackBackgroundColor, trackShape),
                activeTrackEndIcon = { iconsState ->
                    AxQsSliderIcon(
                        visible = iconsState.isActiveTrackEndIconVisible,
                        size = iconSize,
                        icon = icon,
                    )
                },
                inactiveTrackEndIcon = { iconsState ->
                    AxQsSliderIcon(
                        visible = !iconsState.isActiveTrackEndIconVisible,
                        size = iconSize,
                        icon = icon,
                    )
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AxQsSliderIcon(
    visible: Boolean,
    size: Dp,
    icon: @Composable (Modifier) -> Unit,
) {
    SliderIcon(
        isVisible = visible,
        icon = { icon(Modifier.size(size).rotate(90f)) },
    )
}
