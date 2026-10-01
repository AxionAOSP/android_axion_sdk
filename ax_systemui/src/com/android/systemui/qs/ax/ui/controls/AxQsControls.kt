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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.qs.ax.pressfeedback.axPressFeedback
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.tiles.ringer.RingerSliderTileContent
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.media.AxMediaPanel
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

@Composable
internal fun AxQsBrightnessButton(
    viewModel: BrightnessSliderViewModel,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val active = viewModel.autoMode
    val cellConfig = LocalAxQsCellConfig.current
    AxQsButtonControl(
        description = stringResource(R.string.ax_qs_auto_brightness),
        active = active,
        interactive = interactive,
        onClick = viewModel::onIconClick,
        modifier = modifier,
    ) { tint ->
        MaterialIcon(
            painter =
                painterResource(
                    if (active) {
                        SysuiR.drawable.ic_qs_brightness_auto_on
                    } else {
                        SysuiR.drawable.ic_qs_brightness_auto_off
                    }
                ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(cellConfig.iconSize),
        )
    }
}

@Composable
internal fun AxQsVolumeMuteButton(
    viewModel: AudioStreamSliderViewModel,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.slider.collectAsStateWithLifecycle()
    val muted = state.value <= state.valueRange.start
    val cellConfig = LocalAxQsCellConfig.current
    AxQsButtonControl(
        description = stringResource(R.string.ax_qs_volume_mute),
        active = muted,
        interactive = interactive && state.isMutable,
        onClick = { viewModel.toggleMuted(state) },
        modifier = modifier,
    ) { tint ->
        state.icon?.let { icon ->
            Icon(icon = icon, tint = tint, modifier = Modifier.size(cellConfig.iconSize))
        }
    }
}

@Composable
private fun AxQsButtonControl(
    description: String,
    active: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val background by
        animateColorAsState(
            targetValue =
                if (active) MaterialTheme.colorScheme.primary else cellConfig.backgroundColor(),
            label = "AxQsButtonBackground",
        )
    val foreground by
        animateColorAsState(
            targetValue =
                if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            label = "AxQsButtonForeground",
        )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(background)
                .axPressFeedback(interactionSource, enabled = interactive)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = interactive,
                    role = Role.Switch,
                    onClick = onClick,
                )
                .semantics { contentDescription = description },
    ) {
        icon(foreground)
    }
}

private val AxButtonControlIconSize = 24.dp

data class AxControlViewModels(
    val brightnessSliderViewModel: BrightnessSliderViewModel,
    val volumeViewModel: AudioStreamSliderViewModel,
    val mediaViewModel: AxMediaViewModel,
)

val LocalAxControlViewModels = staticCompositionLocalOf<AxControlViewModels?> { null }

@Composable
fun AxQsControlPreview(
    control: AxQsControl,
    span: AxQsSpan,
    verticalSliderStyle: AxQsVerticalSliderStyle,
    modifier: Modifier = Modifier,
    viewModels: AxControlViewModels? = LocalAxControlViewModels.current,
) {
    val models = viewModels ?: return
    val brightnessViewModel = models.brightnessSliderViewModel
    val volumeViewModel = models.volumeViewModel
    val mediaViewModel = models.mediaViewModel
    val cellConfig = LocalAxQsCellConfig.current
    val isVertical = control.isVerticalSlider
    val is1x1Widget = span.rows == 1 && span.columns == 1
    val previewModifier =
        when {
            is1x1Widget -> Modifier.size(cellConfig.iconTileSize)
            isVertical -> Modifier.width(cellConfig.iconTileSize).fillMaxHeight()
            else -> Modifier.fillMaxSize()
        }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (control) {
            AxQsControl.BRIGHTNESS,
            AxQsControl.VOLUME ->
                AxQsSliderPreview(
                    control = control,
                    verticalStyle = verticalSliderStyle,
                    brightnessViewModel = brightnessViewModel,
                    volumeViewModel = volumeViewModel,
                    modifier = previewModifier,
                )
            AxQsControl.AUTO_BRIGHTNESS ->
                AxQsBrightnessButton(
                    viewModel = brightnessViewModel,
                    interactive = false,
                    modifier = previewModifier,
                )
            AxQsControl.VOLUME_MUTE ->
                AxQsVolumeMuteButton(
                    viewModel = volumeViewModel,
                    interactive = false,
                    modifier = previewModifier,
                )
            AxQsControl.RINGER ->
                RingerSliderTileContent(
                    interactable = false,
                    shape = axQsControlShape(AxQsControl.RINGER, span),
                    modifier = previewModifier,
                )
            AxQsControl.MEDIA ->
                AxMediaPanel(
                    viewModel = mediaViewModel,
                    span = span,
                    modifier = previewModifier,
                    showPlaceholder = true,
                    interactive = false,
                )
        }
    }
}
