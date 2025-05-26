package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.opencsv.CSVReader;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AnnotationHistory {
    private static final String TAG = "AnnotationHistory";

    public static void showAnnotationHistory(Context context, String patientCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Lịch sử ghi chú");

        // Inflate a layout for displaying annotations in a table
        View view = LayoutInflater.from(context).inflate(R.layout.annotation_history_layout, null);
        TableLayout tableLayout = view.findViewById(R.id.annotationTable);

        // Load annotations from CSV file
        List<AnnotationEntry> annotations = loadAnnotations(patientCode);

        if (annotations.isEmpty()) {
            TextView emptyText = new TextView(context);
            emptyText.setText("Không có ghi chú nào cho bệnh nhân này");
            emptyText.setPadding(20, 20, 20, 20);
            builder.setView(emptyText);
        } else {
            // Sort annotations by timestamp (newest first)
            Collections.sort(annotations, new Comparator<AnnotationEntry>() {
                @Override
                public int compare(AnnotationEntry o1, AnnotationEntry o2) {
                    return o2.timestamp.compareTo(o1.timestamp);
                }
            });

            // Populate the table with annotations
            for (AnnotationEntry entry : annotations) {
                TableRow row = new TableRow(context);

                TextView timestampView = new TextView(context);
                timestampView.setText(entry.timestamp);
                timestampView.setPadding(10, 5, 10, 5);
                row.addView(timestampView);

                TextView noteView = new TextView(context);
                noteView.setText(entry.note);
                noteView.setPadding(10, 5, 10, 5);
                row.addView(noteView);

                TextView patientcodeView = new TextView(context);
                patientcodeView.setText(entry.patientCode);
                patientcodeView.setPadding(10, 5, 10, 5);
                row.addView(patientcodeView);

                tableLayout.addView(row);
            }

            builder.setView(view);
        }

        builder.setPositiveButton("Đóng", null);
        builder.show();
    }

    private static List<AnnotationEntry> loadAnnotations(String patientCode) {
        List<AnnotationEntry> annotations = new ArrayList<>();

        try {
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "OximeterData/Annotation");

            if (!directory.exists()) {
                return annotations;
            }

            // Find annotation file(s)
            File file = new File(directory, "Annotation.csv");
            if (!file.exists()) {
                return annotations;
            }

            CSVReader reader = new CSVReader(new FileReader(file));
            String[] line;
            boolean headerSkipped = false;

            while ((line = reader.readNext()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                if (line.length >= 3) {
                    String timestamp = line[0];
                    String filePatientCode = line[1];
                    String note = line[2];

                    // Filter by patient code if specified
                    if (patientCode == null || patientCode.isEmpty() || patientCode.equals(filePatientCode)) {
                        annotations.add(new AnnotationEntry(timestamp, filePatientCode, note));
                    }
                }
            }
            reader.close();

        } catch (Exception e) {
            Log.e(TAG, "Error reading annotation file", e);
        }

        return annotations;
    }

    private static class AnnotationEntry {
        String timestamp;
        String patientCode;
        String note;

        AnnotationEntry(String timestamp, String patientCode, String note) {
            this.timestamp = timestamp;
            this.patientCode = patientCode;
            this.note = note;
        }
    }
}