package com.kaizenchandra.awseventbridgedemo.notifications.adapter.out;

import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;
import com.kaizenchandra.awseventbridgedemo.notifications.application.IntegrationEvent;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.UUID;

@Component
public class Outbox implements com.kaizenchandra.awseventbridgedemo.booking.application.BookingEvents {
    private final Sql sql;
    private final JsonMapper json;
    private final Clock clock;

    public Outbox(Sql sql, JsonMapper json, Clock clock) {
        this.sql = sql;
        this.json = json;
        this.clock = clock;
    }

    public void append(Booking b) {
        var change = b.changedAt(clock.instant());
        var previous = sql.query("SELECT id FROM outbox WHERE aggregate_id=?1 AND aggregate_version=?2", b.id(), b.version() - 1).getResultList();
        UUID cause = previous.isEmpty() ? b.id() : Sql.uuid(previous.getFirst());
        var span = io.opentelemetry.api.trace.Span.current().getSpanContext();
        String traceParent = span.isValid() ? "00-" + span.getTraceId() + "-" + span.getSpanId() + "-" + span.getTraceFlags().asHex() : null;
        var e = new IntegrationEvent(UUID.randomUUID(), 1, "BookingChanged", change.bookingId(), change.version(), change.occurredAt(), b.id(), cause, change.status().name(), change.showId(), traceParent);
        sql.update("INSERT INTO outbox(id,aggregate_id,aggregate_version,type,payload,created_at,available_at) VALUES(?1,?2,?3,?4,?5,?6,?6)", e.eventId(), b.id(), b.version(), e.type(), json.writeValueAsString(e), clock.instant());
    }
}
