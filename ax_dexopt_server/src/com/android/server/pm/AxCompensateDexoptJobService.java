package com.android.server.pm;

import android.app.job.JobParameters;
import android.app.job.JobService;
import com.android.server.pm.AxCompensateDexoptService;

public class AxCompensateDexoptJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        return AxCompensateDexoptService.getInstance().onStartJob(this, params);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return AxCompensateDexoptService.getInstance().onStopJob(params);
    }
}
