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

package com.android.server.axdragonite

import android.os.PowerManagerInternal
import android.os.SystemProperties
import android.util.Slog
import com.android.server.LocalServices
import java.util.concurrent.atomic.AtomicInteger

class AxDragoniteExt {
    companion object {
        private const val TAG = "AxDragonite"
        private const val INVALID_HANDLE = 0
        private const val DURATION_INTERACTION_MS = 500
        private const val PROP_DEBUG = "persist.dragonite.debug"
    }

    private val debugEnabled = SystemProperties.getBoolean(PROP_DEBUG, false)
    private var powerManagerInternal: PowerManagerInternal? = null
    private val lock = Any()
    private val nextHandle = AtomicInteger(1)
    private val sessions = mutableMapOf<Int, List<Int>>()
    private val refCounts = mutableMapOf<Int, Int>()
    private val axSupported = mutableMapOf<Int, Boolean>()

    fun init() {
        powerManagerInternal = LocalServices.getService(PowerManagerInternal::class.java)
    }

    private fun logDebug(msg: String) {
        if (debugEnabled) Slog.d(TAG, msg)
    }

    private fun newHandle(): Int =
        nextHandle.getAndUpdate { if (it == Int.MAX_VALUE) 1 else it + 1 }

    private fun hold(pm: PowerManagerInternal, mode: Int, checked: Boolean): Boolean {
        if (checked && axSupported[mode] == false) return false
        val count = refCounts.getOrDefault(mode, 0) + 1
        refCounts[mode] = count
        if (count > 1) return true
        val ok = pm.setPowerModeChecked(mode, true)
        if (checked) axSupported[mode] = ok
        if (!ok) {
            refCounts.remove(mode)
            return checked
        }
        return true
    }

    private fun release(pm: PowerManagerInternal, mode: Int) {
        val count = refCounts.getOrDefault(mode, 1) - 1
        if (count <= 0) {
            refCounts.remove(mode)
            pm.setPowerModeChecked(mode, false)
            return
        }
        refCounts[mode] = count
    }

    fun acquireBoost(spec: String?): Int {
        val hints =
            spec?.takeIf { it.isNotEmpty() }?.let { AxSceneTable.getPowerHints(it) }
                ?: return INVALID_HANDLE
        synchronized(lock) {
            val pm =
                powerManagerInternal
                    ?: return INVALID_HANDLE.also {
                        logDebug("power manager unavailable, skip boost")
                    }
            val handle = newHandle()
            if (hold(pm, hints.axPowerMode, true)) {
                sessions[handle] = listOf(hints.axPowerMode)
                logDebug("ax mode ${hints.axPowerMode} acquired for $spec")
                return handle
            }
            logDebug("ax mode ${hints.axPowerMode} unsupported, legacy fallback")
            hints.fallbackBoosts.forEach { pm.setPowerBoost(it, DURATION_INTERACTION_MS) }
            sessions[handle] =
                hints.fallbackModes.mapNotNull { m -> if (hold(pm, m, false)) m else null }
            return handle
        }
    }

    fun releaseBoost(handle: Int) {
        synchronized(lock) {
            val modes = sessions.remove(handle) ?: return
            val pm = powerManagerInternal ?: return
            modes.forEach { release(pm, it) }
        }
    }
}
