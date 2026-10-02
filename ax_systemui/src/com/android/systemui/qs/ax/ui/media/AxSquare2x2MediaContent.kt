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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxSquare2x2MediaContent(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    artwork: IconModel?,
    songKey: String,
    interactive: Boolean,
    hasMultipleSessions: Boolean = false
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val densityScale = LocalTileScale.current
        val widthScale = maxWidth / AxMediaTokens.Square2x2ReferenceWidth
        val heightScale = maxHeight / AxMediaTokens.Square2x2ReferenceHeight
        val scale =
            minOf(widthScale, heightScale).coerceIn(
                AxMediaTokens.MinTileScale,
                1.2f
            ) * densityScale
        val albumSize = (maxHeight * 0.30f).coerceIn(38.dp, 50.dp)
        val albumRadius =
            (albumSize * AxMediaTokens.AlbumSquircleRadiusRatio).coerceIn(
                AxMediaTokens.MinAlbumSquircleRadius,
                12.dp
            )
        val buttonWidth = (36.dp * scale).coerceIn(28.dp, 38.dp)
        val heroButtonWidth = (42.dp * scale).coerceIn(34.dp, 44.dp)
        val iconSize = (20.dp * scale).coerceIn(16.dp, 22.dp)
        val heroIconSize = (24.dp * scale).coerceIn(20.dp, 26.dp)
        val routeButtonSize = (32.dp * scale).coerceIn(26.dp, 36.dp)
        val hPadding = (12.dp * scale).coerceIn(8.dp, 14.dp)
        val vPadding = (10.dp * scale).coerceIn(6.dp, 12.dp)
        val extraBottomPadding by
            animateDpAsState(
                targetValue =
                    if (hasMultipleSessions) {
                        (AxMediaTokens.IndicatorClearanceLarge * scale).coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    },
                animationSpec = AxMediaTokens.DynamicPaddingSpring,
                label = "AxSquare2x2BottomPadding"
            )

        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
            modifier =
                Modifier.fillMaxWidth()
                    .height(maxHeight.coerceAtLeast(0.dp))
                    .padding(
                        horizontal = hPadding.coerceAtLeast(0.dp),
                        vertical = vPadding.coerceAtLeast(0.dp)
                    )
                    .padding(bottom = extraBottomPadding.coerceAtLeast(0.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AxMediaAlbumCover(
                    artwork = artwork,
                    songKey = songKey,
                    session = session,
                    size = albumSize,
                    cornerRadius = albumRadius
                )
                AxMediaRouteButton(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    interactive = interactive,
                    size = routeButtonSize
                )
            }
            Spacer(Modifier.height((6.dp * scale).coerceIn(4.dp, 10.dp)))
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                val title =
                    session?.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.ax_qs_media_not_playing)
                val subtitle = session?.subtitle.orEmpty()
                val titleFontSize = (13.5f * scale).coerceIn(11.5f, 15f).sp
                val subtitleFontSize = (11f * scale).coerceIn(9.5f, 12f).sp
                AnimatedMediaText(
                    text = title,
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontSize = titleFontSize,
                            lineHeight = titleFontSize * 1.25f
                        ),
                    color = colors.foreground
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height((2.dp * scale).coerceIn(1.dp, 4.dp)))
                    AnimatedMediaText(
                        text = subtitle,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontSize = subtitleFontSize,
                                lineHeight = subtitleFontSize * 1.25f
                            ),
                        color = colors.foreground.copy(alpha = AxMediaTokens.SubtitleAlpha)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                with(Icons.Filled) {
                    CoreMediaAction(
                        action = session?.leftAction,
                        imageVector = SkipPrevious,
                        descriptionRes = SysuiR.string.controls_media_button_prev,
                        viewModel = viewModel,
                        width = buttonWidth,
                        iconSize = iconSize,
                        tint = colors.foreground,
                        interactive = interactive
                    )
                    CoreMediaAction(
                        action = session?.playPauseAction,
                        imageVector = playPauseIcon(session),
                        descriptionRes = playPauseDescription(session),
                        viewModel = viewModel,
                        width = heroButtonWidth,
                        iconSize = heroIconSize,
                        tint = colors.foreground,
                        background = Color.Transparent,
                        interactive = interactive
                    )
                    CoreMediaAction(
                        action = session?.rightAction,
                        imageVector = SkipNext,
                        descriptionRes = SysuiR.string.controls_media_button_next,
                        viewModel = viewModel,
                        width = buttonWidth,
                        iconSize = iconSize,
                        tint = colors.foreground,
                        interactive = interactive
                    )
                }
            }
        }
    }
}
