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

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.ax.pressfeedback.AxPressFeedbackHelper
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
internal fun AxCapsuleVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    animDurationMs: Int = 200,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    trackColor: Color? = null,
    onIconClick: (() -> Unit)? = null,
    icon: @Composable (Modifier, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnIconClick by rememberUpdatedState(onIconClick)
    val currentValueRange by rememberUpdatedState(valueRange)

    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val targetFraction =
        if (value.isNaN()) {
            0f
        } else {
            ((value - valueRange.start) / range).coerceIn(0f, 1f)
        }

    val sliderAnim = remember { Animatable(targetFraction) }
    val isUserDragging = remember { mutableStateOf(false) }
    val liveFraction = remember { mutableFloatStateOf(targetFraction) }
    val initialSnapDone = remember { mutableStateOf(false) }
    val committedFraction = remember { mutableFloatStateOf(Float.NaN) }
    val settleDeadlineMs = remember { mutableLongStateOf(0L) }

    LaunchedEffect(targetFraction, isUserDragging.value) {
        val target = targetFraction.coerceIn(0f, 1f)
        if (isUserDragging.value) {
            return@LaunchedEffect
        }
        if (!initialSnapDone.value) {
            initialSnapDone.value = true
            sliderAnim.snapTo(target)
            return@LaunchedEffect
        }
        val now = SystemClock.uptimeMillis()
        if (now < settleDeadlineMs.longValue) {
            val dist = abs(target - committedFraction.floatValue)
            if (dist < 0.06f) {
                sliderAnim.snapTo(target)
                settleDeadlineMs.longValue = 0L
            }
            return@LaunchedEffect
        }
        val delta = abs(target - sliderAnim.value)
        if (delta >= 0.001f) {
            if (animDurationMs <= 0) {
                sliderAnim.snapTo(target)
            } else {
                val duration = (animDurationMs * delta).toInt().coerceIn(80, animDurationMs)
                sliderAnim.animateTo(
                    targetValue = target,
                    animationSpec =
                        tween(
                            durationMillis = duration,
                            easing = FastOutSlowInEasing,
                        ),
                )
            }
        }
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val cellConfig = LocalAxQsCellConfig.current
    val isIconPressed = remember { mutableStateOf(false) }

    val backgroundModifier = if (trackColor != null) Modifier.background(trackColor) else Modifier

    BoxWithConstraints(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .then(backgroundModifier)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    val iconSizePx = cellConfig.iconTileSize.toPx()
                    val outerHeightPx = size.height.toFloat()
                    val outerWidthPx = size.width.toFloat()

                    val iconLeft = (outerWidthPx - iconSizePx) / 2f
                    val iconRight = iconLeft + iconSizePx
                    val iconBottom = outerHeightPx
                    val iconTop = outerHeightPx - iconSizePx

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val down = event.changes.firstOrNull() ?: continue
                            if (down.isConsumed || !down.changedToDown()) continue
                            down.consume()

                            val pos = down.position
                            val isIconTouch =
                                currentOnIconClick != null &&
                                    pos.x in iconLeft..iconRight &&
                                    pos.y in iconTop..iconBottom

                            val session =
                                SliderTouchSession(
                                    startY = pos.y,
                                    isIconTouch = isIconTouch,
                                    startFraction = (1f - (pos.y / outerHeightPx)).coerceIn(0f, 1f),
                                )

                            if (isIconTouch) {
                                isIconPressed.value = true
                                val press = PressInteraction.Press(pos)
                                session.activePress.value = press
                                coroutineScope.launch { interactionSource?.emit(press) }
                            } else {
                                isUserDragging.value = true
                                liveFraction.floatValue = session.fraction.floatValue
                                val drag = DragInteraction.Start()
                                session.activeDrag.value = drag
                                coroutineScope.launch { interactionSource?.emit(drag) }
                                val currentRange =
                                    currentValueRange.endInclusive - currentValueRange.start
                                val newValue =
                                    currentValueRange.start + session.fraction.floatValue * currentRange
                                currentOnValueChange(newValue)
                            }

                            try {
                                while (true) {
                                    val nextEvent = awaitPointerEvent(PointerEventPass.Main)
                                    val change = nextEvent.changes.firstOrNull() ?: break
                                    val currentPos = change.position

                                    if (!change.pressed) {
                                        change.consume()
                                        if (session.isDragging.value) {
                                            session.activeDrag.value?.let { drag ->
                                                coroutineScope.launch {
                                                    interactionSource?.emit(
                                                        DragInteraction.Stop(drag)
                                                    )
                                                }
                                                session.activeDrag.value = null
                                            }
                                            val currentRange =
                                                currentValueRange.endInclusive -
                                                    currentValueRange.start
                                            val committedValue =
                                                currentValueRange.start +
                                                    session.fraction.floatValue * currentRange
                                            committedFraction.floatValue = session.fraction.floatValue
                                            settleDeadlineMs.longValue =
                                                SystemClock.uptimeMillis() + 1000L
                                            coroutineScope.launch {
                                                sliderAnim.snapTo(session.fraction.floatValue)
                                            }
                                            currentOnValueChange(committedValue)
                                            currentOnValueChangeFinished?.invoke()
                                        } else {
                                            currentOnIconClick?.invoke()
                                        }
                                        break
                                    }

                                    if (!session.isDragging.value) {
                                        if (abs(currentPos.y - session.startY) > touchSlop) {
                                            session.isDragging.value = true
                                            isIconPressed.value = false
                                            session.activePress.value?.let { press ->
                                                coroutineScope.launch {
                                                    interactionSource?.emit(
                                                        PressInteraction.Cancel(press)
                                                    )
                                                }
                                                session.activePress.value = null
                                            }
                                            isUserDragging.value = true
                                            val newFraction =
                                                (1f - (currentPos.y / outerHeightPx)).coerceIn(
                                                    0f,
                                                    1f,
                                                )
                                            liveFraction.floatValue = newFraction
                                            session.fraction.floatValue = newFraction
                                            val drag = DragInteraction.Start()
                                            session.activeDrag.value = drag
                                            coroutineScope.launch { interactionSource?.emit(drag) }
                                            val currentRange =
                                                currentValueRange.endInclusive -
                                                    currentValueRange.start
                                            val newValue =
                                                currentValueRange.start + newFraction * currentRange
                                            currentOnValueChange(newValue)
                                        }
                                    } else {
                                        val newFraction =
                                            (1f - (currentPos.y / outerHeightPx)).coerceIn(0f, 1f)
                                        if (abs(newFraction - session.fraction.floatValue) >= 0.0005f) {
                                            liveFraction.floatValue = newFraction
                                            session.fraction.floatValue = newFraction
                                            val currentRange =
                                                currentValueRange.endInclusive -
                                                    currentValueRange.start
                                            val newValue =
                                                currentValueRange.start + newFraction * currentRange
                                            currentOnValueChange(newValue)
                                        }
                                    }
                                    change.consume()
                                }
                            } finally {
                                if (isIconPressed.value) {
                                    isIconPressed.value = false
                                    session.activePress.value?.let { press ->
                                        coroutineScope.launch {
                                            interactionSource?.emit(PressInteraction.Release(press))
                                        }
                                        session.activePress.value = null
                                    }
                                }
                                if (isUserDragging.value) {
                                    isUserDragging.value = false
                                    session.activeDrag.value?.let { drag ->
                                        coroutineScope.launch {
                                            interactionSource?.emit(DragInteraction.Cancel(drag))
                                        }
                                        session.activeDrag.value = null
                                    }
                                }
                            }
                        }
                    }
                },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        val currentDisplayFraction =
            if (isUserDragging.value) liveFraction.floatValue else sliderAnim.value
        val fillHeight = (heightPx * currentDisplayFraction.coerceIn(0f, 1f)).coerceAtLeast(0f)
        val fillHeightDp = with(density) { fillHeight.toDp() }

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(fillHeightDp)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.primary)
        )

        val iconCenterPx = with(density) { (cellConfig.iconTileSize / 2).toPx() }
        val iconRadiusPx = with(density) { 15.dp.toPx() }
        val clearancePx = with(density) { 6.dp.toPx() }

        val collisionStartPx = iconCenterPx - iconRadiusPx - clearancePx
        val collisionEndPx = iconCenterPx + iconRadiusPx + clearancePx

        val isColliding = fillHeight in collisionStartPx..collisionEndPx
        val isIconCovered = fillHeight > collisionEndPx
        val targetScale =
            when {
                isIconPressed.value -> (if (isIconCovered) 1f else 0.85f) * 0.90f
                isColliding -> 0.92f
                isIconCovered -> 1f
                else -> 0.85f
            }
        val iconScale by
            animateFloatAsState(
                targetValue = targetScale,
                animationSpec =
                    if (isIconPressed.value) {
                        AxPressFeedbackHelper.PressSpringSpec
                    } else {
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                    },
                label = "AxCapsuleSliderIconScale",
            )
        val targetTranslationY =
            when {
                isColliding -> {
                    val neededShift = -(fillHeight - (iconCenterPx - iconRadiusPx) + clearancePx)
                    neededShift.coerceAtMost(0f)
                }
                isIconCovered -> 0f
                else -> with(density) { 2.dp.toPx() }
            }
        val iconTranslationY by
            animateFloatAsState(
                targetValue = targetTranslationY,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                label = "AxCapsuleSliderIconTranslationY",
            )
        val iconTint by
            animateColorAsState(
                targetValue =
                    if (isIconCovered) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                label = "AxCapsuleSliderIconTint",
            )
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            Box(
                modifier =
                    Modifier.size(cellConfig.iconTileSize)
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            translationY = iconTranslationY
                        },
                contentAlignment = Alignment.Center,
            ) {
                icon(Modifier, isIconCovered)
            }
        }
    }
}

private class SliderTouchSession(
    val startY: Float,
    isIconTouch: Boolean,
    startFraction: Float,
) {
    val isDragging = mutableStateOf(!isIconTouch)
    val fraction = mutableFloatStateOf(startFraction)
    val activePress = mutableStateOf<PressInteraction.Press?>(null)
    val activeDrag = mutableStateOf<DragInteraction.Start?>(null)
}
