package com.kaizenchandra.awseventbridgedemo.notifications.adapter.in;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.time.*;

import reactor.core.publisher.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import com.kaizenchandra.awseventbridgedemo.inventory.application.InventoryPort;
import com.kaizenchandra.awseventbridgedemo.booking.application.BookingStore;

@RestController
@RequestMapping("/api/live")
public class LiveHttp {
    private final BlockingBoundary boundary;
    private final InventoryPort catalog;
    private final BookingStore bookings;
    private final Clock clock;

    public LiveHttp(BlockingBoundary boundary, InventoryPort catalog, BookingStore bookings, Clock clock) {
        this.boundary = boundary;
        this.catalog = catalog;
        this.bookings = bookings;
        this.clock = clock;
    }

    private <T> Flux<ServerSentEvent<T>> snapshots(Jwt jwt, java.util.function.Supplier<T> snapshot) {
        long seconds = Math.max(0, Math.min(300, Duration.between(clock.instant(), jwt.getExpiresAt()).getSeconds()));
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(2)).onBackpressureDrop()
                .concatMap(tick -> boundary.call(snapshot).map(data -> ServerSentEvent.<T>builder(data).event("snapshot").id(UUID.randomUUID().toString()).retry(Duration.ofSeconds(2)).comment("heartbeat; replace local state").build()), 1)
                .onBackpressureLatest().take(Duration.ofSeconds(seconds));
    }

    @GetMapping(value = "/shows/{id}", produces = "text/event-stream")
    public Flux<?> show(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return snapshots(jwt, () -> catalog.seats(id));
    }

    @GetMapping(value = "/bookings", produces = "text/event-stream")
    public Flux<?> bookings(@AuthenticationPrincipal Jwt jwt) {
        return snapshots(jwt, () -> bookings.history(jwt.getSubject(), 0, 100));
    }
}
