package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.ContentResolver;
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

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

    private static List<String[]> tempBuffer = new ArrayList<>();
    private static boolean enableChunkFile1 = false;
    private static int timeInterval1 = 1;
    private static final String STREAM_MODEL_FOLDER = "StreamModel";

    private static long splitInterval = DEFAULT_SPLIT_INTERVAL;
    private static long nextSplitTime;

    private static WeakReference<Activity> weakActivity;

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

    public static void setSplitInterval(int hours) {
        splitInterval = hours * 60 * 60 * 1000L;
        if (startTimestamp > 0) {
            nextSplitTime = startTimestamp + splitInterval;
        }
    }

    public static boolean isRecordingActive() {
        return currentWriter != null && currentFileUri != null;
    }

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

    private static void renameMediaStoreEntry(Uri uri, String newFileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, newFileName);
        values.put(MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/");
        int updated = appContext.getContentResolver().update(uri, values, null, null);
        Log.i(TAG, "MediaStore rename to [" + newFileName + "] rows updated: " + updated);
        OximeterDataFileIndex.notifyUriUpdated(appContext, uri);
        AppRecordingRegistry.registerCompletedRecording(appContext, uri, newFileName);
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

    public static void startNewRecording(long packageReceivedTimestamp, String patientIdentifier,
                                         boolean enableChunkFile, int timeInterval) {
        synchronized (SPLIT_LOCK) {
            closeCurrentWriter();

            startTimestamp = packageReceivedTimestamp;
            patientCode = patientIdentifier;
            nextSplitTime = startTimestamp + splitInterval;

            enableChunkFile1 = enableChunkFile;
            timeInterval1 = timeInterval;

            openNewUnknownFile();
        }
    }

    private static void openNewUnknownFile() {
        String tempFileName = generateFileName(startTimestamp, "unknown");
        String relPath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME;
        currentFileUri = createMediaStoreEntry(tempFileName, relPath);

        if (currentFileUri == null) {
            Log.e(TAG, "Failed to create MediaStore entry for new recording");
            return;
        }

        try {
            currentOutputStream = appContext.getContentResolver().openOutputStream(currentFileUri, "w");
            if (currentOutputStream == null) {
                Log.e(TAG, "Failed to open output stream for new recording");
                currentFileUri = null;
                return;
            }
            currentWriter = new CSVWriter(new OutputStreamWriter(currentOutputStream));
            writeHeader(currentWriter);
            Log.i(TAG, "New recording started: " + currentFileUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating new CSV file", e);
            currentWriter = null;
            currentOutputStream = null;
        }
    }

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
        renameMediaStoreEntry(currentFileUri, completedFileName);
        OximeterDataFileIndex.notifyRecordingSaved(appContext, completedUri, completedFileName);
        Log.i(TAG, "File split: " + completedFileName);
        FileLogger.i(TAG, "File split: " + completedFileName);

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

            String relPath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/" + STREAM_MODEL_FOLDER;
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
            renameMediaStoreEntry(currentFileUri, newFileName);

            Log.i(TAG, "Recording ended successfully: " + currentFileUri);
            String uriStr = currentFileUri.toString();
            OximeterDataFileIndex.notifyRecordingSaved(appContext, Uri.parse(uriStr), newFileName);
            currentFileUri = null;
            startTimestamp = 0;
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

    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(new Date(timestamp));
    }
}
