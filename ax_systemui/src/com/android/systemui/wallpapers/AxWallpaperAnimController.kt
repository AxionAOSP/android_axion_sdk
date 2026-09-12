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

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.LightRevealScrimInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.util.WallpaperController
import com.android.systemui.wallpapers.data.repository.WallpaperRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@SysUISingleton
class AxWallpaperAnimController
@Inject
constructor(
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val lightRevealScrimInteractor: LightRevealScrimInteractor,
    private val wallpaperRepository: WallpaperRepository,
    private val secureSettingsRepository: SecureSettingsRepository,
    private val wallpaperController: WallpaperController,
    private val animator: AxWallpaperDepthAnimator,
    @Application private val scope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
) : CoreStartable {

    private var aodWallpaper = false
    private var wallpaperZoomDisabled = false
    private var isPrepped = false
    private var isPendingPrep = false
    private var isPendingReveal = false
    private var lightRevealAmount = 0f

    override fun start() {
        onStateChanged(keyguardTransitionInteractor.currentKeyguardState.value)

        scope.launch(context = mainDispatcher) {
            lightRevealScrimInteractor.revealAmount.collect { progress ->
                lightRevealAmount = progress
                checkPrepProgress()
                checkRevealProgress()
            }
        }

        scope.launch(context = mainDispatcher) {
            keyguardTransitionInteractor.transitions
                .filter { it.transitionState == TransitionState.STARTED }
                .collect { onStateChanged(it.to) }
        }

        scope.launch(context = mainDispatcher) {
            combine(
                wallpaperRepository.wallpaperSupportsAmbientMode.distinctUntilChanged(),
                wallpaperRepository.lockscreenWallpaperInfo,
                secureSettingsRepository.boolSetting(DISABLE_WALLPAPER_ZOOM).distinctUntilChanged(),
                ::Triple,
            ).collect { (aod, lockInfo, disabled) ->
                aodWallpaper = aod
                wallpaperZoomDisabled = disabled
                wallpaperController.setWallpaperZoomDisabled(disabled)
                if (disabled || lockInfo != null || aod) {
                    isPendingPrep = false
                    isPendingReveal = false
                    isPrepped = false
                    animator.clear()
                } else {
                    onStateChanged(keyguardTransitionInteractor.currentKeyguardState.value)
                }
            }
        }
    }

    private fun onStateChanged(state: KeyguardState) {
        when (state) {
            KeyguardState.AOD,
            KeyguardState.DOZING,
            KeyguardState.OFF -> {
                isPendingReveal = false
                isPendingPrep = true
                checkPrepProgress()
                setLauncherZoom(false)
            }
            KeyguardState.LOCKSCREEN -> {
                isPendingPrep = false
                isPendingReveal = true
                checkRevealProgress()
                setLauncherZoom(false)
            }
            KeyguardState.GONE -> {
                isPendingPrep = false
                isPendingReveal = false
                isPrepped = false
                animator.clear()
                setLauncherZoom(true)
            }
            KeyguardState.PRIMARY_BOUNCER,
            KeyguardState.ALTERNATE_BOUNCER,
            KeyguardState.OCCLUDED -> {
                isPendingPrep = false
                isPendingReveal = false
                isPrepped = false
                animator.clear()
                setLauncherZoom(false)
            }
            else -> setLauncherZoom(false)
        }
    }

    private fun checkPrepProgress() {
        if (isPendingPrep && lightRevealAmount <= SCRIM_COVERED_THRESHOLD) {
            isPendingPrep = false
            if (canUseWallpaper()) {
                isPrepped = true
                animator.holdDepth()
            }
        }
    }

    private fun checkRevealProgress() {
        if (isPendingReveal && isPrepped && lightRevealAmount >= REVEAL_START_THRESHOLD) {
            isPendingReveal = false
            isPrepped = false
            if (canUseWallpaper()) {
                animator.playReveal()
            }
        }
    }

    private fun canUseWallpaper(): Boolean =
        !wallpaperZoomDisabled &&
            wallpaperRepository.lockscreenWallpaperInfo.value == null &&
            !aodWallpaper

    private fun setLauncherZoom(enabled: Boolean) {
        if (!wallpaperZoomDisabled) {
            wallpaperController.setLauncherZoomEnabled(enabled)
        }
    }

    companion object {
        private const val DISABLE_WALLPAPER_ZOOM = "ax_wallpaper_depth_zoom_disabled"
        private const val SCRIM_COVERED_THRESHOLD = 0.05f
        private const val REVEAL_START_THRESHOLD = 0.55f
    }
}
