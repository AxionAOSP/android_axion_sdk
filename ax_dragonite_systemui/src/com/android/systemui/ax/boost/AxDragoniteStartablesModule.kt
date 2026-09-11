package com.android.systemui.ax.boost

import com.android.systemui.CoreStartable
import com.android.systemui.ax.boost.AxBiometricAuthBoostStartable
import com.android.systemui.ax.boost.AxDozeAnimationBoostStartable
import com.android.systemui.ax.boost.AxKeyguardUnlockBoostStartable
import com.android.systemui.ax.boost.AxLightRevealScrimStartable
import com.android.systemui.ax.boost.AxShadeExpansionBoostStartable
import com.android.systemui.ax.boost.AxVolumeDialogBoostStartable
import com.android.systemui.ax.boost.AxWakefulnessBoostStartable
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
abstract class AxDragoniteStartablesModule {
    @Binds
    @IntoMap
    @ClassKey(AxShadeExpansionBoostStartable::class)
    abstract fun bindAxShadeExpansionBoostStartable(impl: AxShadeExpansionBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxDozeAnimationBoostStartable::class)
    abstract fun bindAxDozeAnimationBoostStartable(impl: AxDozeAnimationBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxVolumeDialogBoostStartable::class)
    abstract fun bindAxVolumeDialogBoostStartable(impl: AxVolumeDialogBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxWakefulnessBoostStartable::class)
    abstract fun bindAxWakefulnessBoostStartable(impl: AxWakefulnessBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxKeyguardUnlockBoostStartable::class)
    abstract fun bindAxKeyguardUnlockBoostStartable(impl: AxKeyguardUnlockBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxBiometricAuthBoostStartable::class)
    abstract fun bindAxBiometricAuthBoostStartable(impl: AxBiometricAuthBoostStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxLightRevealScrimStartable::class)
    abstract fun bindAxLightRevealScrimStartable(impl: AxLightRevealScrimStartable): CoreStartable
}
