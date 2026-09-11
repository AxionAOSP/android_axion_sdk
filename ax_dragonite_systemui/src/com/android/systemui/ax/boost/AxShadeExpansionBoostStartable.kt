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

import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shade.ShadeExpansionChangeEvent
import com.android.systemui.shade.ShadeExpansionListener
import com.android.systemui.shade.ShadeExpansionStateManager
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SysUISingleton
class AxShadeExpansionBoostStartable @Inject constructor(
    private val shadeExpansionStateManager: ShadeExpansionStateManager,
    private val shadeInteractor: ShadeInteractor,
    @Application private val applicationScope: CoroutineScope
) : CoreStartable, ShadeExpansionListener {

    private var isShadeActive = false
    private var isQsActive = false

    override fun start() {
        shadeExpansionStateManager.addExpansionListener(this)
        applicationScope.launch {
            shadeInteractor.qsExpansion.collect { qsFraction ->
                val active = qsFraction > 0.0f && qsFraction < 1.0f
                if (active != isQsActive) {
                    isQsActive = active
                    updateBoostState()
                }
            }
        }
    }

    override fun onPanelExpansionChanged(event: ShadeExpansionChangeEvent) {
        val fraction = event.fraction
        val active = fraction > 0.0f && fraction < 1.0f
        if (active != isShadeActive) {
            isShadeActive = active
            updateBoostState()
        }
    }

    private fun updateBoostState() {
        if (isShadeActive || isQsActive) {
            AxDragonite.onShadeExpand()
        } else {
            AxDragonite.onShadeCollapse()
        }
    }
}
