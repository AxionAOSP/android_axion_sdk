package com.android.systemui.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.media.NotificationMediaManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SysUISingleton
class MediaSessionControllerManager
@Inject
constructor(
    @Application private val context: Context,
) {
    interface Callback {
        fun onPlaybackStateChanged(key: String, state: PlaybackState?)
        fun onMetadataChanged(key: String)
        fun onSessionDestroyed(key: String)
    }

    private val controllers = ConcurrentHashMap<String, MediaController>()
    private val callbacks = ConcurrentHashMap<String, MediaController.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun bind(key: String, token: MediaSession.Token?, listener: Callback) {
        if (token == null) return
        val currentController = controllers[key]
        if (currentController?.sessionToken == token) return

        unbind(key)

        val controller = MediaController(context, token)
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                listener.onPlaybackStateChanged(key, state)
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                listener.onMetadataChanged(key)
            }

            override fun onSessionDestroyed() {
                listener.onSessionDestroyed(key)
            }
        }

        controller.registerCallback(callback, mainHandler)
        controllers[key] = controller
        callbacks[key] = callback
    }

    fun unbind(key: String) {
        val callback = callbacks.remove(key)
        val controller = controllers.remove(key)
        if (callback != null && controller != null) {
            controller.unregisterCallback(callback)
        }
    }

    fun clear() {
        controllers.forEach { (key, controller) ->
            val callback = callbacks.remove(key)
            if (callback != null) {
                controller.unregisterCallback(callback)
            }
        }
        controllers.clear()
        callbacks.clear()
    }

    fun get(key: String): MediaController? = controllers[key]

    fun togglePlayPause(controller: MediaController?) {
        if (controller == null) return
        val playing = NotificationMediaManager.isPlayingState(controller.playbackState?.state ?: 0)
        if (playing) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun play(controller: MediaController?) {
        controller?.transportControls?.play()
    }

    fun pause(controller: MediaController?) {
        controller?.transportControls?.pause()
    }

    fun skipNext(controller: MediaController?) {
        controller?.transportControls?.skipToNext()
    }

    fun skipPrev(controller: MediaController?) {
        controller?.transportControls?.skipToPrevious()
    }

    fun seekTo(controller: MediaController?, positionMs: Long) {
        controller?.transportControls?.seekTo(positionMs)
    }

    fun sendCustomAction(controller: MediaController?, action: String, extras: Bundle?) {
        controller?.transportControls?.sendCustomAction(action, extras)
    }
}
