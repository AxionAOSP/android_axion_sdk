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

package com.android.systemui.qs.ax.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaSessionState
import com.android.systemui.qs.ax.data.repository.AxMediaHistoryRepository
import com.android.systemui.qs.ax.shared.model.AxMediaDismissToken
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.isDisplayable
import com.android.systemui.qs.ax.shared.model.isVisibleOn
import javax.inject.Inject

@SysUISingleton
class AxMediaInteractor
@Inject
constructor(
    private val mediaHistoryRepository: AxMediaHistoryRepository
) {
    fun getSessionPackageName(sessionKey: Any): String? =
        mediaHistoryRepository.getPackageName(sessionKey)

    fun getSessionLastActive(sessionKey: Any): Long =
        mediaHistoryRepository.getLastActive(sessionKey)

    fun compareSessions(
        sessionA: MediaSessionModel?,
        sessionB: MediaSessionModel?,
        keyA: Any,
        keyB: Any,
        lastMediaPackage: String?
    ): Int {
        if (keyA == keyB) return 0

        val isActiveA = sessionA?.isActive == true
        val isActiveB = sessionB?.isActive == true
        if (isActiveA != isActiveB) {
            return if (isActiveA) -1 else 1
        }

        val pkgA = getSessionPackageName(keyA)
        val pkgB = getSessionPackageName(keyB)
        val isLastPkgA = lastMediaPackage != null && pkgA == lastMediaPackage
        val isLastPkgB = lastMediaPackage != null && pkgB == lastMediaPackage
        if (isLastPkgA != isLastPkgB) {
            return if (isLastPkgA) -1 else 1
        }

        val lastActiveA = getSessionLastActive(keyA)
        val lastActiveB = getSessionLastActive(keyB)
        if (lastActiveA != lastActiveB) {
            return lastActiveB.compareTo(lastActiveA)
        }

        return 0
    }

    fun isSessionVisible(
        session: MediaSessionModel?,
        surface: AxMediaSurface,
        dismissedTokens: Set<AxMediaDismissToken>
    ): Boolean {
        if (session == null || !session.isDisplayable()) return false
        if (dismissedTokens.any { it.sessionKey == session.key }) return false
        return session.isVisibleOn(surface)
    }

    fun sortSessions(
        sessions: List<MediaSessionModel>,
        lastMediaPackage: String?
    ): List<MediaSessionModel> =
        sessions
            .filter { it.isDisplayable() }
            .sortedWith { a, b ->
                compareSessions(a, b, a.key, b.key, lastMediaPackage)
            }
}
