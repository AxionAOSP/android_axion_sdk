package com.android.server.pm;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.PackageManagerInternal.PackageListObserver;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.IThermalService;
import android.os.IThermalStatusListener;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.Temperature;
import android.os.UserHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;
import com.android.internal.R;
import com.android.internal.dexopt.IAxUserStartDexoptStatusHandler;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.pm.parsing.pkg.AndroidPackageInternal;
import com.android.server.apphibernation.AppHibernationManagerInternal;
import com.android.server.art.DexUseManagerLocal;
import com.android.server.art.model.ArtManagedFileStats;
import com.android.server.art.model.BatchDexoptParams;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.model.DexoptResult.DexContainerFileDexoptResult;
import com.android.server.art.model.DexoptStatus;
import com.android.server.LocalServices;
import com.android.server.pm.AxArtManagerLocalHelper;
import com.android.server.pm.AxCompensateDexoptService;
import com.android.server.pm.Computer;
import com.android.server.pm.DexOptHelper;
import com.android.server.pm.IAxDexoptManager;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.PackageManagerLocal.FilteredSnapshot;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.PackageManagerServiceUtils;
import com.android.server.pm.PackageSetting;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageStateInternal;
import dalvik.system.DexFile;
import dalvik.system.VMRuntime;
import java.io.File;
import java.io.PrintWriter;
import java.lang.IllegalAccessException;
import java.lang.InterruptedException;
import java.lang.NoSuchMethodException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AxDexoptManagerImpl implements IAxDexoptManager {

    private static final String KEY_COMPENSATE_DEXOPT_PERIOD = "compensate_dexopt_period";
    private static final String KEY_COMPENSATE_DEXOPT_PKGNUM_LIMIT = "compensate_dexopt_pkgnum_limit";
    private static final String KEY_COMPENSATE_DEXOPT_BATTERY_LEVEL = "compensate_dexopt_battery_level";
    private static final String KEY_FIX_DEXOPT_ENABLE = "fix_dexopt_enable";
    private static final String KEY_START_SPEED_PROFILE_ENABLE = "start_speed_profile_enable";
    private static final String KEY_START_SPEED_PROFILE_WHITELIST = "start_speed_profile_whitelist";
    private static final String PROP_LAST_BG_DEXOPT = "persist.sys.ax.last_bg_dexopt";
    private static final String PROP_LAST_US_DEXOPT = "persist.sys.ax.last_us_dexopt";
    private static final int MSG_CONFIG_UPDATE = 0;
    private static final String AXION_PARTS_PACKAGE = "com.android.axion.axionparts";
    private static final String ACTION_APP_OPTIMIZATION = "com.android.axion.axionparts.ACTION_APP_OPTIMIZATION";
    private static final int NOTIFICATION_ID_POST_OTA = 8000;
    private static final int NOTIFICATION_ID_USER_DEXOPT = 8009;

    private static HandlerThread sDexoptFixThread = null;
    private static volatile Handler sDexoptFixHandler = null;

    static final String TAG = "AxDexoptManagerImpl";
    static final String COMPILER_FILTER_VERIFY = "verify";
    static final String COMPILER_FILTER_SPEED_PROFILE = "speed-profile";
    static final String COMPILER_FILTER_RUN_FROM_APK = "run-from-apk";
    static final String COMPILER_FILTER_RUN_FROM_APK_FALLBACK = "run-from-apk-fallback";
    static final String COMPILER_FILTER_VDEX = "vdex";
    static final String REASON_COMPENSATE_DEXOPT = "compensate_dexopt";
    static final String REASON_USER_DEXOPT = "user_dexopt";
    static final String REASON_FIX_DEXOPT = "fix_dexopt";
    static final String REASON_FIRST_STARTUP = "first_startup";

    public static int THERMAL_STATUS_THRESHOLD = -1;
    private static final String KEY_COMPENSATE_DEXOPT_ENABLE = "compensate_dexopt_enable";

    private IThermalService mThermalService;
    private Context mContext;
    private PackageManagerService mPms;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private AxUserStartDexoptServiceWrapper mUserStartDexoptServiceWrapper;
    private PackageList mPackageList;
    private final PackageListObserver mPackageListObserver = new PackageListObserver() {
        @Override
        public void onPackageAdded(String packageName, int uid) {
            onPackageListChanged();
        }

        @Override
        public void onPackageChanged(String packageName, int uid) {
            onPackageListChanged();
        }

        @Override
        public void onPackageRemoved(String packageName, int uid) {
            onPackageListChanged();
        }
    };

    private static final boolean DEBUG = SystemProperties.getBoolean("persist.sys.ax.dexopt.debug", false);
    private static AxDexoptManagerImpl sInstance = null;
    private static boolean sIsRunningBgDexopt = false;
    private static final String PROP_FIX_DEXOPT_ENABLE = "persist.sys.ax.fix_dexopt_enable";
    private static boolean sFixDexoptEnable = SystemProperties.getBoolean(PROP_FIX_DEXOPT_ENABLE, true);
    private static ConcurrentHashMap<String, String> sDexoptStatusMap = new ConcurrentHashMap<>();
    private static final Object sLock = new Object();
    private static final String PROP_START_SPEED_PROFILE_ENABLE = "persist.sys.ax.start_speed_profile_enable";
    private static boolean sStartSpeedProfileEnable = SystemProperties.getBoolean(PROP_START_SPEED_PROFILE_ENABLE, true);
    private static List<String> sStartSpeedProfileWhitelist = new ArrayList<>();

    private volatile int mThermalStatus = THERMAL_STATUS_THRESHOLD;
    private final IThermalStatusListener mThermalStatusListener = new IThermalStatusListener.Stub() {
        @Override
        public void onStatusChange(int status) {
            mThermalStatus = normalizeThermalStatus(status);
        }
    };

    class BootCompleteReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mContext.unregisterReceiver(this);
            AxCompensateDexoptService.getInstance().scheduleCompensateDexoptJob();
            if (!mPms.isDeviceUpgrading()) {
                return;
            }
            mUserStartDexoptServiceWrapper.init(null);
            showPostOtaNotification(mContext);
        }
    }

    class ManagerHandler extends Handler {
        ManagerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what != MSG_CONFIG_UPDATE) {
                return;
            }
            handleOnlineConfigMessage(message);
        }
    }

    public AxDexoptManagerImpl() {
        sInstance = this;
        this.mUserStartDexoptServiceWrapper = new AxUserStartDexoptServiceWrapper();
        Slog.d(TAG, TAG);
    }

    public static AxDexoptManagerImpl getInstance() {
        synchronized (AxDexoptManagerImpl.class) {
            if (sInstance == null) {
                sInstance = new AxDexoptManagerImpl();
            }
            return sInstance;
        }
    }

    private void handleOnlineConfigMessage(Message message) {
        AxCompensateDexoptService.getInstance().handleOnlineConfigUpdate(message);
    }

    public static void performFirstStartupDexopt(String packageName, boolean isFix) {
        Slog.i(TAG, "first startup dexopt start, " + packageName);
        String reason = isFix ? REASON_FIX_DEXOPT : REASON_FIRST_STARTUP;
        DexoptResult result = AxArtManagerLocalHelper.performDexOptimization(packageName, reason);
        if (result == null) {
            return;
        }
        Slog.i(TAG, "first startup compile:" + packageName + ", finalStatus:" + result.getFinalStatus());
        if (result.getFinalStatus() != 20) {
            sDexoptStatusMap.remove(packageName);
        }
    }

    private void initHandlerThread() {
        HandlerThread handlerThread = new HandlerThread("AxionDexoptManager");
        this.mHandlerThread = handlerThread;
        handlerThread.start();
        this.mHandler = new ManagerHandler(this.mHandlerThread.getLooper());
    }

    private void registerThermalListener() {
        if (this.mThermalService == null) {
            return;
        }
        try {
            this.mThermalStatus = normalizeThermalStatus(this.mThermalService.getCurrentThermalStatus());
            if (!this.mThermalService.registerThermalStatusListener(this.mThermalStatusListener)) {
                Slog.w(TAG, "Failed to register thermal status listener");
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register thermal status listener", e);
        }
    }

    private static int normalizeThermalStatus(int status) {
        return status > Temperature.THROTTLING_NONE ? status : THERMAL_STATUS_THRESHOLD;
    }

    private boolean isPrimaryDexUsedByOthers(String packageName) {
        PackageManagerLocal pml = PackageManagerServiceUtils.getPackageManagerLocal();
        if (pml == null) {
            return false;
        }
        try (FilteredSnapshot snapshot = pml.withFilteredSnapshot()) {
            PackageState packageState = snapshot.getPackageState(packageName);
            AndroidPackage androidPackage = (AndroidPackage) this.mPms.mPackages.get(packageName);
            if (androidPackage == null) {
                Slog.d(TAG, "androidPackage is null " + packageName);
                return true;
            }
            String baseApkPath = androidPackage.getBaseApkPath();
            if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(baseApkPath)) {
                return false;
            }
            return isPrimaryDexUsed(packageName, baseApkPath) || isSharedLibrary(packageState, true);
        }
    }

    private static boolean isSharedLibrary(PackageState packageState, boolean checkResourceOverlay) {
        if (packageState == null) {
            return false;
        }
        AndroidPackage androidPackage = packageState.getAndroidPackage();
        if (androidPackage == null) {
            return false;
        }
        if (androidPackage.getLibraryNames().isEmpty() && TextUtils.isEmpty(androidPackage.getSdkLibraryName()) && TextUtils.isEmpty(androidPackage.getStaticSharedLibraryName())) {
            return !checkResourceOverlay && androidPackage.isResourceOverlay();
        }
        return true;
    }

    private static boolean isPrimaryDexUsed(String packageName, String baseApkPath) {
        try {
            DexUseManagerLocal dexUseManagerLocal = DexOptHelper.getDexUseManagerLocal();
            if (dexUseManagerLocal == null) {
                return false;
            }
            Object obj = dexUseManagerLocal.getClass().getMethod("isPrimaryDexUsedByOtherApps", String.class, String.class).invoke(dexUseManagerLocal, packageName, baseApkPath);
            if (obj != null) {
                return Boolean.parseBoolean(obj.toString());
            }
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            Slog.e(TAG, "isPrimaryDexUsed exception: " + e);
        }
        return false;
    }

    private void filterOptimizablePackage(ArrayList<String> list, PackageStateInternal packageStateInternal) {
        AndroidPackageInternal pkg = packageStateInternal.getPkg();
        if (pkg == null || !isDefaultUserApp(pkg)) {
            return;
        }
        list.add(packageStateInternal.getPackageName());
    }

    private void checkAndTriggerStartupDexopt(String cpuAbi, String baseApkPath, String packageName, boolean isWhitelisted) {
        DexFile.OptimizationInfo optInfo;
        try {
            optInfo = DexFile.getDexFileOptimizationInfo(baseApkPath, VMRuntime.getInstructionSet(cpuAbi));
        } catch (Exception e) {
            Slog.e(TAG, "get dexopt status exception," + e.toString());
            optInfo = null;
        }
        if (optInfo == null) {
            Slog.i(TAG, "optInfo is null of " + packageName);
            return;
        }
        Slog.i(TAG, "[" + packageName + "]dex status:" + optInfo.getStatus() + ",baseApkPath:" + baseApkPath);
        boolean needFix = COMPILER_FILTER_RUN_FROM_APK.equals(optInfo.getStatus());
        boolean needStartup = isWhitelisted && sStartSpeedProfileEnable && COMPILER_FILTER_VERIFY.equals(optInfo.getStatus());
        Slog.i(TAG, "needFixDexoptStatus:" + needFix + ", needDexoptforFirstStart:" + needStartup);
        if (!needFix && !needStartup) {
            return;
        }
        if (needStartup && isPrimaryDexUsedByOthers(packageName)) {
            return;
        }
        Slog.i(TAG, "begin to do dexopt :" + packageName);
        scheduleDexoptAsync(packageName, needFix);
    }

    private void showPostOtaNotification(Context context) {
        Slog.d(TAG, "start postOTANotification");
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }
        PendingIntent activity = createAppOptimizationPendingIntent(context);
        notificationManager.notify(NOTIFICATION_ID_POST_OTA, new Notification.Builder(context, SystemNotificationChannels.SYSTEM_DEXOPT_OTA)
                .setSmallIcon(17301633)
                .setContentTitle("App optimisation")
                .setContentText("Optimise apps to improve application launch performance")
                .setContentIntent(activity)
                .setColor(context.getColor(17170460))
                .setStyle(new Notification.BigTextStyle().bigText("Optimise apps to improve application launch performance"))
                .setAutoCancel(true)
                .build());
    }

    private PendingIntent createAppOptimizationPendingIntent(Context context) {
        Intent intent = new Intent(ACTION_APP_OPTIMIZATION);
        intent.setClassName(AXION_PARTS_PACKAGE, "com.android.axion.axionparts.DashboardActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, NOTIFICATION_ID_USER_DEXOPT, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showUserDexoptProgress(int current, int total) {
        Context context = this.mContext;
        if (context == null || total <= 0) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        int safeCurrent = Math.max(0, Math.min(current, total));
        if (safeCurrent == 0) {
            notificationManager.cancel(NOTIFICATION_ID_POST_OTA);
        }
        Notification notification = new Notification.Builder(context, SystemNotificationChannels.SYSTEM_DEXOPT)
                .setSmallIcon(17301633)
                .setContentTitle(context.getString(R.string.dexopt_notification_title))
                .setContentText(context.getString(R.string.dexopt_notification_progress, safeCurrent, total))
                .setProgress(total, safeCurrent, false)
                .setContentIntent(createAppOptimizationPendingIntent(context))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_USER_DEXOPT, notification);
    }

    private void showUserDexoptCompleted(int total) {
        Context context = this.mContext;
        if (context == null) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        int safeTotal = Math.max(1, total);
        Notification notification = new Notification.Builder(context, SystemNotificationChannels.SYSTEM_DEXOPT)
                .setSmallIcon(17301633)
                .setContentTitle(context.getString(R.string.dexopt_notification_title))
                .setContentText(context.getString(R.string.dexopt_notification_complete))
                .setProgress(safeTotal, safeTotal, false)
                .setContentIntent(createAppOptimizationPendingIntent(context))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_USER_DEXOPT, notification);
    }

    private void showUserDexoptError() {
        Context context = this.mContext;
        if (context == null) {
            return;
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        Notification notification = new Notification.Builder(context, SystemNotificationChannels.SYSTEM_DEXOPT)
                .setSmallIcon(17301633)
                .setContentTitle(context.getString(R.string.dexopt_notification_title))
                .setContentText(context.getString(R.string.dexopt_notification_error))
                .setContentIntent(createAppOptimizationPendingIntent(context))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();
        notificationManager.notify(NOTIFICATION_ID_USER_DEXOPT, notification);
    }

    private static void executeStartupDexopt(final String packageName, final boolean isFix) {
        getDexoptFixHandler().post(new Runnable() {
            @Override
            public void run() {
                performFirstStartupDexopt(packageName, isFix);
            }
        });
    }

    private void setLastBgDexoptStartedTime(long time) {
        SystemProperties.set(PROP_LAST_BG_DEXOPT, Long.toString(time));
    }

    public void parseCompensateDexoptConfig(JSONObject jsonObject) {
        boolean enable = jsonObject.optBoolean(KEY_COMPENSATE_DEXOPT_ENABLE, true);
        int period = jsonObject.optInt(KEY_COMPENSATE_DEXOPT_PERIOD, AxCompensateDexoptService.DEFAULT_PERIOD_MINUTES);
        int pkgNum = jsonObject.optInt(KEY_COMPENSATE_DEXOPT_PKGNUM_LIMIT, 5);
        int batteryLevel = jsonObject.optInt(KEY_COMPENSATE_DEXOPT_BATTERY_LEVEL, 20);
        Bundle bundle = new Bundle();
        bundle.putBoolean("enable", enable);
        bundle.putInt("period", period);
        bundle.putInt("pkgNum", pkgNum);
        bundle.putInt("batteryLevel", batteryLevel);
        Message msg = this.mHandler.obtainMessage(MSG_CONFIG_UPDATE);
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
    }

    public static void parseStartSpeedProfileConfig(JSONObject jsonObject) {
        if (jsonObject.has(KEY_START_SPEED_PROFILE_ENABLE)) {
            boolean enable = jsonObject.optBoolean(KEY_START_SPEED_PROFILE_ENABLE, sStartSpeedProfileEnable);
            sStartSpeedProfileEnable = enable;
            SystemProperties.set(PROP_START_SPEED_PROFILE_ENABLE, Boolean.toString(enable));
            debugLog("update sStartSpeedProfileEn:" + sStartSpeedProfileEnable);
        }
        if (!jsonObject.has(KEY_START_SPEED_PROFILE_WHITELIST)) {
            return;
        }
        sStartSpeedProfileWhitelist.clear();
        try {
            JSONArray array = jsonObject.optJSONArray(KEY_START_SPEED_PROFILE_WHITELIST);
            if (array == null || array.length() <= 0) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                String pkg = array.get(i).toString();
                if (pkg != null && !pkg.trim().isEmpty()) {
                    sStartSpeedProfileWhitelist.add(pkg);
                    sDexoptStatusMap.remove(pkg);
                }
            }
            debugLog("update sStartSpeedProfileWhiteList: " + sStartSpeedProfileWhitelist);
        } catch (JSONException e) {
            Slog.e(TAG, "Failed to updateFirstStartDexoptConfig: " + e);
        }
    }

    public static void parseFixDexoptConfig(JSONObject jsonObject) {
        if (jsonObject.has(KEY_FIX_DEXOPT_ENABLE)) {
            boolean enable = jsonObject.optBoolean(KEY_FIX_DEXOPT_ENABLE, sFixDexoptEnable);
            sFixDexoptEnable = enable;
            SystemProperties.set(PROP_FIX_DEXOPT_ENABLE, Boolean.toString(enable));
        }
    }

    public void handleOnlineConfigUpdate(JSONArray jsonArray) {
        if (jsonArray == null) {
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "dexopt online config update: " + jsonArray.toString());
        }
        try {
            JSONObject obj = jsonArray.optJSONObject(0);
            if (obj != null) {
                parseCompensateDexoptConfig(obj);
                parseFixDexoptConfig(obj);
                parseStartSpeedProfileConfig(obj);
            }
        } catch (Exception e) {
            Slog.e(TAG, "online config error: " + e);
        }
    }

    private static void debugLog(String str) {
        if (DEBUG) {
            Slog.d(TAG, str);
        }
    }

    private void dumpStartSpeedProfile(PrintWriter printWriter, String[] args) {
        if (args.length == 1) {
            printWriter.println("");
            printWriter.println("sStartSpeedProfileEn:" + sStartSpeedProfileEnable);
            printWriter.println("sStartSpeedProfileWhiteList:" + sStartSpeedProfileWhitelist);
            return;
        }
        if (args.length >= 3) {
            if (!DEBUG) {
                printWriter.println("No permission!");
                return;
            }
            String cmd = args[1];
            String action = args[2];
            if ("first-start".equals(cmd)) {
                if ("clear".equals(action)) {
                    sStartSpeedProfileWhitelist.clear();
                }
                if (args.length >= 4) {
                    String pkg = args[3];
                    if ("rm".equals(action)) {
                        sStartSpeedProfileWhitelist.remove(pkg);
                    } else if ("add".equals(action)) {
                        sStartSpeedProfileWhitelist.add(pkg);
                    }
                }
            }
        }
    }

    private void dumpFixDexopt(PrintWriter printWriter, String[] args) {
        if (args.length == 1) {
            printWriter.println("");
            printWriter.println("FIX_DEXOPT_EN:" + sFixDexoptEnable);
            printWriter.println("sNoNeedFixDexoptMap:");
            for (Map.Entry<String, String> entry : sDexoptStatusMap.entrySet()) {
                printWriter.println("name:" + entry.getKey() + ", path: " + entry.getValue());
            }
            return;
        }
        if (args.length >= 3) {
            if (!DEBUG) {
                printWriter.println("No permission!");
                return;
            }
            String cmd = args[1];
            String action = args[2];
            if ("fix".equals(cmd)) {
                if ("clear".equals(action)) {
                    sDexoptStatusMap.clear();
                }
                if ("rm".equals(action) && args.length >= 4) {
                    sDexoptStatusMap.remove(args[3]);
                }
            }
        }
    }

    public static DexoptParams getDexoptParams(String reason) {
        int flags = 65;
        String filter = COMPILER_FILTER_SPEED_PROFILE;
        if (REASON_FIRST_STARTUP.equals(reason) || REASON_USER_DEXOPT.equals(reason)) {
            filter = COMPILER_FILTER_SPEED_PROFILE;
        } else if (REASON_FIX_DEXOPT.equals(reason)) {
            filter = COMPILER_FILTER_VERIFY;
        } else if (REASON_COMPENSATE_DEXOPT.equals(reason)) {
            flags = 7;
            filter = COMPILER_FILTER_VERIFY;
        } else {
            filter = COMPILER_FILTER_VERIFY;
        }
        if (DEBUG) {
            Slog.d(TAG, "flags:" + flags + ", filter:" + filter + ", priority:40");
        }
        return new DexoptParams.Builder(reason, flags).setCompilerFilter(filter).setPriorityClass(40).setSplitName(null).build();
    }

    private static Handler getDexoptFixHandler() {
        if (sDexoptFixHandler == null) {
            synchronized (sLock) {
                if (sDexoptFixHandler == null) {
                    HandlerThread thread = new HandlerThread("dexopt_fix_thread");
                    sDexoptFixThread = thread;
                    thread.start();
                    sDexoptFixHandler = new Handler(sDexoptFixThread.getLooper());
                }
            }
        }
        return sDexoptFixHandler;
    }

    public long getLastBgDexoptTime() {
        return SystemProperties.getLong(PROP_LAST_BG_DEXOPT, 0L);
    }

    public List<String> getPkgsToBeOptimized(Computer computer) {
        final ArrayList<String> list = new ArrayList<>();
        this.mPms.forEachPackageState(computer, psi -> filterOptimizablePackage(list, psi));
        return list;
    }

    public void scheduleDexoptAsync(final String packageName, final boolean isFix) {
        getDexoptFixHandler().post(new Runnable() {
            @Override
            public void run() {
                executeStartupDexopt(packageName, isFix);
            }
        });
    }

    public boolean isPhoneInCall() {
        TelecomManager telecomManager = (TelecomManager) this.mContext.getSystemService(Context.TELECOM_SERVICE);
        return telecomManager != null && telecomManager.isInCall();
    }

    public boolean isRunningBgDexopt() {
        return sIsRunningBgDexopt;
    }

    void onBatchDexoptStart(FilteredSnapshot filteredSnapshot, String reason, List<String> list, BatchDexoptParams.Builder builder, CancellationSignal cancellationSignal) {
        if ("bg-dexopt".equals(reason)) {
            if (isPhoneInCall()) {
                Slog.i(TAG, "phone is in call, cancel bg-dexopt!");
                DexOptHelper.getArtManagerLocal().cancelBackgroundDexoptJob();
                return;
            }
            Slog.i(TAG, "bg-dexopt start...");
            sIsRunningBgDexopt = true;
            setLastBgDexoptStartedTime(System.currentTimeMillis());
        }
        if (DEBUG) {
            Slog.d(TAG, "onBatchDexoptStart, reason:" + reason + ", pkgSize:" + list.size() + ", sRunningBgDexopt:" + sIsRunningBgDexopt);
        }
    }

    public void onDexoptDone(DexoptResult dexoptResult) {
        String reason = dexoptResult.getReason();
        if ("bg-dexopt".equals(reason)) {
            Slog.i(TAG, "bg-dexopt done.");
            sIsRunningBgDexopt = false;
        }
        if (DEBUG) {
            Slog.d(TAG, "onDexoptDone, reason:" + reason + ", resultSize:" + dexoptResult.getPackageDexoptResults().size() + ", sRunningBgDexopt:" + sIsRunningBgDexopt);
        }
    }

    void onOverrideJobInfo(JobInfo.Builder builder) {
        Slog.d(TAG, "onOverrideBackgroundDexoptJobInfo");
    }

    @Override
    public void disableCompensateDexoptByCmd(boolean disable) {
        AxCompensateDexoptService.getInstance().setDisable(disable);
    }

    public boolean dump(PrintWriter printWriter, String[] args) {
        synchronized (this) {
            if (args.length == 1) {
                printWriter.println("===AxDexoptManagerImpl info===");
                printWriter.println("mCurTempLevel:" + this.mThermalStatus);
                printWriter.println("sRunningBgDexopt:" + sIsRunningBgDexopt);
                printWriter.println("lastBgDexoptStartedTime:" + getLastBgDexoptTime());
            }
            dumpFixDexopt(printWriter, args);
            dumpStartSpeedProfile(printWriter, args);
            this.mUserStartDexoptServiceWrapper.dump(printWriter, args);
        }
        return true;
    }

    public void init(Context context, PackageManagerService packageManagerService) {
        this.mContext = context;
        this.mPms = packageManagerService;
    }

    @Override
    public void notifyPackageUse(final String packageName, int reason) {
        if ((!sFixDexoptEnable && !sStartSpeedProfileEnable) || reason != 0) {
            return;
        }
        synchronized (this.mPms.mLock) {
            PackageSetting ps = this.mPms.mSettings.getPackageLPr(packageName);
            if (ps == null) {
                debugLog("pkgSetting null," + packageName);
                return;
            }
            final String primaryCpuAbi = ps.getPrimaryCpuAbi();
            if (primaryCpuAbi == null) {
                debugLog("primaryCpuAbi null," + packageName);
                return;
            }
            AndroidPackageInternal pkg = ps.getPkg();
            if (pkg == null || pkg.isVmSafeMode() || pkg.isDebuggable()) {
                debugLog("abnormal AndroidPackage state," + packageName);
                return;
            }
            if (ps.isSystem() && !ps.isUpdatedSystemApp()) {
                debugLog("skip, systemApp not updated, " + packageName);
                return;
            }
            final String baseApkPath = ((AndroidPackage) this.mPms.mPackages.get(packageName)).getBaseApkPath();
            String cachedPath = sDexoptStatusMap.get(packageName);
            if ((cachedPath != null && cachedPath.equalsIgnoreCase(baseApkPath)) || baseApkPath == null) {
                debugLog("already processed," + packageName);
                return;
            }
            final boolean isWhitelisted = sStartSpeedProfileWhitelist.contains(packageName);
            sDexoptStatusMap.put(packageName, baseApkPath);
            this.mPms.mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkAndTriggerStartupDexopt(primaryCpuAbi, baseApkPath, packageName, isWhitelisted);
                }
            }, 8000L);
        }
    }

    public boolean isDefaultUserApp(AndroidPackage androidPackage) {
        if ("android".equals(androidPackage.getPackageName()) || !androidPackage.isDeclaredHavingCode() || androidPackage.isApex()) {
            return false;
        }
        AppHibernationManagerInternal appHibernationManagerInternal = LocalServices.getService(AppHibernationManagerInternal.class);
        return appHibernationManagerInternal == null || !appHibernationManagerInternal.isHibernatingGlobally(androidPackage.getPackageName()) || !appHibernationManagerInternal.isOatArtifactDeletionEnabled();
    }

    public void systemReady() {
        AxCompensateDexoptService.getInstance().init(this.mContext, this.mPms);
        initHandlerThread();
        AxArtManagerLocalHelper.registerCallbacks();
        this.mThermalService = IThermalService.Stub.asInterface(ServiceManager.getService(Context.THERMAL_SERVICE));
        registerThermalListener();
        if (this.mPms.isDeviceUpgrading()) {
            setLastBgDexoptStartedTime(0L);
            this.mUserStartDexoptServiceWrapper.setLastUsDexoptStartedTime(0L);
        }
        this.mUserStartDexoptServiceWrapper.systemReady();
        registerPackageListObserver();
        this.mContext.registerReceiver(new BootCompleteReceiver(), new IntentFilter(Intent.ACTION_BOOT_COMPLETED));
        Slog.d(TAG, "systemReady");
    }

    private void registerPackageListObserver() {
        PackageManagerInternal packageManagerInternal = LocalServices.getService(PackageManagerInternal.class);
        if (packageManagerInternal == null) {
            Slog.w(TAG, "PackageManagerInternal unavailable");
            return;
        }
        this.mPackageList = packageManagerInternal.getPackageList(this.mPackageListObserver);
    }

    private void onPackageListChanged() {
        if (this.mUserStartDexoptServiceWrapper != null) {
            this.mUserStartDexoptServiceWrapper.onPackageListChanged();
        }
    }

    public void systemReady(Context context, PackageManagerService packageManagerService) {
        init(context, packageManagerService);
        systemReady();
    }

    public void connectUserDexopt(IAxUserStartDexoptStatusHandler handler) {
        this.mUserStartDexoptServiceWrapper.init(handler);
    }

    public void disconnectUserDexopt() {
        this.mUserStartDexoptServiceWrapper.deinit();
    }

    public List<String> getPackagesToBeOptimized() {
        return this.mUserStartDexoptServiceWrapper.getPkgsToBeOptimized();
    }

    public void performUserDexopt() {
        this.mUserStartDexoptServiceWrapper.performDexOptimization();
    }

    public int getThermalStatus() {
        return this.mThermalStatus;
    }

    private final class AxUserStartDexoptServiceWrapper {
        private static final int CUR_PROF_FILE_SIZE_MIN = 40960;
        private static final int PER_PKG_OPT_MIN_TIME_MS = 1000;
        public static final int SERVICE_STATE_NONE = 0;
        public static final int SERVICE_STATE_INIT = 1;
        public static final int SERVICE_STATE_RUNNING = 2;
        public static final int SERVICE_STATE_DONE_MANUAL = 3;
        public static final int SERVICE_STATE_DONE_AUTO = 4;
        private static final List<String> mValidFilters = List.of(COMPILER_FILTER_VERIFY, COMPILER_FILTER_SPEED_PROFILE, COMPILER_FILTER_RUN_FROM_APK, COMPILER_FILTER_RUN_FROM_APK_FALLBACK);

        private String curOptPkg;
        private int curState;
        private boolean isNeedToDelayByReConnect;
        private boolean mPackageListDirty;
        private boolean mPackageRefreshScheduled;
        private long lastAllPkgCheckTime;
        private IAxUserStartDexoptStatusHandler mCallbackHandlerRef;
        private final IBinder.DeathRecipient mDeathRecipient;
        private final HashMap<String, Pair<Long, Long>> mLastDexoptFailedPkgsMap;
        private final Object mLock;
        private final List<String> mPkgsToBeOpt;
        private FilteredSnapshot mSnapshotCache;

        private AxUserStartDexoptServiceWrapper() {
            this.mLock = new Object();
            this.isNeedToDelayByReConnect = false;
            this.mPackageListDirty = false;
            this.mPackageRefreshScheduled = false;
            this.lastAllPkgCheckTime = 0L;
            this.mPkgsToBeOpt = new ArrayList<>();
            this.mLastDexoptFailedPkgsMap = new HashMap<>();
            this.mDeathRecipient = new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    Slog.d(TAG, "callback binderDied");
                    synchronized (mLock) {
                        unlinkPreviousDeathRecipient();
                    }
                }
            };
        }

        private void onPackageListChanged() {
            synchronized (this.mLock) {
                this.mPackageListDirty = true;
                this.lastAllPkgCheckTime = 0L;
                if (this.curState == SERVICE_STATE_RUNNING || this.mPackageRefreshScheduled) {
                    return;
                }
                this.mPackageRefreshScheduled = true;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        handleInit();
                    } finally {
                        boolean refreshAgain;
                        synchronized (mLock) {
                            mPackageRefreshScheduled = false;
                            refreshAgain = mPackageListDirty;
                        }
                        if (refreshAgain) {
                            onPackageListChanged();
                        }
                    }
                }
            }, "AxionDexoptPackageRefresh").start();
        }

        private void checkAllPkgs() {
            if (!this.mPackageListDirty && !isNeedRecheckAllPkgs()) {
                return;
            }
            PackageManagerLocal pml = PackageManagerServiceUtils.getPackageManagerLocal();
            if (pml == null) {
                return;
            }
            this.lastAllPkgCheckTime = System.currentTimeMillis();
            if (this.mSnapshotCache != null) {
                this.mSnapshotCache.close();
            }
            this.mSnapshotCache = pml.withFilteredSnapshot();
            this.mPackageListDirty = false;
            List<String> list = AxDexoptManagerImpl.this.getPkgsToBeOptimized(mPms.snapshotComputer());
            if (!this.mPkgsToBeOpt.isEmpty()) {
                this.mPkgsToBeOpt.clear();
            }
            for (String str : list) {
                debugSlog("check pkg:" + str);
                if (!isDefaultUserApp(str)) {
                    debugSlog("[skip]non default user app," + str);
                    continue;
                }
                PackageState packageState = this.mSnapshotCache.getPackageState(str);
                if (packageState == null || packageState.getAndroidPackage() == null) {
                    continue;
                }
                DexoptStatus.DexContainerFileDexoptStatus primaryStatus = getPrimaryStatus(str);
                if (primaryStatus == null) {
                    continue;
                }
                String compilerFilter = primaryStatus.getCompilerFilter();
                if (isNeedToSkipByFilter(compilerFilter)) {
                    debugSlog("[skip]not required compiler filter:" + compilerFilter);
                } else if (COMPILER_FILTER_RUN_FROM_APK.equals(compilerFilter) || COMPILER_FILTER_RUN_FROM_APK_FALLBACK.equals(compilerFilter)) {
                    debugSlog("[add]special filter, need Optimization. filter:" + compilerFilter);
                    this.mPkgsToBeOpt.add(str);
                } else {
                    String compilationReason = primaryStatus.getCompilationReason();
                    if (COMPILER_FILTER_VERIFY.equals(compilerFilter) && COMPILER_FILTER_VDEX.equals(compilationReason)) {
                        if (!getRefPrimaryProfFile(str).exists()) {
                            debugSlog("[skip]verify and vdex, no ref primary.prof");
                        } else if (!isNeedToSkipForLastFailed(str)) {
                            debugSlog("[add]verify and vdex, need Optimization");
                            this.mPkgsToBeOpt.add(str);
                        }
                    } else if (checkCurPrimaryProf(str)) {
                        this.mPkgsToBeOpt.add(str);
                    } else if (getApkLastModified(str) > getOdexLastModified(primaryStatus.getLocationDebugString())) {
                        debugSlog("[add]apk updated, need Optimization");
                        this.mPkgsToBeOpt.add(str);
                    } else {
                        debugSlog("[skip] no condition meet.");
                    }
                }
            }
        }

        private boolean checkCurPrimaryProf(String str) {
            Pair<Long, Long> primaryProfSize = getPrimaryProfSize(str);
            long curSize = primaryProfSize.first;
            if (curSize <= CUR_PROF_FILE_SIZE_MIN) {
                debugSlog("[skip]cur primary.prof not exists or size is not big enough.fileSize:" + curSize + ", requiredSize:" + CUR_PROF_FILE_SIZE_MIN);
                return false;
            }
            if (isNeedToSkipForLastFailed(str, primaryProfSize)) {
                return false;
            }
            debugSlog("[add]cur primary profile updated, need Optimization, size:" + curSize + "Bytes");
            return true;
        }

        private void debugSlog(String str) {
            if (DEBUG) {
                Slog.d(TAG, str);
            }
        }

        private void delayForUiAnimation(long startTime) {
            long remaining = PER_PKG_OPT_MIN_TIME_MS - (System.currentTimeMillis() - startTime);
            if (remaining >= 100) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException unused) {
                    Thread.currentThread().interrupt();
                    Slog.d(TAG, "sleep exception!");
                }
            }
        }

        private long getApkLastModified(String str) {
            try {
                ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(str, 0);
                return new File(appInfo.sourceDir).lastModified();
            } catch (PackageManager.NameNotFoundException unused) {
                Slog.e(TAG, "Package not found: " + str);
                return -1L;
            }
        }

        private int getCurServiceState() {
            synchronized (this.mLock) {
                return this.curState;
            }
        }

        private long getLastUsDexoptStartedTime() {
            return SystemProperties.getLong(PROP_LAST_US_DEXOPT, 0L);
        }

        private File getOdexFile(String str) {
            if (str.isEmpty()) {
                return null;
            }
            return new File(str);
        }

        private long getOdexLastModified(String str) {
            File odexFile = getOdexFile(str);
            if (odexFile == null || !odexFile.exists()) {
                return -1L;
            }
            return odexFile.lastModified();
        }

        private Pair<Long, Long> getPrimaryProfSize(String str) {
            ArtManagedFileStats stats = DexOptHelper.getArtManagerLocal().getArtManagedFileStats(this.mSnapshotCache, str);
            return new Pair<>(stats.getTotalSizeBytesByType(2), stats.getTotalSizeBytesByType(1));
        }

        private DexoptStatus.DexContainerFileDexoptStatus getPrimaryStatus(String str) {
            try {
                DexoptStatus dexoptStatus = DexOptHelper.getArtManagerLocal().getDexoptStatus(this.mSnapshotCache, str);
                if (dexoptStatus != null) {
                    for (DexoptStatus.DexContainerFileDexoptStatus status : dexoptStatus.getDexContainerFileDexoptStatuses()) {
                        if (status.isPrimaryDex()) {
                            return status;
                        }
                    }
                }
                return null;
            } catch (Exception e) {
                Slog.e(TAG, "getPrimaryStatus error: " + e);
                return null;
            }
        }

        private File getRefPrimaryProfFile(String str) {
            return new File("/data/misc/profiles/ref/" + str + "/primary.prof");
        }

        private boolean isCallbackValid() {
            if (this.mCallbackHandlerRef == null) {
                Slog.e(TAG, "mCallbackHandlerRef is null!");
                return false;
            }
            if (this.mCallbackHandlerRef.asBinder() == null) {
                Slog.e(TAG, "mCallbackHandlerRef.asBinder() is null!");
                return false;
            }
            if (this.mCallbackHandlerRef.asBinder().isBinderAlive()) {
                return true;
            }
            Slog.e(TAG, "mCallbackHandlerRef.asBinder().isBinderAlive() is false!");
            return false;
        }

        private boolean isDefaultUserApp(String str) {
            try {
                return UserHandle.getUserId(mContext.getPackageManager().getApplicationInfo(str, 0).uid) == 0;
            } catch (PackageManager.NameNotFoundException unused) {
                Slog.i(TAG, "isDefaultUserApp exception:" + str);
                return false;
            }
        }

        private boolean isNeedRecheckAllPkgs() {
            long now = System.currentTimeMillis();
            if (now - this.lastAllPkgCheckTime >= 86400000L) {
                return true;
            }
            Slog.d(TAG, "all pkgs has checked at time:" + this.lastAllPkgCheckTime + ", curTime:" + now + ", less then 86400000ms");
            return false;
        }

        private boolean isNeedToBatchDexopt() {
            long now = System.currentTimeMillis();
            long lastBg = getLastBgDexoptTime();
            long lastUs = getLastUsDexoptStartedTime();
            if (now - lastBg >= 259200000L && now - lastUs >= 259200000L) {
                return true;
            }
            Slog.d(TAG, "time gap less then 259200000ms,lastBgDexopt:" + lastBg + ",lastUsDexopt:" + lastUs);
            return false;
        }

        private boolean isNeedToSkipByFilter(String str) {
            return !mValidFilters.contains(str);
        }

        private boolean isNeedToSkipForLastFailed(String str) {
            if (this.mLastDexoptFailedPkgsMap.isEmpty() || !this.mLastDexoptFailedPkgsMap.containsKey(str)) {
                return false;
            }
            Pair<Long, Long> lastFailedSize = this.mLastDexoptFailedPkgsMap.get(str);
            if (lastFailedSize != null && lastFailedSize.equals(getPrimaryProfSize(str))) {
                debugSlog("[skip]no profile upated compared to last faild record.");
                return true;
            }
            return false;
        }

        private boolean isNeedToSkipForLastFailed(String str, Pair<Long, Long> currentSize) {
            if (this.mLastDexoptFailedPkgsMap.isEmpty() || !this.mLastDexoptFailedPkgsMap.containsKey(str)) {
                return false;
            }
            if (currentSize.equals(this.mLastDexoptFailedPkgsMap.get(str))) {
                debugSlog("[skip]no profile upated compared to last faild record.");
                return true;
            }
            return false;
        }

        private void handleInit() {
            Slog.d(TAG, "init, checking all pkgs");
            synchronized (this.mLock) {
                checkAllPkgs();
                int state = this.mPkgsToBeOpt.size() > 0 ? SERVICE_STATE_INIT : SERVICE_STATE_DONE_MANUAL;
                setCurServiceState(state);
                if (isCallbackValid()) {
                    try {
                        this.mCallbackHandlerRef.notifyConnected(new ArrayList<>(this.mPkgsToBeOpt), state, null);
                    } catch (RemoteException | IllegalStateException e) {
                        Slog.e(TAG, "notifyConnected error", e);
                    }
                }
            }
        }

        private void handlePerformDexOptimization() {
            setCurServiceState(SERVICE_STATE_RUNNING);
            int total;
            synchronized (this.mLock) {
                total = this.mPkgsToBeOpt.size();
            }
            showUserDexoptProgress(0, total);
            int i = 0;
            try {
                while (true) {
                    String pkg;
                    synchronized (this.mLock) {
                        if (i >= this.mPkgsToBeOpt.size()) {
                            break;
                        }
                        pkg = this.mPkgsToBeOpt.get(i);
                        this.curOptPkg = pkg;
                    }

                    long startTime = System.currentTimeMillis();
                    DexoptResult res = AxArtManagerLocalHelper.performDexOptimization(pkg, REASON_USER_DEXOPT);
                    int finalStatus = (res != null) ? res.getFinalStatus() : 30;
                    String actualFilter = "";
                    if (res != null && !res.getPackageDexoptResults().isEmpty()) {
                        List<DexContainerFileDexoptResult> containerResults = res.getPackageDexoptResults().get(0).getDexContainerFileDexoptResults();
                        if (!containerResults.isEmpty()) {
                            actualFilter = containerResults.get(0).getActualCompilerFilter();
                        }
                    }
                    delayForUiAnimation(startTime);
                    updateLastDexoptFailedPkgsMap(pkg, finalStatus, actualFilter);
                    if (this.isNeedToDelayByReConnect) {
                        sleepForUi(5000L);
                        this.isNeedToDelayByReConnect = false;
                    }
                    int idx = this.mPkgsToBeOpt.lastIndexOf(pkg);
                    i = idx + 1;
                    showUserDexoptProgress(i, total);
                    if (isCallbackValid()) {
                        try {
                            this.mCallbackHandlerRef.notifyProgress(i, this.mPkgsToBeOpt.size(), pkg);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "notifyProgress failed", e);
                        }
                    }
                }

                if (isCallbackValid()) {
                    try {
                        this.mCallbackHandlerRef.notifyCompleted();
                    } catch (RemoteException e) {
                        Slog.e(TAG, "notifyCompleted failed", e);
                    }
                }
                showUserDexoptCompleted(total);
            } catch (RuntimeException e) {
                Slog.e(TAG, "user dexopt failed", e);
                showUserDexoptError();
                if (isCallbackValid()) {
                    try {
                        this.mCallbackHandlerRef.notifyError(e.getMessage());
                    } catch (RemoteException | IllegalStateException callbackException) {
                        Slog.e(TAG, "notifyError failed", callbackException);
                    }
                }
            } finally {
                boolean refreshPackages;
                synchronized (this.mLock) {
                    this.curOptPkg = null;
                    this.mPkgsToBeOpt.clear();
                    setCurServiceState(SERVICE_STATE_DONE_MANUAL);
                    refreshPackages = this.mPackageListDirty;
                }
                if (refreshPackages) {
                    onPackageListChanged();
                }
            }
        }

        private boolean performedIdleBgDexoptLatest() {
            return getLastBgDexoptTime() >= getLastUsDexoptStartedTime();
        }

        private void setCurServiceState(int state) {
            synchronized (this.mLock) {
                this.curState = state;
            }
        }

        private void sleepForUi(long time) {
            try {
                Thread.sleep(time);
            } catch (InterruptedException unused) {
                Thread.currentThread().interrupt();
                Slog.d(TAG, "sleep exception!");
            }
        }

        private void unlinkPreviousDeathRecipient() {
            if (this.mCallbackHandlerRef != null) {
                if (this.mCallbackHandlerRef.asBinder() != null) {
                    try {
                        Slog.d(TAG, "unlinkPreviousDeathRecipient, unlinkToDeath");
                        this.mCallbackHandlerRef.asBinder().unlinkToDeath(this.mDeathRecipient, 0);
                    } catch (Exception e) {
                        Slog.e(TAG, "unlinkPreviousDeathRecipient.", e);
                    }
                }
                this.mCallbackHandlerRef = null;
            }
        }

        private void updateLastDexoptFailedPkgsMap(String packageName, int status, String actualFilter) {
            if (status == 20 && COMPILER_FILTER_SPEED_PROFILE.equals(actualFilter)) {
                this.mLastDexoptFailedPkgsMap.remove(packageName);
                return;
            }
            if (status != 10 && status != 20 && status != 30) {
                return;
            }
            ArtManagedFileStats stats = DexOptHelper.getArtManagerLocal().getArtManagedFileStats(this.mSnapshotCache, packageName);
            this.mLastDexoptFailedPkgsMap.put(packageName, new Pair<>(stats.getTotalSizeBytesByType(2), stats.getTotalSizeBytesByType(1)));
        }

        private void updatePkgsToBeOpt() {
            if (this.mPkgsToBeOpt.size() > 0) {
                Iterator<String> it = this.mPkgsToBeOpt.iterator();
                while (it.hasNext()) {
                    String next = it.next();
                    if (!isDefaultUserApp(next)) {
                        Slog.d(TAG, "remove pkg from mPkgsToBeOpt, " + next);
                        it.remove();
                    }
                }
            }
        }

        public void deinit() {
            synchronized (this.mLock) {
                this.mCallbackHandlerRef = null;
            }
        }

        public void dump(PrintWriter printWriter, String[] args) {
            synchronized (this.mLock) {
                if (args.length == 1) {
                    printWriter.println("");
                    printWriter.println("[ax_us_dexopt]LastUsDexoptStartedTime:" + getLastUsDexoptStartedTime());
                    printWriter.println("[ax_us_dexopt]lastAllPkgCheckTime:" + this.lastAllPkgCheckTime);
                    printWriter.println("[ax_us_dexopt]to be optimized pkgs:" + this.mPkgsToBeOpt.size());
                    for (String pkg : this.mPkgsToBeOpt) {
                        printWriter.println(pkg);
                    }
                    printWriter.println("");
                    if (this.mCallbackHandlerRef == null) {
                        printWriter.println("[ax_us_dexopt]mCallbackHandlerRef: null");
                    } else {
                        try {
                            printWriter.println("[ax_us_dexopt]mCallbackHandlerRef descriptor: " + this.mCallbackHandlerRef.asBinder().getInterfaceDescriptor());
                            printWriter.println("[ax_us_dexopt]mCallbackHandlerRef toString: " + this.mCallbackHandlerRef.asBinder().toString());
                            printWriter.println("[ax_us_dexopt]mCallbackHandlerRef alive: " + this.mCallbackHandlerRef.asBinder().isBinderAlive());
                        } catch (RemoteException e) {
                            Slog.e(TAG, "failed to query mCallbackHandlerRef", e);
                        }
                    }
                    printWriter.println("[ax_us_dexopt]curState:" + this.curState);
                    printWriter.println("[ax_us_dexopt]curOptPkg:" + this.curOptPkg);
                    printWriter.println("[ax_us_dexopt]isNeedToDelayByReConnect:" + this.isNeedToDelayByReConnect);
                    printWriter.println("[ax_us_dexopt]last dexopt failed records:" + this.mLastDexoptFailedPkgsMap.size());
                    printWriter.println("[package_name][cur_prof_bytes][ref_prof_bytes]");
                    for (String str : this.mLastDexoptFailedPkgsMap.keySet()) {
                        Pair<Long, Long> pair = this.mLastDexoptFailedPkgsMap.get(str);
                        printWriter.println(str + "," + pair.first + "," + pair.second);
                    }
                    printWriter.println("");
                } else if (args.length >= 3) {
                    String target = args[1];
                    String action = args[2];
                    if ("us".equals(target)) {
                        if ("check-all".equals(action)) {
                            checkAllPkgs();
                        } else {
                            this.mPkgsToBeOpt.add(action);
                            printWriter.println("add package:" + action);
                        }
                    }
                }
            }
        }

        public List<String> getPkgsToBeOptimized() {
            ArrayList<String> arrayList;
            Slog.d(TAG, "getPkgsToBeOptimized");
            synchronized (this.mLock) {
                updatePkgsToBeOpt();
                arrayList = new ArrayList<>(this.mPkgsToBeOpt);
            }
            Slog.d(TAG, "getPkgsToBeOptimized, size:" + arrayList.size());
            return arrayList;
        }

        public void init(IAxUserStartDexoptStatusHandler handler) {
            int state = SERVICE_STATE_NONE;
            synchronized (this.mLock) {
                if (handler == null) {
                    unlinkPreviousDeathRecipient();
                    this.mCallbackHandlerRef = null;
                    Slog.e(TAG, "init, handler is null");
                } else if (this.mCallbackHandlerRef == null || !handler.asBinder().equals(this.mCallbackHandlerRef.asBinder())) {
                    Slog.d(TAG, "init, handler is a new binder");
                    unlinkPreviousDeathRecipient();
                    this.mCallbackHandlerRef = handler;
                    if (handler.asBinder() != null) {
                        try {
                            this.mCallbackHandlerRef.asBinder().linkToDeath(this.mDeathRecipient, 0);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "linkToDeath failed", e);
                        }
                    }
                } else {
                    Slog.d(TAG, "init, repeat registration, skip");
                }
            }
            int curServiceState = getCurServiceState();
            boolean needBatchDexopt = isNeedToBatchDexopt();
            if (curServiceState != SERVICE_STATE_NONE && curServiceState != SERVICE_STATE_DONE_MANUAL) {
                if (curServiceState == SERVICE_STATE_INIT) {
                    state = needBatchDexopt ? (!isNeedRecheckAllPkgs() ? SERVICE_STATE_INIT : SERVICE_STATE_NONE) : SERVICE_STATE_DONE_MANUAL;
                } else if (!needBatchDexopt) {
                    state = SERVICE_STATE_RUNNING;
                }
            } else if (!needBatchDexopt) {
                state = SERVICE_STATE_DONE_MANUAL;
            }
            setCurServiceState(state);
            if (state == SERVICE_STATE_NONE) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handleInit();
                    }
                }).start();
                return;
            }
            synchronized (this.mLock) {
                if (state == SERVICE_STATE_RUNNING) {
                    this.isNeedToDelayByReConnect = true;
                } else if (state == SERVICE_STATE_DONE_MANUAL) {
                    this.mPkgsToBeOpt.clear();
                    state = performedIdleBgDexoptLatest() ? SERVICE_STATE_DONE_AUTO : SERVICE_STATE_DONE_MANUAL;
                }
                if (isCallbackValid()) {
                    try {
                        updatePkgsToBeOpt();
                        this.mCallbackHandlerRef.notifyConnected(new ArrayList<>(this.mPkgsToBeOpt), state, this.curOptPkg);
                    } catch (RemoteException | IllegalStateException e) {
                        Slog.e(TAG, "notifyConnected error", e);
                    }
                }
            }
        }

        public void performDexOptimization() {
            if (getCurServiceState() == SERVICE_STATE_RUNNING) {
                Slog.d(TAG, "service state is RUNNING, return");
                return;
            }
            Slog.d(TAG, "performDexOptimization");
            setLastUsDexoptStartedTime(System.currentTimeMillis());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    handlePerformDexOptimization();
                }
            }).start();
        }

        public void setLastUsDexoptStartedTime(long time) {
            SystemProperties.set(PROP_LAST_US_DEXOPT, Long.toString(time));
        }

        public void systemReady() {
            synchronized (this.mLock) {
                this.curOptPkg = null;
                this.mCallbackHandlerRef = null;
                this.curState = SERVICE_STATE_NONE;
                this.isNeedToDelayByReConnect = false;
                this.mPackageListDirty = false;
                this.mPackageRefreshScheduled = false;
            }
        }
    }
}
