package com.android.server.pm;

import android.app.job.JobInfo;
import android.os.CancellationSignal;
import android.util.Slog;
import com.android.server.art.ArtManagerLocal;
import com.android.server.art.ArtManagerLocal.BatchDexoptStartCallback;
import com.android.server.art.ArtManagerLocal.DexoptDoneCallback;
import com.android.server.art.ArtManagerLocal.ScheduleBackgroundDexoptJobCallback;
import com.android.server.art.model.BatchDexoptParams;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.pm.AxDexoptManagerImpl;
import com.android.server.pm.DexOptHelper;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.PackageManagerLocal.FilteredSnapshot;
import com.android.server.pm.PackageManagerServiceUtils;
import java.util.List;
import java.util.concurrent.Executor;

public class AxArtManagerLocalHelper {

    private static final String TAG = "AxArtManagerLocalHelper";

    private static final DexoptDoneCallback sDexoptDoneCallback = new DexoptDoneCallback() {
        @Override
        public void onDexoptDone(DexoptResult result) {
            AxDexoptManagerImpl.getInstance().onDexoptDone(result);
        }
    };

    private static final ScheduleBackgroundDexoptJobCallback sScheduleJobCallback = new ScheduleBackgroundDexoptJobCallback() {
        @Override
        public void onOverrideJobInfo(JobInfo.Builder builder) {
            AxDexoptManagerImpl.getInstance().onOverrideJobInfo(builder);
        }
    };

    private static final BatchDexoptStartCallback sBatchDexoptCallback = new BatchDexoptStartCallback() {
        @Override
        public void onBatchDexoptStart(FilteredSnapshot snapshot, String reason, List<String> defaultPackages, BatchDexoptParams.Builder builder, CancellationSignal cancellationSignal) {
            AxDexoptManagerImpl.getInstance().onBatchDexoptStart(snapshot, reason, defaultPackages, builder, cancellationSignal);
        }
    };

    private static boolean sRegistered = false;

    public static DexoptResult performDexOptimization(String packageName, String reason) {
        PackageManagerLocal packageManagerLocal = PackageManagerServiceUtils.getPackageManagerLocal();
        if (packageManagerLocal == null) {
            return null;
        }
        ArtManagerLocal artManagerLocal = DexOptHelper.getArtManagerLocal();
        if (artManagerLocal == null) {
            return null;
        }
        try (FilteredSnapshot snapshot = packageManagerLocal.withFilteredSnapshot()) {
            DexoptParams params = AxDexoptManagerImpl.getInstance().getDexoptParams(reason);
            return artManagerLocal.dexoptPackage(snapshot, packageName, params, new CancellationSignal());
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Failed to optimize package: " + packageName, e);
            return null;
        }
    }

    public static synchronized void registerCallbacks() {
        if (sRegistered) {
            return;
        }
        ArtManagerLocal artManagerLocal = DexOptHelper.getArtManagerLocal();
        if (artManagerLocal == null) {
            return;
        }
        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
        artManagerLocal.addDexoptDoneCallback(false, executor, sDexoptDoneCallback);
        artManagerLocal.setScheduleBackgroundDexoptJobCallback(executor, sScheduleJobCallback);
        artManagerLocal.setBatchDexoptStartCallback(executor, sBatchDexoptCallback);
        sRegistered = true;
    }

    public static synchronized void unregisterCallbacks() {
        if (!sRegistered) {
            return;
        }
        ArtManagerLocal artManagerLocal = DexOptHelper.getArtManagerLocal();
        if (artManagerLocal == null) {
            return;
        }
        artManagerLocal.removeDexoptDoneCallback(sDexoptDoneCallback);
        artManagerLocal.clearScheduleBackgroundDexoptJobCallback();
        artManagerLocal.clearBatchDexoptStartCallback();
        sRegistered = false;
    }
}
