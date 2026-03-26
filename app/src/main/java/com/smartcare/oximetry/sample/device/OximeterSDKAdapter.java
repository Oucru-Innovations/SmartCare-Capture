/*
 * Copyright (c) 2018, SmartCare Analytics Ltd.
 * All Rights Reserved.
 * Proprietary and confidential.
 *
 * Unauthorized copying or distribution of these files via any medium is strictly prohibited.
 */
package com.smartcare.oximetry.sample.device;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.smartcare.oximetry.library.ConnectionManager;

public class OximeterSDKAdapter implements IDeviceAdapter {
    private static final String TAG = "OximeterSDKAdapter";
    
    private ConnectionManager connectionManager;
    private Context context;
    private Handler handler;
    
    public OximeterSDKAdapter(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;

        // Inject the new target device name (FC0392) into the SDK's hardcoded device name filter array.
        // We mutate the contents of the final array without changing its reference.
        try {
            Class<?> aClass = Class.forName("com.smartcare.oximetry.library.a");
            java.lang.reflect.Field dField = aClass.getDeclaredField("d");
            dField.setAccessible(true);
            String[] names = (String[]) dField.get(null);
            if (names != null && names.length > 0) {
                names[0] = ""; // Bypass name filter entirely; matches ANY device.
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject custom device name", e);
        }

        this.connectionManager = new ConnectionManager(context, handler);
    }
    
    @Override
    public void setListener(OximeterDataListener listener) {
    }

    @Override
    public boolean connect() {
        if (connectionManager != null) {
            return connectionManager.connect();
        }
        return false;
    }

    @Override
    public boolean connect(String macAddress) {
        if (connectionManager != null) {
            return connectionManager.connect(macAddress);
        }
        return false;
    }

    @Override
    public void disconnect() {
        if (connectionManager != null && connectionManager.isRecording()) {
            connectionManager.disconnect();
        }
    }

    @Override
    public boolean isRecording() {
        return connectionManager != null && connectionManager.isRecording();
    }

    @Override
    public String getMacAddress() {
        return connectionManager != null ? connectionManager.getMacAddress() : null;
    }

    @Override
    public String getOximeterModelNumber() {
        return connectionManager != null ? connectionManager.getOximeterModelNumber() : "Unknown";
    }

    @Override
    public int getSampleRate() {
        return connectionManager != null ? connectionManager.getSampleRate() : 0;
    }

    public void startRecording() {
        if (connectionManager != null) {
            connectionManager.startRecording();
        }
    }

    @Override
    public void onDestroy() {
        if (connectionManager != null) {
            if (connectionManager.isRecording()) {
                connectionManager.disconnect();
            }
            connectionManager = null;
        }
    }
}
