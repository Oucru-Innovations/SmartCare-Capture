package com.smartcare.oximetry.sample;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class OximeterDataService extends Service {
    private static final String TAG = "OximeterDataService";
    private static final String CHANNEL_ID = "OximeterDataChannel";
    private static final int NOTIFICATION_ID = 1;

    // Service state
    private boolean isRunning = false;
    private boolean recordingFinalized = false;
    private PowerManager.WakeLock wakeLock;
    private long lastAddDataPointDropLogMs = 0;

    public static final String ACTION_SPLIT_RECORDING = "com.smartcare.oximetry.ACTION_SPLIT_RECORDING";

    private final android.content.BroadcastReceiver splitReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_SPLIT_RECORDING.equals(intent.getAction())) {
                Log.i(TAG, "Received broadcast for daily cutoff split");
                FileLogger.logEvent("SERVICE", "DAILY_CUTOFF_SPLIT", "17:00 file rotation");
                OximeterDataWriterFunctions.splitForDailyCutoff();
            }
        }
    };


    private OximeterDataBuffer dataBuffer;
    private String patientCode;
    private boolean enableChunkFile;
    private int timeInterval;

    public String endRecordFilePath = "";
    // Binder for Activity-Service communication
    private final IBinder binder = new LocalBinder();
    private Context serviceContext;

    public class LocalBinder extends Binder {
        OximeterDataService getService() {
            return OximeterDataService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Oximeter Data Service created");
        FileLogger.logEvent("SERVICE", "SERVICE_CREATED", "OximeterDataService onCreate");
        FileUploader.setAppContext(getApplicationContext());
        createNotificationChannel();
        serviceContext = this;

        IntentFilter filter = new IntentFilter(ACTION_SPLIT_RECORDING);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(splitReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(splitReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            FileLogger.logWarningEvent("SERVICE", "SERVICE_RESTART_NULL_INTENT",
                    "onStartCommand with null intent (START_STICKY after system kill); isRunning=" + isRunning
                            + " dataBuffer=" + (dataBuffer != null));
        } else if ((flags & START_FLAG_REDELIVERY) != 0) {
            FileLogger.logWarningEvent("SERVICE", "SERVICE_REDELIVERED",
                    "Service restarted after being killed; patientCode=" + patientCode);
        }

        if (intent != null) {
            if (intent.hasExtra("patientCode")) {
                patientCode = intent.getStringExtra("patientCode");
                enableChunkFile = intent.getBooleanExtra("enableChunkFile", false);
                timeInterval = intent.getIntExtra("timeInterval", 3);

                startForeground(NOTIFICATION_ID, createNotification());
                acquireWakeLock();

                isRunning = true;
                Log.i(TAG, "Oximeter Data Service started with patient code: " + patientCode);
                FileLogger.logEvent("SERVICE", "SERVICE_STARTED",
                        "foreground started patientCode=" + patientCode
                                + " chunk=" + enableChunkFile + " interval=" + timeInterval);
            }
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Oximeter Data Collection",
                    NotificationManager.IMPORTANCE_HIGH);  // Stay high so the system won’t kill you
            channel.setDescription("Channel for oximeter data collection service");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, RecordActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SmartCare Capture")
                .setContentText("Data collection active for " + patientCode)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OximeterDataService::WakeLock");
            wakeLock.acquire(); // keep CPU alive 24/7
        }
    }

    public void startDataCollection(long timestamp, String patientIdentifier, boolean enableChunkFile, int timeInterval) {
        dataBuffer = new OximeterDataBuffer();
        recordingFinalized = false;

        OximeterDataWriterFunctions.initialize(serviceContext);
        OximeterDataWriterFunctions.startNewRecording(
                timestamp,
                patientIdentifier,
                enableChunkFile,
                timeInterval);
        FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STARTED",
                "patient=" + patientIdentifier + " timestamp=" + timestamp);
    }

    public boolean ensureRecordingActive(long timestamp, String patientIdentifier,
                                         boolean enableChunkFile, int timeInterval) {
        if (!OximeterDataWriterFunctions.isRecordingActive()) {
            startDataCollection(timestamp, patientIdentifier, enableChunkFile, timeInterval);
            return true;
        }
        return false;
    }

    // Method to receive new data from RecordActivity
    public void addDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                             int hrVal, long irAdc, double perfusionIndex, int counter,
                             String macAddress, long packageReceived, int plethVal, long timestamp) {

        if (isRunning && dataBuffer != null) {
            dataBuffer.addDataPoint(spo2Val, spo2Status, redAdc, battery, hrVal, irAdc,
                    perfusionIndex, counter, macAddress, packageReceived, plethVal, timestamp);
        } else {
            long now = System.currentTimeMillis();
            if (now - lastAddDataPointDropLogMs > 10_000L) {
                lastAddDataPointDropLogMs = now;
                FileLogger.logWarningEvent("SERVICE", "DATA_POINT_DROPPED",
                        "addDataPoint ignored: isRunning=" + isRunning
                                + " dataBuffer=" + (dataBuffer != null)
                                + " patientCode=" + patientCode);
            }
        }
    }

    public void stopDataCollection() {
        if (recordingFinalized) {
            FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STOP_SKIPPED",
                    "already finalized; isRunning=" + isRunning);
            return;
        }
        recordingFinalized = true;

        FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STOP_REQUESTED",
                "isRunning=" + isRunning + " hadBuffer=" + (dataBuffer != null));

        String filePath = OximeterDataWriterFunctions.endRecording();
        if (filePath != null) {
            Log.i(TAG, "Data collection stopped, file saved: " + filePath);
            endRecordFilePath = filePath;
            FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STOPPED",
                    "file saved uri/path=" + filePath);
        } else {
            Log.w(TAG, "Data collection stopped but no active recording file to finalize");
            FileLogger.logWarningEvent("SERVICE", "DATA_COLLECTION_STOPPED_NO_FILE",
                    "endRecording returned null; isRunning=" + isRunning);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }

        isRunning = false;
        dataBuffer = null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        FileLogger.logWarningEvent("USER", "SERVICE_TASK_REMOVED",
                "User removed app from recent tasks; service will stop"
                        + " isRunning=" + isRunning + " recordingFinalized=" + recordingFinalized,
                getApplicationContext());
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        FileLogger.logEvent("SERVICE", "SERVICE_DESTROY",
                "onDestroy isRunning=" + isRunning
                        + " recordingFinalized=" + recordingFinalized
                        + " | finalizing recording if still active",
                getApplicationContext());
        unregisterReceiver(splitReceiver);
        if (!recordingFinalized) {
            stopDataCollection();
        }
        stopForeground(true);
        Log.i(TAG, "Oximeter Data Service destroyed");
        super.onDestroy();
    }

    public boolean isRunning() {
        return isRunning;
    }
}
