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

import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@SysUISingleton
class PulseAudioDataProcessor @Inject constructor(
    private val displayRepository: PulseDisplayRepository
) {
    companion object {
        private const val TAG = "PulseAudioProcessor"
        private const val DEFAULT_BAR_COUNT = 32
        private const val MEL_MIN_HZ = 40f
        private const val MEL_MAX_HZ = 8000f
        private const val PEAK_DECAY_PER_SEC = 0.55f
        private const val PEAK_FLOOR = 40f
        private const val ATTACK_SPEED = 60f
        private const val DECAY_SPEED = 12f
        private const val GAMMA = 0.65f
        private const val MAX_BAR_HEIGHT = 500f
        private const val A_WEIGHT_PEAK_HZ = 2500f
        private const val A_WEIGHT_GAIN = 0.4f
        private const val A_WEIGHT_OCTAVES = 2f
    }

    private val lock = Any()
    private var state = AudioProcessingState()
    private var barCount = DEFAULT_BAR_COUNT

    private val _audioDataFlow = MutableSharedFlow<FloatArray>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val audioDataFlow: SharedFlow<FloatArray> = _audioDataFlow.asSharedFlow()

    fun startCapture() {
        synchronized(lock) {
            if (state.isProcessing) return
            startCaptureLocked()
        }
    }

    private fun startCaptureLocked() {
        runCatching {
            val viz = Visualizer(0)
            viz.captureSize = Visualizer.getCaptureSizeRange()[1]
            viz.setDataCaptureListener(CaptureListener(), Visualizer.getMaxCaptureRate(), false, true)
            viz.enabled = true
            state.visualizer = viz
            state.isProcessing = true
        }.onFailure { e ->
            Log.e(TAG, "Failed to start visualizer", e)
            stopCaptureLocked()
        }
    }

    private inner class CaptureListener : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, r: Int) {}

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft == null || fft.size < 4) return
            processFFTData(fft, samplingRate)
        }
    }

    fun stopCapture() {
        synchronized(lock) {
            stopCaptureLocked()
        }
    }

    private fun stopCaptureLocked() {
        if (!state.isProcessing && state.visualizer == null) return

        runCatching {
            state.visualizer?.apply {
                enabled = false
                setDataCaptureListener(null, 0, false, false)
                release()
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to release visualizer", e)
        }
        state = AudioProcessingState()
    }

    fun cleanup() {
        stopCapture()
    }

    fun isCapturing(): Boolean = synchronized(lock) { state.isProcessing }

    private fun processFFTData(fftBytes: ByteArray, samplingRate: Int) {
        val result: FloatArray
        synchronized(lock) {
            if (!state.isProcessing) return
            val currentTime = SystemClock.uptimeMillis()
            val throttleMs = displayRepository.displayState.value.throttleMs
            if (currentTime - state.lastUpdateTime < throttleMs) return
            val dt = if (state.lastUpdateTime == 0L) {
                1f / 60f
            } else {
                ((currentTime - state.lastUpdateTime) / 1000f).coerceIn(0.001f, 0.1f)
            }
            state.lastUpdateTime = currentTime

            val count = barCount
            if (count <= 0) return

            val numBins = fftBytes.size / 2
            val sampleRateHz = samplingRate / 1000f
            val binHz = sampleRateHz / (numBins * 2)

            val currentBands = state.melBands
            val currentCenters = state.bandCenterHz
            val (bands, centers) = if (currentBands != null &&
                currentCenters != null &&
                currentBands.size == count &&
                currentCenters.size == count &&
                numBins == state.lastNumBins
            ) {
                currentBands to currentCenters
            } else {
                val (builtBands, builtCenters) = buildMelBands(count, numBins, binHz)
                state.melBands = builtBands
                state.bandCenterHz = builtCenters
                state.lastNumBins = numBins
                builtBands to builtCenters
            }

            val peaks = state.perBandPeak?.takeIf { it.size == count }
                ?: FloatArray(count) { PEAK_FLOOR }.also { state.perBandPeak = it }

            val prev = state.smoothed?.takeIf { it.size == count }
                ?: FloatArray(count).also { state.smoothed = it }

            val peakDecay = PEAK_DECAY_PER_SEC.pow(dt)
            val attackAlpha = (1f - exp(-ATTACK_SPEED * dt)).coerceIn(0.01f, 1f)
            val decayAlpha = (1f - exp(-DECAY_SPEED * dt)).coerceIn(0.01f, 1f)

            for (i in 0 until count) {
                val range = bands[i]
                var sumMag = 0f
                var binCount = 0

                for (bin in range) {
                    if (bin < 1 || bin >= numBins) continue
                    val real = fftBytes[bin * 2].toFloat()
                    val imag = fftBytes[bin * 2 + 1].toFloat()
                    sumMag += sqrt(real * real + imag * imag)
                    binCount++
                }
                if (binCount == 0) {
                    prev[i] -= decayAlpha * prev[i]
                    continue
                }

                val avgMag = (sumMag / binCount) * aWeight(centers[i])

                val decayedPeak = max(peaks[i] * peakDecay, PEAK_FLOOR)
                peaks[i] = max(decayedPeak, avgMag)

                val norm = (avgMag / peaks[i]).coerceIn(0f, 1f)
                val shaped = norm.pow(GAMMA)
                val target = shaped * MAX_BAR_HEIGHT

                val alpha = if (target > prev[i]) attackAlpha else decayAlpha
                prev[i] += alpha * (target - prev[i])
            }

            result = prev.copyOf()
        }
        _audioDataFlow.tryEmit(result)
    }

    fun setBarCount(count: Int) {
        synchronized(lock) {
            if (count != barCount) {
                barCount = count
                state.resetBands()
            }
        }
    }

    private fun buildMelBands(
        barCount: Int,
        numBins: Int,
        binHz: Float
    ): Pair<Array<IntRange>, FloatArray> {
        val minBin = (MEL_MIN_HZ / binHz).toInt().coerceAtLeast(1)
        val maxBin = (MEL_MAX_HZ / binHz).toInt().coerceAtMost(numBins - 1)
        val melMin = hzToMel(minBin * binHz)
        val melMax = hzToMel(maxBin * binHz)
        val melStep = (melMax - melMin) / barCount

        val ranges = Array(barCount) { minBin..minBin }
        val centers = FloatArray(barCount)
        for (i in 0 until barCount) {
            val loHz = melToHz(melMin + i * melStep)
            val hiHz = melToHz(melMin + (i + 1) * melStep)
            val lo = (loHz / binHz).toInt().coerceIn(minBin, maxBin)
            val hi = (hiHz / binHz).toInt().coerceIn(lo, maxBin)
            ranges[i] = lo..hi
            centers[i] = melToHz(melMin + (i + 0.5f) * melStep)
        }
        return ranges to centers
    }

    private fun hzToMel(hz: Float): Float = 2595f * log10(1f + hz / 700f)

    private fun melToHz(mel: Float): Float = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun aWeight(centerHz: Float): Float {
        if (centerHz <= 0f) return 1f
        val octavesFromPeak = ln(centerHz / A_WEIGHT_PEAK_HZ) / ln(2f)
        val falloff = (octavesFromPeak / A_WEIGHT_OCTAVES)
        val bell = exp(-(falloff * falloff))
        return 1f + A_WEIGHT_GAIN * bell
    }
}
