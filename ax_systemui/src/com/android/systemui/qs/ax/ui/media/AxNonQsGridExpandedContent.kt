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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun ExpandedMediaContent(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    span: AxQsSpan,
    colors: AxMediaColors,
    interactive: Boolean,
    hasMultipleSessions: Boolean = false
) {
    val title =
        session?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.ax_qs_media_not_playing)
    val subtitle = session?.subtitle.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerMaxWidth = maxWidth
        val containerMaxHeight = maxHeight
        val sizing = remember(containerMaxWidth, containerMaxHeight, span) {
            AxMediaSizing.from(containerMaxWidth, containerMaxHeight, span)
        }
        val maxActions = mediaActionLimit(span.columns)
        val showCoreActions =
            session?.actionButtonLayout != MediaCardActionButtonLayout.SecondaryActionsOnly
        val extraBottomPadding by animateDpAsState(
            targetValue = if (hasMultipleSessions) AxMediaTokens.IndicatorClearanceLarge.coerceAtLeast(0.dp) else 0.dp,
            animationSpec = AxMediaTokens.DynamicPaddingSpring,
            label = "AxExpandedClearancePadding"
        )
        val scale = (containerMaxHeight / 160.dp).coerceIn(0.85f, 1.25f)
        val vTopPadding = ((if (hasMultipleSessions) 10.dp else 12.dp) * scale).coerceIn(8.dp, 16.dp)
        val vBottomPadding = (6.dp * scale).coerceIn(4.dp, 10.dp)

        Column(
            horizontalAlignment = Alignment.Start,
            modifier =
                Modifier.fillMaxWidth()
                    .height(containerMaxHeight.coerceAtLeast(0.dp))
                    .padding(
                        start = sizing.horizontalPadding.coerceAtLeast(0.dp),
                        end = sizing.horizontalPadding.coerceAtLeast(0.dp),
                        top = vTopPadding.coerceAtLeast(0.dp),
                        bottom = (vBottomPadding + extraBottomPadding).coerceAtLeast(0.dp)
                    )
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MediaAppIcon(session = session, size = sizing.headerIconSize, tint = colors.primary)
                Spacer(Modifier.weight(1f))
                MediaOutputChip(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    interactive = interactive,
                    showLabel = true,
                    compact = sizing.outputChipCompact || containerMaxHeight <= 170.dp,
                    modifier = Modifier.widthIn(max = containerMaxWidth * 0.45f)
                )
            }

            Spacer(Modifier.weight(1.0f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                AnimatedMediaText(
                    text = title,
                    color = colors.foreground,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    textAlign = if (session == null) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    AnimatedMediaText(
                        text = subtitle,
                        color = colors.foreground.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.weight(1.8f))

            Column(modifier = Modifier.fillMaxWidth()) {
                MediaSeekBar(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    dense = true,
                    interactive = interactive,
                    modifier = Modifier.fillMaxWidth()
                )
                if (sizing.showTimestamps) {
                    Spacer(Modifier.height(2.dp))
                    MediaTimestamps(session = session, viewModel = viewModel, colors = colors)
                }
            }

            Spacer(Modifier.weight(0.9f))

            val additionalActions = session?.additionalActions.orEmpty().take((maxActions - 3).coerceAtLeast(0))
            val leftSecondary = additionalActions.firstOrNull()
            val rightSecondary = additionalActions.drop(1).firstOrNull()
            val totalActionCount = if (showCoreActions) 3 + additionalActions.size else additionalActions.size
            val actionSpacing = if (totalActionCount > 3) 14.dp else sizing.actionSpacing
            val actionBtnSize = if (hasMultipleSessions) 34.dp else sizing.actionSize
            val heroBtnSize = if (hasMultipleSessions) 38.dp else sizing.heroActionSize

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leftSecondary != null && showCoreActions) {
                    MediaAction(
                        action = leftSecondary,
                        viewModel = viewModel,
                        width = actionBtnSize,
                        iconSize = sizing.actionIconSize,
                        tint = colors.foreground,
                        interactive = interactive
                    )
                    Spacer(Modifier.width(actionSpacing))
                }

                if (showCoreActions) {
                    ExpandedNavigationAction(
                        action = session?.leftAction,
                        placeholderDescription = SysuiR.string.controls_media_button_prev,
                        viewModel = viewModel,
                        colors = colors,
                        interactive = interactive,
                        size = actionBtnSize,
                        iconSize = sizing.actionIconSize,
                        imageVector = Icons.Default.SkipPrevious
                    )
                    Spacer(Modifier.width(actionSpacing))
                    CoreMediaAction(
                        action = session?.playPauseAction,
                        imageVector = playPauseIcon(session),
                        descriptionRes = playPauseDescription(session),
                        animatedIconRes = SysuiR.drawable.ic_media_play_button,
                        animatedIconAtEnd = session?.state == MediaSessionState.Playing,
                        viewModel = viewModel,
                        width = heroBtnSize,
                        height = heroBtnSize,
                        iconSize = sizing.heroIconSize,
                        tint = colors.foreground,
                        background = Color.Transparent,
                        interactive = interactive
                    )
                    Spacer(Modifier.width(actionSpacing))
                    ExpandedNavigationAction(
                        action = session?.rightAction,
                        placeholderDescription = SysuiR.string.controls_media_button_next,
                        viewModel = viewModel,
                        colors = colors,
                        interactive = interactive,
                        size = actionBtnSize,
                        iconSize = sizing.actionIconSize,
                        imageVector = Icons.Default.SkipNext
                    )
                } else if (additionalActions.isNotEmpty()) {
                    additionalActions.forEachIndexed { index, action ->
                        if (index > 0) Spacer(Modifier.width(actionSpacing))
                    MediaAction(
                        action = action,
                        viewModel = viewModel,
                        width = actionBtnSize,
                        iconSize = sizing.actionIconSize,
                        tint = colors.foreground,
                        interactive = interactive
                    )
                }
            }

            if (rightSecondary != null && showCoreActions) {
                Spacer(Modifier.width(actionSpacing))
                MediaAction(
                    action = rightSecondary,
                    viewModel = viewModel,
                    width = actionBtnSize,
                    iconSize = sizing.actionIconSize,
                    tint = colors.foreground,
                    interactive = interactive
                )
            }
            }
        }
    }
}
