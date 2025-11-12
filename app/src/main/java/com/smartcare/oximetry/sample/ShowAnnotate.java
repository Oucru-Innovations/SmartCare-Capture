package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import java.util.Calendar;
import java.util.Calendar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        builder.setTitle("Add Note");

        // Inflate custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.annotation_dialog, null);
        builder.setView(dialogView);

        // Get references to UI components
        final EditText recordingTimeEdit = dialogView.findViewById(R.id.edit_recording_time);
        final RadioGroup isCollectingRadioGroup = dialogView.findViewById(R.id.radio_is_collecting);
        final RadioButton yesRadioButton = dialogView.findViewById(R.id.radio_yes);
        final RadioButton noRadioButton = dialogView.findViewById(R.id.radio_no);
        final LinearLayout notCollectingLayout = dialogView.findViewById(R.id.layout_not_collecting_reason);
        final LinearLayout invalidDataLayout = dialogView.findViewById(R.id.layout_invalid_data_reason);
        final EditText notCollectingReasonEdit = dialogView.findViewById(R.id.edit_not_collecting_reason);
        final EditText invalidDataReasonEdit = dialogView.findViewById(R.id.edit_invalid_data_reason);
        final RadioGroup dataTypeRadioGroup = dialogView.findViewById(R.id.radio_data_type);
        final RadioButton ppgRadioButton = dialogView.findViewById(R.id.radio_ppg);
        final RadioButton ecgRadioButton = dialogView.findViewById(R.id.radio_ecg);

        // Set date format hint
        recordingTimeEdit.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date()));
        recordingTimeEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});

        // Add TextWatcher for auto-formatting date
        recordingTimeEdit.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isFormatting) return;

                isFormatting = true;
                String input = s.toString().replaceAll("[^\\d]", ""); // Remove all non-digits

                if (input.length() <= 8) {
                    StringBuilder formatted = new StringBuilder();
                    for (int i = 0; i < input.length(); i++) {
                        if (i == 2 || i == 4) {
                            formatted.append("/");
                        }
                        formatted.append(input.charAt(i));
                    }

                    s.replace(0, s.length(), formatted.toString());

                    // Set cursor position
                    int cursorPos = formatted.length();
                    if (cursorPos <= recordingTimeEdit.getText().length()) {
                        recordingTimeEdit.setSelection(cursorPos);
                    }
                }
                isFormatting = false;
            }
        });
        // Set up radio button listeners for conditional fields
        isCollectingRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_yes) {
                invalidDataLayout.setVisibility(View.VISIBLE);
                notCollectingLayout.setVisibility(View.GONE);
            } else {
                invalidDataLayout.setVisibility(View.GONE);
                notCollectingLayout.setVisibility(View.VISIBLE);
            }
        });

        // Default selection
        yesRadioButton.setChecked(true);
        invalidDataLayout.setVisibility(View.VISIBLE);

        builder.setPositiveButton("Save", null); // We'll override this below
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override the "Save" button to handle validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String recordingTime = recordingTimeEdit.getText().toString().trim();
            boolean isCollectedAsScheduled = yesRadioButton.isChecked();
            boolean isPpgSelected = ppgRadioButton.isChecked();
            String dataType = isPpgSelected ? "PPG" : "ECG";

            // Validate recording time format
            if (!isValidDateFormat(recordingTime)) {
                Toast.makeText(context, "Please enter a valid date (DD/MM/YYYY)", Toast.LENGTH_SHORT).show();
                return;
            }

            String reason = "";
            if (isCollectedAsScheduled) {
                reason = invalidDataReasonEdit.getText().toString().trim();
            } else {
                reason = notCollectingReasonEdit.getText().toString().trim();
                if (reason.isEmpty()) {
                    Toast.makeText(context, "Please provide a reason for not collecting data", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // All validation passed, handle the annotation
            handleAnnotation(recordingTime, isCollectedAsScheduled, dataType, reason);
            dialog.dismiss();
        });
    }


    // Helper method to validate date format
    private boolean isValidDateFormat(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            sdf.setLenient(false);
            sdf.parse(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Handle the annotation logic
    private void handleAnnotation(String recordingTime, boolean isCollectedAsScheduled, String dataType, String reason) {
        try {
            // Load existing annotations
            List<AnnotationEntry> annotations = loadAnnotations();

            // Filter notes for this patient
            List<AnnotationEntry> patientNotes = new ArrayList<>();
            for (AnnotationEntry entry : annotations) {
                if (entry.getPatientCode().equals(patientCode)) {
                    patientNotes.add(entry);
                }
            }

            // Remove oldest if there are already 5 notes
            if (patientNotes.size() >= 5) {
                AnnotationEntry oldest = Collections.min(patientNotes, Comparator.comparing(AnnotationEntry::getTimestamp));
                annotations.remove(oldest);
            }

            // Create note content from the fields
            String noteContent = (isCollectedAsScheduled ? "Reason for Invalid Data: " : "Reason for Not Collecting: ") + reason;

            // Create new annotation
            String timeStamp = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US).format(new Date());
            AnnotationEntry newEntry = new AnnotationEntry(
                    timeStamp,
                    patientCode,
                    noteContent,
                    "",
                    ""
            );

            // Add new annotation
            annotations.add(newEntry);
            // Save to file
            saveAnnotations(annotations);

            // Create single note JSON file for server
            File oximeterDataDir = context.getExternalFilesDir(null);
            File mainFolder = new File(oximeterDataDir, "OximeterData");
            if (!mainFolder.exists()) {
                mainFolder.mkdirs();
            }

            String singleFileName = "Annotation_" + patientCode + "_" + timeStamp.replace(" ", ".")
                    .replace(":", ".").replace("-", ".") + "_.json";
            File singleNoteFile = new File(mainFolder, singleFileName);

            // Create JSON with specific format for server
            Map<String, Object> serverNote = new HashMap<>();

            //parse recording time to ISO format
            SimpleDateFormat inputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
//            outputFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT+07:00"));
            outputFormat.setTimeZone(java.util.TimeZone.getDefault());
            try {
                Date date = inputFormat.parse(recordingTime);
                // Set time to current time or default time (12:00:00)
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.set(Calendar.HOUR_OF_DAY, 12);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                String formattedRecordingTime = outputFormat.format(cal.getTime());
                serverNote.put("recording_time", formattedRecordingTime);
            } catch (Exception e) {
                Log.e(TAG, "Error formatting recording time", e);
                serverNote.put("recording_time", recordingTime); // fallback to original
            }
//            serverNote.put("recording_time", recordingTime);
            serverNote.put("data_type", dataType);
            serverNote.put("research_code", patientCode);
            serverNote.put("is_collected_as_scheduled", isCollectedAsScheduled);

            // Add the appropriate reason field
            if (isCollectedAsScheduled) {
                serverNote.put("reason_not_collected_as_scheduled", null);

                // Create reason_invalid_data array with one item
                List<Map<String, Object>> invalidDataList = new ArrayList<>();
                Map<String, Object> invalidDataItem = new HashMap<>();
                serverNote.put("reason_not_collected_as_scheduled", "");
                invalidDataItem.put("start_time", "");
                invalidDataItem.put("end_time", "");
                invalidDataItem.put("reason", reason);
                invalidDataList.add(invalidDataItem);

                serverNote.put("reason_invalid_data", invalidDataList);
            } else {
                serverNote.put("reason_not_collected_as_scheduled", reason);
                serverNote.put("reason_invalid_data", "");
            }

            try (FileWriter writer = new FileWriter(singleNoteFile)) {
                gson.toJson(serverNote, writer);

                // Upload the file to server
                FileUploader.uploadFile(singleNoteFile.getAbsolutePath(), new FileUploader.UploadCallback() {
                    @Override
                    public void onUploadComplete(boolean success, String message) {
                        if (success) {
                            // Move to Uploaded folder on success
                            FileUploader.moveFileToUploadedFolder(singleNoteFile);
                            Log.i(TAG, "Note file uploaded successfully");
                        } else {
                            // File stays in main folder for retry later
                            Log.e(TAG, "Failed to upload note file: " + message);
                        }
                    }
                });
            }

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