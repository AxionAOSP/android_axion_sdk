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

package com.android.systemui.qs.ax.ui.panels

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.qs.ax.fragment.AxSceneKeys
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.shared.ui.QuickSettings.Elements

object AxQsElements {
    val SeparateModeMedia = ElementKey("separateModeMedia")
}

fun TransitionBuilder.axSeparateModeMediaTransition(
    isSeparateMode: () -> Boolean = { false },
) {
    if (isSeparateMode()) {
        fractionRange(end = AxQsTokens.Animation.SEPARATE_MEDIA_FADE_END) {
            fade(AxQsElements.SeparateModeMedia)
            translate(AxQsElements.SeparateModeMedia, y = 48.dp)
        }
    }
}

fun TransitionBuilder.axQuickQuickSettingsToQuickSettings(
    animateTilesExpansion: () -> Boolean = { true },
    isSeparateMode: () -> Boolean = { false },
) {
    if (isSeparateMode()) {
        fractionRange(start = AxQsTokens.Animation.SEPARATE_GRID_ENTRANCE_START) {
            fade(Elements.QuickSettingsContent)
        }
        fractionRange(end = AxQsTokens.Animation.SEPARATE_MEDIA_FADE_END) {
            fade(AxQsElements.SeparateModeMedia)
            translate(AxQsElements.SeparateModeMedia, y = 48.dp)
            fade(Media.Elements.mediaCarousel)
            translate(Media.Elements.mediaCarousel, y = 48.dp)
        }
    } else {
        fractionRange(start = 0.43f) { fade(Elements.QuickSettingsContent) }
        fade(Media.Elements.mediaCarousel)
    }

    fractionRange(start = 0.9f) { fade(Elements.FooterActions) }
    anchoredTranslate(Elements.QuickSettingsContent, Elements.GridAnchor)

    sharedElement(Elements.TileElementMatcher, enabled = animateTilesExpansion())
    sharedElement(Elements.BrightnessSlider)
}

private const val SCENE_EXIT_END_FRACTION = 0.53f
private const val SCENE_ENTER_START_FRACTION = 0.47f
private const val SCENE_TRANSITION_SCALE = 0.94f

fun TransitionBuilder.toAxPanelSettings() {
    fractionRange(end = SCENE_EXIT_END_FRACTION) {
        fade(AxSceneKeys.QuickSettings.rootElementKey)
        scaleDraw(
            AxSceneKeys.QuickSettings.rootElementKey,
            scaleX = SCENE_TRANSITION_SCALE,
            scaleY = SCENE_TRANSITION_SCALE,
        )
    }
    fractionRange(start = SCENE_ENTER_START_FRACTION) {
        fade(AxSceneKeys.PanelSettings.rootElementKey)
        scaleDraw(
            AxSceneKeys.PanelSettings.rootElementKey,
            scaleX = SCENE_TRANSITION_SCALE,
            scaleY = SCENE_TRANSITION_SCALE,
        )
    }
    disableAxQsSharedElements()
}

fun TransitionBuilder.fromAxPanelSettings() {
    fractionRange(end = SCENE_EXIT_END_FRACTION) {
        fade(AxSceneKeys.PanelSettings.rootElementKey)
        scaleDraw(
            AxSceneKeys.PanelSettings.rootElementKey,
            scaleX = SCENE_TRANSITION_SCALE,
            scaleY = SCENE_TRANSITION_SCALE,
        )
    }
    fractionRange(start = SCENE_ENTER_START_FRACTION) {
        fade(AxSceneKeys.QuickSettings.rootElementKey)
        scaleDraw(
            AxSceneKeys.QuickSettings.rootElementKey,
            scaleX = SCENE_TRANSITION_SCALE,
            scaleY = SCENE_TRANSITION_SCALE,
        )
    }
    disableAxQsSharedElements()
}

private fun TransitionBuilder.disableAxQsSharedElements() {
    sharedElement(Elements.TileElementMatcher, enabled = false)
    sharedElement(Elements.BrightnessSlider, enabled = false)
    sharedElement(Elements.VolumeSlider, enabled = false)
}

fun SceneTransitionLayoutState.shouldComposeLiveAxQs(): Boolean {
    return when (val state = transitionState) {
        is TransitionState.Idle -> state.currentScene.isAxQsScene()
        is TransitionState.Transition -> {
            state.fromContent.isAxQsScene() || state.toContent.isAxQsScene()
        }
    }
}

fun Modifier.axQsEntrance(
    progress: () -> Float,
): Modifier = axQsEntrance(includeTranslation = true, progress = progress)

fun Modifier.axQsEntrance(
    includeTranslation: Boolean,
    progress: () -> Float,
): Modifier {
    return graphicsLayer {
        val entrance = progress().coerceIn(0f, 1f)
        alpha = (entrance / AxQsTokens.Animation.AX_ENTRANCE_ALPHA_END_FRACTION).coerceIn(0f, 1f)
        val minScale = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
        val scale = minScale + (1f - minScale) * entrance
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 0f)
        translationY =
            if (includeTranslation) {
                -(1f - entrance) * AxQsTokens.Animation.AX_ENTRANCE_TRANSLATION_PX
            } else {
                0f
            }
    }
}

internal fun Modifier.axQuickSettingsSceneMotion(
    isTransitioningFromQqs: Boolean = false,
    progress: () -> Float,
): Modifier {
    return graphicsLayer {
        if (isTransitioningFromQqs) {
            alpha = 1f
            translationY = 0f
        } else {
            val expansion = progress().coerceIn(0f, 1f)
            alpha =
                (expansion / AxQsTokens.Animation.AX_ENTRANCE_ALPHA_END_FRACTION).coerceIn(0f, 1f)
            val minScale = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
            val scale = minScale + (1f - minScale) * expansion
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
            translationY =
                -(1f - expansion) * AxQsTokens.Animation.AX_ENTRANCE_TRANSLATION_PX
        }
    }
}

internal fun Modifier.axQsItemReveal(
    isRevealed: Boolean,
    row: Int = 0,
    scrollStateValue: () -> Int = { 0 },
    qqsCollapseTranslationY: () -> Float = { 0f },
    progress: () -> Float,
): Modifier {
    if (isRevealed) {
        return graphicsLayer {
            val transY = qqsCollapseTranslationY()
            if (transY != 0f) {
                translationY = transY
            }
        }
    }
    return graphicsLayer {
        val expansion = progress().coerceIn(0f, 1f)
        val minScale = AxQsTokens.Animation.TILE_REVEAL_MIN_SCALE
        val staggerPx = AxQsTokens.Animation.AX_ROW_STAGGER_PX

        val currentScroll = scrollStateValue()
        val collapseFade =
            if (currentScroll > 0) {
                ((expansion - 0.7f) / 0.3f).coerceIn(0f, 1f)
            } else {
                1f
            }
        alpha = computeAxFadeAlpha(expansion) * collapseFade
        val scale = minScale + (1f - minScale) * expansion
        scaleX = scale
        scaleY = scale
        val rowMultiplier = (row - 1).coerceAtLeast(1)
        translationY = -(1f - expansion) * staggerPx * (rowMultiplier * 0.5f).coerceAtLeast(1f)
    }
}

enum class AxQsExpansionMode {
    QQS,
    QS,
}

fun computeHysteresisMode(
    progress: Float,
    currentMode: AxQsExpansionMode,
): AxQsExpansionMode {
    return when (currentMode) {
        AxQsExpansionMode.QQS -> {
            if (progress >= AxQsTokens.Animation.AX_EXPAND_THRESHOLD) {
                AxQsExpansionMode.QS
            } else {
                AxQsExpansionMode.QQS
            }
        }
        AxQsExpansionMode.QS -> {
            if (progress <= AxQsTokens.Animation.AX_COLLAPSE_THRESHOLD) {
                AxQsExpansionMode.QQS
            } else {
                AxQsExpansionMode.QS
            }
        }
    }
}

fun computeAxFadeAlpha(progress: Float): Float {
    if (progress <= AxQsTokens.Animation.AX_COLLAPSE_THRESHOLD) return 0f
    return ((progress - AxQsTokens.Animation.AX_COLLAPSE_THRESHOLD) /
            (1f - AxQsTokens.Animation.AX_COLLAPSE_THRESHOLD)).coerceIn(0f, 1f)
}

fun Modifier.alphaWithA11y(
    alpha: Float,
    isInvisible: Boolean = alpha < AxQsTokens.Animation.AX_A11Y_INVISIBLE_THRESHOLD,
): Modifier {
    val graphics = graphicsLayer { this.alpha = alpha }
    val a11y = semantics {
        if (isInvisible) {
            hideFromAccessibility()
        }
    }
    val pointer =
        if (isInvisible) {
            pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        } else {
            Modifier
        }
    return graphics.then(a11y).then(pointer)
}

private fun ContentKey.isAxQsScene(): Boolean {
    return this == AxSceneKeys.QuickSettings
}

private const val AX_QS_ENTRANCE_ALPHA_START = AxQsTokens.Animation.QS_ENTRANCE_ALPHA_START
private const val AX_QS_ENTRANCE_TRANSLATION_PX = AxQsTokens.Animation.QS_ENTRANCE_TRANSLATION_PX
private const val AX_QS_ENTRANCE_HIDDEN_TRANSLATION_PX =
    AxQsTokens.Animation.QS_ENTRANCE_HIDDEN_TRANSLATION_PX
private const val AX_QS_SCENE_FADE_START = AxQsTokens.Animation.QS_SCENE_FADE_START
private const val AX_QS_SCENE_TRANSLATION_PX = AxQsTokens.Animation.QS_SCENE_TRANSLATION_PX
private const val AX_QS_SCENE_HIDDEN_TRANSLATION_PX =
    AxQsTokens.Animation.QS_SCENE_HIDDEN_TRANSLATION_PX
