package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central upload flow: per-file retry (max 3 attempts), network check, move to Uploaded on success.
 * A new user/batch request starts a fresh attempt cycle and cancels any pending auto-retry.
 */
public final class PendingFileUploadCoordinator {
    private static final String TAG = "PendingUpload";
    /** Số lần thử upload tối đa cho mỗi file trước khi bỏ cuộc. */
    private static final int MAX_ATTEMPTS = 3;
    /** Khoảng chờ giữa các lần retry upload (ms). */
    private static final int RETRY_DELAY_MS = 10_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, AtomicInteger> ATTEMPTS = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Runnable> PENDING_RETRIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FileUploader.UploadCallback> TERMINAL_CALLBACKS =
            new ConcurrentHashMap<>();

    public enum EnqueueResult {
        STARTED,
        ALREADY_IN_FLIGHT,
        SKIPPED,
        INVALID
    }

    private PendingFileUploadCoordinator() {
    }

    /**
     * Upload một file session (không kèm Activity) thông qua overload đầy đủ.
     */
    public static EnqueueResult uploadSessionFile(Context context, String fileUri) {
        return uploadSessionFile(context, null, fileUri, null);
    }

    /**
     * Upload một file session kèm Activity; bỏ qua nếu fileUri trùng file đang ghi (currently-open)
     * để không làm hỏng writer đang mở. Bắt đầu chu kỳ 3 lần thử mới và hủy auto-retry đang chờ.
     */
    public static EnqueueResult uploadSessionFile(Context context, Activity activity, String fileUri) {
        return uploadSessionFile(context, activity, fileUri, null);
    }

    /**
     * Như {@link #uploadSessionFile(Context, Activity, String)}; {@code terminalCallback} (nếu có)
     * chỉ được gọi khi thành công hoặc khi đã hết 3 lần thử.
     */
    public static EnqueueResult uploadSessionFile(Context context, Activity activity, String fileUri,
                                                  FileUploader.UploadCallback terminalCallback) {
        if (context == null || TextUtils.isEmpty(fileUri)) {
            return EnqueueResult.INVALID;
        }
        // Never upload the file that is still being written: moving it to the Uploaded folder
        // invalidates the writer's open URI and makes the next CSV split/end fail.
        String openUri = OximeterDataWriterFunctions.getCurrentOpenFileUriString();
        if (fileUri.equals(openUri)) {
            Log.w(TAG, "Refusing to upload the currently-open recording file: " + fileUri);
            FileLogger.logWarningEvent("UPLOAD", "UPLOAD_SKIP_OPEN_FILE", "uri=" + fileUri);
            return EnqueueResult.SKIPPED;
        }
        FileUploader.setAppContext(context.getApplicationContext());
        return startFreshUpload(context.getApplicationContext(), activity, fileUri, true,
                terminalCallback);
    }

    /**
     * Duyệt toàn bộ file đang chờ (pending), bỏ qua file đang ghi/file rỗng và đưa phần còn lại vào hàng đợi upload.
     * Mỗi file bắt đầu chu kỳ retry mới. Trả về số file đã được đưa vào hàng đợi.
     */
    public static int uploadAllPending(Context context) {
        if (context == null) {
            return 0;
        }
        Context app = context.getApplicationContext();
        FileUploader.setAppContext(app);

        String openUri = OximeterDataWriterFunctions.getCurrentOpenFileUriString();
        List<OximeterDataFileIndex.FileEntry> pending =
                OximeterDataFileIndex.queryPendingFiles(app);

        int queued = 0;
        for (OximeterDataFileIndex.FileEntry entry : pending) {
            if (entry.uri == null) {
                continue;
            }
            String uriStr = entry.uri.toString();
            if (shouldSkipPendingEntry(entry, uriStr, openUri)) {
                continue;
            }
            // Khôi phục từ Trash: chỉ upload khi người dùng bấm upload tay.
            if (FileRetention.isNoAutoUpload(app, entry.displayName)) {
                continue;
            }
            if (startFreshUpload(app, null, uriStr, false, null) == EnqueueResult.STARTED) {
                queued++;
            }
        }
        FileLogger.logEvent("UPLOAD", "PENDING_BATCH_QUEUED",
                "queued=" + queued + " totalListed=" + pending.size());
        return queued;
    }

    public static boolean isInFlight(String fileUri) {
        return fileUri != null && IN_FLIGHT.contains(fileUri);
    }

    /**
     * Safety policy for unknown files:
     * - Always skip the currently-open recording file.
     * - Skip unknown files only when they're empty (0 bytes), which are usually failed placeholders.
     * - Allow non-empty unknown files so crash/power-loss data is not lost.
     */
    private static boolean shouldSkipPendingEntry(OximeterDataFileIndex.FileEntry entry,
                                                  String uriStr, String openUri) {
        if (uriStr.equals(openUri)) {
            return true;
        }
        if (entry.displayName != null
                && entry.displayName.contains("_unknown")
                && entry.sizeBytes <= 0L) {
            return true;
        }
        return false;
    }

    /**
     * User/batch request: cancel delayed retries and reset the attempt counter so tapping Upload
     * after 3 failures starts a new cycle.
     */
    private static EnqueueResult startFreshUpload(Context appContext, Activity activity,
                                                  String fileUri, boolean sessionUpload,
                                                  FileUploader.UploadCallback terminalCallback) {
        if (IN_FLIGHT.contains(fileUri)) {
            Log.i(TAG, "Upload already in flight: " + fileUri);
            return EnqueueResult.ALREADY_IN_FLIGHT;
        }
        cancelPendingRetry(fileUri);
        ATTEMPTS.remove(fileUri);
        if (terminalCallback != null) {
            TERMINAL_CALLBACKS.put(fileUri, terminalCallback);
        } else {
            TERMINAL_CALLBACKS.remove(fileUri);
        }
        uploadWithRetry(appContext, activity, fileUri, sessionUpload);
        return EnqueueResult.STARTED;
    }

    private static void uploadWithRetry(Context appContext, Activity activity,
                                        String fileUri, boolean sessionUpload) {
        if (IN_FLIGHT.contains(fileUri)) {
            return;
        }

        int attempt = incrementAttempt(fileUri);
        if (attempt > MAX_ATTEMPTS) {
            String msg = "Max retry attempts reached";
            Log.e(TAG, msg + ": " + fileUri);
            FileLogger.e(TAG, (sessionUpload ? "Session file upload failed: "
                    : "Pending upload failed: ") + msg);
            notifyTerminal(fileUri, false, msg);
            return;
        }

        IN_FLIGHT.add(fileUri);

        if (!NetworkHelper.isNetworkAvailable(appContext)) {
            IN_FLIGHT.remove(fileUri);
            Log.w(TAG, "No network (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + fileUri);
            FileLogger.logWarningEvent("UPLOAD", "UPLOAD_WAIT_NETWORK",
                    "uri=" + fileUri + " attempt=" + attempt);
            onAttemptFailed(appContext, activity, fileUri, sessionUpload, attempt, "No network");
            return;
        }

        FileUploader.UploadCallback callback = new FileUploader.UploadCallback() {
            @Override
            public void onUploadComplete(boolean success, String message) {
                IN_FLIGHT.remove(fileUri);
                if (success) {
                    ATTEMPTS.remove(fileUri);
                    cancelPendingRetry(fileUri);
                    boolean moved = FileUploader.moveFileToUploadedFolderFromUri(appContext, fileUri);
                    if (moved) {
                        FileLogger.i(TAG, "Upload OK, moved to Uploaded: " + fileUri);
                    } else {
                        FileLogger.w(TAG, "Upload OK but move failed: " + fileUri);
                    }
                    notifyTerminal(fileUri, true, message);
                    return;
                }

                onAttemptFailed(appContext, activity, fileUri, sessionUpload, attempt, message);
            }
        };

        try {
            Activity live = aliveActivity(activity);
            if (live != null) {
                FileUploader.uploadFile(live, fileUri, callback);
            } else {
                FileUploader.uploadFile(fileUri, callback);
            }
        } catch (RuntimeException e) {
            IN_FLIGHT.remove(fileUri);
            Log.e(TAG, "Upload threw before enqueue: " + fileUri, e);
            onAttemptFailed(appContext, activity, fileUri, sessionUpload, attempt,
                    e.getMessage() != null ? e.getMessage() : "Upload error");
        }
    }

    private static void onAttemptFailed(Context appContext, Activity activity, String fileUri,
                                        boolean sessionUpload, int attempt, String message) {
        Log.w(TAG, "Upload failed (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + message);
        if (attempt < MAX_ATTEMPTS) {
            scheduleRetry(appContext, activity, fileUri, sessionUpload);
            return;
        }
        String msg = "Max retry attempts reached";
        Log.e(TAG, msg + ": " + fileUri);
        FileLogger.e(TAG, (sessionUpload ? "Session file upload failed: "
                : "Pending upload failed: ") + msg);
        notifyTerminal(fileUri, false, msg);
    }

    private static void scheduleRetry(Context appContext, Activity activity,
                                      String fileUri, boolean sessionUpload) {
        cancelPendingRetry(fileUri);
        Runnable retry = () -> {
            PENDING_RETRIES.remove(fileUri);
            uploadWithRetry(appContext, activity, fileUri, sessionUpload);
        };
        PENDING_RETRIES.put(fileUri, retry);
        MAIN.postDelayed(retry, RETRY_DELAY_MS);
    }

    private static void cancelPendingRetry(String fileUri) {
        Runnable retry = PENDING_RETRIES.remove(fileUri);
        if (retry != null) {
            MAIN.removeCallbacks(retry);
        }
    }

    private static void notifyTerminal(String fileUri, boolean success, String message) {
        if (!success) {
            ATTEMPTS.remove(fileUri);
            cancelPendingRetry(fileUri);
        }
        FileUploader.UploadCallback callback = TERMINAL_CALLBACKS.remove(fileUri);
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onUploadComplete(success, message));
    }

    private static Activity aliveActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    private static int incrementAttempt(String fileUri) {
        return ATTEMPTS.computeIfAbsent(fileUri, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
