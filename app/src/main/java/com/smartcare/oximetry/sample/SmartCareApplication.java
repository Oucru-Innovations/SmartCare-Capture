package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.os.Bundle;

/**
 * Initializes file logging, crash reporting, and app-wide lifecycle tracking.
 */
public class SmartCareApplication extends Application {
    private static final String TAG = "SmartCareApplication";

    private int startedActivityCount = 0;
    private Thread.UncaughtExceptionHandler defaultExceptionHandler;

    /**
     * Khởi tạo các thành phần toàn cục khi process bắt đầu: FileLogger, FileUploader, crash handler,
     * theo dõi lifecycle của activity và đặt lịch upload hằng ngày lúc 17:00.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        FileLogger.getInstance(this);
        FileUploader.setAppContext(this);
        OximeterDataFileIndex.migrateLegacyPrivateFiles(this);
        installCrashHandler();
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks);

        FileLogger.logEvent("SYSTEM", "PROCESS_START",
                "pid=" + android.os.Process.myPid()
                        + " version=" + BuildConfig.VERSION_NAME
                        + " (" + BuildConfig.VERSION_CODE + ") | "
                        + MemoryDiagnostics.formatDeviceSummary(this) + " | "
                        + MemoryDiagnostics.formatSnapshot(this));
        AnnotationUploadScheduler.scheduleDailyUpload(this);
    }

    /**
     * Cài đặt uncaught exception handler để ghi crash ra file log (qua FileLogger) trước khi
     * chuyển tiếp cho handler mặc định, giúp giữ lại bằng chứng chẩn đoán khi app sập.
     */
    private void installCrashHandler() {
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                FileLogger.logCrash(thread, throwable);
                FileLogger.logEvent("CRASH", "APP_CRASH",
                        "App will terminate due to uncaught exception on thread=" + thread.getName(),
                        SmartCareApplication.this);
                FileLogger.flushToDisk();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Failed to write crash to file log", e);
            }
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private final ActivityLifecycleCallbacks activityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            FileLogger.logEvent("USER", "ACTIVITY_CREATED",
                    activity.getClass().getSimpleName()
                            + (savedInstanceState != null ? " (restored state)" : ""));
        }

        @Override
        public void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {
            SystemBarInsetsHelper.apply(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            startedActivityCount++;
            if (startedActivityCount == 1) {
                FileLogger.logEvent("USER", "APP_FOREGROUND",
                        "User brought app to foreground (" + activity.getClass().getSimpleName() + ")",
                        SmartCareApplication.this);
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            FileLogger.d("AppLifecycle", "Activity resumed: " + activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityPaused(Activity activity) {
            FileLogger.d("AppLifecycle", "Activity paused: " + activity.getClass().getSimpleName());
        }

        @Override
        public void onActivityStopped(Activity activity) {
            startedActivityCount--;
            if (startedActivityCount <= 0) {
                startedActivityCount = 0;
                FileLogger.logEvent("USER", "APP_BACKGROUND",
                        "App moved to background (" + activity.getClass().getSimpleName()
                                + ", isFinishing=" + activity.isFinishing() + ")",
                        SmartCareApplication.this);
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            // no-op
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            String reason;
            boolean abnormal;
            if (activity.isFinishing()) {
                reason = "USER_FINISH_OR_NAVIGATE_AWAY";
                abnormal = false;
            } else if (activity.isChangingConfigurations()) {
                reason = "CONFIGURATION_CHANGE";
                abnormal = false;
            } else {
                reason = "SYSTEM_DESTROYED_ACTIVITY";
                abnormal = true;
            }
            String detail = activity.getClass().getSimpleName() + " reason=" + reason;
            if (abnormal) {
                FileLogger.logWarningEvent("USER", "ACTIVITY_DESTROYED_ABNORMAL", detail,
                        SmartCareApplication.this);
            } else {
                FileLogger.logEvent("USER", "ACTIVITY_DESTROYED", detail);
            }
        }
    };

    /**
     * Ghi cảnh báo LOW_MEMORY khi hệ thống yêu cầu giải phóng bộ nhớ ở mức nghiêm trọng,
     * dùng để chẩn đoán các trường hợp app bị kill trong lúc recording dài.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            String levelName = MemoryDiagnostics.trimLevelName(level);
            String hint = level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                    ? "UI may be hidden or system reclaiming memory"
                    : "App running under memory pressure";
            FileLogger.logWarningEvent("SYSTEM", "LOW_MEMORY",
                    "onTrimMemory " + levelName + " | " + hint + " | "
                            + MemoryDiagnostics.formatSnapshot(this));
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        FileLogger.logWarningEvent("SYSTEM", "LOW_MEMORY",
                "onLowMemory system-wide low memory | "
                        + MemoryDiagnostics.formatSnapshot(this));
    }

    @Override
    public void onTerminate() {
        FileLogger.logEvent("SYSTEM", "PROCESS_TERMINATE",
                "Application onTerminate (emulator or test only on most devices)");
        super.onTerminate();
    }
}
