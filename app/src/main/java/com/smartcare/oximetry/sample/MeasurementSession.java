package com.smartcare.oximetry.sample;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phiên đo theo protocol (hướng A): cần đủ {@link #TARGET_MS} dữ liệu thật. Mất kết nối thì đồng hồ
 * dừng; nối lại trong {@link #GRACE_MS} thì đếm tiếp, quá thì phiên kết thúc với trạng thái incomplete.  
 * Thuần Java (thời gian truyền vào) để test được trên JVM.
 *
 * ponytail: chỉ giữ trong RAM của RecordActivity — Activity bị hủy là mất phiên; lưu prefs nếu cần.
 */
final class MeasurementSession {
    static final long  TARGET_MS = 20 * 60_000L;
    /** Không có gói nào quá khoảng này coi như đang mất kết nối (khoảng trống không được tính). */
    static final long GAP_MS = 3_000L;
    /** Mất kết nối quá lâu thì dừng phiên. */
    static final long GRACE_MS = 5 * 60_000L;

    enum Status { WAITING, RUNNING, PAUSED, COMPLETED, INCOMPLETE }

    private final long startedAtMs;
    private long lastDataMs;
    private long dataMs;
    private long endedAtMs;
    private Status finalStatus;
    private final List<long[]> interruptions = new ArrayList<>();

    MeasurementSession(long nowMs) {
        startedAtMs = nowMs;
    }

    /** Gọi mỗi khi nhận một gói dữ liệu từ máy đo. */
    void onData(long nowMs) {
        if (finalStatus != null) {
            return;
        }
        if (lastDataMs > 0) {
            long delta = nowMs - lastDataMs;
            if (delta <= GAP_MS) {
                dataMs += delta;
            } else {
                interruptions.add(new long[]{lastDataMs, nowMs});
            }
        }
        lastDataMs = nowMs;
        if (dataMs >= TARGET_MS) {
            finish(Status.COMPLETED, nowMs);
        }
    }

    /** Gọi định kỳ (mỗi giây); trả về trạng thái hiện tại, chốt INCOMPLETE khi quá thời gian chờ. */
    Status tick(long nowMs) {
        if (finalStatus != null) {
            return finalStatus;
        }
        if (lastDataMs == 0) {
            return Status.WAITING;
        }
        long silent = nowMs - lastDataMs;
        if (silent > GRACE_MS) {
            interruptions.add(new long[]{lastDataMs, nowMs});
            finish(Status.INCOMPLETE, nowMs);
            return finalStatus;
        }
        return silent > GAP_MS ? Status.PAUSED : Status.RUNNING;
    }

    long dataMs() {
        return dataMs;
    }

    /** Thời gian mất kết nối hiện tại (0 nếu chưa có dữ liệu). */
    long silentMs(long nowMs) {
        return lastDataMs == 0 ? 0 : Math.max(0, nowMs - lastDataMs);
    }

    private void finish(Status status, long nowMs) {
        finalStatus = status;
        endedAtMs = nowMs;
    }

    Map<String, Object> toJson() {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (long[] gap : interruptions) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("start", iso.format(new Date(gap[0])));
            g.put("end", iso.format(new Date(gap[1])));
            gaps.add(g);
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("status", finalStatus == Status.COMPLETED ? "completed" : "incomplete");
        json.put("target_minutes", TARGET_MS / 60_000L);
        json.put("data_seconds", dataMs / 1000L);
        json.put("started_at", iso.format(new Date(startedAtMs)));
        json.put("ended_at", endedAtMs == 0 ? null : iso.format(new Date(endedAtMs)));
        json.put("interruptions", gaps);
        return json;
    }
}
