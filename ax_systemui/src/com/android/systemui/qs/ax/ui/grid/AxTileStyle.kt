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

package com.android.systemui.qs.ax.ui.grid

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.ui.compose.borderOnFocus

val LocalTileScale = staticCompositionLocalOf { 1f }

@Composable
fun computeTileScale(): Float {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val displayMetrics = Resources.getSystem().displayMetrics
    val physicalWidthPx = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
    val qsRenderDensity = physicalWidthPx / 420f
    return if (configuration.smallestScreenWidthDp >= 600) {
        1.0f
    } else if (density > 0f) {
        (qsRenderDensity / density).coerceIn(0.85f, 1.15f)
    } else {
        1.0f
    }
}

@Composable
fun AxLargeTileContent(
    label: String,
    secondaryLabel: String?,
    iconProvider: Context.() -> Icon,
    sideDrawable: Drawable?,
    colors: AxTileColors,
    squishiness: () -> Float,
    tileState: Int,
    span: AxQsSpan,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    accessibilityUiState: AccessibilityUiState? = null,
    iconShape: RoundedCornerShape = RoundedCornerShape(CommonTileDefaults.InactiveCornerRadius),
    textScale: () -> Float = { 1f },
    toggleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isDualTarget: Boolean = toggleClick != null,
    interactionSource: MutableInteractionSource? = null,
    showDivider: Boolean = true,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val scale = cellConfig.densityScale
    val focusBorderColor = MaterialTheme.colorScheme.secondary
    val longPressLabel = longPressLabelSettings().takeIf { onLongClick != null }

    val chipColor by
        animateColorAsState(
            targetValue = colors.chipBackground,
            label = "AxLargeTileChipColor",
        )

    val chipIconColor by
        animateColorAsState(
            targetValue = colors.chipIcon,
            label = "AxLargeTileChipIconColor",
        )

    if (span.rows == 1) {
        val clickableIconModifier =
            if (toggleClick != null) {
                Modifier.combinedClickable(
                    onClick = toggleClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = longPressLabel,
                    hapticFeedbackEnabled = !Flags.msdlFeedback(),
                    interactionSource = interactionSource,
                )
            } else {
                Modifier
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                modifier.padding(
                    start = cellConfig.tileStartPadding.coerceAtLeast(0.dp),
                    end = cellConfig.tileEndPadding.coerceAtLeast(0.dp),
                ),
        ) {
            Box(
                modifier =
                    Modifier.size(cellConfig.iconContainerSize)
                        .background(chipColor, CircleShape)
                        .thenIf(isDualTarget) {
                            Modifier.borderOnFocus(color = focusBorderColor, CircleShape.topEnd)
                        }
                        .then(clickableIconModifier),
                contentAlignment = Alignment.Center,
            ) {
                SmallTileContent(
                    iconProvider = iconProvider,
                    color = chipIconColor,
                    size = { cellConfig.iconSize },
                )
            }

            Spacer(Modifier.width(cellConfig.tileStartPadding))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!secondaryLabel.isNullOrBlank()) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(cellConfig.tileEndPadding.coerceAtLeast(0.dp)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            AxLargeTileIcon(
                iconProvider = iconProvider,
                colors = colors,
                scale = scale,
                toggleClick = toggleClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                tileState = tileState,
                modifier = Modifier.size(cellConfig.iconContainerSize),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!secondaryLabel.isNullOrBlank()) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AxLargeTileIcon(
    iconProvider: Context.() -> Icon,
    colors: AxTileColors,
    scale: Float,
    toggleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource? = null,
    tileState: Int = STATE_INACTIVE,
    modifier: Modifier = Modifier,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val longPressLabel = longPressLabelSettings().takeIf { onLongClick != null }
    val focusBorderColor = MaterialTheme.colorScheme.secondary
    val chipColor by
        animateColorAsState(
            targetValue = colors.chipBackground,
            label = "AxLargeTileIconBackground",
        )
    val chipIconColor by
        animateColorAsState(
            targetValue = colors.chipIcon,
            label = "AxLargeTileIconTint",
        )
    Box(
        modifier =
            modifier.background(chipColor, CircleShape).thenIf(toggleClick != null) {
                Modifier.borderOnFocus(color = focusBorderColor, CircleShape.topEnd)
                    .combinedClickable(
                        onClick = toggleClick!!,
                        onLongClick = onLongClick,
                        onLongClickLabel = longPressLabel,
                        hapticFeedbackEnabled = !Flags.msdlFeedback(),
                        interactionSource = interactionSource,
                    )
            },
        contentAlignment = Alignment.Center,
    ) {
        SmallTileContent(
            iconProvider = iconProvider,
            color = chipIconColor,
            size = { cellConfig.iconSize },
        )
    }
}
