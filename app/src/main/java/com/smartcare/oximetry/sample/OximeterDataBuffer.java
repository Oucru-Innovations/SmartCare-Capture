package com.smartcare.oximetry.sample;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Date;

/**
 * Bộ gom dữ liệu (aggregator) chạy theo mô hình producer/consumer: luồng BLE chỉ đẩy raw sample
 * vào một hàng đợi ({@link #commandQueue}) và trả về ngay (non-blocking), còn một luồng nền duy nhất
 * ({@link #consumerThread}) rút từ hàng đợi, gom các sample thành từng nhóm {@link #BUFFER_SIZE}
 * (=100), tính median HR/SpO2 rồi ghi một dòng CSV {@link ProcessedData} mỗi batch.
 *
 * <p>Tách I/O ghi CSV khỏi luồng nhận/UI giúp main thread không bị chặn khi MediaStore chậm
 * (đặc biệt trên máy yếu như Galaxy Tab T295), nhờ đó việc vẽ sóng không bị giật theo. Toàn bộ
 * trạng thái batch chỉ được luồng consumer chạm vào nên không cần khóa cho các list.
 */
public class OximeterDataBuffer {
    /** Số sample gom lại trước khi tính median và ghi một dòng CSV (một batch). */
    private static final int BUFFER_SIZE = 100;
    private static final String TAG = "OximeterDataBuffer";

    /**
     * Trần (soft cap) số phần tử chờ trong hàng đợi. Ở ~100Hz, 20.000 phần tử ≈ 200s dữ liệu tồn
     * đọng — quá đủ để hấp thụ các đợt I/O chậm. Vượt ngưỡng này sample mới sẽ bị bỏ (đếm + log)
     * thay vì làm phình RAM vô hạn hoặc chặn luồng BLE.
     */
    private static final int MAX_QUEUE_SIZE = 20_000;

    /** Chu kỳ ghi log tổng hợp số liệu hàng đợi ra FileLogger (mili-giây). */
    private static final long SUMMARY_LOG_INTERVAL_MS = 60_000L;

    /** Hàng đợi lệnh: phần tử là {@link DataPoint}, {@link FlushCommand} hoặc {@link #POISON}. */
    private final BlockingQueue<Object> commandQueue = new LinkedBlockingQueue<>();

    /** Sentinel báo luồng consumer dừng (ghi nốt batch dở rồi thoát). */
    private static final Object POISON = new Object();

    // ---- Bộ đếm (thread-safe) để theo dõi và ghi log số liệu xử lý ----
    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong droppedCount = new AtomicLong(0);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong batchesWritten = new AtomicLong(0);
    private long lastDropLogMs = 0;
    private long lastSummaryLogMs = 0;

    private Thread consumerThread;

    // ---- Current batch data (chỉ luồng consumer được chạm vào) ----
    private List<Double> spo2Values;
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
        initializeLists();
        startConsumer();
    }

    private void initializeLists() {
        spo2Values = new ArrayList<>(BUFFER_SIZE);
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
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSXXX", Locale.US);
            dateFormat.setTimeZone(TimeZone.getDefault());
            return dateFormat.format(new Date(timestamp));
        } catch (Exception e) {
            Log.e(TAG, "Error converting timestamp to ISO format", e);
            return null;
        }
    }

    /**
     * Khởi động luồng consumer nền (idempotent). Luồng này là nơi duy nhất xử lý batch và ghi CSV,
     * nên thứ tự sample luôn được giữ nguyên.
     */
    public synchronized void startConsumer() {
        if (consumerThread != null) {
            return;
        }
        consumerThread = new Thread(this::consumeLoop, "OximeterCsvConsumer");
        consumerThread.start();
        FileLogger.logEvent("BUFFER", "CONSUMER_STARTED",
                "CSV consumer thread started; maxQueue=" + MAX_QUEUE_SIZE);
    }

    /**
     * Dừng luồng consumer: đẩy {@link #POISON} để luồng ghi nốt batch dở rồi thoát, sau đó join có
     * timeout. Gọi sau khi đã flush và đóng file.
     */
    public void stopConsumer() {
        Thread t;
        synchronized (this) {
            t = consumerThread;
            consumerThread = null;
        }
        if (t == null) {
            return;
        }
        commandQueue.offer(POISON);
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FileLogger.logEvent("BUFFER", "CONSUMER_STOPPED",
                "enqueued=" + enqueuedCount.get()
                        + " processed=" + processedCount.get()
                        + " dropped=" + droppedCount.get()
                        + " batches=" + batchesWritten.get());
    }

    /**
     * Đưa một raw sample vào hàng đợi rồi trả về ngay (điểm vào từ luồng BLE/UI). KHÔNG ghi CSV ở
     * đây — việc đó do luồng consumer đảm nhiệm. Nếu hàng đợi vượt {@link #MAX_QUEUE_SIZE} thì bỏ
     * sample và đếm/log (có throttle) để tránh phình RAM hoặc chặn luồng nhận.
     */
    public void addDataPoint(double spo2Val, int spo2Status, long redAdc, double battery,
                             int hrVal, long irAdc, double perfusionIndex, int counter,
                             String macAddress, long packageReceived, int plethVal, long timestamp) {
        if (commandQueue.size() >= MAX_QUEUE_SIZE) {
            long dropped = droppedCount.incrementAndGet();
            long now = System.currentTimeMillis();
            if (now - lastDropLogMs > 5_000L) {
                lastDropLogMs = now;
                FileLogger.logWarningEvent("BUFFER", "SAMPLE_DROPPED_QUEUE_FULL",
                        "Queue at cap " + MAX_QUEUE_SIZE + "; totalDropped=" + dropped);
            }
            return;
        }
        DataPoint point = new DataPoint(spo2Val, spo2Status, redAdc, battery, hrVal,
                irAdc, perfusionIndex, plethVal, macAddress, timestamp);
        if (commandQueue.offer(point)) {
            enqueuedCount.incrementAndGet();
        } else {
            droppedCount.incrementAndGet();
        }
    }

    /**
     * Vòng lặp luồng consumer: rút lệnh khỏi hàng đợi và xử lý tuần tự. Là nơi duy nhất chạm vào
     * trạng thái batch và gọi ghi CSV.
     */
    private void consumeLoop() {
        try {
            while (true) {
                Object item = commandQueue.take();
                if (item == POISON) {
                    writeCurrentBatch();
                    break;
                } else if (item instanceof FlushCommand) {
                    writeCurrentBatch();
                    ((FlushCommand) item).latch.countDown();
                } else if (item instanceof DataPoint) {
                    processDataPoint((DataPoint) item);
                    maybeLogSummary();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Consumer interrupted; flushing remaining batch");
            writeCurrentBatch();
        }
    }

    /**
     * Ghi nốt phần batch còn dở (&lt; {@link #BUFFER_SIZE} sample) đang chờ trong consumer, đảm bảo
     * không mất đuôi dữ liệu khi đóng/split segment (stop / split / BLE pause).
     *
     * <p>Yêu cầu flush được gửi qua chính hàng đợi nên nó được thực thi tuần tự SAU mọi sample đã
     * enqueue trước đó (giữ đúng thứ tự, không tranh chấp trạng thái batch). Gọi từ luồng khác và
     * chờ (có timeout) đến khi consumer xác nhận đã ghi xong. Nếu consumer không chạy thì flush
     * trực tiếp tại chỗ (fallback).
     */
    public void flushRemaining() {
        Thread t;
        synchronized (this) {
            t = consumerThread;
        }
        if (t == null || !t.isAlive()) {
            drainInline();
            writeCurrentBatch();
            return;
        }
        FlushCommand cmd = new FlushCommand();
        if (!commandQueue.offer(cmd)) {
            return;
        }
        try {
            if (!cmd.latch.await(5, TimeUnit.SECONDS)) {
                FileLogger.logWarningEvent("BUFFER", "FLUSH_TIMEOUT",
                        "Consumer did not confirm flush within 5s; queue=" + commandQueue.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Xử lý tại chỗ toàn bộ DataPoint còn trong hàng đợi (chỉ dùng khi consumer đã dừng). */
    private void drainInline() {
        Object item;
        while ((item = commandQueue.poll()) != null) {
            if (item instanceof DataPoint) {
                processDataPoint((DataPoint) item);
            } else if (item instanceof FlushCommand) {
                ((FlushCommand) item).latch.countDown();
            }
        }
    }

    private void processDataPoint(DataPoint point) {
        if (spo2Values.isEmpty()) {
            this.macAddress = point.macAddress;
            this.firstBatteryValue = convertBatteryToPercentage(point.battery);
        }

        spo2Values.add(point.spo2Val);
        hrValues.add(point.hrVal);
        spo2StatusValues.add(point.spo2Status);
        plethValues.add(point.plethVal);
        redAdcValues.add(point.redAdc);
        irAdcValues.add(point.irAdc);
        perfusionValues.add(point.perfusionIndex);
        timestampValues.add(point.timestamp);

        processedCount.incrementAndGet();

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
                    isoTimestamp,
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
            batchesWritten.incrementAndGet();
        } else {
            Log.e(TAG, "Failed to process data point due to invalid timestamp");
        }
        initializeLists();
    }

    /** Ghi log tổng hợp số liệu hàng đợi ra FileLogger theo chu kỳ để dễ theo dõi/đọc lại. */
    private void maybeLogSummary() {
        long now = System.currentTimeMillis();
        if (now - lastSummaryLogMs < SUMMARY_LOG_INTERVAL_MS) {
            return;
        }
        lastSummaryLogMs = now;
        FileLogger.logEvent("BUFFER", "QUEUE_SUMMARY",
                "enqueued=" + enqueuedCount.get()
                        + " processed=" + processedCount.get()
                        + " dropped=" + droppedCount.get()
                        + " batches=" + batchesWritten.get()
                        + " queueDepth=" + commandQueue.size());
    }

    public long getEnqueuedCount() {
        return enqueuedCount.get();
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getDroppedCount() {
        return droppedCount.get();
    }

    /** Lệnh yêu cầu consumer ghi nốt batch dở; {@link #latch} được đếm xuống khi đã ghi xong. */
    private static class FlushCommand {
        final CountDownLatch latch = new CountDownLatch(1);
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
