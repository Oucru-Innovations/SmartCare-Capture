package com.smartcare.oximetry.sample;


import static com.smartcare.oximetry.sample.OximeterDataWriterFunctions.formatTimestamp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ShowAnnotate {
    //drop down annotate
    private Context context;
    private static final String[] predefinedCategories = {"Category 1", "Category 2", "Category 3", "Other"};
    private static CSVWriter writer;

    public ShowAnnotate(Context context) {
        this.context = context;
    }

//    public void showAnnotateMenu(View view) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(context);
//        LayoutInflater inflater = LayoutInflater.from(context);
//        View dialogView = inflater.inflate(R.layout.dialog_list, null);
//        builder.setView(dialogView);
//
//        ListView listView = dialogView.findViewById(R.id.listView);
//        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.list_item, R.id.textView, predefinedCategories);
//        listView.setAdapter(adapter);
//
//        AlertDialog alertDialog = builder.create();
//
//
//        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
////                handleAnnotation(predefinedCategories[position]);
////                Toast.makeText(context, "Saved Annotate", Toast.LENGTH_SHORT).show();
//                if (predefinedCategories[position].equals("Other")) {
//                    showOtherCategoryDialog();
//                } else {
//                    handleAnnotation(predefinedCategories[position]);
//                    Toast.makeText(context, "Saved Annotate", Toast.LENGTH_SHORT).show();
//                }
//                alertDialog.dismiss();
//            }
//        });
//
////        builder.setNegativeButton("Other", new DialogInterface.OnClickListener() {
////            @Override
////            public void onClick(DialogInterface dialog, int which) {
////                showOtherCategoryDialog();
////            }
////        });
////        Button otherButton = dialogView.findViewById(R.id.otherButton);
////        otherButton.setOnClickListener(new View.OnClickListener() {
////            @Override
////            public void onClick(View v) {
////                showOtherCategoryDialog();
////            }
////        });
//
//
//        alertDialog.show();
//    }
    public void showAnnotateMenu(View anchorView) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.dialog_list, null);

        ListView listView = popupView.findViewById(R.id.listView);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.list_item, R.id.textView, predefinedCategories);
        listView.setAdapter(adapter);

        PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (predefinedCategories[position].equals("Other")) {
                    showOtherCategoryDialog();
                } else {
                    handleAnnotation(predefinedCategories[position]);
                    Toast.makeText(context, "Saved Annotate", Toast.LENGTH_SHORT).show();
                }
                popupWindow.dismiss();
            }
        });

    popupWindow.setOutsideTouchable(true);
    popupWindow.setFocusable(true);
    popupWindow.setOnDismissListener(() -> {
        // Handle any additional actions on dismiss if needed
    });
}

    public static void startAnnotationSession(long startTime, String formatedCode) {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            // Handle error: external storage not available
            return;
        }

        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OximeterData/Annotation");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String prefix = "Annotation";

        String time = formatTimestamp(startTime);

        String fileName = String.format("%s_%s_%s.csv", prefix, formatedCode, time);

        File file = new File(directory, fileName);

        try {
            writer = new CSVWriter(new FileWriter(file, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stopAnnotationSession() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void handleAnnotation(String category) {
        saveAnnotationToCSV(category);
    }

    private void saveAnnotationToCSV(String annotation) {
        if (writer == null) {
            // Handle error: writer not initialized
            Toast.makeText(context, "No session record to annotate", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String[] csvData = {timeStamp, annotation};

        writer.writeNext(csvData);
        try {
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void showOtherCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Custom Category");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String customCategory = input.getText().toString();
                saveAnnotationToCSV(customCategory);
                Toast.makeText(context, "Saved Annotate", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }
}
