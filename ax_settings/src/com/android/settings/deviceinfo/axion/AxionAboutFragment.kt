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
package com.android.settings.deviceinfo.axion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.fragment.app.Fragment
import com.android.axion.compose.about.AxionAboutScreen
import com.android.settings.Settings.MyDeviceInfoActivity
import com.android.settingslib.spa.framework.theme.SettingsTheme

class AxionAboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsTheme {
                    val interop = rememberNestedScrollInteropConnection()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(interop)
                    ) {
                        AxionAboutScreen(
                            onNavigateBack = {
                                activity?.onBackPressedDispatcher?.onBackPressed()
                            },
                            onNavigateToDeviceInfo = {
                                startActivity(Intent(requireContext(), MyDeviceInfoActivity::class.java))
                            },
                            onEditDeviceName = {},
                            showTopBar = false,
                        )
                    }
                }
            }
        }
    }
}
