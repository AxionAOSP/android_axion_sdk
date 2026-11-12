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

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.android.systemui.qs.ax.domain.interactor.AxQsLayoutInteractor
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import kotlin.math.abs

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <T> AxQsGrid(
    items: List<AxQsGridItem<T>>,
    columns: Int,
    rowHeight: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    maxRows: Int? = null,
    minimumRows: Int = 0,
    animateItemBounds: Boolean = false,
    staticItemId: String? = null,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
    onItemBounds: (String, Rect) -> Unit = { _, _ -> },
    onCells: ((List<AxQsGridCell>) -> Unit)? = null,
    content: @Composable (AxQsGridItem<T>) -> Unit,
) {
    require(columns > 0)
    val itemSnapshot = items.toList()
    val placements =
        remember(itemSnapshot, columns, maxRows, allowStraddle, qqsMaxRows) {
            packItems(itemSnapshot, columns, maxRows, allowStraddle, qqsMaxRows)
        }
    LookaheadScope {
        val lookaheadScope = this
        val boundsTransform = remember {
            BoundsTransform { _, _ ->
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            }
        }
        Layout(
            content = {
                placements.forEach { placement ->
                    key(placement.item.id) {
                        val itemModifier =
                            if (animateItemBounds && placement.item.id != staticItemId) {
                                Modifier.animateBounds(lookaheadScope, boundsTransform = boundsTransform)
                            } else {
                                Modifier
                            }
                        Box(itemModifier.fillMaxSize()) {
                            key(placement.item.value, placement.column, placement.row) {
                                content(
                                    placement.item.copy(
                                        position = AxQsGridPosition(placement.column, placement.row)
                                    )
                                )
                            }
                        }
                    }
                }
            },
            modifier = modifier,
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val horizontalSpacingPx = spacing.roundToPx()
            val verticalSpacingPx = horizontalSpacingPx
            val availableWidth = (width - horizontalSpacingPx * (columns - 1)).coerceAtLeast(0)
            val rowHeightPx = rowHeight.roundToPx()

            fun columnStart(column: Int): Int {
                return availableWidth * column / columns + horizontalSpacingPx * column
            }

            val placeables =
                measurables.mapIndexed { index, measurable ->
                    val placement = placements[index]
                    val start = columnStart(placement.column)
                    val end =
                        columnStart(placement.column + placement.item.span.columns) -
                            horizontalSpacingPx
                    val itemWidth = (end - start).coerceAtLeast(0)
                    val itemHeight =
                        rowHeightPx * placement.item.span.rows +
                            verticalSpacingPx * (placement.item.span.rows - 1)
                    val top = placement.row * (rowHeightPx + verticalSpacingPx)
                    onItemBounds(
                        placement.item.id,
                        Rect(
                            left = start.toFloat(),
                            top = top.toFloat(),
                            right = (start + itemWidth).toFloat(),
                            bottom = (top + itemHeight).toFloat(),
                        ),
                    )
                    measurable.measure(Constraints.fixed(itemWidth, itemHeight))
                }
            val rows =
                maxOf(placements.maxOfOrNull { it.row + it.item.span.rows } ?: 0, minimumRows)
            onCells?.invoke(
                buildList {
                    repeat(rows) { row ->
                        repeat(columns) { column ->
                            val left = columnStart(column)
                            val top = row * (rowHeightPx + verticalSpacingPx)
                            add(
                                AxQsGridCell(
                                    position = AxQsGridPosition(column, row),
                                    bounds =
                                        Rect(
                                            left = left.toFloat(),
                                            top = top.toFloat(),
                                            right =
                                                (columnStart(column + 1) - horizontalSpacingPx)
                                                    .toFloat(),
                                            bottom = (top + rowHeightPx).toFloat(),
                                        ),
                                )
                            )
                        }
                    }
                }
            )
            val height =
                (rowHeightPx * rows + verticalSpacingPx * (rows - 1).coerceAtLeast(0)).coerceIn(
                    constraints.minHeight,
                    constraints.maxHeight,
                )

            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val placement = placements[index]
                    placeable.placeRelative(
                        x = columnStart(placement.column),
                        y = placement.row * (rowHeightPx + verticalSpacingPx),
                    )
                }
            }
        }
    }
}

data class AxQsGridCell(val position: AxQsGridPosition, val bounds: Rect)

internal fun axQsGridCellWidth(gridWidth: Dp, columns: Int, spacing: Dp): Dp =
    calculateAxQsCellConfig(gridWidth, columns, spacing).cellWidth

internal data class AxQsPlacement<T>(val item: AxQsGridItem<T>, val column: Int, val row: Int)

internal fun <T> packItems(
    items: List<AxQsGridItem<T>>,
    columns: Int,
    maxRows: Int?,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
    minUnpositionedRows: Map<String, Int> = emptyMap(),
): List<AxQsPlacement<T>> {
    val occupied = mutableListOf<BooleanArray>()
    val placements = mutableMapOf<String, AxQsPlacement<T>>()

    fun place(item: AxQsGridItem<T>, column: Int, row: Int): AxQsPlacement<T>? {
        val width = item.span.columns.coerceAtMost(columns)
        val height = item.span.rows
        if (column < 0 || column + width > columns || row < 0) return null
        if (maxRows != null && row + height > maxRows) return null
        if (
            !allowStraddle &&
                AxQsLayoutInteractor.wouldStraddleQqs(AxQsSpan(width, height), row, qqsMaxRows)
        ) {
            return null
        }
        val fits =
            (row until row + height).all { candidateRow ->
                (column until column + width).all { candidateColumn ->
                    occupied.getOrNull(candidateRow)?.get(candidateColumn) != true
                }
            }
        if (!fits) return null
        while (occupied.size < row + height) {
            occupied.add(BooleanArray(columns))
        }
        for (occupiedRow in row until row + height) {
            for (occupiedColumn in column until column + width) {
                occupied[occupiedRow][occupiedColumn] = true
            }
        }
        return AxQsPlacement(
            item = item.copy(span = AxQsSpan(width, height)),
            column = column,
            row = row,
        )
    }

    items.forEach { item ->
        item.position?.let { position ->
            place(item, position.column, position.row)?.let { placements[item.id] = it }
        }
    }
    items.forEach { item ->
        if (item.id in placements) return@forEach
        val width = item.span.columns.coerceAtMost(columns)
        var row = (minUnpositionedRows[item.id] ?: 0).coerceAtLeast(0)
        while (maxRows == null || row + item.span.rows <= maxRows) {
            val placement =
                (0..columns - width).firstNotNullOfOrNull { column -> place(item, column, row) }
            if (placement != null) {
                placements[item.id] = placement
                break
            }
            row++
        }
    }
    return items.mapNotNull { placements[it.id] }
}

internal fun <T> fitAxQsGridItems(
    items: List<AxQsGridItem<T>>,
    columns: Int,
    maxRows: Int,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = maxRows,
): List<AxQsGridItem<T>> =
    packItems(items, columns, maxRows, allowStraddle, qqsMaxRows).map { it.item }

internal fun <T> canFitAxQsGridItems(
    items: List<AxQsGridItem<T>>,
    columns: Int,
    maxRows: Int,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
): Boolean = packItems(items, columns, maxRows, allowStraddle, qqsMaxRows).size == items.size

internal fun <T> axQsGridRowCount(
    items: List<AxQsGridItem<T>>,
    columns: Int,
    maxRows: Int?,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
): Int =
    packItems(items, columns, maxRows, allowStraddle, qqsMaxRows).maxOfOrNull {
        it.row + it.item.span.rows
    } ?: 0

internal fun <T> solveResizeItems(
    baseItems: List<AxQsGridItem<T>>,
    itemId: String,
    newSpan: AxQsSpan,
    columns: Int,
    allowStraddle: Boolean = false,
    qqsMaxRows: Int = AxQsLayoutInteractor.QQS_MAX_ROWS,
    preservePinnedPredicate: (AxQsGridItem<T>) -> Boolean = { false },
): List<AxQsGridItem<T>> {
    if (columns <= 0 || baseItems.isEmpty()) return baseItems

    val normalized =
        packItems(
                items = baseItems,
                columns = columns,
                maxRows = null,
                allowStraddle = allowStraddle,
                qqsMaxRows = qqsMaxRows,
            )
            .sortedWith(compareBy({ it.row }, { it.column }))
            .map { placement ->
                placement.item.copy(position = AxQsGridPosition(placement.column, placement.row))
            }

    val source = normalized.firstOrNull { it.id == itemId } ?: return normalized
    val origin = source.position ?: return normalized
    val width = newSpan.columns.coerceIn(1, columns)
    val height = newSpan.rows.coerceAtLeast(1)
    val targetSpan = AxQsSpan(width, height)
    if (source.span == targetSpan) return normalized

    val others = normalized.filter { it.id != itemId }
    val isShrinking = width < source.span.columns || height < source.span.rows

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

    fun fitsAgainstUpperFrozen(col: Int, row: Int): Boolean {
        if (col < 0 || col + width > columns || row < 0) return false
        if (
            !allowStraddle &&
                AxQsLayoutInteractor.wouldStraddleQqs(targetSpan, row, qqsMaxRows)
        ) {
            return false
        }
        val candidate = AxQsGridPosition(col, row)
        return others.none { other ->
            val pos = other.position ?: return@none false
            pos.row < row && overlaps(pos, other.span, candidate, targetSpan)
        }
    }

    fun fitsWithZeroCollisions(col: Int, row: Int): Boolean {
        if (!fitsAgainstUpperFrozen(col, row)) return false
        val candidate = AxQsGridPosition(col, row)
        return others.none { other ->
            val pos = other.position ?: return@none false
            overlaps(pos, other.span, candidate, targetSpan)
        }
    }

    val clampedCol = origin.column.coerceIn(0, (columns - width).coerceAtLeast(0))
    var targetRow = origin.row
    if (
        !allowStraddle &&
            AxQsLayoutInteractor.wouldStraddleQqs(targetSpan, targetRow, qqsMaxRows)
    ) {
        targetRow = qqsMaxRows
    }

    if (!isShrinking) {
        val coveringCols =
            (0..columns - width)
                .filter { col -> col <= origin.column && origin.column < col + width }
                .sortedWith(compareBy({ abs(it - clampedCol) }, { it }))
                .ifEmpty { listOf(clampedCol) }
        val freeCol = coveringCols.firstOrNull { col -> fitsWithZeroCollisions(col, targetRow) }
        if (freeCol != null) {
            val absorbed =
                normalized.map { current ->
                    if (current.id == itemId) {
                        current.copy(
                            span = targetSpan,
                            position = AxQsGridPosition(freeCol, targetRow),
                        )
                    } else {
                        current
                    }
                }
            return absorbed.sortedWith(
                compareBy({ it.position?.row ?: 0 }, { it.position?.column ?: 0 })
            )
        }
    }

    var resolvedCol: Int? = null
    val candidateCols =
        (0..columns - width).sortedWith(compareBy({ abs(it - clampedCol) }, { it }))
    while (resolvedCol == null) {
        resolvedCol = candidateCols.firstOrNull { col -> fitsAgainstUpperFrozen(col, targetRow) }
        if (resolvedCol == null) {
            targetRow++
        }
    }

    val targetPos = AxQsGridPosition(resolvedCol, targetRow)
    val rippleStartCol = minOf(origin.column, resolvedCol)
    val rippleStartRow = minOf(origin.row, targetRow)
    val maxPullUp = if (isShrinking) maxOf(1, source.span.rows) else 1

    val unpinnedIds = mutableSetOf<String>()
    val minUnpositionedRows = mutableMapOf<String, Int>()
    for (other in others) {
        val pos = other.position ?: continue
        val directlyHit = overlaps(pos, other.span, targetPos, targetSpan)
        val keepPinned = preservePinnedPredicate(other) && !directlyHit
        val inReadingOrderTail =
            pos.row > rippleStartRow ||
                (pos.row == rippleStartRow && pos.column >= rippleStartCol)
        if (!keepPinned && (inReadingOrderTail || directlyHit)) {
            unpinnedIds.add(other.id)
            minUnpositionedRows[other.id] = maxOf(rippleStartRow, pos.row - maxPullUp)
        }
    }

    val prepared =
        normalized.map { current ->
            when {
                current.id == itemId -> current.copy(span = targetSpan, position = targetPos)
                current.id in unpinnedIds -> current.copy(position = null)
                else -> current
            }
        }

    return packItems(
            items = prepared,
            columns = columns,
            maxRows = null,
            allowStraddle = allowStraddle,
            qqsMaxRows = qqsMaxRows,
            minUnpositionedRows = minUnpositionedRows,
        )
        .sortedWith(compareBy({ it.row }, { it.column }))
        .map { placement ->
            placement.item.copy(position = AxQsGridPosition(placement.column, placement.row))
        }
}
