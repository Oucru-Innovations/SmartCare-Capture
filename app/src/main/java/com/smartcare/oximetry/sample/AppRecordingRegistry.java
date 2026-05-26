package com.smartcare.oximetry.sample;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers completed recording URIs so File Manager can list them even when
 * MediaStore RELATIVE_PATH is missing or slow to index (common on Samsung tablets).
 */
public final class AppRecordingRegistry {
    private static final String TAG = "AppRecordingRegistry";
    private static final String PREFS = "app_recording_registry";
    private static final String KEY_ENTRIES = "entries";

    private AppRecordingRegistry() {
    }

    public static void registerCompletedRecording(Context context, Uri uri, String displayName) {
        if (context == null || uri == null || TextUtils.isEmpty(displayName)) {
            return;
        }
        if (!OximeterDataFileIndex.isAppDataFileName(displayName)) {
            return;
        }
        try {
            JSONArray entries = loadRawEntries(context);
            String uriStr = uri.toString();
            JSONArray updated = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject obj = entries.getJSONObject(i);
                if (uriStr.equals(obj.optString("uri"))) {
                    obj.put("displayName", displayName);
                    obj.put("savedAt", System.currentTimeMillis());
                    replaced = true;
                }
                updated.put(obj);
            }
            if (!replaced) {
                JSONObject row = new JSONObject();
                row.put("uri", uriStr);
                row.put("displayName", displayName);
                row.put("savedAt", System.currentTimeMillis());
                updated.put(row);
            }
            saveRawEntries(context, updated);
            Log.i(TAG, "Registered recording uri=" + uriStr + " name=" + displayName);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to register recording", e);
        }
    }

    public static void markUploaded(Context context, String uriStr) {
        if (context == null || TextUtils.isEmpty(uriStr)) {
            return;
        }
        try {
            JSONArray entries = loadRawEntries(context);
            JSONArray updated = new JSONArray();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject obj = entries.getJSONObject(i);
                if (!uriStr.equals(obj.optString("uri"))) {
                    updated.put(obj);
                }
            }
            saveRawEntries(context, updated);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to mark uploaded", e);
        }
    }

    public static List<OximeterDataFileIndex.FileEntry> queryRegistryPending(Context context) {
        List<OximeterDataFileIndex.FileEntry> results = new ArrayList<>();
        if (context == null) {
            return results;
        }
        try {
            JSONArray entries = loadRawEntries(context);
            JSONArray surviving = new JSONArray();
            boolean registryMutated = false;
            for (int i = 0; i < entries.length(); i++) {
                JSONObject obj = entries.getJSONObject(i);
                String uriStr = obj.optString("uri");
                String displayName = obj.optString("displayName");
                long savedAt = obj.optLong("savedAt", 0L);
                if (TextUtils.isEmpty(uriStr)) {
                    continue;
                }
                Uri uri = Uri.parse(uriStr);
                if (isUploadedUri(context, uri)) {
                    continue;
                }
                if (!uriStillExists(context, uri)) {
                    OximeterDataFileIndex.FileEntry disk =
                            OximeterDataFileIndex.tryDiskEntryByDisplayName(context, displayName);
                    if (disk != null) {
                        Log.i(TAG, "Registry URI stale; re-bound to disk for " + displayName);
                        FileLogger.logEvent("FILES", "REGISTRY_FALLBACK_DISK",
                                "name=" + displayName + " uri=" + disk.uri);
                        obj.put("uri", disk.uri.toString());
                        obj.put("savedAt", System.currentTimeMillis());
                        registryMutated = true;
                        OximeterDataFileIndex.FileEntry row = disk;
                        if ("content".equalsIgnoreCase(disk.uri.getScheme())) {
                            OximeterDataFileIndex.FileEntry fromStore =
                                    OximeterDataFileIndex.lookupDownloadEntry(context, disk.uri);
                            if (fromStore != null) {
                                row = fromStore;
                            }
                        }
                        results.add(row);
                        surviving.put(obj);
                        continue;
                    }
                    Log.w(TAG, "Dropping stale registry entry (not on disk): " + uriStr);
                    continue;
                }
                String resolvedName = resolveDisplayName(context, uri, displayName);
                if (!OximeterDataFileIndex.isAppDataFileName(resolvedName)) {
                    continue;
                }
                long modified = savedAt;
                long size = 0;
                OximeterDataFileIndex.FileEntry fromStore =
                        OximeterDataFileIndex.lookupDownloadEntry(context, uri);
                if (fromStore != null) {
                    resolvedName = fromStore.displayName;
                    modified = fromStore.lastModifiedMs > 0 ? fromStore.lastModifiedMs : modified;
                    size = fromStore.sizeBytes;
                    uri = fromStore.uri;
                }
                results.add(new OximeterDataFileIndex.FileEntry(uri, resolvedName, size, modified));
                surviving.put(obj);
            }
            if (registryMutated || surviving.length() != entries.length()) {
                saveRawEntries(context, surviving);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to read registry", e);
        }
        Log.d(TAG, "Registry pending entries: " + results.size());
        return results;
    }

    private static boolean uriStillExists(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            return path != null && new java.io.File(path).isFile();
        }
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isUploadedUri(Context context, Uri uri) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            return path != null && path.contains("Uploaded");
        }
        OximeterDataFileIndex.FileEntry entry = OximeterDataFileIndex.lookupDownloadEntry(context, uri);
        if (entry == null) {
            return false;
        }
        try (Cursor c = context.getContentResolver().query(
                uri,
                new String[]{MediaStore.MediaColumns.RELATIVE_PATH},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
                if (col >= 0) {
                    String path = c.getString(col);
                    return path != null && path.contains("Uploaded");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String resolveDisplayName(Context context, Uri uri, String fallback) {
        try (Cursor c = context.getContentResolver().query(
                uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (col >= 0) {
                    String name = c.getString(col);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve display name for " + uri, e);
        }
        return fallback;
    }

    private static JSONArray loadRawEntries(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ENTRIES, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void saveRawEntries(Context context, JSONArray entries) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, entries.toString())
                .apply();
    }
}
