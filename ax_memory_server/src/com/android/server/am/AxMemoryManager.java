package com.android.server.am;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.util.Slog;
import com.android.server.am.psc.ProcessRecordInternal;
import com.android.server.am.ProcessRecord;
import com.android.server.am.AxUsageManager;
import com.android.server.am.SimpleAppRecord;
import com.android.server.am.AxResourceDetector;
import com.android.server.am.AxProcessScoreRecord;
import com.android.server.am.AxProcessManager;
import com.android.server.am.ProcessList;
import com.android.server.wm.WindowManagerService;
import dalvik.system.DexFile;
import dalvik.system.VMRuntime;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static com.android.server.am.CachedAppOptimizer.CompactProfile.POPULATE;
import static com.android.server.am.CachedAppOptimizer.CompactSource.SHELL;

public class AxMemoryManager implements IAxMemoryManager {
    public static final String TAG = "AxMemoryManager";

    private static final String PROP_DEBUG = "persist.sys.ax_mem.debug";
    private static final String PROP_CACHE_PERCENT = "persist.sys.ax_mem.cache_percent";
    private static final String PROP_EXTRA_FREE_KBYTES = "sys.sysctl.extra_free_kbytes";
    private static final String PROP_PRELOAD_FORCE_READ = "persist.sys.ax_mem.preload";
    private static final String PROP_PRELOAD_MASK = "persist.sys.ax_mem.preload_mask";
    private static final String PROP_SWAPPINESS = "persist.sys.ax_mem.swappiness";
    private static final String PROP_BOOST_CAMERA = "persist.sys.ax_mem.boost_camera";
    private static final String PROP_CAMERA_PKG_OVERRIDE = "persist.sys.ax_mem.camera_pkg";
    private static final String PROP_LOW_ADJ = "persist.sys.ax_mem.low_adj";
    private static final String PROP_MID_ADJ = "persist.sys.ax_mem.mid_adj";
    private static final String PROP_HIGH_ADJ = "persist.sys.ax_mem.high_adj";

    private static final String BUNDLE_KEY_PACKAGE_NAME = "packageName";

    public static final String PACKAGE_GAMESPACE = "com.android.axion.gamespace";
    public static final String PACKAGE_SYSTEMUI = "com.android.systemui";
    public static final String PACKAGE_LAUNCHER = "com.android.launcher3";
    public static final String PACKAGE_SETTINGS = "com.android.settings";
    public static final String PACKAGE_PHONE = "com.android.phone";

    private static final Set<String> KNOWN_CAMERA_PACKAGES = Set.of(
            "org.lineageos.aperture",
            "com.google.android.GoogleCamera",
            "com.google.android.apps.googlecamera.fishfood",
            "com.android.camera",
            "com.android.camera2"
    );

    private static final Set<String> KNOWN_LAUNCHER_PACKAGES = Set.of(
            PACKAGE_LAUNCHER,
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher"
    );

    private static final Set<String> CORE_PROTECTED_PACKAGES = Set.of(
            PACKAGE_SYSTEMUI,
            PACKAGE_LAUNCHER,
            PACKAGE_GAMESPACE,
            PACKAGE_SETTINGS,
            PACKAGE_PHONE
    );

    public static final int DEFAULT_RELEASE_MEM_MIN_ADJ = 606;
    public static final int DEFAULT_RELEASE_MEM_MAX_COUNT = 60;
    public static final int MAX_RELEASE_MEM_COUNT_LIMIT = 100;
    public static final int SWAPPINESS_GAMING = 60;
    public static final int EXTRA_FREE_FACTOR_GAMING = 4;
    public static final int EXTRA_FREE_FACTOR_DEFAULT = 3;
    public static final float GAMING_KILL_WEIGHT_ADJ = 0.6f;
    public static final float GAMING_KILL_WEIGHT_RSS = 0.4f;
    public static final long RAM_4G_PSS_LIMIT_KB = 524288L;
    public static final int PROCESS_KILL_COUNT_4G_COLD = 10;
    public static final int PROCESS_KILL_COUNT_4G_WARM = 3;
    public static final int SCREEN_ON_KILL_COUNT_4G = 8;

    private static final boolean DEBUG = SystemProperties.getBoolean(PROP_DEBUG, false);

    private static final long KILL_PREFORK_DELAY_MS = 10800000L;
    private static final long DELAY_BOOT_COMPLETED_PREFORK_MS = 30000L;
    private static final long DELAY_PREFORK_STAGGER_MS = 3000L;

    private static final long RAM_16G_KB = 16777216L;
    private static final long RAM_12G_KB = 12582912L;
    private static final long RAM_10G_KB = 10485760L;
    private static final long RAM_8G_KB = 8388608L;
    private static final long RAM_6G_KB = 6291456L;
    private static final long RAM_4G_KB = 4194304L;

    private static final int MSG_MEM_PRELOAD_APP_FILES = 0;
    private static final int MSG_MEM_LOAD_PROCESS_MEMORY = 1;

    private static final int MSG_TUNE_EXTRA_FREE = 0;
    private static final int MSG_KILL_PRE_FORK_APP = 1;
    private static final int MSG_BOOT_COMPLETED_PREFORK = 2;
    private static final int MSG_SPAWN_PREFORK_APP = 3;
    private static final int MSG_BOOST_CAMERA_WARM = 4;
    private static final int MSG_RESET_BOOST_CAMERA_WARM = 5;
    private static final int MSG_RELEASE_MEMORY_SCREEN_ON = 6;
    private static final int MSG_BOOST_CAMERA_COLD = 8;
    private static final int MSG_RESET_BOOST_CAMERA_COLD = 9;
    private static final int MSG_TUNE_MEMORY_PARAM = 10;
    private static final int MSG_PERIODIC_COMPUTE_ADJ = 11;

    private static final long sPhysicalRamKb = readPhysicalRamKb();
    private static final List<String> sScreenOnKillWhitelist = List.of(
            "com.google.android.googlequicksearchbox:search",
            "com.google.android.gms",
            "com.android.chrome"
    );

    private static volatile AxMemoryManager sInstance;

    private WindowManagerService mWindowManager;
    private ActivityManagerService mActivityManager;
    private Context mContext;
    private HandlerThread mMainThread;
    private Handler mMainHandler;
    private HandlerThread mMemLoaderThread;
    private Handler mMemLoaderHandler;

    private boolean mEnableOptHighUsed = true;
    private boolean mEnableOptFgServiceAdj = true;
    private boolean mEnableForkHighUsed = true;
    private boolean mEnableFaceAuthKill = true;

    private int mForkProcAdj = 801;
    private int mHighUsedAdj = 801;
    private int mMaxForkNumber = 5;
    private int mTopRankHighUsed = 5;

    private long mTotalPssLimitKb = 1048576L;
    private long mDefaultCachedPssKb = 204800L;

    private final ArrayList<ProcessRecord> mForkedHighUsedProcesses = new ArrayList<>();

    private boolean mEnableBoostCamera = true;
    private boolean mBoostCameraCompaction = false;
    private boolean mIsBoostingCameraColdStart = false;
    private boolean mIsBoostingCameraStart = false;

    private long mBoostCameraDurationMs = 5000L;
    private int mKillProcessCountCameraCold = 15;
    private int mKillProcessCountCameraWarm = 5;

    private boolean mEnableReleaseMemoryScreenOn = true;
    private long mLastReleaseMemoryScreenOnTime = 0L;
    private long mReleaseMemoryScreenOnDurationMs = 3600000L;
    private long mReleaseMemoryAdjWeight = 10L;
    private int mKillProcessScreenOnCount = 10;

    private boolean mEnableLoadProcessMemory = true;
    private int mCachePercentDefault = 0;
    private int mCachePercentCamera = 0;

    private final long[] mPssTiers = new long[]{102400L, 204800L, 512000L};
    private final int[] mTargetAdjTiers = new int[]{201, 401, 801};

    private long mRemoveTaskCooldownMs = 86400000L;
    private long mComputeTargetAdjDurationMs = 600000L;

    private boolean mEnablePreFork = true;
    private boolean mIsHighPressureScene = false;
    private int mPreforkMemoryLevel = 0;
    private int mFaceUnlockKillMinAdj = 900;
    private int mFaceUnlockKillCount = 5;

    private final ArrayList<String> mWhiteListForCameraStart = new ArrayList<>();
    private final ArrayList<String> mWhiteListForFaceAuthStart = new ArrayList<>();
    private int mAppStartupPreloadMask = 3;
    private final HashMap<String, Integer> mOptFgServiceAdjMap = new HashMap<>();

    private boolean mEnableTuneSwappiness = true;
    private int mDefaultSwappiness = 100;
    private int mCurSwappiness = 100;
    private final HashMap<String, Integer> mAppSwappinessMap = new HashMap<>();

    private boolean mEnableTuneExtraFree = true;
    private int mDefaultExtraFreeFactor = 3;
    private int mCurExtraFreeFactor = 3;
    private final HashMap<String, Integer> mAppExtraFreeMap = new HashMap<>();

    private String mCurForegroundPackage = "";
    private String mPreForegroundPackage = "";

    public static AxMemoryManager getInstance() {
        if (sInstance == null) {
            synchronized (AxMemoryManager.class) {
                if (sInstance == null) {
                    sInstance = new AxMemoryManager();
                }
            }
        }
        return sInstance;
    }

    public static synchronized AxMemoryManager init(ActivityManagerService ams, WindowManagerService wms, Context context) {
        AxMemoryManager manager = getInstance();
        manager.systemReady(ams, wms, context);
        return manager;
    }

    private AxMemoryManager() {
        configureBuiltinDefaults();
    }

    private void configureBuiltinDefaults() {
        if (sPhysicalRamKb >= RAM_12G_KB) {
            mKillProcessCountCameraCold = 5;
            mKillProcessCountCameraWarm = 5;
            mKillProcessScreenOnCount = 10;
        } else if (sPhysicalRamKb >= RAM_8G_KB) {
            mKillProcessCountCameraCold = 15;
            mKillProcessCountCameraWarm = 5;
            mKillProcessScreenOnCount = 15;
        } else if (sPhysicalRamKb >= RAM_6G_KB) {
            mKillProcessCountCameraCold = 20;
            mKillProcessCountCameraWarm = 8;
            mKillProcessScreenOnCount = 20;
        } else {
            mKillProcessCountCameraCold = PROCESS_KILL_COUNT_4G_COLD;
            mKillProcessCountCameraWarm = PROCESS_KILL_COUNT_4G_WARM;
            mKillProcessScreenOnCount = SCREEN_ON_KILL_COUNT_4G;
            mTotalPssLimitKb = RAM_4G_PSS_LIMIT_KB;
        }

        mWhiteListForCameraStart.addAll(sScreenOnKillWhitelist);
        mWhiteListForFaceAuthStart.addAll(sScreenOnKillWhitelist);

        mOptFgServiceAdjMap.put(PACKAGE_SYSTEMUI, 0);
        mOptFgServiceAdjMap.put(PACKAGE_LAUNCHER, 100);
        mOptFgServiceAdjMap.put("com.camera.ufs.service", 100);

        mAppSwappinessMap.put("org.lineageos.aperture", SWAPPINESS_GAMING);
        mAppSwappinessMap.put("com.google.android.GoogleCamera", SWAPPINESS_GAMING);
        mAppSwappinessMap.put("com.google.android.apps.googlecamera.fishfood", SWAPPINESS_GAMING);
        mAppSwappinessMap.put("com.android.camera", SWAPPINESS_GAMING);
        mAppSwappinessMap.put("com.android.camera2", SWAPPINESS_GAMING);

        mAppExtraFreeMap.put("org.lineageos.aperture", EXTRA_FREE_FACTOR_GAMING);
        mAppExtraFreeMap.put("com.google.android.GoogleCamera", EXTRA_FREE_FACTOR_GAMING);
        mAppExtraFreeMap.put("com.google.android.apps.googlecamera.fishfood", EXTRA_FREE_FACTOR_GAMING);
        mAppExtraFreeMap.put("com.android.camera", EXTRA_FREE_FACTOR_GAMING);
        mAppExtraFreeMap.put("com.android.camera2", EXTRA_FREE_FACTOR_GAMING);
    }

    private final class ProcessMemLoaderHandler extends Handler {
        ProcessMemLoaderHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == MSG_MEM_PRELOAD_APP_FILES) {
                handlePreloadAppFiles(message);
            } else if (message.what == MSG_MEM_LOAD_PROCESS_MEMORY) {
                handleLoadProcessMemory(message);
            }
        }
    }

    private final class MainHandler extends Handler {
        MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_TUNE_EXTRA_FREE:
                    applyExtraFreeTuning();
                    return;

                case MSG_KILL_PRE_FORK_APP:
                    handleKillPreForkApp(message.getData().getInt("pid", -1));
                    return;

                case MSG_BOOT_COMPLETED_PREFORK:
                    executeBootPrefork();
                    return;

                case MSG_SPAWN_PREFORK_APP:
                    handleSpawnPreforkApp(message.getData().getString("proc", ""));
                    return;

                case MSG_BOOST_CAMERA_WARM:
                    handleBoostCameraWarm();
                    return;

                case MSG_RESET_BOOST_CAMERA_WARM:
                    mIsBoostingCameraStart = false;
                    return;

                case MSG_RELEASE_MEMORY_SCREEN_ON:
                    handleReleaseMemoryScreenOn();
                    return;

                case MSG_BOOST_CAMERA_COLD:
                    handleBoostCameraCold();
                    return;

                case MSG_RESET_BOOST_CAMERA_COLD:
                    mIsBoostingCameraColdStart = false;
                    return;

                case MSG_TUNE_MEMORY_PARAM:
                    handleTuneMemoryParam(message.getData().getString(BUNDLE_KEY_PACKAGE_NAME, ""));
                    return;

                case MSG_PERIODIC_COMPUTE_ADJ:
                    handlePeriodicComputeAdj();
                    return;

                default:
                    return;
            }
        }
    }

    public static final class AdjComparator implements Comparator<AxProcessScoreRecord> {
        @Override
        public int compare(AxProcessScoreRecord r1, AxProcessScoreRecord r2) {
            return Integer.compare(r1.adj, r2.adj);
        }
    }

    public static final class RssComparator implements Comparator<AxProcessScoreRecord> {
        @Override
        public int compare(AxProcessScoreRecord r1, AxProcessScoreRecord r2) {
            return Long.compare(r1.rss, r2.rss);
        }
    }

    public static final class ScoreComparator implements Comparator<AxProcessScoreRecord> {
        @Override
        public int compare(AxProcessScoreRecord r1, AxProcessScoreRecord r2) {
            return Float.compare(r2.score, r1.score);
        }
    }

    private static class PreloadWorkerThread extends Thread {
        private final String mPath;

        PreloadWorkerThread(String path) {
            this.mPath = path;
        }

        @Override
        public void run() {
            try {
                AxResourceDetector.readahead(this.mPath, true);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void systemReady(ActivityManagerService ams, WindowManagerService wms, Context context) {
        this.mActivityManager = ams;
        this.mWindowManager = wms;
        this.mContext = context;
        AxUsageManager.getInstance().systemReady(context, null);
        startMainThread();
        startMemLoaderThread();
        scheduleBootPrefork();
        publishTargetAdjs();
        Slog.i(TAG, "AxMemoryManager initialized successfully, physical RAM: " + sPhysicalRamKb + " kB");
    }

    private void startMainThread() {
        this.mMainThread = new HandlerThread("AxMemoryManager");
        this.mMainThread.start();
        this.mMainHandler = new MainHandler(this.mMainThread.getLooper());
    }

    private void startMemLoaderThread() {
        this.mMemLoaderThread = new HandlerThread("AxMmProcessMemLoader");
        this.mMemLoaderThread.start();
        this.mMemLoaderHandler = new ProcessMemLoaderHandler(this.mMemLoaderThread.getLooper());
    }

    private void scheduleBootPrefork() {
        if (this.mMainHandler != null) {
            this.mMainHandler.sendMessageDelayed(
                    this.mMainHandler.obtainMessage(MSG_BOOT_COMPLETED_PREFORK),
                    DELAY_BOOT_COMPLETED_PREFORK_MS
            );
        }
    }

    private void publishTargetAdjs() {
        SystemProperties.set(PROP_LOW_ADJ, Integer.toString(this.mTargetAdjTiers[0]));
        SystemProperties.set(PROP_MID_ADJ, Integer.toString(this.mTargetAdjTiers[1]));
        SystemProperties.set(PROP_HIGH_ADJ, Integer.toString(this.mTargetAdjTiers[2]));
        ProcessList.updateLmkProps();
    }

    private static long readPhysicalRamKb() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    long totalKb = Long.parseLong(line.substring(line.indexOf(":") + 1, line.indexOf("kB")).trim());
                    if (totalKb > RAM_12G_KB) {
                        return RAM_16G_KB;
                    }
                    if (totalKb > RAM_10G_KB) {
                        return RAM_12G_KB;
                    }
                    if (totalKb > RAM_8G_KB) {
                        return RAM_10G_KB;
                    }
                    if (totalKb > RAM_6G_KB) {
                        return RAM_8G_KB;
                    }
                    if (totalKb > RAM_4G_KB) {
                        return RAM_6G_KB;
                    }
                    return RAM_4G_KB;
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to read /proc/meminfo", e);
        }
        return -1L;
    }

    private void handlePreloadAppFiles(Message message) {
        Bundle data = message.getData();
        String processName = data.getString("processName", "");
        ApplicationInfo appInfo = data.getParcelable("appInfo");
        if (SystemProperties.getBoolean(PROP_PRELOAD_FORCE_READ, true) && processName.length() > 0) {
            executeAppFilePreload(processName, appInfo);
        }
    }

    private void executeAppFilePreload(String processName, ApplicationInfo appInfo) {
        if (appInfo == null) {
            return;
        }

        ArrayList<String> candidatePaths = new ArrayList<>();
        ArrayList<String> codeDirs = new ArrayList<>();
        codeDirs.add(appInfo.sourceDir);
        if (appInfo.splitSourceDirs != null) {
            Collections.addAll(codeDirs, appInfo.splitSourceDirs);
        }

        this.mAppStartupPreloadMask = SystemProperties.getInt(PROP_PRELOAD_MASK, 3);
        if ((this.mAppStartupPreloadMask & 1) != 0) {
            candidatePaths.addAll(codeDirs);
        }

        if ((this.mAppStartupPreloadMask & 2) != 0) {
            String abi = appInfo.primaryCpuAbi != null ? appInfo.primaryCpuAbi : Build.SUPPORTED_ABIS[0];
            try {
                String[] dexOutputPaths = DexFile.getDexFileOutputPaths(appInfo.getBaseCodePath(), VMRuntime.getInstructionSet(abi));
                if (dexOutputPaths != null) {
                    Collections.addAll(candidatePaths, dexOutputPaths);
                }
            } catch (IOException ignored) {
            }
        }

        ArrayList<PreloadWorkerThread> workerThreads = new ArrayList<>();
        for (String path : candidatePaths) {
            PreloadWorkerThread worker = new PreloadWorkerThread(path);
            workerThreads.add(worker);
            worker.start();
        }

        for (PreloadWorkerThread worker : workerThreads) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }
    }

    private void handleLoadProcessMemory(Message message) {
        String packageName = message.getData().getString(BUNDLE_KEY_PACKAGE_NAME, "");
        if (packageName.length() > 0) {
            executeLoadProcessMemory(packageName);
        }
    }

    private void executeLoadProcessMemory(String packageName) {
        if (packageName.length() <= 0 || this.mActivityManager == null) {
            return;
        }

        ProcessRecord pr = findProcessRecord(packageName);
        if (pr == null) {
            return;
        }

        synchronized (this.mActivityManager.mProcLock) {
            try {
                this.mActivityManager.getCachedAppOptimizer().compactApp(
                        pr,
                        POPULATE,
                        SHELL,
                        true
                );
            } catch (Throwable ignored) {
            }
        }
    }

    private ProcessRecord findProcessRecord(String packageName) {
        synchronized (this.mActivityManager.mProcLock) {
            int currentUserId = this.mActivityManager.getCurrentUserId();
            int packageUid = this.mActivityManager.getPackageManagerInternal().getPackageUid(packageName, 0L, currentUserId);
            return this.mActivityManager.getProcessRecordLocked(packageName, packageUid);
        }
    }

    private void handleKillPreForkApp(int pid) {
        synchronized (this.mForkedHighUsedProcesses) {
            for (int i = 0; i < this.mForkedHighUsedProcesses.size(); i++) {
                ProcessRecord pr = this.mForkedHighUsedProcesses.get(i);
                if (pr.mPid == pid) {
                    this.mForkedHighUsedProcesses.remove(i);
                    Process.killProcess(pid);
                    break;
                }
            }
        }
    }

    private void handleSpawnPreforkApp(String proc) {
        if (proc.length() > 0 && this.mActivityManager != null) {
            ArrayList<String> emptyApps = new ArrayList<>();
            emptyApps.add(proc);
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("start_empty_apps", emptyApps);
            bundle.putBoolean("fork_high", true);
            this.mActivityManager.startActivityAsUserEmpty(bundle);
        }
    }

    private void handleBoostCameraWarm() {
        if (this.mBoostCameraCompaction) {
            SystemProperties.set(PROP_BOOST_CAMERA, "1");
        }
        killLruProcessesForMemory(900, this.mKillProcessCountCameraWarm, true, this.mWhiteListForCameraStart);
    }

    private void handleBoostCameraCold() {
        if (this.mBoostCameraCompaction) {
            SystemProperties.set(PROP_BOOST_CAMERA, "1");
        }
        killLruProcessesForMemory(900, this.mKillProcessCountCameraCold, true, this.mWhiteListForCameraStart);
    }

    private void handleReleaseMemoryScreenOn() {
        SystemProperties.set(PROP_BOOST_CAMERA, "1");
        killLruProcessesForMemory(900, this.mKillProcessScreenOnCount, false, sScreenOnKillWhitelist);
    }

    private void handleTuneMemoryParam(String packageName) {
        this.mPreForegroundPackage = this.mCurForegroundPackage;
        this.mCurForegroundPackage = packageName;
        applyLmkdCameraPolicy();
        applySwappinessTuning();
        applyExtraFreeTuning();
    }

    private void handlePeriodicComputeAdj() {
        if (this.mEnableOptHighUsed) {
            recalculateAppUsageTargetAdjs();
            if (this.mMainHandler != null) {
                this.mMainHandler.sendMessageDelayed(
                        this.mMainHandler.obtainMessage(MSG_PERIODIC_COMPUTE_ADJ),
                        this.mComputeTargetAdjDurationMs
                );
            }
        }
    }

    private void recalculateAppUsageTargetAdjs() {
        ArrayList<SimpleAppRecord> records = AxUsageManager.getInstance().getHighUsedRecords(false);
        long now = System.currentTimeMillis();
        for (SimpleAppRecord record : records) {
            computeRecordTargetAdj(record, now);
        }
    }

    private void computeRecordTargetAdj(SimpleAppRecord record, long now) {
        int targetAdj = this.mTargetAdjTiers[2];
        long lastRemove = record.mLastRemoveTaskTime;
        if (lastRemove != 0 && (now - lastRemove) < this.mRemoveTaskCooldownMs) {
            AxUsageManager.getInstance().setTargetAdj(record.mPackageName, -1);
            return;
        }

        if ((now - record.mLastLmkdTimeTime) < this.mComputeTargetAdjDurationMs) {
            int curTarget = record.mCurTargetAdj;
            if (curTarget == this.mTargetAdjTiers[2]) {
                targetAdj = this.mTargetAdjTiers[1];
            } else if (curTarget == this.mTargetAdjTiers[1]) {
                targetAdj = this.mTargetAdjTiers[0];
            }
        }

        long lastPss = record.mLastCachedPss;
        if (lastPss > this.mPssTiers[2]) {
            targetAdj = this.mTargetAdjTiers[2];
        } else if (lastPss > this.mPssTiers[1] && targetAdj < this.mTargetAdjTiers[1]) {
            targetAdj = this.mTargetAdjTiers[1];
        }

        AxUsageManager.getInstance().setTargetAdj(record.mPackageName, targetAdj);
    }

    private void executeBootPrefork() {
        if (!this.mEnableForkHighUsed) {
            return;
        }

        ArrayList<SimpleAppRecord> records = AxUsageManager.getInstance().getHighUsedRecords(true);
        if (records.isEmpty()) {
            return;
        }

        if (sPhysicalRamKb != -1L) {
            this.mTotalPssLimitKb = (long) (this.mTotalPssLimitKb * (sPhysicalRamKb / 8388608.0d));
        }

        for (SimpleAppRecord rec : records) {
            if (rec.mLastCachedPss == 0) {
                rec.mLastCachedPss = this.mDefaultCachedPssKb;
            }
        }

        ArrayList<String> forkList = new ArrayList<>();
        long totalPss = 0;
        for (SimpleAppRecord rec : records) {
            totalPss += rec.mLastCachedPss;
            if (totalPss >= this.mTotalPssLimitKb) {
                break;
            }
            forkList.add(rec.mPackageName);
        }

        long delay = 0;
        for (String proc : forkList) {
            Bundle bundle = new Bundle();
            bundle.putString("proc", proc);
            Message msg = this.mMainHandler.obtainMessage(MSG_SPAWN_PREFORK_APP);
            msg.setData(bundle);
            delay += DELAY_PREFORK_STAGGER_MS;
            this.mMainHandler.sendMessageDelayed(msg, delay);
        }
    }

    private void populateDefaultPreforkCandidates() {
        if (this.mContext == null) {
            return;
        }
        PackageManager pm = this.mContext.getPackageManager();
        Intent[] defaultIntents = new Intent[]{
            new Intent(Intent.ACTION_DIAL),
            new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")),
            new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        };
        for (Intent intent : defaultIntents) {
            ResolveInfo info = pm.resolveActivity(intent, 0);
            if (info != null && info.activityInfo != null && info.activityInfo.packageName != null) {
                String pkg = info.activityInfo.packageName;
                if (!"android".equals(pkg) && !pkg.contains("resolver")) {
                    AxUsageManager.getInstance().updateLaunchTime(pkg);
                }
            }
        }
    }

    public void recordForkedProcess(ProcessRecord pr) {
        if (pr == null) {
            return;
        }
        synchronized (this.mForkedHighUsedProcesses) {
            this.mForkedHighUsedProcesses.add(pr);
        }
        if (this.mMainHandler != null) {
            Message msg = this.mMainHandler.obtainMessage(MSG_KILL_PRE_FORK_APP);
            Bundle bundle = new Bundle();
            bundle.putInt("pid", pr.mPid);
            msg.setData(bundle);
            this.mMainHandler.sendMessageDelayed(msg, KILL_PREFORK_DELAY_MS);
        }
    }

    public boolean canForkHighUsedProcess(ProcessRecord pr) {
        if (!this.mEnableForkHighUsed || pr == null || pr.isForkedFromHighUsed) {
            return false;
        }
        if (isGamePackage(pr.processName)) {
            return true;
        }
        if (!AxUsageManager.getInstance().isHighUsedPackages(pr.processName)) {
            return false;
        }
        synchronized (this.mForkedHighUsedProcesses) {
            if (this.mForkedHighUsedProcesses.size() >= this.mMaxForkNumber) {
                return false;
            }
            for (ProcessRecord existing : this.mForkedHighUsedProcesses) {
                if (existing.processName.equals(pr.processName)) {
                    return false;
                }
            }
            return true;
        }
    }

    private ArrayList<ProcessRecord> getLruProcessesSnapshot() {
        if (this.mActivityManager == null) {
            return new ArrayList<>();
        }
        synchronized (this.mActivityManager.mProcLock) {
            return new ArrayList<>(this.mActivityManager.mProcessList.getLruProcessesLOSP());
        }
    }

    private void killLruProcessesForMemory(int minAdj, int killCount, boolean forceUiKill, List<String> whitelist) {
        if (killCount == 0 || this.mActivityManager == null) {
            return;
        }

        try {
            ArrayList<ProcessRecord> lruProcesses = getLruProcessesSnapshot();
            ArrayList<AxProcessScoreRecord> candidates = new ArrayList<>();

            for (ProcessRecord pr : lruProcesses) {
                if (pr == null || pr.getSetAdj() < minAdj) {
                    continue;
                }
                if (!pr.hasActivities() || forceUiKill) {
                    if (!whitelist.contains(pr.processName)) {
                long rss = pr.mProfile != null ? pr.mProfile.getLastRss() : 0L;
                if (rss <= 0) {
                    rss = this.mDefaultCachedPssKb;
                }
                candidates.add(new AxProcessScoreRecord(pr.getPid(), pr.getSetAdj(), rss, pr.processName));
                    }
                }
            }

            float adjWeight = forceUiKill ? 1.0f : (this.mReleaseMemoryAdjWeight / 10.0f);
            float rssWeight = 1.0f - adjWeight;

            if (rssWeight != 0.0f) {
                candidates.sort(new RssComparator());
                accumulateDimensionScores(candidates, rssWeight, 1);
            }

            if (adjWeight != 0.0f) {
                candidates.sort(new AdjComparator());
                accumulateDimensionScores(candidates, adjWeight, 0);
            }

            candidates.sort(new ScoreComparator());

            int killed = 0;
            for (AxProcessScoreRecord rec : candidates) {
                Process.killProcess(rec.pid);
                killed++;
                if (killed >= killCount) {
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void accumulateDimensionScores(ArrayList<AxProcessScoreRecord> list, float weight, int dimensionType) {
        float score = 0.0f;
        for (int i = 0; i < list.size(); i++) {
            if (i != 0) {
                AxProcessScoreRecord prev = list.get(i - 1);
                long prevVal = (dimensionType == 0) ? prev.adj : prev.rss;
                AxProcessScoreRecord curr = list.get(i);
                long currVal = (dimensionType == 0) ? curr.adj : curr.rss;
                if (currVal != prevVal) {
                    score = i * weight;
                }
                curr.score += score;
            }
        }
    }

    private void applyExtraFreeTuning() {
        if (!this.mEnableTuneExtraFree) {
            if (this.mCurExtraFreeFactor != 3) {
                setExtraFreeFactor(3);
            }
            return;
        }

        int targetFactor = this.mAppExtraFreeMap.getOrDefault(this.mCurForegroundPackage, this.mDefaultExtraFreeFactor);
        if (targetFactor != this.mCurExtraFreeFactor) {
            setExtraFreeFactor(targetFactor);
        }
    }

    private void setExtraFreeFactor(int factor) {
        if (this.mWindowManager != null) {
            Point point = new Point();
            this.mWindowManager.getBaseDisplaySize(0, point);
            int extraFreeKbytes = (((point.x * point.y) * 4) * factor) / 1024;
            SystemProperties.set(PROP_EXTRA_FREE_KBYTES, Integer.toString(extraFreeKbytes));
            this.mCurExtraFreeFactor = factor;
        }
    }

    private void applySwappinessTuning() {
        if (!this.mEnableTuneSwappiness) {
            return;
        }

        int targetSwappiness = this.mAppSwappinessMap.getOrDefault(this.mCurForegroundPackage, this.mDefaultSwappiness);
        if (targetSwappiness != this.mCurSwappiness) {
            SystemProperties.set(PROP_SWAPPINESS, Integer.toString(targetSwappiness));
            this.mCurSwappiness = targetSwappiness;
        }
    }

    private void applyLmkdCameraPolicy() {
        int curCachePercent = SystemProperties.getInt(PROP_CACHE_PERCENT, 0);
        boolean isCamera = isCameraPackage(this.mCurForegroundPackage);

        if (isCamera) {
            ProcessList.setCameraTop(1);
            if (curCachePercent != 0) {
                SystemProperties.set(PROP_CACHE_PERCENT, Integer.toString(this.mCachePercentCamera));
                ProcessList.updateLmkProps();
            }
            return;
        }

        if (isCameraPackage(this.mPreForegroundPackage)) {
            ProcessList.setCameraTop(0);
        }

        if (curCachePercent == this.mCachePercentCamera) {
            SystemProperties.set(PROP_CACHE_PERCENT, Integer.toString(this.mCachePercentDefault));
            ProcessList.updateLmkProps();
        }
    }

    private String mActiveGamePackage = "";

    public boolean isGamePackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        if (pkg.equals(this.mActiveGamePackage)) {
            return true;
        }
        if (this.mContext != null) {
            try {
                ApplicationInfo ai = this.mContext.getPackageManager().getApplicationInfo(pkg, 0);
                if (ai != null && (ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public boolean isCameraPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        String override = SystemProperties.get(PROP_CAMERA_PKG_OVERRIDE, "");
        if (!override.isEmpty() && pkg.equals(override)) {
            return true;
        }
        if (KNOWN_CAMERA_PACKAGES.contains(pkg)) {
            return true;
        }
        return pkg.toLowerCase(Locale.ROOT).contains("camera");
    }

    @Override
    public void boostCamera(boolean isColdStart) {
        if (!this.mEnableBoostCamera) {
            return;
        }

        if (isColdStart) {
            if (this.mIsBoostingCameraColdStart) {
                return;
            }
            this.mIsBoostingCameraColdStart = true;
            this.mMainHandler.sendMessage(this.mMainHandler.obtainMessage(MSG_BOOST_CAMERA_COLD));
            this.mMainHandler.sendMessageDelayed(this.mMainHandler.obtainMessage(MSG_RESET_BOOST_CAMERA_COLD), this.mBoostCameraDurationMs);
            return;
        }

        if (this.mIsBoostingCameraStart) {
            return;
        }
        this.mIsBoostingCameraStart = true;
        this.mMainHandler.sendMessage(this.mMainHandler.obtainMessage(MSG_BOOST_CAMERA_WARM));
        this.mMainHandler.sendMessageDelayed(this.mMainHandler.obtainMessage(MSG_RESET_BOOST_CAMERA_WARM), this.mBoostCameraDurationMs);
    }

    @Override
    public int getOptFgServiceAdj(ProcessRecordInternal pri) {
        int optAdj = this.mOptFgServiceAdjMap.getOrDefault(pri.processName, -1);
        if (optAdj > pri.getCurAdj()) {
            return -1;
        }
        return optAdj;
    }

    @Override
    public boolean isEnableOptFgServiceAdj(ProcessRecord app) {
        return (app != null && isEnableOptFgServiceAdj((ProcessRecordInternal) app));
    }

    @Override
    public int getOptFgServiceAdj(ProcessRecord p) {
        return p != null ? getOptFgServiceAdj((ProcessRecordInternal) p) : -1;
    }

    @Override
    public int[] getOptiAdjs() {
        return this.mTargetAdjTiers;
    }

    @Override
    public int getTargetAdj(ProcessRecordInternal pri) {
        if (pri != null && isGamePackage(pri.processName)) {
            return this.mTargetAdjTiers[0];
        }
        SimpleAppRecord sar = AxUsageManager.getInstance().geedHighUsedRecord(false, pri != null ? pri.processName : null);
        return sar != null ? sar.mCurTargetAdj : -1;
    }

    @Override
    public boolean isEnableOptFgServiceAdj(ProcessRecordInternal pri) {
        return (this.mEnableOptFgServiceAdj && this.mOptFgServiceAdjMap.containsKey(pri.processName) && "fg-service".equals(pri.getAdjType()));
    }

    @Override
    public boolean isEnableOptHighUsed(ProcessRecordInternal pri) {
        if (pri == null || "com.android.settings".equals(pri.processName) || !pri.getHasShownUi()) {
            return false;
        }
        if (isGamePackage(pri.processName)) {
            return true;
        }
        if (!this.mEnableOptHighUsed) {
            return false;
        }

        int rank = AxUsageManager.getInstance().getHighUsedPackageList(false).indexOf(pri.processName);
        return rank != -1 && rank < this.mTopRankHighUsed;
    }

    @Override
    public boolean isEnablePreFork(int memoryLevel) {
        if (!this.mEnablePreFork || this.mIsHighPressureScene) {
            return false;
        }
        return memoryLevel <= this.mPreforkMemoryLevel;
    }

    @Override
    public void killProcessOnFaceAuthStart() {
        if (this.mEnableFaceAuthKill) {
            killLruProcessesForMemory(this.mFaceUnlockKillMinAdj, this.mFaceUnlockKillCount, false, this.mWhiteListForFaceAuthStart);
        }
    }

    @Override
    public void loadProcessMemory(String packageName) {
        if (this.mEnableLoadProcessMemory && this.mMemLoaderHandler != null) {
            Bundle bundle = new Bundle();
            bundle.putString(BUNDLE_KEY_PACKAGE_NAME, packageName);
            Message msg = this.mMemLoaderHandler.obtainMessage(MSG_MEM_LOAD_PROCESS_MEMORY);
            msg.setData(bundle);
            this.mMemLoaderHandler.sendMessage(msg);
        }
    }

    @Override
    public long releaseMemory(int minAdj, int maxCount) {
        int effectiveMinAdj = (minAdj > 0) ? minAdj : DEFAULT_RELEASE_MEM_MIN_ADJ;
        int effectiveMaxCount = (maxCount > 0) ? Math.min(maxCount, MAX_RELEASE_MEM_COUNT_LIMIT) : DEFAULT_RELEASE_MEM_MAX_COUNT;

        if (this.mEnableTuneSwappiness) {
            SystemProperties.set(PROP_SWAPPINESS, Integer.toString(SWAPPINESS_GAMING));
            this.mCurSwappiness = SWAPPINESS_GAMING;
        }
        setExtraFreeFactor(EXTRA_FREE_FACTOR_GAMING);

        long freedKb = killProcessesForGaming(effectiveMinAdj, effectiveMaxCount);
        if (this.mActivityManager != null) {
            this.mActivityManager.getCachedAppOptimizer().compactAllSystem();
        }
        return Math.max(freedKb / 1024L, 0L);
    }

    private long killProcessesForGaming(int minAdj, int maxCount) {
        if (this.mActivityManager == null) {
            return 0L;
        }

        long totalFreedKb = 0L;
        try {
            ArrayList<ProcessRecord> lruProcesses = getLruProcessesSnapshot();
            ArrayList<AxProcessScoreRecord> candidates = new ArrayList<>();

            for (ProcessRecord pr : lruProcesses) {
                if (pr == null || pr.getSetAdj() < minAdj) {
                    continue;
                }
                String pkg = pr.info != null ? pr.info.packageName : pr.processName;
                if (pkg == null || isGamingProtectedPackage(pkg)) {
                    continue;
                }
                candidates.add(new AxProcessScoreRecord(pr.getPid(), pr.getSetAdj(), pr.mProfile.getLastRss(), pr.processName));
            }

            if (candidates.isEmpty()) {
                return 0L;
            }

            candidates.sort(new RssComparator());
            accumulateDimensionScores(candidates, GAMING_KILL_WEIGHT_RSS, 1);

            candidates.sort(new AdjComparator());
            accumulateDimensionScores(candidates, GAMING_KILL_WEIGHT_ADJ, 0);

            candidates.sort(new ScoreComparator());

            int killed = 0;
            for (AxProcessScoreRecord rec : candidates) {
                Process.killProcess(rec.pid);
                totalFreedKb += rec.rss;
                killed++;
                if (killed >= maxCount) {
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return totalFreedKb;
    }

    private boolean isGamingProtectedPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return true;
        }
        if (pkg.equals(this.mCurForegroundPackage) || pkg.equals(this.mActiveGamePackage)) {
            return true;
        }
        if (CORE_PROTECTED_PACKAGES.contains(pkg) || sScreenOnKillWhitelist.contains(pkg)) {
            return true;
        }
        return pkg.contains("dialer") || pkg.contains("telephony");
    }

    public boolean isLauncherPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        if (KNOWN_LAUNCHER_PACKAGES.contains(pkg)) {
            return true;
        }
        return pkg.toLowerCase(Locale.ROOT).contains("launcher");
    }

    @Override
    public void onStartProcess(String processName, ApplicationInfo info, boolean isTop, String hostingType) {
        AxUsageManager.getInstance().updateLaunchTime(processName);
        if (isTop && "next-top-activity".equals(hostingType)
                && SystemProperties.getBoolean(PROP_PRELOAD_FORCE_READ, true)
                && this.mMemLoaderHandler != null) {
            Bundle bundle = new Bundle();
            bundle.putString("processName", processName);
            bundle.putParcelable("appInfo", info);
            Message msg = this.mMemLoaderHandler.obtainMessage(MSG_MEM_PRELOAD_APP_FILES);
            msg.setData(bundle);
            this.mMemLoaderHandler.sendMessage(msg);
        }
    }

    @Override
    public void releaseMemoryAtScreenOn() {
        if (!this.mEnableReleaseMemoryScreenOn) {
            return;
        }

        long now = System.currentTimeMillis();
        if (this.mLastReleaseMemoryScreenOnTime == 0 || (now - this.mLastReleaseMemoryScreenOnTime) > this.mReleaseMemoryScreenOnDurationMs) {
            this.mMainHandler.sendMessage(this.mMainHandler.obtainMessage(MSG_RELEASE_MEMORY_SCREEN_ON));
            this.mLastReleaseMemoryScreenOnTime = now;
        }
    }

    @Override
    public void setForkProcAdj(ProcessRecordInternal pri) {
        if (pri.getCurAdj() < 900) {
            if (pri.getCurAdj() != this.mForkProcAdj) {
                pri.isForkedFromHighUsed = false;
            }
            return;
        }
        pri.setAdjType("pre_fork");
        pri.setCurAdj(this.mForkProcAdj);
    }

    @Override
    public void setHighPressureScene(String pkgName) {
        if (pkgName != null) {
            if (isCameraPackage(pkgName) || isGamePackage(pkgName)) {
                this.mIsHighPressureScene = true;
            } else if (isLauncherPackage(pkgName)) {
                this.mIsHighPressureScene = false;
            }
        }
    }

    @Override
    public void setGamingMode(boolean active, String gamePackageName) {
        if (active) {
            this.mIsHighPressureScene = true;
            this.mActiveGamePackage = gamePackageName != null ? gamePackageName : "";
            this.mCurForegroundPackage = this.mActiveGamePackage;
            if (this.mEnableTuneSwappiness) {
                SystemProperties.set(PROP_SWAPPINESS, Integer.toString(SWAPPINESS_GAMING));
                this.mCurSwappiness = SWAPPINESS_GAMING;
            }
            setExtraFreeFactor(EXTRA_FREE_FACTOR_GAMING);
            ProcessList.setCameraTop(1);
            AxProcessManager.getInstance().updateTopApp(this.mCurForegroundPackage);
        } else {
            this.mIsHighPressureScene = false;
            this.mActiveGamePackage = "";
            if (this.mEnableTuneSwappiness) {
                SystemProperties.set(PROP_SWAPPINESS, Integer.toString(this.mDefaultSwappiness));
                this.mCurSwappiness = this.mDefaultSwappiness;
            }
            setExtraFreeFactor(EXTRA_FREE_FACTOR_DEFAULT);
            ProcessList.setCameraTop(0);
            AxProcessManager.getInstance().triggerFlushDelayedServices();
        }
    }

    @Override
    public void setOptAdj(ProcessRecordInternal pri) {
        pri.setCurAdj(this.mHighUsedAdj);
    }

    @Override
    public void tuneMemoryParam(String packageName) {
        if (this.mMainHandler != null) {
            Bundle bundle = new Bundle();
            bundle.putString(BUNDLE_KEY_PACKAGE_NAME, packageName);
            Message msg = this.mMainHandler.obtainMessage(MSG_TUNE_MEMORY_PARAM);
            msg.setData(bundle);
            this.mMainHandler.sendMessage(msg);
        }
    }

    @Override
    public boolean isEnableOptHighUsed() {
        return this.mEnableOptHighUsed;
    }
}
