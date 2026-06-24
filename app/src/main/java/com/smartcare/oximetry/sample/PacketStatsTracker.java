package com.smartcare.oximetry.sample;

import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

/**
 * Theo dõi số liệu packet do thiết bị SmartCare gửi về app và ghi ra {@link FileLogger}:
 * <ul>
 *   <li><b>Tổng số packet nhận được</b> kể từ khi bắt đầu theo dõi.</li>
 *   <li><b>Số packet nhận trong mỗi phút</b> (tốc độ) — ghi log tổng hợp mỗi 60 giây.</li>
 *   <li><b>Số packet bị mất</b> kèm <b>lý do rõ ràng</b>, phát hiện qua gap của bộ đếm tuần tự
 *       {@code KEY_DATA_COUNTER} mà thiết bị gắn vào mỗi packet.</li>
 * </ul>
 *
 * <p>"Mất" ở đây nghĩa là thiết bị đã gửi packet nhưng app không nhận được (mất trên đường truyền
 * BLE hoặc do SDK rớt) — biết được nhờ bộ đếm thiết bị nhảy cách quãng. Đây tách biệt với việc
 * packet đã nhận nhưng không ghi được vào CSV (hàng đợi đầy), vốn được {@link OximeterDataBuffer}
 * tự ghi log riêng (sự kiện {@code BUFFER/QUEUE_SUMMARY} và {@code SAMPLE_DROPPED_QUEUE_FULL}).
 *
 * <p>Là singleton để số liệu liền mạch qua việc xoay màn hình (activity tạo lại). Mọi phương thức
 * được gọi trên main thread (từ handler {@code MESSAGE_PACKET} và ticker dùng main Looper) nên
 * không cần đồng bộ.
 */
public class PacketStatsTracker {

    private static final long MINUTE_MS = 60_000L;

    /**
     * Gap lớn bất thường (ví dụ do bộ đếm thiết bị tràn số/khởi động lại mà ta không chắc modulus)
     * sẽ KHÔNG được tính là mất, để tránh số liệu sai; thay vào đó chỉ ghi log resync.
     */
    private static final int MAX_PLAUSIBLE_GAP = 1000;

    /** Giới hạn tần suất ghi log chi tiết từng đợt gap để tránh spam khi kết nối kém. */
    private static final long GAP_LOG_THROTTLE_MS = 2_000L;

    private static PacketStatsTracker instance;

    public static synchronized PacketStatsTracker getInstance() {
        if (instance == null) {
            instance = new PacketStatsTracker();
        }
        return instance;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());

    private long totalReceived = 0;
    private long totalLostBle = 0;
    private long windowReceived = 0;
    private long windowLostBle = 0;
    private int lastDeviceCounter = -1;
    private long lastGapLogMs = 0;
    private boolean running = false;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            logMinuteSummary();
            windowReceived = 0;
            windowLostBle = 0;
            if (running) {
                handler.postDelayed(this, MINUTE_MS);
            }
        }
    };

    private PacketStatsTracker() {
    }

    /** Bắt đầu ghi log tổng hợp theo phút (idempotent). */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        handler.postDelayed(minuteTick, MINUTE_MS);
        FileLogger.logEvent("PACKET_STATS", "TRACKER_STARTED",
                "Per-minute packet stats logging started; totalReceived=" + totalReceived);
    }

    /** Dừng ghi log tổng hợp theo phút và in một dòng tổng kết. */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        handler.removeCallbacks(minuteTick);
        FileLogger.logEvent("PACKET_STATS", "TRACKER_STOPPED",
                "totalReceived=" + totalReceived + " totalLostBle=" + totalLostBle);
    }

    /**
     * Gọi mỗi khi app nhận một packet từ thiết bị.
     *
     * @param deviceCounter giá trị {@code KEY_DATA_COUNTER} đính kèm packet (tăng 1 mỗi packet)
     * @return số packet vừa bị mất ngay trước packet này (gap đáng tin); 0 nếu liền mạch hoặc không
     *         xác định được (reset/wrap). Dùng để hiển thị khoảng trống đúng nhịp khi vẽ sóng.
     */
    public int onPacketReceived(int deviceCounter) {
        totalReceived++;
        windowReceived++;

        int countedMissing = 0;
        if (lastDeviceCounter >= 0) {
            if (deviceCounter > lastDeviceCounter) {
                int missing = deviceCounter - lastDeviceCounter - 1;
                if (missing > 0) {
                    if (missing <= MAX_PLAUSIBLE_GAP) {
                        countedMissing = missing;
                        totalLostBle += missing;
                        windowLostBle += missing;
                        logGap(lastDeviceCounter, deviceCounter, missing,
                                "BLE_TRANSMISSION_OR_SDK_DROP (device sent but app did not receive)");
                    } else {
                        logResync(lastDeviceCounter, deviceCounter,
                                "implausible gap (" + missing
                                        + ") treated as device counter reset/wrap, not counted as loss");
                    }
                }
            } else if (deviceCounter < lastDeviceCounter) {
                logResync(lastDeviceCounter, deviceCounter,
                        "counter went backwards: device reset/wrap or reconnect, not counted as loss");
            }
            // deviceCounter == lastDeviceCounter: duplicate packet, ignore.
        }
        lastDeviceCounter = deviceCounter;
        return countedMissing;
    }

    /**
     * Gọi khi mất kết nối/chuẩn bị reconnect: đặt lại mốc bộ đếm thiết bị để packet đầu tiên sau khi
     * nối lại không bị tính nhầm thành một gap khổng lồ (vì counter có thể bắt đầu lại từ đầu).
     */
    public void onConnectionReset(String reason) {
        if (lastDeviceCounter != -1) {
            FileLogger.logEvent("PACKET_STATS", "COUNTER_RESYNC_ON_RECONNECT",
                    "reset device-counter baseline; reason=" + reason
                            + " lastCounter=" + lastDeviceCounter);
        }
        lastDeviceCounter = -1;
    }

    private void logGap(int from, int to, int missing, String reason) {
        long now = System.currentTimeMillis();
        if (now - lastGapLogMs < GAP_LOG_THROTTLE_MS) {
            return;
        }
        lastGapLogMs = now;
        FileLogger.logWarningEvent("PACKET_STATS", "BLE_PACKET_GAP",
                "device counter " + from + "->" + to + " missing=" + missing
                        + " reason=" + reason);
    }

    private void logResync(int from, int to, String detail) {
        FileLogger.logWarningEvent("PACKET_STATS", "COUNTER_RESYNC",
                "device counter " + from + "->" + to + "; " + detail);
    }

    private void logMinuteSummary() {
        if (windowReceived == 0 && windowLostBle == 0) {
            // Im lặng khi không có lưu lượng (vd chưa kết nối) để tránh nhiễu log.
            return;
        }
        long denom = windowReceived + windowLostBle;
        double lossPct = denom > 0 ? (windowLostBle * 100.0 / denom) : 0.0;
        FileLogger.logEvent("PACKET_STATS", "MINUTE_SUMMARY",
                "perMin received=" + windowReceived
                        + " lostBle=" + windowLostBle
                        + String.format(Locale.US, " lossPct=%.2f%%", lossPct)
                        + " | total received=" + totalReceived
                        + " totalLostBle=" + totalLostBle);
    }

    public long getTotalReceived() {
        return totalReceived;
    }

    public long getTotalLostBle() {
        return totalLostBle;
    }
}
