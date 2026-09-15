package com.kaizenchandra.awseventbridgedemo.notifications.adapter.in;

import com.kaizenchandra.awseventbridgedemo.booking.application.BookingStore;
import com.kaizenchandra.awseventbridgedemo.inventory.application.InventoryPort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/live")
public class LiveHttp {
    private final SharedSnapshots shared;
    private final InventoryPort catalog;
    private final BookingStore bookings;
    private final Clock clock;

    public LiveHttp(SharedSnapshots shared, InventoryPort catalog, BookingStore bookings, Clock clock) {
        this.shared = shared;
        this.catalog = catalog;
        this.bookings = bookings;
        this.clock = clock;
    }

    private Flux<ServerSentEvent<Object>> snapshots(Jwt jwt, String key, java.util.function.Supplier<?> snapshot) {
        long seconds = Math.max(0, Math.min(300, Duration.between(clock.instant(), jwt.getExpiresAt()).getSeconds()));
        return shared.watch(key, snapshot)
                .map(data -> ServerSentEvent.builder(data).event("snapshot").id(UUID.randomUUID().toString())
                        .retry(Duration.ofSeconds(2)).comment("heartbeat; replace local state").build())
                .onBackpressureLatest().take(Duration.ofSeconds(seconds));
    }

    @GetMapping(value = "/shows/{id}", produces = "text/event-stream")
    public Flux<?> show(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return snapshots(jwt, "show:" + id, () -> catalog.seats(id));
    }

    @GetMapping(value = "/bookings", produces = "text/event-stream")
    public Flux<?> bookings(@AuthenticationPrincipal Jwt jwt) {
        return snapshots(jwt, "user:" + jwt.getSubject(), () -> bookings.history(jwt.getSubject(), 0, 100));
    }
}
