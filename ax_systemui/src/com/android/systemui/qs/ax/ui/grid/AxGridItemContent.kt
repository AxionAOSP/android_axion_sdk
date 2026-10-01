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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.qs.ax.fragment.viewmodel.AxQsFragmentComposeViewModel
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridValue
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.ax.ui.controls.AxQsControlPreview
import com.android.systemui.qs.ax.ui.controls.axQsControlShape
import com.android.systemui.qs.ax.ui.edit.AxQsEditTile
import com.android.systemui.qs.ax.ui.edit.axQsDragSource
import com.android.systemui.qs.ax.ui.panels.AxQsExpansionMode
import com.android.systemui.qs.ax.ui.panels.axQsItemReveal
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Removable
import com.android.systemui.qs.panels.ui.compose.selection.TileState.Selected
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

@Composable
fun ContentScope.AxGridItemContent(
    item: AxQsGridItem<AxQsGridValue>,
    isEditing: Boolean,
    editController: AxGridEditController,
    layout: AxQsLayout,
    columns: Int,
    separateMode: Boolean,
    isQsExpandingOrVisible: Boolean,
    expansionMode: AxQsExpansionMode,
    viewModel: AxQsFragmentComposeViewModel,
    axQsViewModel: AxQsViewModel,
    mediaViewModel: AxMediaViewModel,
    detailsViewModel: DetailsViewModel,
    brightnessSliderViewModel: BrightnessSliderViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel,
    listening: () -> Boolean,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val qqsMaxRows = axQsViewModel.qqsMaxRows
    val row = item.position?.row ?: 0
    val isQqsItem = !separateMode && row < qqsMaxRows && (row + item.span.rows <= qqsMaxRows)

    if (isEditing) {
        val isSelected = editController.selectedId.value == item.id
        val isSlider = (item.value as? AxQsGridValue.Control)?.control?.isSlider == true
        val isStack = item.value is AxQsGridValue.Stack
        val resizable = item.minSpan != item.maxSpan
        val tileState = if (isSelected) Selected else Removable
        val selectionColor = if (isSlider) Color.White else MaterialTheme.colorScheme.primary
        val selectionShape =
            when (val v = item.value) {
                is AxQsGridValue.Tile ->
                    when {
                        item.span.columns == 1 && item.span.rows == 1 -> CircleShape
                        item.span.columns > 1 && item.span.rows == 1 -> RoundedCornerShape(percent = 50)
                        else -> RoundedCornerShape(cellConfig.largeCornerRadius)
                    }
                is AxQsGridValue.Control ->
                    axQsControlShape(
                        v.control,
                        item.span,
                        axQsViewModel.verticalSliderStyle(layout, v.control),
                    )
                is AxQsGridValue.Stack -> RoundedCornerShape(cellConfig.largeCornerRadius)
            }
        val isStackHoverTarget = editController.listState.hoveredStackTargetId == item.id
        val stackHoverScale by
            animateFloatAsState(
                targetValue = if (isStackHoverTarget) 0.94f else 1f,
                animationSpec =
                    tween(
                        durationMillis = AxQsTokens.Animation.AX_RESIZE_DURATION_MS,
                        easing = AxQsTokens.Animation.AX_RESIZE_EASING,
                    ),
                label = "AxStackHoverScale",
            )
        val primaryBorderColor = MaterialTheme.colorScheme.primary
        val hoverBorderModifier =
            if (isStackHoverTarget) {
                Modifier.border(width = 2.dp, color = primaryBorderColor, shape = selectionShape)
            } else {
                Modifier
            }

        val targetHeight = cellConfig.itemHeight(item.span)
        val is1x1 = item.span.columns == 1 && item.span.rows == 1
        val itemModifier =
            when {
                is1x1 -> Modifier.size(cellConfig.iconTileSize)
                isSlider -> Modifier.width(cellConfig.iconTileSize).height(targetHeight)
                else -> Modifier.fillMaxWidth().height(targetHeight)
            }

        val resizeHandle =
            if (resizable) {
                Modifier.axQsResizeHandle(
                    id = item.id,
                    span = { editController.listState.item(item.id)?.span ?: item.span },
                    cellConfig = cellConfig,
                    resolveSpan = { startSpan, columnDelta, rowDelta ->
                        when (item.value) {
                            is AxQsGridValue.Control ->
                                item.value.control.resizeSpan(
                                    startSpan = startSpan,
                                    columnDelta = columnDelta,
                                    rowDelta = rowDelta,
                                    columns = columns,
                                )
                            is AxQsGridValue.Tile -> {
                                val maxCols = minOf(2, columns)
                                val newCols =
                                    (startSpan.columns + columnDelta).coerceIn(1, maxCols)
                                val newRows =
                                    (startSpan.rows + rowDelta).coerceIn(1, 2)
                                val resolved = AxQsSpan(newCols, newRows)
                                if (resolved == AxQsSpan(1, 2)) {
                                    if (columnDelta > 0) AxQsSpan(2, 2) else AxQsSpan(1, 1)
                                } else {
                                    resolved
                                }
                            }
                            is AxQsGridValue.Stack -> startSpan
                        }
                    },
                    canResize = { span -> editController.canResize(item, span) },
                    onResizeStarted = editController.listState::beginResize,
                    onResizeStopped = editController.listState::endResize,
                    onResize = { span -> editController.resizeItem(item, span) },
                    onResizeFinished = { span ->
                        if (editController.canResize(item, span)) {
                            editController.resizeItem(item, span)
                        }
                    },
                )
            } else {
                Modifier
            }

        val hapticFeedback = LocalHapticFeedback.current
        AxInteractiveTileContainer(
            tileState = tileState,
            resizeHandleModifier = resizeHandle,
            selectionColor = selectionColor,
            selectionShape = selectionShape,
            resizable = resizable,
            modifier = Modifier.fillMaxSize(),
            itemModifier = itemModifier,
            showRemoveBadge = true,
            onRemoveClick = { editController.removeItem(item) },
            onResizeClick = {
                if (resizable) {
                    editController.listState.item(item.id)?.let { current ->
                        val nextSpan = current.value.nextSpan(current.span, columns)
                        if (nextSpan != current.span && editController.canResize(current, nextSpan)) {
                            editController.resizeItem(current, nextSpan)
                        }
                    }
                }
            },
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = stackHoverScale
                            scaleY = stackHoverScale
                        }
                        .then(
                            if (isSlider) {
                                Modifier
                            } else {
                                Modifier.clip(selectionShape)
                            }
                        )
                        .then(hoverBorderModifier)
                        .axQsDragSource(
                            id = item.id,
                            state = editController.listState,
                            onDragStart = {
                                editController.selectedId.value = item.id
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                        .thenIf(!isStack) {
                            Modifier.clickable {
                                editController.selectedId.value = if (isSelected) null else item.id
                            }
                        }
            ) {
                when (val value = item.value) {
                    is AxQsGridValue.Tile -> {
                        val editTile = value.editViewModel
                        if (editTile != null) {
                            AxQsEditTile(
                                tile = editTile,
                                span = item.span,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (value.viewModel != null) {
                            this@AxGridItemContent.AxLiveTile(
                                tile = value.viewModel,
                                item = item,
                                iconOnly = item.span == AxQsSpan.TileDefault,
                                qqs = false,
                                separateQqs = false,
                                listening = listening,
                                detailsViewModel = detailsViewModel,
                                viewModel = viewModel,
                                isClickable = false,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is AxQsGridValue.Stack -> {
                        key(item.id, value.editTiles.map { it.tileSpec.spec }, value.tiles.map { it.spec.spec }) {
                            AxEditStackedTilePreview(
                                tiles = value.editTiles,
                                liveTiles = value.tiles,
                                isSelected = isSelected,
                                onRemoveStack = { editController.removeItem(item) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    is AxQsGridValue.Control -> {
                        AxQsControlPreview(
                            control = value.control,
                            span = item.span,
                            verticalSliderStyle = axQsViewModel.verticalSliderStyle(layout, value.control),
                        )
                    }
                }
            }
        }
    } else {
        if (isQqsItem || isQsExpandingOrVisible) {
            val isInteractable =
                isQqsItem ||
                    expansionMode == AxQsExpansionMode.QS ||
                    viewModel.expansionState.progress > AxQsTokens.Animation.AX_EXPAND_THRESHOLD
            val revealModifier =
                if (separateMode) {
                    Modifier
                } else {
                    Modifier.axQsItemReveal(
                        isRevealed = isQqsItem,
                        row = row,
                        progress = { viewModel.expansionState.progress },
                    )
                }
            when (val value = item.value) {
                is AxQsGridValue.Tile ->
                    value.viewModel?.let { liveModel ->
                        this@AxGridItemContent.AxLiveTile(
                            tile = liveModel,
                            item = item,
                            iconOnly = item.span == AxQsSpan.TileDefault,
                            qqs = false,
                            separateQqs = false,
                            listening = listening,
                            detailsViewModel = detailsViewModel,
                            viewModel = viewModel,
                            isClickable = isInteractable,
                            modifier = Modifier.fillMaxSize().then(revealModifier),
                        )
                    }
                is AxQsGridValue.Stack ->
                    this@AxGridItemContent.AxLiveStackedTile(
                        tiles = value.tiles,
                        item = item,
                        detailsViewModel = detailsViewModel,
                        viewModel = viewModel,
                        isClickable = isInteractable,
                        modifier = Modifier.fillMaxSize().then(revealModifier),
                    )
                is AxQsGridValue.Control ->
                    if (value.control == AxQsControl.MEDIA) {
                        Element(
                            key = Media.Elements.mediaCarousel,
                            modifier = Modifier.fillMaxSize().then(revealModifier),
                        ) {
                            AxLiveControl(
                                control = value.control,
                                span = item.span,
                                mediaViewModel = mediaViewModel,
                                mediaViewModelFactory = viewModel.mediaViewModelFactory,
                                brightnessSliderViewModel = brightnessSliderViewModel,
                                volumeSliderViewModel = volumeSliderViewModel,
                                verticalSliderStyle = axQsViewModel.verticalSliderStyle(layout, value.control),
                                entranceProgress = {
                                    viewModel.quickQuickSettingsViewModel.squishinessViewModel.squishiness.value
                                },
                                interactive = isInteractable,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        AxLiveControl(
                            control = value.control,
                            span = item.span,
                            mediaViewModel = mediaViewModel,
                            mediaViewModelFactory = viewModel.mediaViewModelFactory,
                            brightnessSliderViewModel = brightnessSliderViewModel,
                            volumeSliderViewModel = volumeSliderViewModel,
                            verticalSliderStyle = axQsViewModel.verticalSliderStyle(layout, value.control),
                            entranceProgress = {
                                viewModel.quickQuickSettingsViewModel.squishinessViewModel.squishiness.value
                            },
                            interactive = isInteractable,
                            modifier = Modifier.fillMaxSize().then(revealModifier),
                        )
                    }
            }
        }
    }
}
