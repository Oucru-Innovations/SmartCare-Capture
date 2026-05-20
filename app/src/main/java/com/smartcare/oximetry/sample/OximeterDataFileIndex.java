package com.smartcare.oximetry.sample;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lists CSV/JSON files under Download/OximeterData via MediaStore (same source used when recording).
 */
public final class OximeterDataFileIndex {
    private static final String TAG = "OximeterDataFileIndex";
    public static final String FOLDER_NAME = "OximeterData";
    private static final String UPLOADED_SUBDIR = "Uploaded";

    private static final String PENDING_RELATIVE_PATH =
            Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME;
    private static final String UPLOADED_RELATIVE_PATH =
            PENDING_RELATIVE_PATH + "/" + UPLOADED_SUBDIR;

    private OximeterDataFileIndex() {
    }

    public static final class FileEntry {
        public final Uri uri;
        public final String displayName;
        public final long sizeBytes;
        public final long lastModifiedMs;

        FileEntry(Uri uri, String displayName, long sizeBytes, long lastModifiedMs) {
            this.uri = uri;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
            this.lastModifiedMs = lastModifiedMs;
        }
    }

    public static List<FileEntry> queryPendingFiles(Context context) {
        List<FileEntry> entries = queryMediaStoreFolder(context, PENDING_RELATIVE_PATH);
        mergeLegacyDiskFiles(context, entries, false);
        sortByModifiedDesc(entries);
        return entries;
    }

    public static List<FileEntry> queryUploadedFiles(Context context) {
        List<FileEntry> entries = queryMediaStoreFolder(context, UPLOADED_RELATIVE_PATH);
        mergeLegacyDiskFiles(context, entries, true);
        sortByModifiedDesc(entries);
        return entries;
    }

    private static List<FileEntry> queryMediaStoreFolder(Context context, String relativePath) {
        List<FileEntry> results = new ArrayList<>();
        String pathNoSlash = trimTrailingSlash(relativePath);
        String pathWithSlash = pathNoSlash + "/";

        String selection = MediaStore.Downloads.RELATIVE_PATH + "=? OR "
                + MediaStore.Downloads.RELATIVE_PATH + "=?";
        String[] selectionArgs = {pathNoSlash, pathWithSlash};

        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
        };

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Downloads.DATE_MODIFIED + " DESC")) {

            if (cursor == null) {
                Log.w(TAG, "MediaStore query returned null for " + relativePath);
                return results;
            }

            int idCol = cursor.getColumnIndex(MediaStore.Downloads._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED);

            while (cursor.moveToNext()) {
                if (idCol < 0 || nameCol < 0) {
                    continue;
                }
                String name = cursor.getString(nameCol);
                if (!isDataFileName(name)) {
                    continue;
                }

                long id = cursor.getLong(idCol);
                Uri uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);

                long mediaSize = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                long size = resolveAccurateSize(context, uri, mediaSize);

                long modified = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                if (modified > 0 && modified < 1_000_000_000_000L) {
                    modified *= 1000L;
                }

                results.add(new FileEntry(uri, name, size, modified));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query MediaStore for " + relativePath, e);
        }

        Log.d(TAG, "MediaStore files in " + relativePath + ": " + results.size());
        return results;
    }

    private static void mergeLegacyDiskFiles(Context context, List<FileEntry> entries, boolean uploaded) {
        File dir = uploaded ? getUploadedDirectory() : getPendingDirectory();
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        Set<String> knownNames = new HashSet<>();
        for (FileEntry entry : entries) {
            knownNames.add(entry.displayName);
        }

        File[] files = dir.listFiles(file ->
                file.isFile() && isDataFileName(file.getName()));
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (knownNames.contains(file.getName())) {
                continue;
            }
            entries.add(new FileEntry(
                    Uri.fromFile(file),
                    file.getName(),
                    file.length(),
                    file.lastModified()));
        }
    }

    private static long resolveAccurateSize(Context context, Uri uri, long mediaStoreSize) {
        if (!"content".equals(uri.getScheme())) {
            return mediaStoreSize;
        }
        try (ParcelFileDescriptor pfd =
                     context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                long statSize = pfd.getStatSize();
                if (statSize >= 0) {
                    return statSize;
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not stat file size for " + uri + ", using MediaStore size", e);
        }
        return Math.max(mediaStoreSize, 0);
    }

    private static boolean isDataFileName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".csv") || lower.endsWith(".json");
    }

    private static String trimTrailingSlash(String path) {
        if (path != null && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static void sortByModifiedDesc(List<FileEntry> entries) {
        Collections.sort(entries, (a, b) -> Long.compare(b.lastModifiedMs, a.lastModifiedMs));
    }

    public static File getPendingDirectory() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, FOLDER_NAME);
    }

    public static File getUploadedDirectory() {
        return new File(getPendingDirectory(), UPLOADED_SUBDIR);
    }
}
