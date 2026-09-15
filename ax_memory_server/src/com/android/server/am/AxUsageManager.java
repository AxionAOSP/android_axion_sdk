package com.android.server.am;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.util.AtomicFile;
import android.util.Slog;
import com.android.server.am.ProcessRecord;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AxUsageManager implements IAxUsageManager {
    public static final String TAG = "AxUsageManager";

    private static final boolean DEBUG = SystemProperties.getBoolean("persist.sys.ax_mem.debug", false);

    public static final String RECORD_FILENAME = "ax_app_record.txt";
    public static final String RECORD_BACKUP_FILENAME = "ax_app_record_backup.txt";

    private static final int MSG_APP_DIED = 0;
    private static final int MSG_PERSIST_RECORDS = 1;
    private static final long PERSIST_DEBOUNCE_MS = 30000L;

    private static volatile AxUsageManager sInstance;

    private Context mContext;
    private PackageManagerInternal mPackageManagerInternal;
    private HandlerThread mHandlerThread;
    private WorkerHandler mHandler;

    private File mDataDir;
    private AtomicFile mAtomicFile;

    private final Map<String, AppUsageEntry> mEntries = new HashMap<>();
    private final ArrayList<String> mHighUsedCache = new ArrayList<>();
    private final ArrayList<SimpleAppRecord> mHighUsedRecordsCache = new ArrayList<>();
    private String mUpdatingPackage = "";

    public static final class AppUsageEntry {
        public String packageName;
        public int launchCount;
        public long totalDurationMs;
        public long lastLaunchTime;
        public long lastCachedPss;
        public long lastRemoveTaskTime;
        public long lastLmkdKillTime;
        public int curTargetAdj = -1;

        public AppUsageEntry(String packageName) {
            this.packageName = packageName;
        }

        public double calculateScore() {
            return (launchCount * 1000.0) + (totalDurationMs / 1000.0) + (lastLaunchTime / 1000000.0);
        }

        public SimpleAppRecord toSimpleAppRecord() {
            SimpleAppRecord sar = new SimpleAppRecord(packageName);
            sar.mCurTargetAdj = curTargetAdj;
            sar.mLastCachedPss = lastCachedPss;
            sar.mLastLmkdTimeTime = lastLmkdKillTime;
            sar.mLastRemoveTaskTime = lastRemoveTaskTime;
            return sar;
        }

        public String serialize() {
            return "pkg:" + packageName + ",launches:" + launchCount + ",duration:" + totalDurationMs + ",lastLaunch:" + lastLaunchTime + ",pss:" + lastCachedPss + ",removeTask:" + lastRemoveTaskTime + ",lmkdKill:" + lastLmkdKillTime + ",adj:" + curTargetAdj + "\n";
        }
    }

    public static AxUsageManager getInstance() {
        if (sInstance == null) {
            synchronized (AxUsageManager.class) {
                if (sInstance == null) {
                    sInstance = new AxUsageManager();
                }
            }
        }
        return sInstance;
    }

    private AxUsageManager() {
        this.mDataDir = new File("/data/system");
    }

    private final class WorkerHandler extends Handler {
        WorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_APP_DIED) {
                handleAppDiedMessage(msg.getData());
            } else if (msg.what == MSG_PERSIST_RECORDS) {
                writeRecordsToDisk();
            }
        }
    }

    @Override
    public synchronized void systemReady(Context context, PackageManagerInternal pmi) {
        this.mContext = context;
        this.mPackageManagerInternal = pmi;

        this.mHandlerThread = new HandlerThread("AxUsageManager");
        this.mHandlerThread.start();
        this.mHandler = new WorkerHandler(this.mHandlerThread.getLooper());

        readRecordsFromDisk();
        if (this.mEntries.isEmpty()) {
            seedDefaultCandidates();
            recalculateTiers();
            scheduleDiskWrite();
        } else {
            recalculateTiers();
        }
        registerTimeChangeReceiver();
    }

    private void registerTimeChangeReceiver() {
        if (this.mContext == null) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        this.mContext.registerReceiver(new TimeChangeReceiver(), filter);
    }

    private final class TimeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            cleanAllData(System.currentTimeMillis());
        }
    }

    private void seedDefaultCandidates() {
        if (this.mContext == null) {
            return;
        }
        PackageManager pm = this.mContext.getPackageManager();
        Intent[] seedIntents = new Intent[]{
                new Intent(Intent.ACTION_DIAL),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        };

        for (Intent intent : seedIntents) {
            ResolveInfo info = pm.resolveActivity(intent, 0);
            if (info != null && info.activityInfo != null && info.activityInfo.packageName != null) {
                String pkg = info.activityInfo.packageName;
                if (!"android".equals(pkg) && !pkg.contains("resolver")) {
                    AppUsageEntry entry = getOrCreateEntry(pkg);
                    entry.launchCount = 5;
                    entry.lastLaunchTime = System.currentTimeMillis();
                }
            }
        }
    }

    private synchronized AppUsageEntry getOrCreateEntry(String packageName) {
        return this.mEntries.computeIfAbsent(packageName, AppUsageEntry::new);
    }

    @Override
    public synchronized void updateLaunchTime(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        AppUsageEntry entry = getOrCreateEntry(packageName);
        entry.launchCount++;
        entry.lastLaunchTime = System.currentTimeMillis();
        recalculateTiers();
        scheduleDiskWrite();
    }

    @Override
    public synchronized void updateDuration(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }
        AppUsageEntry entry = getOrCreateEntry(packageName);
        long now = System.currentTimeMillis();
        if (entry.lastLaunchTime > 0 && now > entry.lastLaunchTime) {
            long delta = now - entry.lastLaunchTime;
            if (delta < 3600000L) {
                entry.totalDurationMs += delta;
            }
        }
        entry.lastLaunchTime = now;
        scheduleDiskWrite();
    }

    @Override
    public synchronized void setLastCachedPss(String pkgName, long pss) {
        if (pkgName == null || pkgName.isEmpty()) {
            return;
        }
        AppUsageEntry entry = getOrCreateEntry(pkgName);
        entry.lastCachedPss = pss;
    }

    @Override
    public synchronized void setRemoveTaskTime(String pkgName) {
        if (pkgName == null || pkgName.isEmpty()) {
            return;
        }
        AppUsageEntry entry = getOrCreateEntry(pkgName);
        entry.lastRemoveTaskTime = System.currentTimeMillis();
        scheduleDiskWrite();
    }

    @Override
    public synchronized void setTargetAdj(String pkgName, int adj) {
        if (pkgName == null || pkgName.isEmpty()) {
            return;
        }
        AppUsageEntry entry = getOrCreateEntry(pkgName);
        entry.curTargetAdj = adj;
    }

    @Override
    public void appDied(ProcessRecord p) {
        if (p == null || p.info == null || this.mHandler == null) {
            return;
        }
        Message msg = this.mHandler.obtainMessage(MSG_APP_DIED);
        Bundle bundle = new Bundle();
        bundle.putString("pkg_name", p.info.packageName);
        bundle.putInt("pid", p.getPid());
        msg.setData(bundle);
        this.mHandler.sendMessage(msg);
    }

    private void handleAppDiedMessage(Bundle data) {
        if (data == null) {
            return;
        }
        String pkg = data.getString("pkg_name");
        int pid = data.getInt("pid", -1);
        if (pkg == null || pid < 0) {
            return;
        }

        Integer killStatus = ProcessList.checkLmkdKillOptiProc(pid);
        if (killStatus != null && killStatus.intValue() == 1) {
            synchronized (this) {
                AppUsageEntry entry = this.mEntries.get(pkg);
                if (entry != null) {
                    entry.lastLmkdKillTime = System.currentTimeMillis();
                    if (DEBUG) {
                        Slog.d(TAG, "Recorded LMKD kill for: " + pkg + " at " + entry.lastLmkdKillTime);
                    }
                }
            }
            scheduleDiskWrite();
        }
    }

    @Override
    public synchronized boolean isHighUsedPackages(String pkgName) {
        return this.mHighUsedCache.contains(pkgName);
    }

    @Override
    public synchronized ArrayList<String> getHighUsedPackageList(boolean needUpdate) {
        if (needUpdate) {
            recalculateTiers();
        }
        return new ArrayList<>(this.mHighUsedCache);
    }

    @Override
    public synchronized ArrayList<SimpleAppRecord> getHighUsedRecords(boolean needUpdate) {
        if (needUpdate) {
            recalculateTiers();
        }
        return new ArrayList<>(this.mHighUsedRecordsCache);
    }

    @Override
    public synchronized SimpleAppRecord geedHighUsedRecord(boolean needUpdate, String packageName) {
        if (packageName == null) {
            return null;
        }
        AppUsageEntry entry = this.mEntries.get(packageName);
        return entry != null ? entry.toSimpleAppRecord() : null;
    }

    @Override
    public synchronized void setScreenState(boolean isOff) {
        if (isOff) {
            scheduleDiskWrite();
        }
    }

    @Override
    public synchronized void cleanAllData(long millis) {
        long current = System.currentTimeMillis();
        if (Math.abs(millis - current) < 3600000L && !this.mEntries.isEmpty()) {
            return;
        }
        this.mEntries.clear();
        this.mHighUsedCache.clear();
        this.mHighUsedRecordsCache.clear();
        seedDefaultCandidates();
        recalculateTiers();
        scheduleDiskWrite();
    }

    @Override
    public synchronized void removePackage(String packageName) {
        if (packageName == null || packageName.equals(this.mUpdatingPackage)) {
            return;
        }
        this.mEntries.remove(packageName);
        recalculateTiers();
        scheduleDiskWrite();
    }

    @Override
    public synchronized void addNewPackages(String packageName) {
        if (packageName != null) {
            getOrCreateEntry(packageName);
            this.mUpdatingPackage = "";
            recalculateTiers();
        }
    }

    @Override
    public synchronized void setUpdatingPackage(String packageName) {
        this.mUpdatingPackage = packageName != null ? packageName : "";
    }

    private void recalculateTiers() {
        ArrayList<AppUsageEntry> sorted = new ArrayList<>(this.mEntries.values());
        sorted.sort(Comparator.comparingDouble(AppUsageEntry::calculateScore).reversed());

        this.mHighUsedCache.clear();
        this.mHighUsedRecordsCache.clear();

        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            AppUsageEntry entry = sorted.get(i);
            this.mHighUsedCache.add(entry.packageName);
            this.mHighUsedRecordsCache.add(entry.toSimpleAppRecord());
        }
    }

    private void scheduleDiskWrite() {
        if (this.mHandler != null) {
            this.mHandler.removeMessages(MSG_PERSIST_RECORDS);
            this.mHandler.sendEmptyMessageDelayed(MSG_PERSIST_RECORDS, PERSIST_DEBOUNCE_MS);
        }
    }

    private void writeRecordsToDisk() {
        String payload = serializeEntries();
        FileOutputStream fos = null;
        try {
            this.mAtomicFile = new AtomicFile(new File(this.mDataDir, RECORD_FILENAME));
            fos = this.mAtomicFile.startWrite();
            fos.write(payload.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            this.mAtomicFile.finishWrite(fos);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to persist AxUsageManager records", e);
            if (this.mAtomicFile != null) {
                this.mAtomicFile.failWrite(fos);
            }
        }
    }

    private synchronized String serializeEntries() {
        StringBuilder sb = new StringBuilder();
        for (AppUsageEntry entry : this.mEntries.values()) {
            sb.append(entry.serialize());
        }
        return sb.toString();
    }

    private void readRecordsFromDisk() {
        File file = new File(this.mDataDir, RECORD_FILENAME);
        if (!file.exists()) {
            file = new File(this.mDataDir, RECORD_BACKUP_FILENAME);
            if (!file.exists()) {
                return;
            }
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                parseRecordLine(line);
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to read AxUsageManager records", e);
        }
    }

    private void parseRecordLine(String line) {
        try {
            String[] parts = line.split(",");
            String pkg = null;
            int launches = 0;
            long duration = 0;
            long lastLaunch = 0;
            long pss = 0;
            long removeTask = 0;
            long lmkdKill = 0;
            int adj = -1;

            for (String part : parts) {
                int colon = part.indexOf(':');
                if (colon < 0) continue;
                String key = part.substring(0, colon);
                String val = part.substring(colon + 1);
                switch (key) {
                    case "pkg": pkg = val; break;
                    case "launches": launches = Integer.parseInt(val); break;
                    case "duration": duration = Long.parseLong(val); break;
                    case "lastLaunch": lastLaunch = Long.parseLong(val); break;
                    case "pss": pss = Long.parseLong(val); break;
                    case "removeTask": removeTask = Long.parseLong(val); break;
                    case "lmkdKill": lmkdKill = Long.parseLong(val); break;
                    case "adj": adj = Integer.parseInt(val); break;
                }
            }

            if (pkg != null && !pkg.isEmpty()) {
                AppUsageEntry entry = getOrCreateEntry(pkg);
                entry.launchCount = launches;
                entry.totalDurationMs = duration;
                entry.lastLaunchTime = lastLaunch;
                entry.lastCachedPss = pss;
                entry.lastRemoveTaskTime = removeTask;
                entry.lastLmkdKillTime = lmkdKill;
                entry.curTargetAdj = adj;
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void handleDump(PrintWriter pw, String[] args) {
        pw.println("AxUsageManager Dump:");
        synchronized (this) {
            for (AppUsageEntry entry : this.mEntries.values()) {
                pw.println("  " + entry.packageName + " launches=" + entry.launchCount + " durationMs=" + entry.totalDurationMs + " pss=" + entry.lastCachedPss + " adj=" + entry.curTargetAdj);
            }
        }
    }
}
