package com.android.internal.memory;

import android.app.ActivityManager;
import android.os.RemoteException;

/**
 * @hide
 */
public final class AxMemoryManager {
    public static final int DEFAULT_RELEASE_MEM_MIN_ADJ = 606;
    public static final int DEFAULT_RELEASE_MEM_MAX_COUNT = 60;

    private AxMemoryManager() {
    }

    public static void killProcessOnFaceAuthStart() {
        try {
            ActivityManager.getService().killProcessOnFaceAuthStart();
        } catch (RemoteException ignored) {
        }
    }

    public static long releaseMemory(int minAdj, int maxCount) {
        try {
            return ActivityManager.getService().releaseMemory(minAdj, maxCount);
        } catch (RemoteException e) {
            return -1L;
        }
    }

    public static long releaseMemory() {
        return releaseMemory(DEFAULT_RELEASE_MEM_MIN_ADJ, DEFAULT_RELEASE_MEM_MAX_COUNT);
    }
}
