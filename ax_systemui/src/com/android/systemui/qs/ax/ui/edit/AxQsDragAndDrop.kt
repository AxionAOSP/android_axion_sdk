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

package com.android.systemui.qs.ax.ui.edit

import android.content.ClipData
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import com.android.systemui.qs.ax.domain.interactor.AxQsLayoutInteractor
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsGridSection
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.grid.AxQsGridCell
import com.android.systemui.qs.ax.ui.grid.packItems
import kotlin.math.abs

internal data class AxQsSolvedDragTarget(
    val section: AxQsGridSection,
    val position: AxQsGridPosition,
    val isGapInsert: Boolean,
)

class AxQsEditListState<T>(
    initialItems: List<AxQsGridItem<T>>,
    initialQqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
    val allowStraddle: Boolean = false,
) {
    val qqsMaxRowsState = mutableIntStateOf(initialQqsMaxRows)
    val qqsMaxRows: Int
        get() = qqsMaxRowsState.intValue

    fun updateQqsMaxRows(rows: Int) {
        qqsMaxRowsState.intValue = rows
    }
    private val itemBounds = mutableMapOf<String, Pair<AxQsGridSection, Rect>>()
    private val gridCells = mutableMapOf<AxQsGridSection, List<AxQsGridCell>>()
    private val gridOrigins = mutableMapOf<AxQsGridSection, Offset>()
    private val _items: SnapshotStateList<AxQsGridItem<T>> = initialItems.toMutableStateList()
    private var dragSnapshot: List<AxQsGridItem<T>>? = null
    private var resizeSnapshot: List<AxQsGridItem<T>>? = null
    private var dragSnapshotBounds: Map<String, Pair<AxQsGridSection, Rect>> = emptyMap()
    private var lastSolvedTarget: AxQsSolvedDragTarget? = null
    internal val currentSolvedTarget: AxQsSolvedDragTarget?
        get() = lastSolvedTarget
    private var dragAnchor = Offset.Zero

    val items: List<AxQsGridItem<T>> = _items

    var draggedId by mutableStateOf<String?>(null)
        private set

    var draggedPosition by mutableStateOf(Offset.Unspecified)
        private set

    var hoveredStackTargetId by mutableStateOf<String?>(null)
        private set

    fun setHoveredStackTarget(id: String?) {
        if (hoveredStackTargetId != id) {
            hoveredStackTargetId = id
        }
    }

    val dragInProgress: Boolean
        get() = draggedId != null

    var resizeInProgress by mutableStateOf(false)
        private set

    fun updateItems(items: List<AxQsGridItem<T>>) {
        _items.clear()
        _items.addAll(items)
        itemBounds.keys.retainAll(items.mapTo(mutableSetOf()) { it.id })
    }

    fun item(id: String): AxQsGridItem<T>? {
        return _items.firstOrNull { it.id == id }
    }

    fun add(item: AxQsGridItem<T>) {
        _items.add(item)
    }

    fun repack(ids: Set<String>) {
        _items.indices.forEach { index ->
            val item = _items[index]
            if (item.id in ids && item.position != null) {
                _items[index] = item.copy(position = null)
            }
        }
        ids.forEach { id -> itemBounds.remove(id) }
    }

    fun beginResize() {
        capturePositions()
        resizeSnapshot = _items.toList()
        resizeInProgress = true
    }

    fun endResize() {
        resizeSnapshot = null
        resizeInProgress = false
    }

    fun currentResizeBaseItems(): List<AxQsGridItem<T>> {
        val snap = resizeSnapshot
        if (snap != null) return snap
        capturePositions()
        return _items.toList()
    }

    fun applyPreviewItems(items: List<AxQsGridItem<T>>) {
        if (_items.size != items.size) {
            updateItems(items)
        } else {
            items.forEachIndexed { index, updated ->
                if (_items[index] != updated) {
                    _items[index] = updated
                }
            }
        }
    }

    fun remove(id: String): Boolean {
        itemBounds.remove(id)
        return _items.removeAll { it.id == id }
    }

    fun update(id: String, transform: (AxQsGridItem<T>) -> AxQsGridItem<T>): Boolean {
        val index = indexOf(id)
        if (index < 0) return false
        _items[index] = transform(_items[index])
        return true
    }

    fun moveBy(id: String, delta: Int): Boolean {
        val from = indexOf(id)
        if (from < 0) return false
        val to = (from + delta).coerceIn(0, _items.lastIndex)
        if (from == to) return false
        _items.add(to, _items.removeAt(from))
        return true
    }

    fun start(id: String, anchor: Offset) {
        check(!dragInProgress)
        check(indexOf(id) >= 0)
        capturePositions()
        dragSnapshot = _items.toList()
        dragSnapshotBounds = itemBounds.toMap()
        lastSolvedTarget = null
        val bounds = itemBounds[id]?.second
        dragAnchor =
            if (bounds != null) {
                Offset(
                    x = anchor.x.coerceIn(0f, bounds.width),
                    y = anchor.y.coerceIn(0f, bounds.height),
                )
            } else {
                anchor
            }
        draggedId = id
        draggedPosition = Offset.Unspecified
    }

    fun updateItemBounds(id: String, section: AxQsGridSection, bounds: Rect) {
        itemBounds[id] = section to bounds
    }

    fun updateGridOrigin(section: AxQsGridSection, origin: Offset) {
        gridOrigins[section] = origin
    }

    fun updateGridCells(section: AxQsGridSection, cells: List<AxQsGridCell>) {
        gridCells[section] = cells
    }

    fun onMoved(offset: Offset) {
        draggedPosition = offset
    }

    fun findItemAt(offset: Offset, section: AxQsGridSection): Pair<String, Rect>? {
        val localOffset = offsetInGrid(offset, section)
        return itemBounds.entries
            .asSequence()
            .filter {
                it.key != draggedId &&
                    it.value.first == section &&
                    it.value.second.contains(localOffset)
            }
            .map { it.key to it.value.second }
            .minByOrNull { (_, bounds) -> (bounds.center - localOffset).getDistanceSquared() }
    }

    fun offsetInGrid(offset: Offset, section: AxQsGridSection): Offset {
        return offset - gridOrigins.getOrDefault(section, Offset.Zero)
    }

    fun restoreSnapshotPreview() {
        val snapshot = dragSnapshot ?: return
        lastSolvedTarget = null
        snapshot.forEachIndexed { itemIndex, snapshotItem ->
            if (itemIndex < _items.size && _items[itemIndex] != snapshotItem) {
                _items[itemIndex] = snapshotItem
            }
        }
    }

    fun findStackTargetAt(
        offset: Offset,
        section: AxQsGridSection,
        canStack: (AxQsGridItem<T>, AxQsGridItem<T>) -> Boolean,
    ): String? {
        val sourceId = draggedId ?: return null
        val baseItems = dragSnapshot ?: _items
        val draggedItem = baseItems.firstOrNull { it.id == sourceId } ?: return null
        val localOffset = offsetInGrid(offset, section)
        val boundsSource = dragSnapshotBounds.ifEmpty { itemBounds }
        return boundsSource.entries
            .asSequence()
            .filter { (targetId, sectionAndBounds) ->
                if (targetId == sourceId || sectionAndBounds.first != section) return@filter false
                val bounds = sectionAndBounds.second
                val insetX = bounds.width * STACK_TARGET_EDGE_INSET_FRACTION
                val insetY = bounds.height * STACK_TARGET_EDGE_INSET_FRACTION
                val innerBounds =
                    Rect(
                        left = bounds.left + insetX,
                        top = bounds.top + insetY,
                        right = (bounds.right - insetX).coerceAtLeast(bounds.left + insetX),
                        bottom = (bounds.bottom - insetY).coerceAtLeast(bounds.top + insetY),
                    )
                if (!innerBounds.contains(localOffset)) return@filter false
                val targetItem = baseItems.firstOrNull { it.id == targetId } ?: return@filter false
                canStack(draggedItem, targetItem)
            }
            .minByOrNull { (_, sectionAndBounds) ->
                (sectionAndBounds.second.center - localOffset).getDistanceSquared()
            }
            ?.key
    }

    fun moveToCell(offset: Offset, section: AxQsGridSection): Boolean {
        val id = draggedId ?: return false
        val baseItems = dragSnapshot ?: _items.toList()
        val draggedBase = baseItems.firstOrNull { it.id == id } ?: return false
        val cells = gridCells[section] ?: return false
        val localOffset = offsetInGrid(offset, section)
        val itemTopLeft = localOffset - dragAnchor
        val columns = cells.maxOfOrNull { it.position.column }?.plus(1) ?: return false
        val rows = cells.maxOfOrNull { it.position.row }?.plus(1) ?: return false
        val origin = draggedBase.position
        val boundsSource = dragSnapshotBounds.ifEmpty { itemBounds }
        val validCells =
            cells.filter { cell ->
                cell.position.column + draggedBase.span.columns <= columns &&
                    cell.position.row + draggedBase.span.rows <= rows &&
                    (allowStraddle ||
                        !AxQsLayoutInteractor.wouldStraddleQqs(
                            draggedBase.span,
                            cell.position.row,
                            qqsMaxRows,
                        ))
            }
        if (validCells.isEmpty()) return false

        val is1x1 = draggedBase.span == AxQsSpan(1, 1)
        var isGapInsert = false
        val start =
            if (is1x1 && origin != null) {
                val targetRow =
                    validCells
                        .minByOrNull { cell -> abs(cell.bounds.top - itemTopLeft.y) }
                        ?.position
                        ?.row ?: return false
                val rowCells =
                    validCells
                        .filter { it.position.row == targetRow }
                        .sortedBy { it.position.column }
                val tileWidth =
                    boundsSource[id]?.second?.width
                        ?: rowCells.firstOrNull()?.bounds?.width
                        ?: 0f
                val draggedCenterX = itemTopLeft.x + tileWidth * 0.5f
                var gapMatched: AxQsGridCell? = null

                val firstCell = rowCells.firstOrNull()
                if (
                    firstCell != null &&
                        firstCell.position.column == 0 &&
                        origin != firstCell.position &&
                        draggedCenterX < firstCell.bounds.left + firstCell.bounds.width * 0.18f
                ) {
                    gapMatched = firstCell
                    isGapInsert = true
                } else {
                    for (idx in 1 until rowCells.size) {
                        val leftCell = rowCells[idx - 1]
                        val rightCell = rowCells[idx]
                        if (rightCell.position.column != leftCell.position.column + 1) continue
                        val gapLeft = leftCell.bounds.right - leftCell.bounds.width * 0.22f
                        val gapRight = rightCell.bounds.left + rightCell.bounds.width * 0.22f
                        if (draggedCenterX in gapLeft..gapRight) {
                            val comingFromAfter =
                                origin.row > targetRow ||
                                    (origin.row == targetRow &&
                                        origin.column > leftCell.position.column)
                            gapMatched = if (comingFromAfter) rightCell else leftCell
                            isGapInsert = true
                            break
                        }
                    }
                }
                gapMatched
                    ?: rowCells.minByOrNull { cell ->
                        abs(cell.bounds.center.x - draggedCenterX)
                    }
                    ?: return false
            } else {
                validCells.minByOrNull { cell ->
                    (cell.bounds.topLeft - itemTopLeft).getDistanceSquared()
                } ?: return false
            }

        val targetKey = AxQsSolvedDragTarget(section, start.position, isGapInsert)
        if (lastSolvedTarget == targetKey) return true
        lastSolvedTarget = targetKey

        if (origin == start.position) {
            restoreSnapshotPreview()
            lastSolvedTarget = targetKey
            return true
        }
        val sectionIds =
            boundsSource
                .filterValues { (itemSection, _) -> itemSection == section }
                .keys + id

        fun overlaps(
            posA: AxQsGridPosition,
            spanA: AxQsSpan,
            posB: AxQsGridPosition,
            spanB: AxQsSpan,
        ): Boolean {
            return posA.column < posB.column + spanB.columns &&
                posB.column < posA.column + spanA.columns &&
                posA.row < posB.row + spanB.rows &&
                posB.row < posA.row + spanA.rows
        }

        fun compareReadingOrder(a: AxQsGridPosition, b: AxQsGridPosition): Int {
            return if (a.row != b.row) a.row.compareTo(b.row) else a.column.compareTo(b.column)
        }

        val colliders =
            baseItems.filter { current ->
                current.id in sectionIds &&
                    current.id != id &&
                    current.position != null &&
                    overlaps(current.position, current.span, start.position, draggedBase.span)
            }

        val solvedItems: List<AxQsGridItem<T>> =
            if (colliders.isEmpty()) {
                baseItems.map { current ->
                    if (current.id == id) current.copy(position = start.position) else current
                }
            } else {
                val swappedPositions =
                    if (
                        !isGapInsert &&
                            origin != null &&
                            !overlaps(origin, draggedBase.span, start.position, draggedBase.span)
                    ) {
                        val deltaCol = origin.column - start.position.column
                        val deltaRow = origin.row - start.position.row
                        val candidateMap = mutableMapOf<String, AxQsGridPosition>()
                        val fitsOrigin =
                            colliders.all { collider ->
                                val pos = collider.position ?: return@all false
                                val shifted =
                                    AxQsGridPosition(
                                        column = (pos.column + deltaCol).coerceAtLeast(0),
                                        row = (pos.row + deltaRow).coerceAtLeast(0),
                                    )
                                val insideOrigin =
                                    pos.column + deltaCol >= origin.column &&
                                        shifted.column + collider.span.columns <=
                                            origin.column + draggedBase.span.columns &&
                                        pos.row + deltaRow >= origin.row &&
                                        shifted.row + collider.span.rows <=
                                            origin.row + draggedBase.span.rows
                                val straddleOk =
                                    allowStraddle ||
                                        !AxQsLayoutInteractor.wouldStraddleQqs(
                                            collider.span,
                                            shifted.row,
                                            qqsMaxRows,
                                        )
                                if (insideOrigin && straddleOk) {
                                    candidateMap[collider.id] = shifted
                                    true
                                } else {
                                    false
                                }
                            }
                        candidateMap.takeIf { fitsOrigin }
                    } else {
                        null
                    }

                if (swappedPositions != null) {
                    baseItems.map { current ->
                        when {
                            current.id == id -> current.copy(position = start.position)
                            current.id in swappedPositions ->
                                current.copy(position = swappedPositions.getValue(current.id))
                            else -> current
                        }
                    }
                } else {
                    val loPos =
                        if (origin != null && compareReadingOrder(origin, start.position) < 0) {
                            origin
                        } else {
                            start.position
                        }
                    val hiPos =
                        if (origin != null && compareReadingOrder(origin, start.position) > 0) {
                            origin
                        } else {
                            start.position
                        }
                    val minRippleRow =
                        minOf(
                            loPos.row,
                            colliders.minOf { it.position?.row ?: loPos.row },
                        )
                    val visualSortedSection =
                        baseItems
                            .filter { it.id in sectionIds }
                            .sortedWith(
                                compareBy(
                                    { if (it.id == id) 0 else 1 },
                                    { it.position?.row ?: Int.MAX_VALUE },
                                    { it.position?.column ?: Int.MAX_VALUE },
                                )
                            )

                    fun solveSectionRipple(bounded: Boolean): List<AxQsGridItem<T>> {
                        val unpinnedIds = mutableSetOf<String>()
                        for (current in visualSortedSection) {
                            if (current.id == id) continue
                            val pos = current.position ?: continue
                            val directlyHit =
                                overlaps(pos, current.span, start.position, draggedBase.span)
                            val isVerticalSlider =
                                current.span.columns == 1 && current.span.rows > 1
                            if (isVerticalSlider && !directlyHit) continue
                            val inInterval =
                                if (bounded && origin != null) {
                                    compareReadingOrder(pos, loPos) >= 0 &&
                                        compareReadingOrder(pos, hiPos) <= 0
                                } else {
                                    compareReadingOrder(pos, loPos) >= 0
                                }
                            if (inInterval || directlyHit) {
                                unpinnedIds.add(current.id)
                            }
                        }
                        val prepared =
                            visualSortedSection.map { current ->
                                when {
                                    current.id == id -> current.copy(position = start.position)
                                    current.id in unpinnedIds -> current.copy(position = null)
                                    else -> current
                                }
                            }
                        val packed =
                            packItems(
                                items = prepared,
                                columns = columns,
                                maxRows = null,
                                allowStraddle = allowStraddle,
                                qqsMaxRows = qqsMaxRows,
                                minUnpositionedRows = unpinnedIds.associateWith { minRippleRow },
                            )
                        val newPositions =
                            packed.associate { it.item.id to AxQsGridPosition(it.column, it.row) }
                        return baseItems.map { current ->
                            val newPos = newPositions[current.id]
                            if (newPos != null) current.copy(position = newPos) else current
                        }
                    }

                    val boundedResult = solveSectionRipple(bounded = origin != null)
                    val spilledPastOrigin =
                        origin != null &&
                            baseItems.indices.any { idx ->
                                val before = baseItems[idx]
                                val after = boundedResult[idx]
                                val origPos = before.position
                                val newPos = after.position
                                before.id != id &&
                                    before.id in sectionIds &&
                                    origPos != null &&
                                    newPos != null &&
                                    compareReadingOrder(origPos, hiPos) <= 0 &&
                                    compareReadingOrder(newPos, hiPos) > 0
                            }
                    if (spilledPastOrigin) solveSectionRipple(bounded = false) else boundedResult
                }
            }

        if (_items.size != solvedItems.size) {
            updateItems(solvedItems)
        } else {
            solvedItems.forEachIndexed { itemIndex, movedItem ->
                if (_items[itemIndex] != movedItem) {
                    _items[itemIndex] = movedItem
                }
            }
        }
        return true
    }

    fun onTargeting(id: String, insertAfter: Boolean?) {
        val sourceId = draggedId ?: return
        if (sourceId == id) return

        val from = indexOf(sourceId)
        val target = indexOf(id)
        if (from < 0 || target < 0) return

        val targetAfterRemoval = if (from < target) target - 1 else target
        val destination = targetAfterRemoval + if (insertAfter ?: (from < target)) 1 else 0
        if (destination == from) return
        _items.add(destination.coerceIn(0, _items.lastIndex), _items.removeAt(from))
    }

    fun stackItems(
        sourceId: String,
        targetId: String,
        resolver: (AxQsGridItem<T>, AxQsGridItem<T>) -> AxQsGridItem<T>?,
    ): Boolean {
        val source = item(sourceId) ?: return false
        val target = item(targetId) ?: return false
        val stacked = resolver(source, target) ?: return false
        val targetIndex = indexOf(targetId)
        if (targetIndex < 0) return false
        _items[targetIndex] = stacked
        remove(sourceId)
        hoveredStackTargetId = null
        return true
    }

    fun cancel() {
        dragSnapshot?.let(::updateItems)
        dragSnapshot = null
        dragSnapshotBounds = emptyMap()
        lastSolvedTarget = null
        dragAnchor = Offset.Zero
        draggedId = null
        draggedPosition = Offset.Unspecified
        hoveredStackTargetId = null
    }

    fun finish(onDrop: () -> Unit) {
        if (draggedId == null) return
        dragSnapshot = null
        dragSnapshotBounds = emptyMap()
        lastSolvedTarget = null
        dragAnchor = Offset.Zero
        draggedId = null
        draggedPosition = Offset.Unspecified
        hoveredStackTargetId = null
        onDrop()
    }

    fun positions(): Map<String, AxQsGridPosition> {
        capturePositions()
        return _items.mapNotNull { item -> item.position?.let { item.id to it } }.toMap()
    }

    private fun indexOf(id: String): Int {
        return _items.indexOfFirst { it.id == id }
    }

    private fun capturePositions() {
        _items.indices.forEach { index ->
            val item = _items[index]
            val (section, bounds) = itemBounds[item.id] ?: return@forEach
            val position =
                gridCells[section]
                    ?.minByOrNull { cell ->
                        (cell.bounds.topLeft - bounds.topLeft).getDistanceSquared()
                    }
                    ?.position ?: return@forEach
            if (item.position != position) {
                _items[index] = item.copy(position = position)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> Modifier.axQsDragSource(
    id: String,
    state: AxQsEditListState<T>,
    onDragStart: () -> Unit,
): Modifier {
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    @Suppress("DEPRECATION")
    return dragAndDropSource(
        block = {
            detectDragGesturesAfterLongPress(
                onDrag = { _, _ -> },
                onDragStart = { anchor ->
                    state.start(id, anchor)
                    currentOnDragStart()
                    startTransfer(
                        DragAndDropTransferData(
                            ClipData(
                                AX_QS_DRAG_LABEL,
                                arrayOf(AX_QS_DRAG_MIME_TYPE),
                                ClipData.Item(id),
                            )
                        )
                    )
                },
            )
        }
    )
}

@Composable
fun <T> Modifier.axQsDropTarget(
    state: AxQsEditListState<T>,
    section: AxQsGridSection = AxQsGridSection.TILES,
    canStack: ((AxQsGridItem<T>, AxQsGridItem<T>) -> Boolean)? = null,
    onStack: ((AxQsGridItem<T>, AxQsGridItem<T>) -> AxQsGridItem<T>?)? = null,
    onDrop: () -> Unit,
): Modifier {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val layoutDirection = LocalLayoutDirection.current
    val view = LocalView.current
    val target =
        remember(state, layoutDirection, section, canStack, onStack, view) {
            object : DragAndDropTarget {
                override fun onMoved(event: DragAndDropEvent) {
                    val dragEvent = event.toAndroidDragEvent()
                    val offset = Offset(dragEvent.x, dragEvent.y)
                    state.onMoved(offset)

                    val stackTargetId =
                        if (canStack != null && onStack != null) {
                            state.findStackTargetAt(offset, section, canStack)
                        } else {
                            null
                        }

                    if (stackTargetId != null) {
                        if (state.hoveredStackTargetId != stackTargetId) {
                            state.restoreSnapshotPreview()
                            state.setHoveredStackTarget(stackTargetId)
                            view.performHapticFeedback(
                                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                            )
                        }
                        return
                    }

                    state.setHoveredStackTarget(null)
                    val prevTarget = state.currentSolvedTarget
                    if (state.moveToCell(offset, section)) {
                        val nextTarget = state.currentSolvedTarget
                        if (prevTarget != null && nextTarget != null && prevTarget != nextTarget) {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                            )
                        }
                        return
                    }
                    val targetItemAtOffset = state.findItemAt(offset, section)
                    if (targetItemAtOffset != null) {
                        val localOffset = state.offsetInGrid(offset, section)
                        state.onTargeting(
                            id = targetItemAtOffset.first,
                            insertAfter =
                                insertAfter(
                                    bounds = targetItemAtOffset.second,
                                    span = state.item(targetItemAtOffset.first)?.span,
                                    offset = localOffset,
                                    layoutDirection = layoutDirection,
                                ),
                        )
                    }
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val draggedId = state.draggedId
                    val hoverTarget = state.hoveredStackTargetId
                    val draggedItem = draggedId?.let { state.item(it) }
                    val targetItem = hoverTarget?.let { state.item(it) }

                    if (
                        hoverTarget != null &&
                            draggedId != null &&
                            onStack != null &&
                            draggedItem != null &&
                            targetItem != null &&
                            canStack != null &&
                            canStack(draggedItem, targetItem)
                    ) {
                        if (state.stackItems(draggedId, hoverTarget, onStack)) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                    }
                    state.finish(currentOnDrop)
                    return true
                }

                override fun onEnded(event: DragAndDropEvent) {
                    state.setHoveredStackTarget(null)
                    if (state.dragInProgress) {
                        state.cancel()
                    }
                }
            }
        }
    return dragAndDropTarget(
        shouldStartDragAndDrop = { AX_QS_DRAG_MIME_TYPE in it.mimeTypes() },
        target = target,
    )
}

@Composable
fun <T> AxQsDragAutoScroll(
    state: AxQsEditListState<T>,
    scrollState: ScrollState,
    viewportBounds: Rect,
) {
    val scrollTarget by
        remember(state, scrollState, viewportBounds) {
            derivedStateOf {
                val position = state.draggedPosition
                if (!state.dragInProgress || !position.isSpecified || viewportBounds.isEmpty) {
                    null
                } else {
                    when {
                        position.y < viewportBounds.top + AX_QS_AUTO_SCROLL_DISTANCE &&
                            scrollState.value > 0 -> 0
                        position.y > viewportBounds.bottom - AX_QS_AUTO_SCROLL_DISTANCE &&
                            scrollState.value < scrollState.maxValue -> scrollState.maxValue
                        else -> null
                    }
                }
            }
        }
    LaunchedEffect(scrollTarget) {
        scrollTarget?.let { target ->
            val duration =
                (abs(target - scrollState.value) * AX_QS_AUTO_SCROLL_SPEED).coerceAtLeast(1)
            scrollState.animateScrollTo(
                target,
                animationSpec = tween(durationMillis = duration, easing = LinearEasing),
            )
        }
    }
}

private fun insertAfter(
    bounds: Rect,
    span: AxQsSpan?,
    offset: Offset,
    layoutDirection: LayoutDirection,
): Boolean? {
    span ?: return null
    return when {
        span.rows > 1 -> offset.y > bounds.center.y
        span.columns > 1 ->
            if (layoutDirection == LayoutDirection.Ltr) {
                offset.x > bounds.center.x
            } else {
                offset.x < bounds.center.x
            }
        else -> null
    }
}

private const val AX_QS_DRAG_LABEL = "ax_qs_item"
private const val AX_QS_DRAG_MIME_TYPE = "axqs/item"
private const val AX_QS_AUTO_SCROLL_DISTANCE = 100
private const val AX_QS_AUTO_SCROLL_SPEED = 2
private const val STACK_TARGET_EDGE_INSET_FRACTION = 0.2f
