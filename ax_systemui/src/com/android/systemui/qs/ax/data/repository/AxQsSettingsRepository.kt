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

package com.android.systemui.qs.ax.data.repository

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridLayout
import com.android.systemui.qs.ax.shared.model.AxQsGridPosition
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsLayoutData
import com.android.systemui.qs.ax.shared.model.AxQsPanelMode
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderKey
import com.android.systemui.qs.ax.shared.model.AxQsVerticalSliderStyle
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SysUISingleton
class AxQsSettingsRepository
@Inject
constructor(
    @param:Application private val context: Context,
    private val secureSettings: SecureSettings,
    private val userRepository: UserRepository,
    @param:Application private val applicationScope: CoroutineScope,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
) {
    val aospDefaultTiles: List<String> by lazy {
        try {
            QSHost.getDefaultSpecs(context.resources)
                .map { it.trim() }
                .filter { it.isNotBlank() && it !in DEFAULT_NETWORK_IDS && it != "bt" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val defaultSpans: Map<String, AxQsSpan> = parseSpans(DEFAULT_SPANS_STRING)
    val defaultGridItems: List<String>
        get() = (DEFAULT_GRID_ITEMS + aospDefaultTiles).distinct()

    private val _layoutsDataOverride = MutableStateFlow<Map<AxQsLayout, AxQsLayoutData>?>(null)
    val layoutsData: StateFlow<Map<AxQsLayout, AxQsLayoutData>> =
        stringSetting(QS_DATA)
            .map { str -> AxQsLayoutData.parseList(str).associateBy { it.location } }
            .onEach { _layoutsDataOverride.value = null }
            .combine(_layoutsDataOverride) { settingVal, overrideVal ->
                if (overrideVal != null) {
                    settingVal + overrideVal
                } else {
                    settingVal
                }
            }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())

    val panelMode: StateFlow<AxQsPanelMode> =
        intSetting(PANEL_MODE, AxQsPanelMode.TOGETHER.settingValue)
            .map(AxQsPanelMode::fromSetting)
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, AxQsPanelMode.TOGETHER)
    val quickPanelOnLeft: StateFlow<Boolean> = boolSetting(QUICK_PANEL_ON_LEFT, false)
    val qqsMaxRows: StateFlow<Int> =
        intSetting(QQS_MAX_ROWS_KEY, DEFAULT_QQS_MAX_ROWS)
            .map { it.coerceIn(MIN_QQS_MAX_ROWS, MAX_QQS_MAX_ROWS) }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, DEFAULT_QQS_MAX_ROWS)
    val verticalSliderStyles: StateFlow<Map<AxQsVerticalSliderKey, AxQsVerticalSliderStyle>> =
        verticalSliderStyleSettings()

    val gridColumns: StateFlow<Map<AxQsGridLayout, Int>> =
        gridSettings(AxQsGridLayout.entries, ::gridColumnsKey)

    fun setQqsMaxRows(rows: Int) {
        putInt(QQS_MAX_ROWS_KEY, rows.coerceIn(MIN_QQS_MAX_ROWS, MAX_QQS_MAX_ROWS))
    }

    fun layoutData(layout: AxQsLayout): StateFlow<AxQsLayoutData?> =
        layoutsData
            .map { it[layout] }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, layoutsData.value[layout])

    fun updateLayoutData(layout: AxQsLayout, update: (AxQsLayoutData) -> AxQsLayoutData) {
        val currentMap = (_layoutsDataOverride.value ?: layoutsData.value).toMutableMap()
        val current = currentMap[layout] ?: defaultLayoutData(layout)
        val updated = update(current)
        currentMap[layout] = updated
        _layoutsDataOverride.value = currentMap

        val userId = userRepository.getSelectedUserInfo().id
        applicationScope.launch(backgroundDispatcher) {
            val diskMap =
                AxQsLayoutData.parseList(secureSettings.getStringForUser(QS_DATA, userId))
                    .associateBy { it.location }
                    .toMutableMap()
            diskMap[layout] = updated
            secureSettings.putStringForUser(
                QS_DATA,
                AxQsLayoutData.toJson(diskMap.values),
                null,
                false,
                userId,
                true,
            )
        }
    }

    fun setLayoutData(layoutData: AxQsLayoutData) {
        updateLayoutData(layoutData.location) { layoutData }
    }

    fun setOrder(order: List<String>, layout: AxQsLayout) {
        updateLayoutData(layout) { it.copy(order = order.distinct()) }
    }

    fun setSpan(id: String, span: AxQsSpan, layout: AxQsLayout) {
        updateLayoutData(layout) { current -> current.copy(spans = current.spans + (id to span)) }
    }

    fun setPositions(positions: Map<String, AxQsGridPosition>, layout: AxQsLayout) {
        updateLayoutData(layout) { it.copy(positions = positions) }
    }

    fun setPanelMode(mode: AxQsPanelMode) {
        putInt(PANEL_MODE, mode.settingValue)
    }

    fun setQuickPanelOnLeft(onLeft: Boolean) {
        putInt(QUICK_PANEL_ON_LEFT, onLeft.toSetting())
    }

    fun setVerticalSliderStyle(
        layout: AxQsLayout,
        control: AxQsControl,
        style: AxQsVerticalSliderStyle,
    ) {
        putInt(verticalSliderStyleKey(AxQsVerticalSliderKey(layout, control)), style.settingValue)
    }

    fun setColumns(layout: AxQsGridLayout, columns: Int) {
        putInt(gridColumnsKey(layout), columns)
    }

    fun defaultPositions(layout: AxQsLayout): Map<String, AxQsGridPosition> {
        return mapOf(
            "internet" to AxQsGridPosition(column = 0, row = 0),
            AxQsControl.VOLUME.id to AxQsGridPosition(column = 2, row = 0),
            AxQsControl.BRIGHTNESS.id to AxQsGridPosition(column = 3, row = 0),
            "bt" to AxQsGridPosition(column = 0, row = 1),
            AxQsControl.MEDIA.id to AxQsGridPosition(column = 0, row = 2),
        )
    }

    fun defaultLayoutData(
        layout: AxQsLayout,
        defaultTiles: List<String> = aospDefaultTiles,
    ): AxQsLayoutData =
        AxQsLayoutData(
            location = layout,
            order = (DEFAULT_GRID_ITEMS + defaultTiles).distinct(),
            spans = defaultSpans,
            positions = defaultPositions(layout),
        )

    fun resetLayout(
        defaultItems: List<String> = DEFAULT_GRID_ITEMS,
        defaultTiles: List<String> = aospDefaultTiles,
    ) {
        val userId = userRepository.getSelectedUserInfo().id
        val allDefaults =
            AxQsLayout.entries.map { layout -> defaultLayoutData(layout, defaultTiles) }
        val defaultsMap = allDefaults.associateBy { it.location }
        _layoutsDataOverride.value = defaultsMap
        val json = AxQsLayoutData.toJson(allDefaults)
        setQqsMaxRows(DEFAULT_QQS_MAX_ROWS)
        applicationScope.launch(backgroundDispatcher) {
            secureSettings.putStringForUser(QS_DATA, json, userId)
        }
    }

    fun init() {
        applicationScope.launch(backgroundDispatcher) {
            val userId = userRepository.getSelectedUserInfo().id
            secureSettings.getStringForUser(QS_DATA, userId)
        }
    }

    private fun stringSetting(key: String, default: String? = null): Flow<String?> {
        return userRepository.selectedUserInfo
            .flatMapLatest { user ->
                secureSettings
                    .observerFlow(user.id, key)
                    .onStart { emit(Unit) }
                    .map { secureSettings.getStringForUser(key, user.id) ?: default }
            }
            .flowOn(backgroundDispatcher)
    }

    private fun intSetting(key: String, default: Int): Flow<Int> {
        return userRepository.selectedUserInfo
            .flatMapLatest { user ->
                secureSettings
                    .observerFlow(user.id, key)
                    .onStart { emit(Unit) }
                    .map { secureSettings.getIntForUser(key, default, user.id) }
            }
            .flowOn(backgroundDispatcher)
    }

    private fun boolSetting(key: String, default: Boolean): StateFlow<Boolean> =
        intSetting(key, default.toSetting())
            .map { it != 0 }
            .distinctUntilChanged()
            .stateIn(applicationScope, SharingStarted.Eagerly, default)

    private fun gridSettings(
        layouts: Iterable<AxQsGridLayout>,
        keyForLayout: (AxQsGridLayout) -> String,
    ): StateFlow<Map<AxQsGridLayout, Int>> {
        return userRepository.selectedUserInfo
            .flatMapLatest { user ->
                combine(
                    layouts.map { layout ->
                        val key = keyForLayout(layout)
                        secureSettings
                            .observerFlow(user.id, key)
                            .onStart { emit(Unit) }
                            .map { layout to secureSettings.getIntForUser(key, 0, user.id) }
                    }
                ) { values ->
                    values
                        .mapNotNull { (layout, value) ->
                            value.takeIf { it > 0 }?.let { layout to it }
                        }
                        .toMap()
                }
            }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())
    }

    private fun verticalSliderStyleSettings():
        StateFlow<Map<AxQsVerticalSliderKey, AxQsVerticalSliderStyle>> {
        return userRepository.selectedUserInfo
            .flatMapLatest { user ->
                combine(
                    VERTICAL_SLIDER_KEYS.map { slider ->
                        val key = verticalSliderStyleKey(slider)
                        secureSettings
                            .observerFlow(user.id, key)
                            .onStart { emit(Unit) }
                            .map {
                                slider to
                                    AxQsVerticalSliderStyle.fromSetting(
                                        secureSettings.getIntForUser(
                                            key,
                                            AxQsVerticalSliderStyle.M3_EXPRESSIVE.settingValue,
                                            user.id,
                                        )
                                    )
                            }
                    }
                ) { values ->
                    values.toMap()
                }
            }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(applicationScope, SharingStarted.Eagerly, emptyMap())
    }

    private fun putString(key: String, values: List<String>) {
        putString(key, values.filter(String::isNotBlank).joinToString(","))
    }

    private fun putString(
        key: String,
        value: String,
        userId: Int = userRepository.getSelectedUserInfo().id,
    ) {
        applicationScope.launch(backgroundDispatcher) {
            secureSettings.putStringForUser(key, value, userId)
        }
    }

    private fun putInt(key: String, value: Int) {
        val userId = userRepository.getSelectedUserInfo().id
        applicationScope.launch(backgroundDispatcher) {
            secureSettings.putIntForUser(key, value, userId)
        }
    }

    private fun parseOrder(value: String?): List<String>? {
        return value?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.distinct()
    }

    private fun parseSpans(value: String?): Map<String, AxQsSpan> {
        if (value.isNullOrBlank()) return emptyMap()
        return parseKeyValuePairs(value, AxQsSpan::parse)
    }

    private fun parsePositions(value: String?): Map<String, AxQsGridPosition> {
        if (value.isNullOrBlank()) return emptyMap()
        return parseKeyValuePairs(value, AxQsGridPosition::parse)
    }

    private inline fun <V> parseKeyValuePairs(
        value: String,
        parseValue: (String) -> V?,
    ): Map<String, V> {
        return buildMap {
            value.split(',').forEach { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0 || separator == entry.lastIndex) return@forEach
                val id = entry.substring(0, separator).trim()
                val parsed = parseValue(entry.substring(separator + 1).trim())
                if (id.isNotEmpty() && parsed != null) put(id, parsed)
            }
        }
    }

    private fun Boolean.toSetting(): Int = if (this) 1 else 0

    private fun gridColumnsKey(layout: AxQsGridLayout): String = GRID_COLUMNS_KEYS.getValue(layout)

    private fun verticalSliderStyleKey(slider: AxQsVerticalSliderKey): String {
        val prefix =
            when (slider.layout) {
                AxQsLayout.QQS -> "ax_qqs"
                AxQsLayout.QS -> "ax_qs"
                AxQsLayout.SPLIT_SHADE -> "ax_qs_split_shade"
            }
        return when (slider.control) {
            AxQsControl.BRIGHTNESS -> "${prefix}_brightness_vertical_slider_style"
            AxQsControl.VOLUME -> "${prefix}_volume_vertical_slider_style"
            else -> error("Only vertical slider controls have style settings")
        }
    }

    private companion object {
        const val QS_DATA = "ax_qs_data"

        const val PANEL_MODE = "ax_qs_panel_mode"
        const val QUICK_PANEL_ON_LEFT = "ax_qs_quick_panel_on_left"
        const val QQS_COLUMNS = "ax_qqs_columns"
        const val QS_COLUMNS = "ax_qs_columns"
        const val SPLIT_SHADE_COLUMNS = "ax_qs_split_shade_columns"
        const val QQS_MAX_ROWS_KEY = "ax_qqs_max_rows"
        const val DEFAULT_QQS_MAX_ROWS = 2
        const val MIN_QQS_MAX_ROWS = 2
        const val MAX_QQS_MAX_ROWS = 4

        val VERTICAL_SLIDER_KEYS =
            AxQsLayout.entries.flatMap { layout ->
                listOf(AxQsControl.BRIGHTNESS, AxQsControl.VOLUME).map { control ->
                    AxQsVerticalSliderKey(layout, control)
                }
            }

        val GRID_COLUMNS_KEYS =
            mapOf(
                AxQsGridLayout.QQS to QQS_COLUMNS,
                AxQsGridLayout.QS to QS_COLUMNS,
                AxQsGridLayout.SPLIT_SHADE to SPLIT_SHADE_COLUMNS,
            )

        val DEFAULT_NETWORK_IDS = listOf("wifi", "internet")

        val DEFAULT_GRID_ITEMS =
            listOf(
                "internet",
                AxQsControl.VOLUME.id,
                AxQsControl.BRIGHTNESS.id,
                "bt",
                AxQsControl.MEDIA.id,
            )
        const val DEFAULT_SPANS_STRING =
            "bt=2x1,control:brightness=1x2,control:media=2x2," +
                "control:ringer=2x1,control:volume=1x2,internet=2x1"
    }
}
