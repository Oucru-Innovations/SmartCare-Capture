package com.smartcare.oximetry.sample;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

/**
 * Persists an in-progress recording session so {@link OximeterDataService} can resume
 * after START_STICKY restart without a fresh Intent.
 */
public final class RecordingSessionPrefs {
    private static final String PREFS = "recording_session_prefs";
    private static final String KEY_ACTIVE = "session_active";
    private static final String KEY_PATIENT = "patient_code";
    private static final String KEY_CHUNK = "chunk_enabled";
    private static final String KEY_INTERVAL = "chunk_interval";
    private static final String KEY_COLLECTION_START = "collection_start_ms";
    private static final String KEY_LAST_MAC = "last_device_mac";

    /** Ảnh chụp (snapshot) bất biến của phiên ghi đã lưu, dùng để khôi phục sau khi tiến trình bị kill. */
    public static final class Session {
        public final boolean active;
        public final String patientCode;
        public final boolean chunkEnabled;
        public final int chunkInterval;
        public final long collectionStartMs;

        Session(boolean active, String patientCode, boolean chunkEnabled, int chunkInterval,
                long collectionStartMs) {
            this.active = active;
            this.patientCode = patientCode;
            this.chunkEnabled = chunkEnabled;
            this.chunkInterval = chunkInterval;
            this.collectionStartMs = collectionStartMs;
        }
    }

    private RecordingSessionPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Lưu phiên ghi đang chạy vào SharedPreferences (đánh dấu active + các tham số) để service
     * có thể khôi phục sau khi bị kill / restart theo START_STICKY.
     */
    public static void markActive(Context context, String patientCode, boolean chunkEnabled,
                                  int chunkInterval, long collectionStartMs) {
        if (context == null || TextUtils.isEmpty(patientCode)) {
            return;
        }
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_PATIENT, patientCode)
                .putBoolean(KEY_CHUNK, chunkEnabled)
                .putInt(KEY_INTERVAL, chunkInterval)
                .putLong(KEY_COLLECTION_START, collectionStartMs)
                .apply();
    }

    /** Xóa phiên đã lưu khi ghi kết thúc bình thường, để lần restart sau không khôi phục nhầm. */
    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().clear().apply();
    }

    /** MAC của oximeter đang ghi, để service tự kết nối lại sau khi process bị restart. */
    public static void saveLastMac(Context context, String mac) {
        if (context == null || TextUtils.isEmpty(mac)) {
            return;
        }
        prefs(context).edit().putString(KEY_LAST_MAC, mac).apply();
    }

    public static String loadLastMac(Context context) {
        return context == null ? null : prefs(context).getString(KEY_LAST_MAC, null);
    }

    /** True nếu đang có phiên ghi được đánh dấu active cần khôi phục. */
    public static boolean isActive(Context context) {
        return context != null && prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    /** Đọc phiên đã lưu thành {@link Session}; trả về session không active mặc định nếu chưa có. */
    public static Session load(Context context) {
        if (context == null) {
            return new Session(false, null, false, 3, 0L);
        }
        SharedPreferences p = prefs(context);
        return new Session(
                p.getBoolean(KEY_ACTIVE, false),
                p.getString(KEY_PATIENT, null),
                p.getBoolean(KEY_CHUNK, false),
                p.getInt(KEY_INTERVAL, 3),
                p.getLong(KEY_COLLECTION_START, 0L));
    }
}
