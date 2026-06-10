package com.smartcare.oximetry.sample.device;

/**
 * Callback contract (legacy) cho các sự kiện oximeter. Hiện KHÔNG được implement/dùng: đã được
 * thay thế bằng cơ chế {@code ConnectionManager} + {@link android.os.Handler}.
 */
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
