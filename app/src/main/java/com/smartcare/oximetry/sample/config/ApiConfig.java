package com.smartcare.oximetry.sample.config;

import com.smartcare.oximetry.sample.BuildConfig;

/**
 * Cấu hình API tập trung: BASE_URL và token lấy từ Gradle BuildConfig, không hardcode trong source.
 */
public class ApiConfig {
    private ApiConfig() {}

    /** URL gốc của server API, inject từ BuildConfig theo từng build variant. */
    public static final String BASE_URL = BuildConfig.BASE_URL;
    /** Token API từ BuildConfig; hiện chưa được gửi kèm trên call upload đang dùng. */
    public static final String API_TOKEN = BuildConfig.API_TOKEN;
}

