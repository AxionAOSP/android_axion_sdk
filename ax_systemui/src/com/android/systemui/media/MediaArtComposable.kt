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
package com.android.systemui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.systemui.qs.ax.ui.media.AxMediaArtProcessor

private val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
private val ParallaxMotionEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

private const val ZOOM_OUT_START_SCALE = 1.30f
private const val PARALLAX_ZOOM_DURATION_MS = 800
private const val CROSSFADE_DURATION_MS = 450
private const val ENTER_FADE_DURATION_MS = 300
private const val EXIT_FADE_DURATION_MS = 300

private fun Modifier.applyBlur(blurRadiusDp: Int): Modifier {
    if (blurRadiusDp > 0) return blur(radius = blurRadiusDp.dp)
    return this
}

@Composable
fun MediaArt(
    state: MediaArtUiState,
    modifier: Modifier = Modifier
) {
    if (!state.isEnabled || state.artworkDrawable == null) return

    val imageBitmap = remember(state.artworkDrawable, state.metadataKey) {
        AxMediaArtProcessor.getLockscreenArt(state.artworkDrawable, state.metadataKey)
    } ?: return

    val aodAlpha by animateFloatAsState(
        targetValue = state.targetAlpha,
        animationSpec = tween(durationMillis = 300),
        label = "media_art_aod_alpha"
    )

    val colorFilter = remember(state.isDozing) {
        if (state.isDozing) ColorFilter.colorMatrix(grayscaleMatrix) else null
    }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(ENTER_FADE_DURATION_MS)) + scaleIn(
            initialScale = ZOOM_OUT_START_SCALE,
            animationSpec = tween(PARALLAX_ZOOM_DURATION_MS, easing = ParallaxMotionEasing)
        ),
        exit = fadeOut(tween(EXIT_FADE_DURATION_MS)),
        modifier = modifier.fillMaxSize().alpha(aodAlpha)
    ) {
        AnimatedContent(
            targetState = state.metadataKey to imageBitmap,
            transitionSpec = {
                (fadeIn(tween(CROSSFADE_DURATION_MS)) + scaleIn(
                    initialScale = ZOOM_OUT_START_SCALE,
                    animationSpec = tween(PARALLAX_ZOOM_DURATION_MS, easing = ParallaxMotionEasing)
                )) togetherWith fadeOut(tween(CROSSFADE_DURATION_MS))
            },
            label = "media_art_content",
            modifier = Modifier.fillMaxSize()
        ) { (_, bitmap) ->
            if (state.artStyle == MEDIA_ART_STYLE_CONCEPT) {
                ConceptModeArt(
                    imageBitmap = bitmap,
                    blurLevel = state.blurLevel,
                    colorFilter = colorFilter,
                    overlayAlpha = state.overlayAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                NormalModeArt(
                    imageBitmap = bitmap,
                    blurLevel = state.blurLevel,
                    colorFilter = colorFilter,
                    overlayAlpha = state.overlayAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NormalModeArt(
    imageBitmap: ImageBitmap,
    blurLevel: Int,
    colorFilter: ColorFilter?,
    overlayAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .applyBlur(blurLevel)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayAlpha)))
    }
}

@Composable
private fun ConceptCard(
    imageBitmap: ImageBitmap,
    maxWidth: Dp
) {
    val cardSize = (maxWidth * 0.78f).coerceAtMost(380.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(modifier = Modifier.height(136.dp))
    }
}

@Composable
private fun ConceptModeArt(
    imageBitmap: ImageBitmap,
    blurLevel: Int,
    colorFilter: ColorFilter?,
    overlayAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .applyBlur(blurLevel)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayAlpha)))
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ConceptCard(imageBitmap, maxWidth)
        }
    }
}
