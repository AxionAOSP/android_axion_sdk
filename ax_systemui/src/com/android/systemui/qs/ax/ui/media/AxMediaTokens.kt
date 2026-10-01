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

package com.android.systemui.qs.ax.ui.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

internal object AxMediaTokens {
    val OneRowReferenceHeight = 72.dp
    val Square2x2ReferenceWidth = 160.dp
    val Square2x2ReferenceHeight = 144.dp
    val LargeCardReferenceWidth = 360.dp
    val HalfRowThresholdWidth = 220.dp
    val CompactHeightThreshold = 135.dp

    const val MinTileScale = 0.75f
    const val MaxTileScale = 1.25f

    val IndicatorClearanceLarge = 10.dp
    val IndicatorClearanceOneRow = 8.dp

    val DotSize = 5.dp
    val DotSpacing = 5.dp
    val DotBottomPadding = 5.dp
    const val InactiveDotAlpha = 0.38f
    const val InactiveDotLockscreenAlpha = 0.50f

    val MinActionTouchTarget = 32.dp
    val StandardActionTouchTarget = 48.dp
    const val RouteButtonIconRatio = 0.55f
    val MinRouteIconSize = 14.dp
    val MaxRouteIconSize = 22.dp

    const val AlbumSquircleRadiusRatio = 0.25f
    val MinAlbumSquircleRadius = 8.dp
    val MaxAlbumSquircleRadius = 14.dp

    val MediaCardCornerRadius = 28.dp
    val MediaCardShape = RoundedCornerShape(MediaCardCornerRadius)

    const val IndicatorFadeInDurationMs = 180
    const val IndicatorFadeOutDurationMs = 140
    const val DynamicPaddingDurationMs = 200
    const val ArtworkCrossfadeDurationMs = 150
    const val ActionResizeDurationMs = 220

    val ButtonPressSpring: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )

    val ActionSizeSpring: SpringSpec<Dp> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    val DynamicPaddingSpring: SpringSpec<Dp> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    val IndicatorSlideSpring: SpringSpec<IntOffset> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    val CarouselScrollSpring: SpringSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )

    const val SubtitleAlpha = 0.72f
    const val HeaderAppIconAlpha = 0.80f
    const val KeyguardArtworkTintAlpha = 0.65f
}
