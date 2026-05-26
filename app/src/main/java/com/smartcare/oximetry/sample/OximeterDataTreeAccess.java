package com.smartcare.oximetry.sample;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Persists a user-granted {@link android.content.Intent#ACTION_OPEN_DOCUMENT_TREE} URI so the app
 * can list files under {@code Download/OximeterData} when direct disk / {@link android.provider.MediaStore}
 * return nothing (common on Samsung + MTP-visible files).
 */
public final class OximeterDataTreeAccess {
    private static final String PREFS = "oximeter_data_saf_tree";
    private static final String KEY_TREE_URI = "tree_uri";

    private OximeterDataTreeAccess() {
    }

    public static Uri getPersistedTreeUri(Context context) {
        if (context == null) {
            return null;
        }
        String s = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        return TextUtils.isEmpty(s) ? null : Uri.parse(s);
    }

    public static void persistTreeUri(Context context, Uri treeUri) {
        if (context == null || treeUri == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .apply();
    }

    public static void clearPersistedTreeUri(Context context) {
        if (context == null) {
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_TREE_URI)
                .apply();
    }
}
