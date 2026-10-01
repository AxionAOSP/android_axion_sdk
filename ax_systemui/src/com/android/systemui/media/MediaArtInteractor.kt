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
import com.android.systemui.overlay.KeyguardOverlayInteractor
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

private const val AOD_ALPHA = 0.35f
private const val NORMAL_ALPHA = 1f
private const val NORMAL_OVERLAY_ALPHA_AOD = 0.15f
private const val NORMAL_OVERLAY_ALPHA_NORMAL = 0.40f
private const val CONCEPT_OVERLAY_ALPHA_AOD = 0.25f
private const val CONCEPT_OVERLAY_ALPHA_NORMAL = 0.45f
private const val CONCEPT_MIN_BLUR = 30

data class MediaArtUiState(
    val isEnabled: Boolean = false,
    val isVisible: Boolean = false,
    val isDozing: Boolean = false,
    val artworkDrawable: Drawable? = null,
    val blurLevel: Int = 0,
    val artStyle: Int = MEDIA_ART_STYLE_BLUR,
    val metadataKey: String = "",
    val targetAlpha: Float = NORMAL_ALPHA,
    val overlayAlpha: Float = NORMAL_OVERLAY_ALPHA_NORMAL
)

@SysUISingleton
class MediaArtInteractor @Inject constructor(
    @param:Application private val scope: CoroutineScope,
    private val repository: MediaArtSettingsRepository,
    private val mediaSessionManager: MediaSessionManager,
    private val keyguardOverlayInteractor: KeyguardOverlayInteractor,
    private val dumpManager: DumpManager,
) : Dumpable {

    init {
        dumpManager.registerNormalDumpable(TAG, this)
    }

    private val isFeatureEnabled: Flow<Boolean> = repository.settingsFlow
        .map { it.isEnabled }
        .distinctUntilChanged()

    val isMediaArtVisible: Flow<Boolean> = combine(
        isFeatureEnabled,
        mediaSessionManager.activeSession,
        keyguardOverlayInteractor.isAmbientOverlayActive,
        keyguardOverlayInteractor.isShadeCollapsed
    ) { enabled, session, keyguardActive, shadeCollapsed ->
        enabled && (session?.isPlaying == true) && session?.albumArt != null && keyguardActive && shadeCollapsed
    }.distinctUntilChanged()

    val uiState: StateFlow<MediaArtUiState> = combine(
        isMediaArtVisible,
        repository.settingsFlow,
        mediaSessionManager.activeSession,
        keyguardOverlayInteractor.isDozing
    ) { isVisible, settings, session, isDozing ->
        val metadataKey = session?.let {
            if (it.track.isNotEmpty() || it.artist.isNotEmpty()) {
                "${it.packageName}:${it.track}:${it.artist}"
            } else {
                "${it.packageName}:${it.key}"
            }
        } ?: ""

        val isConcept = settings.artStyle == MEDIA_ART_STYLE_CONCEPT
        val targetAlpha = if (isDozing) AOD_ALPHA else NORMAL_ALPHA
        val overlayAlpha = when {
            isConcept && isDozing -> CONCEPT_OVERLAY_ALPHA_AOD
            isConcept -> CONCEPT_OVERLAY_ALPHA_NORMAL
            isDozing -> NORMAL_OVERLAY_ALPHA_AOD
            else -> NORMAL_OVERLAY_ALPHA_NORMAL
        }
        val effectiveBlur = if (isConcept) {
            maxOf(settings.blurLevel, CONCEPT_MIN_BLUR)
        } else {
            settings.blurLevel
        }

        MediaArtUiState(
            isEnabled = settings.isEnabled,
            isVisible = isVisible,
            isDozing = isDozing,
            artworkDrawable = session?.albumArt,
            blurLevel = effectiveBlur,
            artStyle = settings.artStyle,
            metadataKey = metadataKey,
            targetAlpha = targetAlpha,
            overlayAlpha = overlayAlpha
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
        private const val TAG = "MediaArtInteractor"
    }
}
