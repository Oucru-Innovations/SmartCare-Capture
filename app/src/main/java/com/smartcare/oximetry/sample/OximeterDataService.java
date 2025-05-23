package com.smartcare.oximetry.sample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
    private PowerManager.WakeLock wakeLock;


    private OximeterDataBuffer dataBuffer;
    private String patientCode;
    private boolean enableChunkFile;
    private int timeInterval;

    public String endRecordFilePath = "";
    // Binder for Activity-Service communication
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        OximeterDataService getService() {
            return OximeterDataService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Oximeter Data Service created");
        createNotificationChannel();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            // Extract data from the intent if available
            if (intent.hasExtra("patientCode")) {
                patientCode = intent.getStringExtra("patientCode");
                enableChunkFile = intent.getBooleanExtra("enableChunkFile", false);
                timeInterval = intent.getIntExtra("timeInterval", 3);

                // Start service in foreground to prevent it from being killed
                startForeground(NOTIFICATION_ID, createNotification());

                // Acquire wake lock to keep CPU running when screen is off
                acquireWakeLock();

                // Initialize data collection
//                startDataCollection();

                isRunning = true;
                Log.i(TAG, "Oximeter Data Service started with patient code: " + patientCode);
            }
        }

        // If service is killed by the system, restart it
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Oximeter Data Collection",
                    NotificationManager.IMPORTANCE_LOW);  // Low importance reduces visual distraction
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
            wakeLock.acquire(10*60*1000L /*10 minutes*/);
        }
    }

    public void startDataCollection(long timestamp, String patientIdentifier, boolean enableChunkFile, int timeInterval) {
        dataBuffer = new OximeterDataBuffer();

        // Initialize data recording
        OximeterDataWriterFunctions.setActivity(null);  // No need for Activity reference in service
        OximeterDataWriterFunctions.startNewRecording(
                timestamp,
                patientIdentifier,
                enableChunkFile,
                timeInterval);
    }

    // Method to receive new data from RecordActivity
    public void addDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                             int hrVal, long irAdc, double perfusionIndex, int counter,
                             String macAddress, long packageReceived, int plethVal, long timestamp) {

        if (isRunning && dataBuffer != null) {
            dataBuffer.addDataPoint(spo2Val, spo2Status, redAdc, battery, hrVal, irAdc,
                    perfusionIndex, counter, macAddress, packageReceived, plethVal, timestamp);
        }
    }

    public void stopDataCollection() {
        if (isRunning) {
            // Stop data collection and save files
            String filePath = OximeterDataWriterFunctions.endRecording();
            Log.i(TAG, "Data collection stopped, file saved: " + filePath);

            // Release wake lock if held
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                wakeLock = null;
            }

            isRunning = false;
            dataBuffer = null;

            endRecordFilePath = filePath;
        }
    }

    @Override
    public void onDestroy() {
        stopDataCollection();
        stopForeground(true);
        Log.i(TAG, "Oximeter Data Service destroyed");
        super.onDestroy();
    }

    public boolean isRunning() {
        return isRunning;
    }
}