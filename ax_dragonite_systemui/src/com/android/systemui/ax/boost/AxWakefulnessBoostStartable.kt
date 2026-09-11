package com.android.systemui.ax.boost

import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.WakefulnessLifecycle
import javax.inject.Inject

@SysUISingleton
class AxWakefulnessBoostStartable @Inject constructor(
    private val wakefulnessLifecycle: WakefulnessLifecycle
) : CoreStartable, WakefulnessLifecycle.Observer {

    override fun start() {
        wakefulnessLifecycle.addObserver(this)
    }

    override fun onStartedWakingUp() {
        AxDragonite.onWakeUp()
    }

    override fun onFinishedWakingUp() {
        AxDragonite.onWakeUpEnd()
    }
}
