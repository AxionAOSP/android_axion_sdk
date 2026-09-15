package com.android.server.am;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.wm.WindowManagerService;

public interface IAxMemoryManager {
    IAxMemoryManager DEFAULT = new IAxMemoryManager() {
    };

    default void systemReady(ActivityManagerService ams, WindowManagerService wms, Context context) {
    }

    default void setForkProcAdj(ProcessRecordInternal app) {
    }

    default boolean isEnableOptHighUsed(ProcessRecordInternal app) {
        return false;
    }

    default boolean isEnableOptHighUsed() {
        return false;
    }

    default void setOptAdj(ProcessRecordInternal app) {
    }

    default void boostCamera(boolean isColdStart) {
    }

    default void releaseMemoryAtScreenOn() {
    }

    default long releaseMemory(int minAdj, int maxCount) {
        return 0L;
    }

    default void loadProcessMemory(String packageName) {
    }

    default int getTargetAdj(ProcessRecordInternal p) {
        return -1;
    }

    default int[] getOptiAdjs() {
        return null;
    }

    default boolean isEnablePreFork(int memoryLevel) {
        return true;
    }

    default void setHighPressureScene(String pkgName) {
    }

    default void setGamingMode(boolean active, String gamePackageName) {
    }

    default void tuneMemoryParam(String packageName) {
    }

    default void onStartProcess(String processName, ApplicationInfo info, boolean isTop, String hostingType) {
    }

    default boolean isEnableOptFgServiceAdj(ProcessRecordInternal app) {
        return false;
    }

    default int getOptFgServiceAdj(ProcessRecordInternal p) {
        return -1;
    }

    default boolean isEnableOptFgServiceAdj(ProcessRecord app) {
        return false;
    }

    default int getOptFgServiceAdj(ProcessRecord p) {
        return -1;
    }

    default void killProcessOnFaceAuthStart() {
    }
}
