package com.android.systemui.ax.boost

import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.phone.BiometricUnlockController
import javax.inject.Inject

@SysUISingleton
class AxBiometricAuthBoostStartable @Inject constructor(
    private val biometricUnlockController: BiometricUnlockController
) : CoreStartable, BiometricUnlockController.BiometricUnlockEventsListener {

    override fun start() {
        biometricUnlockController.addListener(this)
    }

    override fun onModeChanged(mode: Int) {
        if (mode != BiometricUnlockController.MODE_NONE) {
            AxDragonite.onBiometricAuth()
        }
    }
}
