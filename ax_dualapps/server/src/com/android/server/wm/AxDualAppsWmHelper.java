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

package com.android.server.wm;

import android.app.ProfilerInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.LocalServices;
import android.app.ActivityManagerInternal;
import com.android.server.wm.ActivityStarter;
import com.android.server.wm.ActivityTaskSupervisor;

import com.android.server.axdualapps.AxDualAppsConfig;
import com.android.server.axdualapps.AxDualAppsService;
import com.android.server.axdualapps.AxDualUserHelper;

import java.util.concurrent.ConcurrentHashMap;

public class AxDualAppsWmHelper {
    public static final String EXTRA_FROM_CHOOSER = "com.axion.intent.extra.FROM_CHOOSER";
    private static final String TAG = "AxDualAppsWmHelper";

    private ConcurrentHashMap<String, Integer> mSavedChooserUserIdMap = new ConcurrentHashMap<>();

    public AxDualAppsWmHelper() {
    }

    public static void overrideRequest(ActivityStarter.Request request, ActivityTaskSupervisor supervisor) {
        AxDualAppsService service = AxDualAppsService.get();
        if (service != null && service.getWmHelper() != null && service.getUserHelper() != null && service.getConfig() != null) {
            service.getWmHelper().overrideRequestIfNeeded(request, supervisor, service.getUserHelper(), service.getConfig());
        }
    }

    private int getSavedChooserActivityUserId(String targetPackage, String callingPackage) {
        try {
            String key = targetPackage + "@" + callingPackage;
            Integer savedUserId = this.mSavedChooserUserIdMap.get(key);
            if (savedUserId == null) {
                return -10000;
            }
            this.mSavedChooserUserIdMap.remove(key);
            return savedUserId.intValue();
        } catch (Exception e) {
            return -10000;
        }
    }

    private void setSavedChooserActivityUserId(String targetPackage, String callingPackage, int userId) {
        try {
            this.mSavedChooserUserIdMap.put(callingPackage + "@" + targetPackage, Integer.valueOf(userId));
        } catch (Exception ignored) {
        }
    }

    public void overrideRequestIfNeeded(
            ActivityStarter.Request request,
            ActivityTaskSupervisor supervisor,
            AxDualUserHelper userHelper,
            AxDualAppsConfig config) {
        if (request == null) {
            return;
        }
        int callingUserId = UserHandle.getUserId(request.callingUid);
        boolean fromChooser = false;

        if (AxDualAppsConfig.isDebugLoggingEnabled()) {
            Slog.d(TAG, "overrideRequestIfNeeded: callingPkg=" + request.callingPackage
                    + ", callingUserId=" + callingUserId
                    + ", activityInfo=" + request.activityInfo);
        }

        if ((callingUserId != 0 && !userHelper.isDualAppsUserId(callingUserId)) || !userHelper.isDualSpaceExists()) {
            return;
        }
        ActivityInfo activityInfo = request.activityInfo;
        if (request.intent == null || activityInfo == null || !activityInfo.exported) {
            return;
        }
        if (TextUtils.isEmpty(activityInfo.packageName) || activityInfo.packageName.equals(request.callingPackage)) {
            return;
        }
        if (config.isSkipChooserCallingPackage(request.callingPackage)) {
            return;
        }
        if (request.caller == null && request.requestCode == 0 && !config.isSkipChooserCallerNullActivity(activityInfo.name)) {
            return;
        }
        ComponentName component = request.intent.getComponent();
        if (component != null) {
            if (config.isSkipChooserClazz(component.getClassName())) {
                return;
            }
            if (config.isSkipChooserActionWithClazz(request.intent.getAction(), component.getClassName())) {
                return;
            }
        }
        if (request.intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            return;
        }
        if (Intent.ACTION_MAIN.equals(request.intent.getAction())) {
            return;
        }
        if (TextUtils.isEmpty(request.callingPackage)) {
            return;
        }
        if (config.isSkipChooserCustomAction(request.intent.getAction())) {
            return;
        }
        if (request.intent.hasExtra(EXTRA_FROM_CHOOSER)) {
            request.intent.removeExtra(EXTRA_FROM_CHOOSER);
            fromChooser = true;
        }

        ActivityInfo targetInfo = request.activityInfo;
        if (!config.isAppOpAllowed(targetInfo.packageName, targetInfo.applicationInfo.uid)) {
            return;
        }

        if (fromChooser) {
            setSavedChooserActivityUserId(targetInfo.packageName, request.callingPackage, callingUserId);
            return;
        }

        ApplicationInfo appInfo = targetInfo.applicationInfo;
        int targetUserId = appInfo != null ? UserHandle.getUserId(appInfo.uid) : 0;
        int savedUserId = getSavedChooserActivityUserId(targetInfo.packageName, request.callingPackage);
        if (savedUserId != -10000) {
            if (savedUserId != targetUserId) {
                request.activityInfo = supervisor.resolveActivity(
                        request.intent,
                        supervisor.resolveIntent(request.intent, null, savedUserId, 0, request.callingUid, request.realCallingPid),
                        request.startFlags,
                        (ProfilerInfo) null
                );
            }
            return;
        }

        if (request.requestCode >= 0) {
            request.intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        }
        Intent chooserIntent = Intent.createChooser(request.intent, "");
        chooserIntent.putExtra(Intent.EXTRA_CALLING_PACKAGE, request.callingPackage);
        ActivityManagerInternal ami = LocalServices.getService(ActivityManagerInternal.class);
        if (ami != null) {
            ami.addCreatorToken(chooserIntent, request.callingPackage);
        }
        request.intent = chooserIntent;
        request.activityInfo = supervisor.resolveActivity(
                chooserIntent,
                supervisor.resolveIntent(chooserIntent, null, request.userId, 0, request.callingUid, request.realCallingPid),
                request.startFlags,
                (ProfilerInfo) null
        );
    }

    public static ResolveInfo resolveIntentFallback(ActivityTaskSupervisor supervisor, Intent intent, String resolvedType, int callingUid, int realCallingPid) {
        return supervisor.resolveIntent(intent, resolvedType, 0, 0, callingUid, realCallingPid);
    }
}
