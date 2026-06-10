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

/**
 * Implementation thực tế của {@link IDeviceAdapter} — là file DUY NHẤT cần sửa khi đổi phần cứng
 * thiết bị. Lớp này chỉ là wrapper mỏng bọc quanh {@link ConnectionManager} của vendor SDK; các
 * SDK event (data, connect, disconnect...) được đẩy về app qua các message của {@link Handler}.
 */
public class OximeterSDKAdapter implements IDeviceAdapter {
    private static final String TAG = "OximeterSDKAdapter";
    
    private ConnectionManager connectionManager;
    private Context context;
    private Handler handler;
    
    /**
     * Khởi tạo adapter và áp dụng reflection workaround để vô hiệu hoá bộ lọc tên thiết bị
     * hardcoded trong SDK. SDK lọc thiết bị bằng mảng tên cố định (class {@code ...library.a},
     * field {@code d}); ta set {@code names[0] = ""} để mọi thiết bị đều khớp (match ANY device).
     * LƯU Ý: phụ thuộc vào tên obfuscated nội bộ của SDK nên PHẢI kiểm tra lại mỗi khi nâng cấp .aar.
     */
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
    
    /**
     * No-op: dữ liệu không đi qua listener này; tất cả event được chuyển qua {@link Handler}.
     */
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

    /**
     * Chỉ disconnect khi đang recording, tránh ngắt kết nối thừa khi thiết bị không hoạt động.
     */
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
