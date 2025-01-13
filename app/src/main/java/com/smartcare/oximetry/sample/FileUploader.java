package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class FileUploader {

    private static final String BASE_URL = "http://13.250.243.25:8080/";
    private static final int RETRY_DELAY_MS = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String STATIC_TOKEN = "1c47cc76a192d6e337895baf60b4ce770603213dc03b8954fee1b6c502f7065f";

    private static int retryCount = 0;

    public static void uploadFile(Activity activity, String filePath) {
        File file = new File(filePath);
//        String accessToken = "";
        String ownerName = "Vyyy";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

        ProgressBar progressBar = activity.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, body, owner);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(activity, "File upload successful", Toast.LENGTH_SHORT).show();
                    Log.i("FileUploader", "File upload successful");
                    retryCount = 0;
                } else {
                    Toast.makeText(activity, "File upload failed: " + response.message(), Toast.LENGTH_SHORT).show();
                    Log.e("FileUploader", "File upload failed: " + response.message());
                    scheduleRetry(activity, filePath);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(activity, "Error uploading file", Toast.LENGTH_SHORT).show();
                Log.e("FileUploader", "Error uploading file", t);
                scheduleRetry(activity, filePath);
            }
        });
    }

    private static void scheduleRetry(Activity activity, String filePath) {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> uploadFile(activity, filePath), RETRY_DELAY_MS);
        } else {
            Toast.makeText(activity, "Max retry attempts reached. Upload failed.", Toast.LENGTH_SHORT).show();
            Log.e("FileUploader", "Max retry attempts reached. Upload failed.");
        }
    }

    private static class RetrofitClient {
        private static Retrofit retrofit = null;

        public static Retrofit getClient() {
            if (retrofit == null) {
                retrofit = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            return retrofit;
        }
    }

    private interface ApiService {
        @Multipart
        @POST("api/v1/file/smapp/upload")
        Call<ResponseBody> uploadFile(
                @Header("x-static-token") String staticToken,
                @Part MultipartBody.Part file,
                @Part("owner") RequestBody owner
        );
    }
}