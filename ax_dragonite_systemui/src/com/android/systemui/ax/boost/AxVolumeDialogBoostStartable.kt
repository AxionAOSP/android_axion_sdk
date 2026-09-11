package com.android.systemui.ax.boost

import android.os.Handler
import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.VolumeDialogController
import javax.inject.Inject

@SysUISingleton
class AxVolumeDialogBoostStartable @Inject constructor(
    private val volumeDialogController: VolumeDialogController,
    @Main private val mainHandler: Handler
) : CoreStartable, VolumeDialogController.Callbacks {

    override fun start() {
        volumeDialogController.addCallback(this, mainHandler)
    }

    override fun onShowRequested(reason: Int, keyguardLocked: Boolean, lockTaskModeState: Int) {
        AxDragonite.onVolumeDialog()
    }

    override fun onDismissRequested(reason: Int) {
        AxDragonite.onVolumeDialogEnd()
    }

    override fun onStateChanged(state: VolumeDialogController.State?) {}
    override fun onLayoutDirectionChanged(layoutDirection: Int) {}
    override fun onConfigurationChanged() {}
    override fun onShowVibrateHint() {}
    override fun onShowSilentHint() {}
    override fun onScreenOff() {}
    override fun onShowSafetyWarning(flags: Int) {}
    override fun onAccessibilityModeChanged(showA11yStream: Boolean?) {}
    override fun onCaptionComponentStateChanged(isComponentEnabled: Boolean?, fromTooltip: Boolean?) {}
    override fun onCaptionEnabledStateChanged(isEnabled: Boolean?, checkBeforeSwitch: Boolean?) {}
    override fun onShowCsdWarning(csdWarning: Int, durationMs: Int) {}
    override fun onVolumeChangedFromKey() {}
}
