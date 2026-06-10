package com.smartcare.oximetry.sample;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Build;

import java.util.Locale;

/**
 * Builds human-readable device and app heap memory snapshots for file logs.
 */
public final class MemoryDiagnostics {

    private MemoryDiagnostics() {
    }

    /** Manufacturer, model, Android API, and installed RAM (deviceTotal). */
    public static String formatDeviceSummary(Context context) {
        long installedRamMb = 0;
        if (context != null) {
            try {
                ActivityManager am = (ActivityManager) context.getApplicationContext()
                        .getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(info);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        installedRamMb = bytesToMb(info.totalMem);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return String.format(Locale.US,
                "device=%s %s (API %d) RAM_installed=%dMB",
                Build.MANUFACTURER,
                Build.MODEL,
                Build.VERSION.SDK_INT,
                installedRamMb);
    }

    /**
     * Tạo chuỗi snapshot bộ nhớ hiện tại: RAM khả dụng/tổng/ngưỡng của thiết bị, cờ lowMemory và
     * heap đã dùng/tối đa của app — dùng để chẩn đoán nguy cơ bị OOM-kill trong file log.
     */
    public static String formatSnapshot(Context context) {
        if (context == null) {
            return "memory=unavailable (null context)";
        }
        Context app = context.getApplicationContext();

        long availBytes = 0;
        long totalBytes = 0;
        long thresholdBytes = 0;
        boolean lowMemory = false;

        try {
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                availBytes = info.availMem;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    totalBytes = info.totalMem;
                }
                thresholdBytes = info.threshold;
                lowMemory = info.lowMemory;
            }
        } catch (Exception e) {
            return "memory=device query failed: " + e.getMessage();
        }

        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();

        return String.format(Locale.US,
                "deviceAvail=%dMB deviceTotal=%dMB threshold=%dMB lowMemory=%s"
                        + " | appHeapUsed=%dMB appHeapMax=%dMB",
                bytesToMb(availBytes),
                bytesToMb(totalBytes),
                bytesToMb(thresholdBytes),
                lowMemory,
                bytesToMb(heapUsed),
                bytesToMb(heapMax));
    }

    /** Đổi mã onTrimMemory level sang tên dễ đọc (kèm giá trị số) để ghi log rõ ràng hơn. */
    public static String trimLevelName(int level) {
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
                return "RUNNING_MODERATE(5)";
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
                return "RUNNING_LOW(10)";
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                return "RUNNING_CRITICAL(15)";
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                return "UI_HIDDEN(20)";
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
                return "BACKGROUND(40)";
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                return "MODERATE(60)";
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                return "COMPLETE(80)";
            default:
                return "LEVEL(" + level + ")";
        }
    }

    private static long bytesToMb(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return bytes / (1024L * 1024L);
    }
}
