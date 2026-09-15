package com.android.server.axdualapps;

import android.app.admin.DevicePolicyManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.appop.AppOpsService;
import com.android.server.pm.CrossProfileDomainInfo;
import com.android.server.pm.UserTypeDetails;
import com.android.server.wm.ActivityTaskSupervisor;
import com.android.server.wm.AxDualAppsWmHelper;

import com.axion.dualapps.IAxDualAppsReceiver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AxDualAppsService extends SystemService {
    public static final String TAG = "AxDualAppsService";
    public static final int DUAL_APPS_USER_ID = 999;
    public static final int SYSTEM_UID = 1000;

    private static volatile AxDualAppsService sInstance = null;

    private AxDualAppsWmHelper mWmHelper;
    private AxDualUserHelper mUserHelper;
    private AxDualAppsConfig mConfig;
    private final Object mLock = new Object();

    public AxDualAppsService(Context context) {
        super(context);
        sInstance = this;
        this.mWmHelper = new AxDualAppsWmHelper();
        this.mUserHelper = new AxDualUserHelper(context);
    }

    public static AxDualAppsService get() {
        return sInstance;
    }

    public static AxDualAppsService getInstance() {
        return sInstance;
    }

    public static boolean isDualAppsUid(ApplicationInfo appInfo) {
        return appInfo != null && UserHandle.getUserId(appInfo.uid) == DUAL_APPS_USER_ID;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_SYSTEM_SERVICES_READY) {
            AppOpsService appOps = (AppOpsService) ServiceManager.getService(Context.APP_OPS_SERVICE);
            if (this.mConfig == null) {
                this.mConfig = new AxDualAppsConfig(getContext(), appOps);
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (this.mUserHelper != null) {
            this.mUserHelper.dump(fd, pw, args);
        }
        if (this.mConfig != null) {
            this.mConfig.dump(fd, pw, args);
        }
    }

    public void clearDualSpace(IAxDualAppsReceiver receiver) {
        synchronized (this.mLock) {
            if (this.mUserHelper != null) {
                this.mUserHelper.clearDualSpace(receiver);
            }
        }
    }

    public void prepareDualSpace(IAxDualAppsReceiver receiver) {
        synchronized (this.mLock) {
            if (this.mUserHelper != null) {
                this.mUserHelper.prepareDualSpace(receiver);
            }
        }
    }

    public int installApp(String packageName, int callingUid) {
        return this.mUserHelper != null ? this.mUserHelper.installApp(packageName, callingUid) : 0;
    }

    public void uninstallApp(String packageName, int callingUid, IAxDualAppsReceiver receiver) {
        if (this.mUserHelper != null) {
            this.mUserHelper.uninstallApp(packageName, callingUid, receiver);
        }
    }

    public List<String> queryAllowPackages() {
        if (!isDualSpaceExists()) {
            return Collections.emptyList();
        }
        long identity = Binder.clearCallingIdentity();
        try {
            return this.mConfig != null ? this.mConfig.getEnabledPackageNames() : Collections.emptyList();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public List<String> queryHiddenPackages() {
        return !isDualSpaceExists() ? Collections.emptyList() : queryHiddenPackagesInternal();
    }

    public ParceledListSlice<ResolveInfo> queryAvailableAppList(Intent intent, int flags) {
        return queryAvailableAppListInternal(intent, flags);
    }

    public ParceledListSlice<ResolveInfo> queryRecommendedAppList(Intent intent, int flags) {
        return queryRecommendedAppListInternal(intent, flags);
    }

    private ParceledListSlice<ResolveInfo> queryAvailableAppListInternal(Intent intent, int flags) {
        long identity = Binder.clearCallingIdentity();
        try {
            PackageManagerInternal pmInternal = LocalServices.getService(PackageManagerInternal.class);
            if (pmInternal == null) {
                return new ParceledListSlice<>(Collections.emptyList());
            }
            List<ResolveInfo> activities = pmInternal.queryIntentActivities(intent, (String) null, 0L, 0, 0);
            List<ApplicationInfo> installedApps = pmInternal.getInstalledApplications(0L, DUAL_APPS_USER_ID, Binder.getCallingUid());
            List<ResolveInfo> result = new ArrayList<>();
            List<String> seenPackages = new ArrayList<>();
            for (ResolveInfo info : activities) {
                String pkg = info.activityInfo.packageName;
                boolean isSupport = this.mConfig != null && this.mConfig.isSupportPackage(pkg);
                boolean isBasic = this.mConfig != null && this.mConfig.isBasicPackage(pkg);
                boolean isBlacklisted = this.mConfig != null && this.mConfig.isAvailableBlacklisted(pkg);
                boolean matchesInstalled = installedApps.stream().filter(AxDualAppsService::isDualAppsUid).anyMatch(app -> pkg.equals(app.packageName));
                if (!isSupport && !isBasic && (!isBlacklisted || matchesInstalled)) {
                    if (!seenPackages.contains(pkg)) {
                        result.add(info);
                        seenPackages.add(pkg);
                    }
                }
            }
            return new ParceledListSlice<>(result);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private List<String> queryHiddenPackagesInternal() {
        Set<String> hidden = new HashSet<>();
        if (this.mConfig != null) {
            this.mConfig.addBasicPackages(hidden);
            this.mConfig.addBlacklistPackages(hidden);
        }
        return new ArrayList<>(hidden);
    }

    private ParceledListSlice<ResolveInfo> queryRecommendedAppListInternal(Intent intent, int flags) {
        long identity = Binder.clearCallingIdentity();
        try {
            PackageManagerInternal pmInternal = LocalServices.getService(PackageManagerInternal.class);
            if (pmInternal == null) {
                return new ParceledListSlice<>(Collections.emptyList());
            }
            List<ResolveInfo> activities = pmInternal.queryIntentActivities(intent, (String) null, 0L, 0, 0);
            List<ResolveInfo> result = new ArrayList<>();
            List<String> seenPackages = new ArrayList<>();
            for (ResolveInfo info : activities) {
                String pkg = info.activityInfo.packageName;
                if (this.mConfig != null && this.mConfig.isSupportPackage(pkg) && !seenPackages.contains(pkg)) {
                    result.add(info);
                    seenPackages.add(pkg);
                }
            }
            return new ParceledListSlice<>(result);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean isDualAppsUserId(int userId) {
        return this.mUserHelper != null && this.mUserHelper.isDualAppsUserId(userId);
    }

    public int getDualAppsUserId() {
        return DUAL_APPS_USER_ID;
    }

    public int findDualAppsUserId() {
        return this.mUserHelper != null ? this.mUserHelper.getCloneProfileId() : -10000;
    }

    public boolean hasDualFlags(int flags) {
        return this.mUserHelper != null && this.mUserHelper.hasDualFlags(flags);
    }

    public boolean isCallingRelation(int sourceUserId, int targetUserId) {
        if (isDualAppsUserId(sourceUserId) && targetUserId == 0) {
            return true;
        }
        return sourceUserId == 0 && isDualAppsUserId(targetUserId);
    }

    public void modifyDefaultTypeProfileClone(UserTypeDetails.Builder builder) {
        builder.setIconBadge(R.drawable.ic_corp_badge_color)
                .setBadgePlain(R.drawable.ic_corp_badge_no_background)
                .setBadgeNoBackground(R.drawable.ic_corp_badge_no_background)
                .setBadgeLabels(new int[]{R.string.dual_apps_profile_label_badge})
                .setBadgeColors(new int[]{R.color.dual_badge_icon_color})
                .setDarkThemeBadgeColors(new int[]{R.color.dual_badge_icon_color});
    }

    public boolean skipSyncAppOps(int userId, int callingUid) {
        return isDualAppsUserId(userId) && userId != UserHandle.getUserHandleForUid(callingUid).getIdentifier();
    }

    public boolean blockInstall(int userId, ArrayList<String> packages, PrintWriter pw) {
        if (!isDualAppsUserId(userId)) {
            return false;
        }
        if (userId != getDualAppsUserId()) {
            return false;
        }
        if (pw != null) {
            pw.println("Unable to install on cloned user");
        }
        return true;
    }

    public boolean shouldUninstallDuringUpdate(String packageName) {
        if (this.mConfig == null) {
            return false;
        }
        return !this.mConfig.isBasicPackage(packageName) && !this.mConfig.isDualAppEnabled(packageName);
    }

    public boolean installRedirectToOwner(String packageName, int userId, int callingUid) {
        if (!isDualAppsUserId(userId) || !isDualAppsUserId(callingUid)) {
            return false;
        }
        return "com.android.packageinstaller".equals(packageName) || "com.google.android.packageinstaller".equals(packageName);
    }

    public ResolveInfo resolveIntent(ActivityTaskSupervisor supervisor, Intent intent, String resolvedType, int callingUid, int realCallingPid) {
        return this.mWmHelper != null ? this.mWmHelper.resolveIntentFallback(supervisor, intent, resolvedType, callingUid, realCallingPid) : null;
    }

    public void overrideInstallablePackages(String userType, Set<String> packages) {
        if (UserManager.isUserTypeCloneProfile(userType) && this.mConfig != null) {
            packages.clear();
            this.mConfig.addBasicPackages(packages);
        }
    }

    public int overrideUserId(int userId, boolean isMatch) {
        return (userId != getDualAppsUserId() || isMatch) ? -10000 : 0;
    }

    public boolean installBlocked(String packageName, int userId) {
        if (isDualAppsUserId(userId) && userId == getDualAppsUserId()) {
            return this.mConfig != null && !this.mConfig.isBasicPackage(packageName);
        }
        return false;
    }

    public boolean installBlocked(String packageName, int callingUid, int targetUserId, UserInfo userInfo) {
        if (!userInfo.isCloneProfile()) {
            return false;
        }
        if (targetUserId != 0 && targetUserId != -1 && targetUserId != userInfo.id) {
            return true;
        }
        return this.mConfig != null && !this.mConfig.isBasicPackage(packageName);
    }

    public boolean updateBlocked(String packageName, int flags, int userId) {
        if (isDualAppsUserId(userId) && (flags & 1) != 0) {
            return this.mConfig != null && !this.mConfig.isBasicPackage(packageName);
        }
        return false;
    }

    public void onDualSpaceInitialized() {
        if (this.mUserHelper != null) {
            this.mUserHelper.onDualSpaceInitialized();
        }
    }

    public void onUserRemoved(int userId) {
        if (this.mUserHelper != null && this.mConfig != null) {
            this.mUserHelper.onUserRemoved(userId, this.mConfig);
        }
    }

    public boolean[] installExistingPackageCheck(String packageName, int userId, int callingUid) {
        boolean blocked = false;
        boolean allowed = false;
        if (!isDualAppsUserId(userId)) {
            blocked = false;
            allowed = false;
        } else if (callingUid == SYSTEM_UID) {
            allowed = true;
            blocked = false;
        } else if (this.mConfig != null && this.mConfig.isBasicPackage(packageName)) {
            blocked = false;
            allowed = false;
        } else {
            blocked = true;
            allowed = false;
        }
        return new boolean[]{blocked, allowed};
    }

    public boolean hiddenFromLauncher(String packageName, int userId) {
        if (!isDualAppsUserId(userId)) {
            return false;
        }
        if (this.mConfig == null) {
            return true;
        }
        if (this.mConfig.isBasicPackage(packageName)) {
            return true;
        }
        return !this.mConfig.isDualAppEnabled(packageName);
    }

    public boolean skipCrossProfileAppsTarget(String packageName, int userId) {
        if (isDualAppsUserId(userId) && this.mConfig != null) {
            return this.mConfig.isBasicPackage(packageName) || this.mConfig.isFilterProfilePackage(packageName);
        }
        return false;
    }

    public boolean skipCurrentProfileIntents(Intent intent, String packageName, int userId) {
        if (isDualAppsUserId(userId) && this.mConfig != null) {
            return this.mConfig.isSkipCurrentProfilePackage(packageName);
        }
        return false;
    }

    public boolean isWidgetProviderWhiteListed(String providerPackage, int userId) {
        if (!isDualAppsUserId(userId) || this.mConfig == null) {
            return false;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            DevicePolicyManagerInternal dpmi = LocalServices.getService(DevicePolicyManagerInternal.class);
            if (dpmi == null || dpmi.getProfileOwnerAsUser(userId) != null) {
                return false;
            }
            return this.mConfig.isDualAppEnabled(providerPackage);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean isAuthorityRedirectedForDualAppsProfile(String authority) {
        if (TextUtils.isEmpty(authority) || this.mConfig == null) {
            return false;
        }
        return this.mConfig.isAuthorityWhitelisted(new String[]{authority});
    }

    public boolean isAuthorityRedirectedForDualAppsProfile(String[] authorities) {
        return this.mConfig != null && this.mConfig.isAuthorityWhitelisted(authorities);
    }

    public boolean isAuthorityRedirectedForDualAppsProfile(String authority, int userId) {
        if (isDualAppsUserId(userId)) {
            return isAuthorityRedirectedForDualAppsProfile(authority);
        }
        return false;
    }

    public AxDualAppsWmHelper getWmHelper() {
        return this.mWmHelper;
    }

    public AxDualUserHelper getUserHelper() {
        return this.mUserHelper;
    }

    public AxDualAppsConfig getConfig() {
        return this.mConfig;
    }

    public Intent getUserUnlockIntentForDualApps(int userId) {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo homeResolve = getContext().getPackageManager().resolveActivity(homeIntent, 0);
            if (homeResolve == null || homeResolve.activityInfo == null) {
                return null;
            }
            Intent unlockIntent = new Intent(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
            unlockIntent.setPackage(homeResolve.activityInfo.packageName);
            unlockIntent.putExtra(Intent.EXTRA_USER, UserHandle.of(userId));
            unlockIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
            return unlockIntent;
        } catch (Exception e) {
            return null;
        }
    }

    public void overrideCombineActivitiesResult(int userId, List<ResolveInfo> activities, List<CrossProfileDomainInfo> crossDomains) {
        int dualUserId = getDualAppsUserId();
        if (crossDomains == null || crossDomains.isEmpty()) {
            return;
        }
        if ((userId == 0 || userId == dualUserId) && isDualSpaceExists()) {
            List<String> hidden = queryHiddenPackagesInternal();
            activities.removeIf(info -> info.userHandle != null && info.userHandle.getIdentifier() == dualUserId && hidden.contains(info.activityInfo.packageName));
        }
    }

    public void onPackageUninstalled(int uid, String packageName, Intent intent) {
        if (TextUtils.isEmpty(packageName) || (intent != null && intent.getBooleanExtra("is_privacy_hidden", false))) {
            return;
        }
        if (this.mUserHelper != null) {
            this.mUserHelper.onPackageUninstalled(uid, packageName);
        }
    }

    public int fixResolveInfoUserId(ActivityInfo activityInfo, int userId) {
        if (userId != getDualAppsUserId() || activityInfo == null || activityInfo.applicationInfo == null) {
            return -10000;
        }
        return UserHandle.getUserId(activityInfo.applicationInfo.uid);
    }

    public int adjustDeleteWithParentFlags(String packageName, int userId, int flags) {
        if (userId != getDualAppsUserId() || (flags & 17) == 0) {
            return flags;
        }
        return flags & (-18);
    }

    public boolean shouldHandleIntentActivities(Intent intent, int userId) {
        if (userId != getDualAppsUserId() || intent == null || this.mConfig == null) {
            return false;
        }
        return this.mConfig.shouldHandleIntentActivities(intent);
    }

    public boolean redirectToOwner(int callingUserId, int targetUid) {
        return callingUserId == getDualAppsUserId() && UserHandle.getUserId(targetUid) == 0;
    }

    public boolean isDualSpaceExists() {
        return this.mUserHelper != null && this.mUserHelper.isDualSpaceExists();
    }

    public boolean isDualSpaceInitialized() {
        return this.mUserHelper != null && this.mUserHelper.isDualSpaceInitialized();
    }
}
