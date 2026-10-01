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

package com.android.systemui.qs.ax.ui.grid

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.ContentScope
import com.android.systemui.qs.ax.fragment.viewmodel.AxQsFragmentComposeViewModel
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridValue
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel

@Composable
internal fun ContentScope.AxLiveTile(
    tile: TileViewModel,
    item: AxQsGridItem<AxQsGridValue>,
    iconOnly: Boolean,
    qqs: Boolean,
    separateQqs: Boolean,
    listening: () -> Boolean,
    detailsViewModel: DetailsViewModel?,
    viewModel: AxQsFragmentComposeViewModel,
    isClickable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cellConfig = LocalAxQsCellConfig.current
        this@AxLiveTile.AxTile(
            tile = tile,
            iconOnly = iconOnly,
            span = item.span,
            compactIconSize = cellConfig.iconSize,
            tileShapeOverride = null,
            squishiness = { 1f },
            coroutineScope = coroutineScope,
            bounceableInfo = null,
            tileHapticsViewModelFactoryProvider =
                viewModel.quickQuickSettingsViewModel.tileHapticsViewModelFactoryProvider,
            interactionSource = null,
            detailsViewModel = detailsViewModel,
            isClickable = isClickable,
        )
    }
}
