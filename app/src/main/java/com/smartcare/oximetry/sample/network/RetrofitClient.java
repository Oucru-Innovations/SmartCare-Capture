package com.smartcare.oximetry.sample.network;

import com.smartcare.oximetry.sample.config.ApiConfig;
import com.smartcare.oximetry.sample.config.NetworkConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {
    private static Retrofit retrofit;

    private RetrofitClient(){}

    public static Retrofit getInstance(){
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(NetworkConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(NetworkConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(NetworkConfig.WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(ApiConfig.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
