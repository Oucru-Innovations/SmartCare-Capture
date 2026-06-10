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

/**
 * Tiện ích auto-connect/auto-reconnect Bluetooth (LEGACY/UNUSED). Hiện KHÔNG còn dùng vì
 * RecordActivity tự cài đặt cơ chế scan/reconnect riêng. Giữ lại để tham khảo.
 */
public class BluetoothAutoConnector {
    private static final String TAG = "BluetoothAutoConnector";
    private static final long SCAN_PERIOD = 2000; // Reduced from 10s to 2s for faster reconnection
    private static final String PREFS_NAME = "AutoConnectorPreferences";
    private static final String KEY_BLOCKED_MACS = "blocked_macs";
    // Standard SPP UUID - this is commonly used for Bluetooth medical devices
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private volatile boolean shouldPauseAutoReconnect = false;
    private static final long PAUSE_RECONNECT_DURATION = 3000; // 3 seconds pause
    
    // Retry mechanism variables
    private int retryCount = 0;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 500;

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

    private boolean isReconnecting = false;


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
                    if (device != null) {
                        String disconnectedMac = device.getAddress();
                        if (disconnectedMac != null && disconnectedMac.equals(currentMacAddress)) {
                            isConnected = false;
                            currentMacAddress = null;

                            if (!blockedMacs.contains(disconnectedMac)) {
                                // Implement retry mechanism
                                if (retryCount < MAX_RETRY_ATTEMPTS) {
                                    retryCount++;
                                    Log.i(TAG, "Attempted reconnection #" + retryCount + " for device: " + disconnectedMac);

                                    scanHandler.postDelayed(() -> {
                                        if (!isConnected && !shouldPauseAutoReconnect && !isReconnecting) {

                                            isReconnecting = true;
                                            Log.d(TAG, "Try direct reconnect to: " + disconnectedMac);
                                            callback.connectToDevice(disconnectedMac);
                                        }
                                    }, RETRY_DELAY_MS);
                                } else {
                                    Log.i(TAG, "Max retry attempts reached for device: " + disconnectedMac + ". Stopping reconnection attempts.");
                                    retryCount = 0; // Reset retry count for next connection
                                }
                            } else {
                                // If device is blocked, reset retry count
                                retryCount = 0;
                            }
                            
                            Log.i(TAG, "Connected device disconnected: " + disconnectedMac);
                        } else {
                            Log.d(TAG, "Other device disconnected (not our device): " + disconnectedMac);
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    if (device != null) {
                        String connectedMac = device.getAddress();
                        
                        if (macAddressQueue.contains(connectedMac) || connectedMac.equals(currentMacAddress)) {
                            isConnected = true;
                            isReconnecting = false;

                            currentMacAddress = connectedMac;
                            stopDeviceScan();
                            retryCount = 0; // Reset retry count on successful connection
                            setHighPriorityConnection(device);
                            
                            Log.i(TAG, "Our device connected: " + connectedMac);
                        } else {
                            Log.d(TAG, "Other device connected (not ours): " + connectedMac);
                        }
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
            // Do not open a second RFCOMM socket here — scolib ConnectionManager already owns SPP.
            // A parallel socket caused frequent BLE drops on older SmartCare devices.
        }
    }

    /**
     * Reset trạng thái (pause flag, retry count, blocked MACs) và bắt đầu auto-connect scan.
     */
    public void initialize() {
        shouldPauseAutoReconnect = false;
        retryCount = 0; // Reset retry count on initialization
        clearBlockedMacs();
        startAutoConnect();
    }
    private void startAutoConnect() {
        if (!isConnected && !shouldPauseAutoReconnect) {
            startDeviceScan();
        }
    }
    /**
     * Ngắt kết nối theo chủ ý người dùng: thêm MAC vào blocked list để auto-reconnect không tự nối
     * lại, đóng socket và tạm dừng reconnect một khoảng trước khi quét tiếp.
     */
    public void manualDisconnect(String macAddress) {
        blockedMacs.add(macAddress);
        saveBlockedMacs();
        currentMacAddress = null;
        isConnected = false;

        if (activeSocket != null) {
            try {
                activeSocket.close();
                activeSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket: " + e.getMessage());
            }
        }
        pauseAutoReconnect();
        
        startAutoConnect();
    }

    /**
     * Kết nối theo chủ ý người dùng tới một MAC cụ thể: bỏ block, dừng scan và uỷ quyền việc kết nối
     * cho callback.
     */
    public void manualConnect(String macAddress) {
        shouldPauseAutoReconnect = false; // Reset pause flag
        retryCount = 0; // Reset retry count when manually connecting
        clearBlockedStatus(macAddress); // Remove from block list
        stopDeviceScan(); // Stop any ongoing scan
        currentMacAddress = macAddress;
        callback.connectToDevice(macAddress);
    }
    

    private void pauseAutoReconnect() {
        shouldPauseAutoReconnect = true;
        scanHandler.postDelayed(() -> shouldPauseAutoReconnect = false, PAUSE_RECONNECT_DURATION);
    }

    private final BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            String newMac = device.getAddress();

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
                scanHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopDeviceScan();

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
    // Public method to allow external apps to signal potential interference
    public void requestPauseAutoReconnect(long durationMs) {
        shouldPauseAutoReconnect = true;
        scanHandler.postDelayed(() -> shouldPauseAutoReconnect = false, durationMs);
    }
        
    /**
     * Giải phóng tài nguyên: dừng scan, xoá callback, đóng socket và huỷ đăng ký receiver.
     */
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

        shouldPauseAutoReconnect = false;
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