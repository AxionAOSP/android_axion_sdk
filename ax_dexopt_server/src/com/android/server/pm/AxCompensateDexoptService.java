package com.android.server.pm;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.BatteryManagerInternal;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.server.art.model.DexoptResult;
import com.android.server.LocalServices;
import com.android.server.pm.AxArtManagerLocalHelper;
import com.android.server.pm.AxCompensateDexoptJobService;
import com.android.server.pm.AxDexoptManagerImpl;
import com.android.server.pm.PackageManagerService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AxCompensateDexoptService {

    private static final String TAG = "AxCompensateDexoptService";

    private static final int MSG_START_DEXOPT = 1;
    private static final boolean DEBUG = Build.IS_DEBUGGABLE || SystemProperties.getBoolean("persist.sys.ax.dexopt.debug", false);

    private static final String PROP_COMPENSATE_DEXOPT_ENABLE = "persist.sys.ax.compensate_dexopt_enable";
    private static final String PROP_COMPENSATE_DEXOPT_PERIOD = "persist.sys.ax.compensate_dexopt_period";
    private static final String PROP_COMPENSATE_DEXOPT_PKGNUM_LIMIT = "persist.sys.ax.compensate_dexopt_pkgnum_limit";
    private static final String PROP_COMPENSATE_DEXOPT_BATTERY_LEVEL = "persist.sys.ax.compensate_dexopt_battery_level";

    public static final int COMPENSATE_DEXOPT_JOB_ID = 8008;
    public static final int DEFAULT_PERIOD_MINUTES = 240;
    public static final int DEFAULT_PKGNUM_LIMIT = 5;
    public static final int DEFAULT_BATTERY_LEVEL = 20;

    private static final int DELAY_SHORT_SECONDS = 60;
    private static final int DELAY_LONG_SECONDS = 300;

    private static final AxCompensateDexoptService sInstance = new AxCompensateDexoptService();

    private final Object mLock = new Object();
    private Context mContext;
    private PackageManagerService mPms;
    private JobService mInnerJob;
    private final ServiceHandler mHandler;
    private PowerManager mPowerManager;
    private BatteryManagerInternal mBatteryManagerInternal;
    private long mLastDexoptTime;
    private boolean mDisabled;

    private long mCompensateDexoptPeriod;
    private int mCompensateDexoptPkgNumLimit;
    private int mCompensateDexoptBatteryLevel;

    private AxCompensateDexoptService() {
        this.mHandler = new ServiceHandler(Looper.getMainLooper());
        this.mCompensateDexoptPeriod = DEFAULT_PERIOD_MINUTES * 60 * 1000L;
        this.mCompensateDexoptPkgNumLimit = DEFAULT_PKGNUM_LIMIT;
        this.mCompensateDexoptBatteryLevel = DEFAULT_BATTERY_LEVEL;
    }

    public static AxCompensateDexoptService getInstance() {
        return sInstance;
    }

    public void init(Context context, PackageManagerService pms) {
        this.mContext = context;
        this.mPms = pms;
        this.mBatteryManagerInternal = LocalServices.getService(BatteryManagerInternal.class);
        this.mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    public void setDisable(boolean disable) {
        this.mDisabled = disable;
    }

    public boolean onStartJob(JobService jobService, JobParameters params) {
        this.mInnerJob = jobService;
        if (params.getJobId() != COMPENSATE_DEXOPT_JOB_ID) {
            return false;
        }
        if (this.mDisabled) {
            jobService.jobFinished(params, false);
            return true;
        }
        if (isRunningBgDexopt()) {
            jobService.jobFinished(params, false);
            return false;
        }
        scheduleDexoptMessage(params);
        return true;
    }

    public boolean onStopJob(JobParameters params) {
        if (DEBUG) {
            Slog.d(TAG, "onStopJob: " + params.getJobId());
        }
        this.mInnerJob = null;
        return true;
    }

    public void scheduleCompensateDexoptJob() {
        if (SystemProperties.getBoolean("pm.dexopt.disable_bg_dexopt", false)) {
            return;
        }
        if (isJobPending()) {
            return;
        }
        if (!SystemProperties.getBoolean(PROP_COMPENSATE_DEXOPT_ENABLE, true)) {
            return;
        }
        loadProperties();
        JobScheduler scheduler = (JobScheduler) this.mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        JobInfo jobInfo = new JobInfo.Builder(COMPENSATE_DEXOPT_JOB_ID, new ComponentName(this.mContext, AxCompensateDexoptJobService.class))
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setPeriodic(this.mCompensateDexoptPeriod)
                .build();
        scheduler.schedule(jobInfo);
    }

    public void cancelCompensateDexoptJob() {
        JobScheduler scheduler = (JobScheduler) this.mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        scheduler.cancel(COMPENSATE_DEXOPT_JOB_ID);
    }

    public void handleOnlineConfigUpdate(Message msg) {
        Bundle bundle = msg.getData();
        if (bundle == null) {
            return;
        }
        boolean enable = bundle.getBoolean("enable", true);
        int period = Math.max(15, Math.min(DEFAULT_PERIOD_MINUTES, bundle.getInt("period", DEFAULT_PERIOD_MINUTES)));
        int pkgNum = Math.max(0, Math.min(10, bundle.getInt("pkgNum", DEFAULT_PKGNUM_LIMIT)));
        int batteryLevel = Math.max(DEFAULT_BATTERY_LEVEL, Math.min(98, bundle.getInt("batteryLevel", DEFAULT_BATTERY_LEVEL)));

        SystemProperties.set(PROP_COMPENSATE_DEXOPT_ENABLE, String.valueOf(enable));
        SystemProperties.set(PROP_COMPENSATE_DEXOPT_PERIOD, String.valueOf(period));
        SystemProperties.set(PROP_COMPENSATE_DEXOPT_PKGNUM_LIMIT, String.valueOf(pkgNum));
        SystemProperties.set(PROP_COMPENSATE_DEXOPT_BATTERY_LEVEL, String.valueOf(batteryLevel));

        this.mCompensateDexoptPeriod = period * 60 * 1000L;
        this.mCompensateDexoptPkgNumLimit = pkgNum;
        this.mCompensateDexoptBatteryLevel = batteryLevel;

        cancelCompensateDexoptJob();
        if (enable) {
            scheduleCompensateDexoptJob();
        }
    }

    private void loadProperties() {
        this.mCompensateDexoptPeriod = SystemProperties.getInt(PROP_COMPENSATE_DEXOPT_PERIOD, DEFAULT_PERIOD_MINUTES) * 60 * 1000L;
        this.mCompensateDexoptPkgNumLimit = SystemProperties.getInt(PROP_COMPENSATE_DEXOPT_PKGNUM_LIMIT, DEFAULT_PKGNUM_LIMIT);
        this.mCompensateDexoptBatteryLevel = SystemProperties.getInt(PROP_COMPENSATE_DEXOPT_BATTERY_LEVEL, DEFAULT_BATTERY_LEVEL);
    }

    private void scheduleDexoptMessage(JobParameters params) {
        long now = System.currentTimeMillis();
        long diff = now - this.mLastDexoptTime;
        long delay = (diff < 300000L) ? DELAY_LONG_SECONDS * 1000L : DELAY_SHORT_SECONDS * 1000L;
        Message msg = this.mHandler.obtainMessage(MSG_START_DEXOPT, params);
        this.mHandler.sendMessageDelayed(msg, delay);
    }

    private boolean isJobPending() {
        JobScheduler scheduler = (JobScheduler) this.mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return false;
        }
        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == COMPENSATE_DEXOPT_JOB_ID) {
                return true;
            }
        }
        return false;
    }

    private boolean isScreenOn() {
        if (this.mPowerManager == null) {
            return true;
        }
        return this.mPowerManager.isInteractive();
    }

    private boolean isPowered() {
        if (this.mBatteryManagerInternal == null) {
            return false;
        }
        return this.mBatteryManagerInternal.isPowered(15);
    }

    private boolean isBatteryLow() {
        if (this.mBatteryManagerInternal == null) {
            return true;
        }
        return this.mBatteryManagerInternal.getBatteryLevel() < this.mCompensateDexoptBatteryLevel;
    }

    private boolean isRunningBgDexopt() {
        return AxDexoptManagerImpl.getInstance().isRunningBgDexopt();
    }

    private boolean isThermalThrottled() {
        return AxDexoptManagerImpl.getInstance().getThermalStatus() > -1;
    }

    private boolean isPhoneInCall() {
        return AxDexoptManagerImpl.getInstance().isPhoneInCall();
    }

    private boolean shouldStopDexopt() {
        if (isScreenOn()) {
            return true;
        }
        if (!isPowered()) {
            return true;
        }
        if (this.mPms != null && this.mPms.isStorageLow()) {
            return true;
        }
        if (isBatteryLow()) {
            return true;
        }
        if (isRunningBgDexopt()) {
            return true;
        }
        if (isThermalThrottled()) {
            return true;
        }
        return isPhoneInCall();
    }

    private int dexoptPackage(String packageName) {
        DexoptResult result = AxArtManagerLocalHelper.performDexOptimization(packageName, "compensate_dexopt");
        if (result == null) {
            return 30;
        }
        return result.getFinalStatus();
    }

    private void handleCompensateDexopt(JobParameters params) {
        if (shouldStopDexopt()) {
            return;
        }
        this.mLastDexoptTime = System.currentTimeMillis();
        List<String> pkgs = getOrderedOptimizablePackages();
        int compiledCount = 0;
        for (String pkg : pkgs) {
            if (shouldStopDexopt()) {
                break;
            }
            if (compiledCount >= this.mCompensateDexoptPkgNumLimit) {
                break;
            }
            int status = dexoptPackage(pkg);
            if (status == 20) {
                compiledCount++;
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "handleCompensateDexopt finished, compiled: " + compiledCount);
        }
    }

    private List<String> getOrderedOptimizablePackages() {
        List<String> candidatePkgs = AxDexoptManagerImpl.getInstance().getPkgsToBeOptimized(this.mPms.snapshotComputer());
        if (candidatePkgs == null || candidatePkgs.isEmpty()) {
            return candidatePkgs;
        }
        long now = System.currentTimeMillis();
        List<UsageStats> stats = queryUsageStats(now - 86400000L, now);
        if (stats != null) {
            Iterator<UsageStats> iterator = stats.iterator();
            while (iterator.hasNext()) {
                UsageStats usageStats = iterator.next();
                if (!candidatePkgs.contains(usageStats.getPackageName())) {
                    iterator.remove();
                }
            }
            Collections.sort(stats, new AppLaunchCountComparator());
        }
        List<String> orderedPkgs = new ArrayList<>();
        if (stats != null) {
            int count = 0;
            for (UsageStats usageStats : stats) {
                if (count >= 5) {
                    break;
                }
                orderedPkgs.add(usageStats.getPackageName());
                count++;
            }
            Collections.sort(stats, new TotalForegroundTimeComparator());
            for (UsageStats usageStats : stats) {
                if (!orderedPkgs.contains(usageStats.getPackageName())) {
                    orderedPkgs.add(usageStats.getPackageName());
                }
            }
        }
        for (String pkg : candidatePkgs) {
            if (!orderedPkgs.contains(pkg)) {
                orderedPkgs.add(pkg);
            }
        }
        return orderedPkgs;
    }

    private List<UsageStats> queryUsageStats(long start, long end) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) this.mContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return Collections.emptyList();
        }
        Map<String, UsageStats> map = usageStatsManager.queryAndAggregateUsageStats(start, end);
        if (map == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(map.values());
    }

    private static class AppLaunchCountComparator implements Comparator<UsageStats> {
        @Override
        public int compare(UsageStats u1, UsageStats u2) {
            return Integer.compare(u2.getAppLaunchCount(), u1.getAppLaunchCount());
        }
    }

    private static class TotalForegroundTimeComparator implements Comparator<UsageStats> {
        @Override
        public int compare(UsageStats u1, UsageStats u2) {
            return Long.compare(u2.getTotalTimeInForeground(), u1.getTotalTimeInForeground());
        }
    }

    private class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_START_DEXOPT) {
                JobParameters params = (JobParameters) msg.obj;
                new Thread(new WorkerRunnable(params), "AxCompensateDexopt").start();
            }
        }
    }

    private class WorkerRunnable implements Runnable {
        private final JobParameters mParams;

        WorkerRunnable(JobParameters params) {
            this.mParams = params;
        }

        @Override
        public void run() {
            handleCompensateDexopt(this.mParams);
            synchronized (mLock) {
                if (mInnerJob != null) {
                    mInnerJob.jobFinished(this.mParams, false);
                }
            }
        }
    }
}
