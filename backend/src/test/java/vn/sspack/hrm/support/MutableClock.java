package vn.sspack.hrm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Đồng hồ cho test: cố định thời điểm và tua tới được (vd kiểm tra hết thời gian khóa). */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.instant = new AtomicReference<>(start);
        this.zone = zone;
    }

    public void setInstant(Instant value) {
        instant.set(value);
    }

    public void advance(Duration duration) {
        instant.updateAndGet(i -> i.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant.get(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
