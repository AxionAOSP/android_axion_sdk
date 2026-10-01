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

package com.android.systemui.qs.ax.ui.media

import android.R as AndroidR
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.res.R as SysuiR

@Composable
internal fun MediaTimestamps(
    session: MediaSessionModel?,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    modifier: Modifier = Modifier,
) {
    val progress = session?.let(viewModel::progress) ?: 0f
    val totalMs = session?.durationMs ?: 0L
    val elapsedMs = (progress * totalMs).toLong()

    val elapsedSeconds = elapsedMs / 1000L
    val totalSeconds = totalMs.coerceAtLeast(0L) / 1000L
    val elapsedStr = remember(elapsedSeconds) { DateUtils.formatElapsedTime(elapsedSeconds) }
    val totalStr = remember(totalSeconds) { DateUtils.formatElapsedTime(totalSeconds) }

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = elapsedStr,
            color = colors.foreground.copy(alpha = AxMediaTokens.KeyguardArtworkTintAlpha),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
        Text(
            text = totalStr,
            color = colors.foreground.copy(alpha = AxMediaTokens.KeyguardArtworkTintAlpha),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        )
    }
}

@Composable
internal fun MediaGuts(
    session: MediaSessionModel,
    viewModel: AxMediaViewModel,
    colors: AxMediaColors,
    compact: Boolean,
    surface: AxMediaSurface,
) {
    val message =
        if (session.canBeHidden) {
            stringResource(SysuiR.string.controls_media_close_session, session.appName)
        } else {
            stringResource(SysuiR.string.controls_media_active_session)
        }
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        IconButton(onClick = viewModel::openSettings, modifier = Modifier.align(Alignment.TopEnd)) {
            MaterialIcon(
                painter = painterResource(SysuiR.drawable.ic_settings),
                contentDescription = stringResource(SysuiR.string.controls_media_settings_button),
                tint = colors.foreground,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        ) {
            Text(
                text = message,
                color = colors.foreground,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(end = 48.dp),
            )
            val actionButtons: @Composable () -> Unit = {
                if (session.canBeHidden) {
                    Button(onClick = { viewModel.dismissFromSurface(session, surface) }) {
                        Text(stringResource(SysuiR.string.controls_media_dismiss_button))
                    }
                }
                OutlinedButton(onClick = viewModel::cancelGuts) {
                    Text(stringResource(AndroidR.string.cancel))
                }
            }
            if (compact) {
                actionButtons()
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = { actionButtons() },
                )
            }
        }
    }
}
