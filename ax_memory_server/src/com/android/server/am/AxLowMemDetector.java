package com.android.server.am;

import android.util.Slog;

public class AxLowMemDetector {
    private static final String TAG = "AxLowMemDetector";
    private boolean mRunning;
    private int mEpollFd;
    private LowMemDetectorThread mThread;
    private final Object mLock = new Object();
    private int mPressureLevel = 0;

    private final class LowMemDetectorThread extends Thread {
        LowMemDetectorThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            int pressure;
            while (mRunning && !isInterrupted() && (pressure = AxResourceDetector.waitForPressure(mEpollFd)) >= 0) {
                synchronized (mLock) {
                    if (mRunning) {
                        mPressureLevel = pressure;
                    }
                }
            }
        }
    }

    public AxLowMemDetector(int thresholdUs, int windowUs, int flags, String threadName) {
        this.mEpollFd = -1;
        int fd = AxResourceDetector.init(0, thresholdUs, windowUs, flags);
        this.mEpollFd = fd;
        this.mRunning = fd >= 0;
        if (this.mRunning) {
            this.mThread = new LowMemDetectorThread(threadName);
            this.mThread.start();
        } else {
            Slog.i(TAG, "AxLowMemDetector init failed, mEpollFd=" + this.mEpollFd);
        }
    }

    public int getPressureLevel() {
        synchronized (this.mLock) {
            return this.mPressureLevel;
        }
    }

    public void stop() {
        synchronized (this.mLock) {
            if (this.mRunning && this.mEpollFd >= 0) {
                this.mRunning = false;
                AxResourceDetector.closeDetector(this.mEpollFd);
                if (this.mThread != null) {
                    this.mThread.interrupt();
                }
                this.mEpollFd = -1;
            }
        }
    }
}
