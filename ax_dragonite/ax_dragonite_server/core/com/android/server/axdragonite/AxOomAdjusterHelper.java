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

import static android.os.Process.THREAD_GROUP_AX_FOREGROUND;
import static android.os.Process.THREAD_GROUP_DEFAULT;
import static android.os.Process.THREAD_GROUP_RESTRICTED;

import android.os.Process;
import android.os.UserHandle;
import com.android.server.am.psc.ProcessRecordInternal;
import java.util.Set;

public final class AxOomAdjusterHelper {
    private static final String AXION_PREFIX = "com.axion.";
    private static final String SYSTEMUI_PROCESS_NAME = "com.android.systemui";
    private static final int SCHED_GROUP_TOP_APP = 3;

    private static final Set<String> APP_WHITELIST = Set.of(
            "com.google.android.providers.media.module",
            "android.process.media",
            "android.os.cts"
    );

    private AxOomAdjusterHelper() {}

    public static void onProcessForked(int pid, boolean isTopApp, String processName, int uid) {
        if (pid <= 0 || isTopApp || processName == null) {
            return;
        }
        if (UserHandle.getAppId(uid) >= Process.FIRST_APPLICATION_UID
                && !isAxionApp(processName)
                && !isInWhitelist(processName)) {
            try {
                Process.setProcessGroup(pid, THREAD_GROUP_AX_FOREGROUND);
            } catch (Exception unused) {
            }
        }
    }

    public static int getRestrictedProcessGroup(ProcessRecordInternal app) {
        if (isRestrictedNeedSelfControll(app)) {
            return THREAD_GROUP_AX_FOREGROUND;
        }
        return THREAD_GROUP_RESTRICTED;
    }

    public static int getDefaultProcessGroup(int oldSchedGroup, ProcessRecordInternal app) {
        if (isForegroundNeedSelfControll(oldSchedGroup, app)) {
            return THREAD_GROUP_AX_FOREGROUND;
        }
        return THREAD_GROUP_DEFAULT;
    }

    public static boolean isRestrictedNeedSelfControll(ProcessRecordInternal app) {
        if (app == null) {
            return false;
        }
        if (SYSTEMUI_PROCESS_NAME.equals(app.processName) || isAxionApp(app.processName) || isInWhitelist(app.processName)) {
            return false;
        }
        return !isHostingTopApp(app);
    }

    public static boolean isForegroundNeedSelfControll(int oldScheduleGroup, ProcessRecordInternal app) {
        if (app == null) {
            return false;
        }
        if (oldScheduleGroup == SCHED_GROUP_TOP_APP && app.getHasActivities()) {
            return false;
        }
        if (UserHandle.getAppId(app.uid) < Process.FIRST_APPLICATION_UID
                || SYSTEMUI_PROCESS_NAME.equals(app.processName)
                || isAxionApp(app.processName)
                || isInWhitelist(app.processName)) {
            return false;
        }
        return !isHostingTopApp(app);
    }

    private static boolean isHostingTopApp(ProcessRecordInternal app) {
        return app.getHasTopUi() || app.getScheduleLikeTopApp();
    }

    private static boolean isAxionApp(String processName) {
        return processName != null && processName.startsWith(AXION_PREFIX);
    }

    private static boolean isInWhitelist(String processName) {
        return processName != null && APP_WHITELIST.contains(processName);
    }
}
