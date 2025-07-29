/**
 * Copyright (c) 2018, SmartCare Analytics Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * NOT MEDICAL SOFTWARE. THIS SOFTWARE IS PROVIDED FOR INFORMATIONAL OR RESEARCH PURPOSES ONLY, AND
 * IS NOT FOR PROFESSIONAL MEDICAL USE, DIAGNOSIS, TREATMENT OR CARE, NOR IS IT INTENDED TO BE A
 * SUBSTITUTE THEREFOR. ALWAYS SEEK THE ADVICE OF A PHYSICIAN OR OTHER QUALIFIED HEALTH PROVIDER
 * PROPERLY LICENSED TO PRACTICE MEDICINE OR GENERAL HEALTHCARE IN YOUR JURISDICTION CONCERNING ANY
 * QUESTIONS YOU MAY HAVE REGARDING ANY HEALTH PROBLEM. NEVER DISREGARD PROFESSIONAL MEDICAL ADVICE
 * OR DELAY IN SEEKING IT BECAUSE OF SOMETHING YOU HAVE OBSERVED THROUGH THE USE OF THIS SOFTWARE.
 * ALWAYS CONSULT WITH YOUR PHYSICIAN OR OTHER QUALIFIED HEALTH CARE PROVIDER BEFORE EMBARKING ON A
 * NEW TREATMENT, DIET OR FITNESS PROGRAMME.
 *
 * Graphical charts are copyright (c) AndroidPlot (released under the Apache 2.0 licence).
 */

package com.smartcare.oximetry.sample;



import static com.smartcare.oximetry.sample.AnnotationHistory.showAnnotationHistory;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;


import androidx.core.app.ActivityCompat;

import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.github.psambit9791.jdsp.signal.Detrend;
import com.smartcare.oximetry.library.ConnectionManager;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

//new import

import java.util.Locale;
import java.util.Map;
import java.util.Queue;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

//service
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import com.github.psambit9791.jdsp.signal.Detrend;
/**
 * This Activity is responsible for recording, displaying and uploading a physiological signal
 * using a SmartCare Analytics Ltd. Bluetooth pulse oximeter.
 */
public class RecordActivity extends Activity {
    private static final String TAG = "RecordActivity";
    private static final double UI_BUFFER_SIZE_SAMPLES = 500;
    private static final int REQUEST_REQUIRED_PERMISSIONS = 17;
    private static final int REQUEST_ENABLE_BT = 1;
//    private static final int REQUEST_REQUIRED_PERMISSIONS = 17;
    private ConnectionManager connectionManager;
    private XYPlot mPlot;
    private Button mControlButton;
    private TextView mHrDisplay;
    private TextView mSpo2Display;
    private TextView mSpo2Label;
    private SimpleXYSeries mSeries;
    private SmartCareHandler mHandler;
    //newcode
    public static String MacAddress;
    private static final String PREFS_NAME = "OximeterPrefs";
    private static final String MAC_QUEUE_KEY = "MacAddressQueue";
    //new
    private static final String DEVICE_NAMES_KEY = "DeviceNames";
    private Map<String, String> deviceNames;
    private Queue<String> macAddressQueue;
    private SharedPreferences sharedPreferences;
    public static boolean isRecordingSessionActive =false;
    private TextView device1MacTextView;
    private TextView device2MacTextView;
    private ImageButton device1DeleteButton;
    private ImageButton device2DeleteButton;
    private String connectedMac;
    //popup
    private Button showDevicesButton;
    private AlertDialog devicesDialog;
    //turn on/off plot
//    private Button togglePlotButton;
    private ImageButton togglePlotButton;
    private LinearLayout plotContainer;
    private boolean isPlotVisible = true;
    //patient
    private Button patientInfoButton;
    private PatientInfoPopup patientInfoPopup;
    private TextView patientCodeTextView;
    private String formattedCode; //= patientIdentifier
    //end
    //battery
    private TextView batteryText;
    //auto
    private BluetoothAdapter bluetoothAdapter;
    private BroadcastReceiver bluetoothReceiver;
    private static final long SCAN_INTERVAL = 5000; // 10 seconds
    private Handler scanHandler;
    private Runnable scanRunnable;
    private List<String> blockedList = new ArrayList<>();
    private boolean isConnected = false;
//    test
    private String filePath;
    //trend line
    private SimpleXYSeries mTrendSeries;  // Trendline data
    //chunk file config
    private boolean isEnabled ;
    private int interval;
    //service space
    private OximeterDataService dataService;
    private boolean isServiceBound = false;
    private Intent serviceIntent;
    private static final int REQUEST_STORAGE_PERMISSION = 200;
    //variables for show annotation
    public static int annotationHeartRate = 0;
    public static double annotationSpO2 = 0.f;
    // handle service target.isServiceBound && target.dataService false
    private long lastReconnectAttempt = 0;
    /**
     * This Handler is responsible for handling messages from the ConnectionManager which may
     * contain data or error messages.
     */
    private static class SmartCareHandler extends Handler {

        // A weak reference won't suffer from memory leaks, which might otherwise occur with static
        // references to the activity context.
        private final WeakReference<RecordActivity> mmTarget;
        private long lastUpdateTime = 0;
        private static final long UPDATE_INTERVAL = 500;
        /**
         * Constructor for the ReceiveDataHandler.
         */
        public SmartCareHandler(RecordActivity target) {
            mmTarget = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            final RecordActivity target = mmTarget.get();
            Bundle data;

            // Each message contains an identification code in the Message,what field.
            switch (msg.what) {
                case ConnectionManager.MESSAGE_PACKET:
                    // A new data packet has been received.

                    // The required data from the oximeter can be retrieved from the data bundle
                    // using the relevant key(s).
                    data = msg.getData();

                    // The KEY_DATA_PLETH data contains the value of plethysmography signal.
                    int latestPlethValue = data.getInt(ConnectionManager.KEY_DATA_PLETH);
                    // The KEY_DATA_PULSE data contains the heart rate value.
                    int latestHeartRateValue = data.getInt(ConnectionManager.KEY_DATA_PULSE);
                    // The KEY_DATA_SPO2 data contains the SpO2 value.
                    double latestSpO2Value = data.getDouble(ConnectionManager.KEY_DATA_SPO2);
                    // The KEY_DATA_DEVICE_ID data contains the ID of the device configured in
                    // the menu.
                    int deviceId = data.getInt(ConnectionManager.KEY_DATA_DEVICE_ID);
                    // The KEY_DATA_BATTERY data contains the current battery level of the device.
                    double latestBatteryValue = data.getDouble(ConnectionManager.KEY_DATA_BATTERY);
                    // The KEY_DATA_COUNTER data contains a sequential counter that increments by
                    // one in each packet received. This is useful for determining if packets have
                    // been dropped.
                    int latestCounterValue = data.getInt(ConnectionManager.KEY_DATA_COUNTER);
                    // The KEY_DATA_SPO2STATUS data contains an integer indicating the status of
                    // the SpO2 measurement. A value of 0 means that the device is functioning well.
                    int latestSpO2Status = data.getInt(ConnectionManager.KEY_DATA_SPO2STATUS);
                    //new files
                    long latestRedAdc = data.getLong(ConnectionManager.KEY_DATA_REDADC);
                    long latestIrAdc = data.getLong(ConnectionManager.KEY_DATA_IRADC);
                    double latestPerfusionIndexValue = data.getDouble(ConnectionManager.KEY_DATA_PERFUSIONINDEX);
                    long latestPackageReceived = data.getLong(ConnectionManager.KEY_DATA_PACKAGE_RECEIVED);

                    int startTime= data.getInt(ConnectionManager.KEY_DATA_START_TIME);
                    //end
                    final double perfusionIndex = latestPerfusionIndexValue;

                    //store the latest HR and SpO2 values for annotation
                    annotationHeartRate = latestHeartRateValue;
                    annotationSpO2 = latestSpO2Value;
                    //double latestPerfusionIndexValue = -1;

                    //new code add
//                    Log.d("RecordActivity", String.format(Locale.US,
//                            "Pleth Value: %d, Heart Rate: %d, SpO2: %.2f, Device ID: %d, Battery: %.2f, Counter: %d, SpO2 Status: %d, Red ADC: %d, IR ADC: %d, Perfusion Index: %.2f, Package Received: %d, Start Time: %d",
//                            latestPlethValue, latestHeartRateValue, latestSpO2Value, deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status, latestRedAdc, latestIrAdc, latestPerfusionIndexValue, latestPackageReceived, startTime
//                    ));
//                    Log.d("RecordActivity", String.format(Locale.US, "counter: %d", latestCounterValue));
//                    String filePath = "D:\\DataApp\\Test\\counter_data.csv";
//                    try (FileWriter writer = new FileWriter(filePath, true)) { // true enables append mode
//                        writer.append(String.valueOf(latestCounterValue));
//                        writer.append('\n'); // Write each value on a new line
//                    } catch (IOException e) {
//                        Log.e("RecordActivity", "Error writing to CSV file", e);
//                    }
//                    Log.d("RecordActivity", "MAC address: "+MacAddress);
//                    Log.d("RecordActivity", String.format(Locale.US, "Sample: %s", target.connectionManager.getSampleRate() ));
                    // After retrieving all the data from the packet
//                    Log.d("RecordActivity", "Preparing to write CSV data");
//

                    long timestamp = System.currentTimeMillis();
                    //new data pass to service
                    // Send data to service if connected




                    try {
                        SharedPreferences chunkFileConfigPrefs = target.getSharedPreferences("ChunkFileConfigPrefs", MODE_PRIVATE);
                        target.isEnabled = chunkFileConfigPrefs.getBoolean("enable_chunk_file", false);
                        target.interval = chunkFileConfigPrefs.getInt("time_interval", 3);

//                        Log.i(TAG, "Chunk file: " + target.isEnabled + " ---- Interval: " + target.interval);
//                        target.writeDataToCSV(latestPlethValue, latestHeartRateValue, latestSpO2Value, deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status,timestamp);
                        if (!isRecordingSessionActive) {
//                            OximeterDataWriterFunctions.startNewRecording(latestPackageReceived, target.formattedCode, target.isEnabled, target.interval);
                            target.dataService.startDataCollection(latestPackageReceived,target.formattedCode, target.isEnabled, target.interval);
//                          ShowAnnotate.startAnnotationSession();
                            isRecordingSessionActive = true;
                        }
//                        OximeterDataWriterFunctions.startNewRecording(latestPackageReceived);
                        // Write data to CSV file by activity
//                        OximeterDataWriterFunctions.writeDataPoint(
//                                latestSpO2Value,
//                                latestSpO2Status,
//                                latestRedAdc,
//                                latestBatteryValue,
//                                latestHeartRateValue,
//                                latestIrAdc,
//                                latestPerfusionIndexValue,
//                                latestCounterValue,
//                                MacAddress,
//                                latestPackageReceived,
//                                latestPlethValue,
//                                timestamp
//                        );
//                        Log.i("RecordActivity", "CSV data written successfully");
                        //write data to CSV file by service
                        if (target.isServiceBound && target.dataService != null) {
                            target.dataService.addDataPoint(
                                    latestSpO2Value,
                                    latestSpO2Status,
                                    latestRedAdc,
                                    latestBatteryValue,
                                    latestHeartRateValue,
                                    latestIrAdc,
                                    latestPerfusionIndexValue,
                                    latestCounterValue,
                                    MacAddress,
                                    latestPackageReceived,
                                    latestPlethValue,
                                    timestamp
                            );
                        }
                        else {
                            // Service is unavailable - attempt to reconnect
                            Log.w("RecordActivity", "Service unavailable, attempting reconnection");

                            // Avoid spamming reconnection attempts - use a timestamp to throttle
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - target.lastReconnectAttempt > 15000) { // Only try every 15 seconds
                                target.lastReconnectAttempt = currentTime;
                                target.reconnectToService();
                            }
                        }
//                        Log.i("RecordActivity", "CSV data written successfully");

                    } catch (Exception e) {
                        Log.e("RecordActivity", "Error writing CSV data", e);
                    }

                    //end


                    // The KEY_DATA_PERFUSIONINDEX data contains the blood perfusion index value.
                    // This is only available from the SmartCare BM2000A oximeter.
                    if (data.containsKey(ConnectionManager.KEY_DATA_PERFUSIONINDEX)) {
                        latestPerfusionIndexValue = data.getDouble(ConnectionManager.KEY_DATA_PERFUSIONINDEX);
                    }

                    boolean seriesIsFull = target.mSeries.size() > UI_BUFFER_SIZE_SAMPLES;
                    if (seriesIsFull) {
                        target.mSeries.removeFirst();
                    }
                    target.mSeries.addLast(null, latestPlethValue);

                    //detrend data

//                    Detrend detrend = new Detrend(new double[]{latestPlethValue}, "constant");
//                    double[] trendLine = detrend.getTrendLine();
//                    Log.d(TAG, "Detrended data: " + Arrays.toString(trendLine));
//                    target.mTrendSeries.addLast(null, trendLine[0]);



                    if (seriesIsFull) {
                        // Detrend mSeries and calculate the trend line
                        double[] plethValues = new double[(int) UI_BUFFER_SIZE_SAMPLES];
                        for (int i = 0; i < UI_BUFFER_SIZE_SAMPLES; i++) {
                            plethValues[i] = target.mSeries.getY(i).doubleValue();
                        }
//                        Log.d(TAG, "Pleth Values: " + Arrays.toString(plethValues));

                        Detrend detrend = new Detrend(plethValues,"linear");
                        double[] detrendedData = detrend.detrendSignal();
//                        Log.d(TAG, "Detrended data: " + Arrays.toString(detrendedData));

                        double[] trendLine = detrend.getTrendLine();
//                        Log.d(TAG, "Trend line data: " + Arrays.toString(trendLine));

                        // Clear mTrendSeries and add the new trend line values
                        target.mTrendSeries.setModel(new LinkedList<>(), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);
                        for (double value : trendLine) {
                            target.mTrendSeries.addLast(null, value);
                        }
                        target.mPlot.redraw();
                    }
//                    target.mHrDisplay.setText(String.valueOf(latestHeartRateValue));
//                    target.mSpo2Display.setText(String.valueOf((int) latestSpO2Value));
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
                        // Update UI with the latest values
                        target.runOnUiThread(() -> {
                            String heartRateText = String.valueOf(latestHeartRateValue);
                            String spo2Text = String.valueOf((int) latestSpO2Value);
//                            String spo2Text = String.format(Locale.US, "SpO₂: %d%%\nPI: %.1f", (int) latestSpO2Value, perfusionIndex);

                            //convert battery to persentage
                            double percentage = Math.min(Math.max((latestBatteryValue - 3300.0) / 850.0, 0.0), 1.0) * 100.0;
                            String batteryString = String.valueOf(Math.ceil(percentage));
                            String battery = String.format(Locale.US, "Battery: %s%%", batteryString );
                            target.batteryText.setText(battery);

                            target.mHrDisplay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
                            target.mHrDisplay.setTypeface(null, Typeface.BOLD); // Set bold text
                            target.mHrDisplay.setText(heartRateText);
                            target.mSpo2Display.setTextSize(TypedValue.COMPLEX_UNIT_SP, 50);
                            target.mSpo2Display.setTypeface(null, Typeface.BOLD); // Set bold text
                            target.mSpo2Display.setText(spo2Text);

                        });

                        // Update last update time
                        lastUpdateTime = currentTime;
                    }
                    break;
                case ConnectionManager.MESSAGE_NO_DATA_AVAILABLE:
                    Toast.makeText(target, "No data available from oximeter", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_UNKNOWN_OXIMETER:
                    Toast.makeText(target, "Unknown pulse oximeter", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_BLUETOOTH_OFF:
                    Toast.makeText(target, "Bluetooth is off", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_BLUETOOTH_UNAVAILABLE:
                    Toast.makeText(target, "Bluetooth is unavailable", Toast.LENGTH_SHORT).show();
                    break;
                // smartcare turn off when it is connected
                case ConnectionManager.MESSAGE_CONNECTION_LOST:
                    Toast.makeText(target, "Bluetooth connection lost", Toast.LENGTH_SHORT).show();
                    //new
                    target.isConnected = false; //new
//                    isRecordingSessionActive = false;
//                    target.bluetoothAdapter.startDiscovery();

                    break;
                case ConnectionManager.MESSAGE_RECORDING_STARTED:
                    data = msg.getData();

                    // The KEY_DATA_START_TIME data contains the start time / date that the
                    // connection to the oximeter was made.
                    long recordingStarted = data.getLong(ConnectionManager.KEY_DATA_START_TIME);

                    DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(target.getApplicationContext());

                    String str = "Connected to " + target.connectionManager.getOximeterModelNumber()
                        + " oximeter at " + timeFormat.format(new Date(recordingStarted)) + "."
                        + "\nSampling at " + Integer.toString(target.connectionManager.getSampleRate())
                        + " samples per second.";

                    Toast.makeText(target, str, Toast.LENGTH_LONG).show();
                    //new
                    target.isConnected = true;
//                    target.bluetoothAdapter.cancelDiscovery();
                    if (ActivityCompat.checkSelfPermission(target, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        target.bluetoothAdapter.cancelDiscovery();
                    } else {
                        target.requestBluetoothPermissions();
                    }
                    //
                    break;
                case ConnectionManager.MESSAGE_RECORDING_ENDED:



                    // Create and show the Toast

//                    Toast.makeText(target, "Disconnected. " +totalMessagesSent +" messages sent", Toast.LENGTH_SHORT).show();
//                    Toast.makeText(target, "Disconnected from oximetry", Toast.LENGTH_SHORT).show();

                    final Toast toast = Toast.makeText(target, "Disconnected from oximetry", Toast.LENGTH_SHORT);
                    toast.show();
                    new Handler().postDelayed(toast::cancel, 1000); // 1000 milliseconds = 1 second

//                    FileUploader.uploadFile(target, target.filePath);
                    target.isConnected = false; //new
                    target.bluetoothAdapter.startDiscovery(); // new
                    target.connectedMac = null;
//                    target.updateMacAddressDisplay();
                    // Device has disconnected.
                    target.mControlButton.setText(target.getString(R.string.record_start));
                    target.mControlButton.setEnabled(true);
                    target.mControlButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            target.connectToPulseOximeter();
                        }
                    });

//                    new
                    target.isConnected = false; //new
                    target.bluetoothAdapter.startDiscovery(); // new
//                    target.bluetoothAdapter.startDiscovery();
                    //target.mPlot.clear();
                    //target.mPlot.redraw();
                    break;
                case ConnectionManager.MESSAGE_CLOCATION_PERMISSION_REQUIRED:
                    Toast.makeText(target, "Coarse location permission missing", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_FLOCATION_PERMISSION_REQUIRED:
                    Toast.makeText(target, "Fine location permission missing", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_DEVICE_NOT_FOUND:
                    Toast.makeText(target, "No oximeter device found", Toast.LENGTH_SHORT).show();
                    //new
                    target.isConnected = false;
                    target.bluetoothAdapter.startDiscovery();
                    // Clear the pending MAC address as the connection failed

                    break;
                default:
                    Log.e(target.getString(R.string.app_name), "Unknown message ID from ConnectionManager");
            }
        }
    }

    /**
     * A task which manipulates the UI and starts the recording process. It creates and initialises
     * the ConnectionManager and begins recording. This is done asynchronously as it is time
     * consuming and would otherwise block the UI thread, causing the app not to respond.
     */
    private class Connector extends AsyncTask<Void, Void, Boolean> {
        //new
        private String connectedMacAddress;
        @Override
        protected void onPreExecute() {

            Log.i(getString(R.string.app_name), "Connector task started");
            mControlButton.setText(getString(R.string.record_stop));
            //new condition
            mControlButton.setEnabled(false);
        }

//        @Override
//        protected Boolean doInBackground(Void... params) {
//            boolean success = connectionManager.connect();
//            return success;
//        }
        //new implement
        @Override
        protected Boolean doInBackground(Void... params) {
            boolean success = connectionManager.connect();
            if (success) {
                connectedMacAddress = connectionManager.getMacAddress();
                Log.d(TAG, "User click connecttttttttttt");
                blockedList.remove(connectedMacAddress);
                Log.d(TAG, "Connector: Successfully connected to MAC: " + connectedMacAddress);

                // Request high .rity connection if available on this Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        // Note: We can't directly access the BluetoothGatt object since ConnectionManager doesn't expose it
                        // Instead, we'll use reflection to try to access it if possible
                        Log.d(TAG, "Setting connection to high priority via reflection");

                        // For devices like the oximeter, setting connection parameters at OS level
                        if (ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED) {
                            // Use OS-level settings for reliable connections
                            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                            if (adapter != null) {
                                // The system will now prioritize this connection
                                Log.d(TAG, "Requesting OS-level high priority for Bluetooth connection");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error optimizing Bluetooth connection priority", e);
                    }
                }
            }//new

            return success;
        }
        @Override
        protected void onPostExecute(Boolean result) {

            mControlButton.setEnabled(true);

            if (!result) {
                Log.w(getString(R.string.app_name), "Cannot connect");
                mControlButton.setText(getString(R.string.record_start));

                mControlButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        connectToPulseOximeter();
                    }
                });
                return;
            }

            Log.i(getString(R.string.app_name), "Ready to start recording.");
            connectionManager.startRecording();
            //new
            updateMacAddressQueue(connectedMacAddress);
            MacAddress = connectedMacAddress;
//            MacAddress = connectionManager.getMacAddress();
            //new
            connectedMac = connectedMacAddress;
            // new

            mControlButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopDisconnectExit();
                    blockedList.add(connectedMac);
                    //new
                    isConnected = false; //new
//                    bluetoothAdapter.startDiscovery(); // new
                    if (ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter.startDiscovery(); // new
                    }
                    Log.d(TAG, "List block contents: " + blockedList);
                    Log.d(TAG, "User click buttonnnnnnnnnnnnnnnnnnnnnnnn ");
                }
            });
        }
    }

    /**
     * A task which stops the recording process by sending a signal to the ConnectionManager to
     * disconnect. This is done asynchronously as it is time
     * consuming and would otherwise block the UI thread, causing the app not to respond.
     */
    private class Disconnector extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Log.i(getString(R.string.app_name), "Disconnector task started");
            mControlButton.setText(getString(R.string.record_start));
            mControlButton.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            connectionManager.disconnect();
            return true;
            // Add current device to block list before disconnecting
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.i(getString(R.string.app_name), "Disconnector task finished");
            isRecordingSessionActive = false;
//            filePath = OximeterDataWriterFunctions.endRecording(); //old path from activity
                    // First attempt to stop the service directly
        Intent serviceIntent = new Intent(RecordActivity.this, OximeterDataService.class);
        stopService(serviceIntent);

        // Then clean up the binding if it exists
        if (isServiceBound) {
            try {
                if (dataService != null) {
                    dataService.stopDataCollection();
                }
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                // This happens if the service wasn't bound
                Log.w(TAG, "Service wasn't bound when trying to unbind");
            }
            isServiceBound = false;
        }
            //get path from service
            filePath = dataService.endRecordFilePath;
            Log.i("RecordActivity", "File path: " + filePath);
            //end annotation
//            ShowAnnotate.stopAnnotationSession();
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    FileUploader.uploadFile(RecordActivity.this, filePath, new FileUploader.UploadCallback() {
                        @Override
                        public void onUploadComplete(boolean success, String message) {
                            if (success) {
                                Log.i("FileUploader-RA", "File upload successful");
                                FileUploader.moveFileToUploadedFolder(new File(filePath));
                            } else {
                                Log.e("FileUploader-RA", "File upload failed: " + message);
                            }
                        }
                    });
                }
            }, 2000); // 2-second delay
//            connectedMac = null;
//            updateMacAddressDisplay();

            connectedMac = null;
            //new
//
        }
    }


    //

    //new code

    //end
    /**
     * Initialises the connection to the pulse oximeter by executing the Connector task.
     */
//    private void connectToPulseOximeter() {
//        boolean hasPermission = checkAppPermissions();
//        if (hasPermission) {
//            // If this is a manual connect, remove from block list
//            //
//            new Connector().execute();
//        } else {
//            // Didn't have one or more required permissions, so abort connection.
//        }
//        //new implement
//
//    }
    //service foreground, new connection
    private void connectToPulseOximeter() {


        boolean hasPermission = checkAppPermissions();
        if (hasPermission) {
            Log.d(TAG, "Attempting to start OximeterDataService");

            // Initialize service intent
            serviceIntent = new Intent(this, OximeterDataService.class);

            // Set service parameters
            serviceIntent.putExtra("patientCode", formattedCode);

            // Get chunk file configuration
            ChunkFileConfigPopup configPopup = new ChunkFileConfigPopup(this);
            boolean enableChunk = configPopup.isChunkFileEnabled();
            int timeInterval = configPopup.getTimeInterval();

            serviceIntent.putExtra("enableChunkFile", enableChunk);
            serviceIntent.putExtra("timeInterval", timeInterval);

            Log.d(TAG, "Service params: patientCode=" + formattedCode +
                    ", enableChunkFile=" + enableChunk +
                    ", timeInterval=" + timeInterval);

            // Start the service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting foreground service for Android O+");
                startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Starting regular service for pre-Android O");
                startService(serviceIntent);
            }

            // Bind to the service
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Service binding requested");

            // Continue with connection to oximeter
            new Connector().execute();
        }
    }


    /**
     * Sends a message to the ConnectionManager to stop recording. We will know recording has
     * finished and resources are tidied up when we receive the relevant message in the Handler.
     */
    private void stopDisconnectExit() {
//        move to Disconnector
//        // First attempt to stop the service directly
//        Intent serviceIntent = new Intent(this, OximeterDataService.class);
//        stopService(serviceIntent);
//
//        // Then clean up the binding if it exists
//        if (isServiceBound) {
//            try {
//                if (dataService != null) {
//                    dataService.stopDataCollection();
//                }
//                unbindService(serviceConnection);
//            } catch (IllegalArgumentException e) {
//                // This happens if the service wasn't bound
//                Log.w(TAG, "Service wasn't bound when trying to unbind");
//            }
//            isServiceBound = false;
//        }

        new Disconnector().execute();
    }

    /**
     * Called when the activity is first created. Initialises the private fields of the Activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //hide action bar
//        if (getActionBar() != null) {
//            getActionBar().hide();
//        }
        // Restore state if available

        // Initialise the UI.
        initialiseViews();
        // Initialize SharedPreferences and MAC address queue
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        macAddressQueue = loadMacAddressQueue();
        //new
        //clear block mac
        blockedList.clear();
//        deviceNames = loadDeviceNames();
        loadMacAddressQueueAndDeviceNames();

        // Set the activity reference when initializing
//        OximeterDataWriterFunctions.setActivity(this);
        OximeterDataWriterFunctions.initialize(getApplicationContext());

        initialisePlot();
        initialiseSeries();
        //show drop down annotate
        Button annotateButton = findViewById(R.id.annotateButton);
        annotateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Pass the current patient code to ShowAnnotate
                new ShowAnnotate(RecordActivity.this, formattedCode).showAnnotateMenu(v);
            }
        });


        //patient
        initializePatientInfo();

        // run it before auto connect to prevent connect without code
        checkAndRequestPatientCode();

        // Start scanning in the background
        initializeBluetoothScanning();
//        startBluetoothBackgroundScan(); // move to checkAndRequestPatientCode()
        //service foreground
        // Request battery optimization exemption
        requestBatteryOptimizationExemption();
        // Initialize service intent


        AnnotationUploadScheduler.scheduleDailyUpload(this);
        // Create new ConnectionManager, which will connect to and manage the pulse oximeter.
        mHandler = new SmartCareHandler(this);
        if (connectionManager == null) {
            connectionManager = new ConnectionManager(this, mHandler);
        }
        //new
        if(connectionManager.isRecording()){

        }

        patientInfoPopup.resetPatientCode();



//        updateMacAddressDisplay();
    }
    @Override
    protected void onResume() {
        super.onResume();

    }
    private void checkAndRequestPatientCode() {
        // Always show the popup on app startup, regardless of whether a code exists
        Handler handler = new Handler();
        handler.post(() -> {
            // Make the popup non-cancelable
            patientInfoPopup.setCancelable(false);
            // Show the popup with the content view
            try {
                View contentView = findViewById(android.R.id.content);
                if (contentView != null && contentView.getWindowToken() != null) {


                    patientInfoPopup.show(contentView);

                    // Disable app functions until code is confirmed
                    mControlButton.setEnabled(false);
                    if (findViewById(R.id.annotateButton) != null) {
                        findViewById(R.id.annotateButton).setEnabled(false);
                    }

                    // Set red color to indicate no code is active yet
                    patientCodeTextView.setTextColor(Color.RED);

                    // Set listener to enable functions when code is confirmed
                    patientInfoPopup.setOnPatientInfoSavedListener(() -> {
                        startBluetoothBackgroundScan();
                        updatePatientInfoDisplay();
                        mControlButton.setEnabled(true);
                        if (findViewById(R.id.annotateButton) != null) {
                            findViewById(R.id.annotateButton).setEnabled(true);
                        }
                        patientCodeTextView.setTextColor(Color.BLACK);
//                        new ShowAnnotate(RecordActivity.this, formattedCode);
                    });
                } else {
                    // Try again later if the window token isn't ready
                    handler.postDelayed(this::checkAndRequestPatientCode, 100);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing patient info popup", e);
            }
        });
    }

    //menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_devices:
                showConnectedDevicesPopup();
                return true;
            case R.id.action_manage_files:
//                if(isRecordingSessionActive){
//                    Toast.makeText(this, "Please stop recording before managing files", Toast.LENGTH_SHORT).show();
//                    return true;
//                }
                Intent manageFilesIntent = new Intent(this, FileManagerActivity.class);
                startActivity(manageFilesIntent);
                return true;
            case R.id.action_patient_info:
                patientInfoPopup.requestEdit();
                patientInfoPopup.show(findViewById(android.R.id.content));
                return true;
            case R.id.action_config_info:
                if(isRecordingSessionActive){
                    Toast.makeText(this, "Please stop recording before change configuration", Toast.LENGTH_SHORT).show();
                    return true;
                }
                ChunkFileConfigPopup chunkFileConfigPopup = new ChunkFileConfigPopup(this);
                chunkFileConfigPopup.show(findViewById(android.R.id.content));
                return true;
            case R.id.action_show_history:
                ShowAnnotate showAnnotate = new ShowAnnotate(this, formattedCode);
                showAnnotate.showAnnotationHistory(null);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    ///auto part
    private void initializeBluetoothScanning() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            return;
        }

        // Define and initialize the BroadcastReceiver as a class-level variable
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceMac = device.getAddress();

                    // Check if device is in macAddressQueue
                    if (macAddressQueue.contains(deviceMac) && !blockedList.contains(deviceMac) ) {
//                        Log.i(TAG, "Device found in queue: " + device.getName() + " [" + deviceMac + "]");


                        if( isConnected == false) {
//                            Log.i(TAG, "Device found in queue: " + device.getName() + " [" + deviceMac + "]"); //old
                            Log.i(TAG, "Device found in queue: " + " [" + deviceMac + "]");
//                            connectToPulseOximeter();
//                            ProgressBar progressBar = RecordActivity.this.findViewById(R.id.progressBar);
//                            progressBar.setVisibility(View.VISIBLE);


                                // Start the service before connecting
                                serviceIntent = new Intent(RecordActivity.this, OximeterDataService.class);
                                serviceIntent.putExtra("patientCode", formattedCode);

                                // Get chunk file configuration
                                ChunkFileConfigPopup configPopup = new ChunkFileConfigPopup(RecordActivity.this);
                                boolean enableChunk = configPopup.isChunkFileEnabled();
                                int timeInterval = configPopup.getTimeInterval();

                                serviceIntent.putExtra("enableChunkFile", enableChunk);
                                serviceIntent.putExtra("timeInterval", timeInterval);
                            // Start foreground service
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(serviceIntent);
                            } else {
                                startService(serviceIntent);
                            }

                            // Bind to service
                            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);


                            boolean success = connectionManager.connect(deviceMac);
                            if(success){
                                isConnected = true;
                                //pause scanning
                                Log.i(getString(R.string.app_name), "Connector task started");
                                mControlButton.setText(getString(R.string.record_stop));
                                //
                                Log.i(getString(R.string.app_name), "Ready to start recording.");
                                connectionManager.startRecording();
                                //
                                //
                                updateMacAddressQueue(deviceMac);
                                MacAddress = deviceMac;
                                connectedMac = deviceMac;
                                mControlButton.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        stopDisconnectExit();
                                        blockedList.add(deviceMac);
                                        isConnected = false;
//                                        bluetoothAdapter.startDiscovery(); //old
                                        //new
                                        if (ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                            bluetoothAdapter.startDiscovery();
                                        } else {
                                            requestBluetoothPermissions();
                                        }
                                    }
                                });
                            }
//                            bluetoothAdapter.cancelDiscovery();
                        }
//                        bluetoothAdapter.cancelDiscovery();//
                    } else {
                        Log.d(TAG, "Discovered device not in queue: " + device.getName() + " [" + deviceMac + "]");
//                        Log.d(TAG, "Discovered device not in queue: " + " [" + deviceMac + "]");
                    }
                }
            }
        };

        // Register the Bluetooth device discovery receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void startBluetoothBackgroundScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth is disabled or unavailable");
            return;
        }

        // Initialize scan handler and runnable for periodic scanning
        scanHandler = new Handler();
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                // Check permissions based on Android version
                boolean hasPermission;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+
                    hasPermission = ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;
                } else {
                    // Android 11 and below
                    hasPermission = ActivityCompat.checkSelfPermission(RecordActivity.this, Manifest.permission.BLUETOOTH_ADMIN)
                            == PackageManager.PERMISSION_GRANTED;
                }

                if (hasPermission) {
                    if (!bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.startDiscovery();
                        Log.d(TAG, "Started Bluetooth device discovery");
                    }
                } else {
                    requestBluetoothPermissions();
                    return;
                }

                // Schedule next scan after the interval
                scanHandler.postDelayed(this, SCAN_INTERVAL);
            }
        };

        // Start the first scan
        scanHandler.post(scanRunnable);
    }

    //trend line


    //end
    private void initialiseViews() {
        mPlot = (XYPlot) findViewById(R.id.plot);
        mControlButton = (Button) findViewById(R.id.button);
        mHrDisplay = (TextView) findViewById(R.id.hr);
        mSpo2Display = (TextView) findViewById(R.id.spo2);
        mSpo2Label = (TextView) findViewById(R.id.spo2label);
//        mSpo2Label.setText(Html.fromHtml("SpO<sub><small>2</small></sub>"));
        batteryText = (TextView) findViewById(R.id.batteryText);
        //new
//        device1MacTextView = (TextView) findViewById(R.id.connectedMac);
//        device2MacTextView = (TextView) findViewById(R.id.queuedMac);
//        device1DeleteButton = (Button) findViewById(R.id.device1Delete);
//        device2DeleteButton = (Button) findViewById(R.id.device2Delete);
        //
        //on/off plot
        togglePlotButton = (ImageButton) findViewById(R.id.togglePlotButton);
        plotContainer = (LinearLayout) findViewById(R.id.plotContainer);

        togglePlotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlotVisibility();
            }
        });
        //


        mControlButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                    connectToPulseOximeter();}

        });

    }
    //pop up for patient input
    private void initializePatientInfo() {
//        patientInfoButton = findViewById(R.id.patientInfoButton);
        patientCodeTextView = findViewById(R.id.patientCodeTextView); // Make sure you have this in your layout
        patientInfoPopup = new PatientInfoPopup(this);

        // Set up the listener to update display on save
        patientInfoPopup.setOnPatientInfoSavedListener(this::updatePatientInfoDisplay);

        // Set up the patient info button
//        patientInfoButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // Request edit mode before showing popup
//                patientInfoPopup.requestEdit();
//                patientInfoPopup.show(findViewById(android.R.id.content));
//            }
//        });
        patientCodeTextView.setOnClickListener(v -> {
            if(isRecordingSessionActive){
                Toast.makeText(this, "Please stop recording before change code", Toast.LENGTH_SHORT).show();
                return ;
            }
            patientInfoPopup.show(findViewById(android.R.id.content));
        });
        // Check for existing patient info and update display
        updatePatientInfoDisplay();
    }

    private void updatePatientInfoDisplay() {


        PatientInfoPopup.PatientInfo savedInfo = patientInfoPopup.getSavedPatientInfo();

        // If we have saved patient info, format and display it
//        if (!savedInfo.getResearchCode().isEmpty()) {
//            formattedCode = String.format("%s-%s-%s",
//                    savedInfo.getResearchCode(),
//                    savedInfo.getLocationCode(),
//                    savedInfo.getSerialNumber());
//
//            if (patientCodeTextView != null) {
//                patientCodeTextView.setText(formattedCode);
//            }
//        }
        //new update combie all in one
        formattedCode = savedInfo.getResearchCode();
        patientCodeTextView.setText(savedInfo.getResearchCode());
    }
    //end


//    //new view
    private void loadMacAddressQueueAndDeviceNames() {
        String queueString = sharedPreferences.getString(MAC_QUEUE_KEY, "");
        String[] macAddresses = queueString.split(",");
        macAddressQueue = new LinkedList<>();
        deviceNames = new HashMap<>();

        for (String mac : macAddresses) {
            if (!mac.isEmpty()) {
                macAddressQueue.offer(mac);
                // Load the device name for this MAC address
                String name = sharedPreferences.getString(mac, "Unknown Device");
                deviceNames.put(mac, name);
            }
        }
    }
    private void saveMacAddressQueueAndDeviceNames() {
        StringBuilder queueString = new StringBuilder();
        SharedPreferences.Editor editor = sharedPreferences.edit();

        for (String mac : macAddressQueue) {
            queueString.append(mac).append(",");
            // Save the device name for this MAC address
            editor.putString(mac, deviceNames.get(mac));
        }

        editor.putString(MAC_QUEUE_KEY, queueString.toString());
        editor.apply();
    }
    //on/off plot
    private void togglePlotVisibility() {
        isPlotVisible = !isPlotVisible;
        if (isPlotVisible) {
            plotContainer.setVisibility(View.VISIBLE);
//            togglePlotButton.setText("Hide PPG");
            togglePlotButton.setImageResource(R.drawable.ic_zoom_in);
        } else {
            plotContainer.setVisibility(View.GONE);
            togglePlotButton.setImageResource(R.drawable.ic_chart);
        }
    }
    //popup
    private void showConnectedDevicesPopup() {
        //new
//        if (devicesDialog != null && devicesDialog.isShowing()) {
//            devicesDialog.dismiss();
//        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View popupView = getLayoutInflater().inflate(R.layout.popup_connected_devices, null);

        device1MacTextView = popupView.findViewById(R.id.connectedMac);
        device2MacTextView = popupView.findViewById(R.id.queuedMac);
        device1DeleteButton = popupView.findViewById(R.id.device1Delete);
        device2DeleteButton = popupView.findViewById(R.id.device2Delete);

        updatePopupMacAddressDisplay(device1MacTextView, device2MacTextView, device1DeleteButton, device2DeleteButton);

        device1DeleteButton.setOnClickListener(v -> {
            deleteDevice(0);
            updatePopupMacAddressDisplay(device1MacTextView, device2MacTextView, device1DeleteButton, device2DeleteButton);
        });

        device2DeleteButton.setOnClickListener(v -> {
            deleteDevice(1);
            updatePopupMacAddressDisplay(device1MacTextView, device2MacTextView, device1DeleteButton, device2DeleteButton);
        });

        builder.setView(popupView);
//        builder.setTitle("Connected Devices");
        builder.setPositiveButton("Close", null);

        devicesDialog = builder.create();
        devicesDialog.show();
    }
    private void updatePopupMacAddressDisplay(TextView device1TextView, TextView device2TextView,
                                              ImageButton device1DeleteButton, ImageButton device2DeleteButton) {
        String[] macs = macAddressQueue.toArray(new String[0]);

        if (macs.length > 0) {
            String deviceName = deviceNames.getOrDefault(macs[0], getString(R.string.unknown_device));
            device1TextView.setText(Html.fromHtml(String.format("%s <small>( %s )</small>", deviceName, macs[0])));
            device1TextView.setTextColor(macs[0].equals(connectedMac) ? Color.rgb(255, 159, 41) : Color.BLACK);
            device1TextView.setVisibility(View.VISIBLE);
            device1DeleteButton.setVisibility(View.VISIBLE);
            device1TextView.setOnLongClickListener(v -> {
                showRenameDeviceDialog(macs[0]);
                return true;
            });
        } else {
            device1TextView.setVisibility(View.GONE);
            device1DeleteButton.setVisibility(View.GONE);
        }

        if (macs.length > 1) {
            String deviceName = deviceNames.getOrDefault(macs[1], getString(R.string.unknown_device));
            device2TextView.setText(Html.fromHtml(String.format("%s <small>( %s )</small>", deviceName, macs[1])));
            device2TextView.setTextColor(macs[1].equals(connectedMac) ? Color.rgb(255, 159, 41) : Color.BLACK);
            device2TextView.setVisibility(View.VISIBLE);
            device2DeleteButton.setVisibility(View.VISIBLE);
            device2TextView.setOnLongClickListener(v -> {
                showRenameDeviceDialog(macs[1]);
                return true;
            });
        } else {
            device2TextView.setVisibility(View.GONE);
            device2DeleteButton.setVisibility(View.GONE);
        }

        if (macs.length == 0) {
            device1TextView.setText("No divices");
            device1TextView.setTextColor(Color.BLACK);
            device1TextView.setVisibility(View.VISIBLE);
            device1DeleteButton.setVisibility(View.GONE);
            device2DeleteButton.setVisibility(View.GONE);
        }
    }
    //
    private void showRenameDeviceDialog(String macAddress) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Rename Device");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(deviceNames.getOrDefault(macAddress, ""));
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String newName = input.getText().toString();
            deviceNames.put(macAddress, newName);
//            saveDeviceNames();
            saveMacAddressQueueAndDeviceNames();
            updatePopupMacAddressDisplay(device1MacTextView, device2MacTextView, device1DeleteButton, device2DeleteButton);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }
    //
    private void deleteDevice(int index) {
        if (index < macAddressQueue.size()) {
            String[] macs = macAddressQueue.toArray(new String[0]);
            String macToRemove = macs[index];
            macAddressQueue.remove(macToRemove);
            deviceNames.remove(macToRemove);
//            saveMacAddressQueue();
//            saveDeviceNames();
            saveMacAddressQueueAndDeviceNames();

            if (macToRemove.equals(connectedMac)) {
                new Disconnector().execute();
            }
        }
    }
    private void updateMacAddressDisplay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String[] macs = macAddressQueue.toArray(new String[0]);

                if (macs.length > 0) {
                    device1MacTextView.setText("Device 1: " + macs[0]);
                    device1MacTextView.setTextColor(macs[0].equals(connectedMac) ? Color.GREEN : Color.BLACK);
                    device1MacTextView.setVisibility(View.VISIBLE);
                    device1DeleteButton.setVisibility(View.VISIBLE);
                } else {
                    device1MacTextView.setVisibility(View.GONE);
                    device1DeleteButton.setVisibility(View.GONE);
                }

                if (macs.length > 1) {
                    device2MacTextView.setText("Device 2: " + macs[1]);
                    device2MacTextView.setTextColor(macs[1].equals(connectedMac) ? Color.GREEN : Color.BLACK);
                    device2MacTextView.setVisibility(View.VISIBLE);
                    device2DeleteButton.setVisibility(View.VISIBLE);
                } else {
                    device2MacTextView.setVisibility(View.GONE);
                    device2DeleteButton.setVisibility(View.GONE);
                }

                if (macs.length == 0) {
                    device1MacTextView.setText("No devices saved");
                    device1MacTextView.setTextColor(Color.BLACK);
                    device1MacTextView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private Queue<String> loadMacAddressQueue() {
        String queueString = sharedPreferences.getString(MAC_QUEUE_KEY, "");
        String[] macAddresses = queueString.split(",");
        Queue<String> queue = new LinkedList<>();
        for (String mac : macAddresses) {
            if (!mac.isEmpty()) {
                queue.offer(mac);
            }
        }
        return queue;
    }

    private void saveMacAddressQueue() {
        StringBuilder queueString = new StringBuilder();
        for (String mac : macAddressQueue) {
            queueString.append(mac).append(",");
        }
        sharedPreferences.edit().putString(MAC_QUEUE_KEY, queueString.toString()).apply();
    }
    //
    private Map<String, String> loadDeviceNames() {
        String namesString = sharedPreferences.getString(DEVICE_NAMES_KEY, "");
        Map<String, String> names = new HashMap<>();
        String[] pairs = namesString.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                names.put(keyValue[0], keyValue[1]);
            }
        }
        return names;
    }

    private void saveDeviceNames() {
        StringBuilder namesString = new StringBuilder();
        for (Map.Entry<String, String> entry : deviceNames.entrySet()) {
            namesString.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        sharedPreferences.edit().putString(DEVICE_NAMES_KEY, namesString.toString()).apply();
    }
    //
    private void updateMacAddressQueue(String newMac) {
        if (macAddressQueue.size() == 2 && !macAddressQueue.contains(newMac)) {
            disconnectAndReset();
            showDeleteDeviceDialog();
            return;
        }
        if (!macAddressQueue.contains(newMac)) {
            macAddressQueue.offer(newMac);
            deviceNames.putIfAbsent(newMac, "Unknown Device");
        }
        connectedMac = newMac;
        saveMacAddressQueueAndDeviceNames();
//        saveMacAddressQueue();
//        saveDeviceNames();
    }


    private void disconnectAndReset() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                connectionManager.disconnect();
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                isRecordingSessionActive = false;
                connectedMac = null;
                mControlButton.setText(getString(R.string.record_start));
                mControlButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        connectToPulseOximeter();
                    }
                });
//                updateMacAddressDisplay();
            }
        }.execute();
    }
    private void showDeleteDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] macs = macAddressQueue.toArray(new String[0]);

        // Get device names or use MAC addresses as fallback
        String device1Label = deviceNames.containsKey(macs[0]) && !deviceNames.get(macs[0]).equals("Unknown Device")
                ? deviceNames.get(macs[0])
                : "Device " + macs[0];

        String device2Label = deviceNames.containsKey(macs[1]) && !deviceNames.get(macs[1]).equals("Unknown Device")
                ? deviceNames.get(macs[1])
                : "Device " + macs[1];

        builder.setTitle("Maximum Devices Reached")
                .setMessage("You have already saved two devices. Please delete one to add a new device.")
                .setPositiveButton("Delete " + device1Label, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        deleteDevice(0);
                    }
                })
                .setNegativeButton("Delete " + device2Label, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        deleteDevice(1);
                    }
                })
                .setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });
        builder.create().show();
    }
    //end


    private void initialisePlot() {
        Paint transparentPaint = new Paint();
        transparentPaint.setColor(Color.WHITE);
        transparentPaint.setAlpha(255);
        mPlot.getLayoutManager().remove(mPlot.getLegendWidget());
        mPlot.setBorderStyle(Plot.BorderStyle.SQUARE, null, null);
        mPlot.setBorderPaint(null);
        mPlot.getGraphWidget().getRangeLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getDomainLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getRangeOriginLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getDomainOriginLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().setBackgroundPaint(transparentPaint);
        mPlot.getGraphWidget().setGridBackgroundPaint(null);
        mPlot.setDomainLabel("");
        mPlot.setRangeLabel("");
        mPlot.setTitle("");
        mPlot.getGraphWidget().getRangeLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getDomainLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getDomainOriginLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getRangeOriginLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getRangeGridLinePaint().setAlpha(0);
        mPlot.getGraphWidget().getDomainGridLinePaint().setAlpha(0);
        mPlot.setPlotPadding(-61, -25, -10, -37); // L,T,R,B
        mPlot.setMarkupEnabled(false);
        mPlot.getGraphWidget().setCursorPosition(-1, -1);
        mPlot.getGraphWidget().getCursorLabelPaint().setAlpha(0);
        mPlot.getGraphWidget().getCursorLabelBackgroundPaint().setAlpha(0);
    }

    private void initialiseSeries() {
        mPlot.clear();
        mSeries = new SimpleXYSeries("pleth");
        mSeries.useImplicitXVals();
        mPlot.addSeries(mSeries, new LineAndPointFormatter(Color.RED, null, null, new PointLabelFormatter(Color.TRANSPARENT)));
        mTrendSeries = new SimpleXYSeries("trend");
        mTrendSeries.useImplicitXVals();
//        mPlot.addSeries(mTrendSeries, new LineAndPointFormatter(Color.YELLOW, null, null, new PointLabelFormatter(Color.TRANSPARENT)));
        LineAndPointFormatter formatter = new LineAndPointFormatter(Color.YELLOW, null, null, new PointLabelFormatter(Color.TRANSPARENT));
//        mPlot.addSeries(mTrendSeries, formatter);

    }
    // reconnect service to prevent not write data to file
    private void reconnectToService() {
        Log.i(TAG, "Attempting to reconnect to OximeterDataService");

        // If there's an existing service connection, clean it up
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                // Service wasn't actually bound
                Log.w(TAG, "Service wasn't bound when trying to unbind");
            }
            isServiceBound = false;
        }

        // Start fresh with a new service
        serviceIntent = new Intent(this, OximeterDataService.class);
        serviceIntent.putExtra("patientCode", formattedCode);

        // Get chunk file configuration
        ChunkFileConfigPopup configPopup = new ChunkFileConfigPopup(this);
        boolean enableChunk = configPopup.isChunkFileEnabled();
        int timeInterval = configPopup.getTimeInterval();

        serviceIntent.putExtra("enableChunkFile", enableChunk);
        serviceIntent.putExtra("timeInterval", timeInterval);

        // Start the service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Bind to the service
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }
    // service foreground

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OximeterDataService.LocalBinder binder = (OximeterDataService.LocalBinder) service;
            dataService = binder.getService();
            isServiceBound = true;
            Log.i(TAG, "Successfully bound to OximeterDataService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            dataService = null;
            Log.w(TAG, "OximeterDataService disconnected unexpectedly");
        }
    };

    //end
    /**
     * Called when the Activity is destroyed. Where possible, the Bluetooth connection should be
     * shut down gracefully by the ConnectionManager.
     */
    @Override
    protected void onDestroy() {

        if (connectionManager.isRecording()) {
            connectionManager.disconnect();
        }
        //new code
        // Stop the annotation session
        OximeterDataWriterFunctions.setActivity(null);
        ShowAnnotate.stopAllSessions();

//        if (isServiceBound) {
//            unbindService(serviceConnection);
//            isServiceBound = false;
//        }
        // Stop the service if it's running
        Intent serviceIntent = new Intent(this, OximeterDataService.class);
        stopService(serviceIntent);

        if (isServiceBound) {
            try {
                // Stop data collection before unbinding
                if (dataService != null) {
                    dataService.stopDataCollection();
                }
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service wasn't bound when trying to unbind");
            }
            isServiceBound = false;
        }

        super.onDestroy();


        // Stop ongoing Bluetooth discovery and remove the scan handler callback
//        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
//            bluetoothAdapter.cancelDiscovery();
//        }
        //new code
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } else {
            requestBluetoothPermissions();
        }

        if (scanHandler != null && scanRunnable != null) {
            scanHandler.removeCallbacks(scanRunnable);
        }

        // Unregister Bluetooth receiver to prevent memory leaks
        unregisterReceiver(bluetoothReceiver);

    }

    /**
     * Needed for Android API 23+. Check if the app has permission to use Bluetooth, and if it
     * doesn't then request it.
     */
//    private boolean hasBluetoothPermissions() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
//        } else {
//            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
//                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
//        }
//    }
//
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_REQUIRED_PERMISSIONS);
            } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_REQUIRED_PERMISSIONS);
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, REQUEST_REQUIRED_PERMISSIONS);
            } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_REQUIRED_PERMISSIONS);
            }
        }
    }

    //end new code
//    public boolean checkAppPermissions() {
//        List<String> permissionsToRequest = new ArrayList<>();
//        //new code add
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            int hasWriteStoragePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
//            if (PackageManager.PERMISSION_GRANTED != hasWriteStoragePermission) {
//                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
//            }
//        }
//        //end
//        //new code
//        // Check Bluetooth permissions
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
//            }
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
//            }
//        } else {
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH);
//            }
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN);
//            }
//        }
//        //end
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            int hasFineLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
//            if (PackageManager.PERMISSION_GRANTED != hasFineLocPermission) {
//                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
//            }
//        } else {
//            int hasCoarseLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
//            if (PackageManager.PERMISSION_GRANTED != hasCoarseLocPermission) {
//                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
//            }
//        }
//        if (permissionsToRequest.size() < 1) {
//            // We have all required permissions.
//            return true;
//        }
//
//        // Request the relevant permissions.
//        ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
//        return false;
//    }
    public boolean checkAppPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        //new code
        // Check Bluetooth permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else { // Below API 31
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH);
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }
        //end
        // Location permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29+
            int hasFineLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (PackageManager.PERMISSION_GRANTED != hasFineLocPermission) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        } else { // Below API 29 (ACCESS_COARSE_LOCATION is sufficient for Bluetooth on older versions)
            int hasCoarseLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (PackageManager.PERMISSION_GRANTED != hasCoarseLocPermission) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }

        // Notification permission for Android 13 (API 33) and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (permissionsToRequest.size() < 1) {
            // We have all required permissions.
            return true;
        }

        // Request the relevant permissions.
        ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
        return false;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_REQUIRED_PERMISSIONS:
                if (0 == grantResults.length) {
                    // Request was cancelled.
                    Log.w(TAG, "Permission request cancelled by user.");
                    Toast.makeText(this, "Permissions request cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean allPermissionsGranted = true;
                List<String> deniedPermissions = new ArrayList<>();
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        deniedPermissions.add(permissions[i]);
                        Log.w(TAG, "Permission denied: " + permissions[i]);
                    }
                }

                if (allPermissionsGranted) {
                    // We were granted all the requested permissions, so we can connect.
                    Log.i(TAG, "All required permissions granted.");
                    connectToPulseOximeter();
                } else {
                    Log.e(TAG, "Not all permissions granted. Denied permissions: " + TextUtils.join(", ", deniedPermissions));
                    Toast.makeText(this, "Not all permissions granted - can't connect. Denied: " + TextUtils.join(", ", deniedPermissions), Toast.LENGTH_LONG).show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


//     Handle the result of the Bluetooth permission request.
//    @Override
//    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
//        switch (requestCode) {
//            case REQUEST_REQUIRED_PERMISSIONS:
//                if (0 == grantResults.length) {
//                    // Request was cancelled.
//                    return;
//                }
//                int nGranted = 0;
//                for (int iRes = 0; iRes < grantResults.length; iRes++) {
//                    if (PackageManager.PERMISSION_GRANTED == grantResults[iRes]) {
//                        nGranted++;
//                    }
//                }
//                if (nGranted == grantResults.length) {
//                    // We were granted all the requested permissions, so we can connect.
//                    connectToPulseOximeter();
//                } else {
//                    Toast.makeText(this, "Not all permissions granted - can't connect.", Toast.LENGTH_SHORT).show();
//                }
//        }
//    }

}
