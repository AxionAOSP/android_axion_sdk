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

package com.android.systemui.qs.ax.ui.header

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

private const val DEFAULT_SQUISH_ALPHA_START = 0.83f

fun Modifier.squishScale(
    squishiness: Float,
    alphaStart: Float = DEFAULT_SQUISH_ALPHA_START,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    return squishScale({ squishiness }, alphaStart, transformOrigin)
}

fun Modifier.squishScale(
    squishiness: () -> Float,
    alphaStart: Float = DEFAULT_SQUISH_ALPHA_START,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    return graphicsLayer {
        applySquishScale(squishiness(), alphaStart, transformOrigin)
    }
}

fun Modifier.squishLayoutScale(
    squishiness: () -> Float,
    alphaStart: Float = DEFAULT_SQUISH_ALPHA_START,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
): Modifier {
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val scale = squishiness()
        val scaledHeight = (placeable.height * scale).roundToInt()
        val y = (scaledHeight - placeable.height) / 2

        layout(placeable.width, scaledHeight) {
            placeable.placeWithLayer(0, y) {
                applySquishScale(scale, alphaStart, transformOrigin)
            }
        }
    }
}

private fun squishAlpha(
    squishiness: Float,
    alphaStart: Float = DEFAULT_SQUISH_ALPHA_START,
): Float {
    return if (squishiness < alphaStart) {
        0f
    } else {
        ((squishiness - alphaStart) / (1f - alphaStart)).coerceIn(0f, 1f)
    }
}

private fun GraphicsLayerScope.applySquishScale(
    squishiness: Float,
    alphaStart: Float,
    transformOrigin: TransformOrigin,
) {
    scaleX = squishiness
    scaleY = squishiness
    this.transformOrigin = transformOrigin
    alpha = squishAlpha(squishiness, alphaStart)
}
