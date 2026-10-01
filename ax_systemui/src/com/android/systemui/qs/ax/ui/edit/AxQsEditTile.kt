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

import android.service.quicksettings.Tile.STATE_INACTIVE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.grid.AxTile
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState

private val DUAL_TARGET_SPECS = setOf("internet", "bt", "wifi", "cell")

@Composable
fun AxQsEditTile(
    tile: EditTileViewModel,
    span: AxQsSpan,
    modifier: Modifier = Modifier,
) {
    val isDualTarget = tile.tileSpec.spec in DUAL_TARGET_SPECS
    val labelText = tile.label.text
    val secondaryText = tile.appName?.text.orEmpty()
    val uiState =
        remember(tile.tileSpec, labelText, secondaryText, isDualTarget) {
            TileUiState(
                label = labelText,
                secondaryLabel = secondaryText,
                state = STATE_INACTIVE,
                handlesLongClick = true,
                handlesSecondaryClick = isDualTarget,
                sideDrawable = null,
                accessibilityUiState =
                    AccessibilityUiState(
                        contentDescription = labelText,
                        stateDescription = "",
                        accessibilityRole = Role.Button,
                    ),
            )
        }

    AxTile(
        uiState = uiState,
        iconProvider = { tile.icon },
        compact = span.columns == 1,
        span = span,
        isClickable = false,
        isDualTarget = isDualTarget,
        secondaryClick = if (isDualTarget) ({}) else null,
        modifier = modifier,
    )
}
