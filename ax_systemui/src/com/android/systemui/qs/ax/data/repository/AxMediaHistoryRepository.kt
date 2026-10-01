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

package com.android.systemui.qs.ax.data.repository

import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@SysUISingleton
class AxMediaHistoryRepository
@Inject
constructor(
    @Application private val context: Context,
    private val mediaDataManager: MediaDataManager,
    private val secureSettings: SecureSettings,
    private val userRepository: UserRepository,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : MediaDataManager.Listener {
    private val savedPackages = ConcurrentHashMap<Int, String>()
    private val keyToInstanceId = ConcurrentHashMap<String, Any>()
    private val sessionPackages = ConcurrentHashMap<Any, String>()
    private val sessionLastActive = ConcurrentHashMap<Any, Long>()
    private val writes = Channel<SaveRequest>(Channel.UNLIMITED)
    private val listening = AtomicBoolean(false)

    val lastMediaPackage: StateFlow<String?> =
        userRepository.selectedUserInfo
            .flatMapLatest { user ->
                secureSettings
                    .observerFlow(user.id, SETTING_LAST_MEDIA_PACKAGE)
                    .onStart { emit(Unit) }
                    .map { readPackage(user.id) }
            }
            .distinctUntilChanged()
            .flowOn(backgroundDispatcher)
            .stateIn(applicationScope, SharingStarted.Eagerly, null)

    internal fun startListening() {
        if (!listening.compareAndSet(false, true)) return
        applicationScope.launch(context = backgroundDispatcher) {
            for (request in writes) {
                secureSettings.putStringForUser(
                    SETTING_LAST_MEDIA_PACKAGE,
                    request.packageName,
                    request.userId,
                )
            }
        }
        mediaDataManager.addListener(this)
    }

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean,
    ) {
        if (oldKey != null && oldKey != key) {
            val oldInstanceId = keyToInstanceId.remove(oldKey)
            if (oldInstanceId != null) {
                sessionPackages.remove(oldInstanceId)
                sessionLastActive.remove(oldInstanceId)
            }
            sessionPackages.remove(oldKey)
            sessionLastActive.remove(oldKey)
        }

        val packageName = data.packageName?.takeIf { it.isNotBlank() }
        if (packageName != null) {
            sessionPackages[key] = packageName
        }
        sessionLastActive[key] = data.lastActive
        data.instanceId?.let { instanceId ->
            keyToInstanceId[key] = instanceId
            if (packageName != null) {
                sessionPackages[instanceId] = packageName
            }
            sessionLastActive[instanceId] = data.lastActive
        }

        if (data.isPlaying == true && packageName != null) {
            val userId = userRepository.getSelectedUserInfo().id
            if (savedPackages.put(userId, packageName) != packageName) {
                writes.trySend(SaveRequest(userId, packageName))
            }
        }
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        val instanceId = keyToInstanceId.remove(key)
        if (instanceId != null) {
            sessionPackages.remove(instanceId)
            sessionLastActive.remove(instanceId)
        }
        sessionPackages.remove(key)
        sessionLastActive.remove(key)
    }

    fun getPackageName(sessionKey: Any): String? = sessionPackages[sessionKey]

    fun getLastActive(sessionKey: Any): Long = sessionLastActive[sessionKey] ?: 0L

    internal suspend fun getLaunchTarget(): AxMediaLaunchTarget? =
        withContext(backgroundDispatcher) {
            val user = userRepository.getSelectedUserInfo()
            val packageName = readPackage(user.id) ?: return@withContext null
            val intent =
                context
                    .createContextAsUser(user.userHandle, 0)
                    .packageManager
                    .getLaunchIntentForPackage(packageName)
            if (intent == null) {
                clearPackage(user.id, packageName)
                return@withContext null
            }
            AxMediaLaunchTarget(
                intent = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                userHandle = user.userHandle,
            )
        }

    private fun readPackage(userId: Int): String? {
        return secureSettings
            .getStringForUser(SETTING_LAST_MEDIA_PACKAGE, userId)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun clearPackage(userId: Int, packageName: String) {
        savedPackages.remove(userId, packageName)
        writes.trySend(SaveRequest(userId, null))
    }

    private data class SaveRequest(val userId: Int, val packageName: String?)

    private companion object {
        const val SETTING_LAST_MEDIA_PACKAGE = "ax_qs_last_media_package"
    }
}

internal data class AxMediaLaunchTarget(val intent: Intent, val userHandle: UserHandle)
