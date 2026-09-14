package com.kaizenchandra.awseventbridgedemo.booking.adapter.in;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

import reactor.core.publisher.Mono;
import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;
import com.kaizenchandra.awseventbridgedemo.booking.application.TicketPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;

@RestController
@RequestMapping("/api/bookings")
public class BookingHttp {
    private final Bookings bookings;
    private final BookingStore store;
    private final BlockingBoundary boundary;
    private final TicketPort catalog;

    public BookingHttp(Bookings bookings, BookingStore store, BlockingBoundary boundary, TicketPort catalog) {
        this.bookings = bookings;
        this.store = store;
        this.boundary = boundary;
        this.catalog = catalog;
    }

    public record Reserve(@NotNull UUID showId,
                          @NotEmpty @Size(max = 8) List<@NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,12}") String> seats) {
    }

    public record Pay(@NotNull String mode) {
    }

    @PostMapping
    public Mono<Booking> reserve(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody Reserve request) {
        return boundary.call(() -> bookings.reserve(jwt.getSubject(), key, request.showId(), request.seats()));
    }

    @GetMapping("/{id}")
    public Mono<Booking> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return boundary.call(() -> bookings.get(jwt.getSubject(), id));
    }

    @GetMapping
    public Mono<List<Booking>> history(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return boundary.call(() -> store.history(jwt.getSubject(), page, size));
    }

    @PostMapping("/{id}/payment")
    public Mono<Booking> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Pay body) {
        return boundary.call(() -> bookings.pay(jwt.getSubject(), id, body.mode()));
    }

    @PostMapping("/{id}/cancel")
    public Mono<Booking> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return boundary.call(() -> bookings.cancel(jwt.getSubject(), id));
    }

    @GetMapping("/{id}/tickets")
    public Mono<List<Map<String, Object>>> tickets(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return boundary.call(() -> {
            bookings.get(jwt.getSubject(), id);
            return catalog.tickets(id);
        });
    }
}
