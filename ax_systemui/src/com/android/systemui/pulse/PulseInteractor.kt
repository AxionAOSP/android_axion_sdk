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
package com.android.systemui.pulse

import android.R as AndroidR
import android.content.Context
import android.graphics.Color
import android.media.MediaMetadata
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class MediaState(
    val isPlaying: Boolean = false,
    val albumColor: Int? = null
)

private data class PulseConfig(
    val isEnabled: Boolean = false,
    val barCount: Int = 32,
    val roundedBars: Boolean = true,
    val barColor: Int = Color.WHITE,
    val colorMode: PulseColorMode = PulseColorMode.LAVALAMP,
    val style: PulseStyle = PulseStyle.BARS,
    val refreshRate: Float = 60f
)

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class PulseInteractor @Inject constructor(
    @Application private val context: Context,
    @Application private val scope: CoroutineScope,
    private val settingsRepository: PulseSettingsRepository,
    private val displayRepository: PulseDisplayRepository,
    private val audioProcessor: PulseAudioDataProcessor,
    private val mediaSessionManager: MediaSessionManager,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val keyguardInteractor: KeyguardInteractor,
    private val powerInteractor: PowerInteractor,
    private val shadeInteractor: ShadeInteractor
) {

    private val accentColor: Int
        get() = context.getColor(AndroidR.color.system_accent1_100)

    private val mediaFlow: Flow<MediaState> = mediaSessionManager.activeSession
        .map { session ->
            MediaState(
                isPlaying = session?.isPlaying ?: false,
                albumColor = session?.mediaColor
            )
        }
        .distinctUntilChanged()

    private val isShadeCollapsed: Flow<Boolean> = combine(
        shadeInteractor.isQsExpanded,
        shadeInteractor.anyExpansion,
        shadeInteractor.isAnyFullyExpanded
    ) { isQs, expansion, isFullyExpanded ->
        !isQs && !isFullyExpanded && expansion <= 0.1f
    }.distinctUntilChanged()

    private val isBiometricDismissing: Flow<Boolean> = combine(
        keyguardInteractor.biometricUnlockState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep
    ) { biometric, step ->
        BiometricUnlockMode.dismissesKeyguard(biometric.mode) || step.to == KeyguardState.GONE
    }.distinctUntilChanged()

    private val unlockAlpha: Flow<Float> = combine(
        keyguardTransitionInteractor.transitionValue(KeyguardState.GONE),
        isBiometricDismissing
    ) { progress, isDismissing ->
        if (isDismissing) 0f else (1f - (progress * 2f)).coerceIn(0f, 1f)
    }.distinctUntilChanged()

    private val isKeyguardActive: Flow<Boolean> = combine(
        keyguardTransitionInteractor.currentKeyguardState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep,
        keyguardInteractor.isKeyguardGoingAway,
        keyguardInteractor.isPulsing,
        isBiometricDismissing
    ) { currentKeyguard, startedStep, isGoingAway, isPulsing, isDismissing ->
        if (isDismissing || isGoingAway) return@combine false
        if (startedStep.to == KeyguardState.GONE || currentKeyguard == KeyguardState.GONE) return@combine false
        when (currentKeyguard) {
            KeyguardState.LOCKSCREEN, KeyguardState.AOD -> true
            KeyguardState.DOZING -> isPulsing
            else -> false
        }
    }.distinctUntilChanged()

    private val isScreenActive: Flow<Boolean> = combine(
        displayRepository.displayState,
        powerInteractor.screenPowerState,
        keyguardInteractor.isPulsing
    ) { display, power, isPulsing ->
        display.isScreenOn && (isPulsing || power != ScreenPowerState.SCREEN_OFF)
    }.distinctUntilChanged()

    private val configFlow: Flow<PulseConfig> = combine(
        settingsRepository.settingsFlow,
        displayRepository.displayState,
        mediaFlow
    ) { settings, display, media ->
        val barColor = when (settings.colorMode) {
            PulseColorMode.ACCENT -> accentColor
            PulseColorMode.ALBUM -> media.albumColor ?: accentColor
            PulseColorMode.LAVALAMP -> Color.WHITE
        }
        PulseConfig(
            isEnabled = settings.isEnabled,
            barCount = settings.barCount,
            roundedBars = settings.roundedBars,
            barColor = barColor,
            colorMode = settings.colorMode,
            style = settings.style,
            refreshRate = display.refreshRate
        )
    }.distinctUntilChanged()

    private val isPulseVisible: Flow<Boolean> = combine(
        configFlow.map { it.isEnabled }.distinctUntilChanged(),
        mediaFlow.map { it.isPlaying }.distinctUntilChanged(),
        isShadeCollapsed,
        isKeyguardActive,
        isScreenActive
    ) { isEnabled, isPlaying, isShadeOk, isKeyguardOk, isScreenOk ->
        isEnabled && isPlaying && isShadeOk && isKeyguardOk && isScreenOk
    }.distinctUntilChanged()

    private val barHeightsFlow: Flow<FloatArray> = isPulseVisible.flatMapLatest { visible ->
        if (visible) audioProcessor.audioDataFlow else flowOf(floatArrayOf())
    }

    val uiState: StateFlow<PulseUiState> = combine(
        configFlow,
        isPulseVisible,
        unlockAlpha,
        barHeightsFlow
    ) { config, visible, alpha, heights ->
        PulseUiState(
            isEnabled = config.isEnabled,
            isVisible = visible,
            alpha = if (visible) alpha else 0f,
            barHeights = heights,
            barCount = config.barCount,
            roundedBars = config.roundedBars,
            barColor = config.barColor,
            colorMode = config.colorMode,
            style = config.style,
            refreshRate = config.refreshRate
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = PulseUiState()
    )

    init {
        scope.launch {
            isPulseVisible.collect { visible ->
                if (visible) {
                    audioProcessor.startCapture()
                } else {
                    audioProcessor.stopCapture()
                }
            }
        }
        scope.launch {
            settingsRepository.settingsFlow
                .map { it.barCount }
                .distinctUntilChanged()
                .collect(audioProcessor::setBarCount)
        }
    }
}
