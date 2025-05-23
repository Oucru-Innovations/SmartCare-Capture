package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

//    private static final String BASE_URL = "http://13.250.243.25:8080/";
    private static final String BASE_URL = "http://vital-ei.oucru.org/";
    private static final int RETRY_DELAY_MS = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String STATIC_TOKEN = "1c47cc76a192d6e337895baf60b4ce770603213dc03b8954fee1b6c502f7065f";

    private static int retryCount = 0;

    // Interface for handling upload results
    public interface UploadCallback {
        void onUploadComplete(boolean success, String message);
    }
    // move file when upload is successful
    public static boolean moveFileToUploadedFolder(File file) {
        // Create Uploaded directory if it doesn't exist
        File uploadedDir = new File(file.getParentFile(), "Uploaded");
        if (!uploadedDir.exists()) {
            uploadedDir.mkdirs();
        }

        // Move file to Uploaded directory
        File newFile = new File(uploadedDir, file.getName());
        return file.renameTo(newFile);
    }


    //upload with activity
    public static void uploadFile(Activity activity, String filePath, UploadCallback callback) {
        File file = new File(filePath);
//        String accessToken = "";
        String ownerName = "Vyyy";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

//        ProgressBar progressBar = activity.findViewById(R.id.progressBar);
//        progressBar.setVisibility(View.VISIBLE);

        String checksum = calculateMD5(file);

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
//        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, checksum, body, owner);
        Call<ResponseBody> call = apiService.uploadFile(body, owner); // test route
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(activity, "File upload successful", Toast.LENGTH_SHORT).show();
                    Log.i("FileUploader", "File upload successful");
                    retryCount = 0;
                    callback.onUploadComplete(true, "File upload successful");
                } else {
                    Toast.makeText(activity, "File upload failed: " + response.message(), Toast.LENGTH_SHORT).show();
                    Log.e("FileUploader", "File upload failed: " + response.message());
                    scheduleRetry(activity, filePath, callback);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
//                progressBar.setVisibility(View.GONE);
                Toast.makeText(activity, "Error uploading file", Toast.LENGTH_SHORT).show();
                Log.e("FileUploader", "Error uploading file", t);
                scheduleRetry(activity, filePath, callback);
            }
        });
    }

    private static String calculateMD5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int numRead;
            while ((numRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, numRead);
            }
            byte[] md5Bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5Bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.e("FileUploader", "Error calculating MD5 checksum", e);
            return null;
        }
    }
    // schedule retry for upload (activity)
    private static void scheduleRetry(Activity activity, String filePath, UploadCallback callback) {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> uploadFile(activity, filePath, callback), RETRY_DELAY_MS);
        } else {
            Toast.makeText(activity, "Max retry attempts reached. Upload failed.", Toast.LENGTH_SHORT).show();
            Log.e("FileUploader", "Max retry attempts reached. Upload failed.");
            callback.onUploadComplete(false, "Max retry attempts reached");
        }
    }
    // upload without activity
    public static void uploadFile(String filePath, UploadCallback callback) {
        File file = new File(filePath);
        String ownerName = "Vyyy";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

        String checksum = calculateMD5(file);

        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
//        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, checksum, body, owner);
        Call<ResponseBody> call = apiService.uploadFile(body, owner); // test route
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.i("FileUploader", "File upload successful");
                    retryCount = 0;
                    callback.onUploadComplete(true, "File upload successful");
                } else {
                    Log.e("FileUploader", "File upload failed: " + response.message());
                    scheduleRetryWithoutActivity(filePath, callback);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("FileUploader", "Error uploading file", t);
                scheduleRetryWithoutActivity(filePath, callback);
            }
        });
    }

    private static void scheduleRetryWithoutActivity(String filePath, UploadCallback callback) {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> uploadFile(filePath, callback), RETRY_DELAY_MS);
        } else {
            Log.e("FileUploader", "Max retry attempts reached. Upload failed.");
            callback.onUploadComplete(false, "Max retry attempts reached");
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
//        @POST("api/v1/file/smapp/upload")
        @POST("api/v1/file/upload") //route for test upload
        Call<ResponseBody> uploadFile(
//                @Header("x-static-token") String staticToken,
//                @Header("x-content-md5") String checksum,
                @Part MultipartBody.Part file,
                @Part("owner") RequestBody owner
        );
    }
}