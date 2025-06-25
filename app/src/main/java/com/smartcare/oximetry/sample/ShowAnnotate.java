package com.smartcare.oximetry.sample;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class ShowAnnotate {
    private Context context;
    private static Map<String, CSVWriter> writerMap = new HashMap<>();
    private String patientCode;

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

    // UPDATED: Changed to use app-private external storage
    private CSVWriter getWriterForPatient(String patientCode) throws IOException {
        if (writerMap.containsKey(patientCode)) {
            return writerMap.get(patientCode);
        }

        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            throw new IOException("External storage not mounted");
        }

        // CHANGED: Use app-private external storage instead of public Downloads
        File directory = getAnnotationDirectory();
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new IOException("Failed to create annotation directory");
            }
        }

        String fileName = "Annotation.csv";
        File file = new File(directory, fileName);
        boolean isNewFile = !file.exists();

        CSVWriter writer = new CSVWriter(new FileWriter(file, true));

        // Write header if it's a new file
        if (isNewFile) {
            writer.writeNext(new String[]{"Timestamp", "Patient Code", "Note", "Heart Rate", "SpO2"});
        }

        writerMap.put(patientCode, writer);
        return writer;
    }

    // NEW: Helper method to get annotation directory
    private File getAnnotationDirectory() {
        // Use the same pattern as FileManagerActivity and OximeterDataWriterFunctions
        File externalDir = context.getExternalFilesDir(null);
        File oximeterDataDir = new File(externalDir, "OximeterData");
        return new File(oximeterDataDir, "Annotation");
    }

    public static void startAnnotationSession(String patientCode, Context context) {
        try {
            ShowAnnotate annotate = new ShowAnnotate(context, patientCode);
            annotate.getWriterForPatient(patientCode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stopAnnotationSession(String patientCode) {
        CSVWriter writer = writerMap.get(patientCode);
        if (writer != null) {
            try {
                writer.close();
                writerMap.remove(patientCode);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void stopAllSessions() {
        for (Map.Entry<String, CSVWriter> entry : writerMap.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        writerMap.clear();
    }

    private void handleAnnotation(String noteContent) {
        try {
            CSVWriter writer = getWriterForPatient(patientCode);
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String[] csvData = {
                    timeStamp,
                    patientCode,
                    noteContent,
                    String.valueOf(RecordActivity.annotationHeartRate),
                    String.valueOf(RecordActivity.annotationSpO2)};
            writer.writeNext(csvData);
            writer.flush();
            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show();
        }
    }

    public void showAnnotationHistory(View view) {
        AnnotationHistory.showAnnotationHistory(context, patientCode);
    }
}