package com.smartcare.oximetry.sample.network;

import com.google.gson.annotations.SerializedName;

public class UploadResponse {
    @SerializedName("message")
    private String message;

    @SerializedName("file")
    private FileInfo file;

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

