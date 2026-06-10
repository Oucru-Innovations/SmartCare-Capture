package com.smartcare.oximetry.sample;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Date;

/**
 * Bộ gom dữ liệu (aggregator) thread-safe: gom các raw sample thành từng nhóm
 * {@link #BUFFER_SIZE} (=100), tính median HR/SpO2 cho mỗi nhóm rồi phát ra một dòng CSV
 * {@link ProcessedData} mỗi batch. Giúp giảm số dòng ghi và làm mượt nhiễu tức thời.
 */
public class OximeterDataBuffer {
    /** Số sample gom lại trước khi tính median và ghi một dòng CSV (một batch). */
    private static final int BUFFER_SIZE = 100;
    private static final String TAG = "OximeterDataBuffer";
    // Thread-safe queue to store incoming data
    private final ConcurrentLinkedQueue<DataPoint> dataQueue;
    private final AtomicBoolean isProcessing;

    // Current batch data

    private List<Double> spo2Values;
//    private List<Double> batteryValues;
    private Double firstBatteryValue;
    private List<Integer> hrValues;
    private List<Integer> spo2StatusValues;
    private List<Integer> plethValues;
    private List<Long> redAdcValues;
    private List<Long> irAdcValues;
    private List<Double> perfusionValues;
    private List<Long> timestampValues;
    private String macAddress;


    public OximeterDataBuffer() {
        dataQueue = new ConcurrentLinkedQueue<>();
        isProcessing = new AtomicBoolean(false);
        initializeLists();
    }

    private void initializeLists() {
        spo2Values = new ArrayList<>(BUFFER_SIZE);
//        batteryValues = new ArrayList<>(BUFFER_SIZE);
        firstBatteryValue = null;
        hrValues = new ArrayList<>(BUFFER_SIZE);
        spo2StatusValues = new ArrayList<>(BUFFER_SIZE);
        plethValues = new ArrayList<>(BUFFER_SIZE);
        redAdcValues = new ArrayList<>(BUFFER_SIZE);
        irAdcValues = new ArrayList<>(BUFFER_SIZE);
        perfusionValues = new ArrayList<>(BUFFER_SIZE);
        timestampValues = new ArrayList<>(BUFFER_SIZE);
    }
    /**
     * Quy đổi giá trị pin thô (millivolt) sang phần trăm 0–100, kẹp (clamp) trong khoảng
     * điện áp 3300–4150 mV của pin thiết bị.
     */
    private static double convertBatteryToPercentage(double rawBatteryValue) {
        double percentage = Math.min(Math.max((rawBatteryValue - 3300.0) / 850.0, 0.0), 1.0) * 100.0;
        return Math.ceil(percentage);
    }

    /**
     * Tính median của một danh sách số (dùng cho HR/SpO2 mỗi batch) để giảm ảnh hưởng
     * của các outlier so với trung bình cộng. Trả về 0 nếu danh sách rỗng.
     */
    private static int calculateMedian(List<? extends Number> values) {
        if (values.isEmpty()) return 0;
        ArrayList<Double> sorted = new ArrayList<>();
        for (Number n : values) {
            sorted.add(n.doubleValue());
        }
        Collections.sort(sorted);
        return (int) Math.round(sorted.get(sorted.size() / 2));
    }

    private static String convertToISOTimestamp(long timestamp) {
        try {
//            Instant instant = Instant.ofEpochMilli(timestamp);
//            OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.ofHours(7));
//            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSXXX");
//            return offsetDateTime.format(formatter);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSXXX", Locale.US);
//        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+7"));
            dateFormat.setTimeZone(TimeZone.getDefault());
            return dateFormat.format(new Date(timestamp));
        } catch (Exception e) {
            Log.e(TAG, "Error converting timestamp to ISO format", e);
            return null;
        }
    }

    /**
     * Đưa một raw sample vào queue và kích hoạt xử lý. Là điểm vào (entry point) từ luồng BLE;
     * nhận đầy đủ các kênh đo (SpO2, HR, pleth, red/IR ADC, perfusion, pin, timestamp).
     */
    public void addDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                             int hrVal, long irAdc, double perfusionIndex, int counter,
                             String macAddress, long packageReceived, int plethVal, long timestamp) {
        DataPoint point = new DataPoint(spo2Val, spo2Status, redAdc, battery, hrVal,
                irAdc, perfusionIndex, plethVal, macAddress, packageReceived);
        dataQueue.offer(point);
        processQueueIfNeeded();
    }

    /**
     * Rút và xử lý các sample trong queue, dùng cờ {@link #isProcessing} (CAS) để đảm bảo chỉ
     * một luồng xử lý tại một thời điểm; các luồng khác chỉ enqueue rồi trả về ngay (non-blocking).
     */
    private void processQueueIfNeeded() {
        if (isProcessing.compareAndSet(false, true)) {
            try {
                while (!dataQueue.isEmpty()) {
                    DataPoint point = dataQueue.poll();
                    if (point != null) {
                        processDataPoint(point);
                    }
                }
            } finally {
                isProcessing.set(false);
            }
        }
    }

    /**
     * Drains anything still queued and writes the partial batch (&lt; {@link #BUFFER_SIZE} samples)
     * that would otherwise be lost when a segment is closed (stop / split / BLE pause).
     *
     * <p>Without this, a connection that delivers fewer than 100 samples — or the tail of every
     * segment — produces a CSV with only the header and zero data rows. Uses the same
     * {@link #isProcessing} gate as {@link #processQueueIfNeeded()} so it never races with the
     * sample-producing thread.
     */
    public void flushRemaining() {
        while (!isProcessing.compareAndSet(false, true)) {
            Thread.yield();
        }
        try {
            while (!dataQueue.isEmpty()) {
                DataPoint point = dataQueue.poll();
                if (point != null) {
                    processDataPoint(point);
                }
            }
            writeCurrentBatch();
        } finally {
            isProcessing.set(false);
        }
    }

    private void processDataPoint(DataPoint point) {
        if (spo2Values.isEmpty()) {
            this.macAddress = point.macAddress;
            this.firstBatteryValue = convertBatteryToPercentage(point.battery);
        }

        // Add all values to their respective lists
        spo2Values.add(point.spo2Val);
//        batteryValues.add(convertBatteryToPercentage(point.battery));
        hrValues.add(point.hrVal);
        spo2StatusValues.add(point.spo2Status);
        plethValues.add(point.plethVal);
        redAdcValues.add(point.redAdc);
        irAdcValues.add(point.irAdc);
        perfusionValues.add(point.perfusionIndex);
        timestampValues.add(point.timestamp);

        if (spo2Values.size() >= BUFFER_SIZE) {
            writeCurrentBatch();
        }
    }

    /**
     * Writes the currently buffered samples as one aggregated CSV row, then resets the lists.
     * Handles both full batches (100 samples) and the partial remainder flushed on close.
     */
    private void writeCurrentBatch() {
        if (spo2Values.isEmpty()) {
            return;
        }
        int medianIndex = timestampValues.size() / 2;
        String isoTimestamp = convertToISOTimestamp(timestampValues.get(medianIndex));
        if (isoTimestamp != null) {
            ProcessedData processedData = new ProcessedData(
                    isoTimestamp,                    // ISO 8601 formatted timestamp
                    macAddress,
                    firstBatteryValue != null ? firstBatteryValue.intValue() : 0,
                    calculateMedian(hrValues),
                    calculateMedian(spo2Values),
                    new ArrayList<>(spo2StatusValues),
                    new ArrayList<>(plethValues),
                    new ArrayList<>(redAdcValues),
                    new ArrayList<>(irAdcValues),
                    new ArrayList<>(perfusionValues)
            );

            OximeterDataWriterFunctions.writeProcessedDataPoint(processedData);
        } else {
            Log.e(TAG, "Failed to process data point due to invalid timestamp");
        }
        initializeLists();
    }


    /** Một raw sample (mẫu đo gốc) nhận từ thiết bị trước khi được gom thành batch. */
    private static class DataPoint {
        final double spo2Val;
        final int spo2Status;
        final long redAdc;
        final double battery;
        final int hrVal;
        final long irAdc;
        final double perfusionIndex;
        final int plethVal;
        final String macAddress;
        final long timestamp;

        DataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                  int hrVal, long irAdc, double perfusionIndex, int plethVal,
                  String macAddress, long timestamp) {
            this.spo2Val = spo2Val;
            this.spo2Status = spo2Status;
            this.redAdc = redAdc;
            this.battery = battery;
            this.hrVal = hrVal;
            this.irAdc = irAdc;
            this.perfusionIndex = perfusionIndex;
            this.plethVal = plethVal;
            this.macAddress = macAddress;
            this.timestamp = timestamp;
        }
    }

    /**
     * Một dòng CSV đã gom (aggregated) đại diện cho một batch: pleth/red/ir/perfusion là mảng
     * đầy đủ từng sample trong batch, còn hr/o2 là giá trị median của batch đó.
     */
    public static class ProcessedData {
        public final String timestamp;
        public final String deviceId;
        public final int battery;
        public final int hr;
        public final int o2;
        public final List<Integer> spo2Status;
        public final List<Integer> pleth;
        public final List<Long> red;
        public final List<Long> ir;
        public final List<Double> perfusion;

        ProcessedData(String timestamp, String deviceId, int battery, int hr, int o2,
                      List<Integer> spo2Status, List<Integer> pleth, List<Long> red,
                      List<Long> ir, List<Double> perfusion) {
            this.timestamp = timestamp;
            this.deviceId = deviceId;
            this.battery = battery;
            this.hr = hr;
            this.o2 = o2;
            this.spo2Status = spo2Status;
            this.pleth = pleth;
            this.red = red;
            this.ir = ir;
            this.perfusion = perfusion;
        }
    }
}
