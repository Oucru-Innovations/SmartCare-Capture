package com.smartcare.oximetry.sample;

import android.app.Activity;
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

    private static final String BASE_URL = "http://192.168.134.111:8080/";

    public static void uploadFile(Activity activity, String filePath) {
        File file = new File(filePath);
        String accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJwYXlsb2FkIjp7ImVtYWlsIjoidGVzdEBleGFtcGxlLmNvbSJ9LCJpYXQiOjE3MzE2Mzg4ODAsImV4cCI6MTczMTY0MDY4MH0.2WQsHLOjJxm3O4MK-N1Kc48rJJEYh0-TKqi_LY-rKh0";
        String ownerName = "Vyyy";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

        ProgressBar progressBar = activity.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.VISIBLE);

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.uploadFile("Bearer " + accessToken, body, owner);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(activity, "File upload successful", Toast.LENGTH_SHORT).show();
                    Log.i("FileUploader", "File upload successful");
                } else {
                    Toast.makeText(activity, "File upload failed: " + response.message(), Toast.LENGTH_SHORT).show();
                    Log.e("FileUploader", "File upload failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(activity, "Error uploading file", Toast.LENGTH_SHORT).show();
                Log.e("FileUploader", "Error uploading file", t);
            }
        });
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
        @POST("api/v1/file/upload")
        Call<ResponseBody> uploadFile(
                @Header("Authorization") String authorization,
                @Part MultipartBody.Part file,
                @Part("owner") RequestBody owner
        );
    }
}