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

package com.android.systemui.qs.ax.shared.model

import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel

data class AxQsGridItem<T>(
    val id: String,
    val span: AxQsSpan,
    val minSpan: AxQsSpan,
    val maxSpan: AxQsSpan,
    val value: T,
    val position: AxQsGridPosition? = null,
)

sealed interface AxQsGridValue {
    fun isSpanAllowed(span: AxQsSpan, columns: Int): Boolean = true
    fun nextSpan(currentSpan: AxQsSpan, columns: Int): AxQsSpan = currentSpan

    data class Tile(
        val viewModel: TileViewModel? = null,
        val editViewModel: EditTileViewModel? = null,
    ) : AxQsGridValue {
        override fun isSpanAllowed(span: AxQsSpan, columns: Int): Boolean =
            span == AxQsSpan(1, 1) ||
                (columns >= 2 && span == AxQsSpan(2, 1)) ||
                (columns >= 2 && span == AxQsSpan(2, 2))

        override fun nextSpan(currentSpan: AxQsSpan, columns: Int): AxQsSpan {
            return when (currentSpan) {
                AxQsSpan(1, 1) -> if (columns >= 2) AxQsSpan(2, 1) else AxQsSpan(1, 1)
                AxQsSpan(2, 1) -> if (columns >= 2) AxQsSpan(2, 2) else AxQsSpan(1, 1)
                AxQsSpan(2, 2) -> AxQsSpan(1, 1)
                else -> AxQsSpan(1, 1)
            }
        }
    }

    data class Control(val control: AxQsControl) : AxQsGridValue {
        override fun isSpanAllowed(span: AxQsSpan, columns: Int): Boolean =
            control.isSpanAllowed(span, columns)

        override fun nextSpan(currentSpan: AxQsSpan, columns: Int): AxQsSpan =
            control.nextSpan(currentSpan, columns)
    }

    data class Stack(
        val tiles: List<TileViewModel> = emptyList(),
        val editTiles: List<EditTileViewModel> = emptyList(),
    ) : AxQsGridValue {
        override fun isSpanAllowed(span: AxQsSpan, columns: Int): Boolean =
            span == AxQsSpan(columns = 2, rows = 2)

        override fun nextSpan(currentSpan: AxQsSpan, columns: Int): AxQsSpan =
            AxQsSpan(columns = 2, rows = 2)
    }
}

enum class AxMediaSurface(val dismissible: Boolean) {
    CONTROL(false),
    SEPARATE_QQS(true),
    LOCKSCREEN(true),
    DYNAMIC_BAR(false),
}
