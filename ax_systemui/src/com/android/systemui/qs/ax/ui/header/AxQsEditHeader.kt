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

package com.android.systemui.qs.ax.ui.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.internal.R as InternalR
import com.android.systemui.qs.ax.res.R
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.res.R as SysuiR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AxQsEditHeader(
    onDone: () -> Unit,
    onReset: () -> Unit,
    onOpenPanelSettings: () -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val tileBackgroundColor = LocalAxQsCellConfig.current.backgroundColor()
    val tileContentColor = MaterialTheme.colorScheme.onSurface
    val tileButtonColors =
        ButtonDefaults.filledTonalButtonColors(
            containerColor = tileBackgroundColor,
            contentColor = tileContentColor,
        )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLandscape) {
                Spacer(Modifier.weight(1f))
            } else {
                FilledTonalButton(
                    onClick = onOpenPanelSettings,
                    shapes = ButtonDefaults.shapes(),
                    colors = tileButtonColors,
                ) {
                    Text(stringResource(R.string.ax_qs_panel_settings))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onReset,
                    shapes = ButtonDefaults.shapes(),
                    colors = tileButtonColors,
                ) {
                    Text(stringResource(InternalR.string.reset))
                }
                Button(
                    onClick = onDone,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(stringResource(SysuiR.string.quick_settings_done))
                }
            }
        }
        Text(
            text = stringResource(R.string.ax_qs_reorder_education),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
    }
}
