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

package com.android.systemui.qs.ax.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.grid.fitAxQsGridItems
import com.android.systemui.qs.ax.ui.grid.packItems
import javax.inject.Inject

@SysUISingleton
class AxQsLayoutInteractor
@Inject
constructor() {
    fun <T> filterQqsItems(
        items: List<AxQsGridItem<T>>,
        columns: Int,
        maxRows: Int = QQS_MAX_ROWS,
    ): List<AxQsGridItem<T>> {
        return fitAxQsGridItems(items, columns, maxRows)
    }

    fun wouldStraddleQqs(
        span: AxQsSpan,
        position: AxQsGridPosition?,
        maxRows: Int = QQS_MAX_ROWS,
    ): Boolean {
        val row = position?.row ?: return false
        return wouldStraddleQqs(span, row, maxRows)
    }

    fun wouldStraddleQqs(
        span: AxQsSpan,
        row: Int,
        maxRows: Int = QQS_MAX_ROWS,
    ): Boolean {
        return row < maxRows && (row + span.rows) > maxRows
    }

    fun isPositionAllowedForSpan(
        span: AxQsSpan,
        row: Int,
        maxRows: Int = QQS_MAX_ROWS,
    ): Boolean {
        return !wouldStraddleQqs(span, row, maxRows)
    }

    fun <T> sanitizePositionsForTogetherMode(
        items: List<AxQsGridItem<T>>,
        columns: Int,
        maxRows: Int = QQS_MAX_ROWS,
    ): List<AxQsGridItem<T>> {
        val hasStraddleConflict =
            items.any { item ->
                val row = item.position?.row ?: return@any false
                wouldStraddleQqs(item.span, row, maxRows)
            }
        if (!hasStraddleConflict) return items

        val placements = packItems(items, columns, maxRows = null, allowStraddle = false)
        val placementMap = placements.associate { it.item.id to AxQsGridPosition(it.column, it.row) }
        return items.map { item ->
            val newPos = placementMap[item.id]
            if (newPos != null && newPos != item.position) {
                item.copy(position = newPos)
            } else {
                item
            }
        }
    }

    fun canResizeControl(control: AxQsControl, targetSpan: AxQsSpan, columns: Int): Boolean {
        if (!control.spanSpec.isResizable) return false
        return control.isSpanAllowed(targetSpan, columns)
    }

    companion object {
        const val QQS_MAX_ROWS: Int = 2
        const val QQS_BOUNDARY_ROW: Int = 1

        fun wouldStraddleQqs(
            span: AxQsSpan,
            row: Int,
            maxRows: Int = QQS_MAX_ROWS,
        ): Boolean {
            return row < maxRows && (row + span.rows) > maxRows
        }
    }
}
