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



import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.core.app.ActivityCompat;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.smartcare.oximetry.library.ConnectionManager;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//new import
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;
import java.util.Locale;

/**
 * This Activity is responsible for recording, displaying and uploading a physiological signal
 * using a SmartCare Analytics Ltd. Bluetooth pulse oximeter.
 */
public class RecordActivity extends Activity {

    private static final double UI_BUFFER_SIZE_SAMPLES = 1000;
    private static final int REQUEST_REQUIRED_PERMISSIONS = 17;

    private ConnectionManager connectionManager;
    private XYPlot mPlot;
    private Button mControlButton;
    private TextView mHrDisplay;
    private TextView mSpo2Display;
    private TextView mSpo2Label;
    private SimpleXYSeries mSeries;
    private SmartCareHandler mHandler;
    //newcode

    //end
    /**
     * This Handler is responsible for handling messages from the ConnectionManager which may
     * contain data or error messages.
     */
    private static class SmartCareHandler extends Handler {

        // A weak reference won't suffer from memory leaks, which might otherwise occur with static
        // references to the activity context.
        private final WeakReference<RecordActivity> mmTarget;

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

                    double latestPerfusionIndexValue = -1;

                    //new code add
                    Log.d("RecordActivity", String.format(Locale.US,
                            "Received data:" +
                                    "\nPleth Value: %d" +
                                    "\nHeart Rate: %d" +
                                    "\nSpO2: %.2f" +
                                    "\nDevice ID: %d" +
                                    "\nBattery: %.2f" +
                                    "\nCounter: %d" +
                                    "\nSpO2 Status: %d",
                            latestPlethValue, latestHeartRateValue, latestSpO2Value,
                            deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status
                    ));
                    // After retrieving all the data from the packet
                    Log.d("RecordActivity", "Preparing to write CSV data");
                    long timestamp = System.currentTimeMillis();
                    try {
                        target.writeDataToCSV(latestPlethValue, latestHeartRateValue, latestSpO2Value, deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status,timestamp);
                        Log.i("RecordActivity", "CSV data written successfully");
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
                    if (seriesIsFull) {
                        target.mPlot.redraw();
                    }
                    target.mHrDisplay.setText(String.valueOf(latestHeartRateValue));
                    target.mSpo2Display.setText(String.valueOf((int) latestSpO2Value));
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
                case ConnectionManager.MESSAGE_CONNECTION_LOST:
                    Toast.makeText(target, "Bluetooth connection lost", Toast.LENGTH_SHORT).show();
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
                    break;
                case ConnectionManager.MESSAGE_RECORDING_ENDED:
                    Toast.makeText(target, "Disconnected from oximeter", Toast.LENGTH_SHORT).show();
                    // Device has disconnected.
                    target.mControlButton.setText(target.getString(R.string.record_start));
                    target.mControlButton.setEnabled(true);
                    target.mControlButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            target.connectToPulseOximeter();
                        }
                    });
                    target.mPlot.clear();
                    target.mPlot.redraw();
                    break;
                case ConnectionManager.MESSAGE_CLOCATION_PERMISSION_REQUIRED:
                    Toast.makeText(target, "Coarse location permission missing", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_FLOCATION_PERMISSION_REQUIRED:
                    Toast.makeText(target, "Fine location permission missing", Toast.LENGTH_SHORT).show();
                    break;
                case ConnectionManager.MESSAGE_DEVICE_NOT_FOUND:
                    Toast.makeText(target, "No oximeter device found", Toast.LENGTH_SHORT).show();
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

        @Override
        protected void onPreExecute() {
            Log.i(getString(R.string.app_name), "Connector task started");
            mControlButton.setText(getString(R.string.record_stop));
            mControlButton.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            boolean success = connectionManager.connect();
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
            mControlButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopDisconnectExit();
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
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Log.i(getString(R.string.app_name), "Disconnector task finished");
        }
    }

    //new code add
    private void writeDataToCSV(int plethValue, int heartRate, double spO2, int deviceId, double batteryValue, int counterValue, int spO2Status,long timestamp) {
        File directory;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // For Android 10 and above, use Downloads directory
            directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else {
            // For older versions, use a folder in external storage
            directory = new File(Environment.getExternalStorageDirectory(), "OximetryData");
        }
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String fileName = "oximeter_data.csv";
        File file = new File(directory, fileName);
        CSVWriter writer;
        // File header
        String[] header = {"Timestamp", "Pleth Value", "Heart Rate", "SpO2", "Device ID", "Battery Value", "Counter Value", "SpO2 Status"};
        try {
            // If file doesn't exist, create it and add header
            if (!file.exists()) {
                writer = new CSVWriter(new FileWriter(file));
                writer.writeNext(header);
            } else {
                // Else, open the file in append mode
                writer = new CSVWriter(new FileWriter(file, true));
            }
            // Write the data
            String[] data = {String.valueOf(timestamp), String.valueOf(plethValue),
                    String.valueOf(heartRate), String.valueOf(spO2), String.valueOf(deviceId), String.valueOf(batteryValue), String.valueOf(counterValue), String.valueOf(spO2Status)};
            writer.writeNext(data);
            writer.close();
            Log.i("RecordActivity", "CSV file saved at: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e("RecordActivity", "Error writing CSV file", e);
        }
    }
    //


    /**
     * Initialises the connection to the pulse oximeter by executing the Connector task.
     */
    private void connectToPulseOximeter() {
        boolean hasPermission = checkAppPermissions();
        if (hasPermission) {
            new Connector().execute();
        } else {
            // Didn't have one or more required permissions, so abort connection.
        }
    }

    /**
     * Sends a message to the ConnectionManager to stop recording. We will know recording has
     * finished and resources are tidied up when we receive the relevant message in the Handler.
     */
    private void stopDisconnectExit() {
        new Disconnector().execute();
    }

    /**
     * Called when the activity is first created. Initialises the private fields of the Activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialise the UI.
        initialiseViews();
        initialisePlot();
        initialiseSeries();

        // Create new ConnectionManager, which will connect to and manage the pulse oximeter.
        mHandler = new SmartCareHandler(this);
        if (connectionManager == null) {
            connectionManager = new ConnectionManager(this, mHandler);
        }
    }

    private void initialiseViews() {
        mPlot = (XYPlot) findViewById(R.id.plot);
        mControlButton = (Button) findViewById(R.id.button);
        mHrDisplay = (TextView) findViewById(R.id.hr);
        mSpo2Display = (TextView) findViewById(R.id.spo2);
        mSpo2Label = (TextView) findViewById(R.id.spo2label);
        mSpo2Label.setText(Html.fromHtml("SpO<sub><small>2</small></sub>"));

        mControlButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToPulseOximeter();
            }
        });
    }

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
    }

    /**
     * Called when the Activity is destroyed. Where possible, the Bluetooth connection should be
     * shut down gracefully by the ConnectionManager.
     */
    @Override
    protected void onDestroy() {
        if (connectionManager.isRecording()) {
            connectionManager.disconnect();
        }
        super.onDestroy();
    }

    /**
     * Needed for Android API 23+. Check if the app has permission to use Bluetooth, and if it
     * doesn't then request it.
     */
    private boolean checkAppPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        //new code add
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int hasWriteStoragePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (PackageManager.PERMISSION_GRANTED != hasWriteStoragePermission) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        //end
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int hasFineLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (PackageManager.PERMISSION_GRANTED != hasFineLocPermission) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        } else {
            int hasCoarseLocPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (PackageManager.PERMISSION_GRANTED != hasCoarseLocPermission) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
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


    // Handle the result of the Bluetooth permission request.
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_REQUIRED_PERMISSIONS:
                if (0 == grantResults.length) {
                    // Request was cancelled.
                    return;
                }
                int nGranted = 0;
                for (int iRes = 0; iRes < grantResults.length; iRes++) {
                    if (PackageManager.PERMISSION_GRANTED == grantResults[iRes]) {
                        nGranted++;
                    }
                }
                if (nGranted == grantResults.length) {
                    // We were granted all the requested permissions, so we can connect.
                    connectToPulseOximeter();
                } else {
                    Toast.makeText(this, "Not all permissions granted - can't connect.", Toast.LENGTH_SHORT).show();
                }
        }
    }
}