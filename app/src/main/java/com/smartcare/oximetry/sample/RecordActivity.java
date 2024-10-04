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
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.MacAddress;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.smartcare.oximetry.library.ConnectionManager;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

//new import
import com.opencsv.CSVWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.os.Environment;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;


import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
/**
 * This Activity is responsible for recording, displaying and uploading a physiological signal
 * using a SmartCare Analytics Ltd. Bluetooth pulse oximeter.
 */
public class RecordActivity extends Activity {
    private static final String TAG = "RecordActivity";
    private static final double UI_BUFFER_SIZE_SAMPLES = 1000;
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
    private Queue<String> macAddressQueue;
    private SharedPreferences sharedPreferences;
    public static boolean isRecordingSessionActive =false;
    private TextView device1MacTextView;
    private TextView device2MacTextView;
    private Button device1DeleteButton;
    private Button device2DeleteButton;
    private String connectedMac;
    private boolean isQueueFull = false;
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
                    //new files
                    long latestRedAdc = data.getLong(ConnectionManager.KEY_DATA_REDADC);
                    long latestIrAdc = data.getLong(ConnectionManager.KEY_DATA_IRADC);
                    double latestPerfusionIndexValue = data.getDouble(ConnectionManager.KEY_DATA_PERFUSIONINDEX);
                    long latestPackageReceived = data.getLong(ConnectionManager.KEY_DATA_PACKAGE_RECEIVED);

                    int startTime= data.getInt(ConnectionManager.KEY_DATA_START_TIME);
                    //end




                    //double latestPerfusionIndexValue = -1;

                    //new code add
                    Log.d("RecordActivity", String.format(Locale.US,
                            "Pleth Value: %d, Heart Rate: %d, SpO2: %.2f, Device ID: %d, Battery: %.2f, Counter: %d, SpO2 Status: %d, Red ADC: %d, IR ADC: %d, Perfusion Index: %.2f, Package Received: %d, Start Time: %d",
                            latestPlethValue, latestHeartRateValue, latestSpO2Value, deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status, latestRedAdc, latestIrAdc, latestPerfusionIndexValue, latestPackageReceived, startTime
                    ));
                    Log.d("RecordActivity", "MAC address: "+MacAddress);
                    // After retrieving all the data from the packet
                    Log.d("RecordActivity", "Preparing to write CSV data");
//

                    long timestamp = System.currentTimeMillis();
                    try {
//                        target.writeDataToCSV(latestPlethValue, latestHeartRateValue, latestSpO2Value, deviceId, latestBatteryValue, latestCounterValue, latestSpO2Status,timestamp);
                        if (!isRecordingSessionActive) {
                            OximeterDataWriterFunctions.startNewRecording(latestPackageReceived);
                            isRecordingSessionActive = true;
                        }
//                        OximeterDataWriterFunctions.startNewRecording(latestPackageReceived);
                        OximeterDataWriterFunctions.writeDataPoint(
                                latestSpO2Value,
                                latestSpO2Status,
                                latestRedAdc,
                                latestBatteryValue,
                                latestHeartRateValue,
                                latestIrAdc,
                                latestPerfusionIndexValue,
                                latestCounterValue,
                                deviceId,
                                latestPackageReceived,
                                latestPlethValue,
                                timestamp
                        );
                        Log.i("RecordActivity", "CSV data written successfully");
                        //
//                        JSONObject jsonData = new JSONObject();
//                        jsonData.put("spo2Val", latestSpO2Value);
//                        jsonData.put("spo2Status", latestSpO2Status);
//                        jsonData.put("redAdc", latestRedAdc);
//                        jsonData.put("battery", latestBatteryValue);
//                        jsonData.put("hrVal", latestHeartRateValue);
//                        jsonData.put("irAdc", latestIrAdc);
//                        jsonData.put("perfusionIndex", latestPerfusionIndexValue);
//                        jsonData.put("counter", latestCounterValue);
//                        jsonData.put("deviceId", deviceId);
//                        jsonData.put("packageReceived", latestPackageReceived);
//                        jsonData.put("timeStamp", timestamp);
//                        jsonData.put("plethVal", latestPlethValue);
//                        RabbitMQProducer.sendMessage(jsonData);
//                        target.sendDataToWebSocket(data);
                        // Send data to RabbitMQ queue

                    } catch (Exception e) {
                        Log.e("RecordActivity", "Error writing CSV data", e);
                    }

                    //end


                    // The KEY_DATA_PERFUSIONINDEX data contains the blood perfusion index value.
                    // This is only available from the SmartCare BM2000A oximeter.
                    if (data.containsKey(ConnectionManager.KEY_DATA_PERFUSIONINDEX)) {
                        latestPerfusionIndexValue = data.getDouble(ConnectionManager.KEY_DATA_PERFUSIONINDEX);
                    }

//                    boolean seriesIsFull = target.mSeries.size() > UI_BUFFER_SIZE_SAMPLES;
//                    if (seriesIsFull) {
//                        target.mSeries.removeFirst();
//                    }
//                    target.mSeries.addLast(null, latestPlethValue);
//                    if (seriesIsFull) {
//                        target.mPlot.redraw();
//                    }
//                    target.mHrDisplay.setText(String.valueOf(latestHeartRateValue));
//                    target.mSpo2Display.setText(String.valueOf((int) latestSpO2Value));
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
                    //new
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

                    //
                    break;
                case ConnectionManager.MESSAGE_RECORDING_ENDED:

                    long totalMessagesSent = RabbitMQProducer.getTotalMessagesSent();

                    // Create and show the Toast

//                    Toast.makeText(target, "Disconnected. " +totalMessagesSent +" messages sent", Toast.LENGTH_SHORT).show();
                    Toast.makeText(target, "Disconnected from oximetry", Toast.LENGTH_SHORT).show();
                    RabbitMQProducer.resetMessageCount() ;
                    //
                    target.connectedMac = null;
                    target.updateMacAddressDisplay();
                    // Device has disconnected.
                    target.mControlButton.setText(target.getString(R.string.record_start));
                    target.mControlButton.setEnabled(true);
                    target.mControlButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            target.connectToPulseOximeter();
                        }
                    });
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
                Log.d(TAG, "Connector: Successfully connected to MAC: " + connectedMac);
            }
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
                        if (!isQueueFull){
                        connectToPulseOximeter();}
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
            isRecordingSessionActive = false;
            connectedMac = null;
            updateMacAddressDisplay();
        }
    }

    //new class
    public static class OximeterDataWriterFunctions {
        private static final String FOLDER_NAME = "OximeterData";
        private static File currentFile;
        private static CSVWriter currentWriter;
        private static long startTimestamp;

        public static void startNewRecording(long packageReceivedTimestamp) {
            closeCurrentWriter();
            startTimestamp = packageReceivedTimestamp;
            currentFile = createFile(packageReceivedTimestamp);
            try {
                currentWriter = new CSVWriter(new FileWriter(currentFile));
                writeHeader(currentWriter);
                Log.i("OximeterDataWriter", "New recording started: " + currentFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e("OximeterDataWriter", "Error creating new CSV file", e);
                currentWriter = null;
            }
        }

        public static void writeDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                                          int hrVal, long irAdc, double perfusionIndex, int counter,
                                          int deviceId, long packageReceived, int plethVal, long timestamp) {
            if (currentWriter == null) {
                Log.e("OximeterDataWriter", "No active recording session");
                return;
            }

            String[] data = {
//                    String.valueOf(packageReceived - startTimestamp),
                    String.valueOf(packageReceived),
                    String.valueOf(timestamp),
                    String.valueOf(counter),
                    String.valueOf(deviceId),
                    String.valueOf(hrVal),
                    String.valueOf((int)spo2Val),
                    String.valueOf(spo2Status),
                    String.valueOf(plethVal),
//                    String.valueOf((int)convertBatteryToPercentage(battery)),
                    String.valueOf(battery),
                    String.valueOf(redAdc),
                    String.valueOf(irAdc),
                    String.valueOf(perfusionIndex)
            };
            currentWriter.writeNext(data);
            Log.i("OximeterDataWriter", "File saved at: " + currentFile.getAbsolutePath());
        }

        public static void endRecording() {
            closeCurrentWriter();
            Log.i("OximeterDataWriter", "Recording ended");
        }

        private static File createFile(long timestamp) {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String fileName = generateFileName(timestamp);
            return new File(directory, fileName);
        }

        private static void writeHeader(CSVWriter writer) {
            String[] header = {"TIMESTAMP_DV","TIMESTAMP_APP", "COUNTER", "DEVICE_ID", "PULSE_BPM", "SPO2_PCT",
                    "SPO2_STATUS", "PLETH", "BATTERY_PCT", "RED_ADC", "IR_ADC", "PERFUSION_INDEX"};
            writer.writeNext(header);
        }

        private static void closeCurrentWriter() {
            if (currentWriter != null) {
                try {
                    currentWriter.close();
                } catch (IOException e) {
                    Log.e("OximeterDataWriter", "Error closing CSV writer", e);
                }
                currentWriter = null;
            }
        }
//
        private static String generateFileName(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS", Locale.US);
            sdf.setTimeZone(TimeZone.getDefault());
            Date date = new Date(timestamp);
            String formattedDate = sdf.format(date);

            TimeZone tz = TimeZone.getDefault();
            int offsetMillis = tz.getOffset(timestamp);
            int offsetHours = offsetMillis / (60 * 60 * 1000);
            int offsetMinutes = Math.abs((offsetMillis / (60 * 1000)) % 60);

            return String.format(Locale.US, "%s%+03d%02d.csv", formattedDate, offsetHours, offsetMinutes);
        }

        private static double convertBatteryToPercentage(double rawBatteryValue) {
            return Math.min(Math.max(rawBatteryValue - 3300.0, 0.0) / 850.0, 1.0) * 100.0;
        }
    }
    //end
    //new code add

    //
    //new code
    public static class RabbitMQProducer {
        private static final String TAG = "RabbitMQProducer";
        private static final String RABBITMQ_HOST = "10.232.79.154"; // Your RabbitMQ server IP
        private static final int RABBITMQ_PORT = 5672;
        private static final String QUEUE_NAME = "device_data_queue";

        private static Connection connection;
        private static Channel channel;
        private static AtomicLong messagesSent = new AtomicLong(0);

        public static void initialize() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "Starting RabbitMQ Producer...");
                    try {
                        createChannel();
                    } catch (IOException | TimeoutException e) {
                        Log.e(TAG, "Failed to initialize RabbitMQ Producer: " + e.getMessage());
                    }
                }
            }).start();
        }

        private static void createChannel() throws IOException, TimeoutException {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBITMQ_HOST);
            factory.setPort(RABBITMQ_PORT);

            connection = factory.newConnection();
            channel = connection.createChannel();

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("x-message-ttl", 60000);  // Message TTL in milliseconds (60 seconds)
            arguments.put("x-max-length", 10000);   // Max number of messages in the queue

            channel.queueDeclare(QUEUE_NAME, true, false, false, arguments);
            Log.i(TAG, "Queue '" + QUEUE_NAME + "' declared successfully.");
        }

        public static void sendMessage(final JSONObject message) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (channel == null || !channel.isOpen()) {
                            createChannel();
                        }
                        String messageBody = message.toString();
                        channel.basicPublish("", QUEUE_NAME, null, messageBody.getBytes(StandardCharsets.UTF_8));
                        long totalSent = messagesSent.incrementAndGet(); // Increment the counter
                        Log.i(TAG, "Sent message: " + messageBody + ". \nTotal messages sent: " + totalSent);
                    } catch (IOException | TimeoutException e) {
                        Log.e(TAG, "Failed to send message: " + e.getMessage());
                    }
                }
            }).start();
        }

        public static void close() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (channel != null && channel.isOpen()) {
                            channel.close();
                        }
                        if (connection != null && connection.isOpen()) {
                            connection.close();
                        }
                        Log.i(TAG, "RabbitMQ connection closed. Total messages sent: " + messagesSent.get());
                    } catch (IOException | TimeoutException e) {
                        Log.e(TAG, "Error closing RabbitMQ connection: " + e.getMessage());
                    }
                }
            }).start();
        }

        public static long getTotalMessagesSent() {
            return messagesSent.get();
        }

        public static void resetMessageCount() {
            messagesSent.set(0);
        }
    }

    //end
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
        //new implement

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
        // Initialize SharedPreferences and MAC address queue
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        macAddressQueue = loadMacAddressQueue();

        //initialisePlot();
        //initialiseSeries();
        //new code
//        initWebSocket();
//        RabbitMQProducer.initialize();
//


        // Create new ConnectionManager, which will connect to and manage the pulse oximeter.
        mHandler = new SmartCareHandler(this);
        if (connectionManager == null) {
            connectionManager = new ConnectionManager(this, mHandler);
        }
        //new
        updateMacAddressDisplay();
    }

    private void initialiseViews() {
//        mPlot = (XYPlot) findViewById(R.id.plot);
        mControlButton = (Button) findViewById(R.id.button);
        mHrDisplay = (TextView) findViewById(R.id.hr);
        mSpo2Display = (TextView) findViewById(R.id.spo2);
        mSpo2Label = (TextView) findViewById(R.id.spo2label);
        mSpo2Label.setText(Html.fromHtml("SpO<sub><small>2</small></sub>"));
        //new
        device1MacTextView = (TextView) findViewById(R.id.connectedMac);
        device2MacTextView = (TextView) findViewById(R.id.queuedMac);
        device1DeleteButton = (Button) findViewById(R.id.device1Delete);
        device2DeleteButton = (Button) findViewById(R.id.device2Delete);

        mControlButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isQueueFull){
                    connectToPulseOximeter();}

            }
        });
        //new button
        device1DeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteDevice(0);
            }
        });

        device2DeleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteDevice(1);
            }
        });

    }

    //new view
    private void deleteDevice(int index) {
        if (index < macAddressQueue.size()) {
            String[] macs = macAddressQueue.toArray(new String[0]);
            String macToRemove = macs[index];
            macAddressQueue.remove(macToRemove);
            saveMacAddressQueue();
            updateMacAddressDisplay();

            if (macToRemove.equals(connectedMac)) {
                // If the deleted device was connected, disconnect it
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

    private void updateMacAddressQueue(String newMac) {
        if (macAddressQueue.size() == 2 && !macAddressQueue.contains(newMac)) {
            // Disconnect and reset to original state
            disconnectAndReset();
            // Show dialog to request deletion of a device
            showDeleteDeviceDialog();
            return;
        }
        if (!macAddressQueue.contains(newMac)) {
            macAddressQueue.offer(newMac);
        }
        connectedMac = newMac;
        saveMacAddressQueue();
        updateMacAddressDisplay();
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
                updateMacAddressDisplay();
            }
        }.execute();
    }
    private void showDeleteDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Maximum Devices Reached")
                .setMessage("You have already saved two devices. Please delete one to add a new device.")
                .setPositiveButton("Delete Device 1", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        deleteDevice(0);
                    }
                })
                .setNegativeButton("Delete Device 2", new DialogInterface.OnClickListener() {
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
        //new code

        RabbitMQProducer.close();
//        closeConnection();
//
        //
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