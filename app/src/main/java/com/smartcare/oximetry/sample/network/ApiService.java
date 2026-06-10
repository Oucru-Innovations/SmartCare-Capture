package com.smartcare.oximetry.sample.network;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * Retrofit interface khai báo endpoint multipart upload của server.
 */
public interface ApiService {

    /**
     * Gửi POST api/v1/file/upload dạng multipart: phần {@code file} và field {@code owner}.
     */
    @Multipart
    @POST("api/v1/file/upload")
    Call<UploadResponse> uploadFile(
            @Part MultipartBody.Part file,
            @Part("owner") RequestBody owner
    );
}
