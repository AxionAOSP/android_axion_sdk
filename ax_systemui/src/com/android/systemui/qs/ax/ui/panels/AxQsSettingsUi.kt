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

package com.android.systemui.qs.ax.ui.panels

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.axion.compose.preferences.SliderPreference
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.shared.model.AxQsGridLayout
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsPanelMode
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.header.AxQuickSettingsLayoutDefaults
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import com.android.systemui.qs.ui.composable.QuickSettingsTheme
import com.android.systemui.res.R as SysuiR
import kotlin.math.roundToInt

@Composable
internal fun AxQsPanelSettings(
    viewModel: AxQsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    QuickSettingsTheme {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            BoxWithConstraints(modifier.fillMaxSize()) {
                val sidePadding =
                    if (landscape) {
                        AxQuickSettingsLayoutDefaults.LandscapeSidePadding
                    } else {
                        AxQuickSettingsLayoutDefaults.PortraitSidePadding
                    }
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(horizontal = sidePadding.coerceAtLeast(0.dp))
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(
                                    SysuiR.string.accessibility_back
                                ),
                            )
                        }
                        Text(
                            text = stringResource(R.string.ax_qs_panel_settings),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (!landscape) {
                        Text(
                            text = stringResource(R.string.ax_qs_panel_mode),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        PanelModeRow(
                            selected = viewModel.panelMode == AxQsPanelMode.TOGETHER,
                            title = stringResource(R.string.ax_qs_together),
                            summary = stringResource(R.string.ax_qs_together_summary),
                        ) {
                            viewModel.setPanelMode(AxQsPanelMode.TOGETHER)
                        }
                        PanelModeRow(
                            selected = viewModel.panelMode == AxQsPanelMode.SEPARATE,
                            title = stringResource(R.string.ax_qs_separate),
                            summary =
                                stringResource(
                                    if (viewModel.quickPanelOnLeft) {
                                        R.string.ax_qs_separate_summary_left
                                    } else {
                                        R.string.ax_qs_separate_summary_right
                                    }
                                ),
                        ) {
                            viewModel.setPanelMode(AxQsPanelMode.SEPARATE)
                        }
                        if (viewModel.panelMode == AxQsPanelMode.SEPARATE) {
                            SettingSwitchRow(
                                label = stringResource(R.string.ax_qs_quick_panel_left),
                                checked = viewModel.quickPanelOnLeft,
                                onCheckedChange = viewModel::setQuickPanelOnLeft,
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelModeRow(selected: Boolean, title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    LocalAxQsCellConfig.current.backgroundColor(),
                    RoundedCornerShape(24.dp)
                )
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (selected) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun AxQsGridSettings(
    layout: AxQsGridLayout,
    viewModel: AxQsViewModel,
) {
    val isSplitShade = layout == AxQsGridLayout.SPLIT_SHADE
    val colorScheme = MaterialTheme.colorScheme.copy(surfaceBright = Color.Transparent)
    MaterialTheme(colorScheme = colorScheme) {
        PreferenceGroup {
            item {
                GridColumnSlider(
                    layout = layout,
                    viewModel = viewModel,
                )
            }
            if (viewModel.panelMode != AxQsPanelMode.SEPARATE && !isSplitShade) {
                item {
                    QqsMaxRowsSlider(
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AxQsGridSettings(
    layout: AxQsLayout,
    viewModel: AxQsViewModel,
) {
    AxQsGridSettings(layout = AxQsGridLayout.from(layout), viewModel = viewModel)
}

@Composable
private fun QqsMaxRowsSlider(
    viewModel: AxQsViewModel,
) {
    val label = stringResource(R.string.ax_qs_qqs_max_rows)
    GridSizeSlider(
        label = label,
        savedValue = viewModel.qqsMaxRows,
        range = viewModel.qqsMaxRowsRange,
        valueLabel = R.string.ax_qs_row_count,
        onValueChangeFinished = { viewModel.setQqsMaxRows(it) },
    )
}

@Composable
private fun GridColumnSlider(
    layout: AxQsGridLayout,
    viewModel: AxQsViewModel,
) {
    val label = stringResource(R.string.ax_qs_grid_columns)
    GridSizeSlider(
        label = label,
        savedValue = viewModel.columns(layout),
        range = viewModel.columnRange(layout),
        valueLabel = R.string.ax_qs_column_count,
        onValueChangeFinished = { viewModel.setColumns(layout, it) },
    )
}

@Composable
private fun GridSizeSlider(
    label: String,
    savedValue: Int,
    range: IntRange,
    @StringRes valueLabel: Int,
    onValueChangeFinished: (Int) -> Unit,
) {
    val currentSavedValue = savedValue.coerceIn(range.first, range.last)
    val sliderValue = remember { mutableFloatStateOf(currentSavedValue.toFloat()) }
    LaunchedEffect(currentSavedValue) {
        sliderValue.floatValue = currentSavedValue.toFloat()
    }
    val selectedValue = sliderValue.floatValue.roundToInt().coerceIn(range.first, range.last)
    SliderPreference(
        title = label,
        summary = "",
        value = sliderValue.floatValue,
        onValueChange = {
            sliderValue.floatValue = it.roundToInt().coerceIn(range.first, range.last).toFloat()
        },
        onValueChangeFinished = {
            val finalValue = sliderValue.floatValue.roundToInt().coerceIn(range.first, range.last)
            onValueChangeFinished(finalValue)
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.count() - 2).coerceAtLeast(0),
        displayValue = stringResource(valueLabel, selectedValue),
    )
}
