package com.smartcare.oximetry.sample;

import static com.smartcare.oximetry.sample.BuildConfig.BASE_URL;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.smartcare.oximetry.sample.config.ApiConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UploadAnnotationWorker extends Worker {
    private static final String TAG = "UploadAnnotWorker";

    public UploadAnnotationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    private String calculateMD5(File file) {
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
            Log.e(TAG, "Error calculating MD5", e);
            return null;
        }
    }

    public interface ApiService {
        @retrofit2.http.Multipart
        @retrofit2.http.POST("api/v1/file/smapp/upload")
        retrofit2.Call<ResponseBody> uploadFile(
                @retrofit2.http.Header("x-static-token") String staticToken,
                @retrofit2.http.Header("x-content-md5") String checksum,
                @retrofit2.http.Part MultipartBody.Part file,
                @retrofit2.http.Part("owner") RequestBody owner
        );
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "Starting daily auto-upload worker...");

        Intent splitIntent = new Intent("com.smartcare.oximetry.ACTION_SPLIT_RECORDING");
        getApplicationContext().sendBroadcast(splitIntent);

        try {
            Log.i(TAG, "Waiting 5 seconds for file split to complete...");
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Log.e(TAG, "Worker wait interrupted", e);
        }

        // Get the oximeter data folder from internal storage
        File externalDir = getApplicationContext().getExternalFilesDir(null);
        File directory = new File(externalDir, "OximeterData");

        if (!directory.exists()) {
            Log.i(TAG, "Data directory does not exist.");
            return Result.success();
        }

        // Get the Uploaded directory
        File uploadedDir = new File(directory, "Uploaded");
        if (!uploadedDir.exists()) {
            uploadedDir.mkdirs();
        }

        // Scan for pending files (.csv and .json)
        File[] files = directory.listFiles((dir, name) ->
            (name.toLowerCase().endsWith(".csv") || name.toLowerCase().endsWith(".json"))
        );

        if (files == null || files.length == 0) {
            Log.i(TAG, "No pending files found for upload.");
            return Result.success();
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        ApiService apiService = retrofit.create(ApiService.class);

        boolean allUploaded = true;
        for (File file : files) {
            if (file.isDirectory()) continue;

            Log.i(TAG, "Uploading pending file: " + file.getName());
            String checksum = calculateMD5(file);
            if (checksum == null) {
                allUploaded = false;
                continue;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
            RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), "Auto-Worker");

            try {
                retrofit2.Call<ResponseBody> call = apiService.uploadFile(ApiConfig.API_TOKEN, checksum, body, owner);
                Response<ResponseBody> response = call.execute();
                if (response.isSuccessful()) {
                    Log.i(TAG, "File uploaded successfully: " + file.getName());
                    // Move to uploaded folder
                    File newFile = new File(uploadedDir, file.getName());
                    if (file.renameTo(newFile)) {
                        Log.i(TAG, "Moved file to Uploaded folder.");
                    }
                } else {
                    Log.e(TAG, "Upload failed for " + file.getName() + ": " + response.message());
                    allUploaded = false;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error uploading file: " + file.getName(), e);
                allUploaded = false;
            }
        }

        return allUploaded ? Result.success() : Result.retry();
    }
}