package com.smartcare.oximetry.sample;
import android.app.Activity;
import android.util.Log;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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


    public static void startNewRecording(long packageReceivedTimestamp, String patientIdentifier) {
        closeCurrentWriter();


        startTimestamp = packageReceivedTimestamp;
        patientCode = patientIdentifier;


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

            currentWriter.writeNext(csvData);
            currentWriter.flush();


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

    private static String formatTimestamp(long timestamp) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        return dateFormat.format(new Date(timestamp));
    }
}
