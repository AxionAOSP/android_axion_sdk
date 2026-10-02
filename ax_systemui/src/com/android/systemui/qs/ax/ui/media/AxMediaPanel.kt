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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.ui.compose.PagerDots
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaCarouselVisibility
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import com.android.systemui.qs.ax.shared.model.AxMediaSurface
import com.android.systemui.qs.ax.shared.model.AxQsSpan
import com.android.systemui.qs.ax.ui.viewmodel.AxMediaViewModel

@Composable
fun AxMediaPanel(
    viewModel: AxMediaViewModel,
    span: AxQsSpan,
    modifier: Modifier = Modifier,
    mediaViewModelFactory: MediaViewModel.Factory? = null,
    showPlaceholder: Boolean = false,
    interactive: Boolean = true,
    allowGuts: Boolean = true,
    surface: AxMediaSurface = AxMediaSurface.CONTROL
) {
    val showOnLockscreen by viewModel.showOnLockscreen.collectAsStateWithLifecycle()
    if (surface == AxMediaSurface.LOCKSCREEN && !showOnLockscreen) return

    val sessions = viewModel.visibleSessions(surface)
    val currentSession = viewModel.currentSession(surface)
    val lastMediaPackage by viewModel.lastMediaPackage.collectAsStateWithLifecycle()
    val hasVisibleMedia = sessions.isNotEmpty() || currentSession != null || showPlaceholder

        LaunchedEffect(viewModel, currentSession?.key) {
            viewModel.synchronizeSession(currentSession?.key)
        }

        if (hasVisibleMedia) {
            val factory = mediaViewModelFactory
            if (factory == null || (sessions.isEmpty() && showPlaceholder)) {
                val fallbackModifier =
                    if (surface == AxMediaSurface.CONTROL) {
                        modifier
                    } else {
                        modifier.fillMaxWidth().height(nonQsGridMediaHeight)
                    }
                key(currentSession?.key ?: "placeholder") {
                    if (surface == AxMediaSurface.CONTROL) {
                        AxQsGridMediaCard(
                            viewModel = viewModel,
                            session = currentSession,
                            lastMediaPackage = lastMediaPackage,
                            span = span,
                            interactive = interactive,
                            hasMultipleSessions = sessions.size > 1,
                            modifier = fallbackModifier
                        )
                    } else {
                        AxNonQsMediaCard(
                            viewModel = viewModel,
                            session = currentSession,
                            lastMediaPackage = lastMediaPackage,
                            span = span,
                            interactive = interactive,
                            allowGuts = false,
                            surface = surface,
                            hasMultipleSessions = sessions.size > 1,
                            modifier = fallbackModifier
                        )
                    }
                }
            } else {
                val shape = AxMediaTokens.MediaCardShape
                val gesturesEnabled = !viewModel.hasVisibleGuts()
                val isQsGrid = surface == AxMediaSurface.CONTROL
                val carouselScrollingEnabled =
                    gesturesEnabled && (isQsGrid || sessions.size > 1)
                val isFalseTouchDetected = remember(surface) { mutableStateOf(false) }
                val behavior =
                    remember(viewModel, surface, carouselScrollingEnabled) {
                        MediaUiBehavior(
                            isCarouselDismissible = false,
                            isCarouselScrollingEnabled = carouselScrollingEnabled,
                            carouselVisibility = MediaCarouselVisibility.WhenNotEmpty,
                            isCarouselScrollFalseTouch =
                                if (!isQsGrid) {
                                    null
                                } else {
                                    {
                                        viewModel.isSwipeFalseTouch().also {
                                            isFalseTouchDetected.value = it
                                        }
                                    }
                                }
                        )
                    }
                val onDismissed =
                    remember(viewModel, surface) { { viewModel.dismissBySwipe(surface) } }
                val mediaModifier =
                    when (surface) {
                        AxMediaSurface.CONTROL -> modifier.fillMaxSize()
                        AxMediaSurface.SEPARATE_QQS -> {
                            modifier
                                .fillMaxSize()
                                .clip(shape)
                                .mediaOverscrollToDismiss(
                                    enabled = gesturesEnabled,
                                    dismissAllowed = !isFalseTouchDetected.value,
                                    onDismissed = onDismissed
                                )
                        }
                        else -> {
                            modifier
                                .fillMaxWidth()
                                .height(nonQsGridMediaHeight)
                        }
                    }
                val context = LocalContext.current
                val mediaViewModel: MediaViewModel =
                    rememberViewModel(traceName = "AxMediaViewModel") {
                        factory.create(context, behavior.carouselVisibility)
                    }
                AxMediaCarousel(
                    viewModel = mediaViewModel,
                    behavior = behavior,
                    onDismissed = onDismissed,
                    modifier = mediaModifier,
                    cardFilter = { viewModel.isSessionVisible(it.key, surface) },
                    carouselShape = shape,
                    cardContent = { card, cardModifier ->
                        val session = viewModel.sessionForKey(card.key)
                        key(card.key) {
                            if (surface == AxMediaSurface.CONTROL) {
                                AxQsGridMediaCard(
                                    viewModel = viewModel,
                                    session = session,
                                    lastMediaPackage = lastMediaPackage,
                                    span = span,
                                    interactive = interactive,
                                    hasMultipleSessions = sessions.size > 1,
                                    modifier = cardModifier.fillMaxSize()
                                )
                            } else {
                                AxNonQsMediaCard(
                                    viewModel = viewModel,
                                    session = session,
                                    lastMediaPackage = lastMediaPackage,
                                    span = span,
                                    interactive = interactive,
                                    allowGuts = allowGuts,
                                    surface = surface,
                                    hasMultipleSessions = sessions.size > 1,
                                    modifier = cardModifier.fillMaxSize()
                                )
                            }
                        }
                    },
                    pagerIndicator = { pagerState ->
                        val isQsGrid = surface == AxMediaSurface.CONTROL
                        val dotActiveColor =
                            if (isQsGrid) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.White
                            }
                        val dotInactiveColor =
                            if (isQsGrid) {
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = AxMediaTokens.InactiveDotAlpha
                                )
                            } else {
                                Color.White.copy(alpha = AxMediaTokens.InactiveDotLockscreenAlpha)
                            }
                        PagerDots(
                            pagerState = pagerState,
                            activeColor = dotActiveColor,
                            nonActiveColor = dotInactiveColor,
                            dotSize = AxMediaTokens.DotSize,
                            spaceSize = AxMediaTokens.DotSpacing,
                            modifier =
                                Modifier.align(Alignment.BottomCenter)
                                    .padding(bottom = AxMediaTokens.DotBottomPadding)
                        )
                    }
                )
            }
        }
    }
