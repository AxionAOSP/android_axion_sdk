/*
 * Copyright (C) 2026 AxionOS
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

package com.android.systemui.wallpapers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.android.app.animation.Interpolators
import kotlin.math.abs

class WallpaperZoomHandoffEngine(
    private val onUpdate: () -> Unit,
) {
    var handoffOffset: Float = 0f
        private set

    val isAnimating: Boolean
        get() = handoffAnimator?.isRunning == true

    private var handoffAnimator: ValueAnimator? = null

    fun onOwnerChanged(
        previousOwner: WallpaperZoomOwner?,
        newOwner: WallpaperZoomOwner?,
        currentEffectiveZoom: Float,
        newTargetZoom: Float,
    ) {
        val previousIsLauncher = previousOwner == WallpaperZoomOwner.APP_ZOOM || previousOwner == WallpaperZoomOwner.BASE_DEPTH
        val newIsLauncher = newOwner == WallpaperZoomOwner.APP_ZOOM || newOwner == WallpaperZoomOwner.BASE_DEPTH
        if (previousIsLauncher && newIsLauncher) {
            cancel()
            return
        }

        val jump = currentEffectiveZoom - newTargetZoom
        if (abs(jump) < ZOOM_EPSILON) {
            cancel()
            return
        }

        handoffAnimator?.cancel()
        handoffAnimator = ValueAnimator.ofFloat(jump, 0f).apply {
            duration = HANDOFF_DURATION_MS
            interpolator = Interpolators.DECELERATE_QUINT
            addUpdateListener { anim ->
                handoffOffset = anim.animatedValue as Float
                onUpdate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (handoffAnimator == animation) {
                        handoffOffset = 0f
                        handoffAnimator = null
                        onUpdate()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (handoffAnimator == animation) {
                        handoffOffset = 0f
                        handoffAnimator = null
                    }
                }
            })
            start()
        }
    }

    fun blend(rawTarget: Float): Float =
        (rawTarget + handoffOffset).coerceIn(0f, 1f)

    fun cancel() {
        handoffAnimator?.cancel()
        handoffAnimator = null
        handoffOffset = 0f
    }

    companion object {
        const val ZOOM_EPSILON = 0.001f
        const val HANDOFF_DURATION_MS = 280L
    }
}
