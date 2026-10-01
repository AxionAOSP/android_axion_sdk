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

package com.android.systemui.qs.ax.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.common.ui.domain.interactor.ConfigurationInteractor
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.ax.data.repository.AxQsSettingsRepository
import com.android.systemui.qs.ax.domain.interactor.AxQsLayoutInteractor
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridColumns
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridLayout
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsLayoutData
import com.android.systemui.qs.ax.shared.model.AxQsPanelMode
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderKey
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.qs.panels.data.repository.QSColumnsRepository
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class AxQsViewModel
@Inject
constructor(
    private val repository: AxQsSettingsRepository,
    val layoutInteractor: AxQsLayoutInteractor,
    configurationInteractor: ConfigurationInteractor,
    shadeInteractor: ShadeInteractor,
    columnsRepository: QSColumnsRepository,
    @param:ShadeDisplayAware private val context: Context,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("AxQsViewModel")
    private val defaultTileIds = QSHost.getDefaultSpecs(context.resources)
    private val normalDefaultColumns by
        hydrator.hydratedStateOf(
            traceName = "normalDefaultColumns",
            initialValue = columnsRepository.defaultColumns,
            source = columnsRepository.columns,
        )
    private val splitShadeDefaultColumns by
        hydrator.hydratedStateOf(
            traceName = "splitShadeDefaultColumns",
            initialValue =
                context.resources.getInteger(R.integer.quick_settings_split_shade_num_columns),
            source = columnsRepository.splitShadeColumns,
        )
    private val screenWidthDp by
        hydrator.hydratedStateOf(
            traceName = "screenWidthDp",
            initialValue = context.resources.configuration.screenWidthDp,
            source =
                configurationInteractor.configurationValues.map { configuration ->
                    configuration.screenWidthDp
                },
        )
    val defaultTileSpecs: List<String>
        get() = defaultTileIds

    private val layoutsData by
        hydrator.hydratedStateOf(
            traceName = "layoutsData",
            initialValue = emptyMap(),
            source = repository.layoutsData,
        )
    val panelMode by
        hydrator.hydratedStateOf(
            traceName = "panelMode",
            initialValue = AxQsPanelMode.TOGETHER,
            source = repository.panelMode,
        )
    val isQsBypassingShade by
        hydrator.hydratedStateOf(
            traceName = "isQsBypassingShade",
            initialValue = false,
            source = shadeInteractor.isQsBypassingShade,
        )
    var holdQsSceneDuringCollapse by mutableStateOf(false)
        private set

    val quickPanelOnLeft by
        hydrator.hydratedStateOf(
            traceName = "quickPanelOnLeft",
            initialValue = false,
            source = repository.quickPanelOnLeft,
        )
    private val verticalSliderStyles by
        hydrator.hydratedStateOf(
            traceName = "verticalSliderStyles",
            initialValue = emptyMap(),
            source = repository.verticalSliderStyles,
        )
    private val gridColumns by
        hydrator.hydratedStateOf(
            traceName = "gridColumns",
            initialValue = emptyMap(),
            source = repository.gridColumns,
        )
    val qqsMaxRows by
        hydrator.hydratedStateOf(
            traceName = "qqsMaxRows",
            initialValue = 2,
            source = repository.qqsMaxRows,
        )
    val qqsMaxRowsFlow: StateFlow<Int> = repository.qqsMaxRows
    val qqsMaxRowsRange: IntRange = 2..4

    fun setQqsMaxRows(rows: Int) {
        repository.setQqsMaxRows(rows)
    }

    fun columns(layout: AxQsGridLayout): Int {
        val default = defaultColumns(layout)
        return (gridColumns[layout] ?: default).coerceIn(columnRange(layout))
    }

    fun columns(layout: AxQsLayout): Int = columns(AxQsGridLayout.from(layout))

    fun columnRange(layout: AxQsGridLayout): IntRange = layout.columnRange(screenWidthDp)

    fun columnRange(layout: AxQsLayout): IntRange =
        AxQsGridColumns.columnRange(screenWidthDp, layout == AxQsLayout.SPLIT_SHADE)

    fun layoutData(layout: AxQsLayout): AxQsLayoutData? = layoutsData[layout]

    fun order(layout: AxQsLayout): List<String>? =
        layoutData(layout)?.order?.takeIf { it.isNotEmpty() }

    fun spans(layout: AxQsLayout): Map<String, AxQsSpan> =
        layoutData(layout)?.spans ?: repository.defaultSpans

    fun positions(layout: AxQsLayout): Map<String, AxQsGridPosition> =
        layoutData(layout)?.positions ?: emptyMap()

    fun stacks(layout: AxQsLayout): Map<String, List<String>> =
        layoutData(layout)?.stacks ?: emptyMap()

    fun orderedIds(
        layout: AxQsLayout,
        availableIds: List<String>,
        defaultIds: List<String>,
    ): List<String> {
        val available = availableIds.toSet()
        val saved = order(layout)
        if (saved != null) return saved.filter(available::contains)
        val defaultItems = defaultControlIds(available)
        return (defaultItems + defaultIds).distinct().filter(available::contains)
    }

    fun span(id: String, layout: AxQsLayout, default: AxQsSpan): AxQsSpan {
        val resolvedDefault =
            if (
                default == AxQsSpan.TileDefault &&
                    (id in DEFAULT_NETWORK_IDS || id == BLUETOOTH_TILE_ID)
            ) {
                AxQsSpan.TileWideDefault
            } else {
                default
            }
        return spans(layout)[id] ?: resolvedDefault
    }

    fun isInGrid(id: String, layout: AxQsLayout = AxQsLayout.QS): Boolean {
        val currentOrder = order(layout)
        if (currentOrder != null) return id in currentOrder
        return id in repository.defaultGridItems
    }

    fun <T> filterQqsItems(items: List<AxQsGridItem<T>>, columns: Int): List<AxQsGridItem<T>> =
        layoutInteractor.filterQqsItems(items, columns, qqsMaxRows)

    fun wouldStraddleQqs(
        span: AxQsSpan,
        position: AxQsGridPosition?,
        maxRows: Int = qqsMaxRows,
    ): Boolean =
        layoutInteractor.wouldStraddleQqs(span, position, maxRows)

    fun setLayoutData(layoutData: AxQsLayoutData) {
        repository.setLayoutData(layoutData)
    }

    fun setOrder(order: List<String>, layout: AxQsLayout) {
        repository.setOrder(order, layout)
    }

    fun setPositions(positions: Map<String, AxQsGridPosition>, layout: AxQsLayout) {
        repository.setPositions(positions, layout)
    }

    fun setSpan(id: String, span: AxQsSpan, layout: AxQsLayout, columns: Int) {
        val control = AxQsControl.entries.firstOrNull { it.id == id }
        repository.setSpan(
            id,
            control?.coerceSpan(span, columns) ?: span.coerceTileSpan(columns),
            layout,
        )
    }

    fun setPanelMode(mode: AxQsPanelMode) = repository.setPanelMode(mode)

    fun setForceQsEvent(forceQsEvent: Boolean) {
        holdQsSceneDuringCollapse = forceQsEvent
    }

    fun clearCollapseGuard() {
        holdQsSceneDuringCollapse = false
    }

    fun setQuickPanelOnLeft(onLeft: Boolean) = repository.setQuickPanelOnLeft(onLeft)

    fun verticalSliderStyle(layout: AxQsLayout, control: AxQsControl): AxQsVerticalSliderStyle =
        verticalSliderStyles[AxQsVerticalSliderKey(layout, control)]
            ?: AxQsVerticalSliderStyle.M3_EXPRESSIVE

    fun setVerticalSliderStyle(
        layout: AxQsLayout,
        control: AxQsControl,
        style: AxQsVerticalSliderStyle,
    ) = repository.setVerticalSliderStyle(layout, control, style)

    fun setColumns(layout: AxQsGridLayout, columns: Int) {
        val clamped = columns.coerceIn(columnRange(layout))
        repository.setColumns(layout, clamped)
    }

    fun setColumns(layout: AxQsLayout, columns: Int) {
        setColumns(AxQsGridLayout.from(layout), columns)
    }

    fun defaultLayoutData(layout: AxQsLayout): AxQsLayoutData = repository.defaultLayoutData(layout)

    fun resetLayout() {
        repository.resetLayout()
    }

    override suspend fun onActivated(): Nothing = hydrator.activate()

    fun defaultColumns(layout: AxQsGridLayout): Int =
        if (layout.layout == AxQsLayout.SPLIT_SHADE) {
            splitShadeDefaultColumns
        } else {
            normalDefaultColumns
        }

    fun defaultColumns(layout: AxQsLayout): Int = defaultColumns(AxQsGridLayout.from(layout))

    private fun defaultControlIds(available: Set<String>): List<String> {
        val network = DEFAULT_NETWORK_IDS.firstOrNull(available::contains)
        return (listOfNotNull(
                network,
                AxQsControl.VOLUME.id,
                AxQsControl.BRIGHTNESS.id,
                BLUETOOTH_TILE_ID,
                AxQsControl.MEDIA.id,
            ) + repository.aospDefaultTiles)
            .filter(available::contains)
            .distinct()
    }

    private companion object {
        const val MIN_TILE_WIDTH_DP = 56f
        const val GRID_SPACING_DP = 16f
        val DEFAULT_NETWORK_IDS = listOf("wifi", "internet")
        const val BLUETOOTH_TILE_ID = "bt"
    }
}
