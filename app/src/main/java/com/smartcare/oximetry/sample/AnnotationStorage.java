package com.smartcare.oximetry.sample;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class AnnotationStorage {
    private static final String TAG = "AnnotationStorage";

    static final String FOLDER_NAME = "OximeterData";
    static final String ANNOTATION_SUBDIR = "Annotation";
    static final String MASTER_FILE_NAME = "Annotations.json";
    static final String MIME_JSON = "application/json";

    /** Phần đường dẫn tương đối nhận diện file master (để phân biệt với các JSON khác cùng tên). */
    static final String MASTER_PATH_MARKER = FOLDER_NAME + "/" + ANNOTATION_SUBDIR;

    private AnnotationStorage() {
    }

    /** {@code Download/OximeterData/Annotation/} — nơi đặt file master Annotations.json. */
    static String masterRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/" + ANNOTATION_SUBDIR + "/";
    }

    /** {@code Download/OximeterData/} — nơi đặt các file export Annotation_*.json. */
    static String exportRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/";
    }

    /**
     * Tìm entry trong MediaStore Downloads theo tên hiển thị; nếu {@code pathContains} khác null thì
     * chỉ chấp nhận entry có RELATIVE_PATH chứa chuỗi đó. Trả về content URI hoặc null nếu không có.
     */
    static Uri findEntry(Context context, String displayName, String pathContains) {
        if (context == null || TextUtils.isEmpty(displayName)) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] args = {displayName};
        try (Cursor cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.RELATIVE_PATH},
                selection, args, MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return null;
            }
            int idCol = cursor.getColumnIndex(MediaStore.Downloads._ID);
            int pathCol = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                if (idCol < 0) {
                    continue;
                }
                if (pathContains != null) {
                    String relPath = pathCol >= 0 ? cursor.getString(pathCol) : null;
                    if (relPath == null || !relPath.toLowerCase(Locale.US)
                            .contains(pathContains.toLowerCase(Locale.US))) {
                        continue;
                    }
                }
                return ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idCol));
            }
        } catch (Exception e) {
            Log.w(TAG, "findEntry failed: " + displayName, e);
        }
        return null;
    }

    /** Tạo một entry mới trong MediaStore Downloads; trả về content URI hoặc null nếu thất bại. */
    static Uri createEntry(Context context, String displayName, String relativePath, String mime) {
        if (context == null) {
            return null;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
        values.put(MediaStore.Downloads.MIME_TYPE, mime);
        values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
        try {
            Uri uri = context.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            Log.i(TAG, "Created MediaStore entry " + uri + " at " + relativePath + displayName);
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "createEntry failed: " + displayName, e);
            return null;
        }
    }

    /** Lấy URI của file master Annotations.json, tạo mới nếu chưa tồn tại. */
    static Uri ensureMasterEntry(Context context) {
        Uri uri = findEntry(context, MASTER_FILE_NAME, MASTER_PATH_MARKER);
        if (uri != null) {
            return uri;
        }
        return createEntry(context, MASTER_FILE_NAME, masterRelativePath(), MIME_JSON);
    }

    /** Đọc toàn bộ nội dung text (UTF-8) của một entry; null nếu thiếu/không đọc được. */
    static String readText(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "readText failed: " + uri, e);
            return null;
        }
    }

    /** Ghi đè (truncate) nội dung text UTF-8 vào entry; trả về true nếu thành công. */
    static boolean writeText(Context context, Uri uri, String content) {
        if (context == null || uri == null) {
            return false;
        }
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) {
                return false;
            }
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write(content);
            writer.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeText failed: " + uri, e);
            return false;
        }
    }
}
