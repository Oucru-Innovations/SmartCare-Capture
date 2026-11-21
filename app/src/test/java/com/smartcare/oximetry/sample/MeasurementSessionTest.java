package com.smartcare.oximetry.sample;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MeasurementSessionTest {
    /** Gửi gói mỗi giây từ {@code from} tới {@code to} (ms). */
    private static void feed(MeasurementSession s, long from, long to) {
        for (long t = from; t <= to; t += 1000) {
            s.onData(t);
        }
    }

    @Test
    public void dropWithinGrace_resumesAndCompletesAfterTwentyMinutesOfData() {
        MeasurementSession s = new MeasurementSession(0);
        assertEquals(MeasurementSession.Status.WAITING, s.tick(0));
        long dropAt = 1000 + 10 * 60_000L;
        feed(s, 1000, dropAt);                         // 10 phút dữ liệu
        assertEquals(MeasurementSession.Status.PAUSED, s.tick(dropAt + 60_000));
        long back = dropAt + 2 * 60_000L;              // nối lại sau 2 phút
        feed(s, back, back + 9 * 60_000L);
        assertEquals(MeasurementSession.Status.RUNNING, s.tick(back + 9 * 60_000L));
        feed(s, back + 9 * 60_000L + 1000, back + 11 * 60_000L);
        assertEquals(MeasurementSession.Status.COMPLETED, s.tick(back + 11 * 60_000L));
        Map<String, Object> json = s.toJson();
        assertEquals("completed", json.get("status"));
        assertEquals(1, ((List<?>) json.get("interruptions")).size());
    }

    @Test
    public void dropLongerThanGrace_endsIncomplete() {
        MeasurementSession s = new MeasurementSession(0);
        long dropAt = 1000 + 10 * 60_000L;
        feed(s, 1000, dropAt);
        assertEquals(MeasurementSession.Status.INCOMPLETE,
                s.tick(dropAt + MeasurementSession.GRACE_MS + 1));
        assertEquals("incomplete", s.toJson().get("status"));
        assertEquals(600L, s.toJson().get("data_seconds"));
    }
}
