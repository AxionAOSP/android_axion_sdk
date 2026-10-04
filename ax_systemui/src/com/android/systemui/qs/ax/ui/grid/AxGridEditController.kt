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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.android.systemui.qs.ax.domain.interactor.AxQsLayoutInteractor
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsGridValue
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsLayoutData
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.edit.AxQsEditListState
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec

class AxGridEditController(
    val listState: AxQsEditListState<AxQsGridValue>,
    val selectedId: MutableState<String?>,
    val showResetDialog: MutableState<Boolean>,
    val isSeparateMode: Boolean,
    private val layout: AxQsLayout,
    private val columns: Int,
    private val axQsViewModel: AxQsViewModel,
    private val editModeViewModel: EditModeViewModel,
    private val editTilesBySpec: Map<String, EditTileViewModel>,
    private val liveTilesBySpec: Map<String, TileViewModel>,
) {
    fun saveOrders() {
        val placements = packItems(listState.items, columns, maxRows = null, allowStraddle = isSeparateMode)
        val newPositions = placements.associate { it.item.id to AxQsGridPosition(it.column, it.row) }
        placements.forEach { placement ->
            listState.update(placement.item.id) {
                it.copy(position = AxQsGridPosition(placement.column, placement.row))
            }
        }
        val order = listState.items.map { it.id }
        val updatedSpans = listState.items.associate { it.id to it.span }
        val stacks = buildMap {
            listState.items.forEach { item ->
                val v = item.value
                if (v is AxQsGridValue.Stack) {
                    val tileSpecs =
                        v.editTiles.map { it.tileSpec.spec }
                            .ifEmpty { v.tiles.map { it.spec.spec } }
                    put(item.id, tileSpecs)
                }
            }
        }
        val layoutData =
            AxQsLayoutData(
                location = layout,
                order = order,
                spans = updatedSpans,
                positions = newPositions,
                stacks = stacks,
            )
        axQsViewModel.setLayoutData(layoutData)
        val tileSpecs =
            listState.items.flatMap { item ->
                when (val v = item.value) {
                    is AxQsGridValue.Tile -> {
                        listOfNotNull(
                            v.editViewModel?.tileSpec ?:
                            v.viewModel?.spec ?:
                            TileSpec.create(item.id)
                        )
                    }
                    is AxQsGridValue.Stack -> {
                        val specs = v.editTiles.map { it.tileSpec }
                        if (specs.isNotEmpty()) specs else v.tiles.map { it.spec }
                    }
                    else -> emptyList()
                }
            }
        if (tileSpecs.isNotEmpty()) {
            editModeViewModel.setTiles(tileSpecs)
        }
    }

    fun canResize(item: AxQsGridItem<AxQsGridValue>, span: AxQsSpan): Boolean {
        val isSlider = (item.value as? AxQsGridValue.Control)?.control?.isSlider == true
        val isStack = item.value is AxQsGridValue.Stack
        if (isSlider || isStack) return false
        return item.value.isSpanAllowed(span, columns)
    }

    fun resizeItem(item: AxQsGridItem<AxQsGridValue>, span: AxQsSpan) {
        if (!canResize(item, span)) return
        val current = listState.item(item.id) ?: item
        val wouldStraddle =
            if (isSeparateMode) {
                false
            } else {
                current.position?.row?.let { row ->
                    AxQsLayoutInteractor.wouldStraddleQqs(span, row, listState.qqsMaxRows)
                } ?: false
            }
        val newPosition = if (wouldStraddle) null else current.position
        listState.update(item.id) {
            it.copy(span = span, position = newPosition)
        }
        if (newPosition != null) {
            val targetCol = newPosition.column
            val targetRow = newPosition.row
            val targetCols = span.columns
            val targetRows = span.rows
            val collidingIds =
                listState.items
                    .filter { other ->
                        if (other.id == item.id) return@filter false
                        val pos = other.position ?: return@filter false
                        pos.column < targetCol + targetCols &&
                            targetCol < pos.column + other.span.columns &&
                            pos.row < targetRow + targetRows &&
                            targetRow < pos.row + other.span.rows
                    }
                    .mapTo(mutableSetOf()) { it.id }
            if (collidingIds.isNotEmpty()) {
                listState.repack(collidingIds)
            }
        } else {
            listState.repack(setOf(item.id))
        }
        saveOrders()
    }

    fun canStack(
        source: AxQsGridItem<AxQsGridValue>,
        target: AxQsGridItem<AxQsGridValue>,
    ): Boolean {
        val isSourceTile = source.value is AxQsGridValue.Tile
        val isTargetStack = target.value is AxQsGridValue.Stack
        val isTarget2x2Tile =
            target.span == AxQsSpan(columns = 2, rows = 2) && target.value is AxQsGridValue.Tile
        return isSourceTile && (isTargetStack || isTarget2x2Tile)
    }

    fun onStack(
        source: AxQsGridItem<AxQsGridValue>,
        target: AxQsGridItem<AxQsGridValue>,
    ): AxQsGridItem<AxQsGridValue>? {
        val sourceValue = source.value as? AxQsGridValue.Tile ?: return null
        val sourceEdit = sourceValue.editViewModel ?: editTilesBySpec[source.id]
        return when (val targetValue = target.value) {
            is AxQsGridValue.Stack -> {
                val newLive = if (sourceValue.viewModel != null) targetValue.tiles + sourceValue.viewModel else targetValue.tiles
                val newEdit = if (sourceEdit != null) targetValue.editTiles + sourceEdit else targetValue.editTiles
                target.copy(
                    value = AxQsGridValue.Stack(tiles = newLive, editTiles = newEdit),
                    span = AxQsSpan(columns = 2, rows = 2),
                )
            }
            is AxQsGridValue.Tile -> {
                if (target.span == AxQsSpan(columns = 2, rows = 2)) {
                    val stackId = "stack_" + System.currentTimeMillis()
                    val targetEdit = targetValue.editViewModel ?: editTilesBySpec[target.id]
                    val liveTiles = listOfNotNull(targetValue.viewModel, sourceValue.viewModel)
                    val editTiles = listOfNotNull(targetEdit, sourceEdit)
                    AxQsGridItem(
                        id = stackId,
                        span = AxQsSpan(columns = 2, rows = 2),
                        minSpan = AxQsSpan(columns = 2, rows = 2),
                        maxSpan = AxQsSpan(columns = 2, rows = 2),
                        value = AxQsGridValue.Stack(tiles = liveTiles, editTiles = editTiles),
                        position = target.position,
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    fun removeItem(item: AxQsGridItem<AxQsGridValue>) {
        when (val value = item.value) {
            is AxQsGridValue.Tile -> {
                value.editViewModel?.tileSpec?.let { editModeViewModel.removeTile(it) }
                listState.remove(item.id)
            }
            is AxQsGridValue.Stack -> {
                if (value.editTiles.size > 2) {
                    val remainingEdit = value.editTiles.dropLast(1)
                    val remainingLive = value.tiles.dropLast(1)
                    listState.update(item.id) {
                        it.copy(value = AxQsGridValue.Stack(tiles = remainingLive, editTiles = remainingEdit))
                    }
                } else if (value.editTiles.size == 2) {
                    val remainingEdit = value.editTiles.first()
                    val remainingLive = value.tiles.firstOrNull()
                    listState.update(item.id) {
                        it.copy(
                            id = remainingEdit.tileSpec.spec,
                            value = AxQsGridValue.Tile(viewModel = remainingLive, editViewModel = remainingEdit),
                        )
                    }
                } else {
                    listState.remove(item.id)
                }
            }
            is AxQsGridValue.Control -> {
                listState.remove(item.id)
            }
        }
        selectedId.value = null
        saveOrders()
    }

    fun addTile(tile: EditTileViewModel) {
        val currentIds = listState.items.map { it.id }.toSet()
        if (tile.tileSpec.spec !in currentIds) {
            val newItem =
                AxQsGridItem<AxQsGridValue>(
                    id = tile.tileSpec.spec,
                    span = AxQsSpan.TileDefault,
                    minSpan = AxQsSpan.TileMin,
                    maxSpan = AxQsSpan(columns = minOf(2, columns), rows = 2),
                    value =
                        AxQsGridValue.Tile(
                            viewModel = null,
                            editViewModel = tile,
                        ),
                )
            listState.add(newItem)
            saveOrders()
        }
    }

    fun addControl(control: AxQsControl) {
        val spans = control.spans(columns)
        val newItem =
            AxQsGridItem<AxQsGridValue>(
                id = control.id,
                span = spans.default,
                minSpan = spans.min,
                maxSpan = spans.max,
                value = AxQsGridValue.Control(control),
            )
        listState.add(newItem)
        saveOrders()
    }

    fun addStack() {
        val stackId = "stack_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val newItem =
            AxQsGridItem<AxQsGridValue>(
                id = stackId,
                span = AxQsSpan(columns = 2, rows = 2),
                minSpan = AxQsSpan(columns = 2, rows = 2),
                maxSpan = AxQsSpan(columns = 2, rows = 2),
                value = AxQsGridValue.Stack(emptyList(), emptyList()),
            )
        listState.add(newItem)
        saveOrders()
    }
}

@Composable
fun rememberAxGridEditController(
    gridItems: List<AxQsGridItem<AxQsGridValue>>,
    layout: AxQsLayout,
    columns: Int,
    isEditing: Boolean,
    isSeparateMode: Boolean,
    allEditTiles: List<EditTileViewModel>?,
    tiles: List<TileViewModel>,
    axQsViewModel: AxQsViewModel,
    editModeViewModel: EditModeViewModel,
    resetEpoch: Int,
): AxGridEditController {
    val selectedId = remember { mutableStateOf<String?>(null) }
    val showResetDialog = remember { mutableStateOf(false) }

    val listState =
        remember(isEditing, layout, resetEpoch, isSeparateMode) {
            AxQsEditListState(
                initialItems = gridItems,
                initialQqsMaxRows = axQsViewModel.qqsMaxRows,
                allowStraddle = isSeparateMode,
            )
        }

    val editTilesBySpec =
        remember(allEditTiles) {
            allEditTiles?.associateBy { it.tileSpec.spec } ?: emptyMap()
        }
    val liveTilesBySpec = remember(tiles) { tiles.associateBy { it.spec.spec } }

    LaunchedEffect(gridItems) {
        if (!isEditing) {
            listState.updateItems(gridItems)
        } else {
            val existingIds = listState.items.map { it.id }.toSet()
            val newItems = gridItems.filter { it.id !in existingIds }
            if (newItems.isNotEmpty()) {
                newItems.forEach { listState.add(it) }
            }
        }
    }

    LaunchedEffect(isEditing, allEditTiles) {
        if (isEditing) {
            val stacks = axQsViewModel.stacks(layout)
            listState.items.forEach { item ->
                val v = item.value
                if (v is AxQsGridValue.Stack) {
                    val tileSpecs = stacks[item.id] ?: emptyList()
                    val resolvedEditTiles = tileSpecs.mapNotNull { editTilesBySpec[it] }
                    val resolvedLiveTiles = tileSpecs.mapNotNull { liveTilesBySpec[it] }
                    if (resolvedEditTiles.isNotEmpty() || resolvedLiveTiles.isNotEmpty()) {
                        listState.update(item.id) {
                            it.copy(
                                value =
                                    AxQsGridValue.Stack(
                                        tiles = if (resolvedLiveTiles.isNotEmpty()) resolvedLiveTiles else v.tiles,
                                        editTiles = if (resolvedEditTiles.isNotEmpty()) resolvedEditTiles else v.editTiles,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }

    return remember(listState, layout, columns, isSeparateMode, axQsViewModel, editModeViewModel, editTilesBySpec, liveTilesBySpec) {
        AxGridEditController(
            listState = listState,
            selectedId = selectedId,
            showResetDialog = showResetDialog,
            isSeparateMode = isSeparateMode,
            layout = layout,
            columns = columns,
            axQsViewModel = axQsViewModel,
            editModeViewModel = editModeViewModel,
            editTilesBySpec = editTilesBySpec,
            liveTilesBySpec = liveTilesBySpec,
        )
    }
}
