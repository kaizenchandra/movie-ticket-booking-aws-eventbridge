package com.kaizenchandra.awseventbridgedemo.notifications.adapter.out;

import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class OutboxDelivery {
    private final Sql sql;
    private final Transactions tx;
    private final EventBridgeClient events;
    private final Clock clock;
    private final String bus;
    public OutboxDelivery(Sql sql, Transactions tx, EventBridgeClient events, Clock clock, @Value("${aws.bus:cinema}") String bus) {
        this.sql = sql;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
        this.bus = bus;
    }

    public List<Claimed> claim() {
        return tx.execute(() -> {
            var now = clock.instant();
            var token = UUID.randomUUID();
            var rows = sql.rows("SELECT id,payload,attempts FROM outbox WHERE delivered_at IS NULL AND available_at<=?1 AND (lease_until IS NULL OR lease_until<=?1) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 10", now);
            var result = new ArrayList<Claimed>();
            for (var r : rows) {
                UUID id = Sql.uuid(r[0]);
                sql.update("UPDATE outbox SET claim_token=?2,lease_until=?3,attempts=attempts+1 WHERE id=?1", id, token, now.plusSeconds(60));
                result.add(new Claimed(id, token, (String) r[1], ((Number) r[2]).intValue() + 1));
            }
            return result;
        });
    }

    public void mark(Claimed c, String error) {
        tx.execute(() -> {
            if (error == null)
                sql.update("UPDATE outbox SET delivered_at=?3,lease_until=NULL,last_error=NULL WHERE id=?1 AND claim_token=?2", c.id(), c.token(), clock.instant());
            else
                sql.update("UPDATE outbox SET lease_until=NULL,available_at=?3,last_error=?4 WHERE id=?1 AND claim_token=?2", c.id(), c.token(), clock.instant().plusSeconds(Math.min(300, 1L << Math.min(c.attempts(), 8))), error);
            return true;
        });
    }

    public void publish() {
        var batch = claim();
        if (batch.isEmpty()) return;
        try {
            var entries = batch.stream().map(c -> PutEventsRequestEntry.builder().eventBusName(bus).source("cinema.booking").detailType("BookingChanged.v1").detail(c.payload()).build()).toList();
            var response = events.putEvents(PutEventsRequest.builder().entries(entries).build());
            for (int i = 0; i < batch.size(); i++) {
                var r = response.entries().get(i);
                mark(batch.get(i), r.errorCode() != null ? r.errorCode() : r.eventId() == null ? "MISSING_EVENT_ID" : null);
            }
        } catch (RuntimeException e) {
            for (var c : batch) mark(c, "PUBLISH_UNCERTAIN");
            throw e;
        }
    }

    public record Claimed(UUID id, UUID token, String payload, int attempts) {
    }
}
