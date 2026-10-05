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

package com.android.systemui.qs.ax.ui.grid

import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.mechanics.TileRevealFlag
import com.android.mechanics.compose.modifier.verticalTactileSurfaceReveal
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ax.fragment.viewmodel.AxQsFragmentComposeViewModel
import com.android.systemui.qs.ax.pressfeedback.axPressFeedback
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridValue
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import kotlinx.coroutines.launch

@Composable
fun ContentScope.AxStackedTileWidget(
    tiles: List<TileViewModel>,
    item: AxQsGridItem<AxQsGridValue>,
    detailsViewModel: DetailsViewModel?,
    viewModel: AxQsFragmentComposeViewModel,
    isClickable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val distinctTiles = remember(tiles) { tiles.distinctBy { it.spec.spec } }
    if (distinctTiles.isEmpty()) return

    val view = LocalView.current
    val cellConfig = LocalAxQsCellConfig.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { distinctTiles.size })
    val tileShape = RoundedCornerShape(cellConfig.largeCornerRadius)
    val tileBackgroundColor = AxTileColorsDefaults.backgroundTileColors()
    val animatedBgColor by animateColorAsState(tileBackgroundColor, label = "AxStackedTileBg")

    val marginBottom = with(LocalDensity.current) { QuickSettingsShade.Dimensions.Padding.toPx() }
    val surfaceRevealModifier =
        if (TileRevealFlag.isEnabled) {
            Modifier.verticalTactileSurfaceReveal(deltaY = marginBottom, label = "StackedTile")
        } else {
            Modifier
        }

    TileListener(
        tiles = distinctTiles,
        listeningEnabled = { isClickable },
    )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(tileShape)
                .background(animatedBgColor)
                .interceptParentTouch(enabled = isClickable && distinctTiles.size > 1, view = view)
                .then(surfaceRevealModifier),
        contentAlignment = Alignment.Center,
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = isClickable && distinctTiles.size > 1,
            beyondViewportPageCount = 1,
            key = { index -> "${item.id}_${distinctTiles.getOrNull(index)?.spec?.spec ?: index}" },
        ) { pageIndex ->
            val tile = distinctTiles.getOrNull(pageIndex) ?: return@VerticalPager
            AxStackedTilePageContent(
                tile = tile,
                detailsViewModel = detailsViewModel,
                viewModel = viewModel,
                isClickable = isClickable,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (distinctTiles.size > 1) {
            AxVerticalDotIndicator(
                pageCount = distinctTiles.size,
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
}

@Composable
private fun AxStackedTilePageContent(
    tile: TileViewModel,
    detailsViewModel: DetailsViewModel?,
    viewModel: AxQsFragmentComposeViewModel,
    isClickable: Boolean,
    modifier: Modifier = Modifier,
) {
    val res = LocalView.current.resources
    val uiState by
        produceState(tile.currentState.toUiState(res), tile, res) {
            tile.state.collect { value = it.toUiState(res) }
        }
    val icon by
        produceState(tile.currentState.toIconProvider(), tile) {
            tile.state.collect { value = it.toIconProvider() }
        }
    val effectiveClickable = isClickable && uiState.state != STATE_UNAVAILABLE
    val isDualTarget = uiState.handlesSecondaryClick
    val colors =
        AxTileColorsDefaults.getColorForState(
            uiState = uiState,
            iconOnly = false,
            isDualTarget = isDualTarget,
            is2x2 = true,
        )
    val interactionSource = remember { MutableInteractionSource() }
    val hapticsViewModel: TileHapticsViewModel? =
        rememberViewModel(traceName = "TileHapticsViewModel") {
            viewModel.quickQuickSettingsViewModel.tileHapticsViewModelFactoryProvider
                ?.getHapticsViewModelFactory()
                ?.create(tile)
        }

    val handleClick = {
        val hasDetails =
            QsDetailedView.isEnabled &&
                detailsViewModel?.onTileClicked(tile.spec) == true
        if (!hasDetails) {
            tile.mainClick(null)
            hapticsViewModel?.setTileInteractionState(
                TileHapticsViewModel.TileInteractionState.CLICKED
            )
        }
    }

    val handleLongClick: (() -> Unit)? =
        {
            hapticsViewModel?.setTileInteractionState(
                TileHapticsViewModel.TileInteractionState.LONG_CLICKED
            )
            tile.settingsClick(null)
        }.takeIf { uiState.handlesLongClick }

    val handleToggleClick: (() -> Unit)? =
        if (isDualTarget) {
            {
                hapticsViewModel?.setTileInteractionState(
                    TileHapticsViewModel.TileInteractionState.CLICKED
                )
                tile.toggleClick()
            }
        } else {
            null
        }

    val clickEffectModifier = Modifier.axPressFeedback(interactionSource, enabled = effectiveClickable)

    Box(
        modifier =
            modifier
                .then(clickEffectModifier)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = effectiveClickable,
                    onClick = handleClick,
                    onLongClick = handleLongClick,
                ),
    ) {
        AxLargeTileContent(
            label = uiState.label,
            secondaryLabel = uiState.secondaryLabel,
            iconProvider = { getAxTileIcon(icon) },
            sideDrawable = uiState.sideDrawable,
            colors = colors,
            squishiness = { 1f },
            tileState = uiState.state,
            span = AxQsSpan(columns = 2, rows = 2),
            isDualTarget = isDualTarget,
            toggleClick = handleToggleClick,
            onLongClick = handleLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ContentScope.AxStackedTile(
    tiles: List<TileViewModel>,
    item: AxQsGridItem<AxQsGridValue>,
    detailsViewModel: DetailsViewModel?,
    viewModel: AxQsFragmentComposeViewModel,
    isClickable: Boolean = true,
    isEditing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AxStackedTileWidget(
        tiles = tiles,
        item = item,
        detailsViewModel = detailsViewModel,
        viewModel = viewModel,
        isClickable = isClickable,
        modifier = modifier,
    )
}

@Composable
internal fun ContentScope.AxLiveStackedTile(
    tiles: List<TileViewModel>,
    item: AxQsGridItem<AxQsGridValue>,
    detailsViewModel: DetailsViewModel?,
    viewModel: AxQsFragmentComposeViewModel,
    isClickable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AxStackedTileWidget(
        tiles = tiles,
        item = item,
        detailsViewModel = detailsViewModel,
        viewModel = viewModel,
        isClickable = isClickable,
        modifier = modifier,
    )
}

@Composable
fun AxVerticalDotIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    onDotClick: ((Int) -> Unit)? = null,
) {
    if (pageCount <= 1) return
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotColor by
                animateColorAsState(
                    targetValue = if (isActive) activeColor else inactiveColor,
                    label = "AxStackDotColor",
                )
            val dotSize by
                animateDpAsState(
                    targetValue = if (isActive) 5.dp else 4.dp,
                    label = "AxStackDotSize",
                )
            val dotClickableModifier =
                if (onDotClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        onDotClick(index)
                    }
                } else {
                    Modifier
                }
            Box(
                modifier =
                    Modifier.size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                        .then(dotClickableModifier)
            )
        }
    }
}

internal fun Modifier.interceptParentTouch(enabled: Boolean, view: View): Modifier {
    if (!enabled) return this
    return pointerInput(view) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
