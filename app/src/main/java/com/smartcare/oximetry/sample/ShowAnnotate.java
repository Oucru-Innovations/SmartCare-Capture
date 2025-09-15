package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.TextWatcher;

import java.io.OutputStream;
import java.util.Calendar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private static final String TEMPLATE_LOG_NOTE = "log_note";
    private static final String TEMPLATE_MODEL_NOTE = "model_note";

//    private static final String TEMPLATE_TASK_LIST = "task_list";

    // Annotation model class with template support
    public static class AnnotationEntry {
        String timestamp;
        String patientCode;
        String note;
        String heartRate;
        String spO2;
        String template;
        Map<String, Object> templateData;

        public AnnotationEntry(String timestamp, String patientCode, String note,
                               String heartRate, String spO2) {
            this.timestamp = timestamp;
            this.patientCode = patientCode;
            this.note = note;
            this.heartRate = heartRate;
            this.spO2 = spO2;
            this.template = TEMPLATE_LOG_NOTE; // Default template for backward compatibility
            this.templateData = new HashMap<>();
        }

        public AnnotationEntry(String timestamp, String patientCode, String note,
                               String heartRate, String spO2, String template, Map<String, Object> templateData) {
            this.timestamp = timestamp;
            this.patientCode = patientCode;
            this.note = note;
            this.heartRate = heartRate;
            this.spO2 = spO2;
            this.template = template;
            this.templateData = templateData != null ? templateData : new HashMap<>();
        }

        // Getters
        public String getTimestamp() { return timestamp; }
        public String getPatientCode() { return patientCode; }
        public String getNote() { return note; }
        public String getHeartRate() { return heartRate; }
        public String getSpO2() { return spO2; }
        public String getTemplate() { return template; }
        public Map<String, Object> getTemplateData() { return templateData; }

        // Setter for note content
        public void setNote(String note) { this.note = note; }
        public void setTemplateData(Map<String, Object> templateData) { this.templateData = templateData; }
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

        // Get template selector references
        RadioGroup templateSelector = dialogView.findViewById(R.id.template_selector);
        RadioButton templateLogNote = dialogView.findViewById(R.id.template_log_note);
        RadioButton templateMeeting = dialogView.findViewById(R.id.template_model_note);
        final ViewFlipper templateFlipper = dialogView.findViewById(R.id.template_content_flipper);

        // Set default template (Log Note)
        templateLogNote.setChecked(true);
        templateFlipper.setDisplayedChild(0);

        // Initialize Log Note template
        View logNoteView = templateFlipper.getChildAt(0);
        final EditText recordingTimeEdit = logNoteView.findViewById(R.id.edit_recording_time);
        final RadioGroup isCollectingRadioGroup = logNoteView.findViewById(R.id.radio_is_collecting);
        final RadioButton yesRadioButton = logNoteView.findViewById(R.id.radio_yes);
        final RadioButton noRadioButton = logNoteView.findViewById(R.id.radio_no);
        final LinearLayout notCollectingLayout = logNoteView.findViewById(R.id.layout_not_collecting_reason);
        final LinearLayout invalidDataLayout = logNoteView.findViewById(R.id.layout_invalid_data_reason);
        final EditText notCollectingReasonEdit = logNoteView.findViewById(R.id.edit_not_collecting_reason);
        final EditText invalidDataReasonEdit = logNoteView.findViewById(R.id.edit_invalid_data_reason);

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

        View modelView = templateFlipper.getChildAt(1);
        RadioGroup comorbidGroup = modelView.findViewById(R.id.radio_comorbid);
        RadioGroup ventilationGroup = modelView.findViewById(R.id.radio_ventilation);
        // Set up template selection listener
        templateSelector.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.template_log_note) {
                templateFlipper.setDisplayedChild(0);
            } else if (checkedId == R.id.template_model_note) {
                templateFlipper.setDisplayedChild(1);
            }
//            else if (checkedId == R.id.template_task_list) {
//                templateFlipper.setDisplayedChild(2);
//            }
        });

        builder.setPositiveButton("Save", null); // We'll override this below
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        final AlertDialog dialog = builder.create();
        dialog.show();

        // Override the "Save" button to handle validation and template-specific saving
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int selectedTemplateIndex = templateFlipper.getDisplayedChild();

            switch (selectedTemplateIndex) {
                case 0: // Log Note
                    String recordingTime = recordingTimeEdit.getText().toString().trim();
                    boolean isCollectedAsScheduled = yesRadioButton.isChecked();

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

                    handleQuickNoteAnnotation(recordingTime, isCollectedAsScheduled, reason);
                    break;

                case 1: // Model template
                    String modelAblett = ((EditText) modelView.findViewById(R.id.edit_ablett_score)).getText().toString().trim();
                    String modelAge = ((EditText) modelView.findViewById(R.id.edit_age)).getText().toString().trim();

                    // Get RadioGroup values
                    String modelComorbid = getRadioGroupValue(comorbidGroup);
                    String modelVentilation = getRadioGroupValue(ventilationGroup);

                    handleMeetingAnnotation(modelAblett, modelComorbid, modelVentilation, modelAge);
                    break;
            }

            dialog.dismiss();
        });
    }
    // Helper method to get selected value from RadioGroup
    private String getRadioGroupValue(RadioGroup radioGroup) {
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId != -1) {
            RadioButton selectedButton = radioGroup.findViewById(selectedId);
            return selectedButton.getText().toString();
        }
        return "";
    }
    // Helper method to add date formatting TextWatcher to EditText fields
    private void addDateFormatWatcher(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
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
                    if (cursorPos <= editText.getText().length()) {
                        editText.setSelection(cursorPos);
                    }
                }
                isFormatting = false;
            }
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

    // Handle Log Note annotations
    private void handleQuickNoteAnnotation(String recordingTime, boolean isCollectedAsScheduled, String reason) {
        try {
            // Create note content from the fields
            String noteContent = (isCollectedAsScheduled ? "Reason for Invalid Data: " : "Reason for Not Collecting: ") + reason;

            // Create template-specific data
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("recordingTime", recordingTime);
            templateData.put("isCollectedAsScheduled", isCollectedAsScheduled);
            templateData.put("reason", reason);

            // Save the annotation
            saveAnnotationEntry(noteContent, TEMPLATE_LOG_NOTE, templateData);

            // Create server JSON with specific format
            createServerJsonForLogNote(recordingTime, isCollectedAsScheduled, reason);

            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving annotation", e);
            Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show();
        }
    }
    // Handle model annotations
    private void handleMeetingAnnotation(String ablettScore, String comorbid, String ventilation, String age) {
        try {
            // Create note content from the model template fields
            String noteContent = "Ablett Score: " + ablettScore;
            if (!comorbid.isEmpty()) {
                noteContent += "\nComorbidities: " + comorbid;
            }
            if (!ventilation.isEmpty()) {
                noteContent += "\nVentilation: " + ventilation;
            }
            if (!age.isEmpty()) {
                noteContent += "\nAge: " + age;
            }
            // Create template-specific data
            Map<String, Object> templateData = new HashMap<>();
            templateData.put("ablettScore", ablettScore);
            templateData.put("comorbidities", comorbid);
            templateData.put("ventilation", ventilation);
            templateData.put("age", age);

            // Save the annotation
            saveAnnotationEntry(noteContent, TEMPLATE_MODEL_NOTE, templateData);

            // Create server JSON for the model template
            createServerJsonForModel(ablettScore, comorbid, ventilation, age);

            Toast.makeText(context, "Model note saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving model annotation", e);
            Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show();
        }
    }
    // Common method to save annotation entry
    private void saveAnnotationEntry(String noteContent, String template, Map<String, Object> templateData) throws IOException {
        // Load existing annotations
        List<AnnotationEntry> annotations = loadAnnotations();

        // Filter notes for this patient
        List<AnnotationEntry> patientNotes = new ArrayList<>();
        for (AnnotationEntry entry : annotations) {
            if (entry.getPatientCode().equals(patientCode)) {
                patientNotes.add(entry);
            }
        }
        // \|\./|/
        // Remove oldest if there are already 5 notes
        if (patientNotes.size() >= 5) {
            AnnotationEntry oldest = Collections.min(patientNotes, Comparator.comparing(AnnotationEntry::getTimestamp));
            annotations.remove(oldest);
        }

        // Create new annotation
        String timeStamp = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US).format(new Date());
        AnnotationEntry newEntry = new AnnotationEntry(
                timeStamp,
                patientCode,
                noteContent,
                "",
                "",
                template,
                templateData
        );

        // Add new annotation
        annotations.add(newEntry);

        // Save to file
        saveAnnotations(annotations);
    }

    // Create server JSON for Log Note template
    private void createServerJsonForLogNote(String recordingTime, boolean isCollectedAsScheduled, String reason) {
        String timeStamp = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US).format(new Date());

        try {
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
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'XXX", Locale.US);
            outputFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT+07:00"));

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

            serverNote.put("data_type", "PPG");
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
                FileUploader.uploadNoteFile(singleNoteFile.getAbsolutePath(), new FileUploader.UploadCallback() {
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
        } catch (IOException e) {
            Log.e(TAG, "Error creating server JSON for quick note", e);
        }
    }

    // Create server JSON for Model template
    private void createServerJsonForModel(String ablettScore, String comorbid, String ventilation, String age) {
        String timeStamp = new SimpleDateFormat("dd.MM.yyyy.HH.mm.ss", Locale.US).format(new Date());

        try {
//            File oximeterDataDir = context.getExternalFilesDir(null);
//            File mainFolder = new File(oximeterDataDir, "OximeterData");
//            if (!mainFolder.exists()) {
//                mainFolder.mkdirs();
//            }

            String singleFileName = "Model_" + patientCode + "_" + timeStamp.replace(" ", ".")
                    .replace(":", ".").replace("-", ".") + "_.json";

            // Create JSON with specific format for server
            Map<String, Object> serverNote = new HashMap<>();
            serverNote.put("ablett", ablettScore);
            serverNote.put("comorbidities", comorbid);
            serverNote.put("ventilation", ventilation);
            serverNote.put("age", age);


            // Use MediaStore to save to Download/OximeterData/Annotation
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, singleFileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/OximeterData/Annotation");

            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream outputStream = contentResolver.openOutputStream(uri)) {
                    if (outputStream != null) {
                        String jsonString = gson.toJson(serverNote);
                        outputStream.write(jsonString.getBytes());
                        outputStream.flush();

                        // Upload the file using the URI
//                        FileUploader.uploadFile(uri.toString(), new FileUploader.UploadCallback() {
//                            @Override
//                            public void onUploadComplete(boolean success, String message) {
//                                if (success) {
//                                    // Move to Uploaded folder on success
//                                    FileUploader.moveFileToUploadedFolderFromUri(context, uri.toString());
//                                    Log.i(TAG, "Model note file uploaded successfully");
//                                } else {
//                                    Log.e(TAG, "Failed to upload model note file: " + message);
//                                }
//                            }
//                        });
                    }
                }
            } else {
                Log.e(TAG, "Failed to create file URI with MediaStore");
                Toast.makeText(context, "Failed to save model note", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating server JSON for model", e);
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
    //
    // Get JSON file for annotations
    private File getAnnotationFile() {
        File directory = getAnnotationDirectory();
        return new File(directory, "Annotations.json");
    }
    // Helper method to get annotation directory (unchanged from original)
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

    // Delete an annotation (unchanged from original)
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

    // Legacy methods for compatibility
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