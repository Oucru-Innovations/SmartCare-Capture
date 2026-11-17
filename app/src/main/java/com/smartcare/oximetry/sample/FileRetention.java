package com.smartcare.oximetry.sample;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Vòng đời file sau upload / khi người dùng xoá:
 * <ul>
 *   <li>Uploaded/: tự xoá sau {@link #UPLOADED_RETENTION_DAYS} ngày kể từ lúc upload.</li>
 *   <li>Trash/: file pending bị xoá tay; tự xoá sau {@link #TRASH_RETENTION_DAYS} ngày kể từ lúc
 *       vào Trash. Không xoá tay được, chỉ khôi phục về pending.</li>
 * </ul>
 * Mốc thời gian = lastModified của file ở thư mục đích: {@link FileUploader#moveUriToFolder} luôn
 * tạo bản mới nên mốc này chính là lúc chuyển. Không cần lưu thêm metadata.
 * <p>
 * File khôi phục từ Trash được đánh dấu "không tự upload" (theo tên file) cho tới khi upload tay
 * thành công.
 */
public final class FileRetention {
    private static final String TAG = "FileRetention";

    static final int UPLOADED_RETENTION_DAYS = 15;
    static final int TRASH_RETENTION_DAYS = 30;

    private static final String PREFS = "file_retention";
    private static final String KEY_NO_AUTO_UPLOAD = "no_auto_upload_names";

    public enum TrashResult { MOVED, BUSY, FAILED }

    private FileRetention() {
    }

    /** Mốc không rõ (<= 0) thì coi như chưa hết hạn: thà giữ lâu hơn còn hơn xoá nhầm. */
    static boolean isExpired(long movedAtMs, long nowMs, int retentionDays) {
        return movedAtMs > 0 && nowMs - movedAtMs >= TimeUnit.DAYS.toMillis(retentionDays);
    }

    /** Số ngày còn lại trước khi bị tự xoá (làm tròn lên, tối thiểu 0). */
    static int daysLeft(long movedAtMs, long nowMs, int retentionDays) {
        if (movedAtMs <= 0) {
            return retentionDays;
        }
        long leftMs = movedAtMs + TimeUnit.DAYS.toMillis(retentionDays) - nowMs;
        if (leftMs <= 0) {
            return 0;
        }
        long day = TimeUnit.DAYS.toMillis(1);
        return (int) ((leftMs + day - 1) / day);
    }

    /**
     * Xoá file Uploaded quá 15 ngày và file Trash quá 30 ngày. Chạy ở luồng nền (có quét file).
     * Trả về số file đã xoá.
     */
    public static synchronized int purgeExpired(Context context) {
        if (context == null) {
            return 0;
        }
        Context app = context.getApplicationContext();
        OximeterDataFileIndex.FileLists lists = OximeterDataFileIndex.queryAllFiles(app, true);
        long now = System.currentTimeMillis();
        int deleted = purgeList(app, lists.uploaded, now, UPLOADED_RETENTION_DAYS, "UPLOADED")
                + purgeList(app, lists.trash, now, TRASH_RETENTION_DAYS, "TRASH");
        if (deleted > 0) {
            OximeterDataFileIndex.invalidateListCache();
        }
        FileLogger.logEvent("FILES", "RETENTION_PURGE", "deleted=" + deleted
                + " uploadedListed=" + lists.uploaded.size() + " trashListed=" + lists.trash.size());
        return deleted;
    }

    private static int purgeList(Context app, List<OximeterDataFileIndex.FileEntry> entries,
                                 long now, int days, String bucket) {
        int deleted = 0;
        for (OximeterDataFileIndex.FileEntry e : entries) {
            if (!isExpired(e.lastModifiedMs, now, days)) {
                continue;
            }
            if (FileUploader.deleteUri(app, e.uri)) {
                deleted++;
                clearNoAutoUpload(app, e.displayName);
                FileLogger.logEvent("FILES", "AUTO_DELETE", "bucket=" + bucket
                        + " name=" + e.displayName + " movedAtMs=" + e.lastModifiedMs);
            } else {
                FileLogger.logWarningEvent("FILES", "AUTO_DELETE_FAILED",
                        "bucket=" + bucket + " name=" + e.displayName + " uri=" + e.uri);
            }
        }
        return deleted;
    }

    /** Chạy {@link #purgeExpired} trên thread riêng (gọi được từ main thread). */
    public static void purgeExpiredAsync(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                purgeExpired(app);
            } catch (Exception e) {
                Log.e(TAG, "purgeExpired failed", e);
            }
        }, "FileRetentionPurge").start();
    }

    /** Chuyển file pending vào Trash. Từ chối file đang ghi hoặc đang upload. Gọi ở luồng nền. */
    public static TrashResult moveToTrash(Context context, String uriStr) {
        if (uriStr.equals(OximeterDataWriterFunctions.getCurrentOpenFileUriString())
                || PendingFileUploadCoordinator.isInFlight(uriStr)) {
            return TrashResult.BUSY;
        }
        String name = FileUploader.moveUriToFolder(
                context, uriStr, OximeterDataFileIndex.trashRelativePath());
        if (name == null) {
            return TrashResult.FAILED;
        }
        // Gỡ khỏi registry pending để không bị liệt kê / auto-upload lại.
        AppRecordingRegistry.markUploaded(context, uriStr);
        FileLogger.logEvent("FILES", "MOVED_TO_TRASH", "name=" + name);
        return TrashResult.MOVED;
    }

    /** Khôi phục file từ Trash về pending, đánh dấu không tự upload. Gọi ở luồng nền. */
    public static boolean restoreFromTrash(Context context, String uriStr) {
        String name = FileUploader.moveUriToFolder(
                context, uriStr, OximeterDataFileIndex.pendingRelativePath());
        if (name == null) {
            return false;
        }
        setNoAutoUpload(context, name, true);
        FileLogger.logEvent("FILES", "RESTORED_FROM_TRASH", "name=" + name);
        return true;
    }

    public static boolean isNoAutoUpload(Context context, String displayName) {
        return displayName != null && prefs(context)
                .getStringSet(KEY_NO_AUTO_UPLOAD, new HashSet<>()).contains(displayName);
    }

    static void clearNoAutoUpload(Context context, String displayName) {
        setNoAutoUpload(context, displayName, false);
    }

    private static synchronized void setNoAutoUpload(Context context, String displayName,
                                                     boolean on) {
        if (context == null || displayName == null) {
            return;
        }
        SharedPreferences p = prefs(context);
        // getStringSet trả về set nội bộ: phải copy trước khi sửa.
        Set<String> names = new HashSet<>(p.getStringSet(KEY_NO_AUTO_UPLOAD, new HashSet<>()));
        if (on ? names.add(displayName) : names.remove(displayName)) {
            p.edit().putStringSet(KEY_NO_AUTO_UPLOAD, names).apply();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
