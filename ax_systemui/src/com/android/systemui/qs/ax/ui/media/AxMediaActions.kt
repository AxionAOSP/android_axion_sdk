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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R

@Composable
internal fun CoreMediaAction(
    action: MediaActionModel?,
    imageVector: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    @StringRes descriptionRes: Int,
    @DrawableRes animatedIconRes: Int? = null,
    animatedIconAtEnd: Boolean = false,
    viewModel: AxMediaViewModel,
    width: Dp,
    height: Dp = width,
    iconSize: Dp,
    tint: Color,
    background: Color = Color.Transparent,
    shape: Shape = CircleShape,
    interactive: Boolean,
) {
    when (action) {
        is MediaActionModel.Action ->
            MediaAction(
                action = action,
                viewModel = viewModel,
                width = width,
                height = height,
                iconSize = iconSize,
                tint = tint,
                background = background,
                shape = shape,
                interactive = interactive,
                imageVector = imageVector,
                animatedIconRes = animatedIconRes,
                animatedIconAtEnd = animatedIconAtEnd,
                contentDescription = stringResource(descriptionRes),
            )
        MediaActionModel.ReserveSpace -> Spacer(Modifier.size(width = width, height = height))
        MediaActionModel.None,
        null ->
            PlaceholderMediaAction(
                iconRes = iconRes,
                imageVector = imageVector,
                descriptionRes = descriptionRes,
                width = width,
                height = height,
                iconSize = iconSize,
                tint = tint,
                background = background,
                shape = shape,
            )
    }
}

@Composable
internal fun PlaceholderMediaAction(
    @DrawableRes iconRes: Int? = null,
    imageVector: ImageVector? = null,
    @StringRes descriptionRes: Int,
    width: Dp,
    height: Dp = width,
    iconSize: Dp = 20.dp,
    tint: Color,
    background: Color = Color.Transparent,
    shape: Shape = CircleShape,
) {
    val description = stringResource(descriptionRes)
    val buttonWidth by
        animateDpAsState(
            targetValue = width.coerceAtLeast(0.dp),
            animationSpec = AxMediaTokens.ActionSizeSpring,
            label = "AxMediaPlaceholderWidth",
        )
    val buttonHeight by
        animateDpAsState(
            targetValue = height.coerceAtLeast(0.dp),
            animationSpec = AxMediaTokens.ActionSizeSpring,
            label = "AxMediaPlaceholderHeight",
        )
    val buttonBackground by
        animateColorAsState(targetValue = background, label = "AxMediaPlaceholderBackground")
    val minTouchTarget = minOf(48.dp, maxOf(buttonWidth.coerceAtLeast(0.dp), 32.dp))
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                .clip(CircleShape)
                .semantics {
                    contentDescription = description
                    disabled()
                },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(
                    width = buttonWidth.coerceAtLeast(0.dp),
                    height = buttonHeight.coerceAtLeast(0.dp)
                )
                    .clip(shape)
                    .background(buttonBackground),
        ) {
            if (imageVector != null) {
                MaterialIcon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            } else if (iconRes != null) {
                Icon(
                    icon = IconModel.Resource(iconRes, null),
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
internal fun ExpandedNavigationAction(
    action: MediaActionModel?,
    @StringRes placeholderDescription: Int,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    size: Dp,
    iconSize: Dp,
    background: Color = Color.Transparent,
    imageVector: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) {
    CoreMediaAction(
        action = action,
        imageVector = imageVector,
        iconRes = iconRes,
        descriptionRes = placeholderDescription,
        viewModel = viewModel,
        width = size,
        height = size,
        iconSize = iconSize,
        tint = colors.foreground,
        background = background,
        interactive = interactive,
    )
}

@Composable
internal fun MediaAction(
    action: MediaActionModel,
    viewModel: AxMediaViewModel,
    width: Dp,
    height: Dp = width,
    iconSize: Dp = 20.dp,
    tint: Color,
    background: Color = Color.Transparent,
    shape: Shape = CircleShape,
    interactive: Boolean,
    imageVector: ImageVector? = null,
    @DrawableRes animatedIconRes: Int? = null,
    animatedIconAtEnd: Boolean = false,
    contentDescription: String? = null,
) {
    val buttonWidth by
        animateDpAsState(
            targetValue = width.coerceAtLeast(0.dp),
            animationSpec = AxMediaTokens.ActionSizeSpring,
            label = "AxMediaActionWidth",
        )
    val buttonHeight by
        animateDpAsState(
            targetValue = height.coerceAtLeast(0.dp),
            animationSpec = AxMediaTokens.ActionSizeSpring,
            label = "AxMediaActionHeight",
        )
    val buttonBackground by
        animateColorAsState(targetValue = background, label = "AxMediaActionBackground")
    when (action) {
        is MediaActionModel.Action -> {
            val minTouchTarget = minOf(48.dp, maxOf(buttonWidth.coerceAtLeast(0.dp), 32.dp))
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by
                animateFloatAsState(
                    targetValue = if (isPressed) 0.88f else 1f,
                    animationSpec = AxMediaTokens.ButtonPressSpring,
                    label = "AxMediaActionPressScale",
                )

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = true, radius = minTouchTarget / 2),
                            enabled = interactive && action.onClick != null,
                        ) {
                            viewModel.runAction(action)
                        },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }
                            .size(
                                width = buttonWidth.coerceAtLeast(0.dp),
                                height = buttonHeight.coerceAtLeast(0.dp)
                            )
                            .clip(shape)
                            .background(buttonBackground),
                ) {
                    if (animatedIconRes != null) {
                        val painter =
                            rememberAnimatedVectorPainter(
                                animatedImageVector =
                                    AnimatedImageVector.animatedVectorResource(animatedIconRes),
                                atEnd = animatedIconAtEnd,
                            )
                        MaterialIcon(
                            painter = painter,
                            contentDescription = contentDescription,
                            tint = tint,
                            modifier = Modifier.size(iconSize),
                        )
                    } else if (imageVector != null) {
                        AnimatedContent(
                            targetState = imageVector,
                            transitionSpec = {
                                (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                                    scaleIn(
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                        initialScale = 0.72f,
                                    )) togetherWith
                                    (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                                        scaleOut(
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                            targetScale = 1.18f,
                                        ))
                            },
                            contentAlignment = Alignment.Center,
                            label = "AxMediaActionIcon",
                        ) { vector ->
                            MaterialIcon(
                                imageVector = vector,
                                contentDescription = contentDescription,
                                tint = tint,
                                modifier = Modifier.size(iconSize),
                            )
                        }
                    } else {
                        Icon(icon = action.icon, tint = tint, modifier = Modifier.size(iconSize))
                    }
                }
            }
        }
        MediaActionModel.None -> Unit
        MediaActionModel.ReserveSpace -> {
            val minTouchTarget = minOf(48.dp, maxOf(buttonWidth.coerceAtLeast(0.dp), 32.dp))
            Spacer(
                Modifier.sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                    .size(
                        width = buttonWidth.coerceAtLeast(0.dp),
                        height = buttonHeight.coerceAtLeast(0.dp)
                    )
            )
        }
    }
}

internal fun playPauseIcon(session: MediaSessionModel?): ImageVector =
    with(Icons.Filled) {
        if (session?.state == MediaSessionState.Playing) Pause else PlayArrow
    }

@StringRes
internal fun playPauseDescription(session: MediaSessionModel?): Int =
    if (session?.state == MediaSessionState.Playing) {
        R.string.controls_media_button_pause
    } else {
        R.string.controls_media_button_play
    }
