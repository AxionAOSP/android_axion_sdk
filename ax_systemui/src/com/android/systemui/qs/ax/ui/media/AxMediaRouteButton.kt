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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Expandable as ExpandableContainer
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxMediaRouteButton(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val outputDescription =
        session?.outputDevice?.name?.takeUnless { it.isBlank() || it == "null" }
            ?: stringResource(SysuiR.string.ax_dynamic_bar_media_output)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.88f else 1f,
            animationSpec = AxMediaTokens.ButtonPressSpring,
            label = "AxMediaRoutePressScale",
        )
    val iconSize =
        (size * AxMediaTokens.RouteButtonIconRatio).coerceIn(
            AxMediaTokens.MinRouteIconSize,
            AxMediaTokens.MaxRouteIconSize,
        )

    ExpandableContainer(
        controller =
            rememberExpandableController(
                color = { Color.Transparent },
                shape = CircleShape,
            ),
        modifier = modifier.size(size),
        defaultMinSize = false,
        useModifierBasedImplementation = true,
    ) { expandable ->
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(CircleShape)
                    .indication(interactionSource, ripple(bounded = true, radius = size / 2))
                    .semantics { contentDescription = outputDescription }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = interactive && session != null,
                    ) {
                        session?.let { viewModel.openOutput(it.outputDevice, expandable) }
                    },
        ) {
            if (session != null) {
                Icon(
                    icon = session.outputDevice.icon,
                    tint = colors.foreground,
                    modifier = Modifier.size(iconSize),
                )
            } else {
                MaterialIcon(
                    painter = painterResource(SysuiR.drawable.ic_phone_expressive),
                    contentDescription = null,
                    tint = colors.foreground,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
internal fun MediaAppIcon(
    session: MediaSessionModel?,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        if (session != null) {
            Icon(
                icon = session.appIcon,
                tint = tint,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            MaterialIcon(
                painter = painterResource(SysuiR.drawable.ic_music_note),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun MediaOutputChip(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    showLabel: Boolean,
    compact: Boolean,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val outputDescription =
        session?.outputDevice?.name?.takeUnless { it.isBlank() || it == "null" }
            ?: stringResource(SysuiR.string.ax_dynamic_bar_media_output)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by
        animateFloatAsState(
            targetValue = if (isPressed) 0.90f else 1f,
            animationSpec = AxMediaTokens.ButtonPressSpring,
            label = "AxMediaChipPressScale",
        )
    val chipHeight = if (compact) 20.dp else 24.dp
    val iconSize = if (compact) 12.dp else 13.dp

    ExpandableContainer(
        controller =
            rememberExpandableController(color = { Color.Transparent }, shape = CircleShape),
        modifier = modifier,
        defaultMinSize = false,
        useModifierBasedImplementation = true,
    ) { expandable ->
        val chipBackground = colors.primary
        val contentColor = colors.onPrimary
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(CircleShape)
                    .background(chipBackground)
                    .indication(interactionSource, ripple())
                    .heightIn(min = chipHeight)
                    .semantics { contentDescription = outputDescription }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = interactive && session != null,
                    ) {
                        session?.let { viewModel.openOutput(it.outputDevice, expandable) }
                    }
                    .then(
                        if (showLabel) {
                            Modifier.padding(
                                horizontal = if (compact) 8.dp else 10.dp,
                                vertical = if (compact) 2.dp else 3.dp,
                            )
                        } else {
                            Modifier.size(chipHeight)
                        },
                    ),
        ) {
            if (session != null) {
                Icon(
                    icon = session.outputDevice.icon,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize),
                )
            } else {
                MaterialIcon(
                    painter = painterResource(SysuiR.drawable.ic_phone_expressive),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (showLabel) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = label ?: outputDescription,
                    color = contentColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
