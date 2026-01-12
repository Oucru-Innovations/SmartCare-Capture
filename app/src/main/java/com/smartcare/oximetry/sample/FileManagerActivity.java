package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class FileManagerActivity extends Activity {
    private static final String FOLDER_NAME = "OximeterData";
    private static final String TAG = "FileManagerActivity";

    private TabHost tabHost;
    private ListView fileListView;
    private ListView uploadedListView;
    private TextView emptyTextView;
    private TextView emptyUploadedTextView;
    private Button refreshButton;
    private Button backButton;

    private ArrayList<CsvFile> csvFiles = new ArrayList<>();
    private ArrayList<CsvFile> uploadedFiles = new ArrayList<>();
    private CsvFileAdapter adapter;
    private UploadedFileAdapter uploadedAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        // Hide the action bar
        if (getActionBar() != null) {
            getActionBar().hide();
        }

        // Initialize views and tabs
        initializeViews();
        setupTabs();

        // Set up custom adapters
        adapter = new CsvFileAdapter();
        fileListView.setAdapter(adapter);

        uploadedAdapter = new UploadedFileAdapter();
        uploadedListView.setAdapter(uploadedAdapter);

        // Load and display CSV files
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

        // Set up click listeners
        refreshButton.setOnClickListener(v -> refreshFileLists());
        backButton.setOnClickListener(v -> navigateToRecordActivity());
    }

    private void setupTabs() {
        tabHost.setup();

        // Tab 1: Active Files
        TabHost.TabSpec tabSpec1 = tabHost.newTabSpec("active");
        tabSpec1.setContent(R.id.tab1);
        tabSpec1.setIndicator("Pending Files");
        tabHost.addTab(tabSpec1);

        // Tab 2: Uploaded Files
        TabHost.TabSpec tabSpec2 = tabHost.newTabSpec("uploaded");
        tabSpec2.setContent(R.id.tab2);
        tabSpec2.setIndicator("Uploaded Files");
        tabHost.addTab(tabSpec2);

        // Set default tab
        tabHost.setCurrentTab(0);
    }

    private void refreshFileLists() {
        Log.d(TAG, "Refreshing file lists...");
        Toast.makeText(this, "Refreshing files...", Toast.LENGTH_SHORT).show();

        // Reload both file lists
        loadCsvFiles();
        loadUploadedFiles();

        // Show confirmation message
        Toast.makeText(this, "File lists refreshed", Toast.LENGTH_SHORT).show();
    }

    private void loadCsvFiles() {
        // Clear previous files
        csvFiles.clear();

        // Get the app's private external storage directory
        File baseDir = getOximeterDataDirectory();

        // Check if directory exists
        if (!baseDir.exists()) {
            emptyTextView.setText("No OximeterData folder found");
            updateActiveFileList();
            return;
        }

        // Get CSV files from the main directory
        File[] files = baseDir.listFiles(file ->
                (file.isFile() && file.getName().toLowerCase().endsWith(".csv") ) || (file.isFile() && file.getName().toLowerCase().endsWith(".json"))
        );

        // Log the number of files found
        Log.d(TAG, "Number of active CSV files found: " + (files != null ? files.length : 0));

        // Check if any CSV files exist
        if (files == null || files.length == 0) {
            emptyTextView.setText("No CSV files found in OximeterData folder");
            updateActiveFileList();
            return;
        }

        // Sort files by last modified date (most recent first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        // Create CsvFile objects
        for (File file : files) {
            csvFiles.add(new CsvFile(file));
        }

        // Update the UI
        updateActiveFileList();
    }

    private void loadUploadedFiles() {
        // Clear previous files
        uploadedFiles.clear();

        // Get the uploaded directory
        File uploadedDir = getUploadedDirectory();

        // Check if directory exists
        if (!uploadedDir.exists()) {
            emptyUploadedTextView.setText("No uploaded files folder found");
            updateUploadedFileList();
            return;
        }

        // Get CSV files from the uploaded directory
        File[] files = uploadedDir.listFiles(file ->
                (file.isFile() && file.getName().toLowerCase().endsWith(".csv") ) || (file.isFile() && file.getName().toLowerCase().endsWith(".json"))
        );

        // Log the number of files found
        Log.d(TAG, "Number of uploaded CSV files found: " + (files != null ? files.length : 0));

        // Check if any CSV files exist
        if (files == null || files.length == 0) {
            emptyUploadedTextView.setText("No uploaded CSV files found");
            updateUploadedFileList();
            return;
        }

        // Sort files by last modified date (most recent first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        // Create CsvFile objects
        for (File file : files) {
            uploadedFiles.add(new CsvFile(file));
        }

        // Update the UI
        updateUploadedFileList();
    }

    private File getOximeterDataDirectory() {
        // Use app's private external storage directory
        File externalDir = getExternalFilesDir(null);
        return new File(externalDir, FOLDER_NAME);
    }

    private File getUploadedDirectory() {
        return new File(getOximeterDataDirectory(), "Uploaded");
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

        // Set click listener for file items
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            CsvFile csvFile = csvFiles.get(position);
            openCsvFile(csvFile.getFile());
        });
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

        // Set click listener for uploaded file items
        uploadedListView.setOnItemClickListener((parent, view, position, id) -> {
            CsvFile csvFile = uploadedFiles.get(position);
            openCsvFile(csvFile.getFile());
        });
    }

    private void deleteUploadedFile(CsvFile csvFile, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete '" + csvFile.getName() + "'?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (csvFile.getFile().delete()) {
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

    private void openCsvFile(File file) {
        try {
            // Use FileProvider to generate a content URI
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            // Create an intent to view the file
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "text/csv");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open file: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening file: " + file.getAbsolutePath(), e);
        }
    }

    private void navigateToRecordActivity() {
        Intent intent = new Intent(this, RecordActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    // Custom adapter for active CSV files
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
            // Inflate the custom layout
            if (convertView == null) {
                convertView = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.item_csv_file, parent, false);
            }

            // Get the current CSV file
            CsvFile csvFile = getItem(position);

            // Find views
            TextView fileNameText = convertView.findViewById(R.id.fileNameText);
            TextView fileSizeText = convertView.findViewById(R.id.fileSizeText);
            TextView fileDateText = convertView.findViewById(R.id.fileDateText);
            ImageView fileIcon = convertView.findViewById(R.id.fileIcon);
            ImageButton uploadButton = convertView.findViewById(R.id.uploadButton);

            // Set data
            fileNameText.setText(csvFile.getName());
            fileSizeText.setText(String.format("Size: %s", csvFile.getFormattedSize()));
            fileDateText.setText(String.format("Modified: %s", csvFile.getFormattedDate()));

            // Set click listener for upload button
            uploadButton.setOnClickListener(v -> {
                File fileToUpload = csvFile.getFile();
                String filePath = fileToUpload.getAbsolutePath();
                FileUploader.uploadFile(FileManagerActivity.this, filePath, new FileUploader.UploadCallback() {
                    @Override
                    public void onUploadComplete(boolean success, String message) {
                        // Run on UI thread since we're updating UI
                        runOnUiThread(() -> {
                            if (success) {
                                // Move file to Uploaded folder after successful upload
                                // Check if filePath is a content URI or regular file path
                                boolean moved;
                                if (filePath.startsWith("content://")) {
                                    moved = FileUploader.moveFileToUploadedFolderFromUri(FileManagerActivity.this, filePath);
                                } else {
                                    moved = FileUploader.moveFileToUploadedFolder(fileToUpload);
                                }
                                if (moved) {
                                    Log.d(TAG, "File moved to Uploaded folder: " + fileToUpload.getName());
                                } else {
                                    Log.e(TAG, "Failed to move file to Uploaded folder: " + fileToUpload.getName());
                                }
                                // Refresh both file lists to reflect the changes
                                loadCsvFiles();
                                loadUploadedFiles();
                                Toast.makeText(
                                        FileManagerActivity.this,
                                        "File uploaded successfully",
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                // If upload failed, file stays in place
                                Toast.makeText(FileManagerActivity.this,
                                        "Upload failed: " + message,
                                        Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Upload failed: " + message);
                            }
                        });
                    }
                });
                // Prevent click event from propagating to the ListView item
                v.setPressed(true);
            });

            // Set click listener for open file icon
            fileNameText.setOnClickListener(v -> {
                // Open file
                openCsvFile(csvFile.getFile());
                // Prevent click event from propagating to the ListView item
                v.setPressed(true);
            });

            return convertView;
        }
    }

    // Custom adapter for uploaded CSV files
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
            // Inflate the custom layout for uploaded files
            if (convertView == null) {
                convertView = LayoutInflater.from(FileManagerActivity.this)
                        .inflate(R.layout.item_uploaded_file, parent, false);
            }

            // Get the current CSV file
            CsvFile csvFile = getItem(position);

            // Find views
            TextView fileNameText = convertView.findViewById(R.id.uploadedFileNameText);
            TextView fileDateText = convertView.findViewById(R.id.uploadedFileDateText);
            ImageButton deleteButton = convertView.findViewById(R.id.deleteButton);

            // Set data
            fileNameText.setText(csvFile.getName());
            fileDateText.setText(String.format("Uploaded: %s", csvFile.getFormattedDate()));

            // Set click listener for delete button
            deleteButton.setOnClickListener(v -> {
                deleteUploadedFile(csvFile, position);
                // Prevent click event from propagating to the ListView item
                v.setPressed(true);
            });

            // Set click listener for file name to open file
            fileNameText.setOnClickListener(v -> {
                openCsvFile(csvFile.getFile());
                v.setPressed(true);
            });

            return convertView;
        }
    }

    // Data class to hold file information
    private static class CsvFile {
        private final File file;

        public CsvFile(File file) {
            this.file = file;
        }

        public String getName() {
            return file.getName();
        }

        public File getFile() {
            return file;
        }

        public String getFormattedSize() {
            long length = file.length();
            if (length < 1024) return length + " B";
            int exp = (int) (Math.log(length) / Math.log(1024));
            char pre = "KMGTPE".charAt(exp-1);
            return String.format("%.1f %sB", length / Math.pow(1024, exp), pre);
        }

        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(file.lastModified()));
        }
    }

    @Override
    public void onBackPressed() {
        navigateToRecordActivity();
    }

    // Helper method to get the directory path (useful for debugging)
    public String getOximeterDataPath() {
        return getOximeterDataDirectory().getAbsolutePath();
    }
}