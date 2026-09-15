package com.android.server.am;

import android.content.Context;
import android.os.Process;
import android.os.SystemProperties;
import android.util.Slog;
import java.io.PrintWriter;

public class AxMemoryStatusReporter implements IAxMemoryStatusReporter {
    public static final String TAG = "AxMemoryStatusReporter";

    private static final boolean DEBUG = SystemProperties.getBoolean("persist.sys.ax_mem.debug", false);

    private static volatile AxMemoryStatusReporter sInstance;

    private ActivityManagerService mAm;
    private Context mContext;
    private int mMemFactor = 0;
    private int mColdStartCount = 0;

    public static AxMemoryStatusReporter getInstance() {
        if (sInstance == null) {
            synchronized (AxMemoryStatusReporter.class) {
                if (sInstance == null) {
                    sInstance = new AxMemoryStatusReporter();
                }
            }
        }
        return sInstance;
    }

    private AxMemoryStatusReporter() {
    }

    @Override
    public void systemReady(ActivityManagerService ams, Context context) {
        this.mAm = ams;
        this.mContext = context;
        Slog.i(TAG, "AxMemoryStatusReporter initialized");
    }

    @Override
    public void updatePsi(int pressure) {
        this.mMemFactor = pressure;
        if (DEBUG && pressure >= 2) {
            Slog.w(TAG, "PSI Memory Pressure elevated: level=" + pressure);
        }
    }

    @Override
    public int getMemFactor() {
        return this.mMemFactor;
    }

    @Override
    public void checkLowMemory(ProcessRecord dieApp) {
        if (dieApp == null || this.mAm == null) {
            return;
        }
        if (this.mAm.mAppProfiler.getLastMemoryLevelLocked() > 0 && DEBUG) {
            Slog.w(TAG, "Low memory detected on death of " + dieApp.processName);
        }
    }

    @Override
    public void silentKillSystemui() {
        if (this.mAm == null) {
            return;
        }
        ProcessRecord systemUiRecord = null;
        synchronized (this.mAm.mPidsSelfLocked) {
            for (int i = 0; i < this.mAm.mPidsSelfLocked.size(); i++) {
                ProcessRecord pr = this.mAm.mPidsSelfLocked.valueAt(i);
                if (pr != null && "com.android.systemui".equals(pr.processName)) {
                    systemUiRecord = pr;
                    break;
                }
            }
        }
        if (systemUiRecord != null && systemUiRecord.mProfile != null) {
            long pss = systemUiRecord.mProfile.getLastPss();
            if (pss > 1048576L) {
                Slog.w(TAG, "SystemUI PSS excessive during sleep (" + pss + "kB), triggering silent restart");
                Process.killProcess(systemUiRecord.getPid());
            }
        }
    }

    @Override
    public void updateColdStart(ProcessRecord app) {
        this.mColdStartCount++;
    }

    @Override
    public void getBackgroundProcesses(String packageName) {
    }

    @Override
    public void getAppColdTime(String packageName, int type, int time) {
    }

    @Override
    public void handleDump(PrintWriter pw, String[] args) {
        pw.println("AxMemoryStatusReporter Dump:");
        pw.println("  mMemFactor=" + this.mMemFactor);
        pw.println("  mColdStartCount=" + this.mColdStartCount);
    }
}
