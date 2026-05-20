package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileManagerActivity extends Activity {
    private static final String TAG = "FileManagerActivity";

    private TabHost tabHost;
    private ListView fileListView;
    private ListView uploadedListView;
    private TextView emptyTextView;
    private TextView emptyUploadedTextView;
    private Button refreshButton;
    private Button backButton;

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

        loadCsvFiles();
        loadUploadedFiles();
    }

    private void initializeViews() {
        tabHost = findViewById(R.id.tabHost);
        fileListView = findViewById(R.id.fileListView);
        uploadedListView = findViewById(R.id.uploadedListView);
        emptyTextView = findViewById(R.id.emptyTextView);
        emptyUploadedTextView = findViewById(R.id.emptyUploadedTextView);
        refreshButton = findViewById(R.id.refreshButton);
        backButton = findViewById(R.id.backButton);

        refreshButton.setOnClickListener(v -> refreshFileLists());
        backButton.setOnClickListener(v -> navigateToRecordActivity());
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

    private void refreshFileLists() {
        Log.d(TAG, "Refreshing file lists from MediaStore...");
        Toast.makeText(this, "Refreshing files...", Toast.LENGTH_SHORT).show();

        loadCsvFiles();
        loadUploadedFiles();

        Toast.makeText(this, "File lists refreshed", Toast.LENGTH_SHORT).show();
    }

    private void loadCsvFiles() {
        csvFiles.clear();

        List<OximeterDataFileIndex.FileEntry> entries = OximeterDataFileIndex.queryPendingFiles(this);
        Log.d(TAG, "Number of active CSV files found: " + entries.size());

        if (entries.isEmpty()) {
            emptyTextView.setText("No CSV files found in OximeterData folder");
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
            emptyUploadedTextView.setText("No uploaded CSV files found");
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
            fileListView.setVisibility(View.GONE);
            emptyTextView.setVisibility(View.VISIBLE);
        } else {
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
