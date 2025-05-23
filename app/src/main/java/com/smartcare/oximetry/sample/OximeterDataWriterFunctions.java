package com.smartcare.oximetry.sample;
import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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
    //model
    // Chunking related fields
    private static final int CHUNK_SIZE = 10;
    private static List<String[]> tempBuffer = new ArrayList<>();
    private static final String CHUNK_FILE_NAME = "chunk_file.csv";
    private static boolean enableChunkFile1 = false;
    private static int timeInterval1 = 1;
    //Holds the last chunk file created
    private static File lastChunkFile = null;
    private static final String STREAM_MODEL_FOLDER = "StreamModel";
    // New fields for file splitting
    private static final long DEFAULT_SPLIT_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours in milliseconds 6 * 60 * 60 * 1000;
    private static long splitInterval = DEFAULT_SPLIT_INTERVAL;
    private static long nextSplitTime;

    private static Activity currentActivity; // Add this field to store the Activity reference

    // Add this method to set the Activity reference
    public static void setActivity(Activity activity) {
        currentActivity = activity;
    }
    // Add setter for split interval
    public static void setSplitInterval(int hours) {
        splitInterval = hours * 60 * 60 * 1000L; // Convert hours to milliseconds
        if (startTimestamp > 0) {
            // Update next split time if recording is active
            nextSplitTime = startTimestamp + splitInterval;
        }
    }



    public static void startNewRecording(long packageReceivedTimestamp, String patientIdentifier, boolean enableChunkFile, int timeInterval) {
        closeCurrentWriter();


        startTimestamp = packageReceivedTimestamp;
        patientCode = patientIdentifier;
        nextSplitTime = startTimestamp + splitInterval;

        enableChunkFile1 = enableChunkFile;
        timeInterval1 = timeInterval;

        currentFile = createFileWithTempName(startTimestamp);
        dataBuffer = new OximeterDataBuffer();
        try {
            currentWriter = new CSVWriter(new FileWriter(currentFile));
            writeHeader(currentWriter);
            Log.i(TAG, "New recording started: " + currentFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Error creating new CSV file", e);
            currentWriter = null;
        }
    }

    public static void writeDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                                      int hrVal, long irAdc, double perfusionIndex, int counter,
                                      String macAddress, long packageReceived, int plethVal, long timestamp) {
        if (currentWriter == null || dataBuffer == null) {
            Log.e(TAG, "No active recording session");
            return;
        }

        dataBuffer.addDataPoint(spo2Val, spo2Status, redAdc, battery, hrVal, irAdc,
                perfusionIndex, counter, macAddress, packageReceived, plethVal, timestamp);
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
                    data.spo2Status.toString(), // Save as array
                    data.pleth.toString(),      // Save as array
                    data.red.toString(),        // Save as array
                    data.ir.toString(),         // Save as array
                    data.perfusion.toString()   // Save as array
            };

            // Check if it's time to split the file

            if (convertFromISOTimestamp(data.timestamp) >= nextSplitTime) {
                splitFile(convertFromISOTimestamp(data.timestamp));
            }

            currentWriter.writeNext(csvData);
            currentWriter.flush();

            // Write to temporary buffer
            // Check on/off chunk file creation
//            Log.i(TAG, "Chunk file creation: " + enableChunkFile1);
            if (enableChunkFile1) {
//                Log.i(TAG, "test 1111111");
                tempBuffer.add(csvData);
                if (tempBuffer.size() >= timeInterval1*60) {
                    writeChunkToFile();
                    tempBuffer.clear();
                }
            }


        } catch (IOException e) {
            Log.e(TAG, "Error writing processed data", e);
        }
    }

    private static String formatArray(List<?> list) {
        return list.toString();
    }

    public static String endRecording() {
        long endTimestamp = System.currentTimeMillis();
        closeCurrentWriter();
        renameFileWithEndTime(endTimestamp);

        Log.i(TAG, "Recording ended");
        return currentFile.getAbsolutePath();
//        FileUploader.uploadFile(currentFile.getAbsolutePath());
//        Log.i(TAG, "File uploaded successfully");
    }

    private static File createFileWithTempName(long startTimestamp) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Initial file name with "Temp" suffix
        String fileName = generateFileName(startTimestamp, "temp");
        return new File(directory, fileName);
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
    // Break the current file and start a new one
    private static long convertFromISOTimestamp(String isoTimestamp) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX");
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(isoTimestamp, formatter);
            return offsetDateTime.toInstant().toEpochMilli();
        } catch (Exception e) {
            Log.e(TAG, "Error converting ISO format to timestamp", e);
            return System.currentTimeMillis(); // Fallback to current time
        }
    }
    private static void splitFile(long timestamp) {
        try {
            // Close current writer
            currentWriter.flush();
            currentWriter.close();

            // Rename the current file with its end time
            File completedFile = new File(currentFile.getParentFile(),
                    generateFileName(startTimestamp, formatTimestamp(timestamp)));
            currentFile.renameTo(completedFile);

            // Start new file
            startTimestamp = timestamp;
            nextSplitTime = timestamp + splitInterval;
            currentFile = createFileWithTempName(timestamp);
            currentWriter = new CSVWriter(new FileWriter(currentFile));
            writeHeader(currentWriter);

            Log.i(TAG, "File split successful. New file: " + currentFile.getAbsolutePath());
            if (currentActivity != null) {
                FileUploader.uploadFile(currentActivity, completedFile.getAbsolutePath(),
                        new FileUploader.UploadCallback() {
                            @Override
                            public void onUploadComplete(boolean success, String message) {
                                if (success) {
                                    Log.i(TAG, "Split file uploaded successfully: " + completedFile.getName());
                                    FileUploader.moveFileToUploadedFolder(completedFile);
                                } else {
                                    Log.e(TAG, "Failed to upload split file: " + completedFile.getName());
                                }
                            }
                        });
            } else {
//                Log.w(TAG, "Activity context not available, skipping file upload");
                FileUploader.uploadFile(completedFile.getAbsolutePath(), new FileUploader.UploadCallback() {
                    @Override
                    public void onUploadComplete(boolean success, String message) {
                        if (success) {
                            Log.i("Upload", "File uploaded successfully");
                            FileUploader.moveFileToUploadedFolder(completedFile);
                        } else {
                            Log.e("Upload", "File upload failed: " + message);
                        }
                    }
                });
            }

        } catch (IOException e) {
            Log.e(TAG, "Error during file split", e);
            // If split fails, try to reopen the current file
            try {
                currentWriter = new CSVWriter(new FileWriter(currentFile, true));
            } catch (IOException reopenError) {
                Log.e(TAG, "Critical error: Failed to reopen file after split attempt", reopenError);
                currentWriter = null;
            }
        }
    }
    // Create chunk file from temporary buffer
    private static void writeChunkToFile() {
        try {
            // Get current timestamp for end time
            long currentTime = System.currentTimeMillis();
            // Calculate start time (3 minutes ago)
            long startTime = currentTime - (timeInterval1 * 60 * 1000);

            // Create StreamModel directory inside OximeterData
            File baseDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME);
            File chunkDirectory = new File(baseDirectory, STREAM_MODEL_FOLDER);
            if (!chunkDirectory.exists()) {
                boolean created = chunkDirectory.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create StreamModel directory");
                    return;
                }
            }

            // Generate new chunk file name
            String chunkFileName = String.format("SmartCareCsvChunk_%s_%s_%s.csv",
                    patientCode,
                    formatTimestamp(startTime),
                    formatTimestamp(currentTime));

            // Create new chunk file
            File newChunkFile = new File(chunkDirectory, chunkFileName);
            //now app don't need to upload chunk file to server at the moment
            // Send the chunk file to the server then delete it
//            FileUploader.uploadFile(newChunkFile.getAbsolutePath(), new FileUploader.UploadCallback() {
//                @Override
//                public void onUploadComplete(boolean success, String message) {
//                    if (success) {
//                        Log.i(TAG, "Chunk file uploaded successfully: " + newChunkFile.getName());
//                        // Delete the chunk file after successful upload
//                        boolean deleted = newChunkFile.delete();
//                        if (!deleted) {
//                            Log.w(TAG, "Failed to delete chunk file: " + newChunkFile.getAbsolutePath());
//                        } else {
//                            Log.i(TAG, "Deleted chunk file: " + newChunkFile.getAbsolutePath());
//                        }
//                    } else {
//                        Log.e(TAG, "Failed to upload chunk file: " + newChunkFile.getName());
//                    }
//                }
//            });
            // Delete the previous chunk file if it exists
//            if (lastChunkFile != null && lastChunkFile.exists()) {
//                boolean deleted = lastChunkFile.delete();
//                if (!deleted) {
//                    Log.w(TAG, "Failed to delete previous chunk file: " + lastChunkFile.getAbsolutePath());
//                } else {
//                    Log.i(TAG, "Deleted previous chunk file: " + lastChunkFile.getAbsolutePath());
//                }
//            }

            // Write data to new chunk file
            try (CSVWriter chunkWriter = new CSVWriter(new FileWriter(newChunkFile))) {
                writeHeader(chunkWriter);
                for (String[] row : tempBuffer) {
                    chunkWriter.writeNext(row);
                }
                Log.i(TAG, "New chunk file created: " + newChunkFile.getAbsolutePath());
            }

            // Update reference to current chunk file
            lastChunkFile = newChunkFile;

        } catch (IOException e) {
            Log.e(TAG, "Error writing chunk to file", e);
            e.printStackTrace();
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
    }
    // sample SmartCareCsv_54EI-003-6011_26.10.2024.08.59.31_26.10.2024.10.57.59
    private static void renameFileWithEndTime(long endTimestamp) {
        if (currentFile == null || !currentFile.exists()) {
            Log.e(TAG, "No file to rename");
            return;
        }

        File directory = currentFile.getParentFile();
        String newFileName = generateFileName( startTimestamp, formatTimestamp(endTimestamp));
        File newFile = new File(directory, newFileName);

        if (currentFile.renameTo(newFile)) {
            Log.i(TAG, "File renamed to: " + newFile.getAbsolutePath());
            currentFile = newFile;
        } else {
            Log.e(TAG, "Error renaming file");
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
