package com.smartcare.oximetry.sample.device;

public interface IDeviceAdapter {
    /**
     * Set listener for getting messages from the adapter.
     */
    void setListener(OximeterDataListener listener);

    /**
     * Connect to the default or any available matched oximeter.
     */
    boolean connect();

    /**
     * Connect to a specific oximeter MAC address.
     */
    boolean connect(String macAddress);

    /**
     * Disconnect the currently connected device.
     */
    void disconnect();

    /**
     * Check if currently recording data.
     */
    boolean isRecording();

    /**
     * Get MAC address of the device being connected.
     */
    String getMacAddress();

    String getOximeterModelNumber();

    int getSampleRate();

    /**
     * Clean up resources, threads and destroy connections.
     */
    void onDestroy();
    
    void startRecording();
}
