package com.smartcare.oximetry.sample;

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final String LOG_FILE_DATE_FORMAT = "yyyyMMdd";
    private static final String CURRENT_LOG_FILENAME = "smartcare_app_log.txt";
    private static final String ARCHIVED_LOG_PATTERN = "smartcare_app_log_%s.txt";
    public enum LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    private static Context appContext;
    private static FileLogger instance;

    private FileLogger() {}

    public static synchronized FileLogger getInstance(Context context) {
        if (instance == null) {
            instance = new FileLogger();
            appContext = context.getApplicationContext();
            cleanOldLogs(); // cleanup old logs on initialization
        }
        return instance;
    }

    // Main logging method with filtering
    //
    //
    public static void writeLog(LogLevel level, String tag, String message) {
        if (instance == null || appContext == null) {
            return;
        }

        try {
            File logFile = getLogFile();
            String timestamp = DATE_FORMAT.format(new Date());
            String logEntry = String.format("[%s] %s/%s: %s%n", timestamp, level.name(), tag, message);

            // Append to file
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logEntry);
            }

            // Write to secondary location (Downloads/OximeterData) using MediaStore
            writeToDownloadsFolder(logEntry);

        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    private static void writeToDownloadsFolder(String logEntry) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            return; // Only works on Android 10+
        }

        try {
            ContentResolver resolver = appContext.getContentResolver();

            // Query for existing file first
            String selection = android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            String[] selectionArgs = {CURRENT_LOG_FILENAME,
                    android.os.Environment.DIRECTORY_DOWNLOADS + "/OximeterData/FileLogger/"};

            android.database.Cursor cursor = resolver.query(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.MediaColumns._ID},
                    selection,
                    selectionArgs,
                    null);

            android.net.Uri fileUri;

            if (cursor != null && cursor.moveToFirst()) {
                // File exists - get its URI
                long id = cursor.getLong(0);
                fileUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
                cursor.close();
            } else {
                // File doesn't exist - create it
                if (cursor != null) cursor.close();

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, CURRENT_LOG_FILENAME);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/OximeterData/FileLogger");

                fileUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }

            // Write/append to file
            if (fileUri != null) {
                try (java.io.OutputStream outputStream = resolver.openOutputStream(fileUri, "wa")) {
                    if (outputStream != null) {
                        outputStream.write(logEntry.getBytes());
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error writing to Downloads folder", e);
        }
    }
    // Convenience methods for different log levels
    public static void v(String tag, String message) {
        writeLog(LogLevel.VERBOSE, tag, message);
    }

    public static void d(String tag, String message) {
        writeLog(LogLevel.DEBUG, tag, message);
    }

    public static void i(String tag, String message) {
        writeLog(LogLevel.INFO, tag, message);
    }

    public static void w(String tag, String message) {
        writeLog(LogLevel.WARN, tag, message);
    }

    public static void e(String tag, String message) {
        writeLog(LogLevel.ERROR, tag, message);
    }

    // Get logs filtered by level and time
    public static List<String> getFilteredLogs(LogLevel minLevel) {
        List<String> filteredLogs = new ArrayList<>();
        File logFile = getLogFile();

        if (!logFile.exists()) {
            return filteredLogs;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (shouldIncludeLine(line, minLevel)) {
                    filteredLogs.add(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading log file", e);
        }

        return filteredLogs;
    }

    private static boolean shouldIncludeLine(String line, LogLevel minLevel) {
        try {
            // Extract log level
            int levelStart = line.indexOf("] ") + 2;
            int levelEnd = line.indexOf("/", levelStart);
            if (levelStart < 2 || levelEnd == -1) {
                return false;
            }

            String levelStr = line.substring(levelStart, levelEnd);
            LogLevel logLevel = LogLevel.valueOf(levelStr);

            return logLevel.ordinal() >= minLevel.ordinal();
        } catch (Exception e) {
            return false; // Skip malformed lines
        }
    }

    private static void cleanOldLogs() {
        File logDir = new File(appContext.getExternalFilesDir(null), "Logs");
        if (!logDir.exists()) {
            return;
        }

        // Find all archived log files
        File[] archivedFiles = logDir.listFiles((dir, name) ->
                name.startsWith("smartcare_app_log_") && name.endsWith(".txt") && !name.equals(CURRENT_LOG_FILENAME));

        if (archivedFiles == null || archivedFiles.length <= 1) {
            return; // No cleanup needed if we have 0 or 1 archive files
        }

        // Sort files by modification time (newest first)
        Arrays.sort(archivedFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // Keep the newest archive file, delete the rest
        for (int i = 1; i < archivedFiles.length; i++) {
            if (archivedFiles[i].delete()) {
                Log.i(TAG, "Deleted old log file: " + archivedFiles[i].getName());
            }
        }
    }

    private static File getLogFile() {
        File externalDir = appContext.getExternalFilesDir(null);
        File logDir = new File(externalDir, "Logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        // Get the current log file
        File currentLogFile = new File(logDir, CURRENT_LOG_FILENAME);

        // Check if current log file exceeds size limit
        if (currentLogFile.exists() && currentLogFile.length() >= MAX_FILE_SIZE_BYTES) {
            archiveCurrentLogFile(logDir, currentLogFile);
        }

        return currentLogFile;
    }
    private static void archiveCurrentLogFile(File logDir, File currentLogFile) {
        try {
            // Create archive filename with timestamp
            String timestamp = new SimpleDateFormat(LOG_FILE_DATE_FORMAT + "_HHmmss",
                    Locale.getDefault()).format(new Date());
            File archivedFile = new File(logDir, String.format(ARCHIVED_LOG_PATTERN, timestamp));

            // Rename current file to archived name
            if (currentLogFile.renameTo(archivedFile)) {
                Log.i(TAG, "Log file archived: " + archivedFile.getName());
                cleanOldLogs();
            } else {
                Log.e(TAG, "Failed to archive log file");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error archiving log file", e);
        }
    }
    // Get log file path for external access
    public static String getLogFilePath() {
        return getLogFile().getAbsolutePath();
    }

    // Manual cleanup trigger
    public static void forceCleanup() {
        cleanOldLogs();
    }
}