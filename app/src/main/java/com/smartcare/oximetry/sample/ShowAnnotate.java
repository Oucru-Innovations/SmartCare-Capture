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
        builder.setTitle("Add Note");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("Enter your note here");
        input.setMinLines(3);
        input.setMaxLines(5);
        builder.setView(input);

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String noteContent = input.getText().toString();
                if (!noteContent.isEmpty()) {
                    handleAnnotation(noteContent);
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private static CSVWriter getWriterForPatient(String patientCode) throws IOException {
        if (writerMap.containsKey(patientCode)) {
            return writerMap.get(patientCode);
        }

        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            throw new IOException("External storage not mounted");
        }

        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OximeterData/Annotation");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String fileName = String.format("Annotation_%s.csv", patientCode);
        File file = new File(directory, fileName);
        boolean isNewFile = !file.exists();

        CSVWriter writer = new CSVWriter(new FileWriter(file, true));

        // Write header if it's a new file
        if (isNewFile) {
            writer.writeNext(new String[]{"Timestamp", "Patient Code", "Note"});
        }

        writerMap.put(patientCode, writer);
        return writer;
    }

    public static void startAnnotationSession(String patientCode) {
        try {
            getWriterForPatient(patientCode);
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
            String[] csvData = {timeStamp, patientCode, noteContent};
            writer.writeNext(csvData);
            writer.flush();
            Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show();
        }
    }
}