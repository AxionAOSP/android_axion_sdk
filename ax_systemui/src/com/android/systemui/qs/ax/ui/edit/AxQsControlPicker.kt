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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.LocalPreferencePosition
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.preferenceShape
import com.android.systemui.common.ui.compose.load
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.ax.ui.controls.AxQsControlPreview
import com.android.systemui.qs.ax.ui.controls.axQsControlShape
import com.android.systemui.qs.ax.ui.grid.AxEditStackPlaceholderPreview
import com.android.systemui.qs.ax.ui.grid.AxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.grid.calculateAxQsCellConfig
import com.android.systemui.qs.ax.ui.panels.AxQsPagerIndicator
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.shared.model.CategoryAndName
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.shared.model.groupAndSort
import com.android.systemui.res.R as SysuiR
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class AxAddItem(
    val id: String,
    val label: String,
    override val category: TileCategory = TileCategory.UTILITIES,
    val isAdded: Boolean,
    val span: AxQsSpan = AxQsSpan.TileDefault,
    val isSlider: Boolean = false,
    val preview: @Composable (Modifier) -> Unit,
    val onAdd: () -> Unit,
) : CategoryAndName {
    override val name: String
        get() = label
}

@Composable
internal fun AxAvailableControls(
    allTiles: List<EditTileViewModel>,
    currentIds: Set<String>,
    columns: Int,
    verticalSliderStyle: (AxQsControl) -> AxQsVerticalSliderStyle,
    onVerticalSliderStyleChanged: (AxQsControl, AxQsVerticalSliderStyle) -> Unit,
    canAdd: (AxQsSpan) -> Boolean,
    onAddTile: (EditTileViewModel) -> Unit,
    onAddControl: (AxQsControl, AxQsSpan) -> Unit,
    onAddStack: () -> Unit = {},
    settings: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val brightnessLabel = stringResource(R.string.ax_qs_brightness_vertical)
    val volumeLabel = stringResource(R.string.ax_qs_volume_vertical)
    val ringerLabel = stringResource(SysuiR.string.volume_ringer_mode)
    val autoBrightnessLabel = stringResource(R.string.ax_qs_auto_brightness)
    val volumeMuteLabel = stringResource(R.string.ax_qs_volume_mute)
    val mediaLabel = stringResource(R.string.ax_qs_media)
    val stackLabel = stringResource(R.string.ax_qs_stacked_tile)

    val widgets: List<AxAddItem> =
        remember(
            columns,
            currentIds,
            verticalSliderStyle,
            brightnessLabel,
            volumeLabel,
            ringerLabel,
            autoBrightnessLabel,
            volumeMuteLabel,
            mediaLabel,
            stackLabel,
        ) {
            val controlItems =
                listOf(
                    AxQsControl.BRIGHTNESS to brightnessLabel,
                    AxQsControl.VOLUME to volumeLabel,
                    AxQsControl.RINGER to ringerLabel,
                    AxQsControl.AUTO_BRIGHTNESS to autoBrightnessLabel,
                    AxQsControl.VOLUME_MUTE to volumeMuteLabel,
                    AxQsControl.MEDIA to mediaLabel,
                )
                .map { (control, label) ->
                    val spans = control.spans(columns)
                    val sliderStyle = verticalSliderStyle(control)
                    AxAddItem(
                        id = control.id,
                        label = label,
                        category = TileCategory.UTILITIES,
                        isAdded = control.id in currentIds,
                        span = spans.default,
                        isSlider = control.isSlider,
                        preview = { previewModifier ->
                            AxQsControlPreview(
                                control = control,
                                span = spans.default,
                                verticalSliderStyle = sliderStyle,
                                modifier = previewModifier,
                            )
                        },
                        onAdd = {
                            onAddControl(control, spans.default)
                        },
                    )
                }
            val stackItem =
                AxAddItem(
                    id = "control_tile_stack",
                    label = stackLabel,
                    category = TileCategory.UTILITIES,
                    isAdded = false,
                    span = AxQsSpan(columns = 2, rows = 2),
                    isSlider = false,
                    preview = { previewModifier ->
                        AxEditStackPlaceholderPreview(modifier = previewModifier)
                    },
                    onAdd = onAddStack,
                )
            controlItems + stackItem
        }
    val tiles: List<AxAddItem> =
        remember(allTiles, currentIds) {
            allTiles.map { tile ->
                AxAddItem(
                    id = tile.tileSpec.spec,
                    label = tile.label.text,
                    category = tile.category,
                    isAdded = tile.tileSpec.spec in currentIds,
                    span = AxQsSpan.TileDefault,
                    isSlider = false,
                    preview = { previewModifier ->
                        AxQsEditTile(
                            tile = tile,
                            span = AxQsSpan.TileDefault,
                            modifier = previewModifier,
                        )
                    },
                    onAdd = {
                        onAddTile(tile)
                    },
                )
            }
        }
    val tileGroups = remember(tiles) { groupAndSort(tiles).entries.toList() }
    val spacing = AxQsCellConfig.Defaults.TileSpacing * LocalTileScale.current
    PreferenceGroup(modifier = modifier.fillMaxWidth(), spacing = 2.dp) {
        settings?.let { settingsContent ->
            item { AxPickerSettingsCard(settingsContent) }
        }
        item {
            AxAvailableWidgetsGroup(
                title = stringResource(R.string.ax_qs_widgets),
                icon = Icons.Default.Widgets,
                widgets = widgets,
                columns = columns,
                verticalSliderStyle = verticalSliderStyle,
                onVerticalSliderStyleChanged = onVerticalSliderStyleChanged,
                spacing = spacing,
                canAdd = canAdd,
            )
        }
        tileGroups.forEach { (category, items) ->
            item {
                AxAvailableItemGroup(
                    title = category.label.load().orEmpty(),
                    iconId = category.iconId,
                    items = items,
                    columns = columns,
                    verticalSliderStyle = verticalSliderStyle,
                    onVerticalSliderStyleChanged = onVerticalSliderStyleChanged,
                    spacing = spacing,
                    canAdd = canAdd,
                )
            }
        }
    }
}

@Composable
private fun AxPickerSettingsCard(settings: @Composable () -> Unit) {
    AxEditContainerCard(
        contentPadding = 16.dp,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvailableItemHeader(
            title = stringResource(SysuiR.string.qs_edit_settings),
            iconId = SysuiR.drawable.ic_settings,
        )
        settings()
    }
}

@Composable
private fun AxAvailableItemGroup(
    title: String,
    iconId: Int,
    items: List<AxAddItem>,
    columns: Int,
    verticalSliderStyle: (AxQsControl) -> AxQsVerticalSliderStyle,
    onVerticalSliderStyleChanged: (AxQsControl, AxQsVerticalSliderStyle) -> Unit,
    spacing: Dp,
    canAdd: (AxQsSpan) -> Boolean,
) {
    AxEditContainerCard(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvailableItemHeader(
            title = title,
            iconId = iconId,
            modifier =
                Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                ),
        )
        val rows = packAvailableItems(items, columns)
        val centerRows = items.firstOrNull()?.category == TileCategory.UTILITIES
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            val cellConfig =
                calculateAxQsCellConfig(
                    gridWidth = maxWidth,
                    columns = 4,
                    spacing = spacing,
                    cellHeight = AxQsCellConfig.Defaults.TileHeight * LocalTileScale.current,
                )
            val cellWidth =
                ((maxWidth - spacing * (columns - 1).coerceAtLeast(0)) / columns.coerceAtLeast(1))
                    .coerceAtLeast(0.dp)
            val itemRowHeight = cellConfig.rowHeight
            CompositionLocalProvider(LocalAxQsCellConfig provides cellConfig) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    spacing,
                                    if (centerRows) {
                                        Alignment.CenterHorizontally
                                    } else {
                                        Alignment.Start
                                    },
                                ),
                        ) {
                            row.forEach { item ->
                                val span = item.pickerSpan(columns)
                                AxAddItemCell(
                                    item = item,
                                    span = span,
                                    rowHeight = itemRowHeight,
                                    verticalSliderStyle = verticalSliderStyle,
                                    onVerticalSliderStyleChanged = onVerticalSliderStyleChanged,
                                    canAdd = canAdd(span),
                                    modifier =
                                        Modifier.width(
                                            cellWidth * span.columns + spacing * (span.columns - 1)
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AxEditContainerCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = .32f),
                    preferenceShape(LocalPreferencePosition.current),
                )
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
internal fun AvailableItemHeader(title: String, iconId: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AvailableItemHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun VerticalSliderStylePager(
    control: AxQsControl,
    span: AxQsSpan,
    selectedStyle: AxQsVerticalSliderStyle,
    onStyleSelected: (AxQsVerticalSliderStyle) -> Unit,
    canAdd: Boolean,
    canChangeStyle: Boolean,
    clickLabel: String,
    onAdd: () -> Unit,
    previewHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val styles = AxQsVerticalSliderStyle.entries
    val pagerState = rememberPagerState(initialPage = selectedStyle.ordinal) { styles.size }
    val currentStyle = rememberUpdatedState(selectedStyle)
    val currentCanChangeStyle = rememberUpdatedState(canChangeStyle)
    LaunchedEffect(selectedStyle) {
        if (!pagerState.isScrollInProgress && pagerState.settledPage != selectedStyle.ordinal) {
            pagerState.scrollToPage(selectedStyle.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .map(styles::get)
            .distinctUntilChanged()
            .collect { style ->
                if (currentCanChangeStyle.value && style != currentStyle.value) {
                    onStyleSelected(style)
                }
            }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CommonTileDefaults.TileStartPadding),
    ) {
        Box(Modifier.fillMaxWidth().height(previewHeight)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = styles::get,
                overscrollEffect = null,
                userScrollEnabled = canChangeStyle,
            ) { page ->
                val style = styles[page]
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                        AxQsControlPreview(
                            control = control,
                            span = span,
                            verticalSliderStyle = style,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .clip(axQsControlShape(control, span, style))
                            .clickable(
                                enabled = canAdd,
                                onClickLabel = clickLabel,
                                role = Role.Button,
                                onClick = onAdd,
                            )
                    )
                }
            }
            AxAddItemBadge(Modifier.align(Alignment.TopEnd))
        }
        AxQsPagerIndicator(pagerState)
    }
}
