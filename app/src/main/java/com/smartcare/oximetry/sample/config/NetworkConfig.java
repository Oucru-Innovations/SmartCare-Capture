package com.smartcare.oximetry.sample.config;

public class NetworkConfig {
    private NetworkConfig(){}
    // timeout(ms)
    public static final int CONNECT_TIMEOUT_MS = 15_000;
    public static final int READ_TIMEOUT_MS = 20_000;
    public static final int WRITE_TIMEOUT_MS = 20_000;

    // retry policy (for upload)
    public static final int MAX_RETRY = 3;
    public static final int RETRY_DELAY_MS = 10_000;
}
