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

import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.composefragment.LocalBlurEnabled

data class AxQsCellConfig(
    val gridWidth: Dp,
    val columns: Int,
    val spacing: Dp,
    val cellHeight: Dp? = null,
    val densityScale: Float =
        if (cellHeight != null && Defaults.TileHeight > 0.dp) {
            cellHeight / Defaults.TileHeight
        } else {
            1f
        },
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val densityDpi: Int = 0,
) {
    val cellWidth: Dp =
        if (columns <= 1) {
            gridWidth.coerceAtLeast(0.dp)
        } else {
            ((gridWidth - spacing * (columns - 1).coerceAtLeast(0)) / columns.coerceAtLeast(1))
                .coerceAtLeast(0.dp)
        }

    val rowHeight: Dp =
        if (cellWidth > 0.dp) {
            (cellHeight ?: (Defaults.TileHeight * densityScale)).coerceAtMost(cellWidth)
        } else {
            cellHeight ?: (Defaults.TileHeight * densityScale)
        }

    val iconTileSize: Dp =
        if (rowHeight > 0.dp && cellWidth > 0.dp) {
            (Defaults.CircleTileSize * densityScale).coerceAtMost(minOf(cellWidth, rowHeight))
        } else {
            cellWidth
        }

    val effectiveScale: Float
        get() =
            if (Defaults.TileHeight > 0.dp && rowHeight > 0.dp) {
                ((rowHeight / Defaults.TileHeight) * densityScale).coerceAtLeast(0f)
            } else {
                densityScale.coerceAtLeast(0f)
            }

    val largeCornerRadius: Dp
        get() =
            if (rowHeight > 0.dp) {
                (Defaults.LargeCornerRadius * effectiveScale).coerceAtMost(rowHeight / 2)
            } else {
                Defaults.LargeCornerRadius * effectiveScale
            }

    val iconContainerSize: Dp
        get() =
            if (rowHeight > 0.dp) {
                (Defaults.IconContainerSize * effectiveScale).coerceAtMost(
                    (rowHeight - 8.dp).coerceAtLeast(16.dp)
                )
            } else {
                Defaults.IconContainerSize * effectiveScale
            }

    val iconSize: Dp
        get() =
            (Defaults.IconSize * effectiveScale).coerceAtMost(iconContainerSize * 0.6f)

    val tileStartPadding: Dp
        get() =
            if (rowHeight > 0.dp) {
                ((rowHeight - iconContainerSize) / 2).coerceAtLeast(6.dp)
            } else {
                12.dp * effectiveScale
            }

    val tileEndPadding: Dp get() = 16.dp * effectiveScale
    val dividerWidth: Dp get() = Defaults.DividerWidth * effectiveScale
    val dividerHeight: Dp get() = Defaults.DividerHeight * effectiveScale

    val sliderThumbWidth: Dp get() = 4.dp
    val sliderTrackHeight: Dp get() = 64.dp * effectiveScale

    fun modularGridWidth(cols: Int = columns): Dp {
        val safeCols = cols.coerceAtLeast(1)
        return Defaults.CircleTileSize * safeCols + spacing * (safeCols - 1)
    }

    fun itemWidth(spanColumns: Int, sectionColumns: Int = columns): Dp {
        val cols = spanColumns.coerceAtLeast(1)
        if (sectionColumns == columns) {
            return cellWidth * cols + spacing * (cols - 1)
        }
        val sectionCellWidth =
            if (sectionColumns <= 1) {
                gridWidth.coerceAtLeast(0.dp)
            } else {
                val availableWidth =
                    (gridWidth - spacing * (sectionColumns - 1).coerceAtLeast(0)).coerceAtLeast(
                        0.dp
                    )
                availableWidth / sectionColumns.coerceAtLeast(1)
            }
        return sectionCellWidth * cols + spacing * (cols - 1)
    }

    fun itemHeight(spanRows: Int): Dp {
        val rows = spanRows.coerceAtLeast(1)
        return rowHeight * rows + spacing * (rows - 1)
    }

    fun itemHeight(span: AxQsSpan): Dp = itemHeight(span.rows)

    fun itemWidth(span: AxQsSpan, sectionColumns: Int = columns): Dp =
        itemWidth(span.columns, sectionColumns)

    fun itemSize(span: AxQsSpan, sectionColumns: Int = columns): DpSize {
        return DpSize(itemWidth(span.columns, sectionColumns), itemHeight(span.rows))
    }

    fun tileShape(iconOnly: Boolean, span: AxQsSpan = AxQsSpan.TileDefault): Shape {
        return when {
            iconOnly || (span.columns == 1 && span.rows == 1) -> CircleShape
            span.columns > 1 && span.rows == 1 -> RoundedCornerShape(percent = 50)
            else -> RoundedCornerShape(largeCornerRadius)
        }
    }

    @Composable
    fun backgroundColor(): Color = Defaults.backgroundColor()

    @Composable
    fun dividerColor(state: Int): Color = Defaults.dividerColor(state)

    companion object Defaults {
        val TileHeight = AxQsTokens.Geometry.TileHeight
        val TileSpacing = AxQsTokens.Geometry.TileSpacing
        val CircleTileSize = AxQsTokens.Geometry.CircleTileSize
        val IconContainerSize = AxQsTokens.Geometry.IconContainerSize
        val LargeIconSize = AxQsTokens.Geometry.LargeIconSize
        val IconSize = AxQsTokens.Geometry.IconSize
        val LargeCornerRadius = AxQsTokens.CornerRadius.LargeCornerRadius
        val InactiveCornerRadius = 50.dp
        val DividerWidth = AxQsTokens.Geometry.DividerWidth
        val DividerHeight = AxQsTokens.Geometry.DividerHeight
        val IconDividerSpacing = AxQsTokens.Geometry.IconDividerSpacing
        val DividerLabelSpacing = AxQsTokens.Geometry.DividerLabelSpacing
        val LargeTileStartPadding = AxQsTokens.Geometry.LargeTileStartPadding
        val LargeTileEndPadding = AxQsTokens.Geometry.LargeTileEndPadding

        fun modularGridWidth(columns: Int, densityScale: Float = 1f): Dp {
            val safeCols = columns.coerceAtLeast(1)
            return (CircleTileSize * safeCols + TileSpacing * (safeCols - 1)) * densityScale
        }

        @Composable
        fun dividerColor(state: Int): Color {
            return when (state) {
                STATE_ACTIVE -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                STATE_INACTIVE -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            }
        }

        @Composable
        fun backgroundColor(): Color {
            return if (LocalBlurEnabled.current) {
                LocalAndroidColorScheme.current.surfaceEffect1
            } else {
                MaterialTheme.colorScheme.surfaceBright
            }
        }
    }
}

typealias AxTileDefaults = AxQsCellConfig.Defaults

val LocalAxQsCellConfig = staticCompositionLocalOf {
    calculateAxQsCellConfig(
        gridWidth = 420.dp,
        columns = 4,
        spacing = AxQsCellConfig.Defaults.TileSpacing,
        cellHeight = AxQsCellConfig.Defaults.TileHeight,
        densityScale = 1f,
    )
}

fun calculateAxQsCellConfig(
    gridWidth: Dp,
    columns: Int = 4,
    spacing: Dp = AxQsCellConfig.Defaults.TileSpacing,
    cellHeight: Dp? = AxQsCellConfig.Defaults.TileHeight,
    densityScale: Float =
        if (cellHeight != null && AxQsCellConfig.Defaults.TileHeight > 0.dp) {
            cellHeight / AxQsCellConfig.Defaults.TileHeight
        } else {
            1f
        },
    density: Float = 1f,
    fontScale: Float = 1f,
    densityDpi: Int = 0,
): AxQsCellConfig =
    AxQsCellConfig(
        gridWidth = gridWidth,
        columns = columns,
        spacing = spacing,
        cellHeight = cellHeight,
        densityScale = densityScale,
        density = density,
        fontScale = fontScale,
        densityDpi = densityDpi,
    )

@Composable
fun rememberAxQsCellConfig(
    gridWidth: Dp,
    columns: Int = 4,
    spacing: Dp? = null,
    cellHeight: Dp? = null,
    densityScale: Float = LocalTileScale.current,
): AxQsCellConfig {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val effectiveSpacing = spacing ?: (AxQsCellConfig.Defaults.TileSpacing * densityScale)
    val effectiveHeight = cellHeight ?: (AxQsCellConfig.Defaults.TileHeight * densityScale)
    return remember(
        gridWidth,
        columns,
        effectiveSpacing,
        effectiveHeight,
        densityScale,
        density.density,
        density.fontScale,
        configuration.densityDpi,
    ) {
        calculateAxQsCellConfig(
            gridWidth = gridWidth,
            columns = columns,
            spacing = effectiveSpacing,
            cellHeight = effectiveHeight,
            densityScale = densityScale,
            density = density.density,
            fontScale = density.fontScale,
            densityDpi = configuration.densityDpi,
        )
    }
}
