/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.axdragonite;

import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Slog;
import android.util.SparseArray;

import java.io.PrintWriter;
import java.lang.NumberFormatException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import com.android.internal.dragonite.AxDragoniteConstants;
import static com.android.internal.dragonite.AxDragoniteConstants.*;

/**
 * @hide
 */
public final class AxDragonite {
    private static final String TAG = AxDragoniteConstants.TAG;

    public static final int SCENE_FLING = AxDragoniteConstants.SCENE_FLING;
    public static final int SCENE_SCROLL = AxDragoniteConstants.SCENE_SCROLL;
    public static final int SCENE_APP_LAUNCH_COLD = AxDragoniteConstants.SCENE_APP_LAUNCH_COLD;
    public static final int SCENE_APP_LAUNCH_WARM = AxDragoniteConstants.SCENE_APP_LAUNCH_WARM;
    public static final int SCENE_ROTATION = AxDragoniteConstants.SCENE_ROTATION;
    public static final int SCENE_RECENT_TASK_SLIDE = AxDragoniteConstants.SCENE_RECENT_TASK_SLIDE;
    public static final int SCENE_CAMERA_OPEN = AxDragoniteConstants.SCENE_CAMERA_OPEN;
    public static final int SCENE_GAME_MODE = AxDragoniteConstants.SCENE_GAME_MODE;
    public static final int SCENE_AX_APP_START = AxDragoniteConstants.SCENE_AX_APP_START;
    public static final int SCENE_AX_FLING = AxDragoniteConstants.SCENE_AX_FLING;

    public static final int BOOST_LEVEL_NONE = AxDragoniteConstants.BOOST_LEVEL_NONE;
    public static final int BOOST_LEVEL_LIGHT = AxDragoniteConstants.BOOST_LEVEL_LIGHT;
    public static final int BOOST_LEVEL_HEAVY = AxDragoniteConstants.BOOST_LEVEL_HEAVY;

    public static final class ScenarioConfig {
        public final int sceneId;
        public final int defaultTimeoutMs;
        public final int boostLevel;
        public final boolean boostRenderThread;
        public final boolean pinKswapd;

        public ScenarioConfig(int id, int timeout, int level, boolean boostRt, boolean pinKs) {
            this.sceneId = id;
            this.defaultTimeoutMs = timeout;
            this.boostLevel = level;
            this.boostRenderThread = boostRt;
            this.pinKswapd = pinKs;
        }
    }

    private static final class LeaseRecord {
        public final int handle;
        public final int sceneId;
        public final int callingPid;
        public final int callingUid;
        public final String packageName;
        public final long acquireTimeMs;
        public final int targetPid;
        public final ScenarioConfig config;
        public final Runnable timeoutRunnable;
        public final Set<Integer> boostedTids = new HashSet<>();

        public LeaseRecord(int handle, int sceneId, int callingPid, int callingUid,
                           String pkg, long acquireTime, int targetPid,
                           ScenarioConfig config, Runnable timeoutRunnable) {
            this.handle = handle;
            this.sceneId = sceneId;
            this.callingPid = callingPid;
            this.callingUid = callingUid;
            this.packageName = pkg;
            this.acquireTimeMs = acquireTime;
            this.targetPid = targetPid;
            this.config = config;
            this.timeoutRunnable = timeoutRunnable;
        }
    }

    private static AxDragonite sInstance;

    private final Object mLock = new Object();
    private final AtomicInteger mNextHandle = new AtomicInteger(INITIAL_HANDLE_VALUE);
    private final SparseArray<LeaseRecord> mActiveLeases = new SparseArray<>();
    private final SparseArray<ScenarioConfig> mScenarios = new SparseArray<>();
    private int mGameModeHandle = 0;
    private volatile String mFocusedPkg;

    private final AxCpuClusterManager mClusterManager;
    private final AxPerfEnhancer mPerfEnhancer;
    private final AxNamedThreadAffinityFeature mAffinityFeature;
    private final AxUIBooster mUIBooster;
    private final AxFrameInsertManager mFrameInsertManager;
    private final AxPerfTraceManager mTraceManager;
    private final AxBoostAdjuster mBoostAdjuster;

    private final HandlerThread mWorkerThread;
    private final Handler mWorkerHandler;
    private final HandlerThread mTimerThread;
    private final Handler mTimerHandler;

    public static synchronized AxDragonite getInstance() {
        if (sInstance == null) {
            sInstance = new AxDragonite();
        }
        return sInstance;
    }

    private AxDragonite() {
        mClusterManager = AxCpuClusterManager.getInstance();
        mPerfEnhancer = new AxPerfEnhancer(mClusterManager);
        mAffinityFeature = new AxNamedThreadAffinityFeature(mClusterManager);
        mUIBooster = new AxUIBooster(mPerfEnhancer, mClusterManager);
        mFrameInsertManager = new AxFrameInsertManager();
        mTraceManager = new AxPerfTraceManager();
        mBoostAdjuster = new AxBoostAdjuster(mPerfEnhancer, mClusterManager);

        mWorkerThread = new HandlerThread(WORKER_THREAD_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        mWorkerThread.start();
        try {
            Process.setThreadScheduler(mWorkerThread.getThreadId(), BOOST_SCHED_POLICY, BOOST_SCHED_PRIORITY);
        } catch (Throwable ignored) {
        }
        mWorkerHandler = new Handler(mWorkerThread.getLooper());

        mTimerThread = new HandlerThread(TIMER_THREAD_NAME, Process.THREAD_PRIORITY_URGENT_DISPLAY);
        mTimerThread.start();
        try {
            Process.setThreadScheduler(mTimerThread.getThreadId(), BOOST_SCHED_POLICY, BOOST_SCHED_PRIORITY);
        } catch (Throwable ignored) {
        }
        mTimerHandler = new Handler(mTimerThread.getLooper());

        initScenarios();
        Slog.i(TAG, "AxDragonite subsystem initialized");
    }

    private void initScenarios() {
        mScenarios.put(SCENE_AX_APP_START, new ScenarioConfig(SCENE_AX_APP_START, DURATION_AX_APP_START_MS, BOOST_LEVEL_HEAVY, true, true));
        mScenarios.put(SCENE_FLING, new ScenarioConfig(SCENE_FLING, DURATION_FLING_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_DATA_LOADING, new ScenarioConfig(SCENE_DATA_LOADING, DURATION_DEFAULT_FALLBACK_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_FOLDER_ANIMATION, new ScenarioConfig(SCENE_FOLDER_ANIMATION, DURATION_DEFAULT_FALLBACK_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_DRAG_AND_DROP, new ScenarioConfig(SCENE_DRAG_AND_DROP, DURATION_DEFAULT_FALLBACK_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_AX_NOTIFICATION_EXPAND, new ScenarioConfig(SCENE_AX_NOTIFICATION_EXPAND, DURATION_AX_NOTIFICATION_EXPAND_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_AX_UNLOCK, new ScenarioConfig(SCENE_AX_UNLOCK, DURATION_AX_UNLOCK_MS, BOOST_LEVEL_HEAVY, true, true));
        mScenarios.put(SCENE_AX_SYSTEMUI_ANIMATION, new ScenarioConfig(SCENE_AX_SYSTEMUI_ANIMATION, DURATION_AX_SYSTEMUI_ANIMATION_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_APP_LAUNCH_COLD, new ScenarioConfig(SCENE_APP_LAUNCH_COLD, DURATION_APP_LAUNCH_COLD_MS, BOOST_LEVEL_HEAVY, true, true));
        mScenarios.put(SCENE_APP_LAUNCH_WARM, new ScenarioConfig(SCENE_APP_LAUNCH_WARM, DURATION_APP_LAUNCH_WARM_MS, BOOST_LEVEL_HEAVY, true, false));
        mScenarios.put(SCENE_APP_EXIT_ANIM, new ScenarioConfig(SCENE_APP_EXIT_ANIM, DURATION_APP_EXIT_ANIM_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_ROTATION, new ScenarioConfig(SCENE_ROTATION, DURATION_ROTATION_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_CAMERA_OPEN, new ScenarioConfig(SCENE_CAMERA_OPEN, DURATION_CAMERA_OPEN_MS, BOOST_LEVEL_HEAVY, true, true));
        mScenarios.put(SCENE_CAMERA_CAPTURE, new ScenarioConfig(SCENE_CAMERA_CAPTURE, DURATION_CAMERA_CAPTURE_MS, BOOST_LEVEL_HEAVY, false, false));
        mScenarios.put(SCENE_GAME_MODE, new ScenarioConfig(SCENE_GAME_MODE, 0, BOOST_LEVEL_HEAVY, true, true));
        mScenarios.put(SCENE_BIOMETRIC_UNLOCK, new ScenarioConfig(SCENE_BIOMETRIC_UNLOCK, DURATION_BIOMETRIC_UNLOCK_MS, BOOST_LEVEL_HEAVY, true, false));
        mScenarios.put(SCENE_RECENT_TASK_SLIDE, new ScenarioConfig(SCENE_RECENT_TASK_SLIDE, DURATION_RECENT_TASK_SLIDE_MS, BOOST_LEVEL_LIGHT, true, false));
        mScenarios.put(SCENE_QUICK_SWITCH_APP, new ScenarioConfig(SCENE_QUICK_SWITCH_APP, DURATION_QUICK_SWITCH_APP_MS, BOOST_LEVEL_HEAVY, true, false));
    }

    public int sceneBoostAcquire(int sceneId, Bundle data) {
        ScenarioConfig config = mScenarios.get(sceneId);
        if (config == null) {
            config = new ScenarioConfig(sceneId, DURATION_DEFAULT_FALLBACK_MS, BOOST_LEVEL_LIGHT, true, false);
        }

        int targetPid = INVALID_PID;
        String pkgName = null;
        int customDuration = config.defaultTimeoutMs;

        if (data != null) {
            targetPid = data.getInt(KEY_TARGET_PID, data.getInt(KEY_PID, INVALID_PID));
            pkgName = data.getString(KEY_PACKAGE_NAME, data.getString(KEY_PKG, null));
            int reqDuration = data.getInt(KEY_DURATION, INVALID_DURATION);
            if (reqDuration > 0) {
                customDuration = reqDuration;
            }
        }
        if (targetPid <= 0) {
            targetPid = Binder.getCallingPid();
        }

        if (sceneId == SCENE_APP_LAUNCH_COLD || sceneId == SCENE_APP_LAUNCH_WARM) {
            customDuration = AxActivityCustomizationUtil.getLaunchDuration(pkgName, customDuration);
        } else if (sceneId == SCENE_FLING) {
            customDuration = AxActivityCustomizationUtil.getFlingDuration(pkgName, customDuration);
        }

        final int handle = mNextHandle.getAndIncrement();
        final int finalTargetPid = targetPid;
        final ScenarioConfig finalConfig = config;
        final String params = data != null ? data.getString(KEY_PARAMS) : null;

        Runnable timeoutRunnable = () -> sceneBoostRelease(handle);

        LeaseRecord record = new LeaseRecord(handle, sceneId, Binder.getCallingPid(),
                Binder.getCallingUid(), pkgName, System.currentTimeMillis(),
                targetPid, config, timeoutRunnable);

        synchronized (mLock) {
            mActiveLeases.put(handle, record);
        }

        mTimerHandler.postDelayed(timeoutRunnable, customDuration);

        mWorkerHandler.post(() -> {
            mTraceManager.reportSceneAcquire(sceneId, handle);
            mFrameInsertManager.onSceneStart(sceneId);
            parseAndApplyParams(params, record);

            mPerfEnhancer.applyCpuBoost(finalConfig.boostLevel);
            if (finalConfig.boostRenderThread && finalTargetPid > 0) {
                mUIBooster.boostProcess(finalTargetPid, finalConfig.boostLevel);
                mAffinityFeature.applyNamedAffinityForPid(finalTargetPid);
            }
            if (finalConfig.pinKswapd) {
                mPerfEnhancer.pinKswapd(mClusterManager.getLittleMask());
            }
            if (sceneId == SCENE_APP_LAUNCH_COLD || sceneId == SCENE_CAMERA_OPEN || sceneId == SCENE_AX_APP_START) {
                mBoostAdjuster.freezeBackgroundProcesses(true);
            }
            if (sceneId == SCENE_GAME_MODE) {
                applyGameMode(true);
            } else if (sceneId == SCENE_CAMERA_OPEN) {
                mPerfEnhancer.limitAxForeground(true);
            }
            if (sceneId == SCENE_APP_LAUNCH_COLD || sceneId == SCENE_AX_APP_START || sceneId == SCENE_FLING || sceneId == SCENE_AX_FLING) {
                mPerfEnhancer.sendSurfaceFlingerBoost(true);
            }
        });

        return handle;
    }

    public void sceneBoostRelease(int handle) {
        LeaseRecord record;
        synchronized (mLock) {
            record = mActiveLeases.get(handle);
            if (record != null) {
                mActiveLeases.remove(handle);
            }
        }

        if (record == null) {
            return;
        }

        mTimerHandler.removeCallbacks(record.timeoutRunnable);

        mWorkerHandler.post(() -> {
            mTraceManager.reportSceneRelease(record.sceneId, handle);
            mFrameInsertManager.onSceneEnd(record.sceneId);

            if (record.config.boostRenderThread && record.targetPid > 0) {
                mUIBooster.restoreProcess(record.targetPid);
                mAffinityFeature.resetAffinityForPid(record.targetPid);
            }
            if (record.boostedTids != null) {
                for (int tid : record.boostedTids) {
                    try {
                        Process.setThreadScheduler(tid, Process.SCHED_OTHER, 0);
                        Process.setThreadPriority(tid, Process.THREAD_PRIORITY_DEFAULT);
                        Process.setThreadAffinity(tid, (int) mClusterManager.getAllMask());
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (record.sceneId == SCENE_APP_LAUNCH_COLD || record.sceneId == SCENE_CAMERA_OPEN || record.sceneId == SCENE_AX_APP_START) {
                mBoostAdjuster.freezeBackgroundProcesses(false);
            }
            if (record.sceneId == SCENE_GAME_MODE) {
                applyGameMode(false);
            } else if (record.sceneId == SCENE_CAMERA_OPEN) {
                mPerfEnhancer.limitAxForeground(false);
            }
            if (record.sceneId == SCENE_APP_LAUNCH_COLD || record.sceneId == SCENE_AX_APP_START || record.sceneId == SCENE_FLING || record.sceneId == SCENE_AX_FLING) {
                mPerfEnhancer.sendSurfaceFlingerBoost(false);
            }

            synchronized (mLock) {
                int maxBoostLevel = BOOST_LEVEL_NONE;
                boolean anyPinKswapd = false;
                for (int i = 0; i < mActiveLeases.size(); i++) {
                    ScenarioConfig cfg = mActiveLeases.valueAt(i).config;
                    if (cfg.boostLevel > maxBoostLevel) {
                        maxBoostLevel = cfg.boostLevel;
                    }
                    if (cfg.pinKswapd) {
                        anyPinKswapd = true;
                    }
                }

                if (maxBoostLevel > BOOST_LEVEL_NONE) {
                    mPerfEnhancer.applyCpuBoost(maxBoostLevel);
                } else {
                    mPerfEnhancer.restoreCpuBoost();
                }

                if (!anyPinKswapd) {
                    mPerfEnhancer.pinKswapd(mClusterManager.getAllMask());
                }
            }
        });
    }

    public boolean isSceneIdExist(int sceneId) {
        return mScenarios.indexOfKey(sceneId) >= 0;
    }

    public int getFlingSceneId() {
        return SCENE_FLING;
    }

    public int getScrollSceneId() {
        return SCENE_SCROLL;
    }

    public int getAppLaunchSceneId() {
        return SCENE_APP_LAUNCH_COLD;
    }

    public void animationBoost(int pid, long boostDurationMs) {
        if (boostDurationMs < MIN_BOOST_DURATION_MS) {
            throw new IllegalArgumentException("boostDurationMs cannot be negative: " + boostDurationMs);
        }
        final long clampedDurationMs = Math.min(boostDurationMs, MAX_BOOST_DURATION_MS);
        if (pid <= 0) {
            pid = Binder.getCallingPid();
        }
        mBoostAdjuster.animationBoost(pid, clampedDurationMs);
    }

    public void setSystemuiThreadAffinity(int pid, int affinityType) {
        if (pid <= 0) {
            pid = Binder.getCallingPid();
        }
        final int targetPid = pid;
        final long mask = mClusterManager.getMaskForType(affinityType);
        mWorkerHandler.post(() -> {
            mAffinityFeature.setThreadAffinity(targetPid, mask);
        });
    }

    public void adjustCpusetCpus(String group, String cpus, long durationMs) {
        if ("dex2oat".equals(group) && cpus == null) {
            applyDex2oatCpusetAdjustment(durationMs);
            return;
        }
        if (group == null || !ALLOWED_CPUSET_GROUPS.contains(group)) {
            throw new IllegalArgumentException("Invalid or disallowed cpuset group: " + group);
        }
        if (cpus == null || !CPUSET_CPUS_PATTERN.matcher(cpus).matches()) {
            throw new IllegalArgumentException("Invalid cpuset cpus format: " + cpus);
        }
        if (durationMs < MIN_BOOST_DURATION_MS && durationMs != -1L) {
            throw new IllegalArgumentException("durationMs cannot be negative: " + durationMs);
        }
        final long clampedDurationMs = durationMs == -1L ? -1L : Math.min(durationMs, MAX_BOOST_DURATION_MS);
        mBoostAdjuster.adjustCpusetCpus(group, cpus, clampedDurationMs);
    }

    public void onProcessForked(int pid, boolean isTopApp) {
        if (pid <= 0 || isTopApp) return;
        mWorkerHandler.post(() -> setAxForegroundProcessGroup(pid));
    }

    private void setAxForegroundProcessGroup(int pid) {
        try {
            Process.setProcessGroup(pid, Process.THREAD_GROUP_AX_FOREGROUND);
        } catch (Exception e) {
            Slog.w(TAG, "Failed to set ax_foreground process group: " + e.getMessage());
        }
    }

    public void inputBoost() {
        mBoostAdjuster.inputBoost();
    }

    public void onProcessStarted(int pid, String pkg, String processName, int uid) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onProcessStarted(pid, pkg, processName, uid);
            mAffinityFeature.applyNamedAffinityForPid(pid);
        });
    }

    public void onProcessKilled(int pid, String pkg) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onProcessKilled(pid, pkg);
            mUIBooster.restoreProcess(pid);
            mAffinityFeature.resetAffinityForPid(pid);
        });
    }

    public void onActivityStart(String pkg, String component, int uid, boolean isCold) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onActivityStart(pkg, component, uid, isCold);
            boolean isCamera = pkg != null && pkg.toLowerCase().contains("camera");
            int scene = isCamera ? SCENE_CAMERA_OPEN : (isCold ? SCENE_APP_LAUNCH_COLD : SCENE_APP_LAUNCH_WARM);
            Bundle bundle = new Bundle();
            bundle.putString(KEY_PKG, pkg);
            bundle.putString(KEY_PACKAGE_NAME, pkg);
            bundle.putString(KEY_COMPONENT_NAME, component);
            bundle.putInt(KEY_UID, uid);
            bundle.putBoolean(KEY_IS_COLD, isCold);
            sceneBoostAcquire(scene, bundle);
        });
    }

    public void onSetFocusedApp(String pkg) {
        mFocusedPkg = pkg;
    }

    public String getFocusedPackage() {
        return mFocusedPkg;
    }

    private void applyDex2oatCpusetAdjustment(long durationMs) {
        if (durationMs == 0L) {
            mPerfEnhancer.writeCpusetCpus("dex2oat", mClusterManager.getRestrictedDex2oatCpusString());
            return;
        }
        if (durationMs == -1L) {
            mPerfEnhancer.writeCpusetCpus("dex2oat", mClusterManager.getBackgroundCpusString());
        }
    }

    public void onReportResumedActivity(int pid, String pkg, String component) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onReportResumedActivity(pid, pkg, component);
        });
    }

    public void onAppDied(String pkg, String component, int uid) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onAppDied(pkg, component, uid);
        });
    }

    public void onSetVisibility(String pkg, String component, int uid, boolean visible) {
        mWorkerHandler.post(() -> {
            mBoostAdjuster.onSetVisibility(pkg, component, uid, visible);
        });
    }

    public int scenarioBoost(int scenarioId, Bundle data) {
        return sceneBoostAcquire(scenarioId, data);
    }

    public void scenarioBoostRelease(int handle) {
        sceneBoostRelease(handle);
    }

    public int onSystemFling(int duration) {
        return onSystemFling(duration, mFocusedPkg);
    }

    public int onSystemFling(int duration, String pkg) {
        String targetPkg = pkg != null ? pkg : mFocusedPkg;
        Bundle bundle = new Bundle();
        bundle.putInt(AxDragoniteConstants.KEY_DURATION, duration);
        if (targetPkg != null) {
            bundle.putString(AxDragoniteConstants.KEY_PKG, targetPkg);
            bundle.putString(AxDragoniteConstants.KEY_PACKAGE_NAME, targetPkg);
        }
        return sceneBoostAcquire(SCENE_FLING, bundle);
    }

    public synchronized void updateGameModeBoost(boolean enable) {
        if (enable) {
            if (mGameModeHandle <= 0) {
                mGameModeHandle = sceneBoostAcquire(SCENE_GAME_MODE, null);
            }
        } else {
            if (mGameModeHandle > 0) {
                sceneBoostRelease(mGameModeHandle);
                mGameModeHandle = 0;
            }
        }
    }

    public void setGameMode(boolean enabled) {
        updateGameModeBoost(enabled);
    }

    private void applyGameMode(boolean enabled) {
        mPerfEnhancer.limitAxForeground(enabled);
        if (enabled) {
            mPerfEnhancer.applyCpuBoost(BOOST_LEVEL_HEAVY);
            return;
        }
        mPerfEnhancer.restoreCpuBoost();
    }

    public void setCameraForeground(boolean active) {
        mWorkerHandler.post(() -> {
            mPerfEnhancer.limitAxForeground(active);
            mBoostAdjuster.freezeBackgroundProcesses(active);
        });
    }

    public void dump(PrintWriter pw) {
        pw.println("AxDragonite Subsystem State:");
        pw.println("  Cores: " + mClusterManager.getNumCores() + ", Clusters: " + mClusterManager.getNumClusters());
        pw.println("  Little: 0x" + Long.toHexString(mClusterManager.getLittleMask()));
        pw.println("  Big: 0x" + Long.toHexString(mClusterManager.getBigMask()));
        pw.println("  Prime: 0x" + Long.toHexString(mClusterManager.getPrimeMask()));
        pw.println("  Boost: 0x" + Long.toHexString(mClusterManager.getBoostMask()));
        pw.println("  Active Leases: " + mActiveLeases.size());
        synchronized (mLock) {
            for (int i = 0; i < mActiveLeases.size(); i++) {
                LeaseRecord r = mActiveLeases.valueAt(i);
                pw.println("    Handle=" + r.handle + " Scene=" + r.sceneId + " PID=" + r.targetPid + " Pkg=" + r.packageName);
            }
        }
    }

    private void parseAndApplyParams(String params, LeaseRecord record) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (String part : params.split(PARAM_DELIMITER)) {
            applyParamOpcode(part, record);
        }
    }

    private void applyParamOpcode(String part, LeaseRecord record) {
        String[] kv = part.split(OPCODE_DELIMITER);
        if (kv.length != 2) return;
        int opcode = parseOpcode(kv[0]);
        if (opcode <= 0) return;
        for (String tidStr : kv[1].split(TID_DELIMITER)) {
            applyTidOpcode(opcode, tidStr.trim(), record);
        }
    }

    private int parseOpcode(String opcodeStr) {
        try {
            return Integer.parseInt(opcodeStr.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void applyTidOpcode(int opcode, String tidStr, LeaseRecord record) {
        int tid = parseTid(tidStr);
        if (tid <= 0) return;
        if (opcode == OPCODE_CPU_AFFINITY) {
            if (record != null) record.boostedTids.add(tid);
            mBoostAdjuster.setThreadAffinity(tid, AFFINITY_TYPE_BIG_CORES);
            return;
        }
        if (opcode == OPCODE_SCHED_PRIORITY) {
            if (record != null) record.boostedTids.add(tid);
            Process.setThreadPriority(tid, Process.THREAD_PRIORITY_URGENT_DISPLAY);
            return;
        }
        if (opcode == OPCODE_BOOST_SCHED) {
            if (record != null) record.boostedTids.add(tid);
            applyBoostSched(tid);
            return;
        }
        if (opcode == OPCODE_CPUCTL_TOP_APP) {
            mPerfEnhancer.writeNode(PATH_DEV_CPUCTL_TOP_APP_PROCS, String.valueOf(tid));
            return;
        }
        if (opcode == OPCODE_CPUSET_TOP_APP) {
            mPerfEnhancer.writeNode(PATH_DEV_CPUSET_TOP_APP_PROCS, String.valueOf(tid));
            return;
        }
        if (opcode == OPCODE_FREEZE_PROCESS) {
            mBoostAdjuster.freezeApp(0, tid);
        }
    }

    private void applyBoostSched(int tid) {
        try {
            Process.setThreadScheduler(tid, BOOST_SCHED_POLICY, BOOST_SCHED_PRIORITY);
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to set BOOST_SCHED for tid " + tid + ": " + t.getMessage());
        }
    }

    private int parseTid(String tidStr) {
        try {
            return Integer.parseInt(tidStr);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
