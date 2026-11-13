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

package com.android.systemui.qs.ax.tiles.bluetooth

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.grid.AxTileColors
import com.android.systemui.qs.ax.ui.grid.LocalAxQsCellConfig
import com.android.systemui.qs.ax.ui.grid.interceptParentTouch
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.ui.compose.borderOnFocus
import kotlinx.coroutines.launch

internal const val BLUETOOTH_TILE_SPEC = "bt"

@Composable
internal fun AxBluetoothConnectedLargeContent(
    snapshot: AxBluetoothTileSnapshot,
    fallbackIconProvider: Context.() -> Icon,
    colors: AxTileColors,
    span: AxQsSpan,
    isClickable: Boolean,
    isDualTarget: Boolean,
    toggleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
) {
    val devices = snapshot.connectedDevices
    if (devices.isEmpty()) return

    val cellConfig = LocalAxQsCellConfig.current
    val focusBorderColor = MaterialTheme.colorScheme.secondary
    val longPressLabel = longPressLabelSettings().takeIf { onLongClick != null }

    val chipColor by
        animateColorAsState(
            targetValue = colors.chipBackground,
            label = "AxBtChipBg",
        )
    val chipIconColor by
        animateColorAsState(
            targetValue = colors.chipIcon,
            label = "AxBtChipIcon",
        )

    if (span.rows == 1) {
        val device = devices.first()
        val hasRender = device.productImage != null
        val pillChipBg =
            if (hasRender) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                chipColor
            }
        val clickableIconModifier =
            if (toggleClick != null && isClickable) {
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
                        .background(pillChipBg, CircleShape)
                        .thenIf(isDualTarget) {
                            Modifier.borderOnFocus(color = focusBorderColor, CircleShape.topEnd)
                        }
                        .then(clickableIconModifier),
                contentAlignment = Alignment.Center,
            ) {
                AxBluetoothDeviceVisual(
                    device = device,
                    fallbackIconProvider = fallbackIconProvider,
                    vectorTint = chipIconColor,
                    renderSize = (cellConfig.iconContainerSize * 0.80f).coerceAtLeast(24.dp),
                    vectorSize = cellConfig.iconSize,
                )
            }

            Spacer(Modifier.width(cellConfig.tileStartPadding))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    if (devices.size > 1 && device.secondaryLabel.isNotBlank()) {
                        "${device.secondaryLabel} (+${devices.size - 1})"
                    } else if (device.secondaryLabel.isNotBlank()) {
                        device.secondaryLabel
                    } else {
                        ""
                    }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth().basicMarquee(),
                    )
                }
            }
        }
        return
    }

    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { devices.size })
    LaunchedEffect(devices.size) {
        if (pagerState.currentPage >= devices.size && devices.isNotEmpty()) {
            pagerState.scrollToPage(devices.lastIndex)
        }
    }

    val pad = (cellConfig.tileEndPadding * 0.85f).coerceIn(10.dp, 16.dp)
    val toggleBadgeSize = (cellConfig.iconContainerSize * 0.78f).coerceIn(32.dp, 42.dp)
    val toggleIconSize = (cellConfig.iconSize * 0.82f).coerceIn(16.dp, 22.dp)

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .interceptParentTouch(enabled = isClickable && devices.size > 1, view = view)
                .padding(pad),
    ) {
        val cardHeight = maxHeight
        val cardWidth = maxWidth
        val renderHeight = (cardHeight * 0.46f).coerceIn(48.dp, 78.dp)
        val renderWidth = (cardWidth * 0.68f).coerceIn(64.dp, 112.dp)
        val fallbackHeroVectorSize = (cardHeight * 0.34f).coerceIn(36.dp, 54.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(toggleBadgeSize * 0.45f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                userScrollEnabled = isClickable && devices.size > 1,
                beyondViewportPageCount = 1,
                key = { page -> devices.getOrNull(page)?.address ?: page },
            ) { page ->
                val device = devices.getOrNull(page) ?: return@HorizontalPager
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(
                            targetState = device.productImage,
                            animationSpec = tween(durationMillis = 180),
                            label = "AxBt2x2ProductArt",
                        ) { productBitmap ->
                            if (productBitmap != null) {
                                Image(
                                    bitmap = productBitmap,
                                    contentDescription = device.name,
                                    contentScale = ContentScale.Fit,
                                    modifier =
                                        Modifier.size(width = renderWidth, height = renderHeight),
                                )
                            } else {
                                val iconProvider: Context.() -> Icon =
                                    device.categoryIcon?.let { cat -> { cat } }
                                        ?: fallbackIconProvider
                                SmallTileContent(
                                    iconProvider = iconProvider,
                                    color = colors.label.copy(alpha = 0.90f),
                                    size = { fallbackHeroVectorSize },
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (device.secondaryLabel.isNotBlank()) {
                            Text(
                                text = device.secondaryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.secondaryLabel.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().basicMarquee(),
                            )
                        }
                    }
                }
            }

            if (devices.size > 1) {
                AxHorizontalDotIndicator(
                    pageCount = devices.size,
                    currentPage = pagerState.currentPage,
                    activeColor = colors.label,
                    inactiveColor = colors.secondaryLabel.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 4.dp),
                    onDotClick = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .size(toggleBadgeSize)
                    .background(chipColor, CircleShape)
                    .thenIf(toggleClick != null && isClickable) {
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
                iconProvider = fallbackIconProvider,
                color = chipIconColor,
                size = { toggleIconSize },
            )
        }
    }
}

@Composable
private fun AxBluetoothDeviceVisual(
    device: AxBluetoothDeviceModel,
    fallbackIconProvider: Context.() -> Icon,
    vectorTint: Color,
    renderSize: Dp,
    vectorSize: Dp,
) {
    val bitmap = device.productImage
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = device.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(renderSize),
        )
    } else {
        val provider: Context.() -> Icon =
            device.categoryIcon?.let { cat -> { cat } } ?: fallbackIconProvider
        SmallTileContent(
            iconProvider = provider,
            color = vectorTint,
            size = { vectorSize },
        )
    }
}

@Composable
private fun AxHorizontalDotIndicator(
    pageCount: Int,
    currentPage: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    onDotClick: ((Int) -> Unit)? = null,
) {
    if (pageCount <= 1) return
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotColor by
                animateColorAsState(
                    targetValue = if (isActive) activeColor else inactiveColor,
                    label = "AxBtDotColor",
                )
            val dotSize by
                animateDpAsState(
                    targetValue = if (isActive) 5.dp else 4.dp,
                    label = "AxBtDotSize",
                )
            Box(
                modifier =
                    Modifier.size(dotSize)
                        .clip(CircleShape)
                        .background(dotColor)
                        .thenIf(onDotClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) {
                                onDotClick?.invoke(index)
                            }
                        }
            )
        }
    }
}
