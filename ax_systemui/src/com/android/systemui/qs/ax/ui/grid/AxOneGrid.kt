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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.modifiers.thenIf
import com.android.internal.R as InternalR
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.qs.ax.fragment.viewmodel.AxQsFragmentComposeViewModel
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridItem
import com.android.systemui.qs.ax.shared.model.AxQsGridLayout
import com.android.systemui.qs.ax.shared.model.AxQsGridSection
import com.android.systemui.qs.ax.shared.model.AxQsGridValue
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsPanelMode
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.shared.model.AxQsTokens
import com.android.systemui.qs.ax.shared.model.LocalAxQsLayout
import com.android.systemui.qs.ax.ui.edit.AxAvailableControls
import com.android.systemui.qs.ax.ui.edit.AxQsDragAutoScroll
import com.android.systemui.qs.ax.ui.edit.axQsDropTarget
import com.android.systemui.qs.ax.ui.header.AxQsEditHeader
import com.android.systemui.qs.ax.ui.header.AxQuickSettingsLayoutDefaults
import com.android.systemui.qs.ax.ui.header.LocalAxQsModularSidePadding
import com.android.systemui.qs.ax.ui.media.AxMediaPanel
import com.android.systemui.qs.ax.ui.media.nonQsGridMediaHeight
import com.android.systemui.qs.ax.ui.panels.AxQsDateHeader
import com.android.systemui.qs.ax.ui.panels.AxQsElements
import com.android.systemui.qs.ax.ui.panels.AxQsExpansionMode
import com.android.systemui.qs.ax.ui.panels.AxQsGridSettings
import com.android.systemui.qs.ax.ui.panels.axQsEntrance
import com.android.systemui.qs.ax.ui.panels.computeHysteresisMode
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.ui.composable.QuickSettingsTheme
import com.android.systemui.res.R as SysuiR
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

private val SeparateModeMedia = AxQsElements.SeparateModeMedia

@Composable
internal fun ContentScope.AxOneGrid(
    viewModel: AxQsFragmentComposeViewModel,
    axQsViewModel: AxQsViewModel,
    mediaViewModel: AxMediaViewModel,
    detailsViewModel: DetailsViewModel,
    listening: () -> Boolean,
    brightnessSliderViewModel: BrightnessSliderViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel,
    scrollState: ScrollState,
    headerTopPadding: Dp,
    onOpenPanelSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tiles = viewModel.containerViewModel.tileGridViewModel.tileViewModels
    val layout = LocalAxQsLayout.current
    val splitShade = viewModel.isInSplitShade
    LaunchedEffect(splitShade) {
        if (splitShade) {
            scrollState.scrollTo(0)
        }
    }
    val gridLayout = AxQsGridLayout.from(layout)
    val columns = axQsViewModel.columns(gridLayout)
    val positions = axQsViewModel.positions(layout)

    val editModeViewModel = viewModel.containerViewModel.editModeViewModel
    val isEditing by editModeViewModel.isEditing.collectAsStateWithLifecycle()
    val allEditTiles by editModeViewModel.tiles.collectAsStateWithLifecycle(initialValue = null)

    BackHandler(enabled = isEditing) { editModeViewModel.stopEditing() }

    val resetEpoch = remember { mutableIntStateOf(0) }
    val viewportBounds = remember { mutableStateOf(Rect.Zero) }
    val view = LocalView.current

    val values =
        remember(tiles, allEditTiles, layout, axQsViewModel.layoutData(layout)) {
            val map = LinkedHashMap<String, AxQsGridValue>()
            val editMap = allEditTiles?.associateBy { it.tileSpec.spec } ?: emptyMap()
            tiles.forEach { tile ->
                map[tile.spec.spec] = AxQsGridValue.Tile(
                    viewModel = tile,
                    editViewModel = editMap[tile.spec.spec],
                )
            }
            AxQsControl.entries.forEach { control -> map[control.id] = AxQsGridValue.Control(control) }
            val tilesBySpec = tiles.associateBy { it.spec.spec }
            val stacks = axQsViewModel.stacks(layout)
            val stackedTileSpecs = mutableSetOf<String>()
            stacks.forEach { (stackId, tileSpecs) ->
                val stackLiveTiles = tileSpecs.mapNotNull { tilesBySpec[it] }
                val stackEditTiles = tileSpecs.mapNotNull { editMap[it] }
                map[stackId] = AxQsGridValue.Stack(
                    tiles = stackLiveTiles,
                    editTiles = stackEditTiles,
                )
                stackedTileSpecs.addAll(tileSpecs)
            }
            stackedTileSpecs.forEach { spec -> map.remove(spec) }
            map
        }

    val spans = axQsViewModel.spans(layout)
    val gridItems: List<AxQsGridItem<AxQsGridValue>> =
        remember(values, layout, columns, positions, spans) {
            val orderedIds =
                axQsViewModel.orderedIds(
                    layout = layout,
                    availableIds = values.keys.toList(),
                    defaultIds = tiles.map { it.spec.spec },
                )
            orderedIds.mapNotNull { id ->
                when (val value = values[id]) {
                    is AxQsGridValue.Tile ->
                        AxQsGridItem<AxQsGridValue>(
                            id = id,
                            span =
                                axQsViewModel
                                    .span(id, layout, AxQsSpan.TileDefault)
                                    .coerceTileSpan(columns),
                            minSpan = AxQsSpan.TileDefault,
                            maxSpan = AxQsSpan(columns = minOf(2, columns), rows = 2),
                            value = value,
                            position = positions[id],
                        )
                    is AxQsGridValue.Control -> {
                        val controlSpans = value.control.spans(columns)
                        AxQsGridItem<AxQsGridValue>(
                            id = id,
                            span =
                                axQsViewModel.span(id, layout, controlSpans.default).let {
                                    value.control.coerceSpan(it, columns)
                                },
                            minSpan = controlSpans.min,
                            maxSpan = controlSpans.max,
                            value = value,
                            position = positions[id],
                        )
                    }
                    is AxQsGridValue.Stack ->
                        AxQsGridItem<AxQsGridValue>(
                            id = id,
                            span = AxQsSpan(columns = 2, rows = 2),
                            minSpan = AxQsSpan(columns = 2, rows = 2),
                            maxSpan = AxQsSpan(columns = 2, rows = 2),
                            value = value,
                            position = positions[id],
                        )
                    null -> null
                }
            }
        }

    val editController =
        rememberAxGridEditController(
            gridItems = gridItems,
            layout = layout,
            columns = columns,
            isEditing = isEditing,
            allEditTiles = allEditTiles,
            tiles = tiles,
            axQsViewModel = axQsViewModel,
            editModeViewModel = editModeViewModel,
            resetEpoch = resetEpoch.intValue,
        )

    DisposableEffect(isEditing) {
        onDispose {
            if (editController.listState.dragInProgress) {
                view.cancelDragAndDrop()
                editController.listState.cancel()
            }
        }
    }

    if (editController.showResetDialog.value) {
        AlertDialog(
            onDismissRequest = { editController.showResetDialog.value = false },
            title = { Text(stringResource(InternalR.string.reset)) },
            text = { Text("Reset all Quick Settings tiles to default layout?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editModeViewModel.resetTiles()
                        axQsViewModel.resetLayout()
                        resetEpoch.intValue++
                        editController.showResetDialog.value = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(InternalR.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { editController.showResetDialog.value = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    val separateMode = axQsViewModel.panelMode == AxQsPanelMode.SEPARATE && !splitShade
    val isDirectQs = axQsViewModel.isQsBypassingShade
    val isQsScene by remember(isDirectQs, separateMode) {
        derivedStateOf {
            isDirectQs || (separateMode && viewModel.expansionState.progress > 0.001f)
        }
    }
    val shouldRenderGrid = !separateMode || isQsScene
    val qsEntranceProgress = {
        if (isDirectQs) {
            viewModel.quickQuickSettingsViewModel.squishinessViewModel.squishiness.value
        } else {
            viewModel.expansionState.progress.coerceIn(0f, 1f)
        }
    }
    val gridEntranceModifier =
        if (separateMode) {
            if (isDirectQs) {
                Modifier.axQsEntrance(includeTranslation = false, progress = qsEntranceProgress)
            } else {
                Modifier.graphicsLayer {
                    val progress = viewModel.expansionState.progress.coerceIn(0f, 1f)
                    val startThreshold = AxQsTokens.Animation.SEPARATE_GRID_ENTRANCE_START
                    val endThreshold = AxQsTokens.Animation.SEPARATE_GRID_ENTRANCE_END
                    if (progress <= startThreshold) {
                        alpha = 0f
                        scaleX = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
                        scaleY = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
                        translationY = with(density) { (-24.dp).toPx() }
                    } else {
                        val deferredFraction =
                            ((progress - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
                        val easedGrid = AxQsTokens.Animation.AX_RESIZE_EASING.transform(deferredFraction)
                        val minScale = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
                        alpha = easedGrid
                        val scale = minScale + (1f - minScale) * easedGrid
                        scaleX = scale
                        scaleY = scale
                        translationY = with(density) { (-24.dp * (1f - easedGrid)).toPx() }
                    }
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
        } else {
            Modifier
        }

    val expansionProgress = viewModel.expansionState.progress
    val expansionMode = remember { mutableStateOf(AxQsExpansionMode.QQS) }
    LaunchedEffect(expansionProgress) {
        val newMode = computeHysteresisMode(expansionProgress, expansionMode.value)
        if (newMode != expansionMode.value) {
            expansionMode.value = newMode
        }
    }

    val qqsMaxRows = axQsViewModel.qqsMaxRows
    val isQsExpandingOrVisible by remember(splitShade, separateMode, isDirectQs) {
        derivedStateOf {
            splitShade ||
                viewModel.isEditing ||
                (if (separateMode) isDirectQs || viewModel.expansionState.progress > 0.001f
                 else viewModel.expansionState.progress > 0.001f)
        }
    }

    val listeningTiles =
        remember(gridItems, qqsMaxRows, isQsExpandingOrVisible) {
            val candidateItems =
                if (isQsExpandingOrVisible || splitShade) {
                    gridItems
                } else {
                    gridItems.filter { item ->
                        val row = item.position?.row ?: 0
                        row < qqsMaxRows && (row + item.span.rows <= qqsMaxRows)
                    }
                }
            candidateItems.flatMap { item ->
                when (val v = item.value) {
                    is AxQsGridValue.Tile -> listOfNotNull(v.viewModel)
                    is AxQsGridValue.Stack -> v.tiles
                    else -> emptyList()
                }
            }
        }

    QuickSettingsTheme {
        BoxWithConstraints(modifier) {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val contentPadding = LocalAxQsModularSidePadding.current
            val gridWidth = (maxWidth - contentPadding * 2).coerceAtLeast(0.dp)
            val spacing = AxQsTokens.Geometry.TileSpacing
            val tileScale = LocalTileScale.current
            val cellConfig =
                calculateAxQsCellConfig(
                    gridWidth = gridWidth,
                    columns = columns,
                    spacing = spacing,
                    cellHeight = AxQsCellConfig.Defaults.TileHeight * tileScale,
                    densityScale = tileScale,
                    density = density.density,
                    fontScale = density.fontScale,
                    densityDpi = configuration.densityDpi,
                )
            val effectiveRowHeight = cellConfig.rowHeight
            val contentTopPadding =
                if (splitShade) {
                    AxQuickSettingsLayoutDefaults.LandscapeHeaderHeight +
                        AxQuickSettingsLayoutDefaults.LandscapeHeaderContentSpacing
                } else {
                    0.dp
                }

            val separateMediaVisible =
                separateMode &&
                    !isDirectQs &&
                    mediaViewModel.hasVisibleSessions(AxMediaSurface.SEPARATE_QQS)
            LaunchedEffect(
                headerTopPadding,
                effectiveRowHeight,
                spacing,
                density,
                separateMode,
                isDirectQs,
                separateMediaVisible,
                axQsViewModel.qqsMaxRows,
            ) {
                if (!splitShade) {
                    val qqsHeightPx =
                        with(density) {
                            if (separateMode && !isDirectQs) {
                                if (separateMediaVisible) {
                                    (headerTopPadding +
                                            48.dp +
                                            spacing +
                                            nonQsGridMediaHeight +
                                            spacing)
                                        .roundToPx()
                                } else {
                                    (headerTopPadding + 48.dp + 8.dp).roundToPx()
                                }
                            } else {
                                val currentQqsMaxRows = axQsViewModel.qqsMaxRows
                                val qqsSpacing =
                                    if (currentQqsMaxRows > 1) spacing * (currentQqsMaxRows - 1) else 0.dp
                                val tilesAndHandleHeight =
                                    (effectiveRowHeight * currentQqsMaxRows +
                                            qqsSpacing +
                                            spacing +
                                            DragHandleHeight +
                                            DragHandleBottomPadding)
                                        .roundToPx()
                                (headerTopPadding + 48.dp + spacing).roundToPx() +
                                    tilesAndHandleHeight
                            }
                        }
                    viewModel.qqsHeight = qqsHeightPx
                }
            }

            CompositionLocalProvider(LocalAxQsCellConfig provides cellConfig) {
                Column(
                    modifier =
                        Modifier.fillMaxSize().thenIf(splitShade) {
                            Modifier.padding(top = contentTopPadding.coerceAtLeast(0.dp))
                        }
                ) {
                    val showSeparateMedia =
                        separateMode &&
                            !isDirectQs &&
                            mediaViewModel.hasVisibleSessions(AxMediaSurface.SEPARATE_QQS)
                    val isSeparateMediaVisible by remember(showSeparateMedia) {
                        derivedStateOf {
                            showSeparateMedia &&
                                viewModel.expansionState.progress <
                                    AxQsTokens.Animation.SEPARATE_MEDIA_FADE_END
                        }
                    }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .thenIf(!shouldRenderGrid && !isSeparateMediaVisible) { Modifier.wrapContentHeight() }
                                .thenIf(shouldRenderGrid || isSeparateMediaVisible) { Modifier.weight(1f) }
                    ) {
                        AnimatedContent(
                            targetState = isEditing,
                            transitionSpec = {
                                val enterSpec =
                                    spring<Float>(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    )
                                val exitSpec =
                                    spring<Float>(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    )
                                (fadeIn(animationSpec = enterSpec) +
                                    scaleIn(initialScale = 0.96f, animationSpec = enterSpec))
                                    .togetherWith(
                                        fadeOut(animationSpec = exitSpec) +
                                            scaleOut(targetScale = 0.96f, animationSpec = exitSpec)
                                    )
                            },
                            modifier =
                                Modifier.fillMaxWidth().padding(horizontal = contentPadding.coerceAtLeast(0.dp)),
                            label = "AxHeaderMorph",
                        ) { editing ->
                            if (editing) {
                                AxQsEditHeader(
                                    onDone = editModeViewModel::stopEditing,
                                    onReset = { editController.showResetDialog.value = true },
                                    onOpenPanelSettings = onOpenPanelSettings,
                                    isLandscape = splitShade,
                                )
                            } else {
                                AxQsDateHeader(
                                    toolbarViewModel = viewModel.toolbarViewModel,
                                    shadeHeaderViewModel =
                                        viewModel.containerViewModel.shadeHeaderViewModel,
                                    showEdit = !separateMode || isQsScene,
                                    isFullyVisible = {
                                        viewModel.isQsFullyExpanded && !viewModel.isEditing
                                    },
                                    editButtonProgress = qsEntranceProgress,
                                )
                            }
                        }

                        if (shouldRenderGrid || isSeparateMediaVisible) {
                            Spacer(Modifier.height(spacing))
                            val dropTargetModifier =
                                if (isEditing) {
                                    Modifier.axQsDropTarget(
                                        state = editController.listState,
                                        section = AxQsGridSection.TILES,
                                        canStack = editController::canStack,
                                        onStack = editController::onStack,
                                        onDrop = editController::saveOrders,
                                    )
                                } else {
                                    Modifier
                                }
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .fillMaxWidth()
                            ) {
                                if (shouldRenderGrid) {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxSize()
                                                .padding(horizontal = contentPadding.coerceAtLeast(0.dp))
                                                .then(gridEntranceModifier)
                                                .then(dropTargetModifier)
                                    ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .onGloballyPositioned { viewportBounds.value = it.boundsInRoot() }
                                            .clipScrollableContainer(Orientation.Vertical)
                                            .verticalScroll(scrollState, overscrollEffect = null)
                                ) {
                                    val gridItemsToRender = if (isEditing) editController.listState.items else gridItems
                                    if (gridItemsToRender.isNotEmpty()) {
                                        AxQsGrid(
                                            items = gridItemsToRender,
                                            columns = columns,
                                            rowHeight = effectiveRowHeight,
                                            spacing = spacing,
                                            maxRows = null,
                                            staticItemId = if (isEditing) editController.listState.draggedId else null,
                                            animateItemBounds = isEditing,
                                            onItemBounds = { id, bounds ->
                                                if (isEditing) {
                                                    editController.listState.updateItemBounds(id, AxQsGridSection.TILES, bounds)
                                                }
                                            },
                                            onCells = { cells ->
                                                if (isEditing) {
                                                    editController.listState.updateGridCells(AxQsGridSection.TILES, cells)
                                                }
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth().onGloballyPositioned {
                                                    if (isEditing) {
                                                        editController.listState.updateGridOrigin(AxQsGridSection.TILES, it.positionInRoot())
                                                    }
                                                },
                                            content = { item ->
                                                AxGridItemContent(
                                                    item = item,
                                                    isEditing = isEditing,
                                                    editController = editController,
                                                    layout = layout,
                                                    columns = columns,
                                                    separateMode = separateMode,
                                                    isQsExpandingOrVisible = isQsExpandingOrVisible,
                                                    expansionMode = expansionMode.value,
                                                    viewModel = viewModel,
                                                    axQsViewModel = axQsViewModel,
                                                    mediaViewModel = mediaViewModel,
                                                    detailsViewModel = detailsViewModel,
                                                    brightnessSliderViewModel = brightnessSliderViewModel,
                                                    volumeSliderViewModel = volumeSliderViewModel,
                                                    listening = listening,
                                                )
                                            },
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isEditing,
                                        enter =
                                            slideInVertically(
                                                initialOffsetY = { it / 4 },
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                            ) + fadeIn(
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                            ),
                                        exit =
                                            slideOutVertically(
                                                targetOffsetY = { it / 4 },
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                            ) + fadeOut(
                                                animationSpec =
                                                    spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                            ),
                                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                    ) {
                                        if (isEditing) {
                                            val pickerColumns = axQsViewModel.defaultColumns(gridLayout)
                                            val currentIds = editController.listState.items.map { it.id }.toSet()
                                            AxAvailableControls(
                                                allTiles = allEditTiles ?: emptyList(),
                                                currentIds = currentIds,
                                                columns = pickerColumns,
                                                verticalSliderStyle = { control ->
                                                    axQsViewModel.verticalSliderStyle(layout, control)
                                                },
                                                onVerticalSliderStyleChanged = { control, style ->
                                                    axQsViewModel.setVerticalSliderStyle(layout, control, style)
                                                },
                                                canAdd = { true },
                                                onAddTile = editController::addTile,
                                                onAddControl = { control, _ -> editController.addControl(control) },
                                                onAddStack = editController::addStack,
                                                settings = {
                                                    AxQsGridSettings(layout = gridLayout, viewModel = axQsViewModel)
                                                },
                                            )
                                        }
                                    }
                                    if (isEditing) {
                                        val navBarBottom =
                                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                        Spacer(Modifier.height(32.dp + navBarBottom))
                                    }
                                }
                                if (isEditing) {
                                    AxQsDragAutoScroll(editController.listState, scrollState, viewportBounds.value)
                                }
                                if (!separateMode && !splitShade && !isEditing) {
                                    val isDragHandleVisible by remember {
                                        derivedStateOf { viewModel.expansionState.progress < 0.25f }
                                    }
                                    if (isDragHandleVisible) {
                                        val currentQqsMaxRows = axQsViewModel.qqsMaxRows
                                        val qqsSpacing =
                                            if (currentQqsMaxRows > 1) spacing * (currentQqsMaxRows - 1) else 0.dp
                                        val qqsDragHandleTop =
                                            effectiveRowHeight * currentQqsMaxRows +
                                                qqsSpacing +
                                                spacing
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .offset(y = qqsDragHandleTop)
                                                .zIndex(1f)
                                                .graphicsLayer {
                                                    alpha =
                                                        (1f - (viewModel.expansionState.progress / 0.25f))
                                                            .coerceIn(0f, 1f)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.width(DragHandleWidth)
                                                    .height(DragHandleHeight)
                                                    .background(
                                                        colorResource(
                                                            SysuiR.color.ax_qqs_drag_handle
                                                        ),
                                                        RoundedCornerShape(
                                                            AxQsTokens.CornerRadius
                                                                .DragHandleCornerRadius
                                                        ),
                                                    )
                                            )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isSeparateMediaVisible) {
                                    val defaultQsSidePadding =
                                        if (splitShade) {
                                            AxQuickSettingsLayoutDefaults.LandscapeSidePadding
                                        } else {
                                            AxQuickSettingsLayoutDefaults.PortraitSidePadding
                                        }
                                    Element(
                                        key = SeparateModeMedia,
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .align(Alignment.TopCenter)
                                                .padding(horizontal = defaultQsSidePadding.coerceAtLeast(0.dp))
                                                .graphicsLayer {
                                                    val progress = viewModel.expansionState.progress
                                                    val fadeEnd = AxQsTokens.Animation.SEPARATE_MEDIA_FADE_END
                                                    val mediaProgress = (progress / fadeEnd).coerceIn(0f, 1f)
                                                    val easedMediaProgress =
                                                        AxQsTokens.Animation.AX_RESIZE_EASING.transform(mediaProgress)
                                                    alpha = (1f - easedMediaProgress).coerceIn(0f, 1f)
                                                    val minScale = AxQsTokens.Animation.AX_ENTRANCE_MIN_SCALE
                                                    val scale = 1f - (1f - minScale) * easedMediaProgress
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationY = with(density) { (48.dp * easedMediaProgress).toPx() }
                                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                                },
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .height(nonQsGridMediaHeight),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AxMediaPanel(
                                                viewModel = mediaViewModel,
                                                span = AxQsSpan(columns, 2),
                                                mediaViewModelFactory = viewModel.mediaViewModelFactory,
                                                modifier = Modifier.fillMaxSize(),
                                                surface = AxMediaSurface.SEPARATE_QQS,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    TileListener(
        tiles = listeningTiles,
        listeningEnabled = { (!separateMode || isQsScene) && listening() },
    )
}

private val DragHandleWidth = 72.dp
private val DragHandleHeight = 5.dp
private val DragHandleBottomPadding = 12.dp
