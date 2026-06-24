package com.smartcare.oximetry.sample;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists app exports: {@code SmartCareCsv*.csv} and {@code Annotation_*.json}.
 * <p>
 * Uses <b>disk scan</b> plus <b>MediaStore Downloads</b>. On Android 10+ Samsung devices,
 * {@code File.listFiles()} often returns nothing without storage permission, while
 * MediaStore (and MTP) still see the files — so both sources are merged by display name.
 * If the user grants a {@link android.content.Intent#ACTION_OPEN_DOCUMENT_TREE} folder (see
 * {@link OximeterDataTreeAccess}), that listing is merged too.
 */
public final class OximeterDataFileIndex {
    private static final String TAG = "OximeterDataFileIndex";
    public static final String FOLDER_NAME = "OximeterData";
    /** Common manual-folder typo next to {@link #FOLDER_NAME}; scanned for listing only. */
    private static final String FOLDER_NAME_TYPO = "OximetaData";
    private static final String UPLOADED_SUBDIR = "Uploaded";

    private static final String PREFIX_SMARTCARE = "SmartCareCsv";
    /** Exported per-note JSON only; not {@code Annotation/Annotations.json}. */
    private static final String PREFIX_ANNOTATION_EXPORT = "Annotation_";
    private static final String MASTER_ANNOTATIONS_FILE = "Annotations.json";

    private OximeterDataFileIndex() {
    }

    /** Một mục file trong index: URI, tên hiển thị, kích thước và thời điểm sửa đổi (đã gộp từ nhiều nguồn). */
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

    /**
     * App-generated exports: {@code SmartCareCsv*.csv} and {@code Annotation_*.json}.
     */
    public static boolean isAppDataFileName(String name) {
        if (name == null) {
            return false;
        }
        if (MASTER_ANNOTATIONS_FILE.equalsIgnoreCase(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".csv")) {
            return name.regionMatches(true, 0, PREFIX_SMARTCARE, 0, PREFIX_SMARTCARE.length());
        }
        if (lower.endsWith(".json")) {
            return name.regionMatches(true, 0, PREFIX_ANNOTATION_EXPORT, 0,
                    PREFIX_ANNOTATION_EXPORT.length());
        }
        return false;
    }

    /**
     * Pending: {@code Download/OximeterData} (not under {@code Uploaded}), via disk + MediaStore.
     */
    public static List<FileEntry> queryPendingFiles(Context context) {
        List<FileEntry> merged = new ArrayList<>();
        List<FileEntry> disk = scanPendingDiskFiles(context);
        List<FileEntry> mediaDl = queryMediaStoreDownloads(context, false);
        List<FileEntry> mediaFiles = queryMediaStoreFilesVolume(context, false);
        List<FileEntry> registry = AppRecordingRegistry.queryRegistryPending(context);
        List<FileEntry> docPending = new ArrayList<>();
        List<FileEntry> docUploaded = new ArrayList<>();
        collectDocumentTreeSplit(context, docPending, docUploaded);
        mergeByDisplayName(merged, disk);
        mergeByDisplayName(merged, mediaDl);
        mergeByDisplayName(merged, mediaFiles);
        mergeByDisplayName(merged, registry);
        mergeByDisplayName(merged, docPending);
        sortByModifiedDesc(merged);
        FileLogger.logEvent("FILES", "FILE_INDEX_SOURCE",
                "pending diskCount=" + disk.size()
                        + " mediaDownloadsCount=" + mediaDl.size()
                        + " mediaFilesCount=" + mediaFiles.size()
                        + " registryCount=" + registry.size()
                        + " docTreePending=" + docPending.size()
                        + " docTreeUploaded=" + docUploaded.size()
                        + " path=" + getPendingDirectory().getAbsolutePath());
        logEntryNames("pending", merged);
        return merged;
    }

    /** Uploaded tab: disk + MediaStore under {@code Uploaded}. */
    public static List<FileEntry> queryUploadedFiles(Context context) {
        List<FileEntry> merged = new ArrayList<>();
        List<FileEntry> disk = scanUploadedDiskFiles(context);
        List<FileEntry> mediaDl = queryMediaStoreDownloads(context, true);
        List<FileEntry> mediaFiles = queryMediaStoreFilesVolume(context, true);
        List<FileEntry> docPending = new ArrayList<>();
        List<FileEntry> docUploaded = new ArrayList<>();
        collectDocumentTreeSplit(context, docPending, docUploaded);
        mergeByDisplayName(merged, disk);
        mergeByDisplayName(merged, mediaDl);
        mergeByDisplayName(merged, mediaFiles);
        mergeByDisplayName(merged, docUploaded);
        sortByModifiedDesc(merged);
        logEntryNames("uploaded", merged);
        return merged;
    }

    /** One-time scan so other apps / MediaStore see new files (optional; not required for listing). */
    public static void refreshMediaStore(Context context) {
        refreshMediaStoreDeep(context);
    }

    /**
     * Quét sâu (deep scan) mọi file app dưới các thư mục OximeterData và yêu cầu MediaScanner
     * lập chỉ mục lại, để các app khác / MediaStore thấy được file mới. Chỉ tối ưu hiển thị,
     * không bắt buộc cho việc liệt kê.
     */
    public static void refreshMediaStoreDeep(Context context) {
        if (context == null) {
            return;
        }
        List<String> paths = new ArrayList<>();
        for (File root : getAllOximeterDataRoots()) {
            collectAppDataFilePaths(root, paths);
        }
        if (paths.isEmpty()) {
            Log.d(TAG, "refreshMediaStoreDeep: no matching files to scan");
            return;
        }
        Log.i(TAG, "refreshMediaStoreDeep: scanning " + paths.size() + " files");
        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                paths.toArray(new String[0]),
                null,
                (path, uri) -> Log.d(TAG, "Media scan: " + path + " -> " + uri));
    }

    private static void collectAppDataFilePaths(File root, List<String> out) {
        if (root == null || !root.exists()) {
            return;
        }
        if (root.isFile()) {
            if (isAppDataFileName(root.getName())) {
                out.add(root.getAbsolutePath());
            }
            return;
        }
        File[] children = root.listFiles();
        if (children == null) {
            Log.w(TAG, "collectAppDataFilePaths: listFiles null: " + root);
            return;
        }
        for (File child : children) {
            collectAppDataFilePaths(child, out);
        }
    }

    public static FileEntry tryDiskEntryByDisplayName(Context context, String displayName) {
        if (context == null || !isAppDataFileName(displayName)) {
            return null;
        }
        File pending = new File(getPendingDirectory(), displayName);
        if (pending.isFile()) {
            return entryFromDiskFile(context, pending);
        }
        File uploaded = new File(getUploadedDirectory(), displayName);
        if (uploaded.isFile()) {
            return entryFromDiskFile(context, uploaded);
        }
        File typoRoot = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER_NAME_TYPO);
        File pendingTypo = new File(typoRoot, displayName);
        if (pendingTypo.isFile()) {
            return entryFromDiskFile(context, pendingTypo);
        }
        File uploadedTypo = new File(new File(typoRoot, UPLOADED_SUBDIR), displayName);
        if (uploadedTypo.isFile()) {
            return entryFromDiskFile(context, uploadedTypo);
        }
        return null;
    }

    /**
     * Resolve metadata for any {@code content://} media row (Downloads or Files volume).
     */
    public static FileEntry lookupDownloadEntry(Context context, Uri uri) {
        if (context == null || uri == null || !"content".equals(uri.getScheme())) {
            return null;
        }
        String[] projection = {
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
        };
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            if (nameCol < 0) {
                return null;
            }
            String name = cursor.getString(nameCol);
            if (!isAppDataFileName(name)) {
                return null;
            }
            long mediaSize = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
            long size = resolveAccurateSize(context, uri, mediaSize);
            long modified = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
            if (modified > 0 && modified < 1_000_000_000_000L) {
                modified *= 1000L;
            }
            return new FileEntry(uri, name, size, modified);
        } catch (Exception e) {
            Log.w(TAG, "lookupDownloadEntry failed for " + uri, e);
            return null;
        }
    }

    public static void notifyUriUpdated(Context context, Uri uri) {
        if (context == null || uri == null) {
            return;
        }
        try {
            context.getApplicationContext().getContentResolver().notifyChange(uri, null);
        } catch (Exception e) {
            Log.w(TAG, "notifyChange failed for " + uri, e);
        }
    }

    /**
     * Báo cho hệ thống biết một bản ghi vừa được lưu: notify thay đổi URI, đăng ký vào
     * {@link AppRecordingRegistry} và làm mới MediaStore để File Manager hiển thị ngay.
     */
    public static void notifyRecordingSaved(Context context, Uri recordingUri, String displayName) {
        notifyUriUpdated(context, recordingUri);
        if (!TextUtils.isEmpty(displayName)) {
            AppRecordingRegistry.registerCompletedRecording(context, recordingUri, displayName);
        }
        refreshMediaStore(context);
    }

    /**
     * Rows in {@link MediaStore.Downloads} whose name matches SmartCareCsv* / Annotation_*,
     * filtered by relative path under OximeterData (pending vs Uploaded).
     */
    private static List<FileEntry> queryMediaStoreDownloads(Context context, boolean uploadedOnly) {
        List<FileEntry> results = new ArrayList<>();
        if (context == null) {
            return results;
        }
        String selection = "(" + MediaStore.Downloads.DISPLAY_NAME + " LIKE ? OR "
                + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?)";
        String[] selectionArgs = {PREFIX_SMARTCARE + "%", PREFIX_ANNOTATION_EXPORT + "%"};
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.RELATIVE_PATH,
        };
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return results;
            }
            int idCol = cursor.getColumnIndex(MediaStore.Downloads._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndex(MediaStore.Downloads.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.Downloads.DATE_MODIFIED);
            int pathCol = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH);
            while (cursor.moveToNext()) {
                if (idCol < 0 || nameCol < 0) {
                    continue;
                }
                String name = cursor.getString(nameCol);
                if (!isAppDataFileName(name)) {
                    continue;
                }
                String relPath = pathCol >= 0 ? cursor.getString(pathCol) : null;
                boolean inUploaded = isInUploadedRelativePath(relPath);
                boolean underOximeter = relPath == null || relPathMentionsOximeterFolder(relPath);
                if (!underOximeter) {
                    continue;
                }
                if (uploadedOnly != inUploaded) {
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
            Log.e(TAG, "queryMediaStoreDownloads failed uploadedOnly=" + uploadedOnly, e);
        }
        Log.d(TAG, "MediaStore downloads uploadedOnly=" + uploadedOnly + " count=" + results.size());
        return results;
    }

    /**
     * Samsung/API 30: some rows appear in {@link MediaStore.Files} but not in the Downloads view.
     * Uses the same display-name rules; pending vs Uploaded from {@link MediaStore.MediaColumns#RELATIVE_PATH}.
     */
    private static List<FileEntry> queryMediaStoreFilesVolume(Context context, boolean uploadedOnly) {
        List<FileEntry> results = new ArrayList<>();
        if (context == null) {
            return results;
        }
        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String selection = "(" + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? OR "
                + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?)";
        String[] selectionArgs = {PREFIX_SMARTCARE + "%", PREFIX_ANNOTATION_EXPORT + "%"};
        String[] projection = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.VOLUME_NAME,
        };
        try (Cursor cursor = context.getContentResolver().query(
                collection,
                projection,
                selection,
                selectionArgs,
                MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            if (cursor == null) {
                return results;
            }
            int idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
            int nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            int sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            int pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH);
            int volCol = cursor.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME);
            while (cursor.moveToNext()) {
                if (idCol < 0 || nameCol < 0) {
                    continue;
                }
                String name = cursor.getString(nameCol);
                if (!isAppDataFileName(name)) {
                    continue;
                }
                String relPath = pathCol >= 0 ? cursor.getString(pathCol) : null;
                if (!matchesFilesQueryRow(relPath, uploadedOnly)) {
                    continue;
                }
                long id = cursor.getLong(idCol);
                String volume = MediaStore.VOLUME_EXTERNAL;
                if (volCol >= 0) {
                    String v = cursor.getString(volCol);
                    if (!TextUtils.isEmpty(v)) {
                        volume = v;
                    }
                }
                Uri uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), id);
                long mediaSize = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0;
                long size = resolveAccurateSize(context, uri, mediaSize);
                long modified = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                if (modified > 0 && modified < 1_000_000_000_000L) {
                    modified *= 1000L;
                }
                results.add(new FileEntry(uri, name, size, modified));
            }
        } catch (Exception e) {
            Log.e(TAG, "queryMediaStoreFilesVolume failed uploadedOnly=" + uploadedOnly, e);
        }
        Log.d(TAG, "MediaStore files vol uploadedOnly=" + uploadedOnly + " count=" + results.size());
        return results;
    }

    private static boolean isInUploadedRelativePath(String relPath) {
        if (TextUtils.isEmpty(relPath)) {
            return false;
        }
        String n = relPath.replace('\\', '/');
        return n.contains("/" + UPLOADED_SUBDIR + "/") || n.endsWith("/" + UPLOADED_SUBDIR);
    }

    private static boolean relPathMentionsOximeterFolder(String relPath) {
        if (TextUtils.isEmpty(relPath)) {
            return false;
        }
        String p = relPath.toLowerCase(Locale.US);
        return p.contains(FOLDER_NAME.toLowerCase(Locale.US))
                || p.contains(FOLDER_NAME_TYPO.toLowerCase(Locale.US));
    }

    /** Pending vs uploaded split for MediaStore.Files rows. */
    private static boolean matchesFilesQueryRow(String relPath, boolean uploadedOnly) {
        boolean inUploaded = isInUploadedRelativePath(relPath);
        if (uploadedOnly) {
            return inUploaded && relPathMentionsOximeterFolder(relPath);
        }
        if (inUploaded) {
            return false;
        }
        return TextUtils.isEmpty(relPath) || relPathMentionsOximeterFolder(relPath);
    }

    private static void mergeByDisplayName(List<FileEntry> target, List<FileEntry> more) {
        Map<String, FileEntry> byName = new LinkedHashMap<>();
        for (FileEntry e : target) {
            byName.put(e.displayName, e);
        }
        for (FileEntry e : more) {
            FileEntry cur = byName.get(e.displayName);
            byName.put(e.displayName, preferEntry(cur, e));
        }
        target.clear();
        target.addAll(byName.values());
    }

    private static FileEntry preferEntry(FileEntry a, FileEntry b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean ac = "content".equals(a.uri.getScheme());
        boolean bc = "content".equals(b.uri.getScheme());
        if (bc && !ac) {
            return b;
        }
        if (ac && !bc) {
            return a;
        }
        if (b.lastModifiedMs > a.lastModifiedMs) {
            return b;
        }
        if (b.sizeBytes > a.sizeBytes) {
            return b;
        }
        return a;
    }

    private static List<File> getAllOximeterDataRoots() {
        List<File> roots = new ArrayList<>();
        roots.add(getPendingDirectory());
        File typo = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER_NAME_TYPO);
        if (typo.exists() && typo.isDirectory()) {
            roots.add(typo);
        }
        return roots;
    }

    private static List<FileEntry> scanPendingDiskFiles(Context context) {
        List<FileEntry> results = new ArrayList<>();
        for (File root : getAllOximeterDataRoots()) {
            if (!root.exists() || !root.isDirectory()) {
                Log.d(TAG, "scanPendingDiskFiles: folder missing " + root);
                continue;
            }
            File[] children = root.listFiles();
            if (children == null) {
                Log.w(TAG, "scanPendingDiskFiles: listFiles null (no permission?) " + root);
                continue;
            }
            for (File child : children) {
                if (child.isDirectory() && UPLOADED_SUBDIR.equals(child.getName())) {
                    continue;
                }
                collectDiskFilesRecursive(context, child, results);
            }
        }
        Log.d(TAG, "Disk pending app files: " + results.size());
        return results;
    }

    private static List<FileEntry> scanUploadedDiskFiles(Context context) {
        List<FileEntry> results = new ArrayList<>();
        File mainUploaded = getUploadedDirectory();
        if (mainUploaded.exists() && mainUploaded.isDirectory()) {
            collectDiskFilesRecursive(context, mainUploaded, results);
        }
        File typoUploaded = new File(
                new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        FOLDER_NAME_TYPO),
                UPLOADED_SUBDIR);
        if (typoUploaded.exists() && typoUploaded.isDirectory()
                && !typoUploaded.getAbsolutePath().equals(mainUploaded.getAbsolutePath())) {
            collectDiskFilesRecursive(context, typoUploaded, results);
        }
        Log.d(TAG, "Disk uploaded app files: " + results.size());
        return results;
    }

    private static void collectDiskFilesRecursive(Context context, File node, List<FileEntry> out) {
        if (node == null || !node.exists()) {
            return;
        }
        if (node.isFile()) {
            if (isAppDataFileName(node.getName())) {
                out.add(entryFromDiskFile(context, node));
            }
            return;
        }
        File[] children = node.listFiles();
        if (children == null) {
            Log.w(TAG, "collectDiskFilesRecursive: listFiles null: " + node);
            return;
        }
        for (File child : children) {
            collectDiskFilesRecursive(context, child, out);
        }
    }

    private static FileEntry entryFromDiskFile(Context context, File file) {
        Uri uri = resolveContentUriForFile(context, file);
        if (uri == null) {
            uri = Uri.fromFile(file);
        }
        return new FileEntry(uri, file.getName(), file.length(), file.lastModified());
    }

    /** Best-effort: same filename in Downloads MediaStore → content URI. */
    private static Uri resolveContentUriForFile(Context context, File file) {
        String selection = MediaStore.Downloads.DISPLAY_NAME + "=?";
        String[] selectionArgs = {file.getName()};
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID},
                selection,
                selectionArgs,
                MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndex(MediaStore.Downloads._ID);
                if (idCol >= 0) {
                    return ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idCol));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve MediaStore URI for " + file.getName(), e);
        }
        return null;
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
            Log.w(TAG, "Could not stat file size for " + uri, e);
        }
        return Math.max(mediaStoreSize, 0);
    }

    private static void sortByModifiedDesc(List<FileEntry> entries) {
        Collections.sort(entries, (a, b) -> Long.compare(b.lastModifiedMs, a.lastModifiedMs));
    }

    private static void logEntryNames(String listName, List<FileEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("list=").append(listName).append(" count=").append(entries.size());
        for (FileEntry e : entries) {
            sb.append(" | ").append(e.displayName);
        }
        Log.i(TAG, sb.toString());
        FileLogger.logEvent("FILES", "FILE_INDEX_" + listName.toUpperCase(Locale.US), sb.toString());
    }

    /**
     * User-granted {@link android.content.Intent#ACTION_OPEN_DOCUMENT_TREE} (typically
     * {@code Download/OximeterData}): fills pending vs uploaded file rows using
     * {@link android.provider.DocumentsContract} (works when {@link File} and MediaStore do not).
     */
    private static void collectDocumentTreeSplit(Context context,
            List<FileEntry> pendingOut, List<FileEntry> uploadedOut) {
        pendingOut.clear();
        uploadedOut.clear();
        if (context == null) {
            return;
        }
        Uri treeUri = OximeterDataTreeAccess.getPersistedTreeUri(context);
        if (treeUri == null) {
            return;
        }
        try {
            String rootDocId = DocumentsContract.getTreeDocumentId(treeUri);
            walkDocumentTree(context, treeUri, rootDocId, false, pendingOut, uploadedOut);
            Log.d(TAG, "Document tree split pending=" + pendingOut.size() + " uploaded=" + uploadedOut.size());
        } catch (Exception e) {
            Log.e(TAG, "collectDocumentTreeSplit failed for " + treeUri, e);
        }
    }

    private static boolean documentIdInUploadedBranch(String documentId) {
        if (documentId == null) {
            return false;
        }
        return documentId.contains("/" + UPLOADED_SUBDIR + "/")
                || documentId.endsWith("/" + UPLOADED_SUBDIR);
    }

    private static void walkDocumentTree(Context context, Uri treeUri, String docId,
            boolean inUploadedSubtree, List<FileEntry> pendingOut, List<FileEntry> uploadedOut) {
        if (context == null || treeUri == null || docId == null) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        try (Cursor cursor = resolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        }, null, null, null)) {
            if (cursor == null) {
                return;
            }
            int idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int dateCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
            while (cursor.moveToNext()) {
                if (idCol < 0) {
                    continue;
                }
                String childDocId = cursor.getString(idCol);
                String name = nameCol >= 0 ? cursor.getString(nameCol) : null;
                String mime = mimeCol >= 0 ? cursor.getString(mimeCol) : null;
                long size = sizeCol >= 0 ? cursor.getLong(sizeCol) : 0L;
                long lastMod = dateCol >= 0 ? cursor.getLong(dateCol) : 0L;
                boolean idInUploaded = documentIdInUploadedBranch(childDocId);
                boolean inUp = inUploadedSubtree || idInUploaded;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    boolean nextUploaded = inUploadedSubtree || UPLOADED_SUBDIR.equals(name) || idInUploaded;
                    walkDocumentTree(context, treeUri, childDocId, nextUploaded, pendingOut, uploadedOut);
                } else if (isAppDataFileName(name)) {
                    Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId);
                    FileEntry row = new FileEntry(fileUri, name, size, lastMod);
                    if (inUp) {
                        uploadedOut.add(row);
                    } else {
                        pendingOut.add(row);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "walkDocumentTree failed at docId=" + docId, e);
        }
    }

    /** {@code Download/OximeterData/} — canonical MediaStore relative path for pending exports. */
    public static String pendingRelativePath() {
        return Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/";
    }

    /** {@code Download/OximeterData/Uploaded/} — canonical MediaStore relative path after upload. */
    public static String uploadedRelativePath() {
        return pendingRelativePath() + UPLOADED_SUBDIR + "/";
    }

    /**
     * Old app versions wrote CSV/JSON under {@code Android/data/.../files/OximeterData}.
     * Move any remaining app data files to {@code Download/OximeterData} on startup.
     * {@link FileLogger} under {@code externalFilesDir/Logs} is intentionally not touched.
     */
    public static int migrateLegacyPrivateFiles(Context context) {
        if (context == null) {
            return 0;
        }
        File legacyRoot = getLegacyPrivateDirectory(context);
        if (legacyRoot == null || !legacyRoot.exists()) {
            return 0;
        }
        File pendingDir = getPendingDirectory();
        File uploadedDir = getUploadedDirectory();
        pendingDir.mkdirs();
        uploadedDir.mkdirs();
        int moved = migrateLegacyTree(legacyRoot, pendingDir, uploadedDir);
        if (moved > 0) {
            Log.i(TAG, "Migrated " + moved + " file(s) from legacy private OximeterData to Download");
            FileLogger.logEvent("FILES", "LEGACY_MIGRATION",
                    "moved=" + moved + " from=" + legacyRoot.getAbsolutePath()
                            + " to=" + pendingDir.getAbsolutePath());
            refreshMediaStoreDeep(context);
        }
        deleteEmptyLegacyTree(legacyRoot);
        return moved;
    }

    /** App-private folder used by older builds: {@code externalFilesDir/OximeterData}. */
    public static File getLegacyPrivateDirectory(Context context) {
        if (context == null) {
            return null;
        }
        File base = context.getApplicationContext().getExternalFilesDir(null);
        return base != null ? new File(base, FOLDER_NAME) : null;
    }

    private static int migrateLegacyTree(File srcRoot, File pendingDir, File uploadedDir) {
        return migrateLegacyTree(srcRoot, pendingDir, uploadedDir, false);
    }

    private static int migrateLegacyTree(File srcRoot, File pendingDir, File uploadedDir,
                                         boolean inUploadedBranch) {
        int moved = 0;
        File[] children = srcRoot.listFiles();
        if (children == null) {
            return 0;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                boolean nextUploaded = inUploadedBranch || UPLOADED_SUBDIR.equals(child.getName());
                if (nextUploaded) {
                    moved += migrateLegacyTree(child, pendingDir, uploadedDir, true);
                } else {
                    File destSubdir = new File(pendingDir, child.getName());
                    destSubdir.mkdirs();
                    moved += migrateLegacyTree(child, destSubdir, uploadedDir, false);
                }
                deleteEmptyLegacyTree(child);
                continue;
            }
            if (!isAppDataFileName(child.getName())
                    && !MASTER_ANNOTATIONS_FILE.equalsIgnoreCase(child.getName())) {
                continue;
            }
            File destDir;
            if (MASTER_ANNOTATIONS_FILE.equalsIgnoreCase(child.getName())) {
                destDir = new File(pendingDir, "Annotation");
            } else {
                destDir = inUploadedBranch ? uploadedDir : pendingDir;
            }
            if (moveFileIntoDirectory(child, destDir)) {
                moved++;
            }
        }
        return moved;
    }

    private static boolean moveFileIntoDirectory(File src, File destDir) {
        if (src == null || destDir == null || !src.isFile()) {
            return false;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            return false;
        }
        File dest = new File(destDir, src.getName());
        if (dest.exists() && dest.length() == src.length()) {
            src.delete();
            return true;
        }
        if (src.renameTo(dest)) {
            return true;
        }
        try {
            java.nio.file.Files.copy(
                    src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            src.delete();
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to migrate " + src + " -> " + dest, e);
            return false;
        }
    }

    private static void deleteEmptyLegacyTree(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteEmptyLegacyTree(child);
                }
            }
        }
        File[] remaining = dir.listFiles();
        if (remaining != null && remaining.length == 0) {
            if (!dir.delete()) {
                Log.d(TAG, "Could not delete empty legacy dir: " + dir);
            }
        }
    }

    public static File getPendingDirectory() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadsDir, FOLDER_NAME);
    }

    public static File getUploadedDirectory() {
        return new File(getPendingDirectory(), UPLOADED_SUBDIR);
    }
}
