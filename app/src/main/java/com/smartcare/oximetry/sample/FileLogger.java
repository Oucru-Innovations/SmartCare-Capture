package com.smartcare.oximetry.sample;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Bộ ghi log bền vững của app vào {externalFilesDir}/Logs, tự xoay vòng (rotation) khi file đạt 10MB.
 */
public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final String LOG_FILE_DATE_FORMAT = "yyyyMMdd";
    private static final String CURRENT_LOG_FILENAME = "smartcare_app_log.txt";
    private static final String ARCHIVED_LOG_PATTERN = "smartcare_app_log_%s.txt";
    /** Mức độ log theo thứ tự tăng dần độ nghiêm trọng, dùng để lọc và quyết định flush. */
    public enum LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    private static Context appContext;
    private static FileLogger instance;

    private FileLogger() {}

    /**
     * Trả về singleton FileLogger, khởi tạo context và dọn log cũ ở lần gọi đầu tiên.
     */
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
    /**
     * Ghi một dòng log có timestamp vào file; tự flush ngay với mức WARN trở lên. Bỏ qua nếu chưa init.
     */
    public static void writeLog(LogLevel level, String tag, String message) {
        if (instance == null || appContext == null) {
            return;
        }

        try {
            File logFile = getLogFile();
            String timestamp = DATE_FORMAT.format(new Date());
            String logEntry = String.format("[%s] %s/%s: %s%n", timestamp, level.name(), tag, message);

            appendToLogFile(logFile, logEntry, level.ordinal() >= LogLevel.WARN.ordinal());

        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    private static void appendToLogFile(File logFile, String logEntry, boolean flushImmediately) throws IOException {
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logEntry);
            if (flushImmediately) {
                writer.flush();
            }
        }
    }

    /** Forces buffered log data to disk (call after crash / critical events). */
    public static void flushToDisk() {
        if (instance == null || appContext == null) {
            return;
        }
        try {
            File logFile = getLogFile();
            if (!logFile.exists()) {
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
                fos.flush();
                fos.getFD().sync();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error flushing log file", e);
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

    public static void e(String tag, String message, Throwable throwable) {
        if (throwable != null) {
            message = message + " | " + formatThrowable(throwable);
        }
        writeLog(LogLevel.ERROR, tag, message);
    }

    /**
     * Structured shutdown / lifecycle event for diagnosing why recording or the app stopped.
     *
     * @param category e.g. USER, SYSTEM, CRASH, BLUETOOTH, SERVICE
     * @param event    short event name
     * @param detail   human-readable context
     */
    public static void logEvent(String category, String event, String detail) {
        writeLog(LogLevel.INFO, "AppLifecycle",
                "category=" + category + " event=" + event + " detail=" + detail);
    }

    public static void logEvent(String category, String event, String detail, Context context) {
        String enriched = detail;
        if (context != null) {
            enriched = detail + " | " + MemoryDiagnostics.formatSnapshot(context);
        }
        logEvent(category, event, enriched);
    }

    /**
     * Ghi sự kiện lifecycle ở mức WARN dạng có cấu trúc (category/event/detail) để dễ chẩn đoán.
     */
    public static void logWarningEvent(String category, String event, String detail) {
        writeLog(LogLevel.WARN, "AppLifecycle",
                "category=" + category + " event=" + event + " detail=" + detail);
    }

    public static void logWarningEvent(String category, String event, String detail, Context context) {
        String enriched = detail;
        if (context != null) {
            enriched = detail + " | " + MemoryDiagnostics.formatSnapshot(context);
        }
        logWarningEvent(category, event, enriched);
    }

    /**
     * Ghi block log sự cố (crash) kèm thông tin thread/thiết bị/bộ nhớ và stack trace, rồi flush xuống đĩa ngay.
     */
    public static void logCrash(Thread thread, Throwable throwable) {
        String threadName = thread != null ? thread.getName() : "unknown";
        String memory = appContext != null
                ? MemoryDiagnostics.formatSnapshot(appContext)
                : "memory=unavailable";
        String device = appContext != null
                ? MemoryDiagnostics.formatDeviceSummary(appContext)
                : String.format(Locale.US, "device=%s %s (API %d)",
                Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT);

        writeCrashLogBlock(threadName, device, memory, formatThrowableMultiline(throwable));
        flushToDisk();
    }

    private static void writeCrashLogBlock(String threadName, String device, String memory,
                                           String stackTrace) {
        if (instance == null || appContext == null) {
            return;
        }
        try {
            File logFile = getLogFile();
            String timestamp = DATE_FORMAT.format(new Date());
            StringBuilder block = new StringBuilder();
            block.append(String.format(Locale.US,
                    "[%s] ERROR/AppCrash: Uncaught exception on thread=%s%n", timestamp, threadName));
            block.append(String.format(Locale.US,
                    "[%s] ERROR/AppCrash: %s%n", timestamp, device));
            block.append(String.format(Locale.US,
                    "[%s] ERROR/AppCrash: %s%n", timestamp, memory));
            if (stackTrace != null && !stackTrace.isEmpty()) {
                for (String line : stackTrace.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        block.append(String.format(Locale.US,
                                "[%s] ERROR/AppCrash:   %s%n", timestamp, trimmed));
                    }
                }
            }
            appendToLogFile(logFile, block.toString(), true);
        } catch (IOException e) {
            Log.e(TAG, "Error writing crash log", e);
        }
    }

    private static String formatThrowable(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString().replace("\r\n", " ").replace('\n', ' ');
    }

    private static String formatThrowableMultiline(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString().trim();
    }

    public static boolean isInitialized() {
        return instance != null && appContext != null;
    }

    // Get logs filtered by level and time
    /**
     * Đọc file log hiện tại và trả về các dòng có mức log >= minLevel (dùng cho màn hình xem log).
     */
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