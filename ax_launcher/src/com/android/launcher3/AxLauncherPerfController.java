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

package com.android.launcher3;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import com.android.launcher3.uioverrides.QuickstepLauncher;

public final class AxLauncherPerfController {

    private static final float PROGRESS_EPSILON = 0.005f;

    private final Launcher mLauncher;

    private boolean mIsAllAppsExpanded;
    private boolean mIsNotificationShadeExpanded;
    private boolean mAreExpensiveViewUpdatesPaused;
    private float mLastAllAppsProgress;

    public AxLauncherPerfController(Launcher launcher) {
        mLauncher = launcher;
    }

    public boolean isAllAppsExpanded() {
        return mIsAllAppsExpanded;
    }

    public boolean isNotificationShadeExpanded() {
        return mIsNotificationShadeExpanded;
    }

    public void onResume() {
        if (mLauncher.getStateManager().getState() == NORMAL) {
            mLastAllAppsProgress = 0f;
            onAllAppsExpansionChanged(false);
        }
    }

    public void onStateSetEnd(LauncherState state) {
        if (state == NORMAL) {
            mLastAllAppsProgress = 0f;
            onAllAppsExpansionChanged(false);
        } else if (ALL_APPS.equals(state)) {
            mLastAllAppsProgress = 1f;
            onAllAppsExpansionChanged(true);
        }
    }

    public void onAllAppsTransition(float progress) {
        float lastExpansion = mLastAllAppsProgress;
        mLastAllAppsProgress = progress;

        if (progress <= PROGRESS_EPSILON) {
            onAllAppsExpansionChanged(false);
            return;
        }

        if (progress >= 1.0f - PROGRESS_EPSILON) {
            onAllAppsExpansionChanged(true);
            return;
        }

        boolean isCollapsing = progress < (lastExpansion - PROGRESS_EPSILON);

        onAllAppsExpansionChanged(!isCollapsing);
    }

    public void onAllAppsExpansionChanged(boolean isExpanded) {
        if (mIsAllAppsExpanded == isExpanded) {
            return;
        }
        mIsAllAppsExpanded = isExpanded;
        updateExpensiveViewUpdatesState();
    }

    public void onNotificationShadeExpandChanged(boolean isExpanded) {
        if (mIsNotificationShadeExpanded == isExpanded) {
            return;
        }
        mIsNotificationShadeExpanded = isExpanded;
        updateExpensiveViewUpdatesState();
    }

    private void updateExpensiveViewUpdatesState() {
        boolean shouldPause = mIsAllAppsExpanded || mIsNotificationShadeExpanded;
        if (mAreExpensiveViewUpdatesPaused == shouldPause) {
            return;
        }
        mAreExpensiveViewUpdatesPaused = shouldPause;
        if (shouldPause) {
            mLauncher.pauseExpensiveViewUpdates();
        } else {
            mLauncher.resumeExpensiveViewUpdates();
        }
    }
}
