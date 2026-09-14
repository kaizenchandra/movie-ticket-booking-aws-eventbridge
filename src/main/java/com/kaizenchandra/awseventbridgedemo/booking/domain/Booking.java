package com.kaizenchandra.awseventbridgedemo.booking.domain;

import java.time.Instant;
import java.util.*;

import com.kaizenchandra.awseventbridgedemo.shared.domain.*;
import com.kaizenchandra.awseventbridgedemo.inventory.domain.HoldStatus;
import com.kaizenchandra.awseventbridgedemo.payments.domain.*;

public record Booking(UUID id, UUID showId, String owner, List<String> seats, Money total, Instant expiresAt,
                      BookingStatus status, HoldStatus hold, PaymentStatus payment, RefundStatus refund, long version,
                      String mode) {
    public Booking {
        seats = List.copyOf(seats);
    }

    public static Booking hold(UUID id, UUID show, String owner, List<String> seats, Money price, Instant expiry) {
        Problem.require(!seats.isEmpty() && seats.size() <= 8 && new HashSet<>(seats).size() == seats.size(), "INVALID_SEATS");
        return new Booking(id, show, owner, seats, price.times(seats.size()), expiry, BookingStatus.HELD, HoldStatus.ACTIVE, PaymentStatus.NONE, RefundStatus.NONE, 0, "SUCCESS");
    }

    private Booking change(BookingStatus s, HoldStatus h, PaymentStatus p, RefundStatus r, String m) {
        return new Booking(id, showId, owner, seats, total, expiresAt, s, h, p, r, version + 1, m);
    }

    public Booking expire(Instant now) {
        if (hold != HoldStatus.ACTIVE || now.isBefore(expiresAt)) return this;
        return change(BookingStatus.EXPIRED, HoldStatus.EXPIRED, payment, refund, mode);
    }

    public Booking initiate(Instant now, String requestedMode) {
        if (payment != PaymentStatus.NONE) {
            Problem.require(mode.equals(requestedMode), "IDEMPOTENCY_CONFLICT");
            return this;
        }
        Problem.require(hold == HoldStatus.ACTIVE && now.isBefore(expiresAt), "HOLD_EXPIRED");
        return change(BookingStatus.PAYMENT_PENDING, hold, PaymentStatus.PENDING, refund, requestedMode);
    }

    public Booking outcome(boolean success, Instant now) {
        if (payment == PaymentStatus.SUCCEEDED || (!success && payment == PaymentStatus.FAILED)) return this;
        Problem.require(payment != PaymentStatus.NONE, "PAYMENT_NOT_INITIATED");
        if (success) {
            if (hold == HoldStatus.ACTIVE && now.isBefore(expiresAt))
                return change(BookingStatus.CONFIRMED, HoldStatus.CONSUMED, PaymentStatus.SUCCEEDED, refund, mode);
            Booking b = expire(now);
            return b.change(b.status, b.hold, PaymentStatus.SUCCEEDED, RefundStatus.PENDING, mode);
        }
        if (hold == HoldStatus.ACTIVE)
            return change(BookingStatus.FAILED, HoldStatus.RELEASED, PaymentStatus.FAILED, refund, mode);
        return change(status, hold, PaymentStatus.FAILED, refund, mode);
    }

    public Booking uncertain() {
        return payment == PaymentStatus.PENDING ? change(status, hold, PaymentStatus.UNKNOWN, refund, mode) : this;
    }

    public Booking cancel(Instant now, Instant startsAt) {
        if (status == BookingStatus.CANCELLED) return this;
        Problem.require(now.isBefore(startsAt), "CANCELLATION_CLOSED");
        return change(BookingStatus.CANCELLED, HoldStatus.RELEASED, payment, payment == PaymentStatus.SUCCEEDED ? RefundStatus.PENDING : refund, mode);
    }

    public Booking refunded() {
        return refund == RefundStatus.PENDING ? change(status, hold, payment, RefundStatus.SUCCEEDED, mode) : this;
    }

    public boolean ownsSeats() {
        return hold == HoldStatus.ACTIVE || hold == HoldStatus.CONSUMED;
    }
}
