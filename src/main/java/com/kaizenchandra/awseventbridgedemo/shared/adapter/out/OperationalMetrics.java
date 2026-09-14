package com.kaizenchandra.awseventbridgedemo.shared.adapter.out;

import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.*;
import java.time.*;

import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;

@Component
public class OperationalMetrics {
    private final Transactions tx;
    private final Sql sql;
    private final AtomicLong pending = new AtomicLong(), age = new AtomicLong(), refunds = new AtomicLong(), failed = new AtomicLong();

    public OperationalMetrics(Transactions tx, Sql sql, MeterRegistry registry) {
        this.tx = tx;
        this.sql = sql;
        registry.gauge("cinema.outbox.pending", pending);
        registry.gauge("cinema.outbox.oldest.seconds", age);
        registry.gauge("cinema.refunds.pending", refunds);
        registry.gauge("cinema.bookings.failed", failed);
    }

    public void refresh() {
        tx.execute(() -> {
            var r = sql.rows("SELECT count(*),COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP-min(created_at)),0) FROM outbox WHERE delivered_at IS NULL").getFirst();
            pending.set(((Number) r[0]).longValue());
            age.set(((Number) r[1]).longValue());
            refunds.set(((Number) sql.query("SELECT count(*) FROM booking WHERE refund='PENDING'").getSingleResult()).longValue());
            failed.set(((Number) sql.query("SELECT count(*) FROM booking WHERE status='FAILED'").getSingleResult()).longValue());
            return true;
        });
    }
}
