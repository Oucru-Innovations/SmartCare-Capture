package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
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
    private ListView fileListView;
    private TextView emptyTextView;
    private ArrayList<CsvFile> csvFiles = new ArrayList<>();
    private CsvFileAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        // Initialize views
        fileListView = findViewById(R.id.fileListView);
        emptyTextView = findViewById(R.id.emptyTextView);
        findViewById(R.id.backButton).setOnClickListener(v -> navigateToRecordActivity());

        // Set up custom adapter
        adapter = new CsvFileAdapter();
        fileListView.setAdapter(adapter);

        // Load and display CSV files
        loadCsvFiles();
    }

    private void loadCsvFiles() {
        // Clear previous files
        csvFiles.clear();

        // Create the OximeterData directory in Downloads
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "OximeterData");

        // Check if directory exists
        if (!baseDir.exists()) {
            emptyTextView.setText("No OximeterData folder found");
            updateFileList();
            return;
        }

        // Get CSV files
        File[] files = baseDir.listFiles(file ->
                file.isFile() && file.getName().toLowerCase().endsWith(".csv")
        );

        // Check if any CSV files exist
        if (files == null || files.length == 0) {
            emptyTextView.setText("No CSV files found in OximeterData folder");
            updateFileList();
            return;
        }

        // Sort files by last modified date (most recent first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());

        // Create CsvFile objects
        for (File file : files) {
            csvFiles.add(new CsvFile(file));
        }

        // Update the UI
        updateFileList();
    }

    private void updateFileList() {
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
        }
    }

    private void navigateToRecordActivity() {
        Intent intent = new Intent(this, RecordActivity.class);
        startActivity(intent);
        finish();
    }

    // Custom adapter for CSV files
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

            // Set data
            fileNameText.setText(csvFile.getName());
            fileSizeText.setText(String.format("Size: %s", csvFile.getFormattedSize()));
            fileDateText.setText(String.format("Modified: %s", csvFile.getFormattedDate()));

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
}