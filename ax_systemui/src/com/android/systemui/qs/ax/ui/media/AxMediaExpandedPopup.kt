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

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import kotlinx.coroutines.launch

private const val POPUP_BLUR_RADIUS_PX = 90
private const val POPUP_DIM_BLUR_ON = 0.25f
private const val POPUP_DIM_BLUR_OFF = 0.65f

@Composable
internal fun AxMediaExpandedPopup(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    songKey: String,
    blurEnabled: Boolean,
    onDismiss: () -> Unit
) {
    LaunchedEffect(session == null) { if (session == null) onDismiss() }

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val popupSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    LaunchedEffect(Unit) { progress.animateTo(1f, popupSpring) }
    val requestDismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(durationMillis = 180, easing = FastOutLinearInEasing))
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            val canBlur = blurEnabled && window.windowManager.isCrossWindowBlurEnabled
            if (canBlur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes =
                    window.attributes.apply { blurBehindRadius = POPUP_BLUR_RADIUS_PX }
                window.setDimAmount(POPUP_DIM_BLUR_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setDimAmount(POPUP_DIM_BLUR_OFF)
            }
        }

        val canBlurUi = blurEnabled && view.context.getSystemService(WindowManager::class.java)
            ?.isCrossWindowBlurEnabled == true
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val cardColor =
            if (!canBlurUi && isDark) {
                colors.background
            } else {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (canBlurUi) 0.6f else 1f)
            }
        Box(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { requestDismiss() } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier =
                    Modifier.padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = progress.value
                            val scale = (0.8f + 0.2f * p).coerceAtLeast(0f)
                            scaleX = scale
                            scaleY = scale
                            alpha = p.coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardColor)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Media player",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.foreground
                )

                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AxMediaAlbumCover(
                        artwork = session?.background,
                        songKey = songKey,
                        session = session,
                        size = maxWidth,
                        cornerRadius = 24.dp
                    )
                }

                AxMediaTrackInfo(
                    session = session,
                    colors = colors,
                    titleStyle = MaterialTheme.typography.titleLarge,
                    subtitleStyle = MaterialTheme.typography.bodyMedium
                )

                MediaSeekBar(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    dense = false,
                    interactive = true
                )

                MediaControls(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    interactive = true,
                    actionSize = 56.dp,
                    maxActions = 3,
                    spreadCoreActions = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                PopupVolumeRow(colors = colors)
            }
        }
    }
}

@Composable
private fun PopupVolumeRow(colors: AxMediaColors) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.VolumeDown, contentDescription = null, tint = colors.foreground)
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            colors =
                SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.foreground.copy(alpha = 0.3f)
                ),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = colors.foreground)
    }
}
