package com.android.systemui.media

import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.android.systemui.media.controls.shared.model.MediaDeviceData

data class ResolvedMediaSession(
    val key: String = "",
    val packageName: String = "",
    val appName: String = "",
    val appIcon: Drawable? = null,
    val track: String = "",
    val artist: String = "",
    val albumArt: Drawable? = null,
    val mediaColor: Int? = null,
    val playbackState: PlaybackState? = null,
    val token: MediaSession.Token? = null,
    val controller: MediaController? = null,
    val customActions: List<PlaybackState.CustomAction> = emptyList(),
    val outputDevice: MediaDeviceData? = null,
    val clickIntent: PendingIntent? = null,
    val playbackLocation: Int = 0,
    val isPlaying: Boolean = false,
    val isResumption: Boolean = false,
    val lastActiveTime: Long = 0L,
)

data class MediaSessionsState(
    val activeSession: ResolvedMediaSession? = null,
    val sessions: List<ResolvedMediaSession> = emptyList(),
    val hasPlayingMedia: Boolean = false,
)

interface MediaDataListener {
    fun onPlaybackStateChanged(state: PlaybackState?) {}
    fun onAlbumArtChanged(drawable: Drawable?) {}
    fun onAppIconChanged(drawable: Drawable?) {}
    fun onMediaColorsChanged(color: Int?) {}
    fun onMetadataChanged(track: String, artist: String) {}
    fun onPackageChanged(packageName: String) {}
    fun onMediaSessionLost() {}
    fun onSessionUpdated(session: ResolvedMediaSession) {}
}
