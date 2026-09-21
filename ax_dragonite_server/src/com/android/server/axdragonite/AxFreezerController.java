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

import android.os.Process;
import android.util.Slog;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public final class AxFreezerController {
    private static final String TAG = "AxFreezerController";

    public static final String PATH_FREEZER_V2_FMT = "/sys/fs/cgroup/uid_%d/pid_%d/cgroup.freeze";
    public static final String PATH_FREEZER_V2_ROOT = "/sys/fs/cgroup";
    public static final String PATH_CGROUP_CONTROLLERS = "/sys/fs/cgroup/cgroup.controllers";
    public static final String PATH_FREEZER_V1_STATE = "/sys/fs/cgroup/freezer/freezer.state";
    public static final String PATH_FREEZER_V1_PROCS = "/sys/fs/cgroup/freezer/cgroup.procs";

    public static final String STATE_FROZEN_V2 = "1";
    public static final String STATE_THAWED_V2 = "0";
    public static final String STATE_FROZEN_V1 = "FROZEN";
    public static final String STATE_THAWED_V1 = "THAWED";

    public static final String PREFIX_UID = "uid_";
    public static final String PREFIX_PID = "pid_";
    public static final String FILE_NAME_FREEZE = "cgroup.freeze";

    public static final int INVALID_UID = -1;
    public static final int INVALID_PID = 0;
    public static final int FIRST_APP_UID = Process.FIRST_APPLICATION_UID;

    private boolean mIsCgroupV2 = false;
    private final Map<Integer, String> mFrozenPidPaths = new HashMap<>();
    private final Object mLock = new Object();

    public AxFreezerController() {
        checkCgroupVersion();
    }

    private void checkCgroupVersion() {
        File cgroupControllers = new File(PATH_CGROUP_CONTROLLERS);
        mIsCgroupV2 = cgroupControllers.exists();
        Slog.i(TAG, "Initialized AxFreezerController (cgroup v2: " + mIsCgroupV2 + ")");
    }

    public void freezeBackgroundProcesses(boolean freeze) {
        freezeBackgroundProcesses(freeze, null);
    }

    public void freezeBackgroundProcesses(boolean freeze, Set<Integer> exemptPids) {
        synchronized (mLock) {
            if (freeze) {
                freezeAllBackgroundCgroupV2(exemptPids);
            } else {
                thawAllBackgroundCgroupV2();
            }
        }
    }

    public void freezeApp(int uid, int pid) {
        if (uid < FIRST_APP_UID || pid <= INVALID_PID) {
            return;
        }

        synchronized (mLock) {
            if (mIsCgroupV2) {
                String path = String.format(PATH_FREEZER_V2_FMT, uid, pid);
                if (writeNode(path, STATE_FROZEN_V2)) {
                    mFrozenPidPaths.put(pid, path);
                }
            } else {
                writeNode(PATH_FREEZER_V1_PROCS, String.valueOf(pid));
                writeNode(PATH_FREEZER_V1_STATE, STATE_FROZEN_V1);
                mFrozenPidPaths.put(pid, PATH_FREEZER_V1_STATE);
            }
        }
    }

    public void unfreezeApp(int uid, int pid) {
        if (uid < FIRST_APP_UID || pid <= INVALID_PID) {
            return;
        }

        synchronized (mLock) {
            if (mIsCgroupV2) {
                String path = mFrozenPidPaths.remove(pid);
                if (path == null) {
                    path = String.format(PATH_FREEZER_V2_FMT, uid, pid);
                }
                writeNode(path, STATE_THAWED_V2);
            } else {
                writeNode(PATH_FREEZER_V1_STATE, STATE_THAWED_V1);
                mFrozenPidPaths.remove(pid);
            }
        }
    }

    private void freezeAllBackgroundCgroupV2(Set<Integer> exemptPids) {
        File cgroupRoot = new File(PATH_FREEZER_V2_ROOT);
        File[] uidDirs = cgroupRoot.listFiles();
        if (uidDirs == null) {
            return;
        }

        for (File uidDir : uidDirs) {
            if (!uidDir.isDirectory() || !uidDir.getName().startsWith(PREFIX_UID)) {
                continue;
            }
            int uid = parseIdFromPrefix(uidDir.getName(), PREFIX_UID);
            if (uid < FIRST_APP_UID) {
                continue;
            }
            freezeUidPidDirs(uidDir, exemptPids);
        }
    }

    private void freezeUidPidDirs(File uidDir, Set<Integer> exemptPids) {
        File[] pidDirs = uidDir.listFiles();
        if (pidDirs == null) {
            return;
        }
        for (File pidDir : pidDirs) {
            if (!pidDir.isDirectory() || !pidDir.getName().startsWith(PREFIX_PID)) {
                continue;
            }
            int pid = parseIdFromPrefix(pidDir.getName(), PREFIX_PID);
            if (pid <= 0 || (exemptPids != null && exemptPids.contains(pid))) {
                continue;
            }
            File freezeFile = new File(pidDir, FILE_NAME_FREEZE);
            if (freezeFile.exists() && writeNode(freezeFile.getAbsolutePath(), STATE_FROZEN_V2)) {
                mFrozenPidPaths.put(pid, freezeFile.getAbsolutePath());
            }
        }
    }

    private int parseIdFromPrefix(String name, String prefix) {
        try {
            return Integer.parseInt(name.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void thawAllBackgroundCgroupV2() {
        for (String path : mFrozenPidPaths.values()) {
            writeNode(path, STATE_THAWED_V2);
        }
        mFrozenPidPaths.clear();
    }

    private static boolean writeNode(String path, String value) {
        if (path == null || value == null) {
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            return false;
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(value.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
