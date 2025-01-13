package com.smartcare.oximetry.sample;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.regex.Pattern;

public class PatientInfoPopup {
    private static final String PREFS_NAME = "PatientInfoPrefs";
    private static final String KEY_RESEARCH_CODE = "research_code";
    private static final String KEY_LOCATION_CODE = "location_code";
    private static final String KEY_SERIAL_NUMBER = "serial_number";
    private static final String KEY_IS_FIRST_RUN = "is_first_run";

    private PopupWindow popupWindow;
    private Context context;
    private EditText researchCodeEditText;
    private EditText locationCodeEditText;
    private EditText serialNumberEditText;
    private Button submitButton;
    private SharedPreferences preferences;

    public PatientInfoPopup(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Callback interface
    public interface OnPatientInfoSavedListener {
        void onPatientInfoSaved();
    }

    private OnPatientInfoSavedListener onPatientInfoSavedListener;

    // Method to set the listener
    public void setOnPatientInfoSavedListener(OnPatientInfoSavedListener listener) {
        this.onPatientInfoSavedListener = listener;
    }

    public void show(View anchorView) {
        // Only show popup on first run or when explicitly requested to edit
        if (!isFirstRun() && !isEditRequested()) {
            loadSavedData();
            return;
        }

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.patient_info_popup, null);

        initializeViews(popupView);
        loadSavedData(); // Pre-fill fields with saved data if any
        setupValidationListeners();

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
        researchCodeEditText = popupView.findViewById(R.id.researchCodeEditText);
        locationCodeEditText = popupView.findViewById(R.id.locationCodeEditText);
        serialNumberEditText = popupView.findViewById(R.id.serialNumberEditText);
        submitButton = popupView.findViewById(R.id.submitButton);

        //
        researchCodeEditText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;
            private int prevLength;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!isFormatting) {
                    prevLength = s.length();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isFormatting) {
                    isFormatting = true;
                    StringBuilder formatted = new StringBuilder(s);

                    // Remove all dashes
                    for (int i = formatted.length() - 1; i >= 0; i--) {
                        if (formatted.charAt(i) == '-') {
                            formatted.deleteCharAt(i);
                        }
                    }

                    // Insert dashes at the correct positions
                    if (formatted.length() > 5) {
                        formatted.insert(5, '-');
                    }
                    if (formatted.length() > 9) {
                        formatted.insert(9, '-');
                    }

                    // Calculate the new cursor position
                    int cursorPosition = researchCodeEditText.getSelectionStart();
                    if (prevLength <= cursorPosition && cursorPosition < formatted.length()) {
                        cursorPosition++;
                    }

                    // Set the formatted text
                    researchCodeEditText.setText(formatted.toString());
                    researchCodeEditText.setSelection(Math.min(formatted.length(), cursorPosition));

                    isFormatting = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed here
            }
        });
    }

    private boolean isFirstRun() {
        return preferences.getBoolean(KEY_IS_FIRST_RUN, true);
    }

    private boolean isEditRequested() {
        // This flag would be set when user requests to edit info
        return preferences.getBoolean("edit_requested", false);
    }

    private void loadSavedData() {
        if (researchCodeEditText != null) {
            researchCodeEditText.setText(preferences.getString(KEY_RESEARCH_CODE, ""));
            locationCodeEditText.setText(preferences.getString(KEY_LOCATION_CODE, ""));
            serialNumberEditText.setText(preferences.getString(KEY_SERIAL_NUMBER, ""));
        }
    }

    private void saveData(String researchCode, String locationCode, String serialNumber) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_RESEARCH_CODE, researchCode);
        editor.putString(KEY_LOCATION_CODE, locationCode);
        editor.putString(KEY_SERIAL_NUMBER, serialNumber);
        editor.putBoolean(KEY_IS_FIRST_RUN, false);
        editor.putBoolean("edit_requested", false); // Reset edit flag
        editor.apply();
    }

    // Method to request editing of patient info
    public void requestEdit() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("edit_requested", true);
        editor.apply();
    }

    // Method to get saved patient info
    public PatientInfo getSavedPatientInfo() {
        return new PatientInfo(
                preferences.getString(KEY_RESEARCH_CODE, ""),
                preferences.getString(KEY_LOCATION_CODE, ""),
                preferences.getString(KEY_SERIAL_NUMBER, "")
        );
    }

    private void validateAndSubmit() {
        String researchCode = researchCodeEditText.getText().toString();
        String locationCode = locationCodeEditText.getText().toString();
        String serialNumber = serialNumberEditText.getText().toString();

        boolean isValid = isResearchCodeValid(researchCode) &&
                isLocationCodeValid(locationCode) &&
                isSerialNumberValid(serialNumber);

        if (isValid) {
            saveData(researchCode, locationCode, serialNumber);
            popupWindow.dismiss();

            // Trigger callback if listener is set
            if (onPatientInfoSavedListener != null) {
                onPatientInfoSavedListener.onPatientInfoSaved();
            }
        }
    }

    // Validation methods remain the same
    private boolean isResearchCodeValid(String researchCode) {
//        Pattern pattern = Pattern.compile("^\\d{2}[A-Z]{2}[a-z]?$");
//        return pattern.matcher(researchCode).matches();
        return true;
    }

    private boolean isLocationCodeValid(String locationCode) {
//        Pattern pattern = Pattern.compile("^\\d{3}$");
//        return pattern.matcher(locationCode).matches();
        return true;
    }

    private boolean isSerialNumberValid(String serialNumber) {
//        Pattern pattern = Pattern.compile("^\\w{3,6}$");
//        return pattern.matcher(serialNumber).matches() && !serialNumber.contains("-");
        return true;
    }

    // Helper class to hold patient info
    public static class PatientInfo {
        private String researchCode;
        private String locationCode;
        private String serialNumber;

        public PatientInfo(String researchCode, String locationCode, String serialNumber) {
            this.researchCode = researchCode;
            this.locationCode = locationCode;
            this.serialNumber = serialNumber;
        }

        public String getResearchCode() { return researchCode; }
        public String getLocationCode() { return locationCode; }
        public String getSerialNumber() { return serialNumber; }
    }

    // Existing helper methods remain the same
    private void dimBackground(boolean dim) {
        Activity activity = (Activity) context;
        WindowManager.LayoutParams layoutParams = activity.getWindow().getAttributes();
        layoutParams.dimAmount = dim ? 0.7f : 0.0f;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        activity.getWindow().setAttributes(layoutParams);
    }

    private void setupValidationListeners() {
        // Existing validation listeners implementation remains the same
        researchCodeEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void onTextChanged(String text) {
                validateResearchCode(text);
                updateSubmitButtonState();
            }
        });

        locationCodeEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void onTextChanged(String text) {
                validateLocationCode(text);
                updateSubmitButtonState();
            }
        });

        serialNumberEditText.addTextChangedListener(new TextChangeListener() {
            @Override
            public void onTextChanged(String text) {
                validateSerialNumber(text);
                updateSubmitButtonState();
            }
        });
    }

    private void validateResearchCode(String code) {
        if (code.isEmpty()) {
            researchCodeEditText.setError("Research code is required");
            return;
        }
        if (!isResearchCodeValid(code)) {
            researchCodeEditText.setError("Invalid format (e.g., 12AB or 12ABc)");
            return;
        }
        researchCodeEditText.setError(null);
    }
    //location -> ten benh nha
    private void validateLocationCode(String code) {
        if (code.isEmpty()) {
            locationCodeEditText.setError("Name is required");
            return;
        }
        if (!isLocationCodeValid(code)) {
            locationCodeEditText.setError("Must be exactly 3 digits");
            return;
        }
        locationCodeEditText.setError(null);
    }
    //Serial -> vi tri so giuong
    private void validateSerialNumber(String number) {
        if (number.isEmpty()) {
            serialNumberEditText.setError("Location is required");
            return;
        }
        if (!isSerialNumberValid(number)) {
            serialNumberEditText.setError("Must be 3-6 alphanumeric characters");
            return;
        }
        serialNumberEditText.setError(null);
    }

    private void updateSubmitButtonState() {
        boolean isValid = isResearchCodeValid(researchCodeEditText.getText().toString()) &&
                isLocationCodeValid(locationCodeEditText.getText().toString()) &&
                isSerialNumberValid(serialNumberEditText.getText().toString());
        submitButton.setEnabled(isValid);
    }

    private abstract class TextChangeListener implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onTextChanged(s.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {}

        abstract public void onTextChanged(String text);
    }
}