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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.edit.AxQsEditTile
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.res.R as SysuiR
import kotlinx.coroutines.launch

@Composable
fun AxEditStackPlaceholderPreview(
    showIndicator: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val tileBackgroundColor = AxTileColorsDefaults.backgroundTileColors()
    val shape = RoundedCornerShape(cellConfig.largeCornerRadius)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(shape)
                .background(tileBackgroundColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = shape,
                )
                .padding(cellConfig.tileEndPadding),
    ) {
        Box(
            modifier =
                Modifier.size(cellConfig.iconContainerSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .align(Alignment.TopStart),
            contentAlignment = Alignment.Center,
        ) {
            SmallTileContent(
                iconProvider = { IconModel.Resource(SysuiR.drawable.ic_error_outline, null) },
                color = MaterialTheme.colorScheme.onSurface,
                size = { cellConfig.iconSize },
            )
        }
        if (showIndicator) {
            AxVerticalDotIndicator(
                pageCount = 3,
                currentPage = 0,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.ax_qs_stacked_tile),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.ax_qs_drag_to_stack),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AxEditStackedTilePreview(
    tiles: List<EditTileViewModel>,
    liveTiles: List<TileViewModel> = emptyList(),
    isSelected: Boolean,
    onRemoveStack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val effectiveCount = if (tiles.isNotEmpty()) tiles.size else liveTiles.size
    val pageCount = if (effectiveCount == 0) 1 else effectiveCount + 1
    val pagerState = key(pageCount) { rememberPagerState(pageCount = { pageCount }) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .interceptParentTouch(enabled = effectiveCount > 1, view = view),
        contentAlignment = Alignment.Center,
    ) {
        if (effectiveCount == 0) {
            AxEditStackPlaceholderPreview(
                showIndicator = false,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true,
                beyondViewportPageCount = 1,
                key = { pageIndex ->
                    when {
                        pageIndex < tiles.size -> tiles[pageIndex].tileSpec.spec
                        pageIndex < liveTiles.size -> liveTiles[pageIndex].spec.spec
                        else -> "add_slot"
                    }
                },
            ) { pageIndex ->
                if (pageIndex < tiles.size) {
                    AxQsEditTile(
                        tile = tiles[pageIndex],
                        span = AxQsSpan(columns = 2, rows = 2),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (pageIndex < liveTiles.size) {
                    val liveTile = liveTiles[pageIndex]
                    val res = LocalView.current.resources
                    val uiState by produceState(liveTile.currentState.toUiState(res), liveTile, res) {
                        liveTile.state.collect { value = it.toUiState(res) }
                    }
                    val icon by produceState(liveTile.currentState.toIconProvider(), liveTile) {
                        liveTile.state.collect { value = it.toIconProvider() }
                    }
                    AxTile(
                        uiState = uiState,
                        iconProvider = { getAxTileIcon(icon) },
                        compact = false,
                        span = AxQsSpan(columns = 2, rows = 2),
                        isClickable = false,
                        isDualTarget = uiState.handlesSecondaryClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AxEditStackPlaceholderPreview(
                        showIndicator = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (pageCount > 1) {
                AxVerticalDotIndicator(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    onDotClick = { targetIndex ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    },
                )
            }
        }

        if (isSelected && effectiveCount > 0) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color(0xFFE53935),
                    contentColor = Color.White,
                    shadowElevation = 0.dp,
                    modifier =
                        Modifier.width(58.dp)
                            .height(32.dp)
                            .clickable(
                                enabled = isSelected && effectiveCount > 0,
                                onClick = onRemoveStack,
                            ),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = stringResource(R.string.ax_qs_remove_stack),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
