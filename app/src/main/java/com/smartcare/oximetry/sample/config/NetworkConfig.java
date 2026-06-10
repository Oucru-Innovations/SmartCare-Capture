package com.smartcare.oximetry.sample.config;

/**
 * Hằng số cấu hình mạng (timeout, retry policy) dùng cho OkHttp/Retrofit.
 */
public class NetworkConfig {
    private NetworkConfig(){}
    // timeout(ms)
    /** Timeout thiết lập kết nối tới server (ms). */
    public static final int CONNECT_TIMEOUT_MS = 15_000;
    /** Timeout đọc dữ liệu từ server (ms). */
    public static final int READ_TIMEOUT_MS = 20_000;
    /** Timeout ghi dữ liệu lên server (ms). */
    public static final int WRITE_TIMEOUT_MS = 20_000;

    // retry policy (for upload)
    /** Số lần retry tối đa; chỉ mang tính tài liệu — PendingFileUploadCoordinator tự định nghĩa riêng. */
    public static final int MAX_RETRY = 3;
    /** Khoảng chờ giữa các lần retry (ms); chỉ mang tính tài liệu — coordinator tự định nghĩa riêng. */
    public static final int RETRY_DELAY_MS = 10_000;
}
