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

package com.android.server.axdragonite;

import android.os.SystemProperties;

/**
 * @hide
 */
public final class AxFrameInsertManager {
    public static final String PROP_FRAME_INSERT = "persist.axd.frame_insert";
    public static final String PROP_CHOREOGRAPHER_SKIP_WARNING = "debug.choreographer.skipwarning";
    public static final String VALUE_ENABLED = "1";
    public static final String VALUE_DISABLED = "0";
    public static final boolean DEFAULT_FRAME_INSERT_ENABLED = true;

    private boolean mEnabled = true;

    public AxFrameInsertManager() {
        mEnabled = SystemProperties.getBoolean(PROP_FRAME_INSERT, DEFAULT_FRAME_INSERT_ENABLED);
    }

    public void onSceneStart(int sceneId) {
        if (!mEnabled) {
            return;
        }
        if (sceneId == AxDragonite.SCENE_FLING || sceneId == AxDragonite.SCENE_RECENT_TASK_SLIDE) {
            SystemProperties.set(PROP_CHOREOGRAPHER_SKIP_WARNING, VALUE_ENABLED);
        }
    }

    public void onSceneEnd(int sceneId) {
        if (!mEnabled) {
            return;
        }
        if (sceneId == AxDragonite.SCENE_FLING || sceneId == AxDragonite.SCENE_RECENT_TASK_SLIDE) {
            SystemProperties.set(PROP_CHOREOGRAPHER_SKIP_WARNING, VALUE_DISABLED);
        }
    }
}
