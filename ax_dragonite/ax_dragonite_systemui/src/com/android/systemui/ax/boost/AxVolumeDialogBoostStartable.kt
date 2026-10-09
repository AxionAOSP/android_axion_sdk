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

package com.android.systemui.ax.boost

import android.os.Handler
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.VolumeDialogController
import javax.inject.Inject

@SysUISingleton
class AxVolumeDialogBoostStartable
@Inject
constructor(
    private val volumeDialogController: VolumeDialogController,
    private val boosterController: AxBoosterController,
    @Main private val mainHandler: Handler,
) : CoreStartable, VolumeDialogController.Callbacks {

    override fun start() {
        volumeDialogController.addCallback(this, mainHandler)
    }

    override fun onShowRequested(reason: Int, keyguardLocked: Boolean, lockTaskModeState: Int) {
        boosterController.acquireVolumeDialogShowBoost()
    }

    override fun onDismissRequested(reason: Int) {}

    override fun onStateChanged(state: VolumeDialogController.State?) {}

    override fun onLayoutDirectionChanged(layoutDirection: Int) {}

    override fun onConfigurationChanged() {}

    override fun onShowVibrateHint() {}

    override fun onShowSilentHint() {}

    override fun onScreenOff() {}

    override fun onShowSafetyWarning(flags: Int) {}

    override fun onAccessibilityModeChanged(showA11yStream: Boolean?) {}

    override fun onCaptionComponentStateChanged(
        isComponentEnabled: Boolean?,
        fromTooltip: Boolean?,
    ) {}

    override fun onCaptionEnabledStateChanged(isEnabled: Boolean?, checkBeforeSwitch: Boolean?) {}

    override fun onShowCsdWarning(csdWarning: Int, durationMs: Int) {}

    override fun onVolumeChangedFromKey() {}
}
