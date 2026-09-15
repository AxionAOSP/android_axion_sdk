package com.android.server.am;

public final class AxResourceDetector {
    public static final int TYPE_MEMORY = 0;
    public static final int TYPE_CPU = 1;
    public static final int TYPE_IO = 2;

    private AxResourceDetector() {
        throw new IllegalStateException("Utility class");
    }

    public static native int init(int resourceType, int thresholdUs, int windowUs, int flags);

    public static native int waitForPressure(int epollFd);

    public static native void closeDetector(int epollFd);

    public static native void readahead(String path, boolean forceRead);
}
