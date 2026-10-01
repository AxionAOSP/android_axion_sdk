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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R

private val MediaNavigationIconSize = 24.dp

@Composable
internal fun MediaControls(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    interactive: Boolean,
    actionSize: Dp,
    maxActions: Int = 3,
    spreadCoreActions: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val spacing = dimensionResource(R.dimen.qs_media_action_spacing)
    val iconSize =
        when {
            actionSize < 32.dp -> 18.dp
            actionSize < 40.dp -> 22.dp
            else -> 26.dp
        }
    val navigationIconSize =
        minOf(
            when {
                actionSize < 32.dp -> 18.dp
                actionSize < 40.dp -> 22.dp
                else -> 24.dp
            },
            MediaNavigationIconSize,
        )
    val showPrevious = maxActions >= 3
    val showNext = maxActions >= 2
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        with(Icons.Filled) {
            Row(
                horizontalArrangement =
                    if (spreadCoreActions) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
                    },
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier,
            ) {
                if (session != null) {
                    val coreSlots = if (showPrevious) 3 else if (showNext) 2 else 1
                    val additionalActions =
                        session.additionalActions.take((maxActions - coreSlots).coerceAtLeast(0))
                    if (showPrevious) {
                        additionalActions.firstOrNull()?.let { action ->
                            MediaAction(
                                action = action,
                                viewModel = viewModel,
                                width = actionSize,
                                iconSize = iconSize,
                                tint = colors.foreground,
                                interactive = interactive,
                            )
                        }
                    }
                    if (showPrevious) {
                        CoreMediaAction(
                            action = session.leftAction,
                            imageVector = SkipPrevious,
                            descriptionRes = R.string.controls_media_button_prev,
                            viewModel = viewModel,
                            width = actionSize,
                            iconSize = navigationIconSize,
                            tint = colors.foreground,
                            interactive = interactive,
                        )
                    }
                    CoreMediaAction(
                        action = session.playPauseAction,
                        imageVector = playPauseIcon(session),
                        descriptionRes = playPauseDescription(session),
                        viewModel = viewModel,
                        width = actionSize,
                        iconSize = iconSize,
                        tint = colors.foreground,
                        background = Color.Transparent,
                        interactive = interactive,
                    )
                    if (showNext) {
                        CoreMediaAction(
                            action = session.rightAction,
                            imageVector = SkipNext,
                            descriptionRes = R.string.controls_media_button_next,
                            viewModel = viewModel,
                            width = actionSize,
                            iconSize = navigationIconSize,
                            tint = colors.foreground,
                            interactive = interactive,
                        )
                    }
                    if (showPrevious) {
                        additionalActions.drop(1).forEach { action ->
                            MediaAction(
                                action = action,
                                viewModel = viewModel,
                                width = actionSize,
                                iconSize = iconSize,
                                tint = colors.foreground,
                                interactive = interactive,
                            )
                        }
                    }
                } else {
                    if (showPrevious) {
                        PlaceholderMediaAction(
                            imageVector = SkipPrevious,
                            descriptionRes = R.string.controls_media_button_prev,
                            width = actionSize,
                            iconSize = navigationIconSize,
                            tint = colors.foreground,
                        )
                    }
                    PlaceholderMediaAction(
                        imageVector = PlayArrow,
                        descriptionRes = R.string.controls_media_button_play,
                        width = actionSize,
                        iconSize = iconSize,
                        tint = colors.foreground,
                        background = Color.Transparent,
                    )
                    if (showNext) {
                        PlaceholderMediaAction(
                            imageVector = SkipNext,
                            descriptionRes = R.string.controls_media_button_next,
                            width = actionSize,
                            iconSize = navigationIconSize,
                            tint = colors.foreground,
                        )
                    }
                }
            }
        }
    }
}
