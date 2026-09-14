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

package com.android.systemui.shade

import android.provider.Settings.Secure.DOUBLE_TAP_TO_SLEEP
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shade.Dt2sMode
import com.android.systemui.shade.Dt2sType
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class Dt2sType {
    STATUS_BAR,
    LOCKSCREEN;
}

enum class Dt2sMode(val value: Int) {
    DISABLED(0),
    STATUS_BAR(1),
    LOCKSCREEN(2),
    ALL(3);

    fun isEnabled(type: Dt2sType): Boolean =
        when (type) {
            Dt2sType.STATUS_BAR -> this == STATUS_BAR || this == ALL
            Dt2sType.LOCKSCREEN -> this == LOCKSCREEN || this == ALL
        }

    companion object {
        fun fromValue(value: Int): Dt2sMode = entries.firstOrNull { it.value == value } ?: ALL
    }
}

@SysUISingleton
class Dt2sRepository @Inject constructor(
    @Application scope: CoroutineScope,
    secureSettingsRepository: SecureSettingsRepository,
) {
    val mode: StateFlow<Dt2sMode> =
        secureSettingsRepository
            .intSetting(DOUBLE_TAP_TO_SLEEP, Dt2sMode.ALL.value)
            .map { Dt2sMode.fromValue(it) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = Dt2sMode.ALL,
            )

    fun isEnabled(type: Dt2sType): Boolean = mode.value.isEnabled(type)
}
