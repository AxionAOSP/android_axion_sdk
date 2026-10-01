/*
 * Copyright (C) 2026 AxionOS
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

package com.android.systemui.wallpapers

import android.app.WallpaperManager
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.android.systemui.CoreStartable
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.dump.DumpManager
import com.android.systemui.shared.clocks.DepthWallpaperProvider
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.wallpapers.data.repository.WallpaperRepository
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.EnumMap
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "AxWallpaperZoomController"
private const val DEBUG = false
private const val HISTORY_MAX_SIZE = 50
private const val DISABLE_WALLPAPER_ZOOM = "pref_disable_wallpaper_zoom"

@SysUISingleton
class AxWallpaperZoomController
@Inject
constructor(
    private val wallpaperManager: WallpaperManager,
    private val wallpaperRepository: WallpaperRepository,
    private val secureSettingsRepository: SecureSettingsRepository?,
    private val dumpManager: DumpManager?,
    @param:Application private val scope: CoroutineScope?,
    @param:Main private val mainDispatcher: CoroutineDispatcher?,
) : CoreStartable, Dumpable {

    constructor(
        wallpaperManager: WallpaperManager,
        wallpaperRepository: WallpaperRepository,
        dumpManager: DumpManager?,
    ) : this(wallpaperManager, wallpaperRepository, null, dumpManager, null, null)

    private data class OwnerState(
        val zoom: Float = 0f,
        val enabled: Boolean = true,
        val lastUpdateUptime: Long = 0L,
        val lastUpdateWallTime: Long = 0L,
        val reason: String = "init",
    )

    private data class ZoomHistoryEvent(
        val wallTime: Long,
        val owner: WallpaperZoomOwner,
        val oldZoom: Float,
        val newZoom: Float,
        val effectiveZoom: Float,
        val activeOwner: WallpaperZoomOwner?,
        val reason: String,
    )

    private val ownerStates = EnumMap<WallpaperZoomOwner, OwnerState>(WallpaperZoomOwner::class.java).apply {
        for (owner in WallpaperZoomOwner.entries) {
            put(owner, OwnerState(zoom = owner.restingZoom))
        }
    }

    private val historyLock = Any()
    private val history = ArrayDeque<ZoomHistoryEvent>(HISTORY_MAX_SIZE)

    @Volatile
    var currentEffectiveZoom: Float = 1.0f
        private set

    @Volatile
    var currentActiveOwner: WallpaperZoomOwner? = null
        private set

    private val handoffEngine = WallpaperZoomHandoffEngine {
        applyBlendedZoom(getCurrentRawTargetZoom(), "handoffTick")
    }

    @Volatile
    var launcherZoomEnabled: Boolean = true
        private set

    @Volatile
    var wallpaperZoomDisabled: Boolean = false
        private set

    private var rootView: View? = null

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            updateZoom("viewAttached")
        }
        override fun onViewDetachedFromWindow(v: View) {}
    }

    override fun start() {
        dumpManager?.registerNormalDumpable(TAG, this)
        wallpaperRepository.rootView?.let { onRootViewSet(it) }

        if (scope != null && mainDispatcher != null && secureSettingsRepository != null) {
            scope.launch(context = mainDispatcher) {
                secureSettingsRepository.boolSetting(DISABLE_WALLPAPER_ZOOM)
                    .distinctUntilChanged()
                    .collect { disabled ->
                        setWallpaperZoomDisabled(disabled)
                    }
            }
        }
    }

    fun onRootViewSet(view: View?) {
        if (rootView === view) return
        rootView?.removeOnAttachStateChangeListener(attachListener)
        rootView = view
        view?.addOnAttachStateChangeListener(attachListener)
        updateZoom("rootViewSet")
    }

    fun setNotificationShadeZoom(zoomOut: Float) {
        setZoom(WallpaperZoomOwner.NOTIFICATION_SHADE, zoomOut, "notificationShade")
    }

    fun setLauncherWallpaperZoom(ownerId: Int, zoomOut: Float) {
        val owner = WallpaperZoomOwner.fromId(ownerId) ?: WallpaperZoomOwner.APP_ZOOM
        setZoom(owner, zoomOut, "aidl:$ownerId")
    }

    fun setLauncherWallpaperZoom(zoomOut: Float) {
        setZoom(WallpaperZoomOwner.APP_ZOOM, zoomOut, "launcherAppZoom")
    }

    fun setZoom(owner: WallpaperZoomOwner, zoomOut: Float, reason: String = "") {
        if (wallpaperZoomDisabled) return
        val bounded = zoomOut.coerceIn(0f, 1f)
        val state = ownerStates[owner] ?: return
        val oldZoom = state.zoom
        if (oldZoom == bounded && state.reason == reason) return

        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        ownerStates[owner] = state.copy(
            zoom = bounded,
            lastUpdateUptime = nowUptime,
            lastUpdateWallTime = nowWall,
            reason = reason,
        )

        if (DEBUG) {
            Log.d(TAG, "setZoom: owner=$owner, zoom=$bounded (was $oldZoom), reason='$reason'")
        }
        val dispatchReason = if (reason.isNotEmpty()) "$owner:$reason" else "setZoom:$owner"
        updateZoom(reason = dispatchReason)
    }

    fun clearZoom(owner: WallpaperZoomOwner, reason: String = "") {
        setZoom(owner, owner.restingZoom, reason.ifEmpty { "clearZoom" })
    }

    fun getZoom(owner: WallpaperZoomOwner): Float {
        return ownerStates[owner]?.zoom ?: owner.restingZoom
    }

    fun getCurrentZoom(): Float = currentEffectiveZoom

    fun isOwnerActive(owner: WallpaperZoomOwner): Boolean {
        val state = ownerStates[owner] ?: return false
        return state.enabled && state.zoom != owner.restingZoom
    }

    fun resetAllZoom(reason: String = "resetAll") {
        handoffEngine.cancel()
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        for ((owner, state) in ownerStates) {
            ownerStates[owner] = state.copy(
                zoom = owner.restingZoom,
                reason = reason,
                lastUpdateUptime = nowUptime,
                lastUpdateWallTime = nowWall,
            )
        }
        updateZoom(reason)
    }

    fun clearLauncherZoom() {
        clearZoom(WallpaperZoomOwner.APP_ZOOM, "clearAppZoom")
        clearZoom(WallpaperZoomOwner.BASE_DEPTH, "clearBaseDepth")
    }

    fun setLauncherZoomEnabled(enabled: Boolean) {
        if (launcherZoomEnabled == enabled) return
        launcherZoomEnabled = enabled
        for (owner in listOf(WallpaperZoomOwner.APP_ZOOM, WallpaperZoomOwner.BASE_DEPTH)) {
            val current = ownerStates[owner] ?: continue
            ownerStates[owner] = current.copy(
                enabled = enabled,
                zoom = if (!enabled) owner.restingZoom else current.zoom,
                reason = if (!enabled) "launcherZoomDisabled" else current.reason,
            )
        }
        updateZoom("launcherZoomEnabled=$enabled")
    }

    fun setWallpaperZoomDisabled(disabled: Boolean) {
        if (wallpaperZoomDisabled == disabled) return
        if (DEBUG) Log.d(TAG, "setWallpaperZoomDisabled: disabled=$disabled")
        wallpaperZoomDisabled = disabled
        if (disabled) {
            resetAllZoom("wallpaperZoomDisabled")
        }
        updateZoom("wallpaperZoomDisabled=$disabled")
    }

    private fun reconcileStuckOwners(now: Long) {
        for ((owner, state) in ownerStates) {
            if (!state.enabled || state.zoom == owner.restingZoom) continue
            val elapsed = now - state.lastUpdateUptime
            if (elapsed > owner.maxHoldingDurationMs) {
                Log.e(
                    TAG,
                    "STUCK WALLPAPER ZOOM: ${owner.name} held zoom=${state.zoom} for ${elapsed}ms " +
                        "(max=${owner.maxHoldingDurationMs}ms, reason='${state.reason}'). Auto-recovering to resting ${owner.restingZoom}."
                )
                val oldZoom = state.zoom
                val autoReason = "auto-cleared stuck (${elapsed}ms)"
                ownerStates[owner] = state.copy(
                    zoom = owner.restingZoom,
                    reason = autoReason,
                    lastUpdateUptime = now,
                    lastUpdateWallTime = System.currentTimeMillis(),
                )
                recordHistoryEvent(
                    owner = owner,
                    oldZoom = oldZoom,
                    newZoom = owner.restingZoom,
                    effectiveZoom = owner.restingZoom,
                    activeOwner = null,
                    reason = autoReason,
                )
            }
        }
    }

    private data class ZoomResolution(
        val targetZoom: Float,
        val activeOwner: WallpaperZoomOwner?,
    )

    private fun getActiveZoom(owner: WallpaperZoomOwner): Float {
        val state = ownerStates[owner] ?: return owner.restingZoom
        return if (state.enabled) state.zoom else owner.restingZoom
    }

    private fun resolveTarget(): ZoomResolution {
        if (wallpaperZoomDisabled) return ZoomResolution(1.0f, null)

        val override = getActiveZoom(WallpaperZoomOwner.SYSTEM_OVERRIDE)
        if (override > 0f) return ZoomResolution((1.0f - override).coerceIn(0f, 1f), WallpaperZoomOwner.SYSTEM_OVERRIDE)

        if (!launcherZoomEnabled) {
            val keyguard = getActiveZoom(WallpaperZoomOwner.KEYGUARD_WAKE_ANIM)
            if (keyguard < 1.0f) return ZoomResolution(keyguard, WallpaperZoomOwner.KEYGUARD_WAKE_ANIM)
        }

        val shade = getActiveZoom(WallpaperZoomOwner.NOTIFICATION_SHADE)
        val unfold = getActiveZoom(WallpaperZoomOwner.UNFOLD)
        val overlay = maxOf(shade, unfold)

        val launcher = if (launcherZoomEnabled) {
            maxOf(getActiveZoom(WallpaperZoomOwner.APP_ZOOM), getActiveZoom(WallpaperZoomOwner.BASE_DEPTH))
        } else 0f

        val progress = maxOf(launcher, overlay)
        if (progress <= 0f) return ZoomResolution(1.0f, null)

        val owner = when {
            overlay > 0f -> if (shade >= unfold) WallpaperZoomOwner.NOTIFICATION_SHADE else WallpaperZoomOwner.UNFOLD
            else -> WallpaperZoomOwner.APP_ZOOM
        }
        return ZoomResolution((1.0f - progress).coerceIn(0f, 1f), owner)
    }

    private fun getCurrentRawTargetZoom(): Float = resolveTarget().targetZoom

    private fun updateZoom(reason: String = "") {
        reconcileStuckOwners(SystemClock.uptimeMillis())

        val resolution = resolveTarget()

        val previousOwner = currentActiveOwner
        if (previousOwner != resolution.activeOwner) {
            currentActiveOwner = resolution.activeOwner
            handoffEngine.onOwnerChanged(
                previousOwner = previousOwner,
                newOwner = resolution.activeOwner,
                currentEffectiveZoom = currentEffectiveZoom,
                newTargetZoom = resolution.targetZoom,
            )
        }

        applyBlendedZoom(resolution.targetZoom, reason)
    }

    private fun applyBlendedZoom(rawTarget: Float, reason: String) {
        val effectiveZoom = handoffEngine.blend(rawTarget)
        val previousEffectiveZoom = currentEffectiveZoom
        val zoomChanged = abs(previousEffectiveZoom - effectiveZoom) >= WallpaperZoomHandoffEngine.ZOOM_EPSILON

        if (zoomChanged || handoffEngine.isAnimating) {
            currentEffectiveZoom = effectiveZoom
            val lockscreenZoom = if (wallpaperZoomDisabled || launcherZoomEnabled) 1.0f else effectiveZoom
            DepthWallpaperProvider.setWallpaperZoom(lockscreenZoom)
            val applied = applyWallpaperZoom(effectiveZoom)

            if (DEBUG) {
                Log.d(
                    TAG,
                    "applyBlendedZoom: effectiveZoom=$effectiveZoom (raw=$rawTarget, offset=${handoffEngine.handoffOffset}), " +
                        "activeOwner=$currentActiveOwner, applied=$applied, disabled=$wallpaperZoomDisabled, reason='$reason'"
                )
            }

            recordHistoryEvent(
                owner = currentActiveOwner ?: WallpaperZoomOwner.SYSTEM_OVERRIDE,
                oldZoom = previousEffectiveZoom,
                newZoom = effectiveZoom,
                effectiveZoom = effectiveZoom,
                activeOwner = currentActiveOwner,
                reason = reason,
            )
        }
    }

    fun applyWallpaperZoom(zoomOut: Float): Boolean {
        val root = rootView ?: wallpaperRepository.rootView ?: return false
        val token = root.windowToken ?: run {
            Log.i(TAG, "Won't set zoom. Window not attached $root")
            return false
        }
        return try {
            wallpaperManager.setWallpaperZoomOut(token, zoomOut)
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Can't set zoom. Window is gone: $token", e)
            false
        }
    }

    private fun recordHistoryEvent(
        owner: WallpaperZoomOwner,
        oldZoom: Float,
        newZoom: Float,
        effectiveZoom: Float,
        activeOwner: WallpaperZoomOwner?,
        reason: String,
    ) {
        synchronized(historyLock) {
            if (history.size >= HISTORY_MAX_SIZE) {
                history.removeFirst()
            }
            history.addLast(
                ZoomHistoryEvent(
                    wallTime = System.currentTimeMillis(),
                    owner = owner,
                    oldZoom = oldZoom,
                    newZoom = newZoom,
                    effectiveZoom = effectiveZoom,
                    activeOwner = activeOwner,
                    reason = reason,
                )
            )
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        val nowUptime = SystemClock.uptimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        pw.println("AxWallpaperZoomController:")
        pw.println("  effectiveZoom=$currentEffectiveZoom (activeOwner=${currentActiveOwner ?: "NONE"})")
        pw.println("  handoffOffset=${handoffEngine.handoffOffset} (animating=${handoffEngine.isAnimating})")
        pw.println("  wallpaperZoomDisabled=$wallpaperZoomDisabled")
        pw.println("  launcherZoomEnabled=$launcherZoomEnabled")
        pw.println("  rootViewAttached=${rootView?.isAttachedToWindow == true} (token=${rootView?.windowToken})")

        dumpOwners(pw, nowUptime)
        dumpHistory(pw, dateFormat)
        DepthWallpaperProvider.dump(pw)
    }

    private fun dumpOwners(pw: PrintWriter, nowUptime: Long) {
        pw.println("  Owners State:")
        for (owner in WallpaperZoomOwner.entries) {
            val state = ownerStates[owner] ?: continue
            val elapsed = if (state.lastUpdateUptime > 0L) nowUptime - state.lastUpdateUptime else -1L
            val elapsedStr = if (elapsed >= 0L) "${elapsed}ms ago" else "never"
            val stuckWarning = if (state.enabled && state.zoom != owner.restingZoom && elapsed > owner.maxHoldingDurationMs) {
                " [STUCK! limit=${owner.maxHoldingDurationMs}ms]"
            } else {
                ""
            }
            pw.printf(
                Locale.US,
                "    %-20s [pri=%3d, enabled=%-5b]: zoom=%.3f (lastUpdate=%s, reason='%s')%s\n",
                owner.name,
                owner.priority,
                state.enabled,
                state.zoom,
                elapsedStr,
                state.reason,
                stuckWarning,
            )
        }
    }

    private fun dumpHistory(pw: PrintWriter, dateFormat: SimpleDateFormat) {
        pw.println("  Recent Zoom History (last $HISTORY_MAX_SIZE):")
        synchronized(historyLock) {
            if (history.isEmpty()) {
                pw.println("    (no events recorded)")
                return
            }
            for (event in history) {
                val timeStr = dateFormat.format(Date(event.wallTime))
                pw.printf(
                    Locale.US,
                    "    %s [%-18s] %.3f -> %.3f | eff=%.3f (owner=%-18s) reason='%s'\n",
                    timeStr,
                    event.owner.name,
                    event.oldZoom,
                    event.newZoom,
                    event.effectiveZoom,
                    event.activeOwner?.name ?: "NONE",
                    event.reason,
                )
            }
        }
    }
}
