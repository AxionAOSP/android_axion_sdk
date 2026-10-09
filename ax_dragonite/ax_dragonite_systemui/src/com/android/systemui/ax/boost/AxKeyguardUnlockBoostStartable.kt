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
import com.android.systemui.keyguard.KeyguardUnlockAnimationController
import com.android.systemui.statusbar.policy.KeyguardStateController
import javax.inject.Inject

@SysUISingleton
class AxKeyguardUnlockBoostStartable
@Inject
constructor(
    private val keyguardUnlockAnimationController: KeyguardUnlockAnimationController,
    private val keyguardStateController: KeyguardStateController,
    private val boosterController: AxBoosterController,
) :
    CoreStartable,
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
        unlockAnimationDuration: Long,
    ) {
        boosterController.acquireUnlockAnimationBoost()
    }

    override fun onUnlockAnimationFinished() {
        boosterController.releaseUnlockBoost()
    }

    override fun onKeyguardGoingAwayChanged() {
        if (keyguardStateController.isKeyguardGoingAway) {
            boosterController.acquireUnlockAnimationBoost()
        } else {
            boosterController.releaseKeyguardGoneAnimationBoost()
        }
    }
}
