package com.android.systemui.media

import com.android.systemui.statusbar.StatusBarState

object MediaArtVisibilityPolicy {

    fun shouldShow(
        isFeatureEnabled: Boolean,
        hasArtwork: Boolean,
        isMediaPlaying: Boolean,
        isKeyguardShowing: Boolean,
        isDozing: Boolean,
        statusBarState: Int,
        isOccluded: Boolean,
        isGoingAway: Boolean,
        isFadingAway: Boolean,
        isBouncerShowing: Boolean,
        isQsExpanded: Boolean,
        anyExpansion: Float,
    ): Boolean {
        val conditions = booleanArrayOf(
            isFeatureEnabled,
            hasArtwork,
            isMediaPlaying,
            isKeyguardShowing || isDozing,
            statusBarState == StatusBarState.KEYGUARD,
            !isOccluded,
            !isGoingAway,
            !isFadingAway,
            !isBouncerShowing,
            !isQsExpanded && anyExpansion <= 0.1f,
        )
        return conditions.all { it }
    }
}
