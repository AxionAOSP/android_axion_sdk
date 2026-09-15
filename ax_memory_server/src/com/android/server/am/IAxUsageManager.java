package com.android.server.am;

import android.content.Context;
import android.content.pm.PackageManagerInternal;
import java.io.PrintWriter;
import java.util.ArrayList;

public interface IAxUsageManager {
    default void systemReady(Context context, PackageManagerInternal pmi) {
    }

    default void handleDump(PrintWriter pw, String[] args) {
    }

    default void cleanAllData(long millis) {
    }

    default void setScreenState(boolean isOff) {
    }

    default void removePackage(String packageName) {
    }

    default void setUpdatingPackage(String packageName) {
    }

    default void addNewPackages(String packageName) {
    }

    default void updateLaunchTime(String packageName) {
    }

    default void updateDuration(String packageName) {
    }

    default ArrayList<String> getHighUsedPackageList(boolean needUpdate) {
        return new ArrayList<>();
    }

    default ArrayList<String> getGeneralUsedPackageList(boolean needUpdate) {
        return new ArrayList<>();
    }

    default ArrayList<String> getLowUsedPackageList(boolean needUpdate) {
        return new ArrayList<>();
    }

    default boolean isHighUsedPackages(String pkgName) {
        return false;
    }

    default ArrayList<SimpleAppRecord> getHighUsedRecords(boolean needUpdate) {
        return new ArrayList<>();
    }

    default SimpleAppRecord geedHighUsedRecord(boolean needUpdate, String packageName) {
        return null;
    }

    default void setRemoveTaskTime(String pkgName) {
    }

    default void setLastCachedPss(String pkgName, long pss) {
    }

    default void appDied(ProcessRecord p) {
    }

    default void setTargetAdj(String pkgName, int adj) {
    }
}
