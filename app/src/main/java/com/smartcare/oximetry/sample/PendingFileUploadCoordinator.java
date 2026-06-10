package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Context;
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

    private PendingFileUploadCoordinator() {
    }

    /**
     * Upload một file session (không kèm Activity) thông qua overload đầy đủ.
     */
    public static void uploadSessionFile(Context context, String fileUri) {
        uploadSessionFile(context, null, fileUri);
    }

    /**
     * Upload một file session kèm Activity; bỏ qua nếu fileUri trùng file đang ghi (currently-open)
     * để không làm hỏng writer đang mở.
     */
    public static void uploadSessionFile(Context context, Activity activity, String fileUri) {
        if (context == null || TextUtils.isEmpty(fileUri)) {
            return;
        }
        // Never upload the file that is still being written: moving it to the Uploaded folder
        // invalidates the writer's open URI and makes the next CSV split/end fail.
        String openUri = OximeterDataWriterFunctions.getCurrentOpenFileUriString();
        if (fileUri.equals(openUri)) {
            Log.w(TAG, "Refusing to upload the currently-open recording file: " + fileUri);
            FileLogger.logWarningEvent("UPLOAD", "UPLOAD_SKIP_OPEN_FILE", "uri=" + fileUri);
            return;
        }
        FileUploader.setAppContext(context.getApplicationContext());
        uploadWithRetry(context.getApplicationContext(), activity, fileUri, true);
    }

    /**
     * Duyệt toàn bộ file đang chờ (pending), bỏ qua file đang ghi/file rỗng và đưa phần còn lại vào hàng đợi upload.
     * Trả về số file đã được đưa vào hàng đợi.
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
            uploadWithRetry(app, null, uriStr, false);
            queued++;
        }
        FileLogger.logEvent("UPLOAD", "PENDING_BATCH_QUEUED",
                "queued=" + queued + " totalListed=" + pending.size());
        return queued;
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

    private static void uploadWithRetry(Context appContext, Activity activity,
                                        String fileUri, boolean sessionUpload) {
        if (IN_FLIGHT.contains(fileUri)) {
            return;
        }

        int attempt = incrementAttempt(fileUri);
        if (attempt > MAX_ATTEMPTS) {
            ATTEMPTS.remove(fileUri);
            String msg = "Max retry attempts reached";
            Log.e(TAG, msg + ": " + fileUri);
            FileLogger.e(TAG, (sessionUpload ? "Session file upload failed: "
                    : "Pending upload failed: ") + msg);
            return;
        }

        IN_FLIGHT.add(fileUri);

        if (!NetworkHelper.isNetworkAvailable(appContext)) {
            IN_FLIGHT.remove(fileUri);
            Log.w(TAG, "No network (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + fileUri);
            FileLogger.logWarningEvent("UPLOAD", "UPLOAD_WAIT_NETWORK",
                    "uri=" + fileUri + " attempt=" + attempt);
            scheduleRetry(appContext, activity, fileUri, sessionUpload);
            return;
        }

        FileUploader.UploadCallback callback = new FileUploader.UploadCallback() {
            @Override
            public void onUploadComplete(boolean success, String message) {
                IN_FLIGHT.remove(fileUri);
                if (success) {
                    ATTEMPTS.remove(fileUri);
                    boolean moved = FileUploader.moveFileToUploadedFolderFromUri(appContext, fileUri);
                    if (moved) {
                        FileLogger.i(TAG, "Upload OK, moved to Uploaded: " + fileUri);
                    } else {
                        FileLogger.w(TAG, "Upload OK but move failed: " + fileUri);
                    }
                    return;
                }

                Log.w(TAG, "Upload failed (attempt " + attempt + "/" + MAX_ATTEMPTS + "): " + message);
                scheduleRetry(appContext, activity, fileUri, sessionUpload);
            }
        };

        if (activity != null) {
            FileUploader.uploadFile(activity, fileUri, callback);
        } else {
            FileUploader.uploadFile(fileUri, callback);
        }
    }

    private static void scheduleRetry(Context appContext, Activity activity,
                                      String fileUri, boolean sessionUpload) {
        MAIN.postDelayed(() -> uploadWithRetry(appContext, activity, fileUri, sessionUpload),
                RETRY_DELAY_MS);
    }

    private static int incrementAttempt(String fileUri) {
        return ATTEMPTS.computeIfAbsent(fileUri, k -> new AtomicInteger(0)).incrementAndGet();
    }
}
