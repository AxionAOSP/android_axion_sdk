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

package com.android.systemui.qs.ax

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.fragments.FragmentService
import com.android.systemui.qs.ax.data.repository.AxQsSettingsRepository
import com.android.systemui.qs.ax.fragment.AxQsFragmentCompose
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@SysUISingleton
class AxQsStartable
@Inject
constructor(
    private val fragmentService: FragmentService,
    private val axQsFragmentComposeProvider: Provider<AxQsFragmentCompose>,
    private val settingsRepository: AxQsSettingsRepository,
    private val axQsViewModel: AxQsViewModel,
    @Application private val applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : CoreStartable {
    override fun start() {
        fragmentService.addFragmentInstantiationProvider(
            AxQsFragmentCompose::class.java,
            axQsFragmentComposeProvider,
        )
        settingsRepository.init()
        applicationScope.launch(backgroundDispatcher) {
            axQsViewModel.activate()
        }
    }
}
