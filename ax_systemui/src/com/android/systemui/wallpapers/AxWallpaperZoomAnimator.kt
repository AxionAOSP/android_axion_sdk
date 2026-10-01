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

package com.android.systemui.wallpapers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.android.app.animation.Interpolators
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.LightRevealScrimInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.wallpapers.data.repository.WallpaperRepository
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@SysUISingleton
class AxWallpaperZoomAnimator
@Inject
constructor(
    private val wallpaperZoomController: AxWallpaperZoomController,
    private val wallpaperRepository: WallpaperRepository,
    private val lightRevealScrimInteractor: LightRevealScrimInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val powerInteractor: PowerInteractor,
    @param:Application private val scope: CoroutineScope,
    @param:Main private val mainDispatcher: CoroutineDispatcher,
) {
    private var animator: ValueAnimator? = null
    private var state = AnimationState()
    private var fullAodWakeJob: Job? = null
    private val wakeGeneration = AtomicInteger(0)

    val isHolding: Boolean
        get() = state.isHolding

    val isAnimating: Boolean
        get() = animator?.isRunning == true

    fun start() {
        scope.launch(context = mainDispatcher) {
            lightRevealScrimInteractor.revealAmount.collect { progress ->
                if (progress >= REVEAL_START_THRESHOLD) {
                    triggerRevealIfPrepped("lightReveal:$progress")
                }
            }
        }

        scope.launch(context = mainDispatcher) {
            powerInteractor.screenPowerState.collect { powerState ->
                when (powerState) {
                    ScreenPowerState.SCREEN_ON -> {
                        if (state.isPrepped && !state.hasRevealed) {
                            triggerRevealIfPrepped("screenPowerOnFallback")
                        }
                    }
                    ScreenPowerState.SCREEN_TURNING_OFF, ScreenPowerState.SCREEN_OFF -> {
                        fullAodWakeJob?.cancel()
                        fullAodWakeJob = null
                    }
                    else -> {}
                }
            }
        }

        scope.launch(context = mainDispatcher) {
            keyguardInteractor.isDozing.collect { isDozing ->
                onDozingChanged(isDozing)
            }
        }

        scope.launch(context = mainDispatcher) {
            keyguardTransitionInteractor.startedKeyguardTransitionStep.collect { step ->
                onTransitionStepStarted(step)
            }
        }

        scope.launch(context = mainDispatcher) {
            combine(
                wallpaperRepository.wallpaperSupportsAmbientMode.distinctUntilChanged(),
                wallpaperRepository.lockscreenWallpaperInfo,
                ::Pair,
            ).collect { (aod, lockInfo) ->
                state = state.copy(isAodWallpaper = aod)
                if (lockInfo != null) {
                    fullAodWakeJob?.cancel()
                    fullAodWakeJob = null
                    state = state.copy(isPrepped = false, hasRevealed = false)
                    clear()
                }
            }
        }
    }

    private fun onTransitionStepStarted(step: TransitionStep) {
        when {
            step.to == KeyguardState.GONE -> handleGoneTransition()
            KeyguardState.deviceIsAsleepInState(step.to) -> handleSleepTransition(step.to)
            step.to == KeyguardState.LOCKSCREEN -> handleLockscreenTransition(step.from)
            step.to in listOf(KeyguardState.PRIMARY_BOUNCER, KeyguardState.ALTERNATE_BOUNCER, KeyguardState.OCCLUDED) -> {
                handleBouncerOrOccluded(step.to)
            }
        }
    }

    private fun handleGoneTransition() {
        wakeGeneration.incrementAndGet()
        fullAodWakeJob?.cancel()
        fullAodWakeJob = null
        state = state.copy(isPrepped = false, hasRevealed = false)
        if (isHolding || isAnimating) {
            animateToResting()
        } else {
            clear()
        }
    }

    private fun handleSleepTransition(toState: KeyguardState) {
        wakeGeneration.incrementAndGet()
        fullAodWakeJob?.cancel()
        fullAodWakeJob = null
        val wasAod = state.isAodWallpaper
        state = state.copy(isPrepped = false, hasRevealed = false)
        if (wasAod && canUseWallpaper()) {
            animate(state.zoomOut, WAKE_START_ZOOM, TO_AOD_DURATION_MS, "toAodSleep:$toState")
        } else {
            clear()
        }
    }

    private fun handleLockscreenTransition(fromState: KeyguardState) {
        wakeGeneration.incrementAndGet()
        fullAodWakeJob?.cancel()
        fullAodWakeJob = null
        if (fromState == KeyguardState.GONE) {
            state = state.copy(isPrepped = false, hasRevealed = true)
            apply(LOCKSCREEN_RESTING_ZOOM, "lockscreenFromGone")
        } else {
            onStartedWaking(fromState)
        }
    }

    private fun handleBouncerOrOccluded(toState: KeyguardState) {
        if (state.isPrepped) {
            triggerRevealIfPrepped("bouncerOrOccluded:$toState")
        }
    }

    private fun onStartedWaking(fromState: KeyguardState) {
        if (!canUseWallpaper()) {
            clear()
            return
        }
        val gen = wakeGeneration.incrementAndGet()
        fullAodWakeJob?.cancel()
        stop()
        state = state.copy(isPrepped = true, hasRevealed = false)
        val currentZoom = wallpaperZoomController.currentEffectiveZoom
        val prepZoom = if (currentZoom < 1.0f) currentZoom else WAKE_START_ZOOM
        apply(prepZoom, "wakePrep:$fromState")

        if (state.isAodWallpaper) {
            fullAodWakeJob = scope.launch(mainDispatcher) {
                delay(FULL_AOD_SYNC_DELAY_MS)
                if (wakeGeneration.get() == gen && state.isPrepped) {
                    triggerRevealIfPrepped("fullAodSyncWake")
                }
            }
        }
    }

    private fun triggerRevealIfPrepped(reason: String) {
        if (!state.isPrepped || state.hasRevealed) return
        if (state.isAodWallpaper && !reason.startsWith("fullAodSyncWake")) {
            return
        }
        fullAodWakeJob?.cancel()
        fullAodWakeJob = null
        state = state.copy(isPrepped = false, hasRevealed = true)
        playReveal(reason)
    }

    fun onDozingChanged(isDozing: Boolean) {
        if (isDozing) {
            fullAodWakeJob?.cancel()
            fullAodWakeJob = null
            stop()
            state = state.copy(isPrepped = false, hasRevealed = false)
            apply(WAKE_START_ZOOM, "dozing")
        }
    }

    fun onUnlockProgress(progress: Float) {
        if (progress >= 1f) {
            animateToResting()
        }
    }

    fun playReveal(reason: String = "wakeReveal") {
        if (!canUseWallpaper()) {
            clear()
            return
        }
        val currentZoom = wallpaperZoomController.currentEffectiveZoom
        val current = (animator?.animatedValue as? Float) ?: if (currentZoom < 1.0f) currentZoom else state.zoomOut
        if (abs(current - LOCKSCREEN_RESTING_ZOOM) < 0.001f) {
            return
        }
        animate(current, LOCKSCREEN_RESTING_ZOOM, REVEAL_DURATION_MS, reason)
    }

    fun clear() {
        fullAodWakeJob?.cancel()
        fullAodWakeJob = null
        stop()
        apply(LOCKSCREEN_RESTING_ZOOM, "clear")
    }

    fun animateToResting(durationMs: Long = UNLOCK_TRANSITION_DURATION_MS) {
        val currentZoom = wallpaperZoomController.currentEffectiveZoom
        val current = (animator?.animatedValue as? Float) ?: if (currentZoom < 1.0f) currentZoom else state.zoomOut
        if (abs(current - LOCKSCREEN_RESTING_ZOOM) < 0.001f) {
            clear()
            return
        }
        animate(current, LOCKSCREEN_RESTING_ZOOM, durationMs, "unlockTransition")
    }

    private fun animate(from: Float, to: Float, durationMs: Long, reason: String) {
        val currentZoom = wallpaperZoomController.currentEffectiveZoom
        val startVal = (animator?.animatedValue as? Float) ?: if (currentZoom < 1.0f) currentZoom else from
        stop()
        if (abs(startVal - to) < 0.001f) {
            apply(to, reason)
            return
        }

        val distance = abs(to - startVal).coerceIn(0f, 1f)
        val scaledDuration = (durationMs * distance.coerceAtLeast(0.85f)).toLong().coerceAtLeast(500L)

        animator =
            ValueAnimator.ofFloat(startVal, to).apply {
                duration = scaledDuration
                interpolator = Interpolators.FAST_OUT_SLOW_IN
                addUpdateListener { apply(it.animatedValue as Float, "animating:$reason") }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (animator == animation) {
                                animator = null
                                apply(to, "${reason}End")
                            }
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            if (animator == animation) {
                                animator = null
                            }
                        }
                    }
                )
                start()
            }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    private fun apply(zoom: Float, reason: String) {
        val value = zoom.coerceIn(0f, 1f)
        state = state.copy(zoomOut = value)
        wallpaperZoomController.setZoom(WallpaperZoomOwner.KEYGUARD_WAKE_ANIM, value, reason)
    }

    private fun canUseWallpaper(): Boolean =
        !wallpaperZoomController.wallpaperZoomDisabled &&
            (wallpaperRepository.lockscreenWallpaperInfo.value == null || state.isAodWallpaper)

    private data class AnimationState(
        val zoomOut: Float = LOCKSCREEN_RESTING_ZOOM,
        val isAodWallpaper: Boolean = false,
        val isPrepped: Boolean = false,
        val hasRevealed: Boolean = false,
    ) {
        val isHolding: Boolean
            get() = zoomOut != LOCKSCREEN_RESTING_ZOOM
    }

    companion object {
        private const val FULL_AOD_SYNC_DELAY_MS = 240L
        private const val TO_AOD_DURATION_MS = 500L
        private const val REVEAL_START_THRESHOLD = 0.1f
        private const val REVEAL_DURATION_MS = 800L
        private const val UNLOCK_TRANSITION_DURATION_MS = 650L
        const val WAKE_START_ZOOM = 0f
        const val LOCKSCREEN_RESTING_ZOOM = 1f
    }
}
