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

/**
 * @hide
 */
public final class AxPerfTraceManager {
    public static final String TRACE_ACQUIRE_PREFIX = "AcquireScene_";
    public static final String TRACE_HANDLE_PREFIX = "_H";
    public static final String COUNTER_ACTIVE_SCENE = "ActiveScene";
    public static final int SCENE_INACTIVE = 0;

    public void reportSceneAcquire(int sceneId, int handle) {
        AxSmartTraceUtils.traceBegin(TRACE_ACQUIRE_PREFIX + sceneId + TRACE_HANDLE_PREFIX + handle);
        AxSmartTraceUtils.traceCounter(COUNTER_ACTIVE_SCENE, sceneId);
    }

    public void reportSceneRelease(int sceneId, int handle) {
        AxSmartTraceUtils.traceEnd();
        AxSmartTraceUtils.traceCounter(COUNTER_ACTIVE_SCENE, SCENE_INACTIVE);
    }
}
