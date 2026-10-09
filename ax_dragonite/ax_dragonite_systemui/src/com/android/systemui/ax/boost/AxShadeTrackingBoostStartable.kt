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
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@SysUISingleton
class AxShadeTrackingBoostStartable
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val shadeInteractor: ShadeInteractor,
    private val headsUpManager: HeadsUpManager,
    private val boosterController: AxBoosterController,
) : CoreStartable {

    override fun start() {
        anyOf(shadeInteractor.isUserInteracting, headsUpManager.isTrackingHeadsUp())
            .onEach { boosting ->
                if (boosting) {
                    boosterController.acquireNPVTrackingBoost()
                } else {
                    boosterController.releaseNPVTrackingBoost()
                }
            }
            .launchIn(scope)
    }
}
