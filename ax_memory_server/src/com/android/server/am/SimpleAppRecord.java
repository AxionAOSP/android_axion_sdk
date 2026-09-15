package com.android.server.am;

public class SimpleAppRecord {
    public int mCurTargetAdj = -1;
    public long mLastCachedPss;
    public long mLastLmkdTimeTime;
    public long mLastRemoveTaskTime;
    public String mPackageName;

    public SimpleAppRecord() {
    }

    public SimpleAppRecord(String packageName) {
        this.mPackageName = packageName;
    }
}
