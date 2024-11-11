package com.smartcare.oximetry.sample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class BluetoothAutoConnector {
    private static final long SCAN_PERIOD = 10000;
    private static final String PREFS_NAME = "AutoConnectorPreferences";
    private static final String KEY_BLOCKED_MACS = "blocked_macs";

    private final Context context;
    private final Handler scanHandler = new Handler();
    private final SharedPreferences preferences;
    private final Set<String> blockedMacs;
    private boolean isScanning = false;
    private boolean isConnected = false;
    private final Queue<String> macAddressQueue;
    private final AutoConnectCallback callback;
    private String currentMacAddress;
    private BroadcastReceiver connectionStateReceiver;

    public interface AutoConnectCallback {
        void onDeviceFound(String macAddress);
        boolean checkAppPermissions();
        void connectToDevice(String macAddress);
    }

    public BluetoothAutoConnector(Context context, Queue<String> macAddressQueue, AutoConnectCallback callback) {
        this.context = context;
        this.macAddressQueue = macAddressQueue;
        this.callback = callback;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.blockedMacs = new HashSet<>(preferences.getStringSet(KEY_BLOCKED_MACS, new HashSet<>()));
        setupConnectionStateReceiver();
        initialize();
    }

    // New method to set up the connection state receiver
    private void setupConnectionStateReceiver() {
        // Initialize the receiver
        connectionStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    isConnected = false;
                    if (device != null) {
                        currentMacAddress = null;
                        // Start scanning for other devices if this wasn't a manual disconnect
                        if (!blockedMacs.contains(device.getAddress())) {
                            startAutoConnect();
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    if (device != null) {
                        isConnected = true;
                        currentMacAddress = device.getAddress();
                        stopDeviceScan();
                    }
                }
            }
        };

        // Register the receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(connectionStateReceiver, filter);
    }

    // New method to handle initialization flow
    public void initialize() {
        clearBlockedMacs(); // Clear block list on start
        startAutoConnect(); // Start scanning for first known device
    }

    // New method to handle auto-connect flow
    private void startAutoConnect() {
        if (!isConnected) {
            startDeviceScan();
        }
    }

    // Modified disconnect method for manual disconnect
    public void manualDisconnect(String macAddress) {
        blockedMacs.add(macAddress); // Add to block list
        saveBlockedMacs();
        currentMacAddress = null;
        isConnected = false;
        startAutoConnect(); // Start scanning for other known devices
    }

    // Modified manual connect method
    public void manualConnect(String macAddress) {
        clearBlockedStatus(macAddress); // Remove from block list
        stopDeviceScan(); // Stop any ongoing scan
        currentMacAddress = macAddress;
        callback.connectToDevice(macAddress);
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            String newMac = device.getAddress();
            // Only connect to known devices that aren't blocked
            if (macAddressQueue.contains(newMac) && !blockedMacs.contains(newMac)) {
                stopDeviceScan();
                callback.onDeviceFound(newMac);
                callback.connectToDevice(newMac);
            }
        }
    };

    private void startDeviceScan() {
        if (!isScanning && callback.checkAppPermissions()) {
            isScanning = true;
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.startLeScan(leScanCallback);

                // Stop scan after SCAN_PERIOD
                scanHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopDeviceScan();
                        // Restart scan if not connected
                        if (!isConnected) {
                            startDeviceScan();
                        }
                    }
                }, SCAN_PERIOD);
            }
        }
    }

    private void stopDeviceScan() {
        if (isScanning) {
            isScanning = false;
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter != null) {
                bluetoothAdapter.stopLeScan(leScanCallback);
            }
        }
    }

    private void clearBlockedMacs() {
        blockedMacs.clear();
        saveBlockedMacs();
    }

    private void clearBlockedStatus(String macAddress) {
        if (macAddress != null) {
            blockedMacs.remove(macAddress);
            saveBlockedMacs();
        }
    }

    private void saveBlockedMacs() {
        preferences.edit()
                .putStringSet(KEY_BLOCKED_MACS, blockedMacs)
                .apply();
    }

    public void cleanup() {
        stopDeviceScan();
        scanHandler.removeCallbacksAndMessages(null);
        if (connectionStateReceiver != null) {
            try {
                context.unregisterReceiver(connectionStateReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered
            }
        }
    }
}