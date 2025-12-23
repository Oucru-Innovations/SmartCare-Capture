package com.smartcare.oximetry.sample.config;

import com.smartcare.oximetry.sample.BuildConfig;

public class ApiConfig {
    private ApiConfig() {}

    public static final String BASE_URL = BuildConfig.BASE_URL;
    public static final String API_TOKEN = BuildConfig.API_TOKEN;

    public static final int CONNECT_TIMEOUT = 15_000;
    public static final int READ_TIMEOUT = 20_000;
    public static final int WRITE_TIMEOUT = 20_000;

}
