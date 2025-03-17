package com.smartcare.oximetry.sample;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
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
    private static final String BASE_URL = "http://vital-ei.oucru.org/";
    private static final String STATIC_TOKEN = "1c47cc76a192d6e337895baf60b4ce770603213dc03b8954fee1b6c502f7065f";

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
        // Build the date string (e.g., "2025-03-11")
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        // Get the annotation folder
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "OximeterData/Annotation");
        if (!directory.exists()) {
            Log.i(TAG, "Annotation directory does not exist.");
            return Result.success();
        }

        // Look for a file that matches today's date (ignoring patient code)
        // For example, "Annotation_{patientCode}_2025-03-11.csv" should contain the substring "_2025-03-11.csv"
        File annotationFile = null;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains("_" + currentDate + ".csv")) {
                    annotationFile = file;
                    break;
                }
            }
        }

        if (annotationFile == null || !annotationFile.exists()) {
            Log.i(TAG, "No annotation file found for today");
            return Result.success();
        }

        String checksum = calculateMD5(annotationFile);
        if (checksum == null) {
            return Result.failure();
        }

        String ownerName = "Vyyy";  // Use a constant owner if applicable
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), annotationFile);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", annotationFile.getName(), requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        ApiService apiService = retrofit.create(ApiService.class);
        try {
            retrofit2.Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, checksum, body, owner);
            Response<ResponseBody> response = call.execute();  // Synchronous call
            if (response.isSuccessful()) {
                Log.i(TAG, "Annotation file upload successful");
                return Result.success();
            } else {
                Log.e(TAG, "Annotation file upload failed: " + response.message());
                return Result.retry();
            }
        } catch (IOException e) {
            Log.e(TAG, "Upload error", e);
            return Result.retry();
        }
    }
}