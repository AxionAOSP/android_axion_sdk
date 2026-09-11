package com.android.internal.dragonite;

import android.app.ActivityManager;
import android.os.Bundle;
import android.os.RemoteException;

/**
 * @hide
 */
public final class AxDragoniteInternal {
    private AxDragoniteInternal() {
    }

    public static int sceneBoostAcquire(int sceneId, Bundle bundle) {
        try {
            return ActivityManager.getService().sceneBoostAcquire(sceneId, bundle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public static void sceneBoostRelease(int handle) {
        try {
            ActivityManager.getService().sceneBoostRelease(handle);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    public static boolean isSceneIdExist(int sceneId) {
        try {
            return ActivityManager.getService().isSceneIdExist(sceneId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
