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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.systemui.qs.ax.pressfeedback.axPressFeedback
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.ui.controls.axQsControlShape
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.res.R as SysuiR

@Composable
internal fun AxAddItemCell(
    item: AxAddItem,
    span: AxQsSpan,
    rowHeight: Dp,
    verticalSliderStyle: (AxQsControl) -> AxQsVerticalSliderStyle,
    onVerticalSliderStyleChanged: (AxQsControl, AxQsVerticalSliderStyle) -> Unit,
    canAdd: Boolean,
    modifier: Modifier = Modifier,
) {
    val clickLabel =
        stringResource(SysuiR.string.accessibility_qs_edit_named_tile_add_action, item.label)
    val addedDescription =
        if (item.isAdded) {
            stringResource(SysuiR.string.accessibility_qs_edit_tile_already_added)
        } else {
            null
        }
    val fullDescription =
        if (!item.isAdded && !canAdd) {
            stringResource(R.string.ax_qs_grid_full)
        } else {
            null
        }
    val cellConfig = LocalAxQsCellConfig.current
    val previewHeight = cellConfig.itemHeight(span)
    val isVerticalSlider = item.isSlider
    val sliderControl =
        if (isVerticalSlider) AxQsControl.entries.firstOrNull { it.id == item.id } else null
    val sliderStyle =
        sliderControl?.let(verticalSliderStyle) ?: AxQsVerticalSliderStyle.M3_EXPRESSIVE
    val previewShape =
        if (sliderControl != null) {
            axQsControlShape(sliderControl, span, sliderStyle)
        } else {
            when {
                span.columns == 1 && span.rows == 1 -> CircleShape
                span.columns > 1 && span.rows == 1 -> RoundedCornerShape(percent = 50)
                else -> RoundedCornerShape(AxQsTokens.CornerRadius.ControlCornerRadius)
            }
        }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(CommonTileDefaults.TileStartPadding, Alignment.Top),
        modifier =
            modifier
                .graphicsLayer { alpha = if (item.isAdded || !canAdd) .38f else 1f }
                .semantics(mergeDescendants = true) {
                    if (addedDescription != null) stateDescription = addedDescription
                    if (fullDescription != null) stateDescription = fullDescription
                },
    ) {
        if (sliderControl != null) {
            VerticalSliderStylePager(
                control = sliderControl,
                span = span,
                selectedStyle = sliderStyle,
                onStyleSelected = { style ->
                    onVerticalSliderStyleChanged(sliderControl, style)
                },
                canAdd = !item.isAdded && canAdd,
                canChangeStyle = !item.isAdded,
                clickLabel = clickLabel,
                onAdd = item.onAdd,
                previewHeight = previewHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(previewHeight)) {
                Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    item.preview(Modifier.fillMaxSize())
                }
                val previewInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .zIndex(1f)
                            .clip(previewShape)
                            .axPressFeedback(
                                previewInteractionSource,
                                enabled = !item.isAdded && canAdd,
                            )
                            .clickable(
                                interactionSource = previewInteractionSource,
                                indication = ripple(),
                                enabled = !item.isAdded && canAdd,
                                onClickLabel = clickLabel,
                                role = Role.Button,
                            ) {
                                item.onAdd()
                            }
                )
                AxAddItemBadge(Modifier.align(Alignment.TopEnd))
            }
        }
        Text(
            text = item.label,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun AxAddItemBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}

internal fun AxAddItem.pickerSpan(columns: Int): AxQsSpan {
    return span.copy(columns = span.columns.coerceAtMost(columns))
}

internal fun packAvailableItems(items: List<AxAddItem>, columns: Int): List<List<AxAddItem>> {
    val initial = listOf(emptyList<AxAddItem>())
    val packed =
        items.fold(initial) { acc, item ->
            val currentRow = acc.last()
            val usedColumns = currentRow.sumOf { it.pickerSpan(columns).columns }
            val itemSpan = item.pickerSpan(columns)
            val rowSpan = currentRow.firstOrNull()?.pickerSpan(columns)?.rows ?: itemSpan.rows
            if (currentRow.isNotEmpty() &&
                (usedColumns + itemSpan.columns > columns || rowSpan != itemSpan.rows)
            ) {
                acc + listOf(listOf(item))
            } else {
                acc.dropLast(1) + listOf(currentRow + item)
            }
        }
    return packed.filter { it.isNotEmpty() }
}
