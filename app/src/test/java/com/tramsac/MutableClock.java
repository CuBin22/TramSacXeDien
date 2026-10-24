package com.tramsac;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Đồng hồ cho test: có thể tua tới để kiểm tra khóa 15 phút và hết hạn token mà không phải chờ thật. */
public class MutableClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant.truncatedTo(ChronoUnit.MICROS); // PostgreSQL chỉ lưu đến micro giây
        this.zone = zone;
    }

    public void advance(Duration d) {
        instant = instant.plus(d).truncatedTo(ChronoUnit.MICROS);
    }

    public void resetToNow() {
        instant = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
