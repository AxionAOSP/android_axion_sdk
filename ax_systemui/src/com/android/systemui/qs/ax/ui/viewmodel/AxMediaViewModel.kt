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

package com.android.systemui.qs.ax.ui.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.android.internal.jank.Cuj
import com.android.systemui.animation.Expandable
import com.android.systemui.classifier.Classifier
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.remedia.domain.interactor.MediaInteractor
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaOutputDeviceModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaFalsingSystem
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.qs.ax.data.repository.AxMediaHistoryRepository
import com.android.systemui.qs.ax.domain.interactor.AxMediaInteractor
import com.android.systemui.qs.ax.shared.model.AxMediaDismissToken
import com.android.systemui.qs.ax.shared.model.AxMediaSessionModel
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.isDisplayable
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AxMediaViewModel
@Inject
constructor(
    private val interactor: MediaInteractor,
    private val falsingSystem: MediaFalsingSystem,
    private val mediaCarouselInteractor: MediaCarouselInteractor,
    private val mediaHistoryRepository: AxMediaHistoryRepository,
    private val activityStarter: ActivityStarter,
    private val axMediaInteractor: AxMediaInteractor,
    private val shadeInteractor: ShadeInteractor,
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher
) {
    val isShadeInteracting: StateFlow<Boolean> = shadeInteractor.isUserInteracting
    private val scrubbingSessionKey = mutableStateOf<Any?>(null)
    private val gutsSessionKey = mutableStateOf<Any?>(null)
    private val scrubProgress = mutableFloatStateOf(0f)
    private val dismissedSessions =
        mutableStateOf(emptyMap<AxMediaSurface, Set<AxMediaDismissToken>>())
    private val sessions: List<MediaSessionModel> by derivedStateOf {
        interactor.sessions.map { raw ->
            AxMediaSessionModel(raw) {
                interactor.sessions.firstOrNull { it.key == raw.key }?.positionMs ?: raw.positionMs
            }
        }
    }

    private val activeSessions: List<MediaSessionModel> by derivedStateOf {
        axMediaInteractor.sortSessions(sessions, lastMediaPackage.value)
    }

    val currentSession: MediaSessionModel? by derivedStateOf {
        val selected = activeSessions.getOrNull(interactor.currentCarouselIndex)
        selected?.takeIf { it.isDisplayable() && it.isActive } ?: activeSessions.firstOrNull { it.isActive }
    }

    fun currentSession(surface: AxMediaSurface): MediaSessionModel? {
        val visible = visibleSessions(surface)
        val selected = visible.getOrNull(interactor.currentCarouselIndex)
        return selected ?: visible.firstOrNull()
    }

    val showOnLockscreen = mediaCarouselInteractor.allowMediaOnLockscreen

    fun setDynamicBarExpanded(expanded: Boolean) {}

    val lastMediaPackage = mediaHistoryRepository.lastMediaPackage

    init {
        mediaHistoryRepository.startListening()
    }

    fun getSessionPackageName(sessionKey: Any): String? =
        axMediaInteractor.getSessionPackageName(sessionKey)

    fun getSessionLastActive(sessionKey: Any): Long =
        axMediaInteractor.getSessionLastActive(sessionKey)

    fun compareSessions(keyA: Any, keyB: Any, lastMediaPackage: String?): Int {
        if (keyA == keyB) return 0
        val sessionA = sessionForKey(keyA)
        val sessionB = sessionForKey(keyB)
        return axMediaInteractor.compareSessions(sessionA, sessionB, keyA, keyB, lastMediaPackage)
    }

    fun synchronizeSession(sessionKey: Any?) {
        if (scrubbingSessionKey.value != null && scrubbingSessionKey.value != sessionKey) {
            scrubbingSessionKey.value = null
        }
        if (gutsSessionKey.value != null && gutsSessionKey.value != sessionKey) {
            gutsSessionKey.value = null
        }
    }

    fun visibleSessions(surface: AxMediaSurface): List<MediaSessionModel> {
        if (surface == AxMediaSurface.LOCKSCREEN && !showOnLockscreen.value) {
            return emptyList()
        }
        val dismissed = dismissedSessions.value[surface].orEmpty()
        return activeSessions.filter { axMediaInteractor.isSessionVisible(it, surface, dismissed) }
    }

    fun hasVisibleSessions(surface: AxMediaSurface): Boolean = visibleSessions(surface).isNotEmpty()

    fun sessionForKey(sessionKey: Any): MediaSessionModel? =
        sessions.firstOrNull { it.key == sessionKey }

    fun isSessionVisible(sessionKey: Any, surface: AxMediaSurface): Boolean {
        if (surface == AxMediaSurface.LOCKSCREEN && !showOnLockscreen.value) {
            return false
        }
        val session = sessionForKey(sessionKey) ?: return false
        val dismissed = dismissedSessions.value[surface].orEmpty()
        return axMediaInteractor.isSessionVisible(session, surface, dismissed)
    }

    fun hasVisibleGuts(): Boolean {
        val sessionKey = gutsSessionKey.value ?: return false
        return sessions.any { it.key == sessionKey && it.isDisplayable() }
    }

    fun isGutsVisible(session: MediaSessionModel): Boolean = gutsSessionKey.value == session.key

    fun showGuts(session: MediaSessionModel) {
        gutsSessionKey.value = session.key
    }

    fun closeGuts() {
        gutsSessionKey.value = null
    }

    fun cancelGuts() {
        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY, ::closeGuts)
    }

    fun isSwipeFalseTouch(): Boolean =
        falsingSystem.isFalseTouch(Classifier.MEDIA_CAROUSEL_SWIPE)

    fun dismissBySwipe(surface: AxMediaSurface) {
        if (!surface.dismissible) return
        dismissedSessions.value =
            dismissedSessions.value +
                (surface to activeSessions.mapTo(mutableSetOf()) { it.dismissToken() })
        closeGuts()
    }

    fun dismissFromSurface(session: MediaSessionModel, surface: AxMediaSurface) {
        if (!surface.dismissible) return
        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) {
            val dismissed = dismissedSessions.value[surface].orEmpty() + session.dismissToken()
            dismissedSessions.value = dismissedSessions.value + (surface to dismissed)
            closeGuts()
        }
    }

    fun openSettings() {
        cancelGuts()
        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) { interactor.openMediaSettings() }
    }

    fun progress(session: MediaSessionModel): Float {
        if (scrubbingSessionKey.value == session.key) return scrubProgress.floatValue
        if (session.durationMs <= 0L) return 0f
        return (session.positionMs.toFloat() / session.durationMs).coerceIn(0f, 1f)
    }

    fun onScrubChange(session: MediaSessionModel, progress: Float) {
        scrubbingSessionKey.value = session.key
        scrubProgress.floatValue = progress.coerceIn(0f, 1f)
    }

    fun onScrubFinished(session: MediaSessionModel, dragDelta: Offset) {
        if (
            session.canBeScrubbed &&
                scrubbingSessionKey.value == session.key &&
                dragDelta.isHorizontal() &&
                !falsingSystem.isFalseTouch(Classifier.MEDIA_SEEKBAR)
        ) {
            interactor.seek(
                session.key,
                (scrubProgress.floatValue * session.durationMs).roundToLong()
            )
        }
        scrubbingSessionKey.value = null
    }

    fun openSession(session: MediaSessionModel, expandable: Expandable) {
        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) { session.onClick(expandable) }
    }

    fun openLastMediaApp(expandable: Expandable) {
        falsingSystem.runIfNotFalseTap(FalsingManager.LOW_PENALTY) {
            applicationScope.launch(context = mainDispatcher) {
                val target = mediaHistoryRepository.getLaunchTarget() ?: return@launch
                activityStarter.postStartActivityDismissingKeyguard(
                    target.intent,
                    0,
                    expandable.activityTransitionController(Cuj.CUJ_SHADE_APP_LAUNCH_FROM_MEDIA_PLAYER),
                    null,
                    target.userHandle
                )
            }
        }
    }

    fun openOutput(device: MediaOutputDeviceModel, expandable: Expandable) {
        falsingSystem.runIfNotFalseTap(FalsingManager.MODERATE_PENALTY) { device.onClick(expandable) }
    }

    fun runAction(action: MediaActionModel.Action) {
        falsingSystem.runIfNotFalseTap(FalsingManager.MODERATE_PENALTY) { action.onClick?.invoke() }
    }

    private fun Offset.isHorizontal(): Boolean = abs(x) >= abs(y)

    private fun MediaSessionModel.dismissToken(): AxMediaDismissToken =
        AxMediaDismissToken(key, title, subtitle)
}
