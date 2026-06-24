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

import android.content.pm.ServiceInfo;

import android.os.Binder;

import android.os.Build;

import android.os.Handler;

import android.os.IBinder;

import android.os.Looper;

import android.os.PowerManager;

import android.text.TextUtils;

import android.util.Log;



import androidx.core.app.NotificationCompat;



import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;

import java.util.concurrent.atomic.AtomicBoolean;



/**
 * Foreground service chạy dài hạn để ghi dữ liệu oximeter ra CSV. Dữ liệu được đệm qua
 * {@link OximeterDataBuffer} và ghi xuống file qua {@link OximeterDataWriterFunctions}; service giữ
 * partial wake lock để CPU không ngủ. Sống sót qua việc bị kill bằng START_STICKY kết hợp
 * {@link RecordingSessionPrefs} để khôi phục phiên ghi sau khi process bị khởi động lại.
 */
public class OximeterDataService extends Service {

    private static final String TAG = "OximeterDataService";

    private static final String CHANNEL_ID = "OximeterDataChannel";

    private static final int NOTIFICATION_ID = 1;



    private boolean isRunning = false;

    private boolean recordingFinalized = false;

    private PowerManager.WakeLock wakeLock;

    private long lastAddDataPointDropLogMs = 0;



    public static final String ACTION_SPLIT_RECORDING = "com.smartcare.oximetry.ACTION_SPLIT_RECORDING";



    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);



    private final android.content.BroadcastReceiver splitReceiver = new android.content.BroadcastReceiver() {

        @Override

        public void onReceive(Context context, Intent intent) {

            if (ACTION_SPLIT_RECORDING.equals(intent.getAction())) {

                Log.i(TAG, "Received broadcast for daily cutoff split");

                FileLogger.logEvent("SERVICE", "DAILY_CUTOFF_SPLIT", "17:00 file rotation");

                ioExecutor.execute(() -> {
                    flushBufferedDataQuietly();
                    OximeterDataWriterFunctions.splitForDailyCutoff();
                });

            }

        }

    };



    private OximeterDataBuffer dataBuffer;

    private String patientCode;

    private boolean enableChunkFile;

    private int timeInterval;



    public String endRecordFilePath = "";



    private final IBinder binder = new LocalBinder();

    private Context serviceContext;



    /**
     * Binder cho bound connection, cho phép Activity lấy tham chiếu trực tiếp tới service đang chạy.
     */
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

        OximeterDataWriterFunctions.initialize(serviceContext);

        OximeterDataWriterFunctions.setSplitInterval(RecordingConstants.SPLIT_INTERVAL_HOURS);



        IntentFilter filter = new IntentFilter(ACTION_SPLIT_RECORDING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            registerReceiver(splitReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        } else {

            registerReceiver(splitReceiver, filter);

        }

    }



    @Override

    /**
     * Điểm vào mỗi lần service được start. Intent null nghĩa là process bị system kill rồi khởi động
     * lại (START_STICKY) nên ta khôi phục phiên ghi; trả về START_STICKY khi còn phiên active,
     * ngược lại START_NOT_STICKY để không bị tái tạo vô ích.
     */
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {

            FileLogger.logWarningEvent("SERVICE", "SERVICE_RESTART_NULL_INTENT",

                    "onStartCommand with null intent (START_STICKY after system kill); isRunning=" + isRunning

                            + " dataBuffer=" + (dataBuffer != null));

            restoreSessionAfterProcessRestart();

        } else if ((flags & START_FLAG_REDELIVERY) != 0) {

            FileLogger.logWarningEvent("SERVICE", "SERVICE_REDELIVERED",

                    "Service restarted after being killed; patientCode=" + patientCode);

        }



        if (intent != null && intent.hasExtra("patientCode")) {

            patientCode = intent.getStringExtra("patientCode");

            enableChunkFile = intent.getBooleanExtra("enableChunkFile", false);

            timeInterval = intent.getIntExtra("timeInterval", 3);



            enterForeground();

            acquireWakeLock();



            isRunning = true;

            // Arm the session (foreground + buffer) but DO NOT open a CSV file here. The file is
            // created lazily when the first real data sample arrives (see startDataCollection /
            // ensureRecordingActive on the first packet), so starting/binding the service without
            // a connected device no longer produces an empty header-only file.
            prepareForDataCollection(patientCode, enableChunkFile, timeInterval);

            Log.i(TAG, "Oximeter Data Service started with patient code: " + patientCode);

            FileLogger.logEvent("SERVICE", "SERVICE_STARTED",

                    "foreground patientCode=" + patientCode

                            + " chunk=" + enableChunkFile + " interval=" + timeInterval

                            + " splitHours=" + RecordingConstants.SPLIT_INTERVAL_HOURS);

        }



        return RecordingSessionPrefs.isActive(getApplicationContext()) ? START_STICKY : START_NOT_STICKY;

    }



    private void enterForeground() {

        Notification notification = createNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(

                    NOTIFICATION_ID,

                    notification,

                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

        } else {

            startForeground(NOTIFICATION_ID, notification);

        }

    }



    /**
     * Khôi phục phiên ghi từ {@link RecordingSessionPrefs} sau khi process bị kill và restart: nạp
     * lại patientCode/cấu hình, dựng lại buffer rồi vào lại foreground để tiếp tục ghi.
     */
    private void restoreSessionAfterProcessRestart() {

        RecordingSessionPrefs.Session session = RecordingSessionPrefs.load(getApplicationContext());

        if (!session.active || TextUtils.isEmpty(session.patientCode)) {

            return;

        }

        patientCode = session.patientCode;

        enableChunkFile = session.chunkEnabled;

        timeInterval = session.chunkInterval;

        recordingFinalized = false;

        if (dataBuffer == null) {
            dataBuffer = new OximeterDataBuffer();
        }

        if (!isRunning) {

            enterForeground();

            acquireWakeLock();

            isRunning = true;

            FileLogger.logEvent("SERVICE", "SERVICE_RESTORED_SESSION",

                    "patient=" + patientCode

                            + " collectionStartMs=" + session.collectionStartMs

                            + " writerActive=" + OximeterDataWriterFunctions.isRecordingActive());

        }

    }



    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(

                    CHANNEL_ID,

                    "Oximeter Data Collection",

                    NotificationManager.IMPORTANCE_LOW);

            channel.setDescription("Continuous SmartCare oximeter recording");



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

                .setContentText("Recording " + (patientCode != null ? patientCode : "…")

                        + " · split every " + RecordingConstants.SPLIT_INTERVAL_HOURS + "h")

                .setSmallIcon(R.mipmap.ic_launcher)

                .setContentIntent(pendingIntent)

                .setOngoing(true)

                .setPriority(NotificationCompat.PRIORITY_LOW)

                .build();

    }



    /**
     * Lấy partial wake lock để CPU tiếp tục chạy việc ghi dữ liệu khi màn hình tắt; idempotent
     * (không lấy lại nếu đang giữ).
     */
    private void acquireWakeLock() {

        if (wakeLock != null && wakeLock.isHeld()) {

            return;

        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (powerManager != null) {

            wakeLock = powerManager.newWakeLock(

                    PowerManager.PARTIAL_WAKE_LOCK,

                    "OximeterDataService::WakeLock");

            wakeLock.setReferenceCounted(false);

            wakeLock.acquire();

        }

    }



    /**
     * Bắt đầu thu thập dữ liệu: chuẩn bị phiên rồi mở segment CSV mới nếu writer chưa active. Dùng
     * thời điểm mở file thật (không phải session start) làm mốc cho từng segment để tránh trùng tên
     * file và lệch lịch split.
     */
    public void startDataCollection(long timestamp, String patientIdentifier, boolean enableChunkFile,

                                    int timeInterval) {

        prepareForDataCollection(patientIdentifier, enableChunkFile, timeInterval);

        if (!OximeterDataWriterFunctions.isRecordingActive()) {

            // Each CSV segment starts when its file is actually opened. Using the persisted
            // session start here would label every restart's segment with the same time, which
            // (a) collided into "_unknown (1)" files and (b) corrupted the split schedule.
            OximeterDataWriterFunctions.startNewRecording(System.currentTimeMillis(), patientIdentifier,

                    enableChunkFile, timeInterval);

        }

        FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STARTED",

                "patient=" + patientIdentifier + " collectionStartMs=" + timestamp);

    }

    /**
     * Prepares the recording session (foreground buffer, split interval, flags) WITHOUT opening a
     * CSV file. The actual file is opened lazily by {@link #startDataCollection} or
     * {@link #ensureRecordingActive} on the first real data sample, so arming/binding the service
     * with no connected device does not create an empty header-only file.
     */
    private void prepareForDataCollection(String patientIdentifier, boolean enableChunkFile,
                                          int timeInterval) {

        patientCode = patientIdentifier;

        this.enableChunkFile = enableChunkFile;

        this.timeInterval = timeInterval;

        recordingFinalized = false;

        shutdownInProgress.set(false);

        if (dataBuffer == null) {

            dataBuffer = new OximeterDataBuffer();

        }

        isRunning = true;

        OximeterDataWriterFunctions.initialize(serviceContext);

        OximeterDataWriterFunctions.setSplitInterval(RecordingConstants.SPLIT_INTERVAL_HOURS);

    }



    /** Opens a new CSV segment when the 24h session is active but the writer was closed (e.g. BLE pause). */

    public boolean ensureRecordingActive(long timestamp, String patientIdentifier,

                                         boolean enableChunkFile, int timeInterval) {

        if (recordingFinalized) {

            return false;

        }

        patientCode = patientIdentifier;

        this.enableChunkFile = enableChunkFile;

        this.timeInterval = timeInterval;

        if (dataBuffer == null) {

            dataBuffer = new OximeterDataBuffer();

        }

        isRunning = true;

        OximeterDataWriterFunctions.initialize(serviceContext);

        OximeterDataWriterFunctions.setSplitInterval(RecordingConstants.SPLIT_INTERVAL_HOURS);

        if (!OximeterDataWriterFunctions.isRecordingActive()) {

            // Each CSV segment starts when its file is actually opened. Using the persisted
            // session start here would label every restart's segment with the same time, which
            // (a) collided into "_unknown (1)" files and (b) corrupted the split schedule.
            OximeterDataWriterFunctions.startNewRecording(System.currentTimeMillis(), patientIdentifier,

                    enableChunkFile, timeInterval);

        }

        return OximeterDataWriterFunctions.isRecordingActive();

    }



    /**
     * Đẩy một sample vào buffer khi service đang chạy; nếu không (chưa sẵn sàng) thì bỏ qua và log
     * cảnh báo có throttle (tối đa 10s/lần) để tránh spam log.
     */
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

                                + " writerActive=" + OximeterDataWriterFunctions.isRecordingActive()

                                + " patientCode=" + patientCode);

            }

        }

    }



    /**
     * Flushes any buffered samples that have not yet reached a full 100-sample batch into the
     * currently open CSV segment. Safe to call from any thread and when no buffer exists.
     * Call this before closing/splitting a segment so the segment's tail is not lost.
     */
    public void flushBufferedData() {
        flushBufferedDataQuietly();
    }

    private void flushBufferedDataQuietly() {
        OximeterDataBuffer buffer = dataBuffer;
        if (buffer == null) {
            return;
        }
        try {
            buffer.flushRemaining();
        } catch (Exception e) {
            Log.w(TAG, "flushBufferedData failed", e);
            FileLogger.w(TAG, "flushBufferedData failed: " + e.getMessage());
        }
    }

    /**
     * Kết thúc phiên ghi: flush batch còn dở (< 100 sample) để không mất đuôi dữ liệu, đóng file CSV,
     * nhả wake lock và dọn buffer. Idempotent qua cờ {@code recordingFinalized}.
     */
    public void stopDataCollection() {

        if (recordingFinalized) {

            FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STOP_SKIPPED",

                    "already finalized; isRunning=" + isRunning);

            return;

        }

        recordingFinalized = true;



        FileLogger.logEvent("SERVICE", "DATA_COLLECTION_STOP_REQUESTED",

                "isRunning=" + isRunning + " hadBuffer=" + (dataBuffer != null));



        // Persist the partial batch (< 100 samples) before closing, otherwise a short session
        // ends up as a header-only CSV with no data rows.
        flushBufferedDataQuietly();

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

        releaseWakeLockQuietly();

        isRunning = false;

        // Stop the background CSV consumer after the tail batch has been flushed and the file
        // closed, otherwise the worker thread would leak when the buffer reference is dropped.
        OximeterDataBuffer bufferToStop = dataBuffer;

        if (bufferToStop != null) {

            bufferToStop.stopConsumer();

        }

        dataBuffer = null;

    }



    /**
     * Tắt việc ghi không đồng bộ: chạy {@link #stopDataCollection()} trên IO thread (tránh block
     * main/ANR), sau đó gỡ foreground và stopSelf trên main thread rồi gọi {@code onFinished}. Có
     * guard chống gọi shutdown trùng lặp.
     */
    public void shutdownRecordingAsync(Runnable onFinished) {

        if (!shutdownInProgress.compareAndSet(false, true)) {

            FileLogger.logEvent("SERVICE", "SHUTDOWN_ALREADY_IN_PROGRESS",

                    "Ignoring duplicate shutdown; endRecordFilePath="

                            + (TextUtils.isEmpty(endRecordFilePath) ? "empty" : endRecordFilePath));

            return;

        }

        ioExecutor.execute(() -> {

            try {

                stopDataCollection();

            } finally {

                mainHandler.post(() -> {

                    detachForegroundAndStop();

                    shutdownInProgress.set(false);

                    if (onFinished != null) {

                        onFinished.run();

                    }

                });

            }

        });

    }



    public String getCompletedRecordFilePath() {

        return TextUtils.isEmpty(endRecordFilePath) ? null : endRecordFilePath;

    }



    private void detachForegroundAndStop() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            stopForeground(STOP_FOREGROUND_DETACH);

        } else {

            stopForeground(false);

        }

        stopSelf();

        FileLogger.logEvent("SERVICE", "SERVICE_STOP_SELF", "stopForeground+stopSelf completed");

    }



    private void releaseWakeLockQuietly() {

        if (wakeLock != null && wakeLock.isHeld()) {

            try {

                wakeLock.release();

            } catch (RuntimeException e) {

                Log.w(TAG, "WakeLock release", e);

            }

            wakeLock = null;

        }

    }



    @Override

    /**
     * Khi người dùng xoá app khỏi recent tasks: nếu còn phiên ghi active thì GIỮ service sống để BLE
     * có thể reconnect; ngược lại mới tiến hành shutdown bình thường.
     */
    public void onTaskRemoved(Intent rootIntent) {

        if (RecordingSessionPrefs.isActive(getApplicationContext())) {

            FileLogger.logEvent("USER", "SERVICE_TASK_REMOVED_KEEP_ALIVE",

                    "Recent tasks cleared; recording session stays active for BLE reconnect");

            return;

        }

        FileLogger.logWarningEvent("USER", "SERVICE_TASK_REMOVED",

                "No active capture prefs; allowing default task removal behavior");

        shutdownRecordingAsync(null);

    }



    @Override

    public void onDestroy() {

        FileLogger.logEvent("SERVICE", "SERVICE_DESTROY",

                "onDestroy isRunning=" + isRunning

                        + " recordingFinalized=" + recordingFinalized,

                getApplicationContext());

        try {

            unregisterReceiver(splitReceiver);

        } catch (IllegalArgumentException ignored) {

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            stopForeground(STOP_FOREGROUND_DETACH);

        }

        releaseWakeLockQuietly();

        ioExecutor.shutdown();

        Log.i(TAG, "Oximeter Data Service destroyed");

        super.onDestroy();

    }



    public boolean isRunning() {

        return isRunning;

    }

}

