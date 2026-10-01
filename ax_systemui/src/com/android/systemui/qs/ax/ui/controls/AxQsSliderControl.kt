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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.qs.ax.ui.controls

import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.modifiers.sliderPercentage
import com.android.compose.ui.graphics.drawInOverlay
import com.android.systemui.brightness.shared.model.GammaBrightness
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.brightness.ui.viewmodel.Drag
import com.android.systemui.common.ui.compose.Icon as SystemUiIcon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SeekableSliderTrackerConfig
import com.android.systemui.haptics.slider.SliderHapticFeedbackConfig
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.ui.grid.AxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.panels.axQsEntrance
import com.android.systemui.res.R
import com.android.systemui.utils.PolicyRestriction
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.SliderState
import kotlin.math.round
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
private fun AxQsSliderControl(
    verticalStyle: AxQsVerticalSliderStyle = AxQsVerticalSliderStyle.M3_EXPRESSIVE,
    modifier: Modifier = Modifier,
    interceptParentScroll: Boolean = true,
    mirrorInOverlay: Boolean = false,
    entranceProgress: () -> Float = { 1f },
    content: @Composable (sliderHeight: Dp) -> Unit,
) {
    val mirrorModifier =
        if (mirrorInOverlay) {
            Modifier.drawInOverlay().axQsEntrance(entranceProgress)
        } else {
            Modifier
        }
    Box(
        modifier = modifier.then(mirrorModifier),
        contentAlignment = Alignment.Center,
    ) {
        val view = LocalView.current
        val layoutDirection = LocalLayoutDirection.current
        val inputModifier = Modifier.interceptParentScroll(interceptParentScroll, view)
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().then(inputModifier),
            contentAlignment = Alignment.Center,
        ) {
            if (verticalStyle == AxQsVerticalSliderStyle.PLATFORM) {
                val sliderHeight = maxHeight
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        content(sliderHeight)
                    }
                }
            } else {
                val sliderWidth = maxHeight
                val availableHeight = maxWidth
                val sliderHeight = axQsSliderTrackHeight(availableHeight)
                Box(
                    modifier =
                        Modifier.requiredWidth(sliderWidth)
                            .requiredHeight(availableHeight)
                            .rotate(-90f),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        content(sliderHeight)
                    }
                }
            }
        }
    }
}

private fun Modifier.interceptParentScroll(enabled: Boolean, view: View): Modifier {
    if (!enabled) return this
    return pointerInput(view) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}

internal fun axQsControlShape(
    control: AxQsControl,
    span: AxQsSpan,
    verticalStyle: AxQsVerticalSliderStyle = AxQsVerticalSliderStyle.M3_EXPRESSIVE,
): Shape {
    return when {
        control == AxQsControl.RINGER -> CircleShape
        control.isSlider ->
            when (verticalStyle) {
                AxQsVerticalSliderStyle.M3_EXPRESSIVE -> VerticalSliderShape
                AxQsVerticalSliderStyle.PLATFORM -> CircleShape
            }
        span == AxQsSpan.TileDefault && control != AxQsControl.MEDIA -> CircleShape
        else -> RoundedCornerShape(AxQsControlCornerRadius)
    }
}

internal val AxQsControlCornerRadius = AxQsTokens.CornerRadius.ControlCornerRadius
private val VerticalSliderShape = AxQsTokens.CornerRadius.VerticalSliderShape
private val AxSliderTrackHeight = AxQsTokens.Slider.TrackHeight
private val PlatformSliderFramePadding = AxQsTokens.Slider.FramePadding
private val AxSliderTrackInsideCornerRadius = AxQsTokens.Slider.TrackInsideCornerRadius
private val AxSliderThumbWidth = AxQsTokens.Slider.ThumbWidth
private val AxSliderThumbTrackGap = AxQsTokens.Slider.ThumbTrackGap
private const val VERTICAL_SLIDER_CORNER_DIVISOR = AxQsTokens.Slider.CORNER_DIVISOR

internal fun axQsSliderTrackHeight(availableHeight: Dp): Dp {
    return AxSliderTrackHeight * (availableHeight / AxQsCellConfig.Defaults.TileHeight)
}

@Composable
private fun AxQsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    sliderHeight: Dp,
    verticalStyle: AxQsVerticalSliderStyle,
    trackBackgroundColor: Color = Color.Transparent,
    onIconClick: (() -> Unit)? = null,
    icon: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val inactiveTrackColor = cellConfig.backgroundColor()
    val sliderScale = sliderHeight / cellConfig.sliderTrackHeight
    val iconSize = cellConfig.iconSize * sliderScale
    if (verticalStyle == AxQsVerticalSliderStyle.PLATFORM) {
        val platformTrackColor =
            if (trackBackgroundColor == Color.Transparent) {
                inactiveTrackColor
            } else {
                trackBackgroundColor
            }
        AxCapsuleVerticalSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = interactionSource,
            trackColor = platformTrackColor,
            onIconClick = onIconClick,
            icon = { iconModifier, isIconCovered -> icon(iconModifier) },
            modifier = modifier.fillMaxSize(),
        )
    } else {
        AxExpressiveVerticalSlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
            interactionSource = interactionSource,
            sliderHeight = sliderHeight,
            trackBackgroundColor = trackBackgroundColor,
            icon = icon,
            modifier = modifier,
        )
    }
}

@Composable
internal fun AxQsSliderPreview(
    control: AxQsControl,
    verticalStyle: AxQsVerticalSliderStyle,
    brightnessViewModel: BrightnessSliderViewModel,
    volumeViewModel: AudioStreamSliderViewModel,
    modifier: Modifier = Modifier,
) {
    AxQsSliderControl(
        verticalStyle = verticalStyle,
        modifier = modifier,
        interceptParentScroll = false,
    ) { sliderHeight ->
        Box(Modifier.fillMaxSize().gesturesDisabled().clearAndSetSemantics {}) {
            when (control) {
                AxQsControl.BRIGHTNESS ->
                    AxBrightnessSlider(
                        viewModel = brightnessViewModel,
                        sliderHeight = sliderHeight,
                        verticalStyle = verticalStyle,
                        interactive = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                AxQsControl.VOLUME ->
                    AxVolumeSlider(
                        viewModel = volumeViewModel,
                        interactive = false,
                        sliderHeight = sliderHeight,
                        verticalStyle = verticalStyle,
                        modifier = Modifier.fillMaxSize(),
                    )
                AxQsControl.AUTO_BRIGHTNESS,
                AxQsControl.VOLUME_MUTE,
                AxQsControl.RINGER,
                AxQsControl.MEDIA -> Unit
            }
        }
    }
}

@Composable
internal fun AxQsBrightnessControl(
    verticalStyle: AxQsVerticalSliderStyle,
    viewModel: BrightnessSliderViewModel,
    entranceProgress: () -> Float,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val isDragging = remember { mutableStateOf(false) }
    val mirrorInOverlay = isDragging.value && viewModel.showMirror
    AxQsSliderControl(
        verticalStyle = verticalStyle,
        mirrorInOverlay = mirrorInOverlay,
        entranceProgress = entranceProgress,
        modifier = modifier,
    ) { sliderHeight ->
        AxBrightnessSlider(
            viewModel = viewModel,
            onDraggingChanged = { isDragging.value = it },
            sliderHeight = sliderHeight,
            verticalStyle = verticalStyle,
            interactive = interactive,
            mirrorInOverlay = mirrorInOverlay,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AxBrightnessSlider(
    viewModel: BrightnessSliderViewModel,
    sliderHeight: Dp,
    verticalStyle: AxQsVerticalSliderStyle,
    interactive: Boolean = true,
    mirrorInOverlay: Boolean = false,
    onDraggingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val gamma = viewModel.currentBrightness.value
    if (gamma == BrightnessSliderViewModel.initialValue.value) return

    var value by remember(gamma) { mutableIntStateOf(gamma) }
    val animatedValue by
        animateFloatAsState(targetValue = value.toFloat(), label = "AxBrightnessSliderValue")
    val valueRange =
        viewModel.minBrightness.value.toFloat()..viewModel.maxBrightness.value.toFloat()
    val percentage =
        ((value - valueRange.start) * 100f / (valueRange.endInclusive - valueRange.start)).coerceIn(
            0f,
            100f,
        )
    val iconRes =
        if (viewModel.autoMode) {
            R.drawable.ic_qs_brightness_auto_on
        } else {
            BrightnessSliderViewModel.getIconForPercentage(percentage)
        }
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel =
        rememberViewModel(traceName = "AxBrightnessSliderHaptics") {
            viewModel.hapticsViewModelFactory.create(
                interactionSource,
                valueRange,
                Orientation.Horizontal,
                SliderHapticFeedbackConfig(maxVelocityToScale = 1f),
                SeekableSliderTrackerConfig(),
            )
        }
    val restriction by
        viewModel.policyRestriction.collectAsStateWithLifecycle(
            initialValue = PolicyRestriction.NoRestriction
        )
    val restricted = restriction as? PolicyRestriction.Restricted
    val enabled = restricted == null && interactive
    val overriddenByApp by viewModel.brightnessOverriddenByWindow.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val contentDescription = stringResource(R.string.accessibility_brightness)
    val mirrorBackgroundColor = LocalAxQsCellConfig.current.backgroundColor()
    var dragging by remember { mutableStateOf(false) }
    val currentDragging by rememberUpdatedState(dragging)
    val currentOnDraggingChanged by rememberUpdatedState(onDraggingChanged)
    val inputModifier =
        if (interactive) {
            Modifier
                .semantics(mergeDescendants = true) {
                    text = AnnotatedString(contentDescription)
                }
                .sliderPercentage {
                    (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                }
                .pointerInteropFilter {
                    if (
                        it.actionMasked == MotionEvent.ACTION_UP ||
                            it.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        viewModel.emitBrightnessTouchForFalsing()
                    }
                    false
                }
                .then(
                    if (restricted != null) {
                        Modifier.clickable { viewModel.showPolicyRestrictionDialog(restricted) }
                    } else {
                        Modifier
                    }
                )
        } else {
            Modifier
        }

    DisposableEffect(viewModel) {
        onDispose {
            if (currentDragging) {
                viewModel.setIsDragging(false)
                currentOnDraggingChanged(false)
            }
        }
    }

    LaunchedEffect(interactionSource, overriddenByApp) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && overriddenByApp) {
                viewModel.showToast(context, R.string.quick_settings_brightness_unable_adjust_msg)
            }
        }
    }

    AxQsSlider(
        value = animatedValue,
        onValueChange = { newValue ->
            if (interactive && enabled && !overriddenByApp) {
                hapticsViewModel.onValueChange(newValue)
                value = newValue.toInt()
                if (!dragging) {
                    dragging = true
                    viewModel.setIsDragging(true)
                    onDraggingChanged(true)
                }
                coroutineScope.launch { viewModel.onDrag(Drag.Dragging(GammaBrightness(value))) }
            }
        },
        onValueChangeFinished = {
            if (interactive && enabled && !overriddenByApp) {
                hapticsViewModel.onValueChangeEnded()
                coroutineScope.launch { viewModel.onDrag(Drag.Stopped(GammaBrightness(value))) }
            }
            if (dragging) {
                dragging = false
                viewModel.setIsDragging(false)
                onDraggingChanged(false)
            }
        },
        valueRange = valueRange,
        enabled = enabled,
        interactionSource = interactionSource,
        sliderHeight = sliderHeight,
        verticalStyle = verticalStyle,
        trackBackgroundColor = if (mirrorInOverlay) mirrorBackgroundColor else Color.Transparent,
        onIconClick = {
            viewModel.onIconClick()
        },
        icon = { iconModifier ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = iconModifier,
            )
        },
        modifier =
            modifier
                .fillMaxSize()
                .sysuiResTag("ax_brightness_slider")
                .then(inputModifier),
    )
}

@Composable
internal fun AxQsVolumeControl(
    verticalStyle: AxQsVerticalSliderStyle,
    viewModel: AudioStreamSliderViewModel,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.slider.collectAsStateWithLifecycle()
    AxQsSliderControl(
        verticalStyle = verticalStyle,
        modifier = modifier,
    ) { sliderHeight ->
        AxVolumeSliderContent(
            state = state,
            viewModel = viewModel,
            sliderHeight = sliderHeight,
            verticalStyle = verticalStyle,
            interactive = interactive,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AxVolumeSlider(
    viewModel: AudioStreamSliderViewModel,
    sliderHeight: Dp,
    verticalStyle: AxQsVerticalSliderStyle,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.slider.collectAsStateWithLifecycle()
    AxVolumeSliderContent(
        state = state,
        viewModel = viewModel,
        sliderHeight = sliderHeight,
        verticalStyle = verticalStyle,
        interactive = interactive,
        modifier = modifier,
    )
}

@Composable
private fun AxVolumeSliderContent(
    state: SliderState,
    viewModel: AudioStreamSliderViewModel,
    sliderHeight: Dp,
    verticalStyle: AxQsVerticalSliderStyle,
    interactive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val value by axVolumeValueState(state)
    val sliderValue =
        if (verticalStyle == AxQsVerticalSliderStyle.PLATFORM) {
            state.value
        } else {
            value
        }
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel =
        axVolumeHapticsViewModel(
            value = sliderValue,
            state = state,
            interactionSource = interactionSource,
            factory = if (interactive) viewModel.getSliderHapticsViewModelFactory() else null,
        )

    AxQsSlider(
        value = sliderValue,
        valueRange = state.valueRange,
        onValueChange = { newValue ->
            hapticsViewModel?.addVelocityDataPoint(newValue)
            if (interactive) viewModel.onValueChanged(state, newValue)
        },
        onValueChangeFinished = {
            hapticsViewModel?.onValueChangeEnded()
            if (interactive) viewModel.onValueChangeFinished()
        },
        enabled = interactive && state.isEnabled,
        interactionSource = interactionSource,
        sliderHeight = sliderHeight,
        verticalStyle = verticalStyle,
        onIconClick = {
            if (interactive) viewModel.toggleMuted(state)
        },
        icon = { iconModifier ->
            val isVolumeEmpty = sliderValue <= state.valueRange.start
            if (isVolumeEmpty) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_off),
                    contentDescription = null,
                    modifier = iconModifier,
                )
            } else {
                val icon = state.icon
                if (icon != null) {
                    SystemUiIcon(icon = icon, modifier = iconModifier)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = iconModifier,
                    )
                }
            }
        },
        modifier =
            modifier
                .fillMaxSize()
                .sysuiResTag("ax_volume_slider")
                .clearAndSetSemantics {
                if (state.isEnabled) {
                    contentDescription = state.a11yContentDescription
                    state.a11yStateDescription?.let { stateDescription = it }
                    progressBarRangeInfo = ProgressBarRangeInfo(state.value, state.valueRange)
                    if (interactive) {
                        setProgress { targetValue ->
                            val direction =
                                when {
                                    targetValue > value -> 1
                                    targetValue < value -> -1
                                    else -> 0
                                }
                            viewModel.onValueChanged(
                                state,
                                (value + direction * state.step).coerceIn(
                                    state.valueRange.start,
                                    state.valueRange.endInclusive,
                                ),
                            )
                            true
                        }
                    }
                } else {
                    disabled()
                    contentDescription =
                        state.disabledMessage?.let { "${state.label}, $it" } ?: state.label
                }
            },
    )
}

@Composable
private fun axVolumeValueState(state: SliderState): State<Float> {
    val value = remember { Animatable(state.value) }
    var previousState by remember { mutableStateOf<SliderState?>(null) }
    LaunchedEffect(state.value, state.isEnabled) {
        val previous = previousState
        previousState = state
        if (
            previous == null ||
                previous is SliderState.Empty ||
                previous.isEnabled != state.isEnabled
        ) {
            value.snapTo(state.value)
        } else {
            value.animateTo(state.value)
        }
    }
    return value.asState()
}

@Composable
private fun axVolumeHapticsViewModel(
    value: Float,
    state: SliderState,
    interactionSource: MutableInteractionSource,
    factory: SliderHapticsViewModel.Factory?,
): SliderHapticsViewModel? {
    return factory?.let {
        val configs =
            VolumeHapticsConfigsProvider.discreteConfigs(
                1f / (state.valueRange.endInclusive - state.valueRange.start),
                state.hapticFilter,
            )
        rememberViewModel(traceName = "AxVolumeSliderHaptics") {
                it.create(
                    interactionSource,
                    state.valueRange,
                    Orientation.Horizontal,
                    configs.hapticFeedbackConfig,
                    configs.sliderTrackerConfig,
                )
            }
            .also { hapticsViewModel ->
                var lastStep by remember { mutableFloatStateOf(round(value)) }
                LaunchedEffect(value) {
                    snapshotFlow { value }
                        .map { round(it) }
                        .filter { it != lastStep }
                        .distinctUntilChanged()
                        .collect { step ->
                            lastStep = step
                            hapticsViewModel.onValueChange(step)
                        }
                }
            }
    }
}
