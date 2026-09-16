package com.smartcare.oximetry.sample;

import com.smartcare.oximetry.sample.config.ApiConfig;
import com.smartcare.oximetry.sample.network.RetrofitClient;
import com.smartcare.oximetry.sample.network.ApiService;
import com.smartcare.oximetry.sample.network.UploadResponse;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
//move file with MediaStore
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
// For logging JSON response
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
/**
 * Thực hiện upload HTTP multipart thực tế cho content-URI hoặc file path, xác thực message từ server
 * và chuyển file thành công sang thư mục Uploaded/. Lớp này KHÔNG tự retry — caller chịu trách nhiệm retry.
 */
public class FileUploader {

    private static final String BASE_URL = ApiConfig.BASE_URL;
    private static final String STATIC_TOKEN = ApiConfig.API_TOKEN;
    
    private static Context appContext;
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Lưu application context để overload upload không có Activity vẫn dùng được ContentResolver.
     */
    public static void setAppContext(Context context) {
        appContext = context.getApplicationContext();
    }

    /**
     * Callback báo kết quả upload bất đồng bộ về cho caller (thành công/thất bại kèm message).
     */
    public interface UploadCallback {
        void onUploadComplete(boolean success, String message);
    }

    /**
     * Always notify the caller first so retry/in-flight state cannot get stuck if Toast throws
     * (for example after the Activity has been destroyed).
     */
    private static void completeWithToast(Activity activity, UploadCallback callback,
                                          boolean success, String callbackMessage, String toastMessage) {
        if (callback != null) {
            try {
                callback.onUploadComplete(success, callbackMessage);
            } catch (RuntimeException e) {
                Log.e("FileUploader", "Upload callback failed", e);
            }
        }
        showToastSafe(activity, toastMessage);
    }

    private static void showToastSafe(Activity activity, String message) {
        if (activity == null || TextUtils.isEmpty(message)) {
            return;
        }
        try {
            if (activity.isFinishing()) {
                return;
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
                    && activity.isDestroyed()) {
                return;
            }
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.w("FileUploader", "Skipping toast: " + e.getMessage());
        }
    }
    /**
     * Di chuyển file thường sang thư mục con Uploaded/ và cập nhật lại thời gian sửa đổi.
     */
    public static boolean moveFileToUploadedFolder(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        File uploadedDir = OximeterDataFileIndex.getUploadedDirectory();
        if (!uploadedDir.exists() && !uploadedDir.mkdirs()) {
            Log.e("FileUploader", "Could not create Uploaded directory: " + uploadedDir);
            return false;
        }

        File newFile = new File(uploadedDir, file.getName());
        boolean moveSuccess = file.renameTo(newFile);
        if (!moveSuccess) {
            try {
                java.nio.file.Files.copy(
                        file.toPath(), newFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                file.delete();
                moveSuccess = true;
            } catch (IOException e) {
                Log.e("FileUploader", "Failed to move file to Uploaded: " + file, e);
                return false;
            }
        }

        if (moveSuccess) {
            newFile.setLastModified(System.currentTimeMillis());
        }

        return moveSuccess;
    }
    /**
     * MIME theo phần mở rộng: MediaStore (Android 10+) sẽ đổi tên file cho khớp MIME, nên gán cứng
     * "text/csv" sẽ biến Annotation_*.json thành *.csv và file biến mất khỏi File Manager.
     */
    private static String mimeTypeForName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "text/csv";
    }

    // move file with MediaStore
    /**
     * Di chuyển file dạng content-URI sang Download/OximeterData/Uploaded thông qua MediaStore,
     * copy nội dung rồi xóa bản gốc và đánh dấu đã upload trong registry.
     */
    public static boolean moveFileToUploadedFolderFromUri(Context context, String uriPath) {
        try {
            // Get the content resolver
            ContentResolver contentResolver = context.getContentResolver();

            // Parse the URI string
            Uri sourceUri = Uri.parse(uriPath);

            // Extract file details from source URI
            String fileName = null;
            try (Cursor cursor = contentResolver.query(sourceUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
                    fileName = cursor.getString(nameIndex);
                }
            }

            if (fileName == null) {
                Log.e("FileUploader", "Could not retrieve file name from URI: " + uriPath);
                return false;
            }

            // Create the destination URI in the "Uploaded" subfolder
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeTypeForName(fileName));
            values.put(MediaStore.Downloads.RELATIVE_PATH, OximeterDataFileIndex.uploadedRelativePath());

            // Insert the new file
            Uri destinationUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (destinationUri != null) {
                // Copy the content from source to destination
                try (InputStream is = contentResolver.openInputStream(sourceUri);
                     OutputStream os = contentResolver.openOutputStream(destinationUri)) {

                    if (is == null || os == null) {
                        Log.e("FileUploader", "Could not open streams for copy operation");
                        return false;
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }

                    // Delete the original file
                    contentResolver.delete(sourceUri, null, null);
                    AppRecordingRegistry.markUploaded(context, uriPath);

                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e("FileUploader", "Error moving file: " + e.getMessage(), e);
            return false;
        }
    }


    //upload with activity
//    public static void uploadFile(Activity activity, String filePath, UploadCallback callback) {
//        File file = new File(filePath);
//
//
////        String accessToken = "";
//        String ownerName = "Vyyy";
//        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
//        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
//        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);
//
////        ProgressBar progressBar = activity.findViewById(R.id.progressBar);
////        progressBar.setVisibility(View.VISIBLE);
//
//        String checksum = calculateMD5(file);
//
//        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
////        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, checksum, body, owner);
//        Call<ResponseBody> call = apiService.uploadFile(body, owner); // test route
//        call.enqueue(new Callback<ResponseBody>() {
//            @Override
//            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
////                progressBar.setVisibility(View.GONE);
//                if (response.isSuccessful()) {
//                    Toast.makeText(activity, "File upload successful", Toast.LENGTH_SHORT).show();
//                    Log.i("FileUploader", "File upload successful");
//                    retryCount = 0;
//                    callback.onUploadComplete(true, "File upload successful");
//                } else {
//                    Toast.makeText(activity, "File upload failed: " + response.message(), Toast.LENGTH_SHORT).show();
//                    Log.e("FileUploader", "File upload failed: " + response.message());
//                    scheduleRetry(activity, filePath, callback);
//                }
//            }
//
//            @Override
//            public void onFailure(Call<ResponseBody> call, Throwable t) {
////                progressBar.setVisibility(View.GONE);
//                Toast.makeText(activity, "Error uploading file", Toast.LENGTH_SHORT).show();
//                Log.e("FileUploader", "Error uploading file", t);
//                scheduleRetry(activity, filePath, callback);
//            }
//        });
//    }

//    private static String calculateMD5(File file) {
//        try (FileInputStream fis = new FileInputStream(file)) {
//            MessageDigest digest = MessageDigest.getInstance("MD5");
//            byte[] buffer = new byte[1024];
//            int numRead;
//            while ((numRead = fis.read(buffer)) != -1) {
//                digest.update(buffer, 0, numRead);
//            }
//            byte[] md5Bytes = digest.digest();
//            StringBuilder sb = new StringBuilder();
//            for (byte b : md5Bytes) {
//                sb.append(String.format("%02x", b));
//            }
//            return sb.toString();
//        } catch (IOException | NoSuchAlgorithmException e) {
//            Log.e("FileUploader", "Error calculating MD5 checksum", e);
//            return null;
//        }
//    }
/**
 * Upload file (content-URI hoặc đường dẫn thường) kèm Activity để hiển thị Toast cho người dùng;
 * chỉ coi là thành công khi server trả về đúng message xác nhận.
 */
public static void uploadFile(Activity activity, String filePath, UploadCallback callback) {
    uploadFile(activity, filePath, null, callback);
}

/**
 * Upload file with an optional multipart filename (independent of the local file name on disk).
 */
public static void uploadFile(Activity activity, String filePath, String uploadFileName,
                              UploadCallback callback) {
    if (activity != null) {
        setAppContext(activity.getApplicationContext());
    }
    // Check if this is a content URI
    if (filePath.startsWith("content://")) {
        Uri sourceUri = Uri.parse(filePath);
        ContentResolver contentResolver = activity.getContentResolver();
        String fileName = null;

        // Get the file name from URI
        try (Cursor cursor = contentResolver.query(sourceUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
                fileName = cursor.getString(nameIndex);
            }
        } catch (Exception e) {
            Log.e("FileUploader", "Error getting file name from URI", e);
            callback.onUploadComplete(false, "Error getting file name: " + e.getMessage());
            return;
        }

        if (fileName == null) {
            Log.e("FileUploader", "Could not get file name from URI: " + filePath);
            callback.onUploadComplete(false, "Could not get file name from URI");
            return;
        }

        String ownerName = "Vyyy";

        try {
            // Get file content
            InputStream inputStream = contentResolver.openInputStream(sourceUri);
            if (inputStream == null) {
                Log.e("FileUploader", "Failed to open input stream for URI: " + filePath);
                callback.onUploadComplete(false, "Failed to open file");
                return;
            }

            // Create a temporary file for upload
            File tempFile = File.createTempFile("upload_", fileName, activity.getCacheDir());
            try (OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            inputStream.close();

            // Continue with upload process
            String multipartName = !TextUtils.isEmpty(uploadFileName) ? uploadFileName : fileName;
            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), tempFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", multipartName, requestFile);
            RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

            String checksum = calculateMD5(activity, filePath);

            ApiService apiService =
                    RetrofitClient.getInstance().create(ApiService.class);

//            Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN,checksum,body, owner); // for api/v1/file/smapp/upload
            Call<UploadResponse> call = apiService.uploadFile(body, owner); // for api/v1/file/upload
            call.enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    tempFile.delete(); // Clean up temp file
                    if (response.isSuccessful() && response.body() != null) {
                        UploadResponse uploadResponse = response.body();
                        String message = uploadResponse.getMessage();
                        
                        // Log complete JSON response from server
                        String jsonResponse = gson.toJson(uploadResponse);
                        Log.i("FileUploader", "Server Response JSON:\n" + jsonResponse);
                        
                        // Log file information if available
                        if (uploadResponse.getFile() != null) {
                            UploadResponse.FileInfo fileInfo = uploadResponse.getFile();
                            Log.i("FileUploader", "File ID: " + fileInfo.getId() + ", Filename: " + fileInfo.getFilename());
                        }
                        
                        // Only consider upload successful if server confirms with specific messages
                        if (message != null && 
                            (message.equals("File uploaded successfully!") || 
                             message.equals("File uploaded successfully"))) {
                            Log.i("FileUploader", "File upload successful: " + message);
                            completeWithToast(activity, callback, true, message, "File upload successful");
                        } else {
                            String errorMsg = "Server response: " + (message != null ? message : "Unknown error");
                            Log.e("FileUploader", "File upload failed: " + errorMsg);
                            completeWithToast(activity, callback, false, errorMsg,
                                    "File upload failed: " + errorMsg);
                        }
                    } else {
                        String errorMsg = response.message() != null ? response.message() : "Unknown error";
                        Log.e("FileUploader", "File upload failed: " + errorMsg);
                        Log.e("FileUploader", "Response code: " + response.code());
                        completeWithToast(activity, callback, false, errorMsg,
                                "File upload failed: " + errorMsg);
                    }
                }

                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    tempFile.delete(); // Clean up temp file
                    Log.e("FileUploader", "Error uploading file", t);
                    completeWithToast(activity, callback,
                            false, t.getMessage() != null ? t.getMessage() : "Upload error",
                            "Error uploading file");
                }
            });

        } catch (Exception e) {
            Log.e("FileUploader", "Error preparing file for upload", e);
            callback.onUploadComplete(false, "Error: " + e.getMessage());
        }
    } else {
        // Original code for regular files
        File file = new File(filePath);
        String multipartName = !TextUtils.isEmpty(uploadFileName) ? uploadFileName : file.getName();
        String ownerName = "Vyyy";
        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", multipartName, requestFile);
        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

        String checksum = calculateMD5(activity, filePath);

        ApiService apiService =
                RetrofitClient.getInstance().create(ApiService.class);

//        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN,checksum,body, owner); //for api/v1/file/smapp/upload
        Call<UploadResponse> call = apiService.uploadFile(body, owner); // for api/v1/file/upload
        call.enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UploadResponse uploadResponse = response.body();
                    String message = uploadResponse.getMessage();
                    
                    // Log complete JSON response from server
                    String jsonResponse = gson.toJson(uploadResponse);
                    Log.i("FileUploader", "Server Response JSON:\n" + jsonResponse);
                    
                    // Log file information if available
                    if (uploadResponse.getFile() != null) {
                        UploadResponse.FileInfo fileInfo = uploadResponse.getFile();
                        Log.i("FileUploader", "File ID: " + fileInfo.getId() + ", Filename: " + fileInfo.getFilename());
                    }
                    
                    // Only consider upload successful if server confirms with specific messages
                    if (message != null && 
                        (message.equals("File uploaded successfully!") || 
                         message.equals("File uploaded successfully"))) {
                        Log.i("FileUploader", "File upload successful: " + message);
                        completeWithToast(activity, callback, true, message, "File upload successful");
                    } else {
                        String errorMsg = "Server response: " + (message != null ? message : "Unknown error");
                        Log.e("FileUploader", "File upload failed: " + errorMsg);
                        completeWithToast(activity, callback, false, errorMsg,
                                "File upload failed: " + errorMsg);
                    }
                } else {
                    String errorMsg = response.message() != null ? response.message() : "Unknown error";
                    Log.e("FileUploader", "File upload failed: " + errorMsg);
                    Log.e("FileUploader", "Response code: " + response.code());
                    completeWithToast(activity, callback, false, errorMsg,
                            "File upload failed: " + errorMsg);
                }
            }

            @Override
            public void onFailure(Call<UploadResponse> call, Throwable t) {
                Log.e("FileUploader", "Error uploading file", t);
                completeWithToast(activity, callback,
                        false, t.getMessage() != null ? t.getMessage() : "Upload error",
                        "Error uploading file");
            }
        });
    }
}
/**
 * Tính MD5 checksum của file (hỗ trợ cả content-URI và file path), trả về null nếu lỗi đọc/thuật toán.
 */
public static String calculateMD5(Context context, String filePath) {
    try {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        InputStream inputStream;

        // Check if this is a content URI
        if (filePath.startsWith("content://")) {
            inputStream = context.getContentResolver().openInputStream(Uri.parse(filePath));
        } else {
            inputStream = new FileInputStream(new File(filePath));
        }

        if (inputStream == null) {
            Log.e("FileUploader", "Failed to open input stream for MD5 calculation");
            return null;
        }

        byte[] buffer = new byte[1024];
        int numRead;
        while ((numRead = inputStream.read(buffer)) != -1) {
            digest.update(buffer, 0, numRead);
        }
        inputStream.close();

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
    // upload without activity
//    public static void uploadFile(String filePath, UploadCallback callback) {
//        File file = new File(filePath);
//        String ownerName = "Vyyy";
//        RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
//        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
//        RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);
//
//        String checksum = calculateMD5(file);
//
//        ApiService apiService = RetrofitClient.getClient().create(ApiService.class);
////        Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN, checksum, body, owner);
//        Call<ResponseBody> call = apiService.uploadFile(body, owner); // test route
//        call.enqueue(new Callback<ResponseBody>() {
//            @Override
//            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//                if (response.isSuccessful()) {
//                    Log.i("FileUploader", "File upload successful");
//                    retryCount = 0;
//                    callback.onUploadComplete(true, "File upload successful");
//                } else {
//                    Log.e("FileUploader", "File upload failed: " + response.message());
//                    scheduleRetryWithoutActivity(filePath, callback);
//                }
//            }
//
//            @Override
//            public void onFailure(Call<ResponseBody> call, Throwable t) {
//                Log.e("FileUploader", "Error uploading file", t);
//                scheduleRetryWithoutActivity(filePath, callback);
//            }
//        });
//    }
    /**
     * Upload file dùng application context (không có Activity, không Toast) — dành cho background/Worker;
     * yêu cầu setAppContext đã được gọi trước đó.
     */
    public static void uploadFile(String filePath, UploadCallback callback) {
        if (appContext == null) {
            Log.e("FileUploader", "App context is not initialized; cannot upload file safely");
            callback.onUploadComplete(false, "Uploader context is not initialized");
            return;
        }
        // Since we don't have an activity context here, we need app context for content URIs
        if (filePath.startsWith("content://") && appContext != null) {
            // Handle content URIs similar to the activity version but without toasts
            Uri sourceUri = Uri.parse(filePath);
            ContentResolver contentResolver = appContext.getContentResolver();
            String fileName = null;

            try (Cursor cursor = contentResolver.query(sourceUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
                    fileName = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.e("FileUploader", "Error getting file name from URI", e);
                callback.onUploadComplete(false, "Error getting file name: " + e.getMessage());
                return;
            }

            if (fileName == null) {
                Log.e("FileUploader", "Could not get file name from URI: " + filePath);
                callback.onUploadComplete(false, "Could not get file name from URI");
                return;
            }

            String ownerName = "Vyyy";

            try {
                // Get file content
                InputStream inputStream = contentResolver.openInputStream(sourceUri);
                if (inputStream == null) {
                    Log.e("FileUploader", "Failed to open input stream for URI: " + filePath);
                    callback.onUploadComplete(false, "Failed to open file");
                    return;
                }

                // Create a temporary file for upload
                File tempFile = File.createTempFile("upload_", fileName, appContext.getCacheDir());
                try (OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                inputStream.close();
                RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), tempFile);
                MultipartBody.Part body = MultipartBody.Part.createFormData("file", fileName, requestFile);
                RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

                String checksum = calculateMD5(appContext, filePath);

                ApiService apiService =
                        RetrofitClient.getInstance().create(ApiService.class);

//                Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN,checksum,body, owner); // for api/v1/file/smapp/upload
                Call<UploadResponse> call = apiService.uploadFile(body, owner); // for api/v1/file/upload
                call.enqueue(new Callback<UploadResponse>() {
                    @Override
                    public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                        tempFile.delete(); // Clean up temp file
                        if (response.isSuccessful() && response.body() != null) {
                            UploadResponse uploadResponse = response.body();
                            String message = uploadResponse.getMessage();
                            
                            // Log complete JSON response from server
                            String jsonResponse = gson.toJson(uploadResponse);
                            Log.i("FileUploader", "Server Response JSON:\n" + jsonResponse);
                            
                            // Log file information if available
                            if (uploadResponse.getFile() != null) {
                                UploadResponse.FileInfo fileInfo = uploadResponse.getFile();
                                Log.i("FileUploader", "File ID: " + fileInfo.getId() + ", Filename: " + fileInfo.getFilename());
                            }
                            
                            // Only consider upload successful if server confirms with specific messages
                            if (message != null && 
                                (message.equals("File uploaded successfully!") || 
                                 message.equals("File uploaded successfully"))) {
                                Log.i("FileUploader", "File upload successful: " + message);
                                callback.onUploadComplete(true, message);
                            } else {
                                String errorMsg = "Server response: " + (message != null ? message : "Unknown error");
                                Log.e("FileUploader", "File upload failed: " + errorMsg);
                                callback.onUploadComplete(false, errorMsg);
                            }
                        } else {
                            String errorMsg = response.message() != null ? response.message() : "Unknown error";
                            Log.e("FileUploader", "File upload failed: " + errorMsg);
                            Log.e("FileUploader", "Response code: " + response.code());
                            callback.onUploadComplete(false, errorMsg);
                        }
                    }

                    @Override
                    public void onFailure(Call<UploadResponse> call, Throwable t) {
                        tempFile.delete(); // Clean up temp file
                        Log.e("FileUploader", "Error uploading file", t);
                        callback.onUploadComplete(false, t.getMessage() != null ? t.getMessage() : "Upload error");
                    }
                });

            } catch (Exception e) {
                Log.e("FileUploader", "Error preparing file for upload", e);
                callback.onUploadComplete(false, "Error: " + e.getMessage());
            }
        } else {
            // Original code for regular files
            File file = new File(filePath);
            String ownerName = "Vyyy";
            RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
            RequestBody owner = RequestBody.create(MediaType.parse("multipart/form-data"), ownerName);

            String checksum = calculateMD5(appContext, filePath);

            ApiService apiService =
                    RetrofitClient.getInstance().create(ApiService.class);

//            Call<ResponseBody> call = apiService.uploadFile(STATIC_TOKEN,checksum,body, owner);//for api/v1/file/smapp/upload
            Call<UploadResponse> call = apiService.uploadFile(body, owner); // for api/v1/file/upload
            call.enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        UploadResponse uploadResponse = response.body();
                        String message = uploadResponse.getMessage();
                        
                        // Log complete JSON response from server
                        if (BuildConfig.DEBUG) {
                            String jsonResponse = gson.toJson(uploadResponse);
                            Log.i("FileUploader", "Server Response JSON:\n" + jsonResponse);
                        }
                        
                        // Log file information if available
                        if (BuildConfig.DEBUG && uploadResponse.getFile() != null) {
                            UploadResponse.FileInfo fileInfo = uploadResponse.getFile();
                            Log.i("FileUploader",
                                    "File ID: " + fileInfo.getId() +
                                    ", File name: " + fileInfo.getFilename());
                        }
                        
                        // Only consider upload successful if server confirms with specific messages
                        if (message != null && 
                            (message.equals("File uploaded successfully!") || 
                             message.equals("File uploaded successfully"))) {
                            Log.i("FileUploader", "File upload successful: " + message);
                            callback.onUploadComplete(true, message);
                        } else {
                            String errorMsg = "Server response: " + (message != null ? message : "Unknown error");
                            Log.e("FileUploader", "File upload failed: " + errorMsg);
                            callback.onUploadComplete(false, errorMsg);
                        }
                    } else {
                        String errorMsg = response.message() != null ? response.message() : "Unknown error";
                        Log.e("FileUploader", "File upload failed: " + errorMsg);
                        Log.e("FileUploader", "Response code: " + response.code());
                        callback.onUploadComplete(false, errorMsg);
                    }
                }

                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    Log.e("FileUploader", "Error uploading file", t);
                    callback.onUploadComplete(false, t.getMessage() != null ? t.getMessage() : "Upload error");
                }
            });
        }
    }
//    private static class RetrofitClient {
//        private static Retrofit retrofit = null;
//
//        public static Retrofit getClient() {
//            if (retrofit == null) {
//                retrofit = new Retrofit.Builder()
//                        .baseUrl(ApiConfig.BASE_URL)
//                        .addConverterFactory(GsonConverterFactory.create())
//                        .build();
//            }
//            return retrofit;
//        }
//    }

//    private interface ApiService {
//        @Multipart
////        @POST("api/v1/file/smapp/upload")
//        @POST("api/v1/file/upload") //route for upload nepal server
//        Call<ResponseBody> uploadFile(
////                @Header("x-static-token") String staticToken,
////                @Header("x-content-md5") String checksum,
//                @Part MultipartBody.Part file,
//                @Part("owner") RequestBody owner
//        );
//    }

    public static String getBaseUrl() {
        return ApiConfig.BASE_URL;
    }
}
