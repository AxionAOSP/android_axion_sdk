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

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxStudio4x2MediaContent(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    artwork: IconModel?,
    songKey: String,
    interactive: Boolean,
    hasMultipleSessions: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val densityScale = LocalTileScale.current
        val albumSize = (maxHeight * 0.34f).coerceIn(52.dp, 68.dp) * densityScale
        val vPadding = (10.dp * (maxHeight / 180.dp)).coerceIn(8.dp, 14.dp)
        val extraBottomPadding by
            animateDpAsState(
                targetValue =
                    if (hasMultipleSessions) AxMediaTokens.IndicatorClearanceLarge.coerceAtLeast(0.dp) else 0.dp,
                animationSpec = AxMediaTokens.DynamicPaddingSpring,
                label = "AxStudio4x2BottomPadding"
            )
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier.fillMaxWidth()
                    .height(maxHeight.coerceAtLeast(0.dp))
                    .padding(horizontal = 14.dp, vertical = vPadding.coerceAtLeast(0.dp))
                    .padding(bottom = extraBottomPadding.coerceAtLeast(0.dp))
        ) {
            AxLargeMediaHeaderRow(
                session = session,
                artwork = artwork,
                songKey = songKey,
                colors = colors,
                albumSize = albumSize
            )
            if (session?.canBeScrubbed == true) {
                AxLargeMediaProgressRow(
                    session = session,
                    viewModel = viewModel,
                    colors = colors,
                    interactive = interactive
                )
            }
            AxLargeMediaActionsRow(
                session = session,
                viewModel = viewModel,
                colors = colors,
                interactive = interactive
            )
        }
    }
}

@Composable
private fun AxLargeMediaHeaderRow(
    session: MediaSessionModel?,
    artwork: IconModel?,
    songKey: String,
    colors: AxMediaColors,
    albumSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        AxMediaAlbumCover(
            artwork = artwork,
            songKey = songKey,
            session = session,
            size = albumSize,
            cornerRadius = 12.dp
        )
        AxMediaTrackInfo(
            session = session,
            colors = colors,
            titleStyle = MaterialTheme.typography.titleMedium,
            subtitleStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        MediaAppIcon(
            session = session,
            size = 26.dp,
            tint = colors.foreground.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun AxLargeMediaProgressRow(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = session?.let(viewModel::progress) ?: 0f
    val durationMs = session?.durationMs ?: 0L
    val elapsedMs = (progress * durationMs).toLong().coerceAtLeast(0L)
    val remainingMs = (durationMs - elapsedMs).coerceAtLeast(0L)
    val elapsedTime = DateUtils.formatElapsedTime(elapsedMs / 1000L)
    val remainingTime = "-" + DateUtils.formatElapsedTime(remainingMs / 1000L)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = elapsedTime,
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground.copy(alpha = 0.72f)
        )
        MediaSeekBar(
            session = session,
            viewModel = viewModel,
            colors = colors,
            dense = true,
            interactive = interactive,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text(
            text = remainingTime,
            style = MaterialTheme.typography.labelSmall,
            color = colors.foreground.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun AxLargeMediaActionsRow(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val extraAction = session?.additionalActions?.firstOrNull()
        if (extraAction != null) {
            MediaAction(
                action = extraAction,
                viewModel = viewModel,
                width = 36.dp,
                iconSize = 22.dp,
                tint = colors.foreground,
                interactive = interactive
            )
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoreMediaAction(
                action = session?.leftAction,
                imageVector = Icons.Filled.SkipPrevious,
                descriptionRes = SysuiR.string.controls_media_button_prev,
                viewModel = viewModel,
                width = 36.dp,
                iconSize = 24.dp,
                tint = colors.foreground,
                interactive = interactive
            )
            CoreMediaAction(
                action = session?.playPauseAction,
                imageVector = playPauseIcon(session),
                descriptionRes = playPauseDescription(session),
                viewModel = viewModel,
                width = 44.dp,
                iconSize = 28.dp,
                tint = colors.foreground,
                background = Color.Transparent,
                interactive = interactive
            )
            CoreMediaAction(
                action = session?.rightAction,
                imageVector = Icons.Filled.SkipNext,
                descriptionRes = SysuiR.string.controls_media_button_next,
                viewModel = viewModel,
                width = 36.dp,
                iconSize = 24.dp,
                tint = colors.foreground,
                interactive = interactive
            )
        }
        AxMediaRouteButton(
            session = session,
            viewModel = viewModel,
            colors = colors,
            interactive = interactive
        )
    }
}
