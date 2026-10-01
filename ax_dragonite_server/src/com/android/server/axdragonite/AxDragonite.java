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

import com.android.internal.dragonite.AxDragoniteConstants;
import static com.android.internal.dragonite.AxDragoniteConstants.*;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * @hide
 */
public final class AxDragonite {
    private static final String TAG = AxDragoniteConstants.TAG;

    private static AxDragonite sInstance;

    private final AxCpuClusterManager mClusterManager;
    private final AxPerfEnhancer mPerfEnhancer;
    private final AxNamedThreadAffinityFeature mAffinityFeature;
    private final AxUIBooster mUIBooster;
    private final AxFrameInsertManager mFrameInsertManager;
    private final AxPerfTraceManager mTraceManager;
    private final AxBoostAdjuster mBoostAdjuster;

    private final AxSceneRegistry mSceneRegistry;
    private final AxProcessTracker mProcessTracker;
    private final AxBoostSessionManager mSessionManager;
    private final AxOpcodeDispatcher mOpcodeDispatcher;

    private final HandlerThread mWorkerThread;
    private final Handler mWorkerHandler;

    private int mGameModeHandle = 0;

    public static synchronized AxDragonite getInstance() {
        if (sInstance == null) {
            sInstance = new AxDragonite();
        }
        return sInstance;
    }

    private AxDragonite() {
        mClusterManager = AxCpuClusterManager.getInstance();
        mProcessTracker = new AxProcessTracker();
        mPerfEnhancer = new AxPerfEnhancer(mClusterManager);
        mAffinityFeature = new AxNamedThreadAffinityFeature(mClusterManager, mProcessTracker);
        mUIBooster = new AxUIBooster(mPerfEnhancer, mClusterManager);
        mFrameInsertManager = new AxFrameInsertManager();
        mTraceManager = new AxPerfTraceManager();
        mBoostAdjuster = new AxBoostAdjuster(mPerfEnhancer, mClusterManager, this::onInputBoostExpired);

        mSceneRegistry = new AxSceneRegistry();
        mSessionManager = new AxBoostSessionManager();
        mOpcodeDispatcher = new AxOpcodeDispatcher(mPerfEnhancer, mBoostAdjuster, mAffinityFeature);

        mWorkerThread = new HandlerThread(WORKER_THREAD_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
        postWorkerTask(() -> {
            mAffinityFeature.applyNamedAffinityForPid(Process.myPid());
            mProcessTracker.getComposerPid();
        });

        Slog.i(TAG, "AxDragonite subsystem initialized");
    }

    private void postWorkerTask(Runnable task) {
        mWorkerHandler.post(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Slog.e(TAG, "Uncaught error in AxDragonite worker task", t);
            }
        });
    }

    public record BoostRequest(
            int targetPid,
            String packageName,
            int durationMs,
            int handle,
            String params
    ) {
        public static BoostRequest parse(Bundle data, int defaultTimeout, int fallbackPid) {
            if (data == null) {
                return new BoostRequest(fallbackPid, null, defaultTimeout, INVALID_HANDLE, null);
            }
            int pid = data.getInt(KEY_TARGET_PID, data.getInt(KEY_PID, fallbackPid));
            if (pid <= 0) {
                pid = fallbackPid;
            }
            String pkg = data.getString(KEY_PACKAGE, data.getString(KEY_PACKAGE_NAME, data.getString(KEY_PKG, null)));
            int reqDur = data.getInt(KEY_DURATION, INVALID_DURATION);
            int duration = reqDur > 0 ? reqDur : defaultTimeout;
            int handle = data.getInt(KEY_HANDLE, INVALID_HANDLE);
            String params = data.getString(KEY_PARAMS);
            return new BoostRequest(pid, pkg, duration, handle, params);
        }
    }

    public int sceneBoostAcquire(int sceneId, Bundle data) {
        try {
            AxSceneRegistry.ScenarioConfig config = mSceneRegistry.getConfig(sceneId);
            BoostRequest request = BoostRequest.parse(data, config.defaultTimeoutMs(), Binder.getCallingPid());

            if (request.handle() > 0 && mSessionManager.extendSession(request.handle(), request.durationMs())) {
                return request.handle();
            }

            int duration = mSceneRegistry.resolveDuration(sceneId, request.packageName(), request.durationMs());
            if (request.targetPid() > 0 && request.targetPid() == mProcessTracker.getLauncherPid() && duration < DURATION_LAUNCHER_GESTURE_MS) {
                duration = DURATION_LAUNCHER_GESTURE_MS;
            }

            int handle = mSessionManager.startSession(
                    sceneId, Binder.getCallingPid(), Binder.getCallingUid(),
                    request.packageName(), request.targetPid(), config, duration,
                    this::sceneBoostRelease
            );

            postWorkerTask(() -> {
                mTraceManager.reportSceneAcquire(sceneId, handle);
                mFrameInsertManager.onSceneStart(sceneId);

                mSessionManager.forEachActiveSession(s -> {
                    if (s.handle() == handle) {
                        mOpcodeDispatcher.parseAndApply(request.params(), s);
                    }
                });

                mPerfEnhancer.applyCpuBoost(config.boostLevel());
                if (mSceneRegistry.isTransitionScene(sceneId)) {
                    applyAnimationBoost(sceneId, request.targetPid(), config.boostLevel(), true);
                    mPerfEnhancer.restrictBackgroundCpusets(true);
                    if (request.targetPid() > 0) {
                        mAffinityFeature.applyNamedAffinityForPid(request.targetPid());
                    }
                } else if (config.boostRenderThread() && request.targetPid() > 0) {
                    mUIBooster.boostProcess(request.targetPid(), config.boostLevel());
                    mAffinityFeature.applyNamedAffinityForPid(request.targetPid());
                }

                if (config.pinKswapd()) {
                    mPerfEnhancer.pinKswapd(mClusterManager.getLittleMask());
                }

                if (sceneId == SCENE_APP_LAUNCH_COLD || sceneId == SCENE_CAMERA_OPEN || sceneId == SCENE_AX_APP_START) {
                    Set<Integer> exempt = new HashSet<>();
                    if (request.targetPid() > 0) exempt.add(request.targetPid());
                    int lPid = mProcessTracker.getLauncherPid();
                    int sPid = mProcessTracker.getSystemUiPid();
                    if (lPid > 0) exempt.add(lPid);
                    if (sPid > 0) exempt.add(sPid);
                    mBoostAdjuster.freezeBackgroundProcesses(true, exempt);
                }

                if (sceneId == SCENE_GAME_MODE) {
                    applyGameMode(true);
                } else if (sceneId == SCENE_CAMERA_OPEN) {
                    mPerfEnhancer.limitAxForeground(true);
                } else if (sceneId == SCENE_DATA_LOADING) {
                    mPerfEnhancer.applyCpuBoost(AxDragoniteConstants.BOOST_LEVEL_HEAVY);
                    if (request.targetPid() > 0) {
                        mAffinityFeature.promoteLoaderThreadsForPid(request.targetPid());
                        mPerfEnhancer.migrateToTopAppCgroup(request.targetPid());
                    }
                }

                if (mSceneRegistry.isSurfaceFlingerBoostScene(sceneId) || config.boostRenderThread()) {
                    boolean isUrgentComposition = mSceneRegistry.isShadeScene(sceneId)
                            || mSceneRegistry.isTransitionScene(sceneId)
                            || (request.targetPid() > 0 && request.targetPid() == mProcessTracker.getLauncherPid());
                    mPerfEnhancer.sendSurfaceFlingerBoost(true, isUrgentComposition);
                    mAffinityFeature.boostDisplayComposer();
                }
            });

            return handle;
        } catch (Throwable t) {
            Slog.e(TAG, "Uncaught error in sceneBoostAcquire for scene " + sceneId, t);
            return INVALID_HANDLE;
        }
    }

    public void sceneBoostRelease(int handle) {
        try {
            AxBoostSessionManager.BoostSession session = mSessionManager.endSession(handle);
            if (session == null) {
                return;
            }
            postWorkerTask(() -> handleSessionRelease(session, handle));
        } catch (Throwable t) {
            Slog.e(TAG, "Uncaught error in sceneBoostRelease for handle " + handle, t);
        }
    }

    public void onWindowsDrawn(String pkg, int pid) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        postWorkerTask(() -> {
            AxBoostSessionManager.BoostSession session =
                    mSessionManager.endSessionForPackage(pkg, SCENE_APP_LAUNCH_WARM);
            if (session == null) {
                session = mSessionManager.endSessionForPackage(pkg, SCENE_APP_LAUNCH_COLD);
            }
            if (session != null) {
                handleSessionRelease(session, session.handle());
            }
        });
    }

    private void handleSessionRelease(AxBoostSessionManager.BoostSession session, int handle) {
        mTraceManager.reportSceneRelease(session.sceneId(), handle);
        mFrameInsertManager.onSceneEnd(session.sceneId());

        if (mSceneRegistry.isTransitionScene(session.sceneId())) {
            applyAnimationBoost(session.sceneId(), session.targetPid(), session.config().boostLevel(), false);
            if (!mSessionManager.hasActiveTransitionScene(mSceneRegistry)) {
                mPerfEnhancer.restrictBackgroundCpusets(false);
                if (session.targetPid() > 0) {
                    mAffinityFeature.resetAffinityForPid(session.targetPid());
                }
            }
        } else if (session.config().boostRenderThread() && session.targetPid() > 0) {
            mUIBooster.restoreProcess(session.targetPid());
        }

        if (session.sceneId() == SCENE_DATA_LOADING && session.targetPid() > 0) {
            mAffinityFeature.restoreLoaderThreadsForPid(session.targetPid());
        }

        for (int tid : session.boostedTids()) {
            if (tid <= 0) {
                continue;
            }
            try {
                Process.setThreadScheduler(tid, Process.SCHED_OTHER, 0);
                Process.setThreadPriority(tid, Process.THREAD_PRIORITY_DEFAULT);
            } catch (Throwable ignored) {
            }
        }

        if (session.sceneId() == SCENE_APP_LAUNCH_COLD || session.sceneId() == SCENE_CAMERA_OPEN || session.sceneId() == SCENE_AX_APP_START) {
            mBoostAdjuster.freezeBackgroundProcesses(false);
        }

        if (session.sceneId() == SCENE_GAME_MODE) {
            applyGameMode(false);
        } else if (session.sceneId() == SCENE_CAMERA_OPEN) {
            mPerfEnhancer.limitAxForeground(false);
        }

        int maxBoostLevel = mSessionManager.getActiveMaxBoostLevel();
        if (maxBoostLevel > BOOST_LEVEL_NONE) {
            mPerfEnhancer.applyCpuBoost(maxBoostLevel);
        } else {
            mPerfEnhancer.restoreCpuBoost();
        }

        if (!mSessionManager.hasActivePinKswapd()) {
            mPerfEnhancer.pinKswapd(mClusterManager.getAllMask());
        }

        if (!mSessionManager.hasActiveSurfaceFlingerBoost(mSceneRegistry)) {
            mPerfEnhancer.sendSurfaceFlingerBoost(false);
            mAffinityFeature.restoreDisplayComposer();
        }
    }

    public void animationBoost(int pid, long boostDurationMs) {
        Bundle bundle = new Bundle();
        bundle.putInt(AxDragoniteConstants.KEY_PID, pid);
        bundle.putInt(AxDragoniteConstants.KEY_TARGET_PID, pid);
        bundle.putInt(AxDragoniteConstants.KEY_DURATION, (int) Math.min(boostDurationMs, AxDragoniteConstants.MAX_BOOST_DURATION_MS));
        sceneBoostAcquire(AxDragoniteConstants.SCENE_APP_EXIT_ANIM, bundle);
    }

    private void setProcessTransitionState(int pid, int boostLevel, boolean enable) {
        if (pid <= 0) {
            return;
        }
        if (enable) {
            mUIBooster.boostProcess(pid, boostLevel);
            mAffinityFeature.applyNamedAffinityForPid(pid);
        } else {
            mUIBooster.restoreProcess(pid);
        }
    }

    private enum SceneRole {
        LAUNCHER_INTERACTION,
        SHADE_INTERACTION,
        APP_TRANSITION,
        STANDALONE
    }

    private SceneRole getSceneRole(int sceneId, int targetPid, int sysUiPid, int launcherPid) {
        if (targetPid > INVALID_PID && targetPid == launcherPid) {
            return SceneRole.LAUNCHER_INTERACTION;
        }
        if (targetPid == sysUiPid || mSceneRegistry.isShadeScene(sceneId)) {
            return SceneRole.SHADE_INTERACTION;
        }
        if (mSceneRegistry.isTransitionScene(sceneId)) {
            return SceneRole.APP_TRANSITION;
        }
        return SceneRole.STANDALONE;
    }

    private void applyAnimationBoost(int sceneId, int targetPid, int boostLevel, boolean enable) {
        if (targetPid <= INVALID_PID) {
            return;
        }

        int sysUiPid = mProcessTracker.getSystemUiPid();
        int launcherPid = mProcessTracker.getLauncherPid();
        SceneRole role = getSceneRole(sceneId, targetPid, sysUiPid, launcherPid);

        setProcessTransitionState(targetPid, boostLevel, enable);

        switch (role) {
            case LAUNCHER_INTERACTION:
                handleLauncherInteraction(targetPid, sysUiPid, enable);
                break;
            case SHADE_INTERACTION:
                handleShadeInteraction(targetPid, sysUiPid, launcherPid, enable);
                break;
            case APP_TRANSITION:
                handleAppTransition(targetPid, sysUiPid, launcherPid, boostLevel, enable);
                break;
            case STANDALONE:
            default:
                break;
        }

        if (enable) {
            mAffinityFeature.applyNamedAffinityForPid(Process.myPid());
        }
    }

    private void handleLauncherInteraction(int launcherPid, int sysUiPid, boolean enable) {
        demoteBackgroundProcesses(launcherPid, sysUiPid, enable);
    }

    private void handleShadeInteraction(int targetPid, int sysUiPid, int launcherPid, boolean enable) {
        int topPid = mProcessTracker.getActiveTopPid();

        if (enable) {
            mAffinityFeature.applyNamedAffinityForPid(sysUiPid);

            if (topPid > INVALID_PID && topPid != sysUiPid) {
                mAffinityFeature.yieldPidToLittleCores(topPid);
            }
            if (launcherPid > INVALID_PID && launcherPid != sysUiPid && launcherPid != topPid) {
                mAffinityFeature.yieldPidToLittleCores(launcherPid);
            }
        } else {
            if (topPid > INVALID_PID && topPid != sysUiPid) {
                restoreProcessAffinity(topPid);
            }
            if (launcherPid > INVALID_PID && launcherPid != sysUiPid && launcherPid != topPid) {
                restoreProcessAffinity(launcherPid);
            }
            restoreProcessAffinity(sysUiPid);
        }
    }

    private void handleAppTransition(int targetPid, int sysUiPid, int launcherPid, int boostLevel, boolean enable) {
        if (sysUiPid > INVALID_PID && sysUiPid != targetPid) {
            setProcessTransitionState(sysUiPid, boostLevel, enable);
        }
        if (launcherPid > INVALID_PID && launcherPid != targetPid) {
            setProcessTransitionState(launcherPid, boostLevel, enable);
        }
        demoteBackgroundProcesses(targetPid, sysUiPid, enable);
    }

    private void demoteBackgroundProcesses(int primaryPid, int secondaryPid, boolean enable) {
        int topPid = mProcessTracker.getActiveTopPid();
        if (topPid > INVALID_PID && topPid != primaryPid && topPid != secondaryPid) {
            if (enable) {
                mAffinityFeature.yieldPidToLittleCores(topPid);
            } else {
                restoreProcessAffinity(topPid);
            }
        }
    }

    private void restoreProcessAffinity(int pid) {
        if (pid <= INVALID_PID) {
            return;
        }
        mAffinityFeature.resetAffinityForPid(pid);
        mAffinityFeature.applyNamedAffinityForPid(pid);
    }

    public boolean isSceneIdExist(int sceneId) {
        return mSceneRegistry.isSceneIdExist(sceneId);
    }

    public int getFlingSceneId(int velocity) {
        return mSceneRegistry.getFlingSceneId(velocity);
    }

    public int getFlingSceneId() {
        return getFlingSceneId(DEFAULT_FLING_VELOCITY);
    }

    public int getScrollSceneId() {
        return SCENE_SCROLL;
    }

    public int getAppLaunchSceneId() {
        return SCENE_APP_LAUNCH_COLD;
    }

    public void adjustCpusetCpus(String group, String cpus, long durationMs) {
        if ("dex2oat".equals(group) && cpus == null) {
            applyDex2oatCpusetAdjustment(durationMs);
            return;
        }
        if (group == null || !ALLOWED_CPUSET_GROUPS.contains(group)) {
            return;
        }
        if (cpus != null && !CPUSET_CPUS_PATTERN.matcher(cpus).matches()) {
            return;
        }
        mBoostAdjuster.adjustCpusetCpus(group, cpus, durationMs);
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

    public void onProcessForked(int pid, boolean isTopApp) {
        if (isTopApp) {
            mPerfEnhancer.migrateToTopAppCgroup(pid);
        }
    }

    public void inputBoost() {
        postWorkerTask(() -> {
            mBoostAdjuster.inputBoost();
            int topPid = mProcessTracker.getActiveTopPid();
            if (topPid > INVALID_PID) {
                mAffinityFeature.applyNamedAffinityForPid(topPid);
            }
        });
    }

    private void onInputBoostExpired() {
        postWorkerTask(() -> {
            if (!mSessionManager.hasActiveTransitionScene(mSceneRegistry)) {
                int topPid = mProcessTracker.getActiveTopPid();
                if (topPid > INVALID_PID) {
                    mAffinityFeature.resetAffinityForPid(topPid);
                }
            }
        });
    }

    public void onProcessStarted(int pid, String pkg, String processName, int uid) {
        onProcessStarted(pid, pkg, processName, uid, false);
    }

    public void onProcessStarted(int pid, String pkg, String processName, int uid, boolean isTopApp) {
        postWorkerTask(() -> handleProcessStarted(pid, pkg, processName, uid, isTopApp));
    }

    private void handleProcessStarted(int pid, String pkg, String processName, int uid, boolean isTopApp) {
        mProcessTracker.onProcessStarted(pid, pkg, processName);
        mBoostAdjuster.onProcessStarted(pid, pkg, processName, uid);

        final boolean hasActiveLaunch = mSessionManager.hasActiveSessionForPackage(pkg);
        if (!isTopApp && !hasActiveLaunch) {
            return;
        }

        mPerfEnhancer.migrateToTopAppCgroup(pid);
        mAffinityFeature.applyNamedAffinityForPid(pid);
        bindNewProcessToActiveSessions(pid, pkg);
    }

    private void bindNewProcessToActiveSessions(int pid, String pkg) {
        if (pkg == null) {
            return;
        }
        mSessionManager.forEachActiveSession(s -> {
            if (!mSceneRegistry.isTransitionScene(s.sceneId()) || !pkg.equals(s.packageName())) {
                return;
            }
            mUIBooster.boostProcess(pid, s.config().boostLevel());
            mSessionManager.updateSessionTargetPid(s.handle(), pid);
        });
    }

    public void onProcessKilled(int pid, String pkg) {
        postWorkerTask(() -> {
            mProcessTracker.onProcessKilled(pid);
            mBoostAdjuster.onProcessKilled(pid, pkg);
            mUIBooster.onProcessKilled(pid);
            mUIBooster.restoreProcess(pid);
            mAffinityFeature.resetAffinityForPid(pid);
        });
    }

    public void onActivityStart(String pkg, String component, int uid, boolean isCold) {
        onActivityStart(pkg, component, uid, isCold, -1);
    }

    public void onActivityStart(String pkg, String component, int uid, boolean isCold, int targetPid) {
        postWorkerTask(() -> {
            mBoostAdjuster.onActivityStart(pkg, component, uid, isCold);
            boolean isCamera = pkg != null && pkg.toLowerCase().contains(KEYWORD_CAMERA);
            int scene = isCamera ? SCENE_CAMERA_OPEN : (isCold ? SCENE_APP_LAUNCH_COLD : SCENE_APP_LAUNCH_WARM);
            Bundle bundle = new Bundle();
            bundle.putString(KEY_PKG, pkg);
            bundle.putString(KEY_PACKAGE_NAME, pkg);
            bundle.putBoolean(KEY_IS_COLD, isCold);
            bundle.putInt(KEY_UID, uid);
            if (targetPid > 0) {
                bundle.putInt(KEY_PID, targetPid);
                bundle.putInt(KEY_TARGET_PID, targetPid);
            }
            sceneBoostAcquire(scene, bundle);
        });
    }

    public void onSetFocusedApp(String pkg) {
        mProcessTracker.onSetFocusedApp(pkg);
    }

    public String getFocusedPackage() {
        return mProcessTracker.getFocusedPackage();
    }

    public void onReportResumedActivity(int pid, String pkg, String component) {
        int previousTopPid = mProcessTracker.onReportResumedActivity(pid, pkg);
        postWorkerTask(() -> {
            if (previousTopPid > 0 && previousTopPid != pid && previousTopPid != mProcessTracker.getSystemUiPid()) {
                mAffinityFeature.yieldPidToLittleCores(previousTopPid);
            }
            if (pid > 0 && pid != mProcessTracker.getSystemUiPid()) {
                mAffinityFeature.applyNamedAffinityForPid(pid);
            }
            AxActivityCustomizationUtil.handleActivityResumed(pid, pkg, component);
            mBoostAdjuster.onReportResumedActivity(pid, pkg, component);
        });
    }

    public void onAppDied(String pkg, String component, int uid) {
        postWorkerTask(() -> {
            mBoostAdjuster.onAppDied(pkg, component, uid);
        });
    }

    public void onSetVisibility(String pkg, String component, int uid, boolean visible) {
        postWorkerTask(() -> {
            mBoostAdjuster.onSetVisibility(pkg, component, uid, visible);
        });
    }

    public int onSystemFling(int duration) {
        return onSystemFling(duration, mProcessTracker.getFocusedPackage());
    }

    public int onSystemFling(int duration, String pkg) {
        String targetPkg = pkg != null ? pkg : mProcessTracker.getFocusedPackage();
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

    private void applyGameMode(boolean enabled) {
        mPerfEnhancer.limitAxForeground(enabled);
        if (enabled) {
            mPerfEnhancer.applyCpuBoost(BOOST_LEVEL_HEAVY);
            return;
        }
        mPerfEnhancer.restoreCpuBoost();
    }

    public void dump(PrintWriter pw) {
        pw.println("AxDragonite Subsystem State:");
        pw.println("  Cores: " + mClusterManager.getNumCores() + ", Clusters: " + mClusterManager.getNumClusters());
        pw.println("  Little: 0x" + Long.toHexString(mClusterManager.getLittleMask()));
        pw.println("  Big: 0x" + Long.toHexString(mClusterManager.getBigMask()));
        pw.println("  Prime: 0x" + Long.toHexString(mClusterManager.getPrimeMask()));
        pw.println("  Boost: 0x" + Long.toHexString(mClusterManager.getBoostMask()));
        pw.println("  Launcher PID: " + mProcessTracker.getLauncherPid() + ", SystemUI PID: " + mProcessTracker.getSystemUiPid());
        mSessionManager.dump(pw);
    }
}
