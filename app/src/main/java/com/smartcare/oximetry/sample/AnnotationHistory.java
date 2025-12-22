package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AnnotationHistory {
    private static final String TAG = "AnnotationHistory";
    private static final Gson gson = new Gson();

    public static void showAnnotationHistory(Context context, String patientCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Annotation");

        // Inflate a layout for displaying annotations in a table
        View view = LayoutInflater.from(context).inflate(R.layout.annotation_history_layout, null);
        TableLayout tableLayout = view.findViewById(R.id.annotationTable);

        // Load annotations from JSON file
        final List<ShowAnnotate.AnnotationEntry> annotations = loadAnnotations(context);

        if (annotations.isEmpty()) {
            TextView emptyText = new TextView(context);
            emptyText.setText("No notes for this patient");
            emptyText.setPadding(20, 20, 20, 20);
            builder.setView(emptyText);
        } else {
            // Sort annotations by timestamp (newest first)
            Collections.sort(annotations, new Comparator<ShowAnnotate.AnnotationEntry>() {
                @Override
                public int compare(ShowAnnotate.AnnotationEntry o1, ShowAnnotate.AnnotationEntry o2) {
                    return o2.getTimestamp().compareTo(o1.getTimestamp());
                }
            });

            // Filter by patient code
            List<ShowAnnotate.AnnotationEntry> filteredAnnotations = new ArrayList<>();
            for (ShowAnnotate.AnnotationEntry entry : annotations) {
                if (entry.getPatientCode().equals(patientCode)) {
                    filteredAnnotations.add(entry);
                }
            }

            if (filteredAnnotations.isEmpty()) {
                TextView emptyText = new TextView(context);
                emptyText.setText("No notes for this patient");
                emptyText.setPadding(20, 20, 20, 20);
                builder.setView(emptyText);
            } else {
                // Populate the table with annotations
                for (int i = 0; i < filteredAnnotations.size(); i++) {
                    final ShowAnnotate.AnnotationEntry entry = filteredAnnotations.get(i);
                    final int position = i;

                    TableRow row = new TableRow(context);
                    row.setClickable(true);

                    TextView timestampView = new TextView(context);
                    timestampView.setText(entry.getTimestamp());
                    timestampView.setPadding(10, 5, 10, 5);
                    row.addView(timestampView);

                    TextView noteView = new TextView(context);
                    noteView.setText(entry.getNote());
                    noteView.setPadding(10, 5, 10, 5);
                    row.addView(noteView);

                    // Remove patient code column from display

                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showAnnotationOptions(context, patientCode, entry);
                        }
                    });

                    tableLayout.addView(row);
                }

                builder.setView(view);
            }
        }

        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private static List<ShowAnnotate.AnnotationEntry> loadAnnotations(Context context) {
        try {
            File jsonFile = getAnnotationFile(context);

            if (!jsonFile.exists()) {
                return new ArrayList<>();
            }

            FileReader reader = new FileReader(jsonFile);
            Type listType = new TypeToken<ArrayList<ShowAnnotate.AnnotationEntry>>(){}.getType();
            List<ShowAnnotate.AnnotationEntry> annotations = gson.fromJson(reader, listType);
            reader.close();

            return annotations != null ? annotations : new ArrayList<>();
        } catch (IOException e) {
            Log.e(TAG, "Error reading annotations", e);
            return new ArrayList<>();
        }
    }

    private static void showAnnotationOptions(final Context context, final String patientCode,
                                              final ShowAnnotate.AnnotationEntry entry) {
        String[] options = {"Edit", "Delete", "Cancel"};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Options");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Edit
                    showEditDialog(context, patientCode, entry);
                    break;
                case 1: // Delete
                    showDeleteConfirmation(context, patientCode, entry);
                    break;
            }
        });
        builder.show();
    }

    private static void showEditDialog(final Context context, final String patientCode,
                                       final ShowAnnotate.AnnotationEntry entry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Note");

        final EditText input = new EditText(context);
        input.setText(entry.getNote());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(5);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newNote = input.getText().toString();
            if (!newNote.isEmpty()) {
                ShowAnnotate annotate = new ShowAnnotate(context, patientCode);
                annotate.updateAnnotationByEntry(entry, newNote);
                // Refresh the dialog
                showAnnotationHistory(context, patientCode);
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static void showDeleteConfirmation(final Context context, final String patientCode, final ShowAnnotate.AnnotationEntry entry) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Delete Note");
        builder.setMessage("Are you sure you want to delete this note?");

        builder.setPositiveButton("Delete", (dialog, which) -> {
            ShowAnnotate annotate = new ShowAnnotate(context, patientCode);
            annotate.deleteAnnotation(entry);
            // Refresh the dialog
            showAnnotationHistory(context, patientCode);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static File getAnnotationFile(Context context) {
        File externalDir = context.getExternalFilesDir(null);
        File oximeterDataDir = new File(externalDir, "OximeterData");
        File annotationDir = new File(oximeterDataDir, "Annotation");
        return new File(annotationDir, "Annotations.json");
    }
}