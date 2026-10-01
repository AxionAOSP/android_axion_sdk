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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class AxQsSpan(val columns: Int, val rows: Int) {
    init {
        require(columns in MIN_COLUMNS..MAX_COLUMNS)
        require(rows in MIN_ROWS..MAX_ROWS)
    }

    fun coerceIn(min: AxQsSpan, max: AxQsSpan): AxQsSpan {
        return AxQsSpan(
            columns = columns.coerceIn(min.columns, max.columns),
            rows = rows.coerceIn(min.rows, max.rows),
        )
    }

    fun coerceTileSpan(columns: Int): AxQsSpan {
        return when {
            this == AxQsSpan(2, 2) && columns >= 2 -> this
            columns >= 2 && this.columns >= 2 && this.rows == 1 -> AxQsSpan(2, 1)
            else -> AxQsSpan.TileDefault
        }
    }

    override fun toString(): String = "${columns}x$rows"

    companion object {
        const val MIN_COLUMNS = 1
        const val MAX_COLUMNS = 16
        const val MIN_ROWS = 1
        const val MAX_ROWS = 4

        val TileDefault = AxQsSpan(1, 1)
        val TileWideDefault = AxQsSpan(2, 1)
        val TileLargeSquare = AxQsSpan(2, 2)
        val TileMin = TileDefault
        val MediaDefault = AxQsSpan(2, 2)
        val MediaMin = AxQsSpan(2, 1)
        val Max = AxQsSpan(4, 4)

        fun tileMax(columns: Int): AxQsSpan {
            return AxQsSpan(
                minOf(2, columns.coerceAtLeast(TileMin.columns)),
                2,
            )
        }

        fun parse(value: String): AxQsSpan? {
            val parts = value.split('x', limit = 2)
            if (parts.size != 2) return null
            val columns = parts[0].toIntOrNull() ?: return null
            val rows = parts[1].toIntOrNull() ?: return null
            if (columns !in MIN_COLUMNS..MAX_COLUMNS || rows !in MIN_ROWS..MAX_ROWS) return null
            return AxQsSpan(columns, rows)
        }
    }
}

data class AxQsWidgetSpanSpec(
    val default: (columns: Int) -> AxQsSpan,
    val min: (columns: Int) -> AxQsSpan = default,
    val max: (columns: Int) -> AxQsSpan = default,
    val isResizable: Boolean = false,
) {
    fun resolve(columns: Int): AxQsControlSpans {
        val minSpan = min(columns)
        val maxSpan = max(columns)
        val defSpan = default(columns).coerceIn(minSpan, maxSpan)
        return AxQsControlSpans(default = defSpan, min = minSpan, max = maxSpan)
    }

    companion object {
        fun fixed(columns: Int, rows: Int): AxQsWidgetSpanSpec {
            val span = AxQsSpan(columns, rows)
            return AxQsWidgetSpanSpec(
                default = { span },
                min = { span },
                max = { span },
                isResizable = false,
            )
        }

        fun fullWidth(
            rows: Int,
            minColumns: Int = 2,
            isResizable: Boolean = false,
        ): AxQsWidgetSpanSpec {
            return AxQsWidgetSpanSpec(
                default = { cols -> AxQsSpan(cols.coerceAtLeast(minColumns), rows) },
                min = { AxQsSpan(minColumns, rows) },
                max = { cols -> AxQsSpan(cols.coerceAtLeast(minColumns), rows) },
                isResizable = isResizable,
            )
        }

        fun responsive(
            default: (columns: Int) -> AxQsSpan,
            min: (columns: Int) -> AxQsSpan = default,
            max: (columns: Int) -> AxQsSpan = default,
            isResizable: Boolean = true,
        ): AxQsWidgetSpanSpec {
            return AxQsWidgetSpanSpec(
                default = default,
                min = min,
                max = max,
                isResizable = isResizable,
            )
        }
    }
}

enum class AxQsControl(
    val id: String,
    val spanSpec: AxQsWidgetSpanSpec,
) {
    BRIGHTNESS(
        id = "control:brightness",
        spanSpec = AxQsWidgetSpanSpec.fixed(columns = 1, rows = 2),
    ),
    VOLUME(
        id = "control:volume",
        spanSpec = AxQsWidgetSpanSpec.fixed(columns = 1, rows = 2),
    ),
    AUTO_BRIGHTNESS(
        id = "control:auto_brightness",
        spanSpec = AxQsWidgetSpanSpec.fixed(columns = 1, rows = 1),
    ),
    VOLUME_MUTE(
        id = "control:volume_mute",
        spanSpec = AxQsWidgetSpanSpec.fixed(columns = 1, rows = 1),
    ),
    RINGER(
        id = "control:ringer",
        spanSpec = AxQsWidgetSpanSpec.fixed(columns = 2, rows = 1),
    ),
    MEDIA(
        id = "control:media",
        spanSpec =
            AxQsWidgetSpanSpec.responsive(
                default = { AxQsSpan(2, 2) },
                min = { AxQsSpan(2, 1) },
                max = { cols -> AxQsSpan(cols.coerceAtLeast(2), 2) },
                isResizable = true,
            ),
    );

    val defaultSpan: AxQsSpan
        get() = spanSpec.resolve(4).default

    val minSpan: AxQsSpan
        get() = spanSpec.resolve(4).min

    val maxSpan: AxQsSpan
        get() = spanSpec.resolve(4).max

    fun spans(columns: Int): AxQsControlSpans = spanSpec.resolve(columns)

    fun isSpanAllowed(span: AxQsSpan, columns: Int): Boolean {
        if (!spanSpec.isResizable) return span == spans(columns).default
        val spans = spans(columns)
        return span == span.coerceIn(spans.min, spans.max)
    }

    fun coerceSpan(span: AxQsSpan, columns: Int): AxQsSpan {
        val spans = spans(columns)
        if (!spanSpec.isResizable) return spans.default
        return span.coerceIn(spans.min, spans.max)
    }

    fun nextSpan(span: AxQsSpan, columns: Int): AxQsSpan {
        val spans = spans(columns)
        if (!spanSpec.isResizable) return spans.default
        val nextCol = if (span.columns < spans.max.columns) span.columns + 1 else spans.min.columns
        return AxQsSpan(nextCol, span.rows)
    }

    fun resizeSpan(startSpan: AxQsSpan, columnDelta: Int, rowDelta: Int, columns: Int): AxQsSpan {
        val spans = spans(columns)
        if (!spanSpec.isResizable) return spans.default
        return AxQsSpan(
            columns =
                (startSpan.columns + columnDelta).coerceIn(
                    spans.min.columns,
                    spans.max.columns,
                ),
            rows = (startSpan.rows + rowDelta).coerceIn(spans.min.rows, spans.max.rows),
        )
    }

    val isVerticalSlider: Boolean
        get() = this == BRIGHTNESS || this == VOLUME

    val isSlider: Boolean
        get() = isVerticalSlider
}

data class AxQsControlSpans(val default: AxQsSpan, val min: AxQsSpan, val max: AxQsSpan)

data class AxQsGridPosition(val column: Int, val row: Int) {
    init {
        require(column >= 0)
        require(row >= 0)
    }

    override fun toString(): String = "$column:$row"

    companion object {
        fun parse(value: String): AxQsGridPosition? {
            val parts = value.split(':', limit = 2)
            if (parts.size != 2) return null
            val column = parts[0].toIntOrNull() ?: return null
            val row = parts[1].toIntOrNull() ?: return null
            if (column < 0 || row < 0) return null
            return AxQsGridPosition(column, row)
        }
    }
}

data class AxQsLayoutData(
    val location: AxQsLayout,
    val order: List<String> = emptyList(),
    val spans: Map<String, AxQsSpan> = emptyMap(),
    val positions: Map<String, AxQsGridPosition> = emptyMap(),
    val stacks: Map<String, List<String>> = emptyMap(),
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put(KEY_LOCATION, location.name.lowercase())

        val orderArray = JSONArray()
        order.forEach { orderArray.put(it) }
        json.put(KEY_ORDER, orderArray)

        val spansObject = JSONObject()
        spans.forEach { (id, span) -> spansObject.put(id, span.toString()) }
        json.put(KEY_SPANS, spansObject)

        val positionsObject = JSONObject()
        positions.forEach { (id, pos) -> positionsObject.put(id, pos.toString()) }
        json.put(KEY_POSITIONS, positionsObject)

        val stacksObject = JSONObject()
        stacks.forEach { (id, tileIds) ->
            val array = JSONArray()
            tileIds.forEach { array.put(it) }
            stacksObject.put(id, array)
        }
        json.put(KEY_STACKS, stacksObject)

        return json
    }

    companion object {
        private const val KEY_LOCATION = "location"
        private const val KEY_ORDER = "order"
        private const val KEY_SPANS = "spans"
        private const val KEY_POSITIONS = "positions"
        private const val KEY_STACKS = "stacks"

        fun fromJsonObject(
            json: JSONObject,
            fallbackLocation: AxQsLayout? = null,
        ): AxQsLayoutData? {
            val locStr = json.optString(KEY_LOCATION)
            val location =
                AxQsLayout.entries.firstOrNull { it.name.equals(locStr, ignoreCase = true) }
                    ?: fallbackLocation
                    ?: return null
            val order = buildList {
                val orderArray = json.optJSONArray(KEY_ORDER)
                if (orderArray != null) {
                    for (i in 0 until orderArray.length()) {
                        val id = orderArray.optString(i)
                        if (id.isNotBlank()) add(id)
                    }
                }
            }
            val spans = buildMap {
                val spansObject = json.optJSONObject(KEY_SPANS)
                if (spansObject != null) {
                    val keys = spansObject.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val spanStr = spansObject.optString(id)
                        AxQsSpan.parse(spanStr)?.let { put(id, it) }
                    }
                }
            }
            val positions = buildMap {
                val positionsObject = json.optJSONObject(KEY_POSITIONS)
                if (positionsObject != null) {
                    val keys = positionsObject.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val posStr = positionsObject.optString(id)
                        AxQsGridPosition.parse(posStr)?.let { put(id, it) }
                    }
                }
            }
            val stacks = buildMap {
                val stacksObject = json.optJSONObject(KEY_STACKS)
                if (stacksObject != null) {
                    val keys = stacksObject.keys()
                    while (keys.hasNext()) {
                        val id = keys.next()
                        val array = stacksObject.optJSONArray(id)
                        if (array != null) {
                            val tileList =
                                (0 until array.length()).mapNotNull { i ->
                                    val tileId = array.optString(i)
                                    tileId.takeIf { it.isNotBlank() }
                                }
                            if (tileList.isNotEmpty()) {
                                put(id, tileList)
                            }
                        }
                    }
                }
            }
            return AxQsLayoutData(
                location = location,
                order = order,
                spans = spans,
                positions = positions,
                stacks = stacks,
            )
        }

        fun parseList(jsonStr: String?): List<AxQsLayoutData> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return runCatching {
                if (jsonStr.trim().startsWith("[")) {
                    val array = JSONArray(jsonStr)
                    (0 until array.length()).mapNotNull { i ->
                        array.optJSONObject(i)?.let { fromJsonObject(it) }
                    }
                } else {
                    val obj = JSONObject(jsonStr)
                    val keys = obj.keys()
                    buildList {
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val subObj = obj.optJSONObject(key)
                            if (subObj != null) {
                                val fallback =
                                    AxQsLayout.entries.firstOrNull {
                                        it.name.equals(key, ignoreCase = true)
                                    }
                                fromJsonObject(subObj, fallback)?.let { add(it) }
                            }
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun toJson(layouts: Collection<AxQsLayoutData>): String {
            val root = JSONObject()
            layouts.forEach { data ->
                root.put(data.location.name.lowercase(), data.toJsonObject())
            }
            return root.toString()
        }
    }
}

enum class AxQsGridSection {
    CONTROLS,
    TILES,
}

object AxQsGridColumns {
    const val MIN_COLUMNS = 4
    const val DEFAULT_COLUMNS = 4

    fun columnRange(
        screenWidthDp: Int,
        isSplitShade: Boolean = false,
    ): IntRange {
        val availableWidth = if (isSplitShade) screenWidthDp / 2 else screenWidthDp
        val maxColumns =
            when {
                availableWidth >= 840 -> 8
                availableWidth >= 600 -> 6
                else -> 5
            }
        return MIN_COLUMNS..maxColumns
    }
}

enum class AxQsGridLayout(
    val layout: AxQsLayout,
) {
    QQS(AxQsLayout.QQS),
    QS(AxQsLayout.QS),
    SPLIT_SHADE(AxQsLayout.SPLIT_SHADE);

    val defaultColumns: Int
        get() = AxQsGridColumns.DEFAULT_COLUMNS

    fun columnRange(screenWidthDp: Int): IntRange =
        AxQsGridColumns.columnRange(screenWidthDp, layout == AxQsLayout.SPLIT_SHADE)

    companion object {
        fun from(layout: AxQsLayout): AxQsGridLayout =
            when (layout) {
                AxQsLayout.QQS -> QQS
                AxQsLayout.QS -> QS
                AxQsLayout.SPLIT_SHADE -> SPLIT_SHADE
            }
    }
}

enum class AxQsLayout {
    QQS,
    QS,
    SPLIT_SHADE,
}

val LocalAxQsLayout = compositionLocalOf { AxQsLayout.QS }

enum class AxQsPanelMode(val settingValue: Int) {
    TOGETHER(0),
    SEPARATE(1);

    companion object {
        fun fromSetting(value: Int): AxQsPanelMode =
            entries.firstOrNull { it.settingValue == value } ?: TOGETHER
    }
}

enum class AxQsVerticalSliderStyle(val settingValue: Int) {
    M3_EXPRESSIVE(0),
    PLATFORM(1);

    companion object {
        fun fromSetting(value: Int): AxQsVerticalSliderStyle =
            entries.firstOrNull { it.settingValue == value } ?: M3_EXPRESSIVE
    }
}

data class AxQsVerticalSliderKey(val layout: AxQsLayout, val control: AxQsControl)

object AxQsLayoutPadding {
    val PORTRAIT_SIDE_PADDING = AxQsTokens.Geometry.PortraitSidePadding
    val LANDSCAPE_SIDE_PADDING = AxQsTokens.Geometry.LandscapeSidePadding
    const val PORTRAIT_SIDE_FRACTION = AxQsTokens.Geometry.PORTRAIT_SIDE_FRACTION
    const val LANDSCAPE_SIDE_FRACTION = AxQsTokens.Geometry.LANDSCAPE_SIDE_FRACTION
    val LANDSCAPE_SPLIT_GRID_SPACING_DP = AxQsTokens.Geometry.LandscapeSplitGridSpacing.value
}
