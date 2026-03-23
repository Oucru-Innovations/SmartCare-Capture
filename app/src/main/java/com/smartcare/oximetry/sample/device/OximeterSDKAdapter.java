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
