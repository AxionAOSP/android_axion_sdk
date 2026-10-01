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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.ui.grid.AxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.grid.calculateAxQsCellConfig

@Composable
internal fun AxAvailableWidgetsGroup(
    title: String,
    icon: ImageVector,
    widgets: List<AxAddItem>,
    columns: Int,
    verticalSliderStyle: (AxQsControl) -> AxQsVerticalSliderStyle,
    onVerticalSliderStyleChanged: (AxQsControl, AxQsVerticalSliderStyle) -> Unit,
    spacing: Dp,
    canAdd: (AxQsSpan) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val widgetMap = remember(widgets) { widgets.associateBy { it.id } }
    val brightness = widgetMap[AxQsControl.BRIGHTNESS.id]
    val volume = widgetMap[AxQsControl.VOLUME.id]
    val ringer = widgetMap[AxQsControl.RINGER.id]
    val autoBrightness = widgetMap[AxQsControl.AUTO_BRIGHTNESS.id]
    val volumeMute = widgetMap[AxQsControl.VOLUME_MUTE.id]
    val media = widgetMap[AxQsControl.MEDIA.id]
    val tileStack = widgetMap["control_tile_stack"]

    AxEditContainerCard(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        AvailableItemHeader(
            title = title,
            icon = icon,
            modifier =
                Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                ),
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            val cellConfig =
                calculateAxQsCellConfig(
                    gridWidth = maxWidth,
                    columns = columns,
                    spacing = spacing,
                    cellHeight = AxQsCellConfig.Defaults.TileHeight * LocalTileScale.current,
                )
            val itemRowHeight = cellConfig.rowHeight
            CompositionLocalProvider(LocalAxQsCellConfig provides cellConfig) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            brightness?.let { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    AxAddItemCell(
                                        item = item,
                                        span = item.span,
                                        rowHeight = itemRowHeight,
                                        verticalSliderStyle = verticalSliderStyle,
                                        onVerticalSliderStyleChanged =
                                            onVerticalSliderStyleChanged,
                                        canAdd = canAdd(item.span),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            volume?.let { item ->
                                Box(modifier = Modifier.weight(1f)) {
                                    AxAddItemCell(
                                        item = item,
                                        span = item.span,
                                        rowHeight = itemRowHeight,
                                        verticalSliderStyle = verticalSliderStyle,
                                        onVerticalSliderStyleChanged =
                                            onVerticalSliderStyleChanged,
                                        canAdd = canAdd(item.span),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            ringer?.let { item ->
                                AxAddItemCell(
                                    item = item,
                                    span = item.span,
                                    rowHeight = itemRowHeight,
                                    verticalSliderStyle = verticalSliderStyle,
                                    onVerticalSliderStyleChanged =
                                        onVerticalSliderStyleChanged,
                                    canAdd = canAdd(item.span),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                            ) {
                                autoBrightness?.let { item ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        AxAddItemCell(
                                            item = item,
                                            span = item.span,
                                            rowHeight = itemRowHeight,
                                            verticalSliderStyle = verticalSliderStyle,
                                            onVerticalSliderStyleChanged =
                                                onVerticalSliderStyleChanged,
                                            canAdd = canAdd(item.span),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                volumeMute?.let { item ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        AxAddItemCell(
                                            item = item,
                                            span = item.span,
                                            rowHeight = itemRowHeight,
                                            verticalSliderStyle = verticalSliderStyle,
                                            onVerticalSliderStyleChanged =
                                                onVerticalSliderStyleChanged,
                                            canAdd = canAdd(item.span),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (media != null || tileStack != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing),
                        ) {
                            val span2x2 = AxQsSpan(columns = minOf(2, columns), rows = 2)
                            if (media != null) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AxAddItemCell(
                                        item = media,
                                        span = span2x2,
                                        rowHeight = itemRowHeight,
                                        verticalSliderStyle = verticalSliderStyle,
                                        onVerticalSliderStyleChanged = onVerticalSliderStyleChanged,
                                        canAdd = canAdd(span2x2),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            if (tileStack != null) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AxAddItemCell(
                                        item = tileStack,
                                        span = span2x2,
                                        rowHeight = itemRowHeight,
                                        verticalSliderStyle = verticalSliderStyle,
                                        onVerticalSliderStyleChanged = onVerticalSliderStyleChanged,
                                        canAdd = canAdd(span2x2),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
