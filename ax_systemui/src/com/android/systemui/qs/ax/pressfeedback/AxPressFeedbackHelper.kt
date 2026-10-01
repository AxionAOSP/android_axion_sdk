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

package com.android.systemui.qs.ax.pressfeedback

import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import javax.inject.Inject

@SysUISingleton
class AxPressFeedbackHelper
@Inject
constructor() {
    private val interpolator = PathInterpolator(0.0f, 0.0f, 0.1f, 1.0f)

    fun computeScaleRatio(widthPx: Float, heightPx: Float, density: Float): Float {
        val minPx = MIN_END_VALUE_DP * density
        val maxWPx = MAX_END_WIDTH_DP * density
        val maxHPx = MAX_END_HEIGHT_DP * density
        val minArea = minPx * minPx
        val maxArea = maxWPx * maxHPx
        val area = widthPx * heightPx

        if (area <= minArea) return MIN_SCALE_RATIO
        if (area >= maxArea) return MAX_SCALE_RATIO
        val norm = (area - minArea) / (maxArea - minArea)
        return MIN_SCALE_RATIO +
            (MAX_SCALE_RATIO - MIN_SCALE_RATIO) * interpolator.getInterpolation(norm)
    }

    companion object {
        private const val MIN_END_VALUE_DP = 42.0f
        private const val MAX_END_WIDTH_DP = 328.0f
        private const val MAX_END_HEIGHT_DP = 220.0f

        const val MIN_SCALE_RATIO = AxQsTokens.Animation.AX_PRESS_MIN_SCALE
        const val MAX_SCALE_RATIO = AxQsTokens.Animation.AX_PRESS_MAX_SCALE

        val PressSpringSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = AxQsTokens.Animation.AX_PRESS_DAMPING,
                stiffness = AxQsTokens.Animation.AX_PRESS_STIFFNESS,
            )

        val ReleaseSpringSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = AxQsTokens.Animation.AX_RELEASE_DAMPING,
                stiffness = AxQsTokens.Animation.AX_RELEASE_STIFFNESS,
            )

        val QuickPressSpringSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = AxQsTokens.Animation.AX_QUICK_PRESS_STIFFNESS,
            )

        val CancelSpringSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            )

        val SpringSpec: AnimationSpec<Float> = ReleaseSpringSpec
    }
}

val LocalAxPressFeedbackHelper = staticCompositionLocalOf { AxPressFeedbackHelper() }

fun Modifier.axPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier =
    composed {
        if (!enabled) return@composed Modifier
        val helper = LocalAxPressFeedbackHelper.current
        val density = LocalDensity.current.density
        val scale = remember { Animatable(1f) }
        val sizeBuffer = remember { FloatArray(2) { 0f } }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        val targetScale =
                            helper.computeScaleRatio(sizeBuffer[0], sizeBuffer[1], density)
                        scale.animateTo(targetScale, AxPressFeedbackHelper.PressSpringSpec)
                    }
                    is PressInteraction.Release -> {
                        val targetScale =
                            helper.computeScaleRatio(sizeBuffer[0], sizeBuffer[1], density)
                        if (scale.value > targetScale + 0.015f) {
                            scale.animateTo(
                                targetScale,
                                AxPressFeedbackHelper.QuickPressSpringSpec,
                            )
                        }
                        scale.animateTo(1f, AxPressFeedbackHelper.ReleaseSpringSpec)
                    }
                    is PressInteraction.Cancel -> {
                        scale.animateTo(1f, AxPressFeedbackHelper.CancelSpringSpec)
                    }
                }
            }
        }

        this.onSizeChanged { size ->
            sizeBuffer[0] = size.width.toFloat()
            sizeBuffer[1] = size.height.toFloat()
        }.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    }
