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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.overlay.KeyguardOverlayInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val keyguardOverlayInteractor: KeyguardOverlayInteractor,
    private val powerInteractor: PowerInteractor
) {

    private val accentColor: Int
        get() = context.getColor(AndroidR.color.system_accent1_100)

    private fun resolveBarColor(
        colorMode: PulseColorMode,
        albumColor: Int?
    ): Int = when (colorMode) {
        PulseColorMode.ACCENT -> accentColor
        PulseColorMode.ALBUM -> albumColor ?: accentColor
        PulseColorMode.LAVALAMP -> Color.WHITE
    }

    private fun checkScreenActive(
        isScreenOn: Boolean,
        powerState: ScreenPowerState,
        isPulsing: Boolean
    ): Boolean {
        if (!isScreenOn) return false
        return isPulsing || powerState != ScreenPowerState.SCREEN_OFF
    }

    private fun checkPulseVisible(
        isEnabled: Boolean,
        isPlaying: Boolean,
        isShadeOk: Boolean,
        isKeyguardOk: Boolean,
        isScreenOk: Boolean
    ): Boolean {
        if (!isEnabled || !isPlaying) return false
        if (!isShadeOk || !isKeyguardOk) return false
        return isScreenOk
    }

    private fun calculateAlpha(visible: Boolean, alpha: Float): Float =
        if (visible) alpha else 0f

    private fun getAudioFlow(visible: Boolean): Flow<FloatArray> =
        if (visible) audioProcessor.audioDataFlow else flowOf(floatArrayOf())

    private fun updateAudioCapture(visible: Boolean) {
        if (visible) {
            audioProcessor.startCapture()
        } else {
            audioProcessor.stopCapture()
        }
    }

    private val isScreenActive: Flow<Boolean> = combine(
        displayRepository.displayState,
        powerInteractor.screenPowerState,
        keyguardOverlayInteractor.isPulsing
    ) { display, power, isPulsing ->
        checkScreenActive(display.isScreenOn, power, isPulsing)
    }.distinctUntilChanged()

    private val configFlow: Flow<PulseConfig> = combine(
        settingsRepository.settingsFlow,
        displayRepository.displayState,
        mediaSessionManager.activeSession
    ) { settings, display, session ->
        PulseConfig(
            isEnabled = settings.isEnabled,
            barCount = settings.barCount,
            roundedBars = settings.roundedBars,
            barColor = resolveBarColor(settings.colorMode, session?.mediaColor),
            colorMode = settings.colorMode,
            style = settings.style,
            refreshRate = display.refreshRate
        )
    }.distinctUntilChanged()

    private val isPulseVisible: Flow<Boolean> = combine(
        configFlow.map { it.isEnabled }.distinctUntilChanged(),
        mediaSessionManager.isPlaying,
        keyguardOverlayInteractor.isShadeCollapsed,
        keyguardOverlayInteractor.isPulsingOverlayActive,
        isScreenActive
    ) { isEnabled, isPlaying, isShadeOk, isKeyguardOk, isScreenOk ->
        checkPulseVisible(isEnabled, isPlaying, isShadeOk, isKeyguardOk, isScreenOk)
    }.distinctUntilChanged()

    private val barHeightsFlow: Flow<FloatArray> =
        isPulseVisible.flatMapLatest(::getAudioFlow)

    val uiState: StateFlow<PulseUiState> = combine(
        configFlow,
        isPulseVisible,
        keyguardOverlayInteractor.unlockAlpha,
        barHeightsFlow
    ) { config, visible, alpha, heights ->
        PulseUiState(
            isEnabled = config.isEnabled,
            isVisible = visible,
            alpha = calculateAlpha(visible, alpha),
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
            isPulseVisible.collect(::updateAudioCapture)
        }
        scope.launch {
            settingsRepository.settingsFlow
                .map { it.barCount }
                .distinctUntilChanged()
                .collect(audioProcessor::setBarCount)
        }
    }
}
