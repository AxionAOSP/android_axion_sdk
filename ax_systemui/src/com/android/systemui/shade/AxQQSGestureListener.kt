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

package com.android.systemui.shade

import android.content.Context
import android.os.PowerManager
import android.view.GestureDetector
import android.view.MotionEvent
import com.android.internal.R as InternalR
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.mistouch.domain.interactor.MistouchInteractor
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.shade.Dt2sRepository
import com.android.systemui.shade.Dt2sType
import javax.inject.Inject

@SysUISingleton
class AxQQSGestureListener @Inject constructor(
    private val context: Context,
    private val falsingManager: FalsingManager,
    private val powerManager: PowerManager,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val powerInteractor: PowerInteractor,
    private val dt2sRepository: Dt2sRepository,
) : GestureDetector.SimpleOnGestureListener() {

    private val quickQsOffsetHeight: Int =
        context.resources.getDimensionPixelSize(InternalR.dimen.quick_qs_offset_height)

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        val currentState = keyguardTransitionInteractor.currentKeyguardState.value

        if (e.actionMasked != MotionEvent.ACTION_UP ||
            currentState == KeyguardState.DOZING ||
            currentState == KeyguardState.AOD ||
            falsingManager.isFalseDoubleTap
        ) {
            return false
        }

        val isStatusBar = e.y < quickQsOffsetHeight
        val isLockscreen = currentState == KeyguardState.LOCKSCREEN

        val enabled = (isStatusBar && dt2sRepository.isEnabled(Dt2sType.STATUS_BAR)) ||
            (isLockscreen && dt2sRepository.isEnabled(Dt2sType.LOCKSCREEN))

        if (enabled) {
            MistouchInteractor.get().handleKeyguardInteraction()
            powerInteractor.setLastTouchToSleepPosition(e.x, e.y)
            powerManager.goToSleep(e.eventTime, PowerManager.GO_TO_SLEEP_REASON_TOUCH, 0)
            return true
        }
        return false
    }
}
