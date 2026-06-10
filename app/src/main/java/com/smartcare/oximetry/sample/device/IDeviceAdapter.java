package com.smartcare.oximetry.sample.device;

/**
 * Interface trừu tượng hoá vòng đời kết nối oximeter (connect/disconnect/record) để khi đổi
 * vendor SDK chỉ cần thay đổi tại một nơi (implementation), phần còn lại của app không bị ảnh hưởng.
 */
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

    /**
     * Lấy model number của oximeter đang kết nối.
     */
    String getOximeterModelNumber();

    /**
     * Lấy sample rate (Hz) mà thiết bị đang stream dữ liệu.
     */
    int getSampleRate();

    /**
     * Clean up resources, threads and destroy connections.
     */
    void onDestroy();
    
    /**
     * Bắt đầu nhận/recording dữ liệu từ thiết bị đã kết nối.
     */
    void startRecording();
}
