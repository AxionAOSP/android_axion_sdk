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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.android.compose.animation.Expandable as ExpandableContainer
import com.android.compose.animation.rememberExpandableController
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxMediaCard(
    viewModel: AxMediaViewModel,
    session: MediaSessionModel?,
    lastMediaPackage: String?,
    span: AxQsSpan,
    interactive: Boolean,
    allowGuts: Boolean,
    surface: AxMediaSurface,
    hasMultipleSessions: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = AxMediaTokens.MediaCardShape
    val gutsVisible = allowGuts && session?.let(viewModel::isGutsVisible) == true
    val artwork = session?.background?.takeIf { span.columns > 1 }
    val isQsGrid = surface == AxMediaSurface.CONTROL
    val theme = rememberAxMediaTheme(session = session, surface = surface)
    val clickLabel =
        if (session != null) {
            stringResource(
                SysuiR.string.controls_media_playing_item_description,
                session.title,
                session.subtitle,
                session.appName
            )
        } else if (lastMediaPackage != null) {
            stringResource(R.string.ax_qs_media_open_last_app)
        } else {
            null
        }
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardPressScale by
        animateFloatAsState(
            targetValue = if (isCardPressed) 0.985f else 1f,
            animationSpec = AxMediaTokens.ButtonPressSpring,
            label = "AxMediaCardPressScale"
        )
    val isNonQsGrid = surface != AxMediaSurface.CONTROL
    val containerColor = if (isNonQsGrid) Color.Transparent else theme.containerBackground
    ExpandableContainer(
        controller = rememberExpandableController(color = { containerColor }, shape = shape),
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = cardPressScale
                    scaleY = cardPressScale
                }
                .fillMaxSize()
                .clip(shape),
        onClick = null,
        onClickLabel = null,
        defaultMinSize = false,
        useModifierBasedImplementation = true
    ) { expandable ->
        Box(
            Modifier.fillMaxSize().combinedClickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    enabled = interactive && (session != null || lastMediaPackage != null),
                    onClick = {
                        if (!gutsVisible) {
                            if (session != null) {
                                viewModel.openSession(session, expandable)
                            } else {
                                viewModel.openLastMediaApp(expandable)
                            }
                        }
                    },
                    onClickLabel = clickLabel,
                    onLongClick =
                        if (allowGuts) {
                            {
                                if (gutsVisible) {
                                    viewModel.closeGuts()
                                } else if (session != null) {
                                    viewModel.showGuts(session)
                                }
                            }
                        } else {
                            null
                        }
                )
            )
            AnimatedContent(
                targetState = gutsVisible,
                transitionSpec = {
                    fadeIn(
                        tween(AxMediaTokens.IndicatorFadeInDurationMs)
                    ) togetherWith fadeOut(tween(120))
                },
                label = "AxMediaGuts",
                modifier = Modifier.fillMaxSize()
            ) { showGuts ->
                if (showGuts && session != null) {
                    MediaGuts(
                        session = session,
                        viewModel = viewModel,
                        colors = theme.colors,
                        compact = span.columns == 1,
                        surface = surface
                    )
                } else if (isQsGrid) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val gridConfig =
                            remember(span, maxHeight) {
                                AxQsMediaGridLayoutConfig.resolve(span, maxHeight)
                            }
                        val songKey =
                            session?.let { "${it.key}:${it.appName}:${it.title}:${it.subtitle}" }
                                .orEmpty()
                        when (gridConfig.variant) {
                            AxQsMediaLayoutVariant.AxStudio4x2 ->
                                AxStudio4x2MediaContent(
                                    session = session,
                                    viewModel = viewModel,
                                    colors = theme.colors,
                                    artwork = artwork,
                                    songKey = songKey,
                                    interactive = interactive,
                                    hasMultipleSessions = hasMultipleSessions
                                )
                            AxQsMediaLayoutVariant.AxSquare2x2 ->
                                AxSquare2x2MediaContent(
                                    session = session,
                                    viewModel = viewModel,
                                    colors = theme.colors,
                                    artwork = artwork,
                                    songKey = songKey,
                                    interactive = interactive,
                                    hasMultipleSessions = hasMultipleSessions
                                )
                            AxQsMediaLayoutVariant.AxCompact,
                            AxQsMediaLayoutVariant.AxHalfRow2x1 ->
                                AxOneRowMediaContent(
                                    session = session,
                                    viewModel = viewModel,
                                    colors = theme.colors,
                                    artwork = artwork,
                                    songKey = songKey,
                                    config = gridConfig,
                                    interactive = interactive,
                                    hasMultipleSessions = hasMultipleSessions
                                )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        if (session != null) {
                            val songKey = "${session.appName}:${session.title}:${session.subtitle}"
                            MediaArtwork(
                                artwork = artwork,
                                songKey = songKey,
                                overlayColor =
                                    if (isNonQsGrid) Color.Black else theme.overlayColor
                            )
                        }
                        ExpandedMediaContent(
                            session = session,
                            viewModel = viewModel,
                            span = span,
                            colors = theme.colors,
                            interactive = interactive,
                            hasMultipleSessions = hasMultipleSessions
                        )
                }
            }
        }
    }
}
