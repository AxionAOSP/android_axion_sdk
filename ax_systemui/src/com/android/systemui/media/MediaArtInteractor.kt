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

import android.graphics.drawable.Drawable
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

const val MEDIA_ART_STYLE_BLUR = 0
const val MEDIA_ART_STYLE_CONCEPT = 1

data class MediaArtUiState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val isDozing: Boolean = false,
    val artworkDrawable: Drawable? = null,
    val blurLevel: Int = 0,
    val artStyle: Int = MEDIA_ART_STYLE_BLUR
)

@SysUISingleton
class MediaArtInteractor @Inject constructor(
    @Application private val scope: CoroutineScope,
    private val repository: MediaArtSettingsRepository,
    private val mediaSessionManager: MediaSessionManager,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val dumpManager: DumpManager,
) : Dumpable {

    init {
        dumpManager.registerNormalDumpable(TAG, this)
    }

    private val isFeatureEnabled: Flow<Boolean> = repository.settingsFlow
        .map { it.isEnabled }
        .distinctUntilChanged()

    private val mediaFlow: Flow<MediaState> = mediaSessionManager.activeSession
        .map { session ->
            MediaState(
                artwork = session?.albumArt,
                isPlaying = session?.isPlaying ?: false
            )
        }
        .distinctUntilChanged()

    private val isShadeCollapsed: Flow<Boolean> = combine(
        shadeInteractor.isQsExpanded,
        shadeInteractor.anyExpansion,
        shadeInteractor.isAnyFullyExpanded
    ) { isQs, expansion, isFullyExpanded ->
        !isQs && !isFullyExpanded && expansion <= SHADE_COLLAPSED_EXPANSION_THRESHOLD
    }.distinctUntilChanged()

    private val isBiometricDismissing: Flow<Boolean> = combine(
        keyguardInteractor.biometricUnlockState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep
    ) { biometric, step ->
        BiometricUnlockMode.dismissesKeyguard(biometric.mode) || step.to == KeyguardState.GONE
    }.distinctUntilChanged()

    private val isKeyguardActive: Flow<Boolean> = combine(
        keyguardTransitionInteractor.currentKeyguardState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep,
        keyguardInteractor.isKeyguardGoingAway,
        keyguardInteractor.isKeyguardOccluded,
        isBiometricDismissing
    ) { currentKeyguard, startedStep, isGoingAway, isOccluded, isDismissing ->
        if (isDismissing || isGoingAway || isOccluded) return@combine false
        if (startedStep.to == KeyguardState.GONE || currentKeyguard == KeyguardState.GONE) return@combine false
        if (startedStep.to == KeyguardState.OCCLUDED || currentKeyguard == KeyguardState.OCCLUDED) return@combine false
        if (startedStep.to == KeyguardState.PRIMARY_BOUNCER || currentKeyguard == KeyguardState.PRIMARY_BOUNCER) return@combine false
        if (startedStep.to == KeyguardState.ALTERNATE_BOUNCER || currentKeyguard == KeyguardState.ALTERNATE_BOUNCER) return@combine false
        when (currentKeyguard) {
            KeyguardState.LOCKSCREEN, KeyguardState.AOD, KeyguardState.DOZING -> true
            else -> false
        }
    }.distinctUntilChanged()

    val isMediaArtVisible: Flow<Boolean> = combine(
        isFeatureEnabled,
        mediaFlow,
        isKeyguardActive,
        isShadeCollapsed
    ) { enabled, media, keyguardActive, shadeCollapsed ->
        enabled && media.isPlaying && media.artwork != null && keyguardActive && shadeCollapsed
    }.distinctUntilChanged()

    val uiState: StateFlow<MediaArtUiState> = combine(
        isMediaArtVisible,
        repository.settingsFlow,
        mediaFlow,
        keyguardInteractor.isDozing
    ) { isVisible, settings, media, isDozing ->
        MediaArtUiState(
            isEnabled = settings.isEnabled,
            isVisible = isVisible,
            isDozing = isDozing,
            artworkDrawable = media.artwork,
            blurLevel = settings.blurLevel,
            artStyle = settings.artStyle
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = MediaArtUiState()
    )

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("MediaArtInteractor:")
        pw.println("  uiState: ${uiState.value}")
    }

    companion object {
        const val MEDIA_ART_STYLE_BLUR = 0
        const val MEDIA_ART_STYLE_CONCEPT = 1
        const val MEDIA_ART_STYLE_GRADIENT = 1
        const val MEDIA_ART_STYLE_COLOR = 2
        private const val SHADE_COLLAPSED_EXPANSION_THRESHOLD = 0.1f
        private const val TAG = "MediaArtInteractor"
    }
}

private data class MediaState(
    val artwork: Drawable? = null,
    val isPlaying: Boolean = false
)
