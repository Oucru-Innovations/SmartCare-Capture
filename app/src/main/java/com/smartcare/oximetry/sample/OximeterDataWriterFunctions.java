package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

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
    private static Uri currentFileUri;
    private static CSVWriter currentWriter;
    private static OximeterDataBuffer dataBuffer;
    private static long startTimestamp;
    private static String patientCode;
    private static Context appContext;

    // Chunking related fields
    private static final int CHUNK_SIZE = 10;
    private static List<String[]> tempBuffer = new ArrayList<>();
    private static final String CHUNK_FILE_NAME = "chunk_file.csv";
    private static boolean enableChunkFile1 = false;
    private static int timeInterval1 = 1;
    private static Uri lastChunkFileUri = null;
    private static final String STREAM_MODEL_FOLDER = "StreamModel";

    // File splitting
    private static final long DEFAULT_SPLIT_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours
    private static long splitInterval = DEFAULT_SPLIT_INTERVAL;
    private static long nextSplitTime;

    private static Activity currentActivity;
    private static WeakReference<Activity> weakActivity;

//    public static void setActivity(Activity activity) {
//        currentActivity = activity;
//        appContext = activity.getApplicationContext();
//    }
    //new
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

    // Add a helper method to safely get context
    private static Context getContext() {
        if (weakActivity != null && weakActivity.get() != null) {
            return weakActivity.get().getApplicationContext();
        }
        return null;
    }
    public static void setSplitInterval(int hours) {
        splitInterval = hours * 60 * 60 * 1000L;
        if (startTimestamp > 0) {
            nextSplitTime = startTimestamp + splitInterval;
        }
    }

    public static void startNewRecording(long packageReceivedTimestamp, String patientIdentifier,
                                         boolean enableChunkFile, int timeInterval) {
        closeCurrentWriter();

        startTimestamp = packageReceivedTimestamp;
        patientCode = patientIdentifier;
        nextSplitTime = startTimestamp + splitInterval;

        enableChunkFile1 = enableChunkFile;
        timeInterval1 = timeInterval;

        currentFileUri = createFileWithTempName(startTimestamp);
        dataBuffer = new OximeterDataBuffer();

        try {
            OutputStream outputStream = appContext.getContentResolver()
                    .openOutputStream(currentFileUri, "w");
            currentWriter = new CSVWriter(new OutputStreamWriter(outputStream));
            writeHeader(currentWriter);
            Log.i(TAG, "New recording started: " + currentFileUri);
        } catch (IOException e) {
            Log.e(TAG, "Error creating new CSV file", e);
            currentWriter = null;
        }
    }

    private static Uri createFileWithTempName(long startTimestamp) {
        ContentValues values = new ContentValues();
        String fileName = generateFileName(startTimestamp, "temp");

        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/" + FOLDER_NAME);

        ContentResolver resolver = appContext.getContentResolver();
        return resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
    }

    public static void writeProcessedDataPoint(OximeterDataBuffer.ProcessedData data) {
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

            if (convertFromISOTimestamp(data.timestamp) >= nextSplitTime) {
                splitFile(convertFromISOTimestamp(data.timestamp));
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

    private static void splitFile(long timestamp) throws IOException {
        currentWriter.flush();
        currentWriter.close();

        // Rename current file
        String newFileName = generateFileName(startTimestamp, formatTimestamp(timestamp));
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, newFileName);
        appContext.getContentResolver().update(currentFileUri, values, null, null);

        Uri completedFileUri = currentFileUri;

        // Start new file
        startTimestamp = timestamp;
        nextSplitTime = timestamp + splitInterval;
        currentFileUri = createFileWithTempName(timestamp);

        OutputStream outputStream = appContext.getContentResolver()
                .openOutputStream(currentFileUri, "w");
        currentWriter = new CSVWriter(new OutputStreamWriter(outputStream));
        writeHeader(currentWriter);

        Log.i(TAG, "File split successful. New file: " + currentFileUri);

        if (currentActivity != null) {
            FileUploader.uploadFile(currentActivity, getPathFromUri(completedFileUri),
                    new FileUploader.UploadCallback() {
                        @Override
                        public void onUploadComplete(boolean success, String message) {
                            if (success) {
                                Log.i(TAG, "Split file uploaded successfully");
                                FileUploader.moveFileToUploadedFolderFromUri(appContext, getPathFromUri(completedFileUri));
                            } else {
                                Log.e(TAG, "Failed to upload split file");
                            }
                        }
                    });
        }
    }

    private static void writeChunkToFile() {
        try {
            long currentTime = System.currentTimeMillis();
            long startTime = currentTime - (timeInterval1 * 60 * 1000);

            String chunkFileName = String.format("SmartCareCsvChunk_%s_%s_%s.csv",
                    patientCode,
                    formatTimestamp(startTime),
                    formatTimestamp(currentTime));

            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, chunkFileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    "Download/" + FOLDER_NAME + "/" + STREAM_MODEL_FOLDER);

            Uri chunkFileUri = appContext.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (chunkFileUri != null) {
                OutputStream outputStream = appContext.getContentResolver()
                        .openOutputStream(chunkFileUri);
                CSVWriter chunkWriter = new CSVWriter(new OutputStreamWriter(outputStream));

                writeHeader(chunkWriter);
                for (String[] row : tempBuffer) {
                    chunkWriter.writeNext(row);
                }
                chunkWriter.flush();
                chunkWriter.close();

                Log.i(TAG, "New chunk file created: " + chunkFileUri);
                lastChunkFileUri = chunkFileUri;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing chunk to file", e);
        }
    }

    public static String endRecording() {
        long endTimestamp = System.currentTimeMillis();
        closeCurrentWriter();

        String newFileName = generateFileName(startTimestamp, formatTimestamp(endTimestamp));
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, newFileName);

        appContext.getContentResolver().update(currentFileUri, values, null, null);
        Log.i(TAG, "Recording ended: " + currentFileUri);

        return getPathFromUri(currentFileUri);
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
    }

    private static String getPathFromUri(Uri uri) {
        return uri.toString();
    }

    // Helper methods remain unchanged
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

    private static long convertFromISOTimestamp(String isoTimestamp) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX");
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoTimestamp, formatter);
            return offsetDateTime.toInstant().toEpochMilli();
        } catch (Exception e) {
            Log.e(TAG, "Error converting ISO format to timestamp", e);
            return System.currentTimeMillis();
        }
    }

    private static String generateFileName(long startTimestamp, String endTimestampPart) {
        String prefix = "SmartCareCsv";
        String startTime = formatTimestamp(startTimestamp);
        return String.format("%s_%s_%s_%s.csv", prefix, patientCode, startTime, endTimestampPart);
    }

    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        return dateFormat.format(new Date(timestamp));
    }
}