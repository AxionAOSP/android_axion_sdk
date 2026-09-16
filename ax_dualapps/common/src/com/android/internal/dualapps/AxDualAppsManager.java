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

package com.android.internal.dualapps;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import com.android.internal.app.ResolverActivity.ResolvedComponentInfo;
import com.android.internal.app.ResolverListController;
import com.android.internal.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AxDualAppsManager {
    public static final int DUAL_APPS_USER_ID = 999;
    public static final String ACTION_DUAL_SPACE_CREATED = "com.axion.intent.action.DUAL_PROFILE_CREATED";
    public static final String EXTRA_CHOOSER = "com.axion.intent.extra.FROM_CHOOSER";

    private static volatile AxDualAppsManager sInstance = null;

    private static final List<String> CHOOSER_ACTIVITIES = new ArrayList<>(Arrays.asList(
            "com.android.internal.app.ChooserActivity",
            "com.android.intentresolver.ChooserActivity",
            "com.android.internal.app.ResolverActivity"
    ));
    private static final List<String> CONTACTS_AUTHORITIES = new ArrayList<>(Arrays.asList(
            "contacts",
            "com.android.contacts",
            "com.android.contacts.files"
    ));

    private Handler mMainHandler;
    private DualSpaceCreationCallback mCreationCallback;
    private DualSpaceDeletionCallback mDeletionCallback;
    private DualAppDeletionCallback mAppDeletionCallback;
    private final IAxDualAppsReceiver mReceiver = new DualAppsReceiverStub();

    public static abstract class DualSpaceCreationCallback {
        public void onPrepareDone() {
        }

        public void onFailure(int errorCode) {
        }
    }

    public static abstract class DualSpaceDeletionCallback {
        public void onDeleteDone() {
        }

        public void onFailure() {
        }
    }

    public static abstract class DualAppDeletionCallback {
        public void onAppDeleted(String packageName, int code) {
        }
    }

    private final class DualAppsReceiverStub extends IAxDualAppsReceiver.Stub {
        @Override
        public void onDualAppDeleted(String packageName, int returnCode) {
            if (mAppDeletionCallback == null || mMainHandler == null) {
                return;
            }
            mMainHandler.post(() -> {
                if (mAppDeletionCallback != null) {
                    mAppDeletionCallback.onAppDeleted(packageName, returnCode);
                }
            });
        }

        @Override
        public void onDualSpaceDeletionStatus(boolean success, String reason) {
            if (mDeletionCallback == null || mMainHandler == null) {
                return;
            }
            mMainHandler.post(() -> {
                if (mDeletionCallback != null) {
                    if (success) {
                        mDeletionCallback.onDeleteDone();
                    } else {
                        mDeletionCallback.onFailure();
                    }
                }
            });
        }

        @Override
        public void onDualSpacePrepareStatus(int status, String reason) {
            if (mCreationCallback == null || mMainHandler == null) {
                return;
            }
            mMainHandler.post(() -> {
                if (mCreationCallback != null) {
                    if (status == 0) {
                        mCreationCallback.onPrepareDone();
                    } else {
                        mCreationCallback.onFailure(status);
                    }
                }
            });
        }
    }

    private AxDualAppsManager() {
        this.mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static AxDualAppsManager getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        synchronized (AxDualAppsManager.class) {
            if (sInstance == null) {
                sInstance = new AxDualAppsManager();
            }
            return sInstance;
        }
    }

    public static AxDualAppsManager get() {
        return getInstance();
    }

    private static IActivityManager getService() {
        return ActivityManager.getService();
    }

    public static boolean isDualAppsUserId(int userId) {
        if (userId == 0) {
            return false;
        }
        if (userId == DUAL_APPS_USER_ID) {
            return true;
        }
        IActivityManager am = getService();
        if (am == null) {
            return false;
        }
        try {
            return am.isCloneProfile(userId);
        } catch (RemoteException e) {
            return false;
        }
    }

    public static int getDualAppsUserId() {
        return DUAL_APPS_USER_ID;
    }

    public static int getOwnerOrCloneUserId(int userId) {
        if (userId != 0) {
            return 0;
        }
        IActivityManager am = getService();
        if (am == null) {
            return 0;
        }
        try {
            return am.getCloneProfileId();
        } catch (RemoteException e) {
            return 0;
        }
    }

    public static String getActionDualSpaceCreated() {
        return ACTION_DUAL_SPACE_CREATED;
    }

    public boolean isDualSpaceExists() {
        IActivityManager am = getService();
        if (am == null) {
            return false;
        }
        try {
            return am.isDualSpaceExists();
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean isDualSpaceInitialized() {
        IActivityManager am = getService();
        if (am == null) {
            return false;
        }
        try {
            return am.isDualSpaceInitialized();
        } catch (RemoteException e) {
            return false;
        }
    }

    public void prepareDualSpace(DualSpaceCreationCallback callback) {
        IActivityManager am = getService();
        if (am != null) {
            try {
                this.mCreationCallback = callback;
                am.prepareDualSpace(this.mReceiver);
            } catch (RemoteException e) {
                if (callback != null) {
                    callback.onFailure(-1);
                }
            }
        } else if (callback != null) {
            callback.onFailure(-1);
        }
    }

    public void clearDualSpace(DualSpaceDeletionCallback callback) {
        IActivityManager am = getService();
        if (am != null) {
            try {
                this.mDeletionCallback = callback;
                am.clearDualSpace(this.mReceiver);
            } catch (RemoteException e) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        } else if (callback != null) {
            callback.onFailure();
        }
    }

    public int installApp(String packageName, int uid) {
        IActivityManager am = getService();
        if (am == null) {
            return 0;
        }
        try {
            return am.installDualApp(packageName, uid);
        } catch (RemoteException e) {
            return 0;
        }
    }

    public void uninstallApp(String packageName, int uid, DualAppDeletionCallback callback) {
        IActivityManager am = getService();
        if (am != null) {
            try {
                this.mAppDeletionCallback = callback;
                am.uninstallDualApp(packageName, uid, this.mReceiver);
            } catch (RemoteException e) {
                if (callback != null) {
                    callback.onAppDeleted(packageName, -1);
                }
            }
        } else if (callback != null) {
            callback.onAppDeleted(packageName, -1);
        }
    }

    public List<ResolveInfo> queryRecommendedAppList(Intent intent, int flags) {
        IActivityManager am = getService();
        if (am == null) {
            return Collections.emptyList();
        }
        try {
            ParceledListSlice<ResolveInfo> slice = am.queryRecommendedAppList(intent, flags);
            return slice != null ? slice.getList() : Collections.emptyList();
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    public List<ResolveInfo> queryAvailableAppList(Intent intent, int flags) {
        IActivityManager am = getService();
        if (am == null) {
            return Collections.emptyList();
        }
        try {
            ParceledListSlice<ResolveInfo> slice = am.queryAvailableAppList(intent, flags);
            return slice != null ? slice.getList() : Collections.emptyList();
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    public List<String> queryAllowPackages() {
        IActivityManager am = getService();
        if (am == null) {
            return Collections.emptyList();
        }
        try {
            List<String> list = am.queryAllowPackages();
            return list != null ? list : Collections.emptyList();
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    public List<String> queryHiddenPackages() {
        IActivityManager am = getService();
        if (am == null) {
            return Collections.emptyList();
        }
        try {
            List<String> list = am.queryHiddenPackages();
            return list != null ? list : Collections.emptyList();
        } catch (RemoteException e) {
            return Collections.emptyList();
        }
    }

    public void shouldTintBadgeBg(Context context, Drawable drawable, int userId) {
        if (isDualAppsUserId(userId) && drawable != null) {
            drawable.setTint(context.getColor(R.color.dual_badge_icon_bg_color));
        }
    }

    public boolean shouldTintBadgeForNoBackground(Context context, Drawable drawable, int userId) {
        if (!isDualAppsUserId(userId) || drawable == null) {
            return false;
        }
        drawable.setTint(context.getColor(R.color.dual_badge_icon_bg_color));
        return true;
    }

    public boolean skipFixUris(Uri uri, int sourceUserId, int targetUserId) {
        if (uri != null && "content".equals(uri.getScheme()) && CONTACTS_AUTHORITIES.contains(uri.getAuthority())) {
            return (sourceUserId == DUAL_APPS_USER_ID && targetUserId == 0) || (sourceUserId == 0 && targetUserId == DUAL_APPS_USER_ID);
        }
        return false;
    }

    public boolean skipLoadBadgedIcon(Context context, ApplicationInfo appInfo) {
        return appInfo != null && isDualAppsUserId(context.getUserId()) && UserHandle.getUserId(appInfo.uid) == 0;
    }

    public boolean isAuthorityRedirectedForDualAppsProfile(String[] authorities, int userId) {
        if (!isDualAppsUserId(userId) || authorities == null || authorities.length == 0) {
            return false;
        }
        IActivityManager am = getService();
        if (am == null) {
            return false;
        }
        try {
            return am.isAuthorityRedirectedForDualAppsProfile(authorities);
        } catch (RemoteException e) {
            return false;
        }
    }

    public boolean isAuthorityRedirectedForDualAppsProfile(String authority, String[] authorities, int userId) {
        if (!isDualAppsUserId(userId)) {
            return false;
        }
        if (!TextUtils.isEmpty(authority)) {
            authorities = new String[]{authority};
        }
        return isAuthorityRedirectedForDualAppsProfile(authorities, userId);
    }

    public void checkFromChooser(Activity activity, Intent intent, int userId) {
        if ((userId == 0 || isDualAppsUserId(userId)) && activity != null) {
            if (CHOOSER_ACTIVITIES.contains(activity.getClass().getName())) {
                intent.putExtra(EXTRA_CHOOSER, true);
            }
        }
    }

    public List<ResolvedComponentInfo> overrideResolversForIntent(
            ResolverListController controller,
            boolean isBrowsing,
            boolean filterSelf,
            boolean queryAll,
            List<Intent> intents,
            String callingPackage,
            int userId) {
        if ((userId != 0 && !isDualAppsUserId(userId)) || !isDualSpaceInitialized()) {
            return null;
        }
        List<ResolvedComponentInfo> result = new ArrayList<>();
        result.addAll(controller.getResolversForIntentAsUser(isBrowsing, filterSelf, queryAll, intents, UserHandle.of(userId)));
        try {
            int targetUserId = getOwnerOrCloneUserId(userId);
            List<ResolvedComponentInfo> otherUserResults = new ArrayList<>();
            otherUserResults.addAll(controller.getResolversForIntentAsUser(isBrowsing, filterSelf, queryAll, intents, UserHandle.of(targetUserId)));
            if (!otherUserResults.isEmpty() && !TextUtils.isEmpty(callingPackage)) {
                List<String> hiddenPackages = queryHiddenPackages();
                for (ResolvedComponentInfo info : otherUserResults) {
                    String pkg = info.name.getPackageName();
                    if (!hiddenPackages.contains(pkg) && !callingPackage.equals(pkg)) {
                        result.add(info);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean shouldShowAlwaysButton(ResolveInfo resolveInfo, String callingPackage, Intent intent, UserHandle userHandle) {
        if (userHandle.getIdentifier() != DUAL_APPS_USER_ID || resolveInfo == null || resolveInfo.targetUserId != -2) {
            return false;
        }
        if (resolveInfo.activityInfo.packageName.equals(callingPackage)
                && (resolveInfo.userHandle == null || resolveInfo.userHandle.getIdentifier() == DUAL_APPS_USER_ID)) {
            return false;
        }
        if (resolveInfo.userHandle == null) {
            return true;
        }
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            boolean hasAudio = pm.checkPermission("android.permission.RECORD_AUDIO", resolveInfo.activityInfo.packageName, resolveInfo.userHandle.getIdentifier()) == 0;
            return hasAudio || !intent.getBooleanExtra("is_audio_capture_device", false);
        } catch (RemoteException e) {
            return true;
        }
    }
}
