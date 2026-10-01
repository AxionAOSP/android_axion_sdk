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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.qs.ax.res.R

@Composable
internal fun AxMediaTrackInfo(
    session: MediaSessionModel?,
    colors: AxMediaColors,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall
) {
    val title =
        session?.title?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.ax_qs_media_not_playing)
    val subtitle = session?.subtitle.orEmpty()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        AnimatedMediaText(
            text = title,
            style = titleStyle,
            color = colors.foreground
        )
        if (subtitle.isNotEmpty()) {
            AnimatedMediaText(
                text = subtitle,
                style = subtitleStyle,
                color = colors.foreground.copy(alpha = AxMediaTokens.SubtitleAlpha)
            )
        }
    }
}

@Composable
internal fun AnimatedMediaText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(160)) togetherWith fadeOut(tween(100)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "AxMediaText",
        modifier = modifier
    ) { value ->
        Text(
            text = value,
            color = color,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE)
        )
    }
}
