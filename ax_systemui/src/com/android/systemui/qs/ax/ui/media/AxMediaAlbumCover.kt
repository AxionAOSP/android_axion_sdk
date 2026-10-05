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

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.Icon as IconModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel

private const val SCRIM_START_ALPHA = 0.65f
private const val SCRIM_END_ALPHA = 0.75f

internal fun createArtworkRadialScrim(color: Color, center: Offset, radius: Float): Brush =
    Brush.radialGradient(
        0f to color.copy(alpha = SCRIM_START_ALPHA),
        1f to color.copy(alpha = SCRIM_END_ALPHA),
        center = center,
        radius = radius
    )

private fun DrawScope.drawCenterCroppedImage(
    image: ImageBitmap,
    dstSize: IntSize,
    alpha: Float = 1f
) {
    val srcW = image.width.toFloat()
    val srcH = image.height.toFloat()
    val dstW = dstSize.width.toFloat()
    val dstH = dstSize.height.toFloat()

    if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) return

    val scale = maxOf(dstW / srcW, dstH / srcH)
    val cropW = dstW / scale
    val cropH = dstH / scale

    val srcX = ((srcW - cropW) / 2f).toInt().coerceAtLeast(0)
    val srcY = ((srcH - cropH) / 2f).toInt().coerceAtLeast(0)
    val cropWidthInt = cropW.toInt().coerceAtMost(image.width - srcX)
    val cropHeightInt = cropH.toInt().coerceAtMost(image.height - srcY)

    drawImage(
        image = image,
        srcOffset = IntOffset(srcX, srcY),
        srcSize = IntSize(cropWidthInt, cropHeightInt),
        dstOffset = IntOffset.Zero,
        dstSize = dstSize,
        alpha = alpha
    )
}

@Composable
internal fun MediaArtwork(
    artwork: IconModel?,
    songKey: String,
    overlayColor: Color,
    modifier: Modifier = Modifier
) {
    val previousBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val currentBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionProgress = remember { Animatable(1f) }
    val displayedSongKey = remember { mutableStateOf<String?>(null) }

    val drawable = (artwork as? IconModel.Loaded)?.drawable
    val newBitmap = remember(songKey, drawable) {
        AxMediaArtProcessor.getCardBackground(drawable, songKey)
    }

    LaunchedEffect(newBitmap, songKey) {
        if (newBitmap != null) {
            if (currentBitmapState.value !== newBitmap) {
                if (currentBitmapState.value != null && displayedSongKey.value != null &&
                    songKey != displayedSongKey.value
                ) {
                    previousBitmapState.value = currentBitmapState.value
                    currentBitmapState.value = newBitmap
                    transitionProgress.snapTo(0f)
                    transitionProgress.animateTo(
                        1f,
                        tween(AxMediaTokens.ArtworkCrossfadeDurationMs, easing = LinearEasing)
                    )
                    previousBitmapState.value = null
                } else {
                    currentBitmapState.value = newBitmap
                    transitionProgress.snapTo(1f)
                }
                displayedSongKey.value = songKey
            }
        } else if (artwork == null || artwork is IconModel.Resource) {
            currentBitmapState.value = null
            previousBitmapState.value = null
            transitionProgress.snapTo(1f)
            displayedSongKey.value = songKey
        }
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val targetSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
                val gradientBrush = Brush.verticalGradient(
                    listOf(
                        overlayColor.copy(alpha = 0.5f),
                        overlayColor.copy(alpha = 0.85f)
                    )
                )
                onDrawBehind {
                    val progress = transitionProgress.value
                    val prev = previousBitmapState.value
                    val curr = currentBitmapState.value

                    if (prev != null && progress < 1f) {
                        drawCenterCroppedImage(prev, targetSize, 1f - progress)
                    }
                    if (curr != null && progress > 0f) {
                        drawCenterCroppedImage(curr, targetSize, progress)
                    }
                    drawRect(brush = gradientBrush)
                }
            }
    )
}

@Composable
fun AxMediaAlbumCover(
    artwork: IconModel?,
    songKey: String,
    session: MediaSessionModel?,
    size: Dp = 56.dp,
    cornerRadius: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val previousBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val currentBitmapState = remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionProgress = remember { Animatable(1f) }
    val displayedSongKey = remember { mutableStateOf<String?>(null) }

    val effectiveArtwork = artwork ?: session?.background
    val drawable = (effectiveArtwork as? IconModel.Loaded)?.drawable
    val rawBitmap = remember(songKey, drawable) {
        AxMediaArtProcessor.getThumbnail(drawable, songKey)
    }

    LaunchedEffect(rawBitmap, songKey) {
        if (rawBitmap != null) {
            if (currentBitmapState.value !== rawBitmap) {
                if (currentBitmapState.value != null && displayedSongKey.value != null &&
                    songKey != displayedSongKey.value
                ) {
                    previousBitmapState.value = currentBitmapState.value
                    currentBitmapState.value = rawBitmap
                    transitionProgress.snapTo(0f)
                    transitionProgress.animateTo(
                        1f,
                        tween(AxMediaTokens.ArtworkCrossfadeDurationMs, easing = LinearEasing)
                    )
                    previousBitmapState.value = null
                } else {
                    currentBitmapState.value = rawBitmap
                    transitionProgress.snapTo(1f)
                }
                displayedSongKey.value = songKey
            }
        } else if (effectiveArtwork == null || effectiveArtwork is IconModel.Resource) {
            currentBitmapState.value = null
            previousBitmapState.value = null
            transitionProgress.snapTo(1f)
            displayedSongKey.value = songKey
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        val prevBitmap = previousBitmapState.value
        val currBitmap = currentBitmapState.value

        if (currBitmap != null || prevBitmap != null) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val targetSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
                        onDrawBehind {
                            val progress = transitionProgress.value
                            val prev = previousBitmapState.value
                            val curr = currentBitmapState.value

                            if (prev != null && progress < 1f) {
                                drawCenterCroppedImage(prev, targetSize, 1f - progress)
                            }
                            if (curr != null && progress > 0f) {
                                drawCenterCroppedImage(curr, targetSize, progress)
                            }
                        }
                    }
            )
        } else if (effectiveArtwork is IconModel.Resource) {
            Image(
                painter = painterResource(id = effectiveArtwork.resId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            MediaAppIcon(
                session = session,
                size = (size * 0.45f).coerceAtLeast(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
