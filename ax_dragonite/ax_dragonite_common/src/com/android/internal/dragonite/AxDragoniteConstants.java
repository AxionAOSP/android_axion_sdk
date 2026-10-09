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

package com.android.internal.dragonite;

import android.os.Process;
import java.util.Set;
import java.util.regex.Pattern;

public final class AxDragoniteConstants {
    private AxDragoniteConstants() {
    }

    public static final String TAG = "AxDragonite";

    public static final String KEY_PID = "pid";
    public static final String KEY_TARGET_PID = "target_pid";
    public static final String KEY_CALLING_PID = "calling_pid";
    public static final String KEY_PKG = "pkg";
    public static final String KEY_PACKAGE_NAME = "package_name";
    public static final String KEY_PACKAGE = "package";
    public static final String KEY_HANDLE = "handle";
    public static final String KEY_COMPONENT_NAME = "componentName";
    public static final String KEY_DURATION = "duration";
    public static final String KEY_PARAMS = "params";
    public static final String KEY_IS_COLD = "isCold";
    public static final String KEY_UID = "uid";

    public static final String PKG_SYSTEMUI = "com.android.systemui";
    public static final String KEYWORD_LAUNCHER = "launcher";

    public static final String WORKER_THREAD_NAME = "AxDragoniteWorker";
    public static final String TIMER_THREAD_NAME = "AxDragoniteTimer";

    public static final int SCENE_APP_START = 1;
    public static final int SCENE_APP_START_EXIT_ANIMATION = 1;
    public static final int SCENE_LAUNCHER_ANIMATION = 2;
    public static final int SCENE_LAUNCHER_ITEM_LOADING = 3;
    public static final int SCENE_LAUNCHER_OVERLOAD = 4;
    public static final int SCENE_LAUNCHER_SCREEN_ON = 5;
    public static final int SCENE_QS_PULL_DOWN = 100;
    public static final int SCENE_NOTIFICATION_EXPAND = 100;
    public static final int SCENE_UNLOCK = 101;
    public static final int SCENE_UNLOCK_ANIMATION = 101;
    public static final int SCENE_SYSTEMUI_ANIMATION = 102;
    public static final int SCENE_ANIMATION = 102;
    public static final int SCENE_GAME_1 = 300;
    public static final int SCENE_GAME_MODE = 300;
    public static final int SCENE_GAME_2 = 301;
    public static final int SCENE_BENCH_GPU = 400;
    public static final int SCENE_BENCH_CPU = 401;
    public static final int SCENE_APP_LAUNCH = 1;
    public static final int SCENE_APP_LAUNCH_COLD = 1;
    public static final int SCENE_APP_LAUNCH_WARM = 1;
    public static final int SCENE_APP_EXIT_ANIM = 1;
    public static final int SCENE_FLING = 500;
    public static final int SCENE_FLING_LEVEL_1 = 500;
    public static final int SCENE_SCROLL = 500;
    public static final int SCENE_RECENT_TASK_SLIDE = 500;
    public static final int SCENE_QUICK_SWITCH_APP = 500;
    public static final int SCENE_ROTATION = 1;
    public static final int SCENE_DATA_LOADING = 3;
    public static final int SCENE_FOLDER_ANIMATION = 4;
    public static final int SCENE_DRAG_AND_DROP = 5;
    public static final int SCENE_BIOMETRIC_UNLOCK = 101;
    public static final int SCENE_DISABLE_INPUT_BOOST = 600;
    public static final int SCENE_TEST = 10000;

    public static final int DEFAULT_TIMEOUT_MS = 500;
    public static final int DURATION_APP_LAUNCH_MS = 1200;
    public static final int DURATION_GESTURE_MS = 400;
    public static final int DURATION_BACK_HOME_MS = 500;
    public static final int DURATION_SCREEN_ON_MS = 600;
    public static final int DURATION_LIGHT_REVEAL_MS = 500;
    public static final int DURATION_DOZE_MS = 500;
    public static final int DURATION_SHADE_EXPAND_MS = 500;
    public static final int DURATION_UNLOCK_MS = 800;
    public static final int DURATION_FLING_MS = 600;
    public static final int DURATION_SCROLL_MS = 400;
    public static final int DURATION_QUICK_SWITCH_MS = 600;
    public static final int DURATION_APP_LAUNCH_COLD_MS = 1200;
    public static final int DURATION_APP_LAUNCH_WARM_MS = 800;
    public static final int DURATION_BIOMETRIC_AUTH_MS = 800;
    public static final int DURATION_ANIMATION_MS = 500;
    public static final int DURATION_VOLUME_DIALOG_MS = 500;
    public static final int DURATION_APP_LAUNCH = 1200;
    public static final int DURATION_GESTURE_START_MS = 400;
    public static final int DURATION_QUICK_SWITCH_APP_MS = 600;
    public static final int DURATION_RECENT_TASK_SLIDE_MS = 400;
    public static final int DURATION_BIOMETRIC_UNLOCK_MS = 800;
    public static final int DURATION_FACE_UNLOCK_MS = 600;
    public static final int DURATION_FLING = 600;
    public static final int DURATION_SCROLL = 400;
    public static final int DURATION_BACK_HOME = 500;
    public static final int DURATION_GESTURE = 400;
    public static final int DURATION_QUICK_SWITCH = 600;
    public static final int DURATION_UNLOCK = 800;
    public static final int DURATION_SHADE_EXPAND = 500;
    public static final int DURATION_LIGHT_REVEAL = 500;
    public static final int DURATION_SCREEN_ON = 600;
    public static final int DURATION_DOZE = 500;
    public static final int DURATION_VOLUME_DIALOG = 500;
    public static final int DURATION_DATA_LOADING_MS = 1000;
    public static final int DURATION_FOLDER_ANIMATION_MS = 400;
    public static final int DURATION_DRAG_AND_DROP_MS = 600;
    public static final int DURATION_APP_EXIT_ANIM_MS = 400;
    public static final int DURATION_ROTATION_MS = 600;

    public static final int OPCODE_THREAD_BOOST = 501;
    public static final int OPCODE_BOOST_SCHED = 501;
    public static final int OPCODE_THREAD_AFFINITY = 600;
    public static final int OPCODE_CPU_AFFINITY = 600;
    public static final int OPCODE_PROCESS_AFFINITY = 601;
    public static final int OPCODE_SCHED_PRIORITY = 601;
    public static final int OPCODE_CPUCTL_TOP_APP = 302;
    public static final int OPCODE_CPUSET_TOP_APP = 100;
    public static final int OPCODE_BACKGROUND_FREEZE = 700;
    public static final int OPCODE_FREEZE_PROCESS = 700;

    public static final int BOOST_SCHED_POLICY = Process.SCHED_RESET_ON_FORK | Process.SCHED_RR;
    public static final int BOOST_SCHED_PRIORITY = 1;

    public static final String PARAM_DELIMITER = ";";
    public static final String OPCODE_DELIMITER = ":";
    public static final String TID_DELIMITER = ",";

    public static final Set<String> ALLOWED_CPUSET_GROUPS = Set.of(
            "top-app", "foreground", "background", "system-background",
            "restricted", "nnapi-hal"
    );
    public static final Pattern CPUSET_CPUS_PATTERN = Pattern.compile("^[0-9,-]+$");
    public static final long MAX_BOOST_DURATION_MS = 30000L;
    public static final long MIN_BOOST_DURATION_MS = 0L;

    public static final String KEY_BINDER_TOKEN = "binder_token";
    public static final int INVALID_PID = -1;
    public static final int INVALID_HANDLE = -1;
    public static final int MIN_VALID_HANDLE = 0;
    public static final int INITIAL_HANDLE_VALUE = 1;
    public static final int EMPTY_COUNT = 0;
}
