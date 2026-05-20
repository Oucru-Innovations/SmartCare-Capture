package com.smartcare.oximetry.sample;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class AnnotationUploadScheduler {
    private static final String UNIQUE_DAILY_WORK = "smartcare_daily_17h_maintenance";

    public static void scheduleDailyUpload(Context context) {
        Calendar current = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 17);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        if (current.after(target)) {
            target.add(Calendar.DAY_OF_MONTH, 1);
        }
        long initialDelay = target.getTimeInMillis() - current.getTimeInMillis();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(UploadAnnotationWorker.class)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_DAILY_WORK,
                ExistingWorkPolicy.REPLACE,
                uploadWork);
    }
}
