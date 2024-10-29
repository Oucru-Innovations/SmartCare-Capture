package com.smartcare.oximetry.sample;
import android.util.Log;

import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;
import java.text.SimpleDateFormat;
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

    public static void startNewRecording(long packageReceivedTimestamp) {
        closeCurrentWriter();
        currentFile = createFile(packageReceivedTimestamp);
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

    public static void endRecording() {
        closeCurrentWriter();
        Log.i(TAG, "Recording ended");
    }

    private static File createFile(long timestamp) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = generateFileName(timestamp);
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

    private static String generateFileName(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        String formattedDate = sdf.format(new Date(timestamp));

        TimeZone tz = TimeZone.getDefault();
        int offsetMillis = tz.getOffset(timestamp);
        int offsetHours = offsetMillis / (60 * 60 * 1000);
        int offsetMinutes = Math.abs((offsetMillis / (60 * 1000)) % 60);

        return String.format(Locale.US, "%s%+03d%02d.csv", formattedDate, offsetHours, offsetMinutes);
    }
}
