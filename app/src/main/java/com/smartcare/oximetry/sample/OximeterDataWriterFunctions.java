package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.opencsv.CSVWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Quản lý phiên ghi CSV đang hoạt động: tạo file trong MediaStore Download/OximeterData,
 * ghi header + các dòng dữ liệu, và xoay (rotate) segment theo thời gian / khi BLE tạm dừng /
 * mốc cắt hằng ngày (daily cutoff). Đặt tên file tạm là {@code _unknown} khi đang ghi và đổi
 * tên về thời điểm kết thúc thật khi đóng segment.
 */
public class OximeterDataWriterFunctions {
    private static final String FOLDER_NAME = "OximeterData";
    private static final String TAG = "OximeterDataWriter";

    private static final long DEFAULT_SPLIT_INTERVAL = RecordingConstants.SPLIT_INTERVAL_MS;
    /** Minimum recording duration before an automatic (non-daily) split. */
    private static final long MIN_AUTO_SPLIT_MS = RecordingConstants.MIN_SEGMENT_MS;

    private static final Object SPLIT_LOCK = new Object();

    private static Uri currentFileUri;
    private static CSVWriter currentWriter;
    private static OutputStream currentOutputStream;

    private static long startTimestamp;
    private static String patientCode;
    private static Context appContext;

    /** Number of data rows (excluding header) written into the currently open segment. */
    private static long currentSegmentDataRows;

    private static List<String[]> tempBuffer = new ArrayList<>();
    private static boolean enableChunkFile1 = false;
    private static int timeInterval1 = 1;
    private static final String STREAM_MODEL_FOLDER = "StreamModel";

    private static long splitInterval = DEFAULT_SPLIT_INTERVAL;
    private static long nextSplitTime;

    private static WeakReference<Activity> weakActivity;

    /** Lưu application context (tránh leak Activity) để dùng cho ContentResolver/MediaStore. */
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void setActivity(Activity activity) {
        if (activity != null) {
            weakActivity = new WeakReference<>(activity);
        } else {
            weakActivity = null;
        }
    }

    /**
     * Đặt khoảng thời gian tự động xoay file (theo giờ) và cập nhật mốc split kế tiếp nếu phiên
     * đang chạy. Cho phép cấu hình độ dài segment thay vì giá trị mặc định.
     */
    public static void setSplitInterval(int hours) {
        splitInterval = hours * 60 * 60 * 1000L;
        if (startTimestamp > 0) {
            nextSplitTime = startTimestamp + splitInterval;
        }
    }

    /** True nếu đang có một segment CSV mở (writer và URI đều hợp lệ). */
    public static boolean isRecordingActive() {
        return currentWriter != null && currentFileUri != null;
    }

    /** URI (dạng String) của file segment đang mở, hoặc null nếu không có phiên nào đang ghi. */
    public static String getCurrentOpenFileUriString() {
        return currentFileUri != null ? currentFileUri.toString() : null;
    }

    /** Duration of the currently open CSV segment (unknown file), in milliseconds. */
    public static long getRecordingSegmentDurationMs() {
        synchronized (SPLIT_LOCK) {
            if (!isRecordingActive() || startTimestamp <= 0) {
                return 0L;
            }
            return System.currentTimeMillis() - startTimestamp;
        }
    }

    /**
     * Close the current segment when BLE reports {@code MESSAGE_RECORDING_ENDED} (brief drop).
     * Opens a new unknown file so the session can continue after reconnect without losing the night.
     *
     * @return display name of the completed segment, or null if nothing was split
     */
    public static String splitFileOnBlePause() {
        synchronized (SPLIT_LOCK) {
            if (currentWriter == null || currentFileUri == null) {
                return null;
            }
            try {
                long now = System.currentTimeMillis();
                if ((now - startTimestamp) < MIN_AUTO_SPLIT_MS) {
                    Log.d(TAG, "Skipping BLE pause split: segment too short");
                    return null;
                }
                String completedName = generateFileName(startTimestamp, formatTimestamp(now));
                splitFileLocked(now, false);
                Log.i(TAG, "File split (BLE pause): " + completedName);
                FileLogger.i(TAG, "File split (BLE pause): " + completedName);
                return completedName;
            } catch (IOException e) {
                Log.e(TAG, "Error during BLE pause split", e);
                FileLogger.e(TAG, "BLE pause split failed", e);
                return null;
            }
        }
    }

    private static Uri createMediaStoreEntry(String fileName, String relativePath) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);

        ContentResolver resolver = appContext.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        Log.i(TAG, "MediaStore entry created: " + uri + " at " + relativePath + "/" + fileName);
        return uri;
    }

    /**
     * Renames a MediaStore entry to its completed name. Returns {@code true} on success.
     *
     * <p>The target entry can disappear out from under us if the file was uploaded/moved/deleted
     * (e.g. the user tapped upload on it in the file manager) while it was still the open segment.
     * In that case {@link ContentResolver#update} throws {@code IllegalStateException}
     * (wrapping {@code FileNotFoundException}). This must never crash the recording, so all
     * failures are swallowed and reported via the return value instead.
     */
    private static boolean renameMediaStoreEntry(Uri uri, String newFileName) {
        if (uri == null || appContext == null) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, newFileName);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                OximeterDataFileIndex.pendingRelativePath());
        try {
            int updated = appContext.getContentResolver().update(uri, values, null, null);
            if (updated <= 0) {
                Log.w(TAG, "MediaStore rename to [" + newFileName + "] affected 0 rows; uri=" + uri);
                FileLogger.w(TAG, "MediaStore rename affected 0 rows (entry missing?): " + newFileName);
                return false;
            }
            Log.i(TAG, "MediaStore rename to [" + newFileName + "] rows updated: " + updated);
            OximeterDataFileIndex.notifyUriUpdated(appContext, uri);
            AppRecordingRegistry.registerCompletedRecording(appContext, uri, newFileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to rename MediaStore entry (entry gone?): " + uri, e);
            FileLogger.e(TAG, "MediaStore rename failed for " + newFileName + " (entry gone?)", e);
            return false;
        }
    }

    public static String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        try (Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        }
        return null;
    }

    /**
     * Bắt đầu một phiên ghi mới: đóng/chốt segment còn mở (nếu có), dọn dẹp các file {@code _unknown}
     * mồ côi do crash/mất điện trước đó, rồi mở file {@code _unknown} mới. Phải chạy đồng bộ để
     * tên file mới không đụng với orphan đang tồn tại.
     */
    public static void startNewRecording(long packageReceivedTimestamp, String patientIdentifier,
                                         boolean enableChunkFile, int timeInterval) {
        synchronized (SPLIT_LOCK) {
            // Finalize any segment still open in THIS process (rename if it holds data, delete if
            // empty) instead of silently abandoning it as a leftover "_unknown" file.
            finalizeCurrentSegmentLocked(System.currentTimeMillis());

            patientCode = patientIdentifier;

            // Recover "_unknown" files left on disk by a previous crash / power loss: rename the
            // ones that already contain data to their real end time, delete empty placeholders.
            // Must run before opening a new file so a regenerated name cannot collide with an
            // existing orphan (the collision is what produced "_unknown (1).csv").
            reconcileOrphanUnknownFilesLocked();

            startTimestamp = packageReceivedTimestamp;
            nextSplitTime = startTimestamp + splitInterval;

            enableChunkFile1 = enableChunkFile;
            timeInterval1 = timeInterval;

            openNewUnknownFile();
        }
    }

    private static void openNewUnknownFile() {
        String tempFileName = generateFileName(startTimestamp, "unknown");
        String relPath = OximeterDataFileIndex.pendingRelativePath();
        currentFileUri = createMediaStoreEntry(tempFileName, relPath);

        if (currentFileUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for new recording");
            return;
        }

        try {
            currentOutputStream = appContext.getContentResolver().openOutputStream(currentFileUri, "w");
            if (currentOutputStream == null) {
                Log.e(TAG, "Failed to open output stream for new recording");
                deleteMediaStoreEntryQuietly(currentFileUri, "openOutputStream returned null");
                currentFileUri = null;
                return;
            }
            currentWriter = new CSVWriter(new OutputStreamWriter(currentOutputStream));
            writeHeader(currentWriter);
            // Flush the header immediately so the file is never a 0-byte placeholder, even if the
            // process is killed before the first data sample arrives.
            currentWriter.flush();
            currentSegmentDataRows = 0;
            Log.i(TAG, "New recording started: " + currentFileUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating new CSV file", e);
            closeCurrentWriter();
            deleteMediaStoreEntryQuietly(currentFileUri, "writer/header initialization failed");
            currentFileUri = null;
            currentWriter = null;
            currentOutputStream = null;
        }
    }

    private static void deleteMediaStoreEntryQuietly(Uri uri, String reason) {
        if (uri == null || appContext == null) {
            return;
        }
        try {
            int deleted = appContext.getContentResolver().delete(uri, null, null);
            Log.w(TAG, "Deleted incomplete unknown file entry (" + reason + "), rows=" + deleted
                    + ", uri=" + uri);
        } catch (Exception e) {
            Log.w(TAG, "Failed to delete incomplete unknown file entry: " + uri, e);
        }
    }

    /**
     * Closes the segment currently open in this process and either renames it to its real end
     * time (when it holds data) or deletes it (when it is just an empty placeholder). This is the
     * in-memory counterpart of {@link #reconcileOrphanUnknownFilesLocked()} and prevents leaving a
     * dangling "_unknown" file behind when a new recording starts without a clean end/split.
     */
    private static void finalizeCurrentSegmentLocked(long endTs) {
        if (currentFileUri == null) {
            closeCurrentWriter();
            return;
        }

        boolean hadData = currentSegmentDataRows > 0;
        closeCurrentWriter();

        Uri uri = currentFileUri;
        currentFileUri = null;

        if (!hadData) {
            deleteMediaStoreEntryQuietly(uri, "finalizing empty open segment (no data rows)");
            return;
        }

        String endPart = formatTimestamp(endTs);
        if (formatTimestamp(startTimestamp).equals(endPart)) {
            endPart = formatTimestamp(endTs + 1000L);
        }
        String completedName = generateFileName(startTimestamp, endPart);
        if (renameMediaStoreEntry(uri, completedName)) {
            OximeterDataFileIndex.notifyRecordingSaved(appContext, uri, completedName);
            Log.i(TAG, "Finalized open segment on new recording: " + completedName);
            FileLogger.i(TAG, "Finalized open segment: " + completedName);
        } else {
            Log.w(TAG, "Failed to finalize open segment (entry gone?): " + completedName);
            FileLogger.w(TAG, "Failed to finalize open segment (entry gone?): " + completedName);
        }
    }

    /**
     * Scans the OximeterData folder for leftover "_unknown" files (created when the app was killed
     * or the tablet lost power mid-recording) and resolves each one safely:
     * <ul>
     *   <li>0-byte / header-only files are deleted (failed placeholders, no data lost).</li>
     *   <li>Files that already contain data are renamed to {@code _<lastSampleTime>.csv} using the
     *       last timestamp found inside the file, so the data is preserved and a new segment can
     *       start cleanly without an identical name colliding (which produced "_unknown (1)").</li>
     * </ul>
     * The currently open file (if any) is never touched.
     */
    private static void reconcileOrphanUnknownFilesLocked() {
        if (appContext == null) {
            return;
        }
        ContentResolver resolver = appContext.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
        };
        String selection = MediaStore.Downloads.RELATIVE_PATH + " LIKE ? AND "
                + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
        String[] args = { "%" + FOLDER_NAME + "%", "%unknown%.csv" };

        try (Cursor cursor = resolver.query(collection, projection, selection, args, null)) {
            if (cursor == null) {
                return;
            }
            int idIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            int sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE);
            int modIdx = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIdx);
                if (name == null || !name.contains("_unknown")) {
                    continue;
                }
                if (patientCode != null && !name.contains("_" + patientCode + "_")) {
                    continue;
                }
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(idIdx));
                if (currentFileUri != null && uri.equals(currentFileUri)) {
                    continue;
                }
                finalizeOneOrphanUnknownFile(uri, name, cursor.getLong(sizeIdx), cursor.getLong(modIdx));
            }
        } catch (Exception e) {
            Log.w(TAG, "reconcileOrphanUnknownFiles failed", e);
            FileLogger.w(TAG, "reconcileOrphanUnknownFiles failed: " + e.getMessage());
        }
    }

    private static void finalizeOneOrphanUnknownFile(Uri uri, String name, long sizeBytes,
                                                     long modSeconds) {
        if (sizeBytes <= 0L) {
            deleteMediaStoreEntryQuietly(uri, "orphan unknown file empty (0 bytes): " + name);
            FileLogger.logEvent("FILES", "ORPHAN_UNKNOWN_DELETED", "empty | " + name);
            return;
        }

        String lastTimestampField = null;
        int dataRows = 0;
        try (InputStream in = appContext.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                dataRows++;
                String field = firstCsvField(line);
                if (field != null && !field.isEmpty()) {
                    lastTimestampField = field;
                }
            }
        } catch (Exception e) {
            // Could not read the file: be conservative and keep it (it may contain real data).
            Log.w(TAG, "Failed to read orphan unknown file: " + name, e);
            FileLogger.w(TAG, "Failed to read orphan unknown file, kept as-is: " + name);
            return;
        }

        if (dataRows == 0) {
            deleteMediaStoreEntryQuietly(uri, "orphan unknown file header-only: " + name);
            FileLogger.logEvent("FILES", "ORPHAN_UNKNOWN_DELETED", "header-only | " + name);
            return;
        }

        String endStamp = isoToFileStamp(lastTimestampField);
        if (endStamp == null && modSeconds > 0) {
            endStamp = formatTimestamp(modSeconds * 1000L);
        }
        if (endStamp == null) {
            endStamp = formatTimestamp(System.currentTimeMillis());
        }

        String completedName = completedNameFromUnknown(name, endStamp);
        if (completedName == null) {
            return;
        }
        if (renameMediaStoreEntry(uri, completedName)) {
            OximeterDataFileIndex.notifyRecordingSaved(appContext, uri, completedName);
            Log.i(TAG, "Finalized orphan unknown file -> " + completedName);
            FileLogger.logEvent("FILES", "ORPHAN_UNKNOWN_FINALIZED",
                    name + " -> " + completedName + " rows=" + dataRows);
        } else {
            Log.w(TAG, "Failed to rename orphan unknown file (entry gone?): " + name);
            FileLogger.w(TAG, "Failed to rename orphan unknown file (entry gone?): " + name);
        }
    }

    /** Extracts the first (timestamp) column from a CSVWriter-formatted line. */
    private static String firstCsvField(String line) {
        if (line == null) {
            return null;
        }
        String s = line.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.charAt(0) == '"') {
            int end = s.indexOf('"', 1);
            return end > 1 ? s.substring(1, end) : null;
        }
        int comma = s.indexOf(',');
        return comma >= 0 ? s.substring(0, comma) : s;
    }

    /**
     * Converts a CSV ISO timestamp ("yyyy-MM-dd HH:mm:ss...") into the file-name stamp format
     * ("dd.MM.yyyy.HH.mm.ss"). Returns null if the value cannot be parsed.
     */
    private static String isoToFileStamp(String iso) {
        if (iso == null || iso.length() < 19) {
            return null;
        }
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            parser.setTimeZone(TimeZone.getDefault());
            Date parsed = parser.parse(iso.substring(0, 19));
            return parsed != null ? formatTimestamp(parsed.getTime()) : null;
        } catch (ParseException e) {
            return null;
        }
    }

    /** Builds the completed name by replacing the trailing "_unknown[...]" part with the end time. */
    private static String completedNameFromUnknown(String displayName, String endStamp) {
        if (displayName == null) {
            return null;
        }
        int idx = displayName.indexOf("_unknown");
        if (idx < 0) {
            return null;
        }
        return displayName.substring(0, idx) + "_" + endStamp + ".csv";
    }

    /**
     * Ghi một dòng dữ liệu đã gom vào segment hiện tại; kiểm tra mốc split theo thời gian trước khi
     * ghi để xoay file khi cần, và đệm thêm cho chunk file streaming nếu tính năng được bật.
     */
    public static void writeProcessedDataPoint(OximeterDataBuffer.ProcessedData data) {
        synchronized (SPLIT_LOCK) {
            if (currentWriter == null) {
                Log.e(TAG, "No active recording session");
                return;
            }

            try {
                String[] csvData = {
                        String.valueOf(data.timestamp),
                        data.deviceId,
                        String.valueOf(data.battery),
                        String.valueOf(data.hr),
                        String.valueOf(data.o2),
                        data.spo2Status.toString(),
                        data.pleth.toString(),
                        data.red.toString(),
                        data.ir.toString(),
                        data.perfusion.toString()
                };

                long now = System.currentTimeMillis();
                if (now >= nextSplitTime && (now - startTimestamp) >= MIN_AUTO_SPLIT_MS) {
                    splitFileLocked(now, false);
                }

                if (currentWriter == null) {
                    return;
                }

                currentWriter.writeNext(csvData);
                currentWriter.flush();
                currentSegmentDataRows++;

                if (enableChunkFile1) {
                    tempBuffer.add(csvData);
                    if (tempBuffer.size() >= timeInterval1 * 60) {
                        writeChunkToFile();
                        tempBuffer.clear();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing processed data", e);
            }
        }
    }

    /**
     * Daily 17:00 cutoff: close current file and open a new one without stopping the service.
     */
    public static void splitForDailyCutoff() {
        synchronized (SPLIT_LOCK) {
            if (currentWriter == null || currentFileUri == null) {
                Log.w(TAG, "No active recording for daily cutoff split");
                return;
            }
            try {
                long end = System.currentTimeMillis();
                Log.i(TAG, "Daily cutoff split at: " + formatTimestamp(end));
                splitFileLocked(end, true);
            } catch (IOException e) {
                Log.e(TAG, "Error during daily cutoff split", e);
            }
        }
    }

    /**
     * Logic cốt lõi của việc xoay segment (phải gọi khi đang giữ {@link #SPLIT_LOCK}): đóng writer
     * hiện tại, đổi tên file về thời điểm kết thúc rồi mở segment {@code _unknown} mới. Luôn mở
     * segment mới kể cả khi đổi tên thất bại để việc thu dữ liệu không bị gián đoạn.
     */
    private static void splitFileLocked(long timestamp, boolean dailyCutoff) throws IOException {
        if (currentWriter == null || currentFileUri == null) {
            return;
        }

        String endFormatted = formatTimestamp(timestamp);
        String startFormatted = formatTimestamp(startTimestamp);
        if (startFormatted.equals(endFormatted)) {
            Log.w(TAG, "Skipping split: start and end share the same second (" + endFormatted + ")");
            return;
        }

        if (!dailyCutoff && (timestamp - startTimestamp) < MIN_AUTO_SPLIT_MS) {
            Log.w(TAG, "Skipping auto split: recording too short");
            return;
        }

        currentWriter.flush();
        currentWriter.close();
        currentOutputStream.close();
        currentWriter = null;
        currentOutputStream = null;

        String completedFileName = generateFileName(startTimestamp, endFormatted);
        Uri completedUri = currentFileUri;
        boolean renamed = renameMediaStoreEntry(currentFileUri, completedFileName);
        if (renamed) {
            OximeterDataFileIndex.notifyRecordingSaved(appContext, completedUri, completedFileName);
            Log.i(TAG, "File split: " + completedFileName);
            FileLogger.i(TAG, "File split: " + completedFileName);
        } else {
            // The previous segment's entry is gone (uploaded/moved/deleted while open). Drop the
            // stale reference and keep recording into a fresh segment instead of crashing.
            Log.w(TAG, "File split rename failed; opening a new segment anyway: " + completedFileName);
            FileLogger.w(TAG, "File split rename failed (entry gone); opened new segment: "
                    + completedFileName);
        }

        // Always advance to a new segment so data collection continues regardless of rename result.
        currentFileUri = null;
        startTimestamp = timestamp;
        nextSplitTime = timestamp + splitInterval;
        openNewUnknownFile();
    }

    private static void writeChunkToFile() {
        try {
            long currentTime = System.currentTimeMillis();
            long startTime = currentTime - (timeInterval1 * 60 * 1000);

            String chunkFileName = String.format(Locale.US, "SmartCareCsvChunk_%s_%s_%s.csv",
                    patientCode,
                    formatTimestamp(startTime),
                    formatTimestamp(currentTime));

            String relPath = OximeterDataFileIndex.pendingRelativePath() + STREAM_MODEL_FOLDER + "/";
            Uri chunkUri = createMediaStoreEntry(chunkFileName, relPath);

            if (chunkUri != null) {
                OutputStream out = appContext.getContentResolver().openOutputStream(chunkUri);
                if (out != null) {
                    CSVWriter chunkWriter = new CSVWriter(new OutputStreamWriter(out));
                    writeHeader(chunkWriter);
                    for (String[] row : tempBuffer) {
                        chunkWriter.writeNext(row);
                    }
                    chunkWriter.flush();
                    chunkWriter.close();
                    Log.i(TAG, "New chunk file created: " + chunkUri);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing chunk to file", e);
        }
    }

    /**
     * Kết thúc phiên ghi: đóng writer và đổi tên file {@code _unknown} về thời điểm kết thúc thật.
     * Trả về URI (String) của file đã hoàn tất để uploader xử lý, hoặc null nếu entry đã biến mất
     * (bị upload/di chuyển/xóa khi còn đang mở).
     */
    public static String endRecording() {
        synchronized (SPLIT_LOCK) {
            long endTimestamp = System.currentTimeMillis();
            closeCurrentWriter();

            if (currentFileUri == null) {
                Log.e(TAG, "Cannot end recording: currentFileUri is null");
                return null;
            }

            String endPart = formatTimestamp(endTimestamp);
            if (formatTimestamp(startTimestamp).equals(endPart)) {
                endPart = formatTimestamp(endTimestamp + 1000L);
            }
            String newFileName = generateFileName(startTimestamp, endPart);
            Log.i(TAG, "Attempting to rename to: " + newFileName);
            boolean renamed = renameMediaStoreEntry(currentFileUri, newFileName);

            String uriStr = currentFileUri.toString();
            currentFileUri = null;
            startTimestamp = 0;

            if (!renamed) {
                // Entry vanished (uploaded/moved/deleted while open): nothing safe to hand back to
                // the uploader. Avoid returning a URI that no longer resolves.
                Log.w(TAG, "Recording end rename failed (entry gone?): " + uriStr);
                FileLogger.w(TAG, "Recording end rename failed; entry may be gone: " + newFileName);
                return null;
            }

            Log.i(TAG, "Recording ended successfully: " + uriStr);
            OximeterDataFileIndex.notifyRecordingSaved(appContext, Uri.parse(uriStr), newFileName);
            return uriStr;
        }
    }

    private static void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing CSV writer", e);
            }
            currentWriter = null;
        }
        if (currentOutputStream != null) {
            try {
                currentOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing output stream", e);
            }
            currentOutputStream = null;
        }
    }

    public static String getOximeterDataPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath() + "/" + FOLDER_NAME;
    }

    public static String getStreamModelPath() {
        return getOximeterDataPath() + "/" + STREAM_MODEL_FOLDER;
    }

    private static void writeHeader(CSVWriter writer) {
        String[] header = {
                "timestamp",
                "device_id",
                "battery",
                "hr",
                "o2",
                "spo2_status",
                "pleth",
                "red",
                "ir",
                "perfusion"
        };
        writer.writeNext(header);
    }

    private static String generateFileName(long startTs, String endTimestampPart) {
        return buildFileName(patientCode, startTs, endTimestampPart);
    }

    private static String buildFileName(String patientIdentifier, long startTs, String endTimestampPart) {
        String startTime = formatTimestamp(startTs);
        return String.format(Locale.US, "SmartCareCsv_%s_%s_%s.csv",
                patientIdentifier, startTime, endTimestampPart);
    }

    /** Định dạng timestamp epoch-ms thành chuỗi dùng trong tên file ("dd.MM.yyyy.HH.mm.ss"). */
    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(new Date(timestamp));
    }
}
