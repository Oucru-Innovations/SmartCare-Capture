package com.smartcare.oximetry.sample;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ghi log bền vững vào {@code {externalFilesDir}/Logs}: một file (hoặc nhiều part) mỗi ngày theo
 * lịch tablet, tối đa 30MB/part, giữ 15 ngày. File legacy không bị đổi tên hay xóa.
 */
public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat LOG_FILE_DATE_FORMAT =
            new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
    private static final long MAX_FILE_SIZE_BYTES = 30L * 1024 * 1024;
    private static final int RETENTION_DAYS = 15;
    private static final String LOG_FILE_PREFIX = "smartcare_app_log_";
    private static final String LOG_FILE_SUFFIX = ".txt";
    /**
     * smartcare_app_log_25.06.2025.txt or smartcare_app_log_25.06.2025_2.txt
     */
    private static final Pattern DAILY_LOG_FILE_PATTERN = Pattern.compile(
            "^smartcare_app_log_(\\d{2})\\.(\\d{2})\\.(\\d{4})(?:_(\\d+))?\\.txt$");

    public enum LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }

    /** Metadata cho màn hình chọn file log khi báo lỗi. */
    public static final class LogFileInfo {
        public final File file;
        public final String displayName;
        private final long sortKey;

        LogFileInfo(File file, String displayName, long sortKey) {
            this.file = file;
            this.displayName = displayName;
            this.sortKey = sortKey;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static Context appContext;
    private static FileLogger instance;
    private static String activeDateKey;
    private static File activeLogFile;

    private FileLogger() {}

    public static synchronized FileLogger getInstance(Context context) {
        if (instance == null) {
            instance = new FileLogger();
            appContext = context.getApplicationContext();
            purgeExpiredLogs();
        }
        return instance;
    }

    public static void writeLog(LogLevel level, String tag, String message) {
        if (instance == null || appContext == null) {
            return;
        }

        try {
            File logFile = getLogFile();
            appendToLogFile(logFile, formatLogEntry(level, tag, message),
                    level.ordinal() >= LogLevel.WARN.ordinal());
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    private static String formatLogEntry(LogLevel level, String tag, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        return String.format(Locale.US, "[%s] %s/%s: %s%n", timestamp, level.name(), tag, message);
    }

    private static void appendToLogFile(File logFile, String logEntry, boolean flushImmediately)
            throws IOException {
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write(logEntry);
            if (flushImmediately) {
                writer.flush();
            }
        }
    }

    public static synchronized void flushToDisk() {
        if (instance == null || appContext == null) {
            return;
        }
        File logFile = activeLogFile;
        if (logFile == null) {
            logFile = getLogFile();
        }
        flushFile(logFile);
    }

    private static void flushFile(File logFile) {
        if (logFile == null || !logFile.exists()) {
            return;
        }
        try (FileOutputStream fos = new FileOutputStream(logFile, true)) {
            fos.flush();
            fos.getFD().sync();
        } catch (IOException e) {
            Log.e(TAG, "Error flushing log file", e);
        }
    }

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
     * Ghi mô tả báo lỗi của user vào một file log cụ thể (trước khi upload file đó).
     */
    public static synchronized void appendUserReportToFile(File logFile, String detail, Context context) {
        if (logFile == null || detail == null) {
            return;
        }
        String enriched = detail;
        if (context != null) {
            enriched = detail + " | " + MemoryDiagnostics.formatSnapshot(context);
        }
        try {
            appendToLogFile(logFile,
                    formatLogEntry(LogLevel.INFO, "AppLifecycle",
                            "category=USER event=BUG_REPORT detail=" + enriched),
                    true);
        } catch (IOException e) {
            Log.e(TAG, "Error appending user report to " + logFile.getName(), e);
        }
    }

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
            int levelStart = line.indexOf("] ") + 2;
            int levelEnd = line.indexOf("/", levelStart);
            if (levelStart < 2 || levelEnd == -1) {
                return false;
            }

            String levelStr = line.substring(levelStart, levelEnd);
            LogLevel logLevel = LogLevel.valueOf(levelStr);

            return logLevel.ordinal() >= minLevel.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    /** Danh sách file log theo format ngày mới, mới nhất trước. */
    public static synchronized List<LogFileInfo> listAvailableLogFiles() {
        List<LogFileInfo> result = new ArrayList<>();
        File logDir = getLogDir();
        if (!logDir.exists()) {
            return result;
        }

        File[] files = logDir.listFiles();
        if (files == null) {
            return result;
        }

        for (File file : files) {
            LogFileInfo info = toLogFileInfo(file);
            if (info != null) {
                result.add(info);
            }
        }

        Collections.sort(result, (a, b) -> Long.compare(b.sortKey, a.sortKey));
        return result;
    }

    public static String getLogFilePath() {
        return getLogFile().getAbsolutePath();
    }

    public static void forceCleanup() {
        purgeExpiredLogs();
    }

    private static synchronized File getLogFile() {
        String todayKey = formatDateKey(new Date());
        if (activeDateKey == null || !todayKey.equals(activeDateKey)) {
            handleDayRollover(todayKey);
        }

        if (activeLogFile == null
                || !activeLogFile.exists()
                || activeLogFile.length() >= MAX_FILE_SIZE_BYTES) {
            File previous = activeLogFile;
            activeLogFile = resolveWritableFile(todayKey);
            if (previous != null && !previous.equals(activeLogFile)) {
                try {
                    appendToLogFile(activeLogFile,
                            formatLogEntry(LogLevel.INFO, "AppLifecycle",
                                    "category=SYSTEM event=LOG_FILE_PART_ROTATION detail=previousFile="
                                            + previous.getName() + " newFile=" + activeLogFile.getName()),
                            true);
                } catch (IOException e) {
                    Log.e(TAG, "Error writing part rotation log", e);
                }
            }
        }
        return activeLogFile;
    }

    private static void handleDayRollover(String todayKey) {
        if (activeLogFile != null) {
            flushFile(activeLogFile);
        }

        String previousDateKey = activeDateKey;
        activeDateKey = todayKey;
        activeLogFile = resolveWritableFile(todayKey);
        purgeExpiredLogs();

        if (previousDateKey != null) {
            try {
                appendToLogFile(activeLogFile,
                        formatLogEntry(LogLevel.INFO, "AppLifecycle",
                                "category=SYSTEM event=LOG_DAY_ROLLOVER detail=previousDate="
                                        + previousDateKey + " newDate=" + todayKey
                                        + " file=" + activeLogFile.getName()),
                        true);
            } catch (IOException e) {
                Log.e(TAG, "Error writing day rollover log", e);
            }
        }
    }

    private static File resolveWritableFile(String dateKey) {
        File logDir = getLogDir();
        int part = 1;
        File candidate = buildLogFile(logDir, dateKey, part);
        while (candidate.exists() && candidate.length() >= MAX_FILE_SIZE_BYTES) {
            part++;
            candidate = buildLogFile(logDir, dateKey, part);
        }
        return candidate;
    }

    private static File getLogDir() {
        File externalDir = appContext.getExternalFilesDir(null);
        File logDir = new File(externalDir, "Logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        return logDir;
    }

    private static String formatDateKey(Date date) {
        return LOG_FILE_DATE_FORMAT.format(date);
    }

    private static File buildLogFile(File logDir, String dateKey, int part) {
        String filename;
        if (part <= 1) {
            filename = LOG_FILE_PREFIX + dateKey + LOG_FILE_SUFFIX;
        } else {
            filename = LOG_FILE_PREFIX + dateKey + "_" + part + LOG_FILE_SUFFIX;
        }
        return new File(logDir, filename);
    }

    private static LogFileInfo toLogFileInfo(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }

        Matcher matcher = DAILY_LOG_FILE_PATTERN.matcher(file.getName());
        if (!matcher.matches()) {
            return null;
        }

        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int year = Integer.parseInt(matcher.group(3));
        int part = 1;
        String partGroup = matcher.group(4);
        if (partGroup != null && !partGroup.isEmpty()) {
            part = Integer.parseInt(partGroup);
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long sortKey = calendar.getTimeInMillis() * 100L + part;
        String displayName = file.getName();
        if (isToday(dateKeyFromParts(day, month, year))) {
            displayName = displayName + " (today)";
        }
        long sizeKb = Math.max(1, file.length() / 1024);
        displayName = displayName + " — " + sizeKb + " KB";

        return new LogFileInfo(file, displayName, sortKey);
    }

    private static String dateKeyFromParts(int day, int month, int year) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return formatDateKey(calendar.getTime());
    }

    private static boolean isToday(String dateKey) {
        return formatDateKey(new Date()).equals(dateKey);
    }

    private static void purgeExpiredLogs() {
        if (appContext == null) {
            return;
        }

        File logDir = getLogDir();
        if (!logDir.exists()) {
            return;
        }

        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS);
        cutoff.set(Calendar.HOUR_OF_DAY, 0);
        cutoff.set(Calendar.MINUTE, 0);
        cutoff.set(Calendar.SECOND, 0);
        cutoff.set(Calendar.MILLISECOND, 0);
        long cutoffMs = cutoff.getTimeInMillis();

        File[] files = logDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            Date fileDate = parseDailyLogFileDate(file.getName());
            if (fileDate == null) {
                continue;
            }
            if (fileDate.getTime() < cutoffMs) {
                if (file.delete()) {
                    Log.i(TAG, "Deleted expired log file: " + file.getName());
                }
            }
        }
    }

    private static Date parseDailyLogFileDate(String filename) {
        Matcher matcher = DAILY_LOG_FILE_PATTERN.matcher(filename);
        if (!matcher.matches()) {
            return null;
        }
        try {
            String dateKey = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
            return LOG_FILE_DATE_FORMAT.parse(dateKey);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Timestamp for bug-report upload filename: {@code dd.MM.yyyy.HH.mm.ss}.
     * Calendar date comes from the log filename; time-of-day is the upload moment.
     */
    public static String formatBugReportUploadTimestamp(File logFile) {
        return formatBugReportUploadTimestamp(logFile, System.currentTimeMillis());
    }

    public static String formatBugReportUploadTimestamp(File logFile, long uploadTimeMs) {
        SimpleDateFormat uploadFormat = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US);
        if (logFile == null) {
            return uploadFormat.format(new Date(uploadTimeMs));
        }
        Date logDay = parseDailyLogFileDate(logFile.getName());
        if (logDay == null) {
            return uploadFormat.format(new Date(uploadTimeMs));
        }
        Calendar result = Calendar.getInstance();
        result.setTimeInMillis(uploadTimeMs);
        Calendar day = Calendar.getInstance();
        day.setTime(logDay);
        result.set(Calendar.YEAR, day.get(Calendar.YEAR));
        result.set(Calendar.MONTH, day.get(Calendar.MONTH));
        result.set(Calendar.DAY_OF_MONTH, day.get(Calendar.DAY_OF_MONTH));
        return uploadFormat.format(result.getTime());
    }
}
