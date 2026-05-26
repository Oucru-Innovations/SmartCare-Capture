package com.smartcare.oximetry.sample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
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

public class FileManagerActivity extends Activity {
    private static final String TAG = "FileManagerActivity";
    private static final int REQUEST_READ_STORAGE = 301;
    private static final int REQUEST_OPEN_DOCUMENT_TREE = 302;
    private static final String EXTERNAL_STORAGE_PROVIDER = "com.android.externalstorage.documents";

    private TabHost tabHost;
    private ListView fileListView;
    private ListView uploadedListView;
    private TextView emptyTextView;
    private TextView emptyUploadedTextView;
    private Button refreshButton;
    private Button backButton;
    private Button folderAccessButton;

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

    private final ArrayList<CsvFile> csvFiles = new ArrayList<>();
    private final ArrayList<CsvFile> uploadedFiles = new ArrayList<>();
    private CsvFileAdapter adapter;
    private UploadedFileAdapter uploadedAdapter;

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

        uploadedAdapter = new UploadedFileAdapter();
        uploadedListView.setAdapter(uploadedAdapter);

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
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshFileListsQuiet();
    }

    private void initializeViews() {
        tabHost = findViewById(R.id.tabHost);
        fileListView = findViewById(R.id.fileListView);
        uploadedListView = findViewById(R.id.uploadedListView);
        emptyTextView = findViewById(R.id.emptyTextView);
        emptyUploadedTextView = findViewById(R.id.emptyUploadedTextView);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);
        folderAccessButton = findViewById(R.id.folderAccessButton);

        refreshButton.setOnClickListener(v -> refreshFileLists());
        backButton.setOnClickListener(v -> navigateToRecordActivity());
        folderAccessButton.setText(R.string.file_manager_choose_folder_manual);
        folderAccessButton.setVisibility(View.GONE);
        folderAccessButton.setOnClickListener(v -> openOximeterDataFolderPicker());
        folderAccessButton.setOnLongClickListener(v -> {
            OximeterDataTreeAccess.clearPersistedTreeUri(this);
            autoSafDeclinedThisSession = false;
            Toast.makeText(this, "Saved folder access cleared", Toast.LENGTH_SHORT).show();
            refreshFileListsQuiet();
            return true;
        });
    }

    /**
     * SAF fallback when {@link OximeterDataFileIndex} disk + MediaStore return nothing (seen on
     * Samsung SM-T295 while MTP still shows files under {@code Download/OximeterData}).
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
            refreshFileListsQuiet();
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

        tabHost.setCurrentTab(0);
    }

    private void refreshFileListsQuiet() {
        Log.d(TAG, "Refreshing file lists (disk scan: SmartCareCsv*.csv, Annotation_*.json)...");
        loadCsvFiles();
        loadUploadedFiles();
        scheduleAutoFolderAccessIfStillEmpty();
    }

    private void refreshFileLists() {
        Toast.makeText(this, "Refreshing files...", Toast.LENGTH_SHORT).show();
        OximeterDataFileIndex.refreshMediaStoreDeep(this);
        loadCsvFiles();
        loadUploadedFiles();
        scheduleAutoFolderAccessIfStillEmpty();
        Toast.makeText(this, "File lists refreshed", Toast.LENGTH_SHORT).show();
    }

    private void loadCsvFiles() {
        csvFiles.clear();

        List<OximeterDataFileIndex.FileEntry> entries = OximeterDataFileIndex.queryPendingFiles(this);
        Log.d(TAG, "Number of active CSV files found: " + entries.size());

        if (entries.isEmpty()) {
            emptyTextView.setText("No files found. Expected SmartCareCsv*.csv or Annotation_*.json under Download/OximeterData (spelling: Oximeter). "
                    + getString(R.string.file_manager_grant_folder_hint));
            updateActiveFileList();
            return;
        }

        for (OximeterDataFileIndex.FileEntry entry : entries) {
            csvFiles.add(new CsvFile(entry));
        }

        updateActiveFileList();
    }

    private void loadUploadedFiles() {
        uploadedFiles.clear();

        List<OximeterDataFileIndex.FileEntry> entries = OximeterDataFileIndex.queryUploadedFiles(this);
        Log.d(TAG, "Number of uploaded CSV files found: " + entries.size());

        if (entries.isEmpty()) {
            emptyUploadedTextView.setText("No uploaded SmartCareCsv / Annotation files found");
            updateUploadedFileList();
            return;
        }

        for (OximeterDataFileIndex.FileEntry entry : entries) {
            uploadedFiles.add(new CsvFile(entry));
        }

        updateUploadedFileList();
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
            fileSizeText.setText(String.format("Size: %s", csvFile.getFormattedSize()));
            fileDateText.setText(String.format("Modified: %s", csvFile.getFormattedDate()));

            uploadButton.setOnClickListener(v -> {
                String uploadPath = csvFile.getUploadPath();
                PendingFileUploadCoordinator.uploadSessionFile(
                        FileManagerActivity.this, FileManagerActivity.this, uploadPath);
                Toast.makeText(FileManagerActivity.this,
                        "Upload started (up to 3 attempts if network available)",
                        Toast.LENGTH_SHORT).show();
                v.setPressed(true);
            });

            fileNameText.setOnClickListener(v -> {
                openCsvFile(csvFile);
                v.setPressed(true);
            });

            return convertView;
        }
    }

    private class UploadedFileAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return uploadedFiles.size();
        }

        @Override
        public CsvFile getItem(int position) {
            return uploadedFiles.get(position);
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
            TextView fileDateText = convertView.findViewById(R.id.uploadedFileDateText);
            ImageButton deleteButton = convertView.findViewById(R.id.deleteButton);

            fileNameText.setText(csvFile.getName());
            fileDateText.setText(String.format("Uploaded: %s", csvFile.getFormattedDate()));

            deleteButton.setOnClickListener(v -> {
                deleteUploadedFile(csvFile, position);
                v.setPressed(true);
            });

            fileNameText.setOnClickListener(v -> {
                openCsvFile(csvFile);
                v.setPressed(true);
            });

            return convertView;
        }
    }

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
            if ("content".equals(entry.uri.getScheme())) {
                int rows = activity.getContentResolver().delete(entry.uri, null, null);
                return rows > 0;
            }
            File file = new File(entry.uri.getPath());
            return file.delete();
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
