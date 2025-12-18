package com.smartcare.oximetry.sample;

import android.content.Context;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class AnnotationUploadScheduler {
    public static void scheduleDailyUpload(Context context) {
        // Calculate delay until 9 PM today; if already past, schedule for tomorrow at 9 PM.
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

        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(UploadAnnotationWorker.class)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueue(uploadWork);
    }
}