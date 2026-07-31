package com.smartcare.oximetry.sample;

/**
 * Long-running capture session defaults (24h BLE + CSV).
 */
public final class RecordingConstants {
    /** Automatic CSV rotation interval while the session stays active. */
    public static final int SPLIT_INTERVAL_HOURS = 6;
    /** {@link #SPLIT_INTERVAL_HOURS} quy đổi sang mili-giây, dùng trực tiếp khi so mốc split. */
    public static final long SPLIT_INTERVAL_MS = SPLIT_INTERVAL_HOURS * 60L * 60L * 1000L;

    /** Minimum segment length before BLE-pause split or auto split. */
    public static final long MIN_SEGMENT_MS = 60_000L;

    /** Approximate recording duration from CSV file size (10 MiB ≈ 1 hour). */
    public static final long BYTES_PER_RECORDING_HOUR = 10L * 1024 * 1024;

    private RecordingConstants() {
    }
}
