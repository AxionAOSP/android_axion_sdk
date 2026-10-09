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

import com.android.systemui.CoreStartable
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
abstract class AxDragoniteStartablesModule {
    @Binds
    @IntoMap
    @ClassKey(AxShadeExpansionBoostStartable::class)
    abstract fun bindAxShadeExpansionBoostStartable(
        impl: AxShadeExpansionBoostStartable
    ): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxDozeAnimationBoostStartable::class)
    abstract fun bindAxDozeAnimationBoostStartable(
        impl: AxDozeAnimationBoostStartable
    ): CoreStartable

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
    abstract fun bindAxKeyguardUnlockBoostStartable(
        impl: AxKeyguardUnlockBoostStartable
    ): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxBiometricAuthBoostStartable::class)
    abstract fun bindAxBiometricAuthBoostStartable(
        impl: AxBiometricAuthBoostStartable
    ): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxLightRevealScrimStartable::class)
    abstract fun bindAxLightRevealScrimStartable(impl: AxLightRevealScrimStartable): CoreStartable

    @Binds
    @IntoMap
    @ClassKey(AxShadeTrackingBoostStartable::class)
    abstract fun bindAxShadeTrackingBoostStartable(
        impl: AxShadeTrackingBoostStartable
    ): CoreStartable
}
