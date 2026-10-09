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

import android.content.Context
import com.android.server.LocalServices
import com.android.server.SystemService

class AxDragoniteService(private val context: Context) : SystemService(context) {
    private val dragonite = AxDragoniteImpl(context)

    override fun onStart() {
        LocalServices.addService(IAxDragonite::class.java, dragonite)
        dragonite.onSystemReady(context, null)
    }

    override fun onBootPhase(phase: Int) {
        if (phase != PHASE_SYSTEM_SERVICES_READY) return
        dragonite.onSystemReady(context, null)
    }
}
