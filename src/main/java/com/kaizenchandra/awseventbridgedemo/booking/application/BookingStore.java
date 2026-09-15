package com.kaizenchandra.awseventbridgedemo.booking.application;

import java.util.*;
import java.time.Instant;

import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;
import com.kaizenchandra.awseventbridgedemo.scheduling.domain.Show;

public interface BookingStore {
    Show lockShow(UUID id);

    Booking get(UUID id);

    Booking lockBooking(UUID id);

    List<UUID> seatOwners(UUID show, List<String> seats);

    void lockSeats(UUID show, List<String> seats);

    List<Booking> active(UUID show);

    Optional<Booking> byKey(String owner, String key);

    void lockKey(String owner, String key);

    void insert(Booking b, String key, String fingerprint);

    String fingerprint(UUID id);

    void save(Booking before, Booking after);

    boolean seatsExist(UUID show, List<String> seats);

    boolean occupied(UUID show, List<String> seats);

    List<UUID> expiredShows(Instant now);

    List<Booking> history(String owner, int page, int size);

    void defer(UUID id, boolean refund);

    List<UUID> pendingPayments();

    List<UUID> pendingRefunds();
}
