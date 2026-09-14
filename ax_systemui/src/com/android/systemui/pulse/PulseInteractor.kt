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
import android.media.session.PlaybackState
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.media.MediaSessionManager
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PulseUiState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val alpha: Float = 1f,
    val barHeights: FloatArray = floatArrayOf(),
    val barCount: Int = 32,
    val roundedBars: Boolean = true,
    val barColor: Int = Color.WHITE,
    val colorMode: PulseColorMode = PulseColorMode.LAVALAMP,
    val style: PulseStyle = PulseStyle.BARS,
    val refreshRate: Float = 60f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PulseUiState) return false
        val same = isEnabled == other.isEnabled &&
               isVisible == other.isVisible &&
               alpha == other.alpha &&
               barHeights.contentEquals(other.barHeights) &&
               barCount == other.barCount &&
               roundedBars == other.roundedBars &&
               barColor == other.barColor &&
               colorMode == other.colorMode &&
               style == other.style &&
               refreshRate == other.refreshRate
        return same
    }

    override fun hashCode(): Int {
        var result = isEnabled.hashCode()
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + alpha.hashCode()
        result = 31 * result + barHeights.contentHashCode()
        result = 31 * result + barCount
        result = 31 * result + roundedBars.hashCode()
        result = 31 * result + barColor
        result = 31 * result + colorMode.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + refreshRate.hashCode()
        return result
    }
}

private sealed interface MediaEvent {
    data class PlaybackStateChanged(val isPlaying: Boolean) : MediaEvent
    data class ColorChanged(val color: Int?) : MediaEvent
}

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
    private val shadeInteractor: ShadeInteractor,
) {

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    private val accentColor: Int
        get() = context.getColor(AndroidR.color.system_accent1_100)

    private var isMediaPlaying = mediaSessionManager.isMediaPlaying
    private var isPulsing = false
    private var isKeyguardGoingAway = false
    private var unlockProgress = 0f
    private var pulseRunning = false

    private val isKeyguardActive: Boolean
        get() {
            if (isKeyguardGoingAway && unlockProgress >= 0.5f) return false
            val state = keyguardTransitionInteractor.currentKeyguardState.value
            if (state == KeyguardState.LOCKSCREEN || state == KeyguardState.AOD) return true
            if (state == KeyguardState.DOZING) return isPulsing
            return false
        }

    private val isScreenActive: Boolean
        get() {
            if (!displayRepository.displayState.value.isScreenOn) return false
            if (isPulsing) return true
            return powerInteractor.screenPowerState.value != ScreenPowerState.SCREEN_OFF
        }

    private val shouldShowPulse: Boolean
        get() {
            if (!_uiState.value.isEnabled) return false
            if (!isMediaPlaying) return false
            if (shadeInteractor.isAnyFullyExpanded.value) return false
            if (!isKeyguardActive && !isPulsing) return false
            return isScreenActive
        }

    private val mediaStateFlow: Flow<MediaEvent> = conflatedCallbackFlow {
        val listener = object : MediaSessionManager.MediaDataListener {
            override fun onPlaybackStateChanged(state: Int) {
                trySend(MediaEvent.PlaybackStateChanged(state == PlaybackState.STATE_PLAYING))
            }

            override fun onMediaColorsChanged(color: Int?) {
                trySend(MediaEvent.ColorChanged(color))
            }
        }
        mediaSessionManager.addListener(listener)
        awaitClose { mediaSessionManager.removeListener(listener) }
    }

    init {
        observeSettings()
        observeDisplayState()
        observeAudioData()
        observeKeyguardAndPower()
        observeMediaState()
        observeUnlockTransition()
        observeKeyguardGoingAway()
    }

    private fun observeUnlockTransition() {
        val flow = keyguardTransitionInteractor.transitionValue(KeyguardState.GONE)
        scope.launch { flow.collect(::onUnlockProgressChanged) }
    }

    private fun onUnlockProgressChanged(progress: Float) {
        unlockProgress = progress
        val newAlpha = (1f - (progress * 2f)).coerceIn(0f, 1f)
        _uiState.update { it.copy(alpha = newAlpha) }
        if (progress >= 1f || (isKeyguardGoingAway && progress >= 0.5f)) {
            updatePulseState()
        }
    }

    private fun observeKeyguardGoingAway() {
        val flow = keyguardInteractor.isKeyguardGoingAway
        scope.launch { flow.collect(::onKeyguardGoingAwayChanged) }
    }

    private fun onKeyguardGoingAwayChanged(goingAway: Boolean) {
        isKeyguardGoingAway = goingAway
        if (!goingAway) {
            _uiState.update { it.copy(alpha = 1f) }
        }
        updatePulseState()
    }

    private fun observeKeyguardAndPower() {
        val stateFlow = combine(
            keyguardTransitionInteractor.currentKeyguardState,
            keyguardInteractor.isPulsing,
            powerInteractor.screenPowerState,
            shadeInteractor.isAnyFullyExpanded
        ) { _, pulsing, _, _ -> pulsing }
        scope.launch { stateFlow.collect(::onKeyguardOrPowerChanged) }
    }

    private fun onKeyguardOrPowerChanged(pulsing: Boolean) {
        isPulsing = pulsing
        updatePulseState()
    }

    private fun observeMediaState() {
        scope.launch { mediaStateFlow.collect(::onMediaEvent) }
    }

    private fun onMediaEvent(event: MediaEvent) {
        when (event) {
            is MediaEvent.PlaybackStateChanged -> onPlaybackChanged(event.isPlaying)
            is MediaEvent.ColorChanged -> onColorChanged(event.color)
        }
    }

    private fun onPlaybackChanged(isPlaying: Boolean) {
        isMediaPlaying = isPlaying
        updatePulseState()
    }

    private fun onColorChanged(color: Int?) {
        if (_uiState.value.colorMode != PulseColorMode.ALBUM) return
        _uiState.update { it.copy(barColor = color ?: accentColor) }
    }

    private fun observeAudioData() {
        scope.launch {
            audioProcessor.audioDataFlow.collect { heights ->
                if (pulseRunning) {
                    _uiState.update { it.copy(barHeights = heights) }
                }
            }
        }
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                audioProcessor.setBarCount(settings.barCount)
                _uiState.update { current ->
                    current.copy(
                        isEnabled = settings.isEnabled,
                        barCount = settings.barCount,
                        roundedBars = settings.roundedBars,
                        colorMode = settings.colorMode,
                        style = settings.style,
                        barColor = when (settings.colorMode) {
                            PulseColorMode.ACCENT -> accentColor
                            else -> current.barColor
                        }
                    )
                }
                updatePulseState()
            }
        }
    }

    private fun observeDisplayState() {
        scope.launch {
            displayRepository.displayState.collect { displayState ->
                _uiState.update { it.copy(refreshRate = displayState.refreshRate) }
                updatePulseState()
            }
        }
    }

    private fun updatePulseState() {
        val shouldShow = shouldShowPulse
        if (shouldShow != pulseRunning) {
            pulseRunning = shouldShow
            if (shouldShow) {
                audioProcessor.startCapture()
            } else {
                audioProcessor.stopCapture()
            }
            _uiState.update { it.copy(isVisible = shouldShow) }
        }
    }
}
