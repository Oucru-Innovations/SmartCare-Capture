package com.smartcare.oximetry.sample.device;

public interface OximeterDataListener {
    void onDataReceived(OximeterDataPacket data);
    
    void onDeviceConnected(String deviceModel, int sampleRate, long startTime, String macAddress);
    void onDeviceDisconnected();
    void onConnectionLost();
    
    void onDeviceNotFound();
    void onBluetoothOff();
    void onBluetoothUnavailable();
    void onPermissionRequired(boolean isFineLocation);
    void onUnknownDevice();
    void onNoDataAvailable();
}
