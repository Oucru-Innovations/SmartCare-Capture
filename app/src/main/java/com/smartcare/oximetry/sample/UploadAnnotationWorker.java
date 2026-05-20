package com.smartcare.oximetry.sample;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UploadAnnotationWorker extends Worker {
    private static final String TAG = "UploadAnnotWorker";

    public UploadAnnotationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting daily maintenance worker (attempt " + getRunAttemptCount() + ")");

        if (getRunAttemptCount() == 0) {
            Intent splitIntent = new Intent(OximeterDataService.ACTION_SPLIT_RECORDING);
            getApplicationContext().sendBroadcast(splitIntent);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Wait for split interrupted", e);
            }
        } else {
            Log.i(TAG, "Skipping split on worker retry");
        }

        int queued = PendingFileUploadCoordinator.uploadAllPending(getApplicationContext());
        Log.i(TAG, "Queued " + queued + " pending file(s) for upload");

        AnnotationUploadScheduler.scheduleDailyUpload(getApplicationContext());

        return Result.success();
    }
}
