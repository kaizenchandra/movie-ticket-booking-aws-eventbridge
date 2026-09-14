package com.kaizenchandra.awseventbridgedemo.booking.application;

import java.time.*;
import java.util.*;

import com.kaizenchandra.awseventbridgedemo.booking.domain.*;
import com.kaizenchandra.awseventbridgedemo.shared.domain.*;

public class Bookings {
    private final BookingStore store;
    private final Clock clock;
    private final Duration ttl;

    public Bookings(BookingStore store, Clock clock, Duration ttl) {
        this.store = store;
        this.clock = clock;
        this.ttl = ttl;
    }

    public Booking reserve(String owner, String key, UUID show, List<String> requested) {
        Problem.require(key != null && key.length() >= 8 && key.length() <= 128, "INVALID_IDEMPOTENCY_KEY");
        List<String> seats = requested.stream().sorted().toList();
        String fp = show + ":" + String.join(",", seats);
        store.lockKey(owner, key);
        var previous = store.byKey(owner, key);
        if (previous.isPresent()) {
            Problem.require(store.fingerprint(previous.get().id()).equals(fp), "IDEMPOTENCY_CONFLICT");
            return previous.get();
        }
        var s = store.lockShow(show);
        expireLocked(show);
        Problem.require(clock.instant().isBefore(s.startsAt()), "SHOW_STARTED");
        Booking b = Booking.hold(UUID.randomUUID(), show, owner, seats, s.price(), clock.instant().plus(ttl));
        Problem.require(store.seatsExist(show, seats), "INVALID_SEATS");
        Problem.require(!store.occupied(show, seats), "SEAT_UNAVAILABLE");
        store.insert(b, key, fp);
        return b;
    }

    public Booking get(String owner, UUID id) {
        var b = store.get(id);
        Problem.require(b.owner().equals(owner), "NOT_FOUND");
        return b;
    }

    public Booking pay(String owner, UUID id, String mode) {
        Problem.require(Set.of("SUCCESS", "FAILURE", "DELAY", "DUPLICATE", "REFUND_RETRY").contains(mode), "INVALID_PAYMENT_MODE");
        var initial = get(owner, id);
        store.lockShow(initial.showId());
        var b = get(owner, id);
        return save(b, b.initiate(clock.instant(), mode));
    }

    public Booking cancel(String owner, UUID id) {
        var initial = get(owner, id);
        var show = store.lockShow(initial.showId());
        var b = get(owner, id);
        return save(b, b.cancel(clock.instant(), show.startsAt()));
    }

    public Booking outcome(UUID id, boolean success) {
        var initial = store.get(id);
        store.lockShow(initial.showId());
        var b = store.get(id);
        return save(b, b.outcome(success, clock.instant()));
    }

    public Booking uncertain(UUID id) {
        var initial = store.get(id);
        store.lockShow(initial.showId());
        var b = store.get(id);
        return save(b, b.uncertain());
    }

    public Booking refunded(UUID id) {
        var initial = store.get(id);
        store.lockShow(initial.showId());
        var b = store.get(id);
        return save(b, b.refunded());
    }

    public void expireShow(UUID id) {
        store.lockShow(id);
        expireLocked(id);
    }

    private void expireLocked(UUID id) {
        for (var b : store.active(id)) save(b, b.expire(clock.instant()));
    }

    private Booking save(Booking a, Booking b) {
        if (a != b) store.save(a, b);
        return b;
    }
}
