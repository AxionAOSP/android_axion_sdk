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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel

@Composable
internal fun AxOneRowMediaContent(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    artwork: IconModel?,
    songKey: String,
    config: AxQsMediaGridLayoutConfig,
    interactive: Boolean,
    hasMultipleSessions: Boolean = false
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val densityScale = LocalTileScale.current
        val scale =
            (maxHeight / AxMediaTokens.OneRowReferenceHeight).coerceIn(
                0.8f,
                1.15f
            ) * densityScale
        val isHalfRow = maxWidth < AxMediaTokens.HalfRowThresholdWidth
        val albumSize =
            if (isHalfRow) {
                (36.dp * scale).coerceIn(30.dp, 40.dp)
            } else {
                (config.albumSize * scale).coerceIn(36.dp, 52.dp)
            }
        val hPadding =
            if (isHalfRow) {
                (8.dp * scale).coerceIn(6.dp, 10.dp)
            } else {
                (config.horizontalPadding * scale).coerceIn(8.dp, 14.dp)
            }
        val vPadding = (config.verticalPadding * scale).coerceIn(4.dp, 8.dp)
        val extraBottomPadding by
            animateDpAsState(
                targetValue =
                    if (hasMultipleSessions) {
                        (AxMediaTokens.IndicatorClearanceOneRow * scale).coerceAtLeast(0.dp)
                    } else {
                        0.dp
                    },
                animationSpec = AxMediaTokens.DynamicPaddingSpring,
                label = "AxOneRowBottomPadding"
            )
        val titleSize =
            if (isHalfRow) {
                (12.5f * scale).coerceIn(11f, 13.5f).sp
            } else {
                (13.5f * scale).coerceAtLeast(11f).sp
            }
        val subtitleSize =
            if (isHalfRow) {
                (10f * scale).coerceIn(9f, 11f).sp
            } else {
                (11f * scale).coerceAtLeast(9.5f).sp
            }
        val trackPadding =
            if (isHalfRow) {
                (6.dp * scale).coerceIn(4.dp, 8.dp)
            } else {
                (10.dp * scale).coerceIn(6.dp, 12.dp)
            }
        val actionSize =
            if (isHalfRow) {
                (28.dp * scale).coerceIn(24.dp, 32.dp)
            } else {
                (32.dp * scale).coerceIn(28.dp, 38.dp)
            }
        val cornerRadius =
            (albumSize * AxMediaTokens.AlbumSquircleRadiusRatio).coerceIn(
                AxMediaTokens.MinAlbumSquircleRadius,
                AxMediaTokens.MaxAlbumSquircleRadius
            )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        start = hPadding.coerceAtLeast(0.dp),
                        end = hPadding.coerceAtLeast(0.dp),
                        top = vPadding.coerceAtLeast(0.dp),
                        bottom = (vPadding + extraBottomPadding).coerceAtLeast(0.dp)
                    )
        ) {
            AxMediaAlbumCover(
                artwork = artwork,
                songKey = songKey,
                session = session,
                size = albumSize,
                cornerRadius = cornerRadius
            )
            AxMediaTrackInfo(
                session = session,
                colors = colors,
                titleStyle =
                    MaterialTheme.typography.titleSmall.copy(
                        fontSize = titleSize,
                        lineHeight = titleSize * 1.25f
                    ),
                subtitleStyle =
                    MaterialTheme.typography.bodySmall.copy(
                        fontSize = subtitleSize,
                        lineHeight = subtitleSize * 1.25f
                    ),
                modifier = Modifier.weight(1f).padding(horizontal = trackPadding.coerceAtLeast(0.dp))
            )
            if (config.showRouteButton && !isHalfRow) {
                AxMediaRouteButton(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    interactive = interactive,
                    size = (30.dp * scale).coerceIn(26.dp, 34.dp),
                    modifier = Modifier.padding(end = (4.dp * scale).coerceIn(2.dp, 6.dp).coerceAtLeast(0.dp))
                )
            }
            MediaControls(
                session = session,
                viewModel = viewModel,
                colors = colors,
                interactive = interactive,
                maxActions = config.maxActions,
                actionSize = actionSize
            )
        }
    }
}
