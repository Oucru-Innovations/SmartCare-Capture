package com.smartcare.oximetry.sample.network;

import com.google.gson.annotations.SerializedName;

/**
 * DTO Gson cho response upload; thành công được xác định qua trường {@code message}.
 */
public class UploadResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("file")
    private FileInfo file;

    /**
     * Trả về message từ server, dùng để kiểm tra upload có thành công hay không.
     */
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public FileInfo getFile() {
        return file;
    }

    public void setFile(FileInfo file) {
        this.file = file;
    }

    /**
     * Thông tin file do server trả về (id, filename) sau khi upload thành công.
     */
    public static class FileInfo {
        @SerializedName("id")
        private Integer id;

        @SerializedName("filename")
        private String filename;

        // Add other fields as needed

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }
    }
}

