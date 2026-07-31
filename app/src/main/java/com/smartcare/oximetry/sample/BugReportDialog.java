package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog báo lỗi: chọn một hoặc nhiều file log theo ngày, ghi mô tả vào từng file chọn, rồi upload
 * tuần tự lên server.
 */
public final class BugReportDialog {

    private BugReportDialog() {}

    public static void show(Activity activity, String patientCode) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_bug_report, null);
        EditText descriptionField = view.findViewById(R.id.edit_bug_report_description);
        ListView logFilesList = view.findViewById(R.id.list_bug_report_log_files);
        TextView noLogsText = view.findViewById(R.id.text_bug_report_no_logs);

        List<FileLogger.LogFileInfo> availableLogs = FileLogger.listAvailableLogFiles();
        ArrayAdapter<FileLogger.LogFileInfo> adapter = new ArrayAdapter<>(
                activity, android.R.layout.simple_list_item_multiple_choice, availableLogs);
        logFilesList.setAdapter(adapter);

        if (availableLogs.isEmpty()) {
            logFilesList.setVisibility(View.GONE);
            noLogsText.setVisibility(View.VISIBLE);
        } else {
            for (int i = 0; i < availableLogs.size(); i++) {
                if (availableLogs.get(i).displayName.contains("(today)")) {
                    logFilesList.setItemChecked(i, true);
                }
            }
            if (logFilesList.getCheckedItemCount() == 0) {
                logFilesList.setItemChecked(0, true);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.bug_report_title)
                .setView(view)
                .setPositiveButton(R.string.bug_report_send, null)
                .setNegativeButton(R.string.bug_report_cancel, null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String description = descriptionField.getText().toString().trim();
            if (TextUtils.isEmpty(description)) {
                Toast.makeText(activity, R.string.bug_report_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            List<FileLogger.LogFileInfo> selected = getSelectedLogFiles(logFilesList, availableLogs);
            if (selected.isEmpty()) {
                Toast.makeText(activity, R.string.bug_report_no_file_selected, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!NetworkHelper.isNetworkAvailable(activity)) {
                Toast.makeText(activity, R.string.bug_report_no_network, Toast.LENGTH_LONG).show();
                return;
            }

            setDialogEnabled(dialog, false);
            sendReport(activity, patientCode, description, selected, dialog);
        });
    }

    private static List<FileLogger.LogFileInfo> getSelectedLogFiles(
            ListView listView, List<FileLogger.LogFileInfo> availableLogs) {
        List<FileLogger.LogFileInfo> selected = new ArrayList<>();
        for (int i = 0; i < availableLogs.size(); i++) {
            if (listView.isItemChecked(i)) {
                selected.add(availableLogs.get(i));
            }
        }
        return selected;
    }

    private static void sendReport(Activity activity, String patientCode, String description,
                                   List<FileLogger.LogFileInfo> selectedFiles, AlertDialog dialog) {
        String patient = TextUtils.isEmpty(patientCode) ? "unknown" : patientCode.trim();
        String detail = "patient=" + patient
                + " | appVersion=" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + " | server=" + FileUploader.getBaseUrl()
                + " | files=" + selectedFiles.size()
                + " | userReport=" + description;

        for (FileLogger.LogFileInfo logFileInfo : selectedFiles) {
            FileLogger.appendUserReportToFile(logFileInfo.file, detail, activity);
        }
        FileLogger.flushToDisk();

        List<String> uploadNames = resolveBugReportUploadNames(patient, selectedFiles);
        uploadFilesSequentially(activity, patient, selectedFiles, uploadNames, 0, dialog, 0,
                selectedFiles.size());
    }

    /**
     * Server filename: SmartCareLog_{patientCode}_{logDate.uploadTime}_.txt
     * Adds _1, _2 only when two files would share the exact same upload name.
     */
    private static List<String> resolveBugReportUploadNames(String patientCode,
            List<FileLogger.LogFileInfo> selectedFiles) {
        long uploadTimeMs = System.currentTimeMillis();
        List<String> baseNames = new ArrayList<>(selectedFiles.size());
        for (FileLogger.LogFileInfo info : selectedFiles) {
            String timestamp = FileLogger.formatBugReportUploadTimestamp(info.file, uploadTimeMs);
            baseNames.add("SmartCareLog_" + patientCode + "_" + timestamp + "_.txt");
        }

        Map<String, Integer> duplicateCounts = new HashMap<>();
        for (String baseName : baseNames) {
            duplicateCounts.put(baseName, duplicateCounts.getOrDefault(baseName, 0) + 1);
        }

        Map<String, Integer> nextSuffix = new HashMap<>();
        List<String> uploadNames = new ArrayList<>(selectedFiles.size());
        for (String baseName : baseNames) {
            if (duplicateCounts.getOrDefault(baseName, 0) <= 1) {
                uploadNames.add(baseName);
                continue;
            }
            int suffix = nextSuffix.getOrDefault(baseName, 0) + 1;
            nextSuffix.put(baseName, suffix);
            uploadNames.add(baseName.replace("_.txt", "_" + suffix + "_.txt"));
        }
        return uploadNames;
    }

    private static void uploadFilesSequentially(Activity activity, String patient,
                                              List<FileLogger.LogFileInfo> selectedFiles,
                                              List<String> uploadNames,
                                              int index, AlertDialog dialog,
                                              int successCount, int totalCount) {
        if (index >= selectedFiles.size()) {
            activity.runOnUiThread(() -> {
                if (successCount == totalCount) {
                    FileLogger.logEvent("USER", "BUG_REPORT_UPLOAD_OK",
                            "patient=" + patient
                                    + " | server=" + FileUploader.getBaseUrl()
                                    + " | filesUploaded=" + successCount);
                    FileLogger.flushToDisk();
                    Toast.makeText(activity,
                            activity.getString(R.string.bug_report_success_count, successCount),
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } else if (successCount > 0) {
                    setDialogEnabled(dialog, true);
                    Toast.makeText(activity,
                            activity.getString(R.string.bug_report_partial_success, successCount, totalCount),
                            Toast.LENGTH_LONG).show();
                } else {
                    setDialogEnabled(dialog, true);
                    Toast.makeText(activity, R.string.bug_report_all_failed, Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        FileLogger.LogFileInfo current = selectedFiles.get(index);
        String logPath = current.file.getAbsolutePath();
        if (!current.file.isFile()) {
            uploadFilesSequentially(activity, patient, selectedFiles, uploadNames, index + 1, dialog,
                    successCount, totalCount);
            return;
        }

        String uploadName = uploadNames.get(index);
        FileLogger.i("BugReport", "Uploading log file (" + (index + 1) + "/" + totalCount + "): "
                + logPath + " as " + uploadName);

        FileUploader.uploadFile(activity, logPath, uploadName, (success, message) -> {
            int updatedSuccess = success ? successCount + 1 : successCount;
            if (!success) {
                FileLogger.w("BugReport", "Upload failed for " + uploadName + ": " + message);
            }
            uploadFilesSequentially(activity, patient, selectedFiles, uploadNames, index + 1, dialog,
                    updatedSuccess, totalCount);
        });
    }

    private static void setDialogEnabled(AlertDialog dialog, boolean enabled) {
        if (dialog == null) {
            return;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
        View descriptionField = dialog.findViewById(R.id.edit_bug_report_description);
        if (descriptionField != null) {
            descriptionField.setEnabled(enabled);
        }
        ListView logFilesList = dialog.findViewById(R.id.list_bug_report_log_files);
        if (logFilesList != null) {
            logFilesList.setEnabled(enabled);
        }
    }
}
