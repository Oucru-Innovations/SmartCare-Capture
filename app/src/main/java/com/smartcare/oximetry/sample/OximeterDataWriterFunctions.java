package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static File currentFile;
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
    private static File lastChunkFile = null;
    private static final String STREAM_MODEL_FOLDER = "StreamModel";

    // File splitting
    private static final long DEFAULT_SPLIT_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours
    private static long splitInterval = DEFAULT_SPLIT_INTERVAL;
    private static long nextSplitTime;

    private static Activity currentActivity;
    private static WeakReference<Activity> weakActivity;

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        createDirectoriesIfNeeded();
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
        return appContext;
    }

    private static void createDirectoriesIfNeeded() {
        File mainDir = getOximeterDataDirectory();
        if (!mainDir.exists()) {
            boolean created = mainDir.mkdirs();
            Log.i(TAG, "Main directory created: " + created + " at " + mainDir.getAbsolutePath());
        }

        File streamModelDir = new File(mainDir, STREAM_MODEL_FOLDER);
        if (!streamModelDir.exists()) {
            boolean created = streamModelDir.mkdirs();
            Log.i(TAG, "StreamModel directory created: " + created + " at " + streamModelDir.getAbsolutePath());
        }
    }

    private static File getOximeterDataDirectory() {
        // Use app's private external storage directory
        File externalDir = appContext.getExternalFilesDir(null);
        return new File(externalDir, FOLDER_NAME);
    }

    private static File getStreamModelDirectory() {
        return new File(getOximeterDataDirectory(), STREAM_MODEL_FOLDER);
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

        currentFile = createFileWithTempName(startTimestamp);
        dataBuffer = new OximeterDataBuffer();

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(currentFile);
            currentWriter = new CSVWriter(new OutputStreamWriter(fileOutputStream));
            writeHeader(currentWriter);
            Log.i(TAG, "New recording started: " + currentFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error creating new CSV file", e);
            currentWriter = null;
        }
    }

    private static File createFileWithTempName(long startTimestamp) {
        String fileName = generateFileName(startTimestamp, "temp");
        File directory = getOximeterDataDirectory();
        return new File(directory, fileName);
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
        File newFile = new File(currentFile.getParent(), newFileName);
        boolean renamed = currentFile.renameTo(newFile);

        if (!renamed) {
            Log.e(TAG, "Failed to rename file from " + currentFile.getName() + " to " + newFileName);
        }

        File completedFile = newFile;

        // Start new file
        startTimestamp = timestamp;
        nextSplitTime = timestamp + splitInterval;
        currentFile = createFileWithTempName(timestamp);

        FileOutputStream fileOutputStream = new FileOutputStream(currentFile);
        currentWriter = new CSVWriter(new OutputStreamWriter(fileOutputStream));
        writeHeader(currentWriter);

        Log.i(TAG, "File split successful. New file: " + currentFile.getAbsolutePath());

        if (currentActivity != null) {
            FileUploader.uploadFile(currentActivity, completedFile.getAbsolutePath(),
                    new FileUploader.UploadCallback() {
                        @Override
                        public void onUploadComplete(boolean success, String message) {
                            if (success) {
                                Log.i(TAG, "Split file uploaded successfully");
                                FileUploader.moveFileToUploadedFolder(completedFile);
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

            File chunkFile = new File(getStreamModelDirectory(), chunkFileName);

            FileOutputStream fileOutputStream = new FileOutputStream(chunkFile);
            CSVWriter chunkWriter = new CSVWriter(new OutputStreamWriter(fileOutputStream));

            writeHeader(chunkWriter);
            for (String[] row : tempBuffer) {
                chunkWriter.writeNext(row);
            }
            chunkWriter.flush();
            chunkWriter.close();

            Log.i(TAG, "New chunk file created: " + chunkFile.getAbsolutePath());
            lastChunkFile = chunkFile;
        } catch (IOException e) {
            Log.e(TAG, "Error writing chunk to file", e);
        }
    }

    public static String endRecording() {
        long endTimestamp = System.currentTimeMillis();
        closeCurrentWriter();

        String newFileName = generateFileName(startTimestamp, formatTimestamp(endTimestamp));
        File newFile = new File(currentFile.getParent(), newFileName);
        boolean renamed = currentFile.renameTo(newFile);

        if (!renamed) {
            Log.e(TAG, "Failed to rename file from " + currentFile.getName() + " to " + newFileName);
            return currentFile.getAbsolutePath();
        }

        Log.i(TAG, "Recording ended: " + newFile.getAbsolutePath());
        return newFile.getAbsolutePath();
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

    // Helper method to get the directory path for external access
    public static String getOximeterDataPath() {
        return getOximeterDataDirectory().getAbsolutePath();
    }

    // Helper method to get the stream model directory path
    public static String getStreamModelPath() {
        return getStreamModelDirectory().getAbsolutePath();
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