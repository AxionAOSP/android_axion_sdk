package com.android.server.axdualapps;

import android.app.ActivityManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.AppOpsManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IProgressListener;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.internal.app.IAppOpsCallback;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerService;
import com.android.server.storage.DeviceStorageMonitorInternal;

import com.axion.dualapps.IAxDualAppsReceiver;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class AxDualUserHelper {
    public static final String PROFILE_NAME = "DualApps";
    public static final int DUAL_APPS_USER_ID = 999;
    public static final int CLONE_PROFILE_FLAGS = 67108864;
    public static final int OP_DUAL_APPS = 1001;
    public static final String ACTION_DUAL_PROFILE_CREATED = "com.axion.intent.action.DUAL_PROFILE_CREATED";

    private static final String TAG = "AxDualAppsService";

    private Context mContext;
    private DualSpacePrepareCallbackWrapper mPrepareCallback;
    private IAxDualAppsReceiver mDeletionReceiver;

    private static final class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private AppOpsManagerInternal mAppOpsManagerInternal = LocalServices.getService(AppOpsManagerInternal.class);
        private IAxDualAppsReceiver mReceiver;
        private int mUid;

        PackageDeleteObserver(int uid, IAxDualAppsReceiver receiver) {
            this.mUid = uid;
            this.mReceiver = receiver;
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode == PackageManager.DELETE_SUCCEEDED && this.mAppOpsManagerInternal != null) {
                this.mAppOpsManagerInternal.setModeFromPermissionPolicy(OP_DUAL_APPS, this.mUid, packageName, AppOpsManager.MODE_IGNORED, (IAppOpsCallback) null);
            }
            if (this.mReceiver != null) {
                try {
                    this.mReceiver.onDualAppDeleted(packageName, returnCode);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    private static final class ProgressWaiter extends IProgressListener.Stub {
        @Override
        public void onFinished(int userId, Bundle bundle) {
        }

        @Override
        public void onProgress(int userId, int progress, Bundle bundle) {
        }

        @Override
        public void onStarted(int userId, Bundle bundle) {
        }
    }

    private static final class DualSpacePrepareCallbackWrapper {
        private IAxDualAppsReceiver mReceiver;

        DualSpacePrepareCallbackWrapper(IAxDualAppsReceiver receiver) {
            this.mReceiver = receiver;
        }

        public void notifyPrepared() {
            if (this.mReceiver != null) {
                try {
                    this.mReceiver.onDualSpacePrepareStatus(0, "create successfully");
                } catch (RemoteException ignored) {
                }
                this.mReceiver = null;
            }
        }
    }

    public AxDualUserHelper(Context context) {
        this.mContext = context;
    }

    private UserInfo createCloneProfile(UserManagerService ums) throws ServiceSpecificException {
        UserInfo existing = getCloneProfileUserInfo();
        if (existing != null) {
            return existing;
        }
        if (isStorageLow()) {
            throw new UserManager.CheckedUserOperationException("Not enough space on disk", 5).toServiceSpecificException();
        }
        long identity = Binder.clearCallingIdentity();
        try {
            return ums.createProfileForUserWithThrow(PROFILE_NAME, "android.os.usertype.profile.CLONE", CLONE_PROFILE_FLAGS, 0, (String[]) null);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public UserInfo getCloneProfileUserInfo() {
        UserManagerService ums = UserManagerService.getInstance();
        if (ums == null) {
            return null;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            for (UserInfo info : ums.getProfiles(0, false)) {
                if (info.isCloneProfile()) {
                    return info;
                }
            }
            return null;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private boolean isStorageLow() {
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            if (pm != null) {
                return pm.isStorageLow();
            }
        } catch (RemoteException ignored) {
        }
        DeviceStorageMonitorInternal dsm = LocalServices.getService(DeviceStorageMonitorInternal.class);
        return dsm != null && dsm.isMemoryLow();
    }

    private void notifyPrepareStatus(IAxDualAppsReceiver receiver, int status, String reason) {
        if (receiver != null) {
            try {
                receiver.onDualSpacePrepareStatus(status, reason);
            } catch (RemoteException ignored) {
            }
        }
    }

    private void notifyDeletionStatus(IAxDualAppsReceiver receiver, boolean success, String reason) {
        if (receiver != null) {
            try {
                receiver.onDualSpaceDeletionStatus(success, reason);
            } catch (RemoteException ignored) {
            }
        }
    }

    public void clearDualSpace(IAxDualAppsReceiver receiver) {
        UserManagerService ums = UserManagerService.getInstance();
        if (ums == null) {
            notifyDeletionStatus(receiver, false, "unknown");
            return;
        }
        if (!isDualSpaceExists()) {
            notifyDeletionStatus(receiver, true, "dual space not exists");
            return;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            this.mDeletionReceiver = receiver;
            if (!ums.removeUser(DUAL_APPS_USER_ID)) {
                this.mDeletionReceiver = null;
                notifyDeletionStatus(receiver, false, "internal error");
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        boolean exists = isDualSpaceExists();
        pw.println("dual space " + (exists ? "exists" : "not exists"));
        if (!exists) {
            return;
        }
        UserInfo info = getCloneProfileUserInfo();
        if (info != null) {
            pw.println();
            pw.println(info.toFullString());
            pw.println();
        }
    }

    public int getCloneProfileId() {
        UserManagerService ums = UserManagerService.getInstance();
        if (ums == null) {
            return -10000;
        }
        UserInfo cloneProfile = ums.getCloneProfile();
        return cloneProfile != null ? cloneProfile.id : -10000;
    }

    public boolean hasDualFlags(int flags) {
        return (flags & CLONE_PROFILE_FLAGS) == CLONE_PROFILE_FLAGS;
    }

    public int installApp(String packageName, int callingUid) {
        IPackageManager pm = AppGlobals.getPackageManager();
        if (pm == null || !isDualSpaceInitialized()) {
            return 0;
        }
        if (isStorageLow()) {
            return -4;
        }
        long identity = Binder.clearCallingIdentity();
        AppOpsManagerInternal appOpsInternal = LocalServices.getService(AppOpsManagerInternal.class);
        int installResult = 0;
        try {
            if (appOpsInternal != null) {
                appOpsInternal.setModeFromPermissionPolicy(OP_DUAL_APPS, callingUid, packageName, AppOpsManager.MODE_ALLOWED, (IAppOpsCallback) null);
            }
            installResult = pm.installExistingPackageAsUser(packageName, DUAL_APPS_USER_ID, 4194304, 0, null);
            if (installResult != PackageManager.INSTALL_SUCCEEDED && appOpsInternal != null) {
                appOpsInternal.setModeFromPermissionPolicy(OP_DUAL_APPS, callingUid, packageName, AppOpsManager.MODE_IGNORED, (IAppOpsCallback) null);
            }
            return installResult;
        } catch (RemoteException e) {
            if (appOpsInternal != null) {
                appOpsInternal.setModeFromPermissionPolicy(OP_DUAL_APPS, callingUid, packageName, AppOpsManager.MODE_IGNORED, (IAppOpsCallback) null);
            }
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean isDualAppsUserId(int userId) {
        if (userId == DUAL_APPS_USER_ID) {
            return true;
        }
        UserManagerService ums = UserManagerService.getInstance();
        return ums != null && ums.isCloneUser(userId);
    }

    public boolean isDualSpaceExists() {
        return getCloneProfileUserInfo() != null;
    }

    public boolean isDualSpaceInitialized() {
        UserInfo info = getCloneProfileUserInfo();
        return info != null && info.isInitialized();
    }

    public void onDualSpaceInitialized() {
        if (this.mPrepareCallback != null) {
            this.mPrepareCallback.notifyPrepared();
            this.mPrepareCallback = null;
        }
        Intent intent = new Intent(ACTION_DUAL_PROFILE_CREATED);
        intent.putExtra(Intent.EXTRA_USER, new UserHandle(DUAL_APPS_USER_ID));
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND);
        this.mContext.sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }

    public void onPackageUninstalled(int uid, String packageName) {
        if (UserHandle.getUserId(uid) != DUAL_APPS_USER_ID) {
            return;
        }
        AppOpsManagerInternal appOpsInternal = LocalServices.getService(AppOpsManagerInternal.class);
        if (appOpsInternal != null) {
            appOpsInternal.setModeFromPermissionPolicy(OP_DUAL_APPS, UserHandle.getUid(0, uid), packageName, AppOpsManager.MODE_IGNORED, (IAppOpsCallback) null);
        }
    }

    public void onUserRemoved(int userId, AxDualAppsConfig config) {
        if (userId != DUAL_APPS_USER_ID) {
            return;
        }
        config.resetAllPackageOps();
        notifyDeletionStatus(this.mDeletionReceiver, true, "delete successfully");
        this.mDeletionReceiver = null;
    }

    public void prepareDualSpace(IAxDualAppsReceiver receiver) {
        UserManagerService ums = UserManagerService.getInstance();
        int errorCode = 1;
        if (ums != null) {
            try {
                UserInfo info = createCloneProfile(ums);
                if (info != null) {
                    if (info.isInitialized()) {
                        notifyPrepareStatus(receiver, 0, "already created");
                        return;
                    }
                    long identity = Binder.clearCallingIdentity();
                    try {
                        if (ActivityManager.getService().startProfileWithListener(info.id, new ProgressWaiter())) {
                            this.mPrepareCallback = new DualSpacePrepareCallbackWrapper(receiver);
                        } else {
                            notifyPrepareStatus(receiver, 1, "unable to start");
                        }
                        return;
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            } catch (ServiceSpecificException e) {
                errorCode = e.errorCode;
            } catch (Exception e) {
                Slog.e(TAG, "createProfile error", e);
            }
        }
        notifyPrepareStatus(receiver, errorCode, "create failed");
    }

    public void uninstallApp(String packageName, int callingUid, IAxDualAppsReceiver receiver) {
        IPackageManager pm = AppGlobals.getPackageManager();
        if (pm == null || !isDualSpaceInitialized()) {
            if (receiver != null) {
                try {
                    receiver.onDualAppDeleted(packageName, -1);
                } catch (RemoteException ignored) {
                }
            }
            return;
        }
        long identity = Binder.clearCallingIdentity();
        try {
            pm.deletePackageAsUser(packageName, -1, new PackageDeleteObserver(callingUid, receiver), DUAL_APPS_USER_ID, PackageManager.DELETE_SYSTEM_APP);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}
