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

package com.android.systemui.qs.ax.ui.grid

import android.content.Context
import android.content.res.Resources
import android.service.quicksettings.Tile.STATE_ACTIVE
import android.service.quicksettings.Tile.STATE_INACTIVE
import android.service.quicksettings.Tile.STATE_UNAVAILABLE
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.trace
import com.android.compose.animation.Expandable as ComposeExpandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ContentScope
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.mechanics.compose.modifier.verticalFadeContentReveal
import com.android.mechanics.compose.modifier.verticalTactileSurfaceReveal
import com.android.systemui.Flags
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModel
import com.android.systemui.haptics.msdl.qs.TileHapticsViewModelFactoryProvider
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.ax.pressfeedback.axPressFeedback
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.composefragment.LocalBlurEnabled
import com.android.systemui.qs.flags.QsDetailedView
import com.android.systemui.qs.panels.ui.compose.BounceableInfo
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.InactiveCornerRadius
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelMoreDetails
import com.android.systemui.qs.panels.ui.compose.infinitegrid.CommonTileDefaults.longPressLabelSettings
import com.android.systemui.qs.panels.ui.compose.infinitegrid.SmallTileContent
import com.android.systemui.qs.panels.ui.compose.infinitegrid.verticalSquish
import com.android.systemui.qs.panels.ui.viewmodel.AccessibilityUiState
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.qs.ui.compose.borderOnFocus
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope

@Composable
fun ContentScope.AxTile(
    tile: TileViewModel,
    iconOnly: Boolean,
    squishiness: () -> Float,
    coroutineScope: CoroutineScope,
    bounceableInfo: BounceableInfo?,
    tileHapticsViewModelFactoryProvider: TileHapticsViewModelFactoryProvider,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
    isVisible: () -> Boolean = { true },
    requestToggleTextFeedback: (TileSpec) -> Unit = {},
    detailsViewModel: DetailsViewModel?,
    enableRevealEffect: Boolean = false,
    span: AxQsSpan = if (iconOnly) AxQsSpan.TileDefault else AxQsSpan.TileWideDefault,
    compactIconSize: Dp? = null,
    tileShapeOverride: RoundedCornerShape? = null,
    isClickable: Boolean = true,
) {
    trace(tile.spec.spec) {
        val compact = span.columns == 1
        val res = axResources()

        val uiState by
            produceState(tile.currentState.toUiState(res), tile, res) {
                tile.state.collect { value = it.toUiState(res) }
            }
        val effectiveClickable = isClickable && uiState.state != STATE_UNAVAILABLE
        val isDualTarget = uiState.handlesSecondaryClick

        val icon by
            produceState(tile.currentState.toIconProvider(), tile) {
                tile.state.collect { value = it.toIconProvider() }
            }

        val hapticsViewModel: TileHapticsViewModel? =
            rememberViewModel(traceName = "TileHapticsViewModel") {
                tileHapticsViewModelFactoryProvider?.getHapticsViewModelFactory()?.create(tile)
            }

        val tileInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

        val useLongClickToSettings = !(compact && isDualTarget && effectiveClickable)
        val longClick: ((Expandable) -> Unit)? =
            { expandable: Expandable ->
                hapticsViewModel?.setTileInteractionState(
                    TileHapticsViewModel.TileInteractionState.LONG_CLICKED
                )
                if (useLongClickToSettings) {
                    tile.settingsClick(expandable)
                } else {
                    tile.mainClick(expandable)
                }
            }.takeIf { !useLongClickToSettings || uiState.handlesLongClick }

        AxTile(
            uiState = uiState,
            iconProvider = { getAxTileIcon(icon = icon) },
            compact = compact,
            span = span,
            modifier = modifier,
            isClickable = effectiveClickable,
            isDualTarget = isDualTarget,
            compactIconSize = compactIconSize,
            squishiness = squishiness,
            isVisible = isVisible,
            enableRevealEffect = enableRevealEffect,
            tileShapeOverride = tileShapeOverride,
            interactionSource = tileInteractionSource,
            hapticsViewModel = hapticsViewModel,
            onClick = { expandable: Expandable ->
                val hasDetails =
                    QsDetailedView.isEnabled &&
                        detailsViewModel?.onTileClicked(tile.spec) == true
                if (hasDetails) return@AxTile

                if (compact && isDualTarget) {
                    tile.toggleClick()
                } else {
                    tile.mainClick(expandable)
                }

                hapticsViewModel?.setTileInteractionState(
                    TileHapticsViewModel.TileInteractionState.CLICKED
                )

                if (uiState.isToggleable && compact) {
                    requestToggleTextFeedback(tile.spec)
                }
            },
            onLongClick = longClick,
            secondaryClick = {
                hapticsViewModel?.setTileInteractionState(
                    TileHapticsViewModel.TileInteractionState.CLICKED
                )
                tile.toggleClick()
            }.takeIf { isDualTarget },
        )
    }
}

@Composable
fun AxTile(
    uiState: TileUiState,
    iconProvider: Context.() -> Icon,
    compact: Boolean,
    span: AxQsSpan,
    modifier: Modifier = Modifier,
    isClickable: Boolean = true,
    isDualTarget: Boolean = uiState.handlesSecondaryClick,
    compactIconSize: Dp? = null,
    squishiness: () -> Float = { 1f },
    isVisible: () -> Boolean = { true },
    enableRevealEffect: Boolean = false,
    tileShapeOverride: RoundedCornerShape? = null,
    interactionSource: MutableInteractionSource? = null,
    hapticsViewModel: TileHapticsViewModel? = null,
    onClick: ((Expandable) -> Unit)? = null,
    onLongClick: ((Expandable) -> Unit)? = null,
    secondaryClick: (() -> Unit)? = null,
) {
    val cellConfig = LocalAxQsCellConfig.current
    val tileScale = cellConfig.densityScale
    val is2x2 = span.columns == 2 && span.rows == 2
    val colors = AxTileColorsDefaults.getColorForState(uiState, compact, isDualTarget, is2x2)
    val isCircleTile = compact || (span.columns == 1 && span.rows == 1)
    val defaultTileShape =
        when {
            isCircleTile -> CircleShape
            span.columns > 1 && span.rows == 1 -> RoundedCornerShape(percent = 50)
            else -> RoundedCornerShape(cellConfig.largeCornerRadius)
        }
    val tileShape: RoundedCornerShape = tileShapeOverride ?: defaultTileShape
    val animatedColor by animateColorAsState(colors.background, label = "QSTileBackgroundColor")

    val surfaceRevealModifier: Modifier
    val contentRevealModifier: Modifier
    if (enableRevealEffect) {
        val marginBottom =
            with(LocalDensity.current) { QuickSettingsShade.Dimensions.Padding.toPx() }
        surfaceRevealModifier =
            Modifier.verticalTactileSurfaceReveal(deltaY = marginBottom, label = uiState.label)
        contentRevealModifier =
            Modifier.verticalFadeContentReveal(deltaY = marginBottom, label = uiState.label)
    } else {
        surfaceRevealModifier = Modifier
        contentRevealModifier = Modifier
    }

    val source = interactionSource ?: remember { MutableInteractionSource() }
    val clickEffectModifier = Modifier.axPressFeedback(source, enabled = isClickable)

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.then(clickEffectModifier),
    ) {
        val targetHeight = cellConfig.itemHeight(span)
        val circleTileSize = cellConfig.iconTileSize
        val tileSizingModifier =
            if (isCircleTile) {
                Modifier.size(circleTileSize)
            } else {
                Modifier.fillMaxWidth().height(targetHeight)
            }

        AxTileExpandable(
            color = { animatedColor },
            shape = tileShape,
            squishiness = squishiness,
            hapticsViewModel = hapticsViewModel,
            modifier =
                Modifier
                    .then(tileSizingModifier)
                    .then(surfaceRevealModifier)
                    .borderOnFocus(color = MaterialTheme.colorScheme.secondary, tileShape.topEnd),
        ) { expandable ->
            AxTileContainer(
                interactionSource = source,
                onClick = onClick?.takeIf { isClickable }?.let { { it(expandable) } },
                onLongClick = onLongClick?.takeIf { isClickable }?.let { { it(expandable) } },
                accessibilityUiState = uiState.accessibilityUiState,
                iconOnly = compact,
                isDualTarget = isDualTarget,
                modifier = contentRevealModifier,
            ) {
                AnimatedContent(
                    targetState = compact,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "AxTileLayout",
                    modifier = Modifier.fillMaxSize(),
                ) { isCompact ->
                    if (isCompact) {
                        val resolvedIconSize =
                            compactIconSize ?: cellConfig.iconSize
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SmallTileContent(
                                iconProvider = iconProvider,
                                color = colors.icon,
                                size = { resolvedIconSize },
                            )
                        }
                    } else {
                        AxLargeTileContent(
                            label = uiState.label,
                            secondaryLabel = uiState.secondaryLabel,
                            iconProvider = iconProvider,
                            sideDrawable = uiState.sideDrawable,
                            colors = colors,
                            iconShape = CircleShape,
                            tileState = uiState.state,
                            span = span,
                            isDualTarget = isDualTarget,
                            toggleClick = secondaryClick,
                            onLongClick = onLongClick?.let { { it(expandable) } },
                            interactionSource = source,
                            accessibilityUiState = uiState.accessibilityUiState,
                            squishiness = squishiness,
                            isVisible = isVisible,
                            textScale = { 1f },
                            showDivider = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AxTileExpandable(
    color: () -> Color,
    shape: Shape,
    squishiness: () -> Float,
    hapticsViewModel: TileHapticsViewModel?,
    modifier: Modifier = Modifier,
    content: @Composable (Expandable) -> Unit,
) {
    ComposeExpandable(
        controller = rememberExpandableController(color = color, shape = shape),
        modifier = modifier.clip(shape).verticalSquish(squishiness),
        useModifierBasedImplementation = true,
    ) {
        content(hapticsViewModel?.createStateAwareExpandable(it) ?: it)
    }
}

@Composable
fun AxTileContainer(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    iconOnly: Boolean,
    isDualTarget: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .axTileCombinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    accessibilityUiState = accessibilityUiState,
                    interactionSource = source,
                    iconOnly = iconOnly,
                    isDualTarget = isDualTarget,
                ),
        content = content,
    )
}

@Composable
private fun Modifier.axTileCombinedClickable(
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    accessibilityUiState: AccessibilityUiState,
    interactionSource: MutableInteractionSource?,
    iconOnly: Boolean,
    isDualTarget: Boolean,
): Modifier {
    if (onClick == null) return this
    val longPressLabel =
        if (iconOnly && isDualTarget) longPressLabelMoreDetails() else longPressLabelSettings()
    return combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onClickLabel = accessibilityUiState.clickLabel,
            onLongClickLabel = longPressLabel,
            hapticFeedbackEnabled = !Flags.msdlFeedback(),
            interactionSource = interactionSource,
        )
        .semantics {
            val accessibilityRole =
                if (iconOnly && isDualTarget) {
                    Role.Switch
                } else {
                    accessibilityUiState.accessibilityRole
                }
            if (accessibilityRole == Role.Switch) {
                accessibilityUiState.toggleableState?.let { toggleableState = it }
            }
            role = accessibilityRole
            stateDescription = accessibilityUiState.stateDescription
        }
        .thenIf(iconOnly) {
            Modifier.semantics { contentDescription = accessibilityUiState.contentDescription }
        }
}

internal fun Context.getAxTileIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

data class AxTileColors(
    val background: Color,
    val label: Color,
    val secondaryLabel: Color,
    val icon: Color,
    val chipBackground: Color,
    val chipIcon: Color,
)

object AxTileColorsDefaults {
    val ActiveIconCornerRadius = 16.dp
    val ActiveTileCornerRadius = 24.dp

    @Composable
    @ReadOnlyComposable
    fun backgroundTileColors(): Color {
        val blurEnabled = LocalBlurEnabled.current
        return if (blurEnabled) {
            LocalAndroidColorScheme.current.surfaceEffect1
        } else {
            MaterialTheme.colorScheme.surfaceBright
        }
    }

    @Composable
    @ReadOnlyComposable
    fun activeTileColors(): AxTileColors =
        AxTileColors(
            background = MaterialTheme.colorScheme.primary,
            label = MaterialTheme.colorScheme.onPrimary,
            secondaryLabel = MaterialTheme.colorScheme.onPrimary,
            icon = MaterialTheme.colorScheme.onPrimary,
            chipBackground = Color.Transparent,
            chipIcon = MaterialTheme.colorScheme.onPrimary,
        )

    @Composable
    @ReadOnlyComposable
    fun inactiveTileColors(): AxTileColors {
        val onSurface = MaterialTheme.colorScheme.onSurface
        return AxTileColors(
            background = backgroundTileColors(),
            label = onSurface,
            secondaryLabel = onSurface,
            icon = onSurface,
            chipBackground = Color.Transparent,
            chipIcon = onSurface,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun unavailableTileColors(): AxTileColors {
        val blurEnabled = LocalBlurEnabled.current
        if (blurEnabled) {
            val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .18f)
            val onSurfaceVariantColor =
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
            return AxTileColors(
                background = surfaceColor,
                label = onSurfaceVariantColor,
                secondaryLabel = onSurfaceVariantColor,
                icon = onSurfaceVariantColor,
                chipBackground = surfaceColor,
                chipIcon = onSurfaceVariantColor,
            )
        } else {
            val bgColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = .38f)
            val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            return AxTileColors(
                background = bgColor,
                label = textColor,
                secondaryLabel = textColor,
                icon = textColor,
                chipBackground = bgColor,
                chipIcon = textColor,
            )
        }
    }

    @Composable
    @ReadOnlyComposable
    fun activeDualTargetTileColors(): AxTileColors {
        val onSurface = MaterialTheme.colorScheme.onSurface
        return AxTileColors(
            background = backgroundTileColors(),
            label = onSurface,
            secondaryLabel = onSurface,
            icon = MaterialTheme.colorScheme.onPrimary,
            chipBackground = MaterialTheme.colorScheme.primary,
            chipIcon = MaterialTheme.colorScheme.onPrimary,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTileColors(): AxTileColors {
        val onSurface = MaterialTheme.colorScheme.onSurface
        return AxTileColors(
            background = backgroundTileColors(),
            label = onSurface,
            secondaryLabel = onSurface,
            icon = onSurface,
            chipBackground = onSurface.copy(alpha = AxQsTokens.Animation.ICON_CHIP_ALPHA),
            chipIcon = onSurface,
        )
    }

    @Composable
    @ReadOnlyComposable
    fun inactiveDualTargetTileColors(): AxTileColors = inactiveDualTileColors()

    @Composable
    @ReadOnlyComposable
    fun dualTargetTileColors(state: Int): AxTileColors =
        when (state) {
            STATE_ACTIVE -> activeDualTargetTileColors()
            STATE_INACTIVE -> inactiveDualTargetTileColors()
            else -> unavailableTileColors()
        }

    @Composable
    @ReadOnlyComposable
    fun getColorForState(
        uiState: TileUiState,
        iconOnly: Boolean,
        isDualTarget: Boolean = false,
        is2x2: Boolean = false,
    ): AxTileColors {
        if ((isDualTarget || is2x2) && !iconOnly) {
            return dualTargetTileColors(uiState.state)
        }
        return when (uiState.state) {
            STATE_ACTIVE -> activeTileColors()
            STATE_INACTIVE -> inactiveTileColors()
            else -> unavailableTileColors()
        }
    }

    @Composable
    fun animateIconShapeAsState(state: Int): State<RoundedCornerShape> {
        return animateShapeAsState(
            state = state,
            activeCornerRadius = ActiveIconCornerRadius,
            label = "AxQSTileCornerRadius",
        )
    }

    @Composable
    fun animateTileShapeAsState(state: Int): State<RoundedCornerShape> {
        return remember { mutableStateOf(CircleShape) }
    }

    @Composable
    fun animateShapeAsState(
        state: Int,
        activeCornerRadius: Dp,
        label: String,
    ): State<RoundedCornerShape> {
        val animatedCornerRadius by
            animateDpAsState(
                targetValue =
                    if (state == STATE_ACTIVE) {
                        activeCornerRadius
                    } else {
                        InactiveCornerRadius
                    },
                label = label,
            )

        return remember {
            val corner =
                object : CornerSize {
                    override fun toPx(shapeSize: Size, density: Density): Float {
                        return with(density) { animatedCornerRadius.toPx() }
                    }
                }
            mutableStateOf(RoundedCornerShape(corner))
        }
    }
}

@Composable
@ReadOnlyComposable
internal fun axResources(): Resources {
    LocalConfiguration.current
    return LocalResources.current
}
