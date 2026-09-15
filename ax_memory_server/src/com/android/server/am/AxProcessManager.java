package com.android.server.am;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import java.io.PrintWriter;
import java.util.ArrayList;

public class AxProcessManager implements IAxProcessManager {
    public static final String TAG = "AxProcessManager";

    private static final int MSG_RESTART_NEXT_SERVICE = 0;
    private static final long DELAY_STAGGER_MS = 3000L;
    private static final boolean DEBUG = SystemProperties.getBoolean("persist.sys.ax_mem.debug", false);

    private static volatile AxProcessManager sInstance;

    private ActivityManagerService mAm;
    private Context mContext;
    private HandlerThread mHandlerThread;
    private WorkHandler mHandler;

    private boolean mEnableDelayRestart = true;
    private final ArrayList<ServiceRecord> mDelayedServices = new ArrayList<>();
    private String mTopApp = "";

    public static AxProcessManager getInstance() {
        if (sInstance == null) {
            synchronized (AxProcessManager.class) {
                if (sInstance == null) {
                    sInstance = new AxProcessManager();
                }
            }
        }
        return sInstance;
    }

    private AxProcessManager() {
    }

    private final class WorkHandler extends Handler {
        WorkHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_RESTART_NEXT_SERVICE) {
                processNextDelayedService();
            }
        }
    }

    @Override
    public void systemReady(ActivityManagerService ams, Context context) {
        this.mAm = ams;
        this.mContext = context;
        this.mHandlerThread = new HandlerThread("AxProcessManager");
        this.mHandlerThread.start();
        this.mHandler = new WorkHandler(this.mHandlerThread.getLooper());
        Slog.i(TAG, "AxProcessManager initialized");
    }

    @Override
    public boolean checkDelayRestartService(ActivityManagerService ams, ServiceRecord sr) {
        if (!this.mEnableDelayRestart || sr == null) {
            return false;
        }

        boolean isHighPressure = AxMemoryManager.getInstance().isCameraPackage(this.mTopApp)
                || AxMemoryStatusReporter.getInstance().getMemFactor() >= 2;

        if (!isHighPressure) {
            return false;
        }

        synchronized (this.mDelayedServices) {
            if (!this.mDelayedServices.contains(sr)) {
                this.mDelayedServices.add(sr);
                if (DEBUG) {
                    Slog.d(TAG, "Delaying restart for crashed service: " + sr.processName + "/" + sr.shortInstanceName);
                }
            }
        }
        return true;
    }

    @Override
    public void updateTopApp(String topPackageName) {
        String prev = this.mTopApp;
        this.mTopApp = topPackageName != null ? topPackageName : "";

        if (AxMemoryManager.getInstance().isCameraPackage(prev) && !AxMemoryManager.getInstance().isCameraPackage(this.mTopApp)) {
            triggerFlushDelayedServices();
        }
    }

    public void triggerFlushDelayedServices() {
        if (this.mHandler != null) {
            this.mHandler.removeMessages(MSG_RESTART_NEXT_SERVICE);
            this.mHandler.sendEmptyMessageDelayed(MSG_RESTART_NEXT_SERVICE, 1000L);
        }
    }

    private void processNextDelayedService() {
        ServiceRecord target = null;
        synchronized (this.mDelayedServices) {
            if (!this.mDelayedServices.isEmpty()) {
                target = this.mDelayedServices.remove(0);
            }
        }

        if (target != null && this.mAm != null) {
            long now = SystemClock.uptimeMillis();
            target.nextRestartTime = now;
            synchronized (this.mAm) {
                try {
                    this.mAm.mServices.performScheduleRestartLocked(target, "Scheduling", "AxDelayRestart", now);
                } catch (Throwable t) {
                    Slog.w(TAG, "Failed to schedule delayed restart for " + target.processName, t);
                }
            }

            synchronized (this.mDelayedServices) {
                if (!this.mDelayedServices.isEmpty() && this.mHandler != null) {
                    this.mHandler.sendEmptyMessageDelayed(MSG_RESTART_NEXT_SERVICE, DELAY_STAGGER_MS);
                }
            }
        }
    }

    @Override
    public void handleDump(PrintWriter pw, String[] args) {
        pw.println("AxProcessManager Dump:");
        pw.println("  TopApp: " + this.mTopApp);
        synchronized (this.mDelayedServices) {
            pw.println("  Pending delayed services: " + this.mDelayedServices.size());
            for (ServiceRecord sr : this.mDelayedServices) {
                pw.println("    " + sr.processName + "/" + sr.shortInstanceName);
            }
        }
    }
}
