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

    private static final int ADJ_FOREGROUND_MIN = 200;

    private static final int ADJ_FOREGROUND_MAX = 800;

    private static final Set<String> APP_SELF_CONTROL_WHITELIST = Set.of(
            "com.google.android.providers.media.module",
            "android.process.media",
            "android.os.cts"
    );

    private AxOomAdjusterHelper() {}

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

    public static boolean isForegroundNeedSelfControll(int oldSchedGroup, ProcessRecordInternal app) {
        if (app == null) return false;
        int curAdj = app.getCurAdj();
        if (curAdj <= ADJ_FOREGROUND_MIN || curAdj >= ADJ_FOREGROUND_MAX) return false;
        if (app.processName != null && app.processName.startsWith(AXION_PREFIX)) return false;
        if (SYSTEMUI_PROCESS_NAME.equals(app.processName)) return false;
        if (UserHandle.getAppId(app.uid) < Process.FIRST_APPLICATION_UID) return false;
        return !APP_SELF_CONTROL_WHITELIST.contains(app.processName);
    }

    public static boolean isRestrictedNeedSelfControll(ProcessRecordInternal app) {
        if (app == null) return false;
        int curAdj = app.getCurAdj();
        if (curAdj < ADJ_FOREGROUND_MIN) return false;
        if (app.processName != null && app.processName.startsWith(AXION_PREFIX)) return false;
        if (SYSTEMUI_PROCESS_NAME.equals(app.processName)) return false;
        if (UserHandle.getAppId(app.uid) < Process.FIRST_APPLICATION_UID) return false;
        return !APP_SELF_CONTROL_WHITELIST.contains(app.processName);
    }
}
