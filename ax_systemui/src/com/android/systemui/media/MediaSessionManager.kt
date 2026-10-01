/*
 * Copyright (C) 2025 The AxionAOSP Project
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

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SysUISingleton
class MediaSessionManager
@Inject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Main private val mainDispatcher: CoroutineDispatcher,
    private val notificationMediaManager: NotificationMediaManager,
    private val mediaDataManager: MediaDataManager,
    private val controllerManager: MediaSessionControllerManager,
    private val dumpManager: DumpManager,
) : MediaDataManager.Listener, Dumpable, CoreStartable {

    companion object {
        private const val TAG = "MediaSessionManager"
    }

    private val rawEntries = ConcurrentHashMap<String, MediaData>()
    private val decodingJobs = ConcurrentHashMap<String, Job>()
    private val resolvedSessions = ConcurrentHashMap<String, ResolvedMediaSession>()
    private val listeners = CopyOnWriteArrayList<MediaDataListener>()

    private val controllerCallback = object : MediaSessionControllerManager.Callback {
        override fun onPlaybackStateChanged(key: String, state: PlaybackState?) {
            applicationScope.launch(backgroundDispatcher) {
                updatePlaybackStateForSession(key, state)
            }
        }

        override fun onMetadataChanged(key: String) {
            applicationScope.launch(backgroundDispatcher) {
                val entry = rawEntries[key] ?: return@launch
                scheduleSessionResolution(key, entry)
            }
        }

        override fun onSessionDestroyed(key: String) {
            applicationScope.launch(mainDispatcher) {
                removeSessionInternal(key)
                publishState()
            }
        }
    }

    private val _mediaSessionsState = MutableStateFlow(MediaSessionsState())
    val mediaSessionsState: StateFlow<MediaSessionsState> = _mediaSessionsState.asStateFlow()

    val activeSession: StateFlow<ResolvedMediaSession?> =
        _mediaSessionsState
            .map { it.activeSession }
            .stateIn(applicationScope, SharingStarted.Eagerly, null)

    val allSessions: StateFlow<List<ResolvedMediaSession>> =
        _mediaSessionsState
            .map { it.sessions }
            .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    val isPlaying: StateFlow<Boolean> =
        _mediaSessionsState
            .map { it.hasPlayingMedia }
            .stateIn(applicationScope, SharingStarted.Eagerly, false)

    val isMediaPlaying: Boolean
        get() = isPlaying.value

    val currentSession: ResolvedMediaSession
        get() = activeSession.value ?: ResolvedMediaSession()

    val activeSessions: List<ResolvedMediaSession>
        get() = allSessions.value

    private val sessionComparator = compareByDescending<ResolvedMediaSession> { it.isPlaying }
        .thenByDescending { !it.isResumption }
        .thenByDescending { it.lastActiveTime }
        .thenBy { it.key }

    override fun start() {
        dumpManager.registerNormalDumpable(TAG, this)
        mediaDataManager.addListener(this)
    }

    fun stop() {
        mediaDataManager.removeListener(this)
        dumpManager.unregisterDumpable(TAG)
        decodingJobs.values.forEach { it.cancel() }
        decodingJobs.clear()
        controllerManager.clear()
        rawEntries.clear()
        resolvedSessions.clear()
        _mediaSessionsState.value = MediaSessionsState()
    }

    fun addListener(listener: MediaDataListener) {
        if (listeners.contains(listener)) return
        listeners.add(listener)
        val session = activeSession.value ?: return
        listener.onSessionUpdated(session)
        listener.onPlaybackStateChanged(session.playbackState)
        listener.onMetadataChanged(session.track, session.artist)
        listener.onPackageChanged(session.packageName)
        listener.onAlbumArtChanged(session.albumArt)
        listener.onAppIconChanged(session.appIcon)
        listener.onMediaColorsChanged(session.mediaColor)
    }

    fun removeListener(listener: MediaDataListener) {
        listeners.remove(listener)
    }

    fun getSession(key: String): ResolvedMediaSession? =
        resolvedSessions[key]

    fun getActiveController(): MediaController? =
        activeSession.value?.controller

    fun getActivePackageName(): String? =
        activeSession.value?.packageName

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean,
    ) {
        if (oldKey != null && oldKey != key) {
            removeSessionInternal(oldKey)
        }
        rawEntries[key] = data
        controllerManager.bind(key, data.token, controllerCallback)
        scheduleSessionResolution(key, data)
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        removeSessionInternal(key)
        publishState()
    }

    private fun scheduleSessionResolution(key: String, data: MediaData) {
        decodingJobs[key]?.cancel()
        decodingJobs[key] = applicationScope.launch(backgroundDispatcher) {
            val resolved = resolveSession(key, data)
            resolvedSessions[key] = resolved
            publishState()
        }
    }

    private suspend fun resolveSession(key: String, data: MediaData): ResolvedMediaSession =
        withContext(backgroundDispatcher) {
            val controller = controllerManager.get(key)
            val metadata = controller?.metadata
            val playbackState = controller?.playbackState

            val track = resolveTrack(data, metadata)
            val artist = resolveArtist(data, metadata)
            val appName = resolveAppName(data)
            val appIcon = resolveAppIcon(data)
            val albumArt = resolveAlbumArt(data, metadata)
            val mediaColor = albumArt?.let { MediaSessionColorExtractor.extractColor(it) }
            val isPlaying = NotificationMediaManager.isPlayingState(playbackState?.state ?: 0) || data.isPlaying == true
            val customActions = playbackState?.customActions ?: emptyList()

            ResolvedMediaSession(
                key = key,
                packageName = data.packageName,
                appName = appName,
                appIcon = appIcon,
                track = track,
                artist = artist,
                albumArt = albumArt,
                mediaColor = mediaColor,
                playbackState = playbackState,
                token = data.token,
                controller = controller,
                customActions = customActions,
                outputDevice = data.device,
                clickIntent = data.clickIntent,
                playbackLocation = data.playbackLocation,
                isPlaying = isPlaying,
                isResumption = data.resumption,
                lastActiveTime = data.lastActive,
            )
        }

    private fun resolveTrack(data: MediaData, metadata: MediaMetadata?): String =
        data.song?.toString()?.ifEmpty { null }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: ""

    private fun resolveArtist(data: MediaData, metadata: MediaMetadata?): String =
        data.artist?.toString()?.ifEmpty { null }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: ""

    private fun resolveAppName(data: MediaData): String {
        val app = data.app
        if (app != null) return app
        return try {
            val appInfo = context.packageManager.getApplicationInfo(data.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            data.packageName
        }
    }

    private fun resolveAppIcon(data: MediaData): Drawable? {
        val customIcon = data.appIcon?.loadDrawable(context)
        if (customIcon != null) return customIcon
        return try {
            context.packageManager.getApplicationIcon(data.packageName)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveAlbumArt(data: MediaData, metadata: MediaMetadata?): Drawable? {
        val artDrawable = data.artwork?.loadDrawable(context)
        if (artDrawable != null) return artDrawable
        val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (albumArtBitmap != null) return BitmapDrawable(context.resources, albumArtBitmap)
        val artBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (artBitmap != null) return BitmapDrawable(context.resources, artBitmap)
        return null
    }

    private fun updatePlaybackStateForSession(key: String, state: PlaybackState?) {
        val existing = resolvedSessions[key] ?: return
        val isPlaying = NotificationMediaManager.isPlayingState(state?.state ?: 0)
        val customActions = state?.customActions ?: existing.customActions

        val updated = existing.copy(
            playbackState = state,
            isPlaying = isPlaying,
            customActions = customActions,
        )
        resolvedSessions[key] = updated
        publishState()
    }

    private fun removeSessionInternal(key: String) {
        rawEntries.remove(key)
        decodingJobs.remove(key)?.cancel()
        controllerManager.unbind(key)
        resolvedSessions.remove(key)
    }

    private fun publishState() {
        val sorted = resolvedSessions.values.sortedWith(sessionComparator)
        val active = sorted.firstOrNull()
        val hasPlaying = sorted.any { it.isPlaying }

        val newState = MediaSessionsState(
            activeSession = active,
            sessions = sorted,
            hasPlayingMedia = hasPlaying,
        )
        _mediaSessionsState.value = newState
        dispatchLegacyCallbacks(active)
    }

    private fun dispatchLegacyCallbacks(active: ResolvedMediaSession?) {
        if (listeners.isEmpty()) return
        applicationScope.launch(mainDispatcher) {
            notifyListeners(active)
        }
    }

    private fun notifyListeners(active: ResolvedMediaSession?) {
        if (active == null) {
            listeners.forEach { it.onMediaSessionLost() }
            return
        }
        listeners.forEach { listener ->
            listener.onSessionUpdated(active)
            listener.onPlaybackStateChanged(active.playbackState)
            listener.onMetadataChanged(active.track, active.artist)
            listener.onPackageChanged(active.packageName)
            listener.onAlbumArtChanged(active.albumArt)
            listener.onAppIconChanged(active.appIcon)
            listener.onMediaColorsChanged(active.mediaColor)
        }
    }

    private fun targetSession(key: String?): ResolvedMediaSession? =
        if (key != null) {
            resolvedSessions[key]
        } else {
            activeSession.value
        }

    fun togglePlayPause(key: String? = null) {
        controllerManager.togglePlayPause(targetSession(key)?.controller)
    }

    fun play(key: String? = null) {
        controllerManager.play(targetSession(key)?.controller)
    }

    fun pause(key: String? = null) {
        controllerManager.pause(targetSession(key)?.controller)
    }

    fun skipNext(key: String? = null) {
        controllerManager.skipNext(targetSession(key)?.controller)
    }

    fun skipPrev(key: String? = null) {
        controllerManager.skipPrev(targetSession(key)?.controller)
    }

    fun seekTo(positionMs: Long, key: String? = null) {
        controllerManager.seekTo(targetSession(key)?.controller, positionMs)
    }

    fun sendCustomAction(action: String, extras: Bundle? = null, key: String? = null) {
        controllerManager.sendCustomAction(targetSession(key)?.controller, action, extras)
    }

    fun getMediaAppIntent(key: String? = null): Intent? {
        val pkg = targetSession(key)?.packageName ?: return null
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun openMediaApp(key: String? = null) {
        val session = targetSession(key) ?: return
        val pendingIntent = session.clickIntent
        if (pendingIntent != null) {
            try {
                pendingIntent.send()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send clickIntent for media app", e)
            }
        }
        val intent = getMediaAppIntent(key) ?: return
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open media app", e)
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("MediaSessionManager:")
        pw.println("  hasPlayingMedia: ${isPlaying.value}")
        pw.println("  activeSession: ${activeSession.value?.packageName} - ${activeSession.value?.track}")
        pw.println("  totalResolvedSessions: ${resolvedSessions.size}")
        resolvedSessions.values.forEach { session ->
            pw.println("    [${session.key}] pkg=${session.packageName} track=${session.track} artist=${session.artist} playing=${session.isPlaying}")
        }
    }
}
