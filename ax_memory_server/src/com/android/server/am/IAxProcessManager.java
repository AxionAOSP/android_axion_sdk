package com.android.server.am;

import android.content.Context;
import java.io.PrintWriter;

public interface IAxProcessManager {
    default void systemReady(ActivityManagerService ams, Context context) {
    }

    default boolean checkDelayRestartService(ActivityManagerService ams, ServiceRecord sr) {
        return false;
    }

    default void updateTopApp(String topPackageName) {
    }

    default void handleDump(PrintWriter pw, String[] args) {
    }
}
