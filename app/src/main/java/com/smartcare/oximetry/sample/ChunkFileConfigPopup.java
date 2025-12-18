package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

public class ChunkFileConfigPopup {
    private static final String PREFS_NAME = "ChunkFileConfigPrefs";
    private static final String KEY_ENABLE_CHUNK_FILE = "enable_chunk_file";
    private static final String KEY_TIME_INTERVAL = "time_interval";

    private PopupWindow popupWindow;
    private Context context;
    private Switch enableChunkFileSwitch;
    private EditText timeIntervalEditText;
    private Button submitButton;
    private SharedPreferences preferences;

    public ChunkFileConfigPopup(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void show(View anchorView) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.chunk_file_config_popup, null);

        initializeViews(popupView);
        loadSavedData();
        setupListeners();

        // Create and show PopupWindow
        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
        dimBackground(true);

        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setOnDismissListener(() -> dimBackground(false));

        submitButton.setOnClickListener(v -> validateAndSubmit());
    }

    private void initializeViews(View popupView) {
        enableChunkFileSwitch = popupView.findViewById(R.id.chunkFileSwitch);
        timeIntervalEditText = popupView.findViewById(R.id.timeEditText);
        submitButton = popupView.findViewById(R.id.submitButton);
    }

    private void setupListeners() {
        enableChunkFileSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            timeIntervalEditText.setEnabled(isChecked);
        });
    }

    private void loadSavedData() {
        boolean enableChunkFile = preferences.getBoolean(KEY_ENABLE_CHUNK_FILE, false);
        int timeInterval = preferences.getInt(KEY_TIME_INTERVAL, 0);

        enableChunkFileSwitch.setChecked(enableChunkFile);
        timeIntervalEditText.setText(String.valueOf(timeInterval));
        timeIntervalEditText.setEnabled(enableChunkFile);
    }

    private void saveData(boolean enableChunkFile, int timeInterval) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_ENABLE_CHUNK_FILE, enableChunkFile);
        editor.putInt(KEY_TIME_INTERVAL, timeInterval);
        editor.commit();
    }

    private void validateAndSubmit() {
        boolean enableChunkFile = enableChunkFileSwitch.isChecked();
        int timeInterval = Integer.parseInt(timeIntervalEditText.getText().toString());

        saveData(enableChunkFile, timeInterval);
        popupWindow.dismiss();
    }

    private void dimBackground(boolean dim) {
        Activity activity = (Activity) context;
        WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
        layoutParams.dimAmount = dim ? 0.7f : 0.0f;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        activity.getWindow().setAttributes(layoutParams);
    }
    // Methods to retrieve the state
    // default is true
    public boolean isChunkFileEnabled() {
        return preferences.getBoolean(KEY_ENABLE_CHUNK_FILE, false);
    }

    // default time interval is 3 minutes
    public int getTimeInterval() {
        return preferences.getInt(KEY_TIME_INTERVAL, 3);
    }


}