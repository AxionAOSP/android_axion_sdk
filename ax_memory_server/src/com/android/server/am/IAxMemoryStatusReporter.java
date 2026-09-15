package com.android.server.am;

import android.content.Context;
import java.io.PrintWriter;

public interface IAxMemoryStatusReporter {
    default void systemReady(ActivityManagerService ams, Context context) {
    }

    default void handleDump(PrintWriter pw, String[] args) {
    }

    default void updatePsi(int pressure) {
    }

    default void checkLowMemory(ProcessRecord dieApp) {
    }

    default void silentKillSystemui() {
    }

    default void updateColdStart(ProcessRecord app) {
    }

    default void getBackgroundProcesses(String packageName) {
    }

    default void getAppColdTime(String packageName, int type, int time) {
    }

    default int getMemFactor() {
        return -1;
    }
}
