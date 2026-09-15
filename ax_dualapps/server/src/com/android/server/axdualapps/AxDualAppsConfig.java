package com.android.server.axdualapps;

import android.app.AppOpsManager;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.PackageOps;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.IndentingPrintWriter;
import com.android.internal.R;
import com.android.server.appop.AppOpsService;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AxDualAppsConfig {
    public static final int OP_DUAL_APPS = 1001;
    public static final String PROP_DEBUG = "persist.sys.dualapps.debug";

    private Context mContext;
    private AppOpsService mAppOpsService;

    private final List<String> mBasicPackages = new ArrayList<>();
    private final List<String> mSupportPackages = new ArrayList<>();
    private final List<String> mAvailableBlacklist = new ArrayList<>();
    private final List<String> mFilterProfilePackages = new ArrayList<>();
    private final List<String> mSkipCurrentProfilePackages = new ArrayList<>();
    private final List<String> mProviderAuthorities = new ArrayList<>();
    private final List<String> mSkipChooserCallingPackages = new ArrayList<>();
    private final List<String> mSkipChooserCallerNullActivityNames = new ArrayList<>();
    private final List<String> mSkipChooserClazzNames = new ArrayList<>();
    private final List<String> mSkipChooserActionMainList = new ArrayList<>();
    private final List<String> mSkipChooserCustomActionList = new ArrayList<>();
    private final HashMap<String, ArrayList<String>> mSkipChooserActionWithClazzMap = new HashMap<>();

    private final List<String> mHandledIntentActions = new ArrayList<>(Arrays.asList(
            Intent.ACTION_VIEW,
            "android.media.action.IMAGE_CAPTURE",
            Intent.ACTION_SEND,
            Intent.ACTION_PICK,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_INSERT,
            Intent.ACTION_INSERT_OR_EDIT,
            Intent.ACTION_SENDTO,
            Intent.ACTION_DIAL,
            Intent.ACTION_EDIT,
            Intent.ACTION_GET_CONTENT,
            Intent.ACTION_TRANSLATE,
            Intent.ACTION_PROCESS_TEXT,
            "android.intent.action.OPEN_DOCUMENT",
            "android.media.action.VIDEO_CAPTURE"
    ));

    public AxDualAppsConfig(Context context, AppOpsService appOpsService) {
        this.mContext = context;
        this.mAppOpsService = appOpsService;
        loadDefaultResources();
    }

    public static boolean isDebugLoggingEnabled() {
        return SystemProperties.getBoolean(PROP_DEBUG, false);
    }

    private void loadDefaultResources() {
        Resources res = this.mContext.getResources();
        Collections.addAll(this.mBasicPackages, res.getStringArray(R.array.config_dualAppsBasicPackages));
        Collections.addAll(this.mSupportPackages, res.getStringArray(R.array.config_dualAppsSupportPackages));
        Collections.addAll(this.mAvailableBlacklist, res.getStringArray(R.array.config_dualAppsAvailableBlacklist));
        Collections.addAll(this.mFilterProfilePackages, res.getStringArray(R.array.config_dualAppsFilterCrossProfilePackages));
        Collections.addAll(this.mSkipCurrentProfilePackages, res.getStringArray(R.array.config_dualAppsSkipCurrentProfilePackages));
        Collections.addAll(this.mProviderAuthorities, res.getStringArray(R.array.config_dualAppsProviderAuthorityConfig));
        Collections.addAll(this.mSkipChooserCallingPackages, res.getStringArray(R.array.config_dualAppsSkipChooserCallingPackages));
        Collections.addAll(this.mSkipChooserCallerNullActivityNames, res.getStringArray(R.array.config_dualAppsSkipChooserCallerNullActivityNames));
        Collections.addAll(this.mSkipChooserClazzNames, res.getStringArray(R.array.config_dualAppsSkipChooserClazzNames));
        Collections.addAll(this.mSkipChooserActionMainList, res.getStringArray(R.array.config_dualAppsSkipChooserActionMainList));
        Collections.addAll(this.mSkipChooserCustomActionList, res.getStringArray(R.array.config_dualAppsSkipChooserCustomActionList));

        String[] actionMapEntries = res.getStringArray(R.array.config_dualAppsSkipChooserCustomActionMap);
        int i = 0;
        while (i < actionMapEntries.length) {
            String entry = actionMapEntries[i];
            if (entry.startsWith("action:")) {
                String actionName = entry.substring(7);
                ArrayList<String> classes = new ArrayList<>();
                int j = i + 1;
                while (j < actionMapEntries.length) {
                    String next = actionMapEntries[j];
                    if (next.startsWith("action:")) {
                        break;
                    }
                    if (!TextUtils.isEmpty(next)) {
                        classes.add(next);
                    }
                    j++;
                }
                if (!classes.isEmpty()) {
                    this.mSkipChooserActionWithClazzMap.put(actionName, classes);
                }
                i = j - 1;
            }
            i++;
        }
    }

    public boolean isAppOpAllowed(String packageName, int callingUid) {
        if (this.mAppOpsService == null) {
            return false;
        }
        return this.mAppOpsService.checkOperation(OP_DUAL_APPS, UserHandle.getUid(0, callingUid), packageName) == AppOpsManager.MODE_ALLOWED;
    }

    public List<String> getEnabledPackageNames() {
        List<PackageOps> opsList = getEnabledPackageOps();
        if (opsList == null || opsList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (PackageOps pkgOps : opsList) {
            names.add(pkgOps.getPackageName());
        }
        return names;
    }

    public List<PackageOps> getEnabledPackageOps() {
        if (this.mAppOpsService == null) {
            return Collections.emptyList();
        }
        List<PackageOps> packagesForOps = this.mAppOpsService.getPackagesForOps(new int[]{OP_DUAL_APPS});
        if (packagesForOps == null) {
            return Collections.emptyList();
        }
        List<PackageOps> result = new ArrayList<>();
        for (PackageOps ops : packagesForOps) {
            if (UserHandle.getUserId(ops.getUid()) == 0) {
                for (OpEntry entry : ops.getOps()) {
                    if (entry.getMode() == AppOpsManager.MODE_ALLOWED) {
                        result.add(ops);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public void resetAllPackageOps() {
        if (this.mAppOpsService == null) {
            return;
        }
        List<PackageOps> opsList = getEnabledPackageOps();
        for (PackageOps ops : opsList) {
            this.mAppOpsService.setMode(OP_DUAL_APPS, ops.getUid(), ops.getPackageName(), AppOpsManager.MODE_IGNORED);
        }
    }

    public boolean isSupportPackage(String packageName) {
        synchronized (this.mSupportPackages) {
            return this.mSupportPackages.contains(packageName);
        }
    }

    public boolean isBasicPackage(String packageName) {
        synchronized (this.mBasicPackages) {
            return this.mBasicPackages.contains(packageName);
        }
    }

    public boolean isAvailableBlacklisted(String packageName) {
        synchronized (this.mAvailableBlacklist) {
            return this.mAvailableBlacklist.contains(packageName);
        }
    }

    public boolean isFilterProfilePackage(String packageName) {
        synchronized (this.mFilterProfilePackages) {
            return this.mFilterProfilePackages.contains(packageName);
        }
    }

    public boolean isSkipCurrentProfilePackage(String packageName) {
        synchronized (this.mSkipCurrentProfilePackages) {
            return this.mSkipCurrentProfilePackages.contains(packageName);
        }
    }

    public boolean isSkipChooserCallingPackage(String packageName) {
        synchronized (this.mSkipChooserCallingPackages) {
            return this.mSkipChooserCallingPackages.contains(packageName);
        }
    }

    public boolean isSkipChooserCallerNullActivity(String activityName) {
        synchronized (this.mSkipChooserCallerNullActivityNames) {
            return this.mSkipChooserCallerNullActivityNames.contains(activityName);
        }
    }

    public boolean isSkipChooserClazz(String className) {
        synchronized (this.mSkipChooserClazzNames) {
            return this.mSkipChooserClazzNames.contains(className);
        }
    }

    public boolean isSkipChooserActionMain(String packageName) {
        synchronized (this.mSkipChooserActionMainList) {
            return this.mSkipChooserActionMainList.contains(packageName);
        }
    }

    public boolean isSkipChooserCustomAction(String action) {
        synchronized (this.mSkipChooserCustomActionList) {
            return this.mSkipChooserCustomActionList.contains(action);
        }
    }

    public boolean isSkipChooserActionWithClazz(String action, String className) {
        if (TextUtils.isEmpty(action) || TextUtils.isEmpty(className)) {
            return false;
        }
        synchronized (this.mSkipChooserActionWithClazzMap) {
            ArrayList<String> list = this.mSkipChooserActionWithClazzMap.get(action);
            return list != null && list.contains(className);
        }
    }

    public boolean isAuthorityWhitelisted(String[] authorities) {
        if (authorities == null || authorities.length == 0) {
            return false;
        }
        synchronized (this.mProviderAuthorities) {
            for (String auth : authorities) {
                if (this.mProviderAuthorities.contains(auth)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isDualAppEnabled(String packageName) {
        return getEnabledPackageNames().contains(packageName);
    }

    public boolean shouldHandleIntentActivities(Intent intent) {
        return intent != null && this.mHandledIntentActions.contains(intent.getAction());
    }

    public void addBasicPackages(Set<String> set) {
        synchronized (this.mBasicPackages) {
            set.addAll(this.mBasicPackages);
        }
    }

    public void addBlacklistPackages(Set<String> set) {
        synchronized (this.mAvailableBlacklist) {
            set.addAll(this.mAvailableBlacklist);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw);
        synchronized (this.mSupportPackages) {
            dumpList(ipw, "Support packages:", this.mSupportPackages);
        }
        dumpList(ipw, "Enabled list:", getEnabledPackageNames());
        synchronized (this.mBasicPackages) {
            dumpList(ipw, "Basic packages:", this.mBasicPackages);
        }
        synchronized (this.mAvailableBlacklist) {
            dumpList(ipw, "Available blacklist:", this.mAvailableBlacklist);
        }
        synchronized (this.mFilterProfilePackages) {
            dumpList(ipw, "Filter cross profile packages:", this.mFilterProfilePackages);
        }
        synchronized (this.mSkipCurrentProfilePackages) {
            dumpList(ipw, "Skip current profile packages:", this.mSkipCurrentProfilePackages);
        }
        synchronized (this.mProviderAuthorities) {
            dumpList(ipw, "Provider authority whitelist:", this.mProviderAuthorities);
        }
        ipw.println("Skip Chooser rules:");
        ipw.increaseIndent();
        synchronized (this.mSkipChooserCallerNullActivityNames) {
            dumpList(ipw, "Caller null activity names:", this.mSkipChooserCallerNullActivityNames);
        }
        synchronized (this.mSkipChooserCallingPackages) {
            dumpList(ipw, "Calling packages:", this.mSkipChooserCallingPackages);
        }
        synchronized (this.mSkipChooserClazzNames) {
            dumpList(ipw, "Clazz names:", this.mSkipChooserClazzNames);
        }
        synchronized (this.mSkipChooserActionMainList) {
            dumpList(ipw, "Action main list:", this.mSkipChooserActionMainList);
        }
        synchronized (this.mSkipChooserCustomActionList) {
            dumpList(ipw, "Custom action list:", this.mSkipChooserCustomActionList);
        }
        ipw.println("Action with clazz name map:");
        synchronized (this.mSkipChooserActionWithClazzMap) {
            for (Map.Entry<String, ArrayList<String>> entry : this.mSkipChooserActionWithClazzMap.entrySet()) {
                ipw.increaseIndent();
                dumpList(ipw, entry.getKey(), entry.getValue());
                ipw.decreaseIndent();
            }
        }
        ipw.decreaseIndent();
    }

    private void dumpList(IndentingPrintWriter ipw, String header, List<String> list) {
        ipw.println(header);
        ipw.increaseIndent();
        for (String item : list) {
            ipw.println(item);
        }
        ipw.decreaseIndent();
        ipw.println();
    }
}
