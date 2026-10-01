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

import com.android.internal.dragonite.AxDragoniteConstants;
import static com.android.internal.dragonite.AxDragoniteConstants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * @hide
 */
public final class AxProcessTracker {

    private static final String[] COMPOSER_COMMANDS = {
        "vendor.qti.hardware.display.composer-service",
        "vendor.mediatek.hardware.composer-service",
        "android.hardware.graphics.composer",
        "composer-service"
    };

    private int mSystemUiPid = INVALID_PID;
    private int mLauncherPid = INVALID_PID;
    private int mActiveTopPid = INVALID_PID;
    private int mComposerPid = INVALID_PID;
    private volatile String mFocusedPkg;

    public void onProcessStarted(int pid, String pkg, String processName) {
        if (PKG_SYSTEMUI.equals(pkg) || PKG_SYSTEMUI.equals(processName)) {
            mSystemUiPid = pid;
        } else if (pkg != null && (pkg.contains(KEYWORD_LAUNCHER) || pkg.equals(AxActivityCustomizationUtil.PKG_LAUNCHER3))) {
            mLauncherPid = pid;
        }
    }

    public void onProcessKilled(int pid) {
        if (pid == mSystemUiPid) {
            mSystemUiPid = INVALID_PID;
        }
        if (pid == mLauncherPid) {
            mLauncherPid = INVALID_PID;
        }
        if (pid == mActiveTopPid) {
            mActiveTopPid = INVALID_PID;
        }
        if (pid == mComposerPid) {
            mComposerPid = INVALID_PID;
        }
    }

    public int getComposerPid() {
        if (mComposerPid > INVALID_PID) {
            return mComposerPid;
        }
        File proc = new File("/proc");
        String[] entries = proc.list();
        if (entries == null) {
            return INVALID_PID;
        }
        for (String entry : entries) {
            int pid = parseEntryPid(entry);
            if (pid <= INVALID_PID) {
                continue;
            }
            if (isComposerProcess(pid)) {
                mComposerPid = pid;
                return mComposerPid;
            }
        }
        return mComposerPid;
    }

    private int parseEntryPid(String entry) {
        if (entry == null || entry.isEmpty()) {
            return INVALID_PID;
        }
        char c = entry.charAt(0);
        if (c < '0' || c > '9') {
            return INVALID_PID;
        }
        try {
            return Integer.parseInt(entry);
        } catch (NumberFormatException ignored) {
            return INVALID_PID;
        }
    }

    private boolean isComposerProcess(int pid) {
        String cmdline = readProcCmdline(pid);
        if (cmdline == null) {
            return false;
        }
        for (String target : COMPOSER_COMMANDS) {
            if (cmdline.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private String readProcCmdline(int pid) {
        File file = new File("/proc/" + pid + "/cmdline");
        if (!file.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception ignored) {
            return null;
        }
    }

    public int onReportResumedActivity(int newTopPid, String pkg) {
        mFocusedPkg = pkg;
        int previousTopPid = mActiveTopPid;
        if (newTopPid > 0) {
            mActiveTopPid = newTopPid;
        }
        return previousTopPid;
    }

    public void onSetFocusedApp(String pkg) {
        mFocusedPkg = pkg;
    }

    public int getSystemUiPid() {
        return mSystemUiPid;
    }

    public int getLauncherPid() {
        return mLauncherPid;
    }

    public int getActiveTopPid() {
        return mActiveTopPid > INVALID_PID ? mActiveTopPid : mLauncherPid;
    }

    public String getFocusedPackage() {
        return mFocusedPkg;
    }
}
