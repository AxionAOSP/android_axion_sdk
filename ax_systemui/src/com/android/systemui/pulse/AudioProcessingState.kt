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

data class AudioProcessingState(
    var visualizer: Visualizer? = null,
    var isProcessing: Boolean = false,
    var lastUpdateTime: Long = 0L,
    var melBands: Array<IntRange>? = null,
    var bandCenterHz: FloatArray? = null,
    var perBandPeak: FloatArray? = null,
    var smoothed: FloatArray? = null,
    var lastNumBins: Int = 0
) {
    fun resetBands() {
        melBands = null
        bandCenterHz = null
        perBandPeak = null
        smoothed = null
        lastNumBins = 0
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioProcessingState) return false
        val same = visualizer == other.visualizer &&
            isProcessing == other.isProcessing &&
            lastUpdateTime == other.lastUpdateTime &&
            lastNumBins == other.lastNumBins &&
            melBands.contentEquals(other.melBands) &&
            bandCenterHz.contentEquals(other.bandCenterHz) &&
            perBandPeak.contentEquals(other.perBandPeak) &&
            smoothed.contentEquals(other.smoothed)
        return same
    }

    override fun hashCode(): Int {
        var result = visualizer?.hashCode() ?: 0
        result = 31 * result + isProcessing.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + (melBands?.contentHashCode() ?: 0)
        result = 31 * result + (bandCenterHz?.contentHashCode() ?: 0)
        result = 31 * result + (perBandPeak?.contentHashCode() ?: 0)
        result = 31 * result + (smoothed?.contentHashCode() ?: 0)
        result = 31 * result + lastNumBins
        return result
    }
}
