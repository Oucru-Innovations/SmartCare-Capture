package com.smartcare.oximetry.sample;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class BluetoothAutoConnector {
    private static final String TAG = "BluetoothAutoConnector";
    private static final long SCAN_PERIOD = 10000;
    private static final String PREFS_NAME = "AutoConnectorPreferences";
    private static final String KEY_BLOCKED_MACS = "blocked_macs";
    // Standard SPP UUID - this is commonly used for Bluetooth medical devices
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

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
    private BluetoothSocket activeSocket;

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

                        // Try setting higher connection priority
                        setHighPriorityConnection(device);
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

    /**
     * Set high priority for the Bluetooth connection using multiple approaches to ensure compatibility
     * across different Android versions and manufacturer implementations.
     */
    private void setHighPriorityConnection(BluetoothDevice device) {
        if (device == null) return;

        // Method 1: Using reflection to call setConnectionPriority on the BluetoothSocket
        try {
            // First try to get bond state - if not bonded, try to create bond
//            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
//                Log.d(TAG, "Device not bonded, attempting to create bond");
//                Method createBondMethod = device.getClass().getMethod("createBond");
//                createBondMethod.invoke(device);
//            }
            if (!hasRequiredPermissions()) {
                Log.e(TAG, "Missing required Bluetooth permissions");
                return;
            }

            try {
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Device not bonded, attempting to create bond");
                    Method createBondMethod = device.getClass().getMethod("createBond");
                    createBondMethod.invoke(device);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception when checking bond state: " + e.getMessage());
                return;
            }

            // Try to set connection priority using reflection
            Method setPriorityMethod = device.getClass().getMethod("setConnectionPriority", int.class);
            setPriorityMethod.invoke(device, 1); // 1 = HIGH
            Log.d(TAG, "Successfully requested high priority connection");
        } catch (Exception e) {
            Log.d(TAG, "Error setting connection priority with primary method: " + e.getMessage());

            // Method 2: Try to use lower-level approach with socket options
            new Thread(() -> {
                try {
                    if (!hasRequiredPermissions()) {
                        Log.e(TAG, "Missing required Bluetooth permissions");
                        return;
                    }

//                    BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
//                    socket.connect();
                    BluetoothSocket socket;
                    try {
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    } catch (SecurityException err) {
                        Log.e(TAG, "Security exception creating RFCOMM socket: " + e.getMessage());
                        return;
                    } catch (IOException err) {
                        Log.e(TAG, "IOException creating RFCOMM socket", e);
                        return;
                    }

                    try {
                        socket.connect();
                    } catch (SecurityException err) {
                        Log.e(TAG, "Security exception connecting socket: " + e.getMessage());
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        return;
                    } catch (IOException err) {
                        Log.e(TAG, "Failed to connect: " + e.getMessage());
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        return;
                    }
                    if (socket.isConnected()) {
                        // Use reflection to access & modify socket parameters
                        try {
                            Method setSocketOpt = socket.getClass().getMethod("setSocketOpt", int.class, int.class, int.class, byte[].class);
                            int CONNECTION_PRIORITY = 21; // This is a private connection priority constant
                            int HIGH_PRIORITY = 1;
                            setSocketOpt.invoke(socket, CONNECTION_PRIORITY, 0, HIGH_PRIORITY, null);
                            Log.d(TAG, "Successfully set high priority through socket options");
                        } catch (Exception e2) {
                            Log.d(TAG, "Error setting socket options: " + e2.getMessage());
                        }

                        // Keep socket reference to keep priority settings active
                        if (activeSocket != null) {
                            try {
                                activeSocket.close();
                            } catch (IOException closeEx) {
                                // Ignore
                            }
                        }
                        activeSocket = socket;
                    }
//                } catch (IOException e2) {
//                    Log.e(TAG, "Failed to establish socket connection: " + e2.getMessage());
//                    try {
//                        if (activeSocket != null) activeSocket.close();
//                    } catch (IOException ignored) {}
//                }
                } catch (Exception err) {
                    Log.e(TAG, "Error in socket connection thread", err);
                }
            }).start();
        }
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

        // Close active socket if it exists
        if (activeSocket != null) {
            try {
                activeSocket.close();
                activeSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket: " + e.getMessage());
            }
        }

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
//                bluetoothAdapter.startLeScan(leScanCallback);
                if (!hasRequiredPermissions()) {
                    Log.e(TAG, "Missing required Bluetooth permissions");
                    isScanning = false;
                    return;
                }

                try {
                    bluetoothAdapter.startLeScan(leScanCallback);
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception starting LE scan: " + e.getMessage());
                    isScanning = false;
                    return;
                }
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
//                bluetoothAdapter.stopLeScan(leScanCallback);
                if (!hasRequiredPermissions()) {
                    Log.e(TAG, "Missing required Bluetooth permissions");
                    return;
                }

                try {
                    bluetoothAdapter.stopLeScan(leScanCallback);
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception stopping LE scan: " + e.getMessage());
                }
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

        // Close socket connection if active
        if (activeSocket != null) {
            try {
                activeSocket.close();
                activeSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket: " + e.getMessage());
            }
        }

        if (connectionStateReceiver != null) {
            try {
                context.unregisterReceiver(connectionStateReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered
            }
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED;
        } else {
            return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADMIN) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

}