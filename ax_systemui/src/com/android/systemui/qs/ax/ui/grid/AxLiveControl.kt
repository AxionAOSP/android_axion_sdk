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

package com.android.systemui.qs.ax.ui.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height as layoutHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.tiles.ringer.RingerSliderTileContent
import com.android.systemui.qs.ax.ui.controls.AxQsBrightnessButton
import com.android.systemui.qs.ax.ui.controls.AxQsBrightnessControl
import com.android.systemui.qs.ax.ui.controls.AxQsVolumeControl
import com.android.systemui.qs.ax.ui.controls.AxQsVolumeMuteButton
import com.android.systemui.qs.ax.ui.controls.axQsControlShape
import com.android.systemui.qs.ax.ui.media.AxMediaPanel
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

@Composable
internal fun AxLiveControl(
    control: AxQsControl,
    span: AxQsSpan,
    mediaViewModel: AxMediaViewModel,
    mediaViewModelFactory: MediaViewModel.Factory,
    brightnessSliderViewModel: BrightnessSliderViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel,
    verticalSliderStyle: AxQsVerticalSliderStyle,
    entranceProgress: () -> Float,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val isVertical = control.isVerticalSlider
    val is1x1Widget = span.rows == 1 && span.columns == 1
    val targetHeight = cellConfig.itemHeight(span)
    val controlModifier =
        when {
            is1x1Widget -> Modifier.size(cellConfig.iconTileSize)
            isVertical -> Modifier.width(cellConfig.iconTileSize).layoutHeight(targetHeight)
            else -> Modifier.fillMaxWidth().layoutHeight(targetHeight)
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (control) {
            AxQsControl.BRIGHTNESS ->
                AxQsBrightnessControl(
                    verticalStyle = verticalSliderStyle,
                    viewModel = brightnessSliderViewModel,
                    entranceProgress = entranceProgress,
                    interactive = interactive,
                    modifier = controlModifier,
                )
            AxQsControl.VOLUME ->
                AxQsVolumeControl(
                    verticalStyle = verticalSliderStyle,
                    viewModel = volumeSliderViewModel,
                    interactive = interactive,
                    modifier = controlModifier,
                )
            AxQsControl.AUTO_BRIGHTNESS ->
                AxQsBrightnessButton(
                    viewModel = brightnessSliderViewModel,
                    interactive = interactive,
                    modifier = controlModifier,
                )
            AxQsControl.VOLUME_MUTE ->
                AxQsVolumeMuteButton(
                    viewModel = volumeSliderViewModel,
                    interactive = interactive,
                    modifier = controlModifier,
                )
            AxQsControl.RINGER ->
                RingerSliderTileContent(
                    shape = axQsControlShape(AxQsControl.RINGER, span),
                    interactable = interactive,
                    modifier = controlModifier,
                )
            AxQsControl.MEDIA ->
                AxMediaPanel(
                    viewModel = mediaViewModel,
                    span = span,
                    mediaViewModelFactory = mediaViewModelFactory,
                    interactive = interactive,
                    modifier = controlModifier,
                    showPlaceholder = true,
                )
        }
    }
}
