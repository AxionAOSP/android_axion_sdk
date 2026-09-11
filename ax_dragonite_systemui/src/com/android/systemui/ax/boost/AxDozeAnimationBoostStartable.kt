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

import android.content.Context
import com.android.axion.dragonite.AxDragonite
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.statusbar.StatusBarStateController
import javax.inject.Inject

@SysUISingleton
class AxDozeAnimationBoostStartable @Inject constructor(
    private val context: Context,
    private val statusBarStateController: StatusBarStateController
) : CoreStartable, StatusBarStateController.StateListener {

    override fun start() {
        statusBarStateController.addCallback(this)
    }

    override fun onDozeAmountChanged(linear: Float, eased: Float) {
        if (linear > 0.0f && linear < 1.0f) {
            AxDragonite.onDozeTransition()
        } else {
            AxDragonite.onDozeTransitionEnd()
        }
    }

    override fun onDozingChanged(isDozing: Boolean) {
        if (!isDozing) {
            AxDragonite.onDozeTransitionEnd()
        }
    }
}
