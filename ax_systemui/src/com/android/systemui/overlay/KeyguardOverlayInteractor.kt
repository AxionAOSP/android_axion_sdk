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

package com.android.systemui.overlay

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow

private const val SHADE_COLLAPSED_EXPANSION_THRESHOLD = 0.1f

private val INACTIVE_KEYGUARD_TARGETS = setOf(
    KeyguardState.GONE,
    KeyguardState.OCCLUDED,
    KeyguardState.PRIMARY_BOUNCER,
    KeyguardState.ALTERNATE_BOUNCER,
)

@SysUISingleton
class KeyguardOverlayInteractor @Inject constructor(
    private val keyguardInteractor: KeyguardInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val keyguardOcclusionInteractor: KeyguardOcclusionInteractor,
    private val shadeInteractor: ShadeInteractor,
) {

    val isShadeCollapsed: Flow<Boolean> = combine(
        shadeInteractor.isQsExpanded,
        shadeInteractor.anyExpansion,
        shadeInteractor.isAnyFullyExpanded
    ) { isQs, expansion, isFullyExpanded ->
        !isQs && !isFullyExpanded && expansion <= SHADE_COLLAPSED_EXPANSION_THRESHOLD
    }.distinctUntilChanged()

    val isBiometricDismissing: Flow<Boolean> = combine(
        keyguardInteractor.biometricUnlockState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep
    ) { biometric, step ->
        BiometricUnlockMode.dismissesKeyguard(biometric.mode) || step.to == KeyguardState.GONE
    }.distinctUntilChanged()

    val unlockAlpha: Flow<Float> = combine(
        keyguardTransitionInteractor.transitionValue(KeyguardState.GONE),
        isBiometricDismissing
    ) { progress, isDismissing ->
        if (isDismissing) 0f else (1f - (progress * 2f)).coerceIn(0f, 1f)
    }.distinctUntilChanged()

    val isDozing: Flow<Boolean> = keyguardInteractor.isDozing

    val isPulsing: Flow<Boolean> = keyguardInteractor.isPulsing

    val isAmbientOverlayActive: Flow<Boolean> = isKeyguardActive(allowDozing = true)

    val isPulsingOverlayActive: Flow<Boolean> = isKeyguardActive(allowDozingWhenPulsing = true)

    fun isKeyguardActive(
        allowDozing: Boolean = false,
        allowDozingWhenPulsing: Boolean = false
    ): Flow<Boolean> = combine(
        keyguardTransitionInteractor.currentKeyguardState,
        keyguardTransitionInteractor.startedKeyguardTransitionStep,
        keyguardOcclusionInteractor.isKeyguardOccluded,
        keyguardInteractor.isPulsing,
        isBiometricDismissing
    ) { currentKeyguard, startedStep, isOccluded, isPulsing, isDismissing ->
        if (isDismissing || isOccluded) return@combine false
        if (startedStep.to in INACTIVE_KEYGUARD_TARGETS) return@combine false
        when (currentKeyguard) {
            KeyguardState.LOCKSCREEN, KeyguardState.AOD -> true
            KeyguardState.DOZING -> when {
                allowDozing -> true
                allowDozingWhenPulsing -> isPulsing
                else -> false
            }
            else -> false
        }
    }.distinctUntilChanged()
}
