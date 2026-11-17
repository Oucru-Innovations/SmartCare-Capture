package com.smartcare.oximetry.sample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Màn hình quản lý file dạng tab: duyệt các file CSV và annotation đang chờ (pending) và đã upload
 * trong {@code Download/OximeterData}. Hỗ trợ upload thủ công, fallback bằng SAF khi không truy cập
 * được file trực tiếp, và lắng nghe MediaStore để tự cập nhật danh sách khi đang recording.
 */
public class FileManagerActivity extends Activity {
    private static final String TAG = "FileManagerActivity";
    private static final int REQUEST_READ_STORAGE = 301;
    private static final int REQUEST_OPEN_DOCUMENT_TREE = 302;
    private static final String EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents";

    private TabHost tabHost;
    private ListView fileListView;
    private ListView uploadedListView;
    private ListView trashListView;
    private TextView emptyTextView;
    private TextView emptyUploadedTextView;
    private TextView emptyTrashTextView;
    /** Dọn file hết hạn (Uploaded 15 ngày, Trash 30 ngày) một lần mỗi lần mở màn hình. */
    private boolean retentionPurgeDone;
    private Button refreshButton;
    private Button backButton;
    private Button folderAccessButton;
    private View loadingOverlay;
    private ProgressBar loadingProgressBar;

    /** Shown at most once per File Manager visit when list is empty (API 30+ all-files settings). */
    private boolean manageAllFilesAutoPromptShown;
    /** User tapped "Not now" on All files access; fall back to SAF. */
    private boolean manageAllFilesDeclinedThisSession;
    private boolean manageAllFilesDialogShowing;

    /** User chose "Not now" on the automatic folder-access explanation. */
    private boolean autoSafDeclinedThisSession;
    private boolean autoSafDialogShowing;
    private Runnable pendingAutoSafRunnable;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService fileQueryExecutor = Executors.newSingleThreadExecutor();
    private int refreshGeneration;
    private boolean refreshInProgress;
    private long lastRefreshAtMs;

    private final ArrayList<CsvFile> csvFiles = new ArrayList<>();
    private final ArrayList<CsvFile> uploadedFiles = new ArrayList<>();
    private final ArrayList<CsvFile> trashFiles = new ArrayList<>();
    private CsvFileAdapter adapter;
    private UploadedFileAdapter uploadedAdapter;
    private UploadedFileAdapter trashAdapter;

    /** Fires when the recording flow inserts/renames/deletes a MediaStore Downloads entry. */
    private ContentObserver downloadsObserver;
    private final Runnable observerRefreshRunnable = () ->
            refreshFileListsInternal(false, false, true);

    /**
     * Khởi tạo view, tab, adapter cho hai danh sách file và đảm bảo có quyền truy cập storage
     * trước khi nạp danh sách lần đầu.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        initializeViews();
        setupTabs();

        adapter = new CsvFileAdapter();
        fileListView.setAdapter(adapter);

        uploadedAdapter = new UploadedFileAdapter(uploadedFiles, false);
        uploadedListView.setAdapter(uploadedAdapter);

        trashAdapter = new UploadedFileAdapter(trashFiles, true);
        trashListView.setAdapter(trashAdapter);

        FileLogger.getInstance(this);
        FileLogger.logEvent("USER", "FILE_MANAGER_OPENED",
                MemoryDiagnostics.formatDeviceSummary(this) + " | FileManagerActivity onCreate", this);

        ensureStorageAccessThenLoad();
    }

    @Override
    protected void onDestroy() {
        if (pendingAutoSafRunnable != null) {
            mainHandler.removeCallbacks(pendingAutoSafRunnable);
            pendingAutoSafRunnable = null;
        }
        mainHandler.removeCallbacks(observerRefreshRunnable);
        unregisterDownloadsObserver();
        fileQueryExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Live-update the list while a recording is in progress: new/renamed CSV segments show
        // up without the user having to leave and re-enter or tap Refresh.
        registerDownloadsObserver();
        // Avoid running an expensive re-query immediately after onCreate().
        if (System.currentTimeMillis() - lastRefreshAtMs > 1200L) {
            refreshFileListsQuiet();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mainHandler.removeCallbacks(observerRefreshRunnable);
        unregisterDownloadsObserver();
    }

    private void registerDownloadsObserver() {
        if (downloadsObserver != null) {
            return;
        }
        downloadsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                // MediaStore can emit a burst of notifications; coalesce them into one refresh.
                mainHandler.removeCallbacks(observerRefreshRunnable);
                mainHandler.postDelayed(observerRefreshRunnable, 800L);
            }
        };
        try {
            getContentResolver().registerContentObserver(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, downloadsObserver);
        } catch (Exception e) {
            Log.w(TAG, "registerContentObserver failed", e);
            downloadsObserver = null;
        }
    }

    private void unregisterDownloadsObserver() {
        if (downloadsObserver == null) {
            return;
        }
        try {
            getContentResolver().unregisterContentObserver(downloadsObserver);
        } catch (Exception e) {
            Log.w(TAG, "unregisterContentObserver failed", e);
        }
        downloadsObserver = null;
    }

    private void initializeViews() {
        tabHost = findViewById(R.id.tabHost);
        fileListView = findViewById(R.id.fileListView);
        uploadedListView = findViewById(R.id.uploadedListView);
        emptyTextView = findViewById(R.id.emptyTextView);
        emptyUploadedTextView = findViewById(R.id.emptyUploadedTextView);
        trashListView = findViewById(R.id.trashListView);
        emptyTrashTextView = findViewById(R.id.emptyTrashTextView);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        folderAccessButton = findViewById(R.id.folderAccessButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);

        refreshButton.setOnClickListener(v -> refreshFileLists());
        backButton.setOnClickListener(v -> navigateToRecordActivity());
        folderAccessButton.setText(R.string.file_manager_choose_folder_manual);
        folderAccessButton.setVisibility(View.GONE);
        folderAccessButton.setOnClickListener(v -> openOximeterDataFolderPicker());
        folderAccessButton.setOnLongClickListener(v -> {
            OximeterDataTreeAccess.clearPersistedTreeUri(this);
            autoSafDeclinedThisSession = false;
            Toast.makeText(this, "Saved folder access cleared", Toast.LENGTH_SHORT).show();
            refreshFileListsInternal(false, false, true);
            return true;
        });
    }

    /**
     * SAF fallback when {@link OximeterDataFileIndex} disk + MediaStore return nothing (seen on
     * Samsung SM-T295 while MTP still shows files under {@code Download/OximeterData}).
     */
    /**
     * Mở System SAF picker tại {@code Download/OximeterData} để người dùng cấp quyền tree URI,
     * dùng làm phương án dự phòng khi truy cập file trực tiếp/MediaStore không trả về kết quả.
     */
    private void openOximeterDataFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Uri initial = DocumentsContract.buildDocumentUri(
                        EXTERNAL_STORAGE_PROVIDER, "primary:Download");
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial);
            } catch (Exception e) {
                Log.w(TAG, "Could not set EXTRA_INITIAL_URI", e);
            }
        }
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT_TREE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_DOCUMENT_TREE || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri treeUri = data.getData();
        if (treeUri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.e(TAG, "takePersistableUriPermission failed", e);
            Toast.makeText(this,
                    "Could not keep folder access. Open the picker again from the app.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        OximeterDataTreeAccess.persistTreeUri(this, treeUri);
        FileLogger.logEvent("FILES", "FILE_MANAGER_SAF_TREE", "uri=" + treeUri, this);
        autoSafDeclinedThisSession = false;
        Toast.makeText(this, "Folder access saved. Refreshing…", Toast.LENGTH_SHORT).show();
        refreshFileLists();
    }

    /**
     * On some devices, disk + MediaStore stay empty until the user grants a tree URI once.
     * We guide them automatically the first time the list is empty (no extra hunt for a button).
     */
    private void scheduleAutoFolderAccessIfStillEmpty() {
        if (!csvFiles.isEmpty()) {
            return;
        }
        if (OximeterDataTreeAccess.getPersistedTreeUri(this) != null) {
            return;
        }
        if (autoSafDeclinedThisSession || autoSafDialogShowing) {
            return;
        }
        if (pendingAutoSafRunnable != null) {
            mainHandler.removeCallbacks(pendingAutoSafRunnable);
            pendingAutoSafRunnable = null;
        }
        pendingAutoSafRunnable = () -> {
            pendingAutoSafRunnable = null;
            if (isFinishing()) {
                return;
            }
            if (!csvFiles.isEmpty() || OximeterDataTreeAccess.getPersistedTreeUri(this) != null) {
                return;
            }
            if (autoSafDeclinedThisSession || autoSafDialogShowing
                    || manageAllFilesDialogShowing) {
                return;
            }
            promptNextEmptyListAccessStep();
        };
        mainHandler.postDelayed(pendingAutoSafRunnable, 450);
    }

    /**
     * Prefer "All files access" (one Settings toggle on API 30+) so {@code File} paths under
     * {@code Download/OximeterData} work without SAF. Falls back to SAF if declined or unavailable.
     */
    private void promptNextEmptyListAccessStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            if (!manageAllFilesAutoPromptShown && !manageAllFilesDeclinedThisSession) {
                manageAllFilesAutoPromptShown = true;
                showManageAllFilesAccessRationaleDialog();
                return;
            }
        }
        showAutoFolderAccessRationaleDialog();
    }

    private void openManageAllFilesAccessSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION failed", e);
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + getPackageName()));
                startActivity(fallback);
            } catch (Exception e2) {
                Log.e(TAG, "Could not open app settings", e2);
                Toast.makeText(this, R.string.file_manager_manage_all_files_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showManageAllFilesAccessRationaleDialog() {
        manageAllFilesDialogShowing = true;
        FileLogger.logEvent("FILES", "FILE_MANAGER_MANAGE_ALL_FILES_PROMPT", "showing", this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_manager_manage_all_files_title)
                .setMessage(R.string.file_manager_manage_all_files_message)
                .setPositiveButton(R.string.file_manager_manage_all_files_open_settings, (d, w) -> {
                    manageAllFilesDialogShowing = false;
                    openManageAllFilesAccessSettings();
                })
                .setNegativeButton(R.string.file_manager_manage_all_files_not_now, (d, w) -> {
                    manageAllFilesDialogShowing = false;
                    manageAllFilesDeclinedThisSession = true;
                    promptNextEmptyListAccessStep();
                })
                .setOnCancelListener(d -> {
                    manageAllFilesDialogShowing = false;
                    manageAllFilesDeclinedThisSession = true;
                    promptNextEmptyListAccessStep();
                })
                .show();
    }

    private void showAutoFolderAccessRationaleDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return;
        }
        autoSafDialogShowing = true;
        FileLogger.logEvent("FILES", "FILE_MANAGER_SAF_AUTO_PROMPT", "showing", this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_manager_saf_rationale_title)
                .setMessage(R.string.file_manager_saf_rationale_message)
                .setPositiveButton(R.string.file_manager_saf_continue, (d, w) -> {
                    autoSafDialogShowing = false;
                    openOximeterDataFolderPicker();
                })
                .setNegativeButton(R.string.file_manager_saf_cancel, (d, w) -> {
                    autoSafDialogShowing = false;
                    autoSafDeclinedThisSession = true;
                    updateActiveFileList();
                })
                .setOnCancelListener(d -> {
                    autoSafDialogShowing = false;
                    autoSafDeclinedThisSession = true;
                    updateActiveFileList();
                })
                .show();
    }

    /**
     * Disk listing under {@code Download/OximeterData} needs {@link Manifest.permission#READ_EXTERNAL_STORAGE}
     * on API 29–32. MediaStore merge still runs without it so MTP-visible files can appear.
     */
    private void ensureStorageAccessThenLoad() {
        refreshFileListsQuiet();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_READ_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                OximeterDataFileIndex.refreshMediaStoreDeep(this);
            } else {
                Toast.makeText(this,
                        "Storage not granted: listing uses MediaStore only; tap Refresh after granting in Settings.",
                        Toast.LENGTH_LONG).show();
            }
            refreshFileListsInternal(false, false, true);
        }
    }

    private void setupTabs() {
        tabHost.setup();

        TabHost.TabSpec tabSpec1 = tabHost.newTabSpec("active");
        tabSpec1.setContent(R.id.tab1);
        tabSpec1.setIndicator("Pending Files");
        tabHost.addTab(tabSpec1);

        TabHost.TabSpec tabSpec2 = tabHost.newTabSpec("uploaded");
        tabSpec2.setContent(R.id.tab2);
        tabSpec2.setIndicator("Uploaded Files");
        tabHost.addTab(tabSpec2);

        TabHost.TabSpec tabSpec3 = tabHost.newTabSpec("trash");
        tabSpec3.setContent(R.id.tab3);
        tabSpec3.setIndicator("Trash");
        tabHost.addTab(tabSpec3);

        tabHost.setCurrentTab(0);
    }

    private void refreshFileListsQuiet() {
        refreshFileListsInternal(false, false, false);
    }

    /** Làm mới hai danh sách file có hiển thị toast (kèm ép quét lại MediaStore); dùng khi user bấm Refresh. */
    private void refreshFileLists() {
        refreshFileListsInternal(true, true, true);
    }

    private void refreshFileListsInternal(boolean forceMediaRefresh, boolean showToast,
                                          boolean bypassCache) {
        if (showToast) {
            Toast.makeText(this, "Refreshing files...", Toast.LENGTH_SHORT).show();
        }
        final int generation = ++refreshGeneration;
        if (refreshInProgress) {
            Log.d(TAG, "Refresh requested while in progress; scheduling latest generation=" + generation);
        }
        refreshInProgress = true;
        // Mark refresh start so onResume() right after onCreate does not queue a duplicate scan.
        lastRefreshAtMs = System.currentTimeMillis();
        setLoadingVisible(true);
        fileQueryExecutor.execute(() -> {
            try {
                if (!retentionPurgeDone) {
                    // Trước lần nạp đầu để không hiện file sắp bị xoá; purge tự quét và để lại
                    // cache mới nên query bên dưới không phải quét lại.
                    retentionPurgeDone = true;
                    try {
                        FileRetention.purgeExpired(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Retention purge failed", e);
                    }
                }
                if (forceMediaRefresh) {
                    OximeterDataFileIndex.invalidateListCache();
                    OximeterDataFileIndex.refreshMediaStoreDeep(this);
                } else if (bypassCache) {
                    OximeterDataFileIndex.invalidateListCache();
                }
                OximeterDataFileIndex.FileLists lists =
                        OximeterDataFileIndex.queryAllFiles(this, forceMediaRefresh || bypassCache);
                mainHandler.post(() -> applyLoadedEntries(
                        generation, lists.pending, lists.uploaded, lists.trash, showToast));
            } catch (Exception e) {
                Log.e(TAG, "Failed to refresh file lists", e);
                mainHandler.post(() -> {
                    if (generation == refreshGeneration) {
                        refreshInProgress = false;
                        setLoadingVisible(false);
                    }
                    if (showToast) {
                        Toast.makeText(this, "Refresh failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void setLoadingVisible(boolean visible) {
        if (loadingOverlay == null) {
            return;
        }
        loadingOverlay.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (loadingProgressBar != null) {
            loadingProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!visible);
        }
    }

    private void applyLoadedEntries(int generation,
                                    List<OximeterDataFileIndex.FileEntry> pendingEntries,
                                    List<OximeterDataFileIndex.FileEntry> uploadedEntries,
                                    List<OximeterDataFileIndex.FileEntry> trashEntries,
                                    boolean showToast) {
        if (generation != refreshGeneration || isFinishing()) {
            return;
        }
        refreshInProgress = false;
        lastRefreshAtMs = System.currentTimeMillis();
        setLoadingVisible(false);
        csvFiles.clear();
        uploadedFiles.clear();

        Log.d(TAG, "Number of active CSV files found: " + pendingEntries.size());
        for (OximeterDataFileIndex.FileEntry entry : pendingEntries) {
            csvFiles.add(new CsvFile(entry));
        }
        Log.d(TAG, "Number of uploaded CSV files found: " + uploadedEntries.size());
        for (OximeterDataFileIndex.FileEntry entry : uploadedEntries) {
            uploadedFiles.add(new CsvFile(entry));
        }
        trashFiles.clear();
        for (OximeterDataFileIndex.FileEntry entry : trashEntries) {
            trashFiles.add(new CsvFile(entry));
        }

        if (pendingEntries.isEmpty()) {
            emptyTextView.setText("No files found. Expected SmartCareCsv*.csv or Annotation_*.json under Download/OximeterData (spelling: Oximeter). "
                    + getString(R.string.file_manager_grant_folder_hint));
        }
        if (uploadedEntries.isEmpty()) {
            emptyUploadedTextView.setText("No uploaded SmartCareCsv / Annotation files found");
        }

        updateActiveFileList();
        updateUploadedFileList();
        updateTrashFileList();
        scheduleAutoFolderAccessIfStillEmpty();
        if (showToast) {
            Toast.makeText(this, "File lists refreshed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateActiveFileList() {
        adapter.notifyDataSetChanged();

        if (csvFiles.isEmpty()) {
            folderAccessButton.setVisibility(
                    autoSafDeclinedThisSession ? View.VISIBLE : View.GONE);
            fileListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
            folderAccessButton.setVisibility(View.GONE);
            fileListView.setVisibility(View.VISIBLE);
            emptyTextView.setVisibility(View.GONE);
        }

        fileListView.setOnItemClickListener((parent, view, position, id) ->
                openCsvFile(csvFiles.get(position)));
    }

    private void updateUploadedFileList() {
        uploadedAdapter.notifyDataSetChanged();

        if (uploadedFiles.isEmpty()) {
            uploadedListView.setVisibility(View.GONE);
            emptyUploadedTextView.setVisibility(View.VISIBLE);
        } else {
            uploadedListView.setVisibility(View.VISIBLE);
            emptyUploadedTextView.setVisibility(View.GONE);
        }

        uploadedListView.setOnItemClickListener((parent, view, position, id) ->
                openCsvFile(uploadedFiles.get(position)));
    }

    private void updateTrashFileList() {
        trashAdapter.notifyDataSetChanged();
        boolean empty = trashFiles.isEmpty();
        trashListView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyTrashTextView.setVisibility(empty ? View.VISIBLE : View.GONE);
        trashListView.setOnItemClickListener((parent, view, position, id) ->
                openCsvFile(trashFiles.get(position)));
    }

    /** Hỏi xác nhận rồi chuyển file pending vào Trash (tự xoá sau 30 ngày, khôi phục được). */
    private void confirmMoveToTrash(CsvFile csvFile) {
        new AlertDialog.Builder(this)
                .setTitle("Move to Trash")
                .setMessage("Move '" + csvFile.getName() + "' to Trash?\n\nYou can restore it from the "
                        + "Trash tab. Files in Trash are deleted automatically after "
                        + FileRetention.TRASH_RETENTION_DAYS + " days.")
                .setPositiveButton("Move", (dialog, which) -> {
                    String uri = csvFile.getUploadPath();
                    setLoadingVisible(true);
                    fileQueryExecutor.execute(() -> {
                        FileRetention.TrashResult result = FileRetention.moveToTrash(this, uri);
                        mainHandler.post(() -> {
                            if (isFinishing()) {
                                return;
                            }
                            String msg;
                            if (result == FileRetention.TrashResult.MOVED) {
                                msg = "Moved to Trash";
                            } else if (result == FileRetention.TrashResult.BUSY) {
                                msg = "File is being recorded or uploaded, try again later";
                            } else {
                                msg = "Failed to move file to Trash";
                            }
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            refreshFileListsInternal(false, false, true);
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /** Khôi phục file từ Trash về Pending; file này sẽ không tự upload, chỉ upload tay. */
    private void restoreFromTrash(CsvFile csvFile) {
        String uri = csvFile.getUploadPath();
        setLoadingVisible(true);
        fileQueryExecutor.execute(() -> {
            boolean ok = FileRetention.restoreFromTrash(this, uri);
            mainHandler.post(() -> {
                if (isFinishing()) {
                    return;
                }
                Toast.makeText(this,
                        ok ? "Restored to Pending (upload manually when ready)"
                                : "Failed to restore file",
                        Toast.LENGTH_SHORT).show();
                refreshFileListsInternal(false, false, true);
            });
        });
    }

    /** Hỏi xác nhận rồi xoá file đã upload khỏi thiết bị và cập nhật lại danh sách Uploaded. */
    private void deleteUploadedFile(CsvFile csvFile, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete '" + csvFile.getName() + "'?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    boolean deleted = csvFile.delete(this);
                    if (deleted) {
                        uploadedFiles.remove(position);
                        uploadedAdapter.notifyDataSetChanged();
                        updateUploadedFileList();
                        Toast.makeText(this, "File deleted successfully", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "File deleted: " + csvFile.getName());
                    } else {
                        Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to delete file: " + csvFile.getName());
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /** Mở file CSV bằng ứng dụng ngoài qua ACTION_VIEW với content URI (FileProvider) tạm cấp quyền đọc. */
    private void openCsvFile(CsvFile csvFile) {
        try {
            Uri fileUri = csvFile.getContentUri(this);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "text/csv");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening file: " + csvFile.getName(), e);
        }
    }

    private void navigateToRecordActivity() {
        Intent intent = new Intent(this, RecordActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Adapter cho tab "Pending": mỗi dòng hiển thị tên/kích thước/ngày file kèm nút upload thủ công. */
    private class CsvFileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return csvFiles.size();
        }

        @Override
        public CsvFile getItem(int position) {
            return csvFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.item_csv_file, parent, false);
            }

            CsvFile csvFile = getItem(position);

            TextView fileNameText = convertView.findViewById(R.id.fileNameText);
            TextView fileSizeText = convertView.findViewById(R.id.fileSizeText);
            TextView fileDateText = convertView.findViewById(R.id.fileDateText);
            ImageButton uploadButton = convertView.findViewById(R.id.uploadButton);

            fileNameText.setText(csvFile.getName());
            // fileSizeText.setText(String.format("Size: %s", csvFile.getFormattedSize()));
            bindFileEstimate(fileSizeText, csvFile);
            fileDateText.setText(String.format("Modified: %s", csvFile.getFormattedDate()));

            uploadButton.setOnClickListener(v -> {
                String uploadPath = csvFile.getUploadPath();
                PendingFileUploadCoordinator.EnqueueResult result =
                        PendingFileUploadCoordinator.uploadSessionFile(
                                FileManagerActivity.this, FileManagerActivity.this, uploadPath,
                                (success, message) -> {
                                    if (isFinishing()) {
                                        return;
                                    }
                                    refreshFileListsQuiet();
                                    if (!success) {
                                        Toast.makeText(FileManagerActivity.this,
                                                "Upload failed after 3 attempts",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                if (result == PendingFileUploadCoordinator.EnqueueResult.STARTED) {
                    Toast.makeText(FileManagerActivity.this,
                            "Upload started (up to 3 attempts if network available)",
                            Toast.LENGTH_SHORT).show();
                } else if (result == PendingFileUploadCoordinator.EnqueueResult.ALREADY_IN_FLIGHT) {
                    Toast.makeText(FileManagerActivity.this,
                            "Upload already in progress",
                            Toast.LENGTH_SHORT).show();
                }
                v.setPressed(true);
            });

            ImageButton trashButton = convertView.findViewById(R.id.trashButton);
            trashButton.setOnClickListener(v -> confirmMoveToTrash(csvFile));

            fileNameText.setOnClickListener(v -> {
                openCsvFile(csvFile);
                v.setPressed(true);
            });

            return convertView;
        }
    }

    /**
     * Adapter cho tab "Uploaded" (nút xoá) và tab "Trash" (chỉ nút khôi phục, không xoá được).
     * Cả hai hiển thị số ngày còn lại trước khi bị tự xoá.
     */
    private class UploadedFileAdapter extends BaseAdapter {
        private final List<CsvFile> files;
        private final boolean trash;

        UploadedFileAdapter(List<CsvFile> files, boolean trash) {
            this.files = files;
            this.trash = trash;
        }

        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public CsvFile getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.item_uploaded_file, parent, false);
            }

            CsvFile csvFile = getItem(position);

            TextView fileNameText = convertView.findViewById(R.id.uploadedFileNameText);
            TextView fileEstimateText = convertView.findViewById(R.id.uploadedFileEstimateText);
            TextView fileDateText = convertView.findViewById(R.id.uploadedFileDateText);
            ImageButton deleteButton = convertView.findViewById(R.id.deleteButton);

            fileNameText.setText(csvFile.getName());
            bindFileEstimate(fileEstimateText, csvFile);
            int days = trash ? FileRetention.TRASH_RETENTION_DAYS
                    : FileRetention.UPLOADED_RETENTION_DAYS;
            int left = FileRetention.daysLeft(
                    csvFile.getLastModifiedMs(), System.currentTimeMillis(), days);
            fileDateText.setText(String.format(Locale.US, "%s: %s · auto-delete in %d day(s)",
                    trash ? "Trashed" : "Uploaded", csvFile.getFormattedDate(), left));

            if (trash) {
                deleteButton.setImageResource(android.R.drawable.ic_menu_revert);
                deleteButton.setContentDescription("Restore file");
                deleteButton.setOnClickListener(v -> restoreFromTrash(csvFile));
            } else {
                deleteButton.setOnClickListener(v -> {
                    deleteUploadedFile(csvFile, position);
                    v.setPressed(true);
                });
            }

            fileNameText.setOnClickListener(v -> {
                openCsvFile(csvFile);
                v.setPressed(true);
            });

            return convertView;
        }
    }

    private void bindFileEstimate(TextView textView, CsvFile csvFile) {
        if (csvFile.isCsvFile()) {
            textView.setVisibility(View.VISIBLE);
            textView.setText(getString(R.string.file_manager_estimate,
                    csvFile.getFormattedDuration()));
        } else {
            textView.setVisibility(View.GONE);
        }
    }

    /**
     * Bọc một {@link OximeterDataFileIndex.FileEntry} để hiển thị trên UI: cung cấp tên, kích thước,
     * ngày đã định dạng, content URI và thao tác xoá, hỗ trợ cả nguồn content:// lẫn file path.
     */
    private static class CsvFile {
        private final OximeterDataFileIndex.FileEntry entry;

        CsvFile(OximeterDataFileIndex.FileEntry entry) {
            this.entry = entry;
        }

        String getName() {
            return entry.displayName;
        }

        String getUploadPath() {
            return entry.uri.toString();
        }

        Uri getContentUri(Activity activity) {
            if ("content".equals(entry.uri.getScheme())) {
                return entry.uri;
            }
            File file = new File(entry.uri.getPath());
            return FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    file);
        }

        boolean delete(Activity activity) {
            return FileUploader.deleteUri(activity, entry.uri);
        }

        long getLastModifiedMs() {
            return entry.lastModifiedMs;
        }

        boolean isCsvFile() {
            String name = entry.displayName;
            return name != null && name.toLowerCase(Locale.US).endsWith(".csv");
        }

        String getFormattedDuration() {
            long sizeBytes = entry.sizeBytes;
            if (sizeBytes <= 0) {
                return "--:--:--";
            }
            long totalSeconds = sizeBytes * 3600L / RecordingConstants.BYTES_PER_RECORDING_HOUR;
            long hours = totalSeconds / 3600;
            long minutes = (totalSeconds % 3600) / 60;
            long seconds = totalSeconds % 60;
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }

        String getFormattedSize() {
            long length = entry.sizeBytes;
            if (length < 1024) {
                return length + " B";
            }
            int exp = (int) (Math.log(length) / Math.log(1024));
            char pre = "KMGTPE".charAt(exp - 1);
            return String.format(Locale.US, "%.1f %sB", length / Math.pow(1024, exp), pre);
        }

        String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            long modified = entry.lastModifiedMs > 0 ? entry.lastModifiedMs : System.currentTimeMillis();
            return sdf.format(new Date(modified));
        }
    }

    @Override
    public void onBackPressed() {
        navigateToRecordActivity();
    }

    public String getOximeterDataPath() {
        return OximeterDataFileIndex.getPendingDirectory().getAbsolutePath();
    }
}
