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
internal fun AxNonQsMediaCard(
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
    val isGutsAllowed = allowGuts && surface.dismissible
    val gutsVisible = isGutsAllowed && session?.let(viewModel::isGutsVisible) == true
    val artwork = session?.background?.takeIf { span.columns > 1 }
    val hasMediaArt = artwork != null
    val theme = rememberAxNonQsMediaTheme(session = session, hasMediaArt = hasMediaArt)
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
            label = "AxNonQsMediaCardPressScale"
        )
    val containerColor =
        if (hasMediaArt && !gutsVisible) Color.Transparent else theme.containerBackground

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
                    if (isGutsAllowed) {
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
        ) {
            AnimatedContent(
                targetState = gutsVisible,
                transitionSpec = {
                    fadeIn(
                        tween(AxMediaTokens.IndicatorFadeInDurationMs)
                    ) togetherWith fadeOut(tween(120))
                },
                label = "AxNonQsMediaGuts",
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
                } else {
                    Box(Modifier.fillMaxSize()) {
                        if (session != null && hasMediaArt) {
                            val songKey = "${session.appName}:${session.title}:${session.subtitle}"
                            MediaArtwork(
                                artwork = artwork,
                                songKey = songKey,
                                overlayColor = Color.Black
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
}
