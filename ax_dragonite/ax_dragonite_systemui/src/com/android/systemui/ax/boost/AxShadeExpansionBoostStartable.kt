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
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.repository.KeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.shade.domain.interactor.ShadeAnimationInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@SysUISingleton
class AxShadeExpansionBoostStartable
@Inject
constructor(
    private val shadeInteractor: ShadeInteractor,
    private val shadeAnimationInteractor: ShadeAnimationInteractor,
    private val keyguardRepository: KeyguardRepository,
    private val boosterController: AxBoosterController,
    @Application private val applicationScope: CoroutineScope,
) : CoreStartable {

    override fun start() {
        val unlocked = keyguardRepository.statusBarState.map { it == StatusBarState.SHADE }
        val active =
            shadeInteractor.anyExpansion
                .map { it > 0.0f && it < 1.0f }
                .combine(unlocked) { active, isUnlocked -> active && isUnlocked }
        val intent =
            shadeInteractor.isAnyExpanded.combine(shadeInteractor.isAnyFullyExpanded.map { !it }) {
                open,
                belowTop ->
                open && belowTop
            }
        val animating =
            shadeAnimationInteractor.isAnyFlingAnimationRunning.combine(
                shadeAnimationInteractor.isAnyCloseAnimationRunning.combine(
                    shadeInteractor.isAnyExpanded
                ) { closing, open ->
                    closing && open
                }
            ) { flinging, closing ->
                flinging || closing
            }
        anyOf(active, intent, animating)
            .onEach { boosting ->
                if (boosting) {
                    boosterController.acquireNPVExpandingBoost()
                } else {
                    boosterController.releaseNPVExpandingBoost()
                }
            }
            .launchIn(applicationScope)
    }
}
