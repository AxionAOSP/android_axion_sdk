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

package com.android.systemui.qs.ax.shared.model

import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.remedia.domain.model.MediaActionModel
import com.android.systemui.media.remedia.domain.model.MediaOutputDeviceModel
import com.android.systemui.media.remedia.domain.model.MediaSessionModel
import com.android.systemui.media.remedia.shared.model.MediaCardActionButtonLayout
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState

data class AxMediaDismissToken(
    val sessionKey: Any,
    val title: String,
    val subtitle: String
)

data class AxMediaMetadata(
    val key: Any,
    val title: String,
    val subtitle: String,
    val appName: String,
    val durationMs: Long,
    val state: MediaSessionState,
    val canBeScrubbed: Boolean,
    val colorScheme: MediaColorScheme?,
    val outputDevice: MediaOutputDeviceModel,
    val suggestedOutputDevice: MediaOutputDeviceModel?,
    val actionButtonLayout: MediaCardActionButtonLayout,
    val playPauseAction: MediaActionModel,
    val leftAction: MediaActionModel,
    val rightAction: MediaActionModel,
    val additionalActions: List<MediaActionModel.Action>,
    val isActive: Boolean,
    val canBeHidden: Boolean,
    val background: Icon?,
    val appIcon: Icon
)

fun MediaSessionModel.toMetadata(): AxMediaMetadata =
    AxMediaMetadata(
        key = key,
        title = title,
        subtitle = subtitle,
        appName = appName,
        durationMs = durationMs,
        state = state,
        canBeScrubbed = canBeScrubbed,
        colorScheme = colorScheme,
        outputDevice = outputDevice,
        suggestedOutputDevice = suggestedOutputDevice,
        actionButtonLayout = actionButtonLayout,
        playPauseAction = playPauseAction,
        leftAction = leftAction,
        rightAction = rightAction,
        additionalActions = additionalActions,
        isActive = isActive,
        canBeHidden = canBeHidden,
        background = background,
        appIcon = appIcon
    )

fun MediaSessionModel.isDisplayable(): Boolean = title.isNotBlank()

fun MediaSessionModel.isVisibleOn(surface: AxMediaSurface): Boolean {
    if (!isDisplayable()) return false
    return when (surface) {
        AxMediaSurface.CONTROL -> true
        AxMediaSurface.LOCKSCREEN,
        AxMediaSurface.SEPARATE_QQS,
        AxMediaSurface.DYNAMIC_BAR -> isActive
    }
}

class AxMediaSessionModel(
    private val delegate: MediaSessionModel,
    private val positionProvider: () -> Long
) : MediaSessionModel by delegate {
    override val positionMs: Long
        get() = positionProvider()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaSessionModel) return false
        return key == other.key &&
            title == other.title &&
            subtitle == other.subtitle &&
            appName == other.appName &&
            durationMs == other.durationMs &&
            state == other.state &&
            canBeScrubbed == other.canBeScrubbed &&
            colorScheme == other.colorScheme &&
            outputDevice == other.outputDevice &&
            suggestedOutputDevice == other.suggestedOutputDevice &&
            actionButtonLayout == other.actionButtonLayout &&
            playPauseAction == other.playPauseAction &&
            leftAction == other.leftAction &&
            rightAction == other.rightAction &&
            additionalActions == other.additionalActions &&
            isActive == other.isActive &&
            canBeHidden == other.canBeHidden &&
            background == other.background &&
            appIcon == other.appIcon
    }

    override fun hashCode(): Int = key.hashCode()
}
