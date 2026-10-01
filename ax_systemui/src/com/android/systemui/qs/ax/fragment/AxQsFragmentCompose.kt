/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.ax.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.util.fastRoundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced
import com.android.compose.PlatformSliderDefaults
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.mechanics.TileRevealFlag
import com.android.compose.animation.scene.mechanics.rememberGestureContext
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.modifiers.height
import com.android.compose.modifiers.thenIf
import com.android.compose.theme.PlatformTheme
import com.android.mechanics.compose.modifier.motionDriver
import com.android.systemui.Dumpable
import com.android.systemui.Flags
import com.android.systemui.Flags.notificationShadeBlur
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import com.android.systemui.initOnBackPressedDispatcherOwner
import com.android.systemui.keyboard.shortcut.ui.composable.InteractionsConfig
import com.android.systemui.keyboard.shortcut.ui.composable.ProvideShortcutHelperIndication
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.setSnapshotBinding
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.plugins.qs.QS
import com.android.systemui.plugins.qs.QSContainerController
import com.android.systemui.qs.ax.fragment.AxSceneKeys.QuickSettings
import com.android.systemui.qs.ax.fragment.AxSceneKeys.debugName
import com.android.systemui.qs.ax.fragment.viewmodel.AxQsFragmentComposeViewModel
import com.android.systemui.qs.ax.pressfeedback.AxPressFeedbackHelper
import com.android.systemui.qs.ax.pressfeedback.LocalAxPressFeedbackHelper
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.AxQsControl
import com.android.systemui.qs.ax.shared.model.AxQsGridLayout
import com.android.systemui.qs.ax.shared.model.AxQsLayout
import com.android.systemui.qs.ax.shared.model.AxQsPanelMode
import com.android.systemui.qs.ax.shared.model.LocalAxQsLayout
import com.android.systemui.qs.ax.tiles.ringer.LocalRingerSliderViewModel
import com.android.systemui.qs.ax.tiles.ringer.RingerSliderViewModel
import com.android.systemui.qs.ax.ui.controls.AxControlViewModels
import com.android.systemui.qs.ax.ui.controls.LocalAxControlViewModels
import com.android.systemui.qs.ax.ui.grid.AxOneGrid
import com.android.systemui.qs.ax.ui.grid.DensityScopeProvider
import com.android.systemui.qs.ax.ui.grid.LocalTileScale
import com.android.systemui.qs.ax.ui.grid.computeTileScale
import com.android.systemui.qs.ax.ui.header.AxQuickSettingsHeader
import com.android.systemui.qs.ax.ui.header.AxQuickSettingsLayoutDefaults
import com.android.systemui.qs.ax.ui.header.LocalAxQsModularSidePadding
import com.android.systemui.qs.ax.ui.panels.AxQsPanelSettings
import com.android.systemui.qs.ax.ui.panels.axQsEntrance
import com.android.systemui.qs.ax.ui.panels.axQuickQuickSettingsToQuickSettings
import com.android.systemui.qs.ax.ui.panels.fromAxPanelSettings
import com.android.systemui.qs.ax.ui.panels.shouldComposeLiveAxQs
import com.android.systemui.qs.ax.ui.panels.toAxPanelSettings
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel
import com.android.systemui.qs.ax.ui.viewmodel.AxQsViewModel
import com.android.systemui.qs.composefragment.LocalBlurEnabled
import com.android.systemui.qs.composefragment.ui.NotificationScrimClipParams
import com.android.systemui.qs.flags.QSComposeFragment
import com.android.systemui.qs.panels.shared.model.QSFragmentComposeClippingTableLog
import com.android.systemui.qs.panels.ui.compose.TileDetails
import com.android.systemui.qs.panels.ui.viewmodel.DetailsViewModel
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.composable.QuickSettingsShade
import com.android.systemui.qs.ui.composable.QuickSettingsShade.systemGestureExclusionInShade
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.ShadeHeaderController
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shade.ui.composable.ShadeHeader
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.phone.ui.TintedIconManager
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
import com.android.systemui.util.LifecycleFragment
import com.android.systemui.util.asIndenting
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.printSection
import com.android.systemui.util.println
import com.android.systemui.volume.domain.interactor.VolumePanelNavigationInteractor
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider
import com.android.systemui.volume.ui.navigation.VolumeNavigator
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import java.io.PrintWriter
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

val LocalQsScrolling = compositionLocalOf { false }

@SuppressLint("ValidFragment")
class AxQsFragmentCompose
@Inject
constructor(
    private val qsFragmentComposeViewModelFactory: AxQsFragmentComposeViewModel.Factory,
    @param:QSFragmentComposeClippingTableLog private val qsClippingTableLogBuffer: TableLogBuffer,
    private val dumpManager: DumpManager,
    @param:Background private val backgroundDispatcher: CoroutineDispatcher,
    private val axMediaViewModel: AxMediaViewModel,
    private val axQsViewModel: AxQsViewModel,
    private val detailsViewModel: DetailsViewModel,
    @param:ShadeDisplayAware private val configurationController: ConfigurationController,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    private val ringerSliderViewModel: RingerSliderViewModel,
    private val tintedIconManagerFactory: TintedIconManager.Factory,
    private val shadeHeaderController: ShadeHeaderController,
    private val volumeNavigator: VolumeNavigator,
    private val volumePanelNavigationInteractor: VolumePanelNavigationInteractor,
    private val pressFeedbackHelper: AxPressFeedbackHelper,
) : LifecycleFragment(), QS, Dumpable {

    private val scrollListener = MutableStateFlow<QS.ScrollListener?>(null)
    private val collapsedMediaVisibilityChangedListener = MutableStateFlow<Consumer<Boolean>?>(null)
    private val qqsVisible = MutableStateFlow(false)
    private val heightListener = MutableStateFlow<QS.HeightListener?>(null)
    private val qqsHeightListener = MutableStateFlow<QS.QqsHeightListener?>(null)
    private val qsContainerController = MutableStateFlow<QSContainerController?>(null)
    private lateinit var viewModel: AxQsFragmentComposeViewModel

    private val qqsPositionOnRoot = Rect()
    private val composeViewPositionOnScreen = Rect()
    private val scrollState = ScrollState(0)
    private val locationTemp = IntArray(2)
    private var bottomContentPadding by mutableIntStateOf(0)
    private val containerView: AxFrameLayoutTouchPassthrough?
        get() = view as? AxFrameLayoutTouchPassthrough

    override fun onStart() {
        super.onStart()
        registerDumpable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        QSComposeFragment.isUnexpectedlyInLegacyMode()
        viewModel = qsFragmentComposeViewModelFactory.create(lifecycleScope)

        setListenerCollections()
        lifecycleScope.launch { viewModel.activate() }
        lifecycleScope.launch { axQsViewModel.activate() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val context = inflater.context
        val scrollEndSlop = ViewConfiguration.get(context).scaledTouchSlop
        val composeView =
            ComposeView(context).apply {
                id = R.id.quick_settings_container
                repeatWhenAttached {
                    repeatOnLifecycle(Lifecycle.State.CREATED) {
                        initOnBackPressedDispatcherOwner(this@repeatWhenAttached.lifecycle)
                        setContent {
                            this@AxQsFragmentCompose.Content(Modifier.sysUiResTagContainer())
                        }
                    }
                }
            }

        val canScrollQs =
            object : AxCanScrollQs {
                override fun forward(): Boolean {
                    return (scrollState.maxValue - scrollState.value > scrollEndSlop &&
                        viewModel.isQsFullyExpanded) || isCustomizing
                }

                override fun backward(): Boolean {
                    return (scrollState.canScrollBackward && viewModel.isQsFullyExpanded) ||
                        isCustomizing
                }
            }

        val frame =
            AxFrameLayoutTouchPassthrough(
                context,
                // Only allow scrolling when we are fully expanded. That way, we don't intercept
                // swipes in lockscreen (when somehow QS is receiving touches).
                canScrollQs,
                viewModel::emitMotionEventForFalsingSwipeNested,
                qsClippingTableLogBuffer,
                backgroundDispatcher,
                isInBottomReservedArea = { _, y ->
                    if (viewModel.isEditing) {
                        val insets = composeView.rootWindowInsets
                        val navBarHeight =
                            insets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
                        navBarHeight > 0 && y >= composeView.height - navBarHeight
                    } else {
                        false
                    }
                },
            )
        frame.addView(
            composeView,
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        return frame
    }

    @Composable
    private fun Content(modifier: Modifier = Modifier) {
        val isBlurCurrentlySupported by
            windowRootViewBlurInteractor.isBlurCurrentlySupported.collectAsStateWithLifecycle()
        val blurEnabled = notificationShadeBlur() && isBlurCurrentlySupported
        val showLandscapeQqs =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                viewModel.isPanelExpanded &&
                !viewModel.isQsExpanded &&
                !axQsViewModel.isQsBypassingShade &&
                !axQsViewModel.holdQsSceneDuringCollapse
        val showQuickSettings = viewModel.isQsVisibleAndAnyShadeExpanded || showLandscapeQqs
        PlatformTheme(isDarkTheme = if (notificationShadeBlur()) isSystemInDarkTheme() else true) {
            ProvideShortcutHelperIndication(interactionsConfig = interactionsConfig()) {
                Box(
                    modifier =
                        modifier
                            .layout { measurable, constraints ->
                                measurable.measure(constraints).run {
                                    layout(width, height) {
                                        if (showQuickSettings) {
                                            place(0, 0)
                                        }
                                    }
                                }
                            }
                            .graphicsLayer { alpha = viewModel.viewAlpha }
                            .thenIf(!Flags.notificationShadeBlur()) {
                                Modifier.offset {
                                    IntOffset(
                                        x = 0,
                                        y = viewModel.viewTranslationY.fastRoundToInt(),
                                    )
                                }
                            }
                            // Disable touches in the whole composable while the mirror is
                            // showing. While the mirror is showing, an ancestor of the
                            // ComposeView is made alpha 0, but touches are still being captured
                            // by the composables.
                            .thenIf(viewModel.showingMirror) { Modifier.gesturesDisabled() }
                ) {
                    DensityScopeProvider {
                        val tileScale = computeTileScale()
                        CompositionLocalProvider(
                            LocalTileScale provides tileScale,
                            LocalBlurEnabled provides blurEnabled,
                            LocalQsScrolling provides scrollState.isScrollInProgress,
                            LocalRingerSliderViewModel provides ringerSliderViewModel,
                            LocalAxPressFeedbackHelper provides pressFeedbackHelper,
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                        ) {
                            WithStatusIconContext(tintedIconManagerFactory) {
                                CollapsableQuickSettingsSTL(showQuickSettings)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * STL that contains both QQS (tiles) and QS (brightness, tiles, footer actions), but no Edit
     * mode. It tracks [QSFragmentComposeViewModel.expansionState] to drive the transition between
     * [AxSceneKeys.QuickQuickSettings] and [AxSceneKeys.QuickSettings].
     */
    @Composable
    private fun CollapsableQuickSettingsSTL(showQuickSettings: Boolean) {
        var panelSettingsOpen by remember { mutableStateOf(false) }
        val qqsSquishiness by
            viewModel.quickQuickSettingsViewModel.squishinessViewModel.squishiness
                .collectAsStateWithLifecycle()
        val nextCookie = remember {
            object {
                var value = 0
            }
        }
        val transitionToCookie = remember { mutableMapOf<TransitionState.Transition, Int>() }

        val isSeparateMode =
            axQsViewModel.panelMode == AxQsPanelMode.SEPARATE && !viewModel.isInSplitShade

        val sceneState =
            rememberMutableSceneTransitionLayoutState(
                initialScene = AxSceneKeys.QuickSettings,
                transitions =
                    transitions {
                        from(AxSceneKeys.QuickQuickSettings, QuickSettings) {
                            axQuickQuickSettingsToQuickSettings(
                                isSeparateMode = { isSeparateMode },
                            )
                        }
                        from(QuickSettings, AxSceneKeys.QuickQuickSettings) {
                            axQuickQuickSettingsToQuickSettings(
                                isSeparateMode = { isSeparateMode },
                            )
                        }
                        from(QuickSettings, AxSceneKeys.PanelSettings) {
                            spec = tween(durationMillis = 300)
                            toAxPanelSettings()
                        }
                        from(AxSceneKeys.PanelSettings, QuickSettings) {
                            spec = tween(durationMillis = 300)
                            fromAxPanelSettings()
                        }
                    },
                onTransitionStart = { transition ->
                    val cookie = nextCookie.value++
                    transitionToCookie[transition] = cookie
                    Trace.beginAsyncSection(
                        "CollapsableQuickSettingsSTL ${transition.debugName}",
                        cookie,
                    )
                },
                onTransitionEnd = { transition ->
                    Trace.endAsyncSection(
                        "CollapsableQuickSettingsSTL ${transition.debugName}",
                        transitionToCookie.remove(transition) ?: -1,
                    )
                },
                deferTransitionProgress = true,
            )

        LaunchedEffect(showQuickSettings) {
            snapshotFlow {
                    useOverlayShadeHeader() && showQuickSettings && viewModel.viewAlpha > 0f
                }
                .collect { shadeHeaderController.setOverlayShadeHeaderActive(it) }
        }
        DisposableEffect(Unit) {
            onDispose { shadeHeaderController.setOverlayShadeHeaderActive(false) }
        }

        LaunchedEffect(Unit) {
            launch {
                synchronizeQsState(
                    sceneState,
                    snapshotFlow { panelSettingsOpen },
                )
            }
            launch {
                viewModel.containerViewModel.editModeViewModel.isEditing.collect { editing ->
                    if (!editing) panelSettingsOpen = false
                }
            }
            // Normally, the Edit mode will stop if the composable leaves, but if the shade
            // is closed, because we are always composed, we don't stop edit mode.
            launch {
                snapshotFlow { viewModel.isQsVisibleAndAnyShadeExpanded }
                    .collect {
                        if (!it) {
                            viewModel.containerViewModel.editModeViewModel.stopEditing()
                        }
                    }
            }
            launch {
                snapshotFlow { viewModel.isQsFullyExpanded }
                    .collect {
                        if (!it && viewModel.isEditing) {
                            viewModel.containerViewModel.editModeViewModel.stopEditing()
                        }
                    }
            }
        }

        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val layout =
            if (isLandscape || viewModel.isInSplitShade) {
                AxQsLayout.SPLIT_SHADE
            } else {
                AxQsLayout.QS
            }
        val gridLayout = AxQsGridLayout.from(layout)
        val columns = axQsViewModel.columns(gridLayout)
        val tileScale = LocalTileScale.current
        val modularSidePadding =
            AxQuickSettingsLayoutDefaults.modularSidePadding(
                maxWidth = LocalConfiguration.current.screenWidthDp.dp,
                landscape = isLandscape || viewModel.isInSplitShade,
                tileScale = tileScale,
                columns = columns,
            )

        val controlViewModels =
            remember(
                viewModel.containerViewModel.brightnessSliderViewModel,
                viewModel.volumeSliderViewModel,
                axMediaViewModel,
            ) {
                AxControlViewModels(
                    brightnessSliderViewModel =
                        viewModel.containerViewModel.brightnessSliderViewModel,
                    volumeViewModel = viewModel.volumeSliderViewModel,
                    mediaViewModel = axMediaViewModel,
                )
            }

        CompositionLocalProvider(
            LocalAxQsLayout provides layout,
            LocalAxQsModularSidePadding provides modularSidePadding,
            LocalAxControlViewModels provides controlViewModels,
        ) {
            Box(
                Modifier.fillMaxSize().thenIf(sceneState.shouldComposeLiveAxQs()) {
                    Modifier.axQsEntrance(includeTranslation = !isSeparateMode) { qqsSquishiness }
                }
            ) {
                SceneTransitionLayout(state = sceneState, modifier = Modifier.fillMaxSize()) {
                    scene(QuickSettings, alwaysCompose = true) {
                        if (sceneState.shouldComposeLiveAxQs()) {
                            LaunchedEffect(Unit) { viewModel.onQSOpen() }
                            val gestureContext = rememberGestureContext()
                            Element(
                                QuickSettings.rootElementKey,
                                Modifier.fillMaxSize().thenIf(TileRevealFlag.isEnabled) {
                                    Modifier.motionDriver(gestureContext, label = "QuickSettings")
                                },
                            ) {
                                QuickSettingsElement(
                                    onOpenPanelSettings = { panelSettingsOpen = true },
                                )
                            }
                        }
                    }

                    scene(AxSceneKeys.PanelSettings) {
                        Element(AxSceneKeys.PanelSettings.rootElementKey, Modifier) {
                            PanelSettingsElement(onDismiss = { panelSettingsOpen = false })
                        }
                    }
                }
                val editHeaderAlpha by
                    animateFloatAsState(
                        targetValue = if (viewModel.isEditing || panelSettingsOpen) 0f else 1f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        label = "statusHeaderAlpha",
                    )
                if (
                    useOverlayShadeHeader() &&
                        sceneState.shouldComposeLiveAxQs() &&
                        editHeaderAlpha > 0f
                ) {
                    QuickSettingsStatusOverlayHeader(
                        headerViewModel = viewModel.containerViewModel.shadeHeaderViewModel,
                        isTransitioning = false,
                        modifier =
                            Modifier.graphicsLayer {
                                alpha = editHeaderAlpha
                            },
                    )
                }
            }
        }
    }

    override fun setPanelView(notificationPanelView: QS.HeightListener?) {
        heightListener.value = notificationPanelView
    }

    override fun setQqsHeightListener(listener: QS.QqsHeightListener?) {
        qqsHeightListener.value = listener
    }

    override fun hideImmediately() {
        //        view?.animate()?.cancel()
        //        view?.y = -qsMinExpansionHeight.toFloat()
    }

    override fun getQsMinExpansionHeight(): Int {
        return if (viewModel.isInSplitShade) {
            getQsMinExpansionHeightForSplitShade()
        } else {
            viewModel.qqsHeight
        }
    }

    /**
     * Returns the min expansion height for split shade.
     *
     * On split shade, QS is always expanded and goes from the top of the screen to the bottom of
     * the QS container.
     */
    private fun getQsMinExpansionHeightForSplitShade(): Int {
        view?.getLocationOnScreen(locationTemp)
        val top = locationTemp.get(1)
        // We want to get the original top position, so we subtract any translation currently set.
        val originalTop = (top - (view?.translationY ?: 0f)).toInt()
        // On split shade the QS view doesn't start at the top of the screen, so we need to add the
        // top margin.
        return originalTop + (view?.height ?: 0)
    }

    override fun getDesiredHeight(): Int {
        /*
         * Looking at the code, it seems that
         * * If customizing, then the height is that of the view post-layout, which is set by
         *   QSContainerImpl.calculateContainerHeight, which is the height the customizer takes
         * * If not customizing, it's the measured height. So we may want to surface that.
         */
        return view?.height ?: 0
    }

    override fun setHeightOverride(desiredHeight: Int) {
        viewModel.heightOverride = desiredHeight
    }

    override fun setHeaderClickable(qsExpansionEnabled: Boolean) {
        // Empty method
    }

    override fun isCustomizing(): Boolean {
        return viewModel.isEditing
    }

    override fun closeCustomizer() {
        viewModel.containerViewModel.editModeViewModel.stopEditing()
    }

    override fun setOverscrolling(overscrolling: Boolean) {
        viewModel.isStackScrollerOverscrolling = overscrolling
    }

    override fun setPanelExpanded(panelExpanded: Boolean) {
        viewModel.isPanelExpanded = panelExpanded
        if (!panelExpanded) {
            axQsViewModel.clearCollapseGuard()
        }
        if (!panelExpanded && !viewModel.isInSplitShade) {
            viewModel.resetCollapsedExpansionState()
        }
    }

    override fun setExpanded(qsExpanded: Boolean) {
        viewModel.isQsExpanded = qsExpanded
        if (!qsExpanded) {
            axQsViewModel.clearCollapseGuard()
            if (!viewModel.isInSplitShade && !viewModel.isPanelExpanded) {
                viewModel.resetCollapsedExpansionState()
            }
        }
    }

    override fun setListening(listening: Boolean) {
        // Not needed, views start listening and collection when composed
    }

    override fun setQsVisible(qsVisible: Boolean) {
        containerView?.qsVisible = qsVisible
        viewModel.isQsVisible = qsVisible
    }

    override fun isShowingDetail(): Boolean {
        return isCustomizing
    }

    override fun closeDetail() {
        closeCustomizer()
    }

    override fun animateHeaderSlidingOut() {
        // TODO(b/353254353)
    }

    override fun setQsExpansion(
        qsExpansionFraction: Float,
        panelExpansionFraction: Float,
        headerTranslation: Float,
        squishinessFraction: Float,
    ) {
        viewModel.setQsExpansionValue(qsExpansionFraction)
        viewModel.panelExpansionFraction = panelExpansionFraction
        viewModel.squishinessFraction = squishinessFraction
        viewModel.proposedTranslation = headerTranslation
    }

    override fun setForceQsEvent(forceQsEvent: Boolean) {
        axQsViewModel.setForceQsEvent(forceQsEvent)
    }

    override fun setHeaderListening(listening: Boolean) {
        // Not needed, header will start listening as soon as it's composed
    }

    override fun notifyCustomizeChanged() {
        // Not needed, only called from inside customizer
    }

    override fun setContainerController(controller: QSContainerController?) {
        qsContainerController.value = controller
    }

    override fun setCollapseExpandAction(action: Runnable?) {
        viewModel.collapseExpandAccessibilityAction = action
    }

    override fun getHeightDiff(): Int {
        return viewModel.heightDiff
    }

    override fun getHeader(): View? {
        QSComposeFragment.isUnexpectedlyInLegacyMode()
        return null
    }

    override fun setShouldUpdateSquishinessOnMedia(shouldUpdate: Boolean) {}

    override fun setInSplitShade(isInSplitShade: Boolean) {
        viewModel.isInSplitShade = isInSplitShade
    }

    override fun setTransitionToFullShadeProgress(
        isTransitioningToFullShade: Boolean,
        qsTransitionFraction: Float,
        qsSquishinessFraction: Float,
    ) {
        viewModel.isTransitioningToFullShade = isTransitioningToFullShade
        viewModel.lockscreenToShadeProgress = qsTransitionFraction
        if (isTransitioningToFullShade) {
            viewModel.squishinessFraction = qsSquishinessFraction
        }
    }

    override fun setFancyClipping(
        leftInset: Int,
        top: Int,
        rightInset: Int,
        bottom: Int,
        cornerRadius: Int,
        visible: Boolean,
        fullWidth: Boolean,
    ) {
        val effectiveRadius =
            context?.resources?.let { (it.displayMetrics.density * CARD_REVEAL_CORNER_RADIUS_DP).toInt() } ?: 0
        containerView?.clipData =
            visible to
                NotificationScrimClipParams(
                    top,
                    bottom,
                    if (fullWidth) 0 else leftInset,
                    if (fullWidth) 0 else rightInset,
                    effectiveRadius,
                )
    }

    override fun isFullyCollapsed(): Boolean {
        return viewModel.isQsFullyCollapsed
    }

    override fun setCollapsedMediaVisibilityChangedListener(listener: Consumer<Boolean>?) {
        collapsedMediaVisibilityChangedListener.value = listener
    }

    override fun setScrollListener(scrollListener: QS.ScrollListener?) {
        this.scrollListener.value = scrollListener
    }

    override fun setOverScrollAmount(overScrollAmount: Int) {
        viewModel.overScrollAmount = overScrollAmount
    }

    override fun setIsNotificationPanelFullWidth(isFullWidth: Boolean) {
        viewModel.isSmallScreen = isFullWidth
    }

    override fun getHeaderTop(): Int {
        return qqsPositionOnRoot.top
    }

    override fun getHeaderBottom(): Int {
        return maxOf(qqsPositionOnRoot.bottom, viewModel.qqsHeight)
    }

    override fun getHeaderLeft(): Int {
        return qqsPositionOnRoot.left
    }

    override fun getHeaderHeight(): Int {
        val top = getHeaderTop()
        val bottom = getHeaderBottom()
        return (bottom - top).coerceAtLeast(viewModel.qqsHeight)
    }

    override fun getHeaderBoundsOnScreen(outBounds: Rect) {
        val bottom = maxOf(qqsPositionOnRoot.bottom, viewModel.qqsHeight)
        val right =
            if (qqsPositionOnRoot.right > qqsPositionOnRoot.left) {
                qqsPositionOnRoot.right
            } else {
                (view?.width ?: 0)
            }
        outBounds.set(
            qqsPositionOnRoot.left,
            qqsPositionOnRoot.top,
            right,
            bottom,
        )
        view?.getBoundsOnScreen(composeViewPositionOnScreen)
            ?: run { composeViewPositionOnScreen.setEmpty() }
        outBounds.offset(composeViewPositionOnScreen.left, composeViewPositionOnScreen.top)
    }

    override fun isHeaderShown(): Boolean {
        return qqsVisible.value
    }

    override fun setQSContentPaddingBottom(padding: Int) {
        bottomContentPadding = padding
    }

    private val configurationListener =
        object : ConfigurationListener {
            override fun onConfigChanged(newConfig: Configuration) {
                view?.dispatchConfigurationChanged(newConfig)
            }
        }

    private fun setListenerCollections() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastQqsHeight = -1
                var lastQqsMediaVisible: Boolean? = null
                this@AxQsFragmentCompose.view?.setSnapshotBinding {
                    scrollListener.value?.onQsPanelScrollChanged(scrollState.value)
                    val qqsMediaVisible =
                        when {
                            resources.configuration.orientation ==
                                Configuration.ORIENTATION_LANDSCAPE -> false
                            axQsViewModel.panelMode == AxQsPanelMode.SEPARATE ->
                                axMediaViewModel.hasVisibleSessions(AxMediaSurface.SEPARATE_QQS)
                            else -> axQsViewModel.isInGrid(AxQsControl.MEDIA.id, AxQsLayout.QQS)
                        }
                    if (ShadeWindowGoesAround.isEnabled) {
                        if (lastQqsMediaVisible != qqsMediaVisible) {
                            lastQqsMediaVisible = qqsMediaVisible
                            collapsedMediaVisibilityChangedListener.value?.accept(qqsMediaVisible)
                        }
                    } else {
                        collapsedMediaVisibilityChangedListener.value?.accept(qqsMediaVisible)
                    }
                    if (lastQqsHeight != viewModel.qqsHeight) {
                        lastQqsHeight = viewModel.qqsHeight
                        val currentRight =
                            if (qqsPositionOnRoot.right > qqsPositionOnRoot.left) {
                                qqsPositionOnRoot.right
                            } else {
                                view?.width ?: 0
                            }
                        qqsPositionOnRoot.set(
                            qqsPositionOnRoot.left,
                            0,
                            currentRight,
                            viewModel.qqsHeight,
                        )
                        qqsHeightListener.value?.onQqsHeightChanged()
                        heightListener.value?.onQsHeightChanged()
                    }
                }
                launch {
                    setListenerJob(
                        heightListener,
                        viewModel.containerViewModel.editModeViewModel.isEditing,
                    ) {
                        onQsHeightChanged()
                    }
                }
                launch {
                    setListenerJob(
                        qsContainerController,
                        viewModel.containerViewModel.editModeViewModel.isEditing,
                    ) {
                        setCustomizerShowing(it, EDIT_MODE_TIME_MILLIS.toLong())
                    }
                }
                launch {
                    try {
                        configurationController.addCallback(configurationListener)
                        awaitCancellation()
                    } finally {
                        configurationController.removeCallback(configurationListener)
                    }
                }
            }
        }
    }

    @Composable
    private fun ContentScope.QuickSettingsElement(
        onOpenPanelSettings: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val qsExtraPadding = dimensionResource(R.dimen.qs_panel_padding_top)
        val qsOffsetReduction = dimensionResource(R.dimen.ax_qs_offset_reduction)
        val overlayShadeHeader = useOverlayShadeHeader()
        val contentTopPadding =
            if (overlayShadeHeader) {
                quickSettingsContentTopPadding()
            } else {
                (qsExtraPadding - qsOffsetReduction).coerceAtLeast(0.dp)
            }
        val targetTopPadding =
            if (viewModel.isEditing && overlayShadeHeader) {
                statusBarTopPadding()
            } else {
                contentTopPadding
            }
        val animatedContentTopPadding by
            animateDpAsState(
                targetValue = targetTopPadding,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                label = "animatedContentTopPadding",
            )
        DisposableEffect(Unit) {
            qqsVisible.value = true
            onDispose { qqsVisible.value = false }
        }
        Column(
            modifier =
                modifier.collapseExpandSemanticAction(
                    stringResource(id = R.string.accessibility_quick_settings_collapse)
                )
        ) {
            if (viewModel.isQsEnabled) {
                Spacer(modifier = Modifier.requiredHeight(animatedContentTopPadding.coerceAtLeast(0.dp)))
                val contentGestureContext = rememberGestureContext()
                Element(
                    Elements.QuickSettingsContent,
                    modifier =
                        Modifier.weight(1f).thenIf(TileRevealFlag.isEnabled) {
                            Modifier.motionDriver(contentGestureContext, label = "QuickSettingsContent")
                        },
                ) {
                    // scrollState never changes
                    LaunchedEffect(Unit) {
                        snapshotFlow { viewModel.isQsFullyCollapsed }
                            .collect { collapsed ->
                                if (collapsed) {
                                    scrollState.scrollTo(0)
                                }
                            }
                    }

                    val isListening: () -> Boolean =
                        remember(viewModel) {
                                derivedStateOf {
                                    val hasOpeningThreshold =
                                        viewModel.isInSplitShade ||
                                            viewModel.isLargeScreenHeader ||
                                            viewModel.panelExpansionFraction >= 0.02f
                                    (hasOpeningThreshold &&
                                        viewModel.isQsVisibleAndAnyShadeExpanded) &&
                                        !viewModel.isEditing &&
                                        !viewModel.isStackScrollerOverscrolling
                                }
                            }
                            .let { state -> { state.value } }
                    val activeDetails = detailsViewModel.activeTileDetails
                    BackHandler(enabled = activeDetails != null) {
                        detailsViewModel.closeDetailedView()
                    }
                    AnimatedContent(
                        targetState = activeDetails != null,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                        label = "AxQsTileDetailsTransition",
                        modifier = Modifier.fillMaxSize(),
                    ) { showDetails ->
                        if (showDetails) {
                            BoxWithConstraints(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .padding(bottom = 8.dp)
                                        .sysuiResTag(AxResIdTags.qsScroll)
                            ) {
                                val sidePadding =
                                    if (viewModel.isInSplitShade) {
                                        AxQuickSettingsLayoutDefaults.LandscapeSidePadding
                                    } else {
                                        AxQuickSettingsLayoutDefaults.PortraitSidePadding
                                    }
                                TileDetails(
                                    modifier =
                                        Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                                    detailsViewModel = detailsViewModel,
                                )
                            }
                        } else {
                            Box(
                                modifier =
                                    Modifier.fillMaxSize()
                                        .onPlaced { coordinates ->
                                            val (leftFromRoot, _) =
                                                coordinates.positionInRoot().round()
                                            val newRight = leftFromRoot + coordinates.size.width
                                            val newBottom = viewModel.qqsHeight
                                            if (qqsPositionOnRoot.left != leftFromRoot ||
                                                qqsPositionOnRoot.right != newRight ||
                                                qqsPositionOnRoot.bottom != newBottom
                                            ) {
                                                qqsPositionOnRoot.set(
                                                    leftFromRoot,
                                                    0,
                                                    newRight,
                                                    newBottom,
                                                )
                                                qqsHeightListener.value?.onQqsHeightChanged()
                                                heightListener.value?.onQsHeightChanged()
                                            }
                                        }
                                        .offset {
                                            IntOffset(
                                                x = 0,
                                                y = viewModel.qsScrollTranslationY.fastRoundToInt(),
                                            )
                                        }
                                        .onSizeChanged { viewModel.qsScrollHeight = it.height }
                                        .padding(bottom = 8.dp)
                                        .sysuiResTag(AxResIdTags.qsScroll)
                            ) {
                                AxOneGrid(
                                    viewModel = viewModel,
                                    axQsViewModel = axQsViewModel,
                                    mediaViewModel = axMediaViewModel,
                                    detailsViewModel = detailsViewModel,
                                    listening = isListening,
                                    brightnessSliderViewModel =
                                        viewModel.containerViewModel.brightnessSliderViewModel,
                                    volumeSliderViewModel = viewModel.volumeSliderViewModel,
                                    scrollState = scrollState,
                                    headerTopPadding = animatedContentTopPadding,
                                    onOpenPanelSettings = onOpenPanelSettings,
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .sysuiResTag(AxResIdTags.quickSettingsPanel)
                                            .graphicsLayer {},
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height { bottomContentPadding }.fillMaxWidth())
        }
    }

    @Composable
    private fun statusBarTopPadding(): Dp {
        val shouldUseDisplayCutOutPadding =
            booleanResource(R.bool.config_shouldUseDisplayCutOutPadding)
        val displayCutoutTopPadding =
            WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        return when {
            isLandscape -> 4.dp
            shouldUseDisplayCutOutPadding -> displayCutoutTopPadding
            else -> dimensionResource(R.dimen.ax_qs_top_padding)
        }
    }

    @Composable
    private fun QuickSettingsStatusOverlayHeader(
        headerViewModel: ShadeHeaderViewModel,
        isTransitioning: Boolean,
        modifier: Modifier = Modifier,
    ) {
        AxQuickSettingsHeader(
            viewModel = headerViewModel,
            isTransitioning = isTransitioning,
            modifier = modifier.fillMaxWidth().padding(top = statusBarTopPadding().coerceAtLeast(0.dp)),
        )
    }

    @Composable
    private fun quickSettingsContentTopPadding(): Dp =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            0.dp
        } else {
            statusBarTopPadding() +
                ShadeHeader.Dimensions.StatusBarHeight +
                QuickSettingsShade.Dimensions.ShortPadding
        }

    @Composable
    private fun ContentScope.VolumeSliderRow(
        layoutState: SceneTransitionLayoutState,
        modifier: Modifier = Modifier,
    ) {
        val volumeSliderViewModel = viewModel.volumeSliderViewModel
        val volumeSliderState by volumeSliderViewModel.slider.collectAsStateWithLifecycle()
        Element(Elements.VolumeSlider, modifier = modifier.fillMaxWidth()) {
            Box(
                Modifier.systemGestureExclusionInShade(
                    enabled = {
                        layoutState.transitionState is TransitionState.Idle &&
                            viewModel.isNotTransitioning
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VolumeSlider(
                        modifier = Modifier.weight(1f),
                        showLabel = false,
                        state = volumeSliderState,
                        onValueChange = { newValue ->
                            volumeSliderViewModel.onValueChanged(volumeSliderState, newValue)
                        },
                        onValueChangeFinished = { volumeSliderViewModel.onValueChangeFinished() },
                        onIconTapped = { volumeSliderViewModel.toggleMuted(volumeSliderState) },
                        sliderColors = PlatformSliderDefaults.defaultPlatformSliderColors(),
                        hapticsViewModelFactory =
                            volumeSliderViewModel.getSliderHapticsViewModelFactory(),
                    )
                    SliderRowButton(
                        iconRes = R.drawable.horizontal_ellipsis,
                        contentDescription = stringResource(R.string.accessibility_volume_settings),
                        onClick = {
                            volumeNavigator.openVolumePanel(
                                volumePanelNavigationInteractor.getVolumePanelRoute()
                            )
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun SliderRowButton(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
        IconButton(
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            onClick = onClick,
        ) {
            Icon(painterResource(iconRes), contentDescription = contentDescription)
        }
    }

    @Composable
    private fun ContentScope.PanelSettingsElement(
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        AxQsPanelSettings(
            viewModel = axQsViewModel,
            onDismiss = onDismiss,
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(top = with(LocalDensity.current) { viewModel.qqsHeaderHeight.toDp().coerceAtLeast(0.dp) }),
        )
    }

    private fun Modifier.collapseExpandSemanticAction(label: String): Modifier {
        return viewModel.collapseExpandAccessibilityAction?.let { action ->
            semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(label) {
                            action.run()
                            true
                        }
                    )
            }
        } ?: this
    }

    private fun registerDumpable() {
        val instanceId = instanceProvider.getNextId()
        // Add an instanceId because the system may have more than 1 of these when re-inflating and
        // DumpManager doesn't like repeated identifiers. Also, put it first because DumpHandler
        // matches by end.
        val stringId = "$instanceId-AxQsFragmentCompose"
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                try {
                    dumpManager.registerNormalDumpable(stringId, this@AxQsFragmentCompose)
                    awaitCancellation()
                } finally {
                    dumpManager.unregisterDumpable(stringId)
                }
            }
        }
    }

    private val clipData
        get() = containerView?.clipData

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.asIndenting().run {
            printSection("NotificationScrimClippingParams") {
                println("isEnabled", clipData?.first)
                println("params", clipData?.second)
            }
            printSection("QQS positioning") {
                println("qqsHeight", "${headerHeight}px")
                println("qqsTop", "${headerTop}px")
                println("qqsBottom", "${headerBottom}px")
                println("qqsLeft", "${headerLeft}px")
                println("qqsPositionOnRoot", qqsPositionOnRoot)
                val rect = Rect()
                getHeaderBoundsOnScreen(rect)
                println("qqsPositionOnScreen", rect)
            }
            println("QQS visible", qqsVisible.value)
            println("bottom QS padding", bottomContentPadding)
            if (::viewModel.isInitialized) {
                printSection("View Model") { viewModel.dump(this@run, args) }
            }
        }
    }
}

private suspend inline fun <Listener : Any, Data> setListenerJob(
    listenerFlow: MutableStateFlow<Listener?>,
    dataFlow: Flow<Data>,
    crossinline onCollect: suspend Listener.(Data) -> Unit,
) {
    coroutineScope {
        try {
            listenerFlow.collectLatest { listenerOrNull ->
                listenerOrNull?.let { currentListener ->
                    launch {
                        // Called when editing mode changes
                        dataFlow.collect { currentListener.onCollect(it) }
                    }
                }
            }
            awaitCancellation()
        } finally {
            listenerFlow.value = null
        }
    }
}

private val instanceProvider =
    object {
        private var currentId = 0

        fun getNextId(): Int {
            return currentId++
        }
    }

private const val EDIT_MODE_TIME_MILLIS = 380L
private const val CARD_REVEAL_CORNER_RADIUS_DP = 28f

object AxSceneKeys {
    val QuickQuickSettings = SceneKey("QuickQuickSettingsScene")
    val QuickSettings = SceneKey("QuickSettingsScene")
    val PanelSettings = SceneKey("PanelSettingsScene")

    val TransitionState.Transition.debugName: String
        get() = "[from=${fromContent.debugName}, to=${toContent.debugName}]"

    fun AxQsFragmentComposeViewModel.QSExpansionState.toIdleSceneKey(): SceneKey {
        return QuickSettings
    }
}

private suspend fun synchronizeQsState(
    state: MutableSceneTransitionLayoutState,
    panelSettings: Flow<Boolean>,
) {
    coroutineScope {
        val animationScope = this
        panelSettings.collectLatest { settings ->
            if (settings && state.currentScene != AxSceneKeys.PanelSettings) {
                state.setTargetScene(AxSceneKeys.PanelSettings, animationScope)?.second?.join()
            } else if (!settings && state.currentScene == AxSceneKeys.PanelSettings) {
                state.setTargetScene(AxSceneKeys.QuickSettings, animationScope)?.second?.join()
            }
        }
    }
}

/**
 * Performs different touch handling based on the state of the ComposeView:
 * * Ignore touches below the value returned by [clipData.second.top], when clipping is enabled, as
 *   per [clipData.first].
 * * Intercept touches that would overscroll QS forward and instead allow them to be used to close
 *   the shade.
 * * Ignore touches in [isInBottomReservedArea] (bottom area when editing). This allows the shade to
 *   close on bottom swipes when editing when using gesture nav.
 */
private class AxFrameLayoutTouchPassthrough(
    context: Context,
    private val canScrollQs: AxCanScrollQs,
    private val emitMotionEventForFalsing: () -> Unit,
    private val logBuffer: TableLogBuffer,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val isInBottomReservedArea: (Float, Float) -> Boolean,
) : FrameLayout(context) {

    private val lastConfig = Configuration(context.resources.configuration)

    init {
        repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launchTraced("AxFrameLayoutTouchPassthrough.logs", backgroundDispatcher) {
                    _clipData
                        .pairwise(initialValue = false to NotificationScrimClipParams())
                        .collect { (prev, new) ->
                            logBuffer.logDiffs(
                                columnPrefix = PREFIX_PARAMS,
                                prevVal = prev.second,
                                newVal = new.second,
                            )
                            if (prev.first != new.first) {
                                logBuffer.logChange(
                                    columnName = COL_CLIP_ENABLED,
                                    value = new.first,
                                    isInitial = false,
                                )
                            }
                        }
                }
            }
        }
    }

    private val currentClippingPath = Path()

    private val _clipData = MutableStateFlow(false to NotificationScrimClipParams())

    // [first] is enabled and [second] is the clipping params
    var clipData
        get() = _clipData.value
        set(value) {
            if (_clipData.value != value) {
                _clipData.value = value
                dirtyClipData = true
                invalidate()
            }
        }

    var qsVisible: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                invalidate()
            }
        }

    private var dirtyClipData = false

    private val clipEnabled
        get() = clipData.first

    private val clipParams
        get() = clipData.second

    private val clipRadii = FloatArray(8)

    private fun updateClippingPath() {
        currentClippingPath.rewind()
        val (clipEnabled, clipParams) = clipData
        if (clipEnabled) {
            val right = width + clipParams.rightInset
            val left = -clipParams.leftInset
            val top = clipParams.top
            val bottom = clipParams.bottom

            val cardRadius = context.resources.displayMetrics.density * CARD_REVEAL_CORNER_RADIUS_DP

            clipRadii[0] = cardRadius
            clipRadii[1] = cardRadius
            clipRadii[2] = cardRadius
            clipRadii[3] = cardRadius
            clipRadii[4] = 0f
            clipRadii[5] = 0f
            clipRadii[6] = 0f
            clipRadii[7] = 0f

            currentClippingPath.addRoundRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                clipRadii,
                Path.Direction.CW,
            )
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (dirtyClipData) {
            dirtyClipData = false
            updateClippingPath()
        }
        if (!currentClippingPath.isEmpty) {
            canvas.translate(0f, -translationY)
            canvas.clipOutPath(currentClippingPath)
            canvas.translate(0f, translationY)
        }
        if (qsVisible) {
            // If QS should not be visible, there's no need to draw this tree at all. We do this
            // in the view (instead of in compose) so it's completely synchronized with the clip.
            // As this FrameLayout doesn't have any content, and the ComposeView is the only child,
            // this is equivalent to blocking the draw in `drawChild`.
            super.dispatchDraw(canvas)
        }
    }

    override fun isTransformedTouchPointInView(
        x: Float,
        y: Float,
        child: View?,
        outLocalPoint: PointF?,
    ): Boolean {
        return if (clipEnabled && y + translationY > clipParams.top) {
            false
        } else if (isInBottomReservedArea(x, y)) { // no translation as it's relative to root
            false
        } else {
            super.isTransformedTouchPointInView(x, y, child, outLocalPoint)
        }
    }

    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downY = 0f
    var downX = 0f
    var preventingIntercept = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                preventingIntercept = false
                if (canScrollQs.forward() || canScrollQs.backward()) {
                    // If we can scroll down, make sure we're not intercepted by the parent
                    preventingIntercept = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (!canScrollQs.backward()) {
                    // Don't pass on the touch to the view, because scrolling will unconditionally
                    // disallow interception even if we can't scroll.
                    // if a user can't scroll at all, we should never listen to the touch.
                    return false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (preventingIntercept) {
                    emitMotionEventForFalsing()
                }
                allowParentIntercept()
            }
            MotionEvent.ACTION_CANCEL -> allowParentIntercept()
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchConfigurationChanged(newConfig: Configuration) {
        if (lastConfig.updateFrom(newConfig) != 0) {
            super.dispatchConfigurationChanged(newConfig)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // If there's a touch on this view and we can scroll down, we don't want to be intercepted
        val action = ev.actionMasked

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                preventingIntercept = false
                // If we can scroll down, make sure none of our parents intercepts us.
                if (canScrollQs.forward()) {
                    preventingIntercept = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                downY = ev.y
                downX = ev.x
            }

            MotionEvent.ACTION_MOVE -> {
                val y = ev.y
                val x = ev.x
                val yDiff = y - downY
                val xDiff = x - downX
                val collapsing = yDiff < -touchSlop && !canScrollQs.forward()
                val vertical = Math.abs(xDiff) < Math.abs(yDiff)
                if (collapsing && vertical) {
                    allowParentIntercept()
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> allowParentIntercept()
        }
        return super.onInterceptTouchEvent(ev)
    }

    private fun allowParentIntercept() {
        if (!preventingIntercept) return
        preventingIntercept = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private companion object {
        const val COL_CLIP_ENABLED = "enabled"
        const val PREFIX_PARAMS = "params"
    }
}

private interface AxCanScrollQs {
    fun forward(): Boolean

    fun backward(): Boolean
}

private object AxResIdTags {
    const val quickSettingsPanel = "quick_settings_panel"
    const val quickQsPanel = "quick_qs_panel"
    const val qsScroll = "expanded_qs_scroll_view"
}

@Composable private fun qsHorizontalMargin() = dimensionResource(id = R.dimen.qs_horizontal_margin)

private fun useOverlayShadeHeader() = true

@Composable
private fun interactionsConfig() =
    InteractionsConfig(
        hoverOverlayColor = MaterialTheme.colorScheme.onSurface,
        hoverOverlayAlpha = 0.11f,
        pressedOverlayColor = MaterialTheme.colorScheme.onSurface,
        pressedOverlayAlpha = 0.15f,
        // we are OK using this as our content is clipped and all corner radius are larger than this
        surfaceCornerRadius = 16.dp,
    )

/**
 * Forces the configuration and themes to be dark theme. This is needed in order to have
 * [colorResource] retrieve the dark mode colors.
 *
 * This should be removed when [notificationShadeBlur] is removed
 */
@Composable
private fun AlwaysDarkMode(content: @Composable () -> Unit) {
    if (notificationShadeBlur()) {
        content()
    } else {
        val currentConfig = LocalConfiguration.current
        val darkConfig =
            Configuration(currentConfig).apply {
                uiMode =
                    (uiMode and (Configuration.UI_MODE_NIGHT_MASK.inv())) or
                        Configuration.UI_MODE_NIGHT_YES
            }
        val newContext = LocalContext.current.createConfigurationContext(darkConfig)
        CompositionLocalProvider(
            LocalConfiguration provides darkConfig,
            LocalContext provides newContext,
        ) {
            content()
        }
    }
}
