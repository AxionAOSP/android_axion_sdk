/*
 * Copyright (C) 2024 The Android Open Source Project
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.media.remedia.ui.compose.MediaUiBehavior
import com.android.systemui.media.remedia.ui.viewmodel.MediaCardViewModel
import com.android.systemui.media.remedia.ui.viewmodel.MediaViewModel
import java.util.Comparator

@Composable
fun AxMediaCarousel(
    viewModel: MediaViewModel,
    behavior: MediaUiBehavior,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    cardFilter: ((MediaCardViewModel) -> Boolean)? = null,
    cardComparator: Comparator<MediaCardViewModel>? = null,
    carouselShape: Shape = RoundedCornerShape(32.dp),
    cardContent: @Composable BoxScope.(MediaCardViewModel, Modifier) -> Unit,
    pagerIndicator: @Composable BoxScope.(PagerState) -> Unit = {}
) {
    val rawCards = cardFilter?.let { viewModel.cards.filter(it) } ?: viewModel.cards
    val cards = if (cardComparator != null) rawCards.sortedWith(cardComparator) else rawCards
    val hasCards = cards.isNotEmpty()
    AnimatedVisibility(
        visible = viewModel.isCarouselVisible && hasCards,
        modifier = modifier
    ) {
        if (cards.isEmpty()) return@AnimatedVisibility
        val cardKeys = cards.map { it.key }
        val initialCardKey = viewModel.cards.getOrNull(viewModel.currentIndex)?.key
        val initialIndex = cardKeys.indexOf(initialCardKey).coerceIn(0, (cards.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(initialPage = initialIndex) { cards.size }
        LaunchedEffect(cards.size) {
            if (pagerState.currentPage >= cards.size && cards.isNotEmpty()) {
                pagerState.scrollToPage(cards.size - 1)
            }
        }
        LaunchedEffect(pagerState.currentPage, cardKeys) {
            val selectedKey = cardKeys.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
            val selectedIndex = viewModel.cards.indexOfFirst { it.key == selectedKey }
            if (selectedIndex >= 0) viewModel.onCardSelected(selectedIndex)
        }
        var isFalseTouchDetected: Boolean by
            remember(behavior.isCarouselScrollFalseTouch) { mutableStateOf(false) }
        val isSwipingEnabled = behavior.isCarouselScrollingEnabled && !isFalseTouchDetected

        Box(
            modifier =
                modifier.clip(carouselShape).pointerInput(behavior) {
                    val isCarouselScrollFalseTouch = behavior.isCarouselScrollFalseTouch
                    if (isCarouselScrollFalseTouch != null) {
                        awaitEachGesture {
                            awaitFirstDown(false, PointerEventPass.Initial)
                            val pointer = currentEvent.changes.first()
                            isFalseTouchDetected = isCarouselScrollFalseTouch.invoke()
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().clip(carouselShape),
                userScrollEnabled = isSwipingEnabled,
                pageSpacing = 8.dp,
                beyondViewportPageCount = 1,
                key = { index: Int -> cards[index].key }
            ) { pageIndex: Int ->
                val cardModifier =
                    Modifier.clip(carouselShape).sysuiResTag("media_control")
                cardContent(cards[pageIndex], cardModifier)
            }

            if (pagerState.pageCount > 1) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    pagerIndicator(pagerState)
                }
            }
        }
    }
}
