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

package com.android.systemui.qs.ax.shared.model

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AxQsTokens {
    object Geometry {
        val TileHeight: Dp = 72.dp
        val TileSpacing: Dp = 22.dp
        val CircleTileSize: Dp = 72.dp
        val IconContainerSize: Dp = 48.dp
        val IconSize: Dp = 24.dp
        val LargeIconSize: Dp = 24.dp

        val PortraitSidePadding: Dp = 22.dp
        val LandscapeSidePadding: Dp = 16.dp
        const val PORTRAIT_SIDE_FRACTION: Float = 0.035f
        const val LANDSCAPE_SIDE_FRACTION: Float = 0.045f

        val LandscapeGridSpacing: Dp = 16.dp
        val LandscapeSplitGridSpacing: Dp = 16.dp
        val LandscapeHeaderContentSpacing: Dp = 8.dp

        val LargeTileStartPadding: Dp = 12.dp
        val LargeTileEndPadding: Dp = 16.dp
        val DividerWidth: Dp = 1.dp
        val DividerHeight: Dp = 16.dp
        val IconDividerSpacing: Dp = 11.dp
        val DividerLabelSpacing: Dp = 11.dp

        val DragHandleWidth: Dp = 56.dp
        val DragHandleHeight: Dp = 4.dp
        val EditGridPadding: Dp = 10.dp
    }

    object CornerRadius {
        val LargeCornerRadius: Dp = 20.dp
        val ControlCornerRadius: Dp = 20.dp
        val InactiveCornerRadiusPercent: Int = 50
        val VerticalSliderShape = RoundedCornerShape(30)
        val EditGridCornerRadius: Dp = 28.dp
        val DragHandleCornerRadius: Dp = 2.dp
    }

    object Slider {
        val TrackHeight: Dp = 64.dp
        val ThumbWidth: Dp = 4.dp
        val ThumbTrackGap: Dp = 6.dp
        val FramePadding: Dp = 4.dp
        val TrackInsideCornerRadius: Dp = 2.dp
        const val CORNER_DIVISOR: Float = 3.333f

        val RingerOuterPadding: Dp = 6.dp
        val RingerDotSize: Dp = 6.dp
    }

    object Badges {
        val RemoveBadgeSize: Dp = 24.dp
        val RemoveBadgeIconSize: Dp = 16.dp
        val ResizeHandleSize: Dp = 24.dp
        val ResizeHandleIconSize: Dp = 12.dp
        val SelectionBorderWidth: Dp = 3.dp
    }

    object Animation {
        const val AX_ENTRANCE_ALPHA_END_FRACTION: Float = 0.35f
        const val AX_ENTRANCE_TRANSLATION_PX: Float = 180f
        const val AX_ENTRANCE_MIN_SCALE: Float = 0.92f
        const val AX_ROW_STAGGER_PX: Float = 24f

        const val AX_PRESS_MIN_SCALE: Float = 0.92f
        const val AX_PRESS_MAX_SCALE: Float = 0.98f
        const val AX_PRESS_STIFFNESS: Float = 1200f
        const val AX_PRESS_DAMPING: Float = 1.0f
        const val AX_RELEASE_STIFFNESS: Float = 550f
        const val AX_RELEASE_DAMPING: Float = 0.75f
        const val AX_QUICK_PRESS_STIFFNESS: Float = 2500f

        const val QS_ENTRANCE_ALPHA_START: Float = 0.89f
        const val QS_ENTRANCE_TRANSLATION_PX: Float = 300f
        const val QS_ENTRANCE_HIDDEN_TRANSLATION_PX: Float = -5000f

        const val QS_SCENE_FADE_START: Float = 0.5f
        const val SEPARATE_MEDIA_FADE_END: Float = 0.40f
        const val SEPARATE_GRID_ENTRANCE_START: Float = 0.35f
        const val SEPARATE_GRID_ENTRANCE_END: Float = 0.75f
        const val QS_SCENE_TRANSLATION_PX: Float = 300f
        const val QS_SCENE_HIDDEN_TRANSLATION_PX: Float = -5000f

        const val EDIT_MODE_TIME_MILLIS: Int = 300
        const val TILE_REVEAL_MIN_SCALE: Float = 0.85f

        const val AX_EXPAND_THRESHOLD: Float = 0.6f
        const val AX_COLLAPSE_THRESHOLD: Float = 0.4f
        const val AX_A11Y_INVISIBLE_THRESHOLD: Float = 0.05f
        const val AX_EDIT_SCRIM_ALPHA: Float = 0.32f
        val AX_RESIZE_EASING = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        const val AX_RESIZE_DURATION_MS: Int = 200

        const val RINGER_SLIDE_DURATION_MS: Int = 250
        const val RINGER_DOT_DURATION_MS: Int = 200

        const val ICON_CHIP_ALPHA: Float = 0.12f
    }
}
