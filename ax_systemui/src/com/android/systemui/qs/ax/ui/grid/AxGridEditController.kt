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
    val allowStraddle: Boolean
        get() = isSeparateMode || layout == AxQsLayout.SPLIT_SHADE

    fun saveOrders() {
        saveOrders(emptyMap())
    }

    private fun saveOrders(minUnpositionedRows: Map<String, Int>) {
        val placements =
            packItems(
                    items = listState.items,
                    columns = columns,
                    maxRows = null,
                    allowStraddle = allowStraddle,
                    qqsMaxRows = listState.qqsMaxRows,
                    minUnpositionedRows = minUnpositionedRows,
                )
                .sortedWith(compareBy({ it.row }, { it.column }))
        val orderedItems =
            placements.map { placement ->
                placement.item.copy(position = AxQsGridPosition(placement.column, placement.row))
            }
        listState.updateItems(orderedItems)
        val newPositions = orderedItems.mapNotNull { item -> item.position?.let { item.id to it } }.toMap()
        val order = orderedItems.map { it.id }
        val updatedSpans = orderedItems.associate { it.id to it.span }
        val stacks = buildMap {
            listState.items.forEach { item ->
                val v = item.value
                if (v is AxQsGridValue.Stack) {
                    val tileSpecs =
                        v.editTiles.map { it.tileSpec.spec }
                            .ifEmpty { v.tiles.map { it.spec.spec } }
                            .distinct()
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
        val currentLayoutTileSpecs =
            orderedItems.flatMap { item ->
                when (val v = item.value) {
                    is AxQsGridValue.Tile -> {
                        listOfNotNull(
                            v.editViewModel?.tileSpec
                                ?: v.viewModel?.spec
                                ?: TileSpec.create(item.id).takeIf { it != TileSpec.Invalid }
                        )
                    }
                    is AxQsGridValue.Stack -> {
                        val specs = v.editTiles.map { it.tileSpec }
                        if (specs.isNotEmpty()) specs else v.tiles.map { it.spec }
                    }
                    else -> emptyList()
                }
            }
        val unionTileSpecs =
            axQsViewModel.allReferencedTileSpecs(layoutData, currentLayoutTileSpecs)
        if (unionTileSpecs.isNotEmpty()) {
            editModeViewModel.setTiles(unionTileSpecs)
        }
    }

    fun canResize(item: AxQsGridItem<AxQsGridValue>, span: AxQsSpan): Boolean {
        val isSlider = (item.value as? AxQsGridValue.Control)?.control?.isSlider == true
        val isStack = item.value is AxQsGridValue.Stack
        if (isSlider || isStack) return false
        return item.value.isSpanAllowed(span, columns)
    }

    fun previewResizeItem(item: AxQsGridItem<AxQsGridValue>, span: AxQsSpan) {
        if (!canResize(item, span)) return
        val solved =
            solveResizeItems(
                baseItems = listState.currentResizeBaseItems(),
                itemId = item.id,
                newSpan = span,
                columns = columns,
                allowStraddle = allowStraddle,
                qqsMaxRows = listState.qqsMaxRows,
                preservePinnedPredicate = { candidate ->
                    (candidate.value as? AxQsGridValue.Control)?.control?.isSlider == true
                },
            )
        listState.applyPreviewItems(solved)
    }

    fun resizeItem(item: AxQsGridItem<AxQsGridValue>, span: AxQsSpan) {
        if (!canResize(item, span)) return
        val solved =
            solveResizeItems(
                baseItems = listState.currentResizeBaseItems(),
                itemId = item.id,
                newSpan = span,
                columns = columns,
                allowStraddle = allowStraddle,
                qqsMaxRows = listState.qqsMaxRows,
                preservePinnedPredicate = { candidate ->
                    (candidate.value as? AxQsGridValue.Control)?.control?.isSlider == true
                },
            )
        listState.updateItems(solved)
        saveOrders()
    }

    fun canStack(
        source: AxQsGridItem<AxQsGridValue>,
        target: AxQsGridItem<AxQsGridValue>,
    ): Boolean {
        if (source.id == target.id) return false
        val isSourceTile = source.value is AxQsGridValue.Tile
        val isTargetStack = target.value is AxQsGridValue.Stack
        val isTarget2x2Tile =
            target.span == AxQsSpan(columns = 2, rows = 2) && target.value is AxQsGridValue.Tile
        if (isTargetStack) {
            val targetStack = target.value as AxQsGridValue.Stack
            val sourceTile = source.value as? AxQsGridValue.Tile
            val sourceSpec = sourceTile?.editViewModel?.tileSpec?.spec
                ?: sourceTile?.viewModel?.spec?.spec
                ?: source.id
            val alreadyInStack = targetStack.editTiles.any { it.tileSpec.spec == sourceSpec } ||
                targetStack.tiles.any { it.spec.spec == sourceSpec }
            if (alreadyInStack) return false
        }
        return isSourceTile && (isTargetStack || isTarget2x2Tile)
    }

    fun onStack(
        source: AxQsGridItem<AxQsGridValue>,
        target: AxQsGridItem<AxQsGridValue>,
    ): AxQsGridItem<AxQsGridValue>? {
        val sourceValue = source.value as? AxQsGridValue.Tile ?: return null
        val sourceEdit = sourceValue.editViewModel ?: editTilesBySpec[source.id]
        val sourceLive = sourceValue.viewModel
        return when (val targetValue = target.value) {
            is AxQsGridValue.Stack -> {
                val newLive = if (sourceLive != null) {
                    (targetValue.tiles + sourceLive).distinctBy { it.spec.spec }
                } else {
                    targetValue.tiles
                }
                val newEdit = if (sourceEdit != null) {
                    (targetValue.editTiles + sourceEdit).distinctBy { it.tileSpec.spec }
                } else {
                    targetValue.editTiles
                }
                target.copy(
                    value = AxQsGridValue.Stack(tiles = newLive, editTiles = newEdit),
                    span = AxQsSpan(columns = 2, rows = 2),
                )
            }
            is AxQsGridValue.Tile -> {
                if (target.span == AxQsSpan(columns = 2, rows = 2)) {
                    val stackId = "stack_" + System.currentTimeMillis()
                    val targetEdit = targetValue.editViewModel ?: editTilesBySpec[target.id]
                    val liveTiles = listOfNotNull(targetValue.viewModel, sourceValue.viewModel).distinctBy { it.spec.spec }
                    val editTiles = listOfNotNull(targetEdit, sourceEdit).distinctBy { it.tileSpec.spec }
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

    fun removeStackPage(item: AxQsGridItem<AxQsGridValue>, pageIndex: Int) {
        val currentItem = listState.item(item.id) ?: item
        val stack = currentItem.value as? AxQsGridValue.Stack ?: return
        val distinctEdit = stack.editTiles.distinctBy { it.tileSpec.spec }
        val distinctLive = stack.tiles.distinctBy { it.spec.spec }
        val targetSpec =
            distinctEdit.getOrNull(pageIndex)?.tileSpec?.spec
                ?: distinctLive.getOrNull(pageIndex)?.spec?.spec
        if (targetSpec == null) {
            listState.remove(currentItem.id)
            selectedId.value = null
            saveOrders()
            return
        }

        val remainingSpecs =
            (distinctEdit.map { it.tileSpec.spec } + distinctLive.map { it.spec.spec })
                .distinct()
                .filter { it != targetSpec }
        when {
            remainingSpecs.size >= 2 -> {
                val remainingEdit = remainingSpecs.mapNotNull { editTilesBySpec[it] ?: distinctEdit.firstOrNull { e -> e.tileSpec.spec == it } }
                val remainingLive = remainingSpecs.mapNotNull { liveTilesBySpec[it] ?: distinctLive.firstOrNull { l -> l.spec.spec == it } }
                listState.update(currentItem.id) {
                    it.copy(
                        value = AxQsGridValue.Stack(tiles = remainingLive, editTiles = remainingEdit)
                    )
                }
            }
            remainingSpecs.size == 1 -> {
                val lastSpec = remainingSpecs.first()
                val remainingEdit = editTilesBySpec[lastSpec] ?: distinctEdit.firstOrNull { it.tileSpec.spec == lastSpec }
                val remainingLive = liveTilesBySpec[lastSpec] ?: distinctLive.firstOrNull { it.spec.spec == lastSpec }
                listState.update(currentItem.id) {
                    it.copy(
                        id = lastSpec,
                        minSpan = AxQsSpan.TileDefault,
                        maxSpan = AxQsSpan(columns = minOf(2, columns), rows = 2),
                        value = AxQsGridValue.Tile(viewModel = remainingLive, editViewModel = remainingEdit),
                    )
                }
                selectedId.value = lastSpec
            }
            else -> {
                listState.remove(currentItem.id)
                selectedId.value = null
            }
        }
        saveOrders()
    }

    fun removeItem(item: AxQsGridItem<AxQsGridValue>) {
        when (val value = item.value) {
            is AxQsGridValue.Tile -> {
                listState.remove(item.id)
            }
            is AxQsGridValue.Stack -> {
                val count = maxOf(value.editTiles.size, value.tiles.size)
                if (count == 0) {
                    listState.remove(item.id)
                } else {
                    removeStackPage(item, count - 1)
                    return
                }
            }
            is AxQsGridValue.Control -> {
                listState.remove(item.id)
            }
        }
        selectedId.value = null
        saveOrders()
    }

    fun activeItemAndTileIds(): Set<String> = buildSet {
        listState.items.forEach { item ->
            add(item.id)
            val v = item.value
            if (v is AxQsGridValue.Stack) {
                v.editTiles.forEach { add(it.tileSpec.spec) }
                v.tiles.forEach { add(it.spec.spec) }
            }
        }
    }

    private fun appendItemAtBottom(newItem: AxQsGridItem<AxQsGridValue>) {
        val existingPlacements =
            packItems(
                items = listState.items,
                columns = columns,
                maxRows = null,
                allowStraddle = allowStraddle,
                qqsMaxRows = listState.qqsMaxRows,
            )
        val bottomStartRow =
            (existingPlacements.maxOfOrNull { it.row + it.item.span.rows }?.minus(1) ?: 0)
                .coerceAtLeast(0)
        listState.add(newItem)
        selectedId.value = newItem.id
        saveOrders(mapOf(newItem.id to bottomStartRow))
    }

    fun addTile(tile: EditTileViewModel) {
        val spec = tile.tileSpec.spec
        if (spec !in activeItemAndTileIds()) {
            val defaultSpan =
                axQsViewModel.span(spec, layout, AxQsSpan.TileDefault).coerceTileSpan(columns)
            val newItem =
                AxQsGridItem<AxQsGridValue>(
                    id = spec,
                    span = defaultSpan,
                    minSpan = AxQsSpan.TileMin,
                    maxSpan = AxQsSpan(columns = minOf(2, columns), rows = 2),
                    value =
                        AxQsGridValue.Tile(
                            viewModel = liveTilesBySpec[spec],
                            editViewModel = tile,
                        ),
                )
            appendItemAtBottom(newItem)
        }
    }

    fun addControl(control: AxQsControl) {
        if (control.id !in activeItemAndTileIds()) {
            val spans = control.spans(columns)
            val newItem =
                AxQsGridItem<AxQsGridValue>(
                    id = control.id,
                    span = spans.default,
                    minSpan = spans.min,
                    maxSpan = spans.max,
                    value = AxQsGridValue.Control(control),
                )
            appendItemAtBottom(newItem)
        }
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
        appendItemAtBottom(newItem)
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
    val allowStraddle = isSeparateMode || layout == AxQsLayout.SPLIT_SHADE
    val selectedId = remember(isEditing, layout, resetEpoch) { mutableStateOf<String?>(null) }
    val showResetDialog = remember { mutableStateOf(false) }

    val listState =
        remember(isEditing, layout, columns, resetEpoch, allowStraddle) {
            AxQsEditListState(
                initialItems = gridItems,
                initialQqsMaxRows = axQsViewModel.qqsMaxRows,
                allowStraddle = allowStraddle,
            )
        }
    LaunchedEffect(listState, axQsViewModel.qqsMaxRows) {
        listState.updateQqsMaxRows(axQsViewModel.qqsMaxRows)
    }


    val editTilesBySpec =
        remember(allEditTiles) {
            allEditTiles?.associateBy { it.tileSpec.spec } ?: emptyMap()
        }
    val liveTilesBySpec = remember(tiles) { tiles.associateBy { it.spec.spec } }

    LaunchedEffect(listState, gridItems) {
        if (!isEditing) {
            listState.updateItems(gridItems)
        } else {
            val allowedOrder = axQsViewModel.order(layout)?.toSet()
            val existingIds = activeItemIdsSnapshot(listState.items)
            val newItems =
                gridItems.filter { item ->
                    item.id !in existingIds && (allowedOrder == null || item.id in allowedOrder)
                }
            if (newItems.isNotEmpty()) {
                newItems.forEach { listState.add(it) }
            }
        }
    }

    LaunchedEffect(listState, isEditing, layout, allEditTiles, tiles) {
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

private fun activeItemIdsSnapshot(items: List<AxQsGridItem<AxQsGridValue>>): Set<String> = buildSet {
    items.forEach { item ->
        add(item.id)
        val v = item.value
        if (v is AxQsGridValue.Stack) {
            v.editTiles.forEach { add(it.tileSpec.spec) }
            v.tiles.forEach { add(it.spec.spec) }
        }
    }
}
