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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.android.systemui.qs.ax.pressfeedback.axPressFeedback
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.panels.ui.compose.selection.TileState
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Selected
import com.android.systemui.qs.ui.compose.borderOnFocus
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

@Composable
internal fun AxInteractiveTileContainer(
    tileState: TileState,
    resizeHandleModifier: Modifier,
    selectionColor: Color,
    selectionShape: Shape,
    resizable: Boolean,
    modifier: Modifier = Modifier,
    itemModifier: Modifier = Modifier,
    showRemoveBadge: Boolean = true,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    removeContentDescription: String? = null,
    resizeContentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val selected = tileState == Selected
    val borderAlpha by
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            label = "AxSelectionBorderAlpha",
        )
    Box(
        modifier = modifier.zIndex(if (selected) 2f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = itemModifier,
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .axSelectionBorder(
                            color = selectionColor,
                            borderWidth = 3.dp,
                            shape = selectionShape,
                            alpha = { borderAlpha },
                        ),
            ) {
                content()
            }
            if (showRemoveBadge) {
                RemoveBadge(contentDescription = removeContentDescription, onClick = onRemoveClick)
            }
            ResizeHandle(
                visible = selected && resizable,
                modifier = resizeHandleModifier,
                contentDescription = resizeContentDescription,
                onClick = onResizeClick,
            )
        }
    }
}

@Composable
private fun BoxScope.RemoveBadge(contentDescription: String?, onClick: () -> Unit) {
    val touchSize = LocalMinimumInteractiveComponentSize.current
    val interactionSource = remember { MutableInteractionSource() }
    val badgeScale by
        animateFloatAsState(
            targetValue = 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "AxRemoveBadgeScale",
        )
    Box(
        modifier =
            Modifier.align(Alignment.TopEnd)
                .offset(x = RemoveBadgeSize / 2, y = -RemoveBadgeSize / 2)
                .size(touchSize)
                .zIndex(2f)
                .axPressFeedback(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = RemoveBadgeSize / 2),
                    onClick = onClick,
                )
                .semantics { contentDescription?.let { this.contentDescription = it } }
                .borderOnFocus(MaterialTheme.colorScheme.secondary, CornerSize(50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(RemoveBadgeSize)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                modifier = Modifier.size(RemoveBadgeIconSize),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    visible: Boolean,
    modifier: Modifier,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    if (!visible) return
    val touchSize = LocalMinimumInteractiveComponentSize.current
    val clickModifier =
        Modifier.pointerInput(onClick) {
            detectTapGestures(onTap = { onClick() })
        }
    Box(
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .offset(x = ResizeHandleSize / 2, y = ResizeHandleSize / 2)
                .size(touchSize)
                .zIndex(2f)
                .systemGestureExclusion { Rect(Offset.Zero, it.size.toSize()) }
                .then(clickModifier)
                .then(modifier)
                .semantics { contentDescription?.let { this.contentDescription = it } }
                .borderOnFocus(MaterialTheme.colorScheme.secondary, CornerSize(50)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier.size(ResizeHandleSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val iconColor = MaterialTheme.colorScheme.onPrimary
            Canvas(modifier = Modifier.size(ResizeHandleIconSize)) {
                val strokeWidth = 2.dp.toPx()
                val w = size.width
                val h = size.height
                val arrowArm = 4.dp.toPx()
                drawLine(iconColor, Offset(0f, 0f), Offset(w, h), strokeWidth, StrokeCap.Round)
                drawLine(
                    iconColor,
                    Offset(0f, 0f),
                    Offset(arrowArm, 0f),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    iconColor,
                    Offset(0f, 0f),
                    Offset(0f, arrowArm),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    iconColor,
                    Offset(w, h),
                    Offset(w - arrowArm, h),
                    strokeWidth,
                    StrokeCap.Round
                )
                drawLine(
                    iconColor,
                    Offset(w, h),
                    Offset(w, h - arrowArm),
                    strokeWidth,
                    StrokeCap.Round
                )
            }
        }
    }
}

private val RemoveBadgeSize = AxQsTokens.Badges.RemoveBadgeSize
private val RemoveBadgeIconSize = AxQsTokens.Badges.RemoveBadgeIconSize
private val ResizeHandleSize = AxQsTokens.Badges.ResizeHandleSize
private val ResizeHandleIconSize = AxQsTokens.Badges.ResizeHandleIconSize

internal fun Modifier.axSelectionBorder(
    color: Color,
    borderWidth: Dp = AxQsTokens.Badges.SelectionBorderWidth,
    shape: Shape,
    alpha: () -> Float = { 1f },
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokeWidth = borderWidth.toPx()
    onDrawWithContent {
        drawContent()
        val currentAlpha = alpha()
        if (currentAlpha > 0f) {
            drawOutline(
                outline = outline,
                color = color,
                style = Stroke(width = strokeWidth),
                alpha = currentAlpha,
            )
        }
    }
}

private data class AxResizeDragState(
    val startSpan: AxQsSpan,
    val dragOffset: Offset = Offset.Zero,
)

@Composable
internal fun Modifier.axQsResizeHandle(
    id: String,
    span: () -> AxQsSpan,
    cellConfig: AxQsCellConfig,
    resolveSpan: (AxQsSpan, Int, Int) -> AxQsSpan,
    canResize: (AxQsSpan) -> Boolean,
    onResizeStarted: () -> Unit,
    onResizeStopped: () -> Unit,
    onResize: (AxQsSpan) -> Unit,
    onResizeFinished: (AxQsSpan) -> Unit,
): Modifier {
    val currentSpan by rememberUpdatedState(span)
    val currentResolveSpan by rememberUpdatedState(resolveSpan)
    val currentCanResize by rememberUpdatedState(canResize)
    val currentOnResizeStarted by rememberUpdatedState(onResizeStarted)
    val currentOnResizeStopped by rememberUpdatedState(onResizeStopped)
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnResizeFinished by rememberUpdatedState(onResizeFinished)
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val columnStep =
        with(density) { (cellConfig.cellWidth + cellConfig.spacing).toPx().coerceAtLeast(1f) }
    val rowStep =
        with(density) { (cellConfig.rowHeight + cellConfig.spacing).toPx().coerceAtLeast(1f) }

    return pointerInput(id, layoutDirection, columnStep, rowStep) {
        val dragRef = AtomicReference(AxResizeDragState(currentSpan()))
        detectDragGestures(
            onDragStart = {
                currentOnResizeStarted()
                dragRef.set(AxResizeDragState(currentSpan()))
            },
            onDragCancel = {
                currentOnResize(dragRef.get().startSpan)
                currentOnResizeStopped()
            },
            onDragEnd = {
                currentOnResizeFinished(currentSpan())
                currentOnResizeStopped()
            },
        ) { change, amount ->
            change.consume()
            val state =
                dragRef.updateAndGet { it.copy(dragOffset = it.dragOffset + amount) }
            val horizontalOffset =
                if (layoutDirection == LayoutDirection.Ltr) {
                    state.dragOffset.x
                } else {
                    -state.dragOffset.x
                }
            val columnDelta = (horizontalOffset / columnStep).roundToInt()
            val rowDelta = (state.dragOffset.y / rowStep).roundToInt()
            val target = currentResolveSpan(state.startSpan, columnDelta, rowDelta)
            if (target != currentSpan() && currentCanResize(target)) {
                currentOnResize(target)
            }
        }
    }
}
