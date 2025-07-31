package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ShowAnnotate {
    private static final String TAG = "ShowAnnotate";
    private Context context;
    private String patientCode;
    private static final Gson gson = new Gson();

    // Annotation model class
    public static class AnnotationEntry {
        String timestamp;
        String patientCode;
        String note;
        String heartRate;
        String spO2;

        public AnnotationEntry(String timestamp, String patientCode, String note,
                               String heartRate, String spO2) {
            this.timestamp = timestamp;
            this.patientCode = patientCode;
            this.note = note;
            this.heartRate = heartRate;
            this.spO2 = spO2;
        }

        // Getters
        public String getTimestamp() { return timestamp; }
        public String getPatientCode() { return patientCode; }
        public String getNote() { return note; }
        public String getHeartRate() { return heartRate; }
        public String getSpO2() { return spO2; }

        // Setter for note content
        public void setNote(String note) { this.note = note; }
    }

    public ShowAnnotate(Context context, String patientCode) {
        this.context = context;
        this.patientCode = patientCode;
    }

    public void showAnnotateMenu(View anchorView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Thêm ghi chú");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Nhập ghi chú tại đây");
        input.setMinLines(3);
        input.setMaxLines(5);
        builder.setView(input);

        builder.setPositiveButton("Lưu", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String noteContent = input.getText().toString();
                if (!noteContent.isEmpty()) {
                    handleAnnotation(noteContent);
                }
            }
        });

        builder.setNegativeButton("Hủy", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void handleAnnotation(String noteContent) {
        try {
            // Load existing annotations
            List<AnnotationEntry> annotations = loadAnnotations();

            // Create new annotation
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            AnnotationEntry newEntry = new AnnotationEntry(
                    timeStamp,
                    patientCode,
                    noteContent,
                    String.valueOf(RecordActivity.annotationHeartRate),
                    String.valueOf(RecordActivity.annotationSpO2)
            );

            // Add new annotation
            annotations.add(newEntry);

            // Save to file
            saveAnnotations(annotations);

            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving annotation", e);
            Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show();
        }
    }

    // Load annotations from JSON file
    private List<AnnotationEntry> loadAnnotations() throws IOException {
        File jsonFile = getAnnotationFile();

        if (!jsonFile.exists()) {
            return new ArrayList<>();
        }

        try (FileReader reader = new FileReader(jsonFile)) {
            Type listType = new TypeToken<ArrayList<AnnotationEntry>>(){}.getType();
            List<AnnotationEntry> annotations = gson.fromJson(reader, listType);
            return annotations != null ? annotations : new ArrayList<>();
        } catch (IOException e) {
            Log.e(TAG, "Error reading annotations", e);
            return new ArrayList<>();
        }
    }

    // Save annotations to JSON file
    private void saveAnnotations(List<AnnotationEntry> annotations) throws IOException {
        File jsonFile = getAnnotationFile();

        // Create directory if it doesn't exist
        if (!jsonFile.getParentFile().exists()) {
            boolean created = jsonFile.getParentFile().mkdirs();
            if (!created) {
                throw new IOException("Failed to create annotation directory");
            }
        }

        try (FileWriter writer = new FileWriter(jsonFile)) {
            gson.toJson(annotations, writer);
        }
    }

    // Get JSON file for annotations
    private File getAnnotationFile() {
        File directory = getAnnotationDirectory();
        return new File(directory, "Annotations.json");
    }

    // Helper method to get annotation directory
    private File getAnnotationDirectory() {
        File externalDir = context.getExternalFilesDir(null);
        File oximeterDataDir = new File(externalDir, "OximeterData");
        return new File(oximeterDataDir, "Annotation");
    }

    // Update an existing annotation
    public void updateAnnotation(int index, String newContent) {
        try {
            List<AnnotationEntry> annotations = loadAnnotations();

            if (index >= 0 && index < annotations.size()) {
                annotations.get(index).setNote(newContent);
                saveAnnotations(annotations);
                Toast.makeText(context, "Note updated", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error updating annotation", e);
            Toast.makeText(context, "Failed to update note", Toast.LENGTH_SHORT).show();
        }
    }

    // Delete an annotation
    public void deleteAnnotation(int index) {
        try {
            List<AnnotationEntry> annotations = loadAnnotations();

            if (index >= 0 && index < annotations.size()) {
                annotations.remove(index);
                saveAnnotations(annotations);
                Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error deleting annotation", e);
            Toast.makeText(context, "Failed to delete note", Toast.LENGTH_SHORT).show();
        }
    }

    // Legacy methods for compatibility - these can be simplified since JSON doesn't need open writers
    public static void startAnnotationSession(String patientCode, Context context) {
        // Nothing needed for JSON implementation
    }

    public static void stopAnnotationSession(String patientCode) {
        // Nothing needed for JSON implementation
    }

    public static void stopAllSessions() {
        // Nothing needed for JSON implementation
    }

    public void showAnnotationHistory(View view) {
        AnnotationHistory.showAnnotationHistory(context, patientCode);
    }
}