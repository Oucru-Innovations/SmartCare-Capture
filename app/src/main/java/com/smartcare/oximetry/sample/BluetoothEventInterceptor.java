package com.smartcare.oximetry.sample;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Queue;

/**
 * High-priority BroadcastReceiver để intercept Bluetooth events TRƯỚC KHI library nhận được.
 * Receiver này sẽ chặn (abort) các sự kiện disconnect/connect từ thiết bị không phải của app này.
 */
public class BluetoothEventInterceptor {
    private static final String TAG = "BTEventInterceptor";
    private static final String PREFS_NAME = "BluetoothInterceptorPrefs";
    private static final String KEY_CURRENT_MAC = "current_mac_address";
    
    private final Context context;
    private final Queue<String> macAddressQueue;
    private BroadcastReceiver interceptor;
    private String connectedMacAddress;
    private SharedPreferences preferences;
    
    /**
     * Khôi phục MAC của thiết bị mục tiêu từ SharedPreferences (bền qua restart) rồi đăng ký
     * receiver. {@code macAddressQueue} chứa danh sách MAC được phép để nhận diện thiết bị của app.
     */
    public BluetoothEventInterceptor(Context context, Queue<String> macAddressQueue) {
        this.context = context;
        this.macAddressQueue = macAddressQueue;
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.connectedMacAddress = preferences.getString(KEY_CURRENT_MAC, null);
        setupInterceptor();
    }
    
    private void setupInterceptor() {
        interceptor = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                
                if (device == null) {
                    return;
                }
                
                String deviceMac;
                try {
                    deviceMac = device.getAddress();
                } catch (SecurityException e) {
                    Log.w(TAG, "Cannot read Bluetooth device address: " + e.getMessage());
                    return;
                }
                
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    // Check if the device is connected.
                    if (deviceMac != null && !deviceMac.equals(connectedMacAddress)) {
                        // maybe another app is connected to the device.
                        // block the event from going to the library.
                        Log.i(TAG, "Intercepted and BLOCKED disconnect event from other device: " + deviceMac);
                        Log.i(TAG, "Our connected device: " + connectedMacAddress);
                        
                        // Abort broadcast, library will not receive this event.
                        // abortBroadcast() only works with ordered broadcasts
                        try {
                            abortBroadcast();
                            Log.i(TAG, "Successfully aborted broadcast");
                        } catch (Exception e) {
                            Log.w(TAG, "Cannot abort broadcast (not ordered): " + e.getMessage());
                        }
                    } else {
                        // our device disconnected
                        Log.i(TAG, "Our device disconnected: " + deviceMac);
                        connectedMacAddress = null;
                        preferences.edit().remove(KEY_CURRENT_MAC).apply();
                    }
                } 
                else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    Log.d(TAG, "ACL_CONNECTED event received for: " + deviceMac);
                    Log.d(TAG, "Current connectedMacAddress: " + connectedMacAddress);
                    Log.d(TAG, "macAddressQueue contains: " + (macAddressQueue != null ? macAddressQueue.toString() : "null"));
                    
                    if (connectedMacAddress != null && !deviceMac.equals(connectedMacAddress)) {
                        // block connect from different device
                        Log.w(TAG, "Already connected to: " + connectedMacAddress);
                        Log.w(TAG, "BLOCKING connect from different device: " + deviceMac);
                        try {
                            abortBroadcast();
                            Log.i(TAG, "Successfully aborted connect broadcast");
                        } catch (Exception e) {
                            Log.w(TAG, "Cannot abort connect broadcast: " + e.getMessage());
                        }
                    } else if (connectedMacAddress == null) {
                        
                        if (macAddressQueue != null && macAddressQueue.contains(deviceMac)) {
                            Log.i(TAG, "First connection - Our device connected: " + deviceMac);
                            connectedMacAddress = deviceMac;
                            preferences.edit().putString(KEY_CURRENT_MAC, deviceMac).apply();
                        } else {

                            Log.i(TAG, "Unknown device connected (not in queue), allowing: " + deviceMac);
                        }
                    } else {
                        // allow reconnect same device
                        Log.i(TAG, "Same device reconnected: " + deviceMac);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        
        context.registerReceiver(interceptor, filter);
        Log.i(TAG, "Bluetooth Event Interceptor registered with SYSTEM_HIGH_PRIORITY");
    }
    
    /**
     * Cập nhật MAC của thiết bị đang kết nối (persist vào prefs) để interceptor biết event nào là
     * của thiết bị mục tiêu; truyền {@code null} để xoá khi đã ngắt kết nối.
     */
    //Update Mac Address of connected device
    public void setConnectedMacAddress(String macAddress) {
        this.connectedMacAddress = macAddress;
        if (macAddress != null) {
            preferences.edit().putString(KEY_CURRENT_MAC, macAddress).apply();
            Log.i(TAG, "Updated connected MAC address: " + macAddress);
        } else {
            preferences.edit().remove(KEY_CURRENT_MAC).apply();
            Log.i(TAG, "Cleared connected MAC address");
        }
    }
    
    // Get Mac Address of connected device
    public String getConnectedMacAddress() {
        return connectedMacAddress;
    }
    
    /**
     * Huỷ đăng ký BroadcastReceiver để tránh leak; BẮT BUỘC gọi khi Activity bị destroy.
     */
    // Cleanup - must call when Activity is destroyed
    public void cleanup() {
        if (interceptor != null) {
            try {
                context.unregisterReceiver(interceptor);
                Log.i(TAG, "Bluetooth Event Interceptor unregistered");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Interceptor was not registered");
            }
        }
    }
}
