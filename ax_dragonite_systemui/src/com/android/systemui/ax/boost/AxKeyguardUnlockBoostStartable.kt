package com.android.systemui.ax.boost

import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import javax.inject.Inject

@SysUISingleton
class AxKeyguardUnlockBoostStartable @Inject constructor(
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardStateController: KeyguardStateController
) : CoreStartable,
    KeyguardUnlockAnimationController.KeyguardUnlockAnimationListener,
    KeyguardStateController.Callback {

    override fun start() {
        keyguardUnlockAnimationController.addKeyguardUnlockAnimationListener(this)
        keyguardStateController.addCallback(this)
    }

    override fun onUnlockAnimationStarted(
        playingCannedAnimation: Boolean,
        isWakeAndUnlockNotFromDream: Boolean,
        unlockAnimationStartDelay: Long,
        unlockAnimationDuration: Long
    ) {
        AxDragonite.onUnlock()
    }

    override fun onUnlockAnimationFinished() {
        AxDragonite.onUnlockEnd()
    }

    override fun onKeyguardGoingAwayChanged() {
        if (keyguardStateController.isKeyguardGoingAway) {
            AxDragonite.onKeyguardDismiss()
        } else {
            AxDragonite.onKeyguardDismissEnd()
        }
    }
}
