package com.android.internal.dexopt;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.os.RemoteException;
import android.util.Log;
import com.android.internal.dexopt.IAxUserStartDexoptStatusHandler;
import java.util.Collections;
import java.util.List;

public class AxUserDexoptManager {

    private static final String TAG = "AxUserDexoptManager";

    public static abstract class BatchDexOptimizationCallback {
        public abstract void onConnected(List<String> list, int i, String str);
        public abstract void onProgress(int i, int i2, String str);
        public abstract void onCompleted();
        public abstract void onError(String str);
    }

    private static BatchDexOptimizationCallback sCallback;

    private static final IAxUserStartDexoptStatusHandler.Stub sHandler = new IAxUserStartDexoptStatusHandler.Stub() {
        @Override
        public void notifyConnected(List<String> pkgs, int status, String message) {
            if (sCallback == null) {
                return;
            }
            sCallback.onConnected(pkgs, status, message);
        }

        @Override
        public void notifyProgress(int current, int total, String pkgName) {
            if (sCallback == null) {
                return;
            }
            sCallback.onProgress(current, total, pkgName);
        }

        @Override
        public void notifyCompleted() {
            if (sCallback == null) {
                return;
            }
            sCallback.onCompleted();
        }

        @Override
        public void notifyError(String error) {
            if (sCallback == null) {
                return;
            }
            sCallback.onError(error);
        }
    };

    private static IActivityManager getService() {
        return ActivityManager.getService();
    }

    public static void connect(BatchDexOptimizationCallback callback) {
        sCallback = callback;
        IActivityManager am = getService();
        if (am == null) {
            return;
        }
        try {
            am.connectUserDexopt(sHandler);
        } catch (RemoteException e) {
            Log.e(TAG, "connectUserDexopt failed", e);
            return;
        }
    }

    public static void disconnect() {
        sCallback = null;
        IActivityManager am = getService();
        if (am == null) {
            return;
        }
        try {
            am.disconnectUserDexopt();
        } catch (RemoteException e) {
            Log.e(TAG, "disconnectUserDexopt failed", e);
            return;
        }
    }

    public static List<String> getPkgsToBeOptimized() {
        IActivityManager am = getService();
        if (am == null) {
            return Collections.emptyList();
        }
        try {
            return am.getPackagesToBeOptimized();
        } catch (RemoteException e) {
            Log.e(TAG, "getPackagesToBeOptimized failed", e);
            return Collections.emptyList();
        }
    }

    public static void performDexOptimization() {
        IActivityManager am = getService();
        if (am == null) {
            return;
        }
        try {
            am.performUserDexopt();
        } catch (RemoteException e) {
            Log.e(TAG, "performUserDexopt failed", e);
            return;
        }
    }
}
