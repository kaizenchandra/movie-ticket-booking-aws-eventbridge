package com.kaizenchandra.awseventbridgedemo.booking.adapter.out;

import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;
import com.kaizenchandra.awseventbridgedemo.booking.domain.BookingStatus;
import com.kaizenchandra.awseventbridgedemo.inventory.domain.HoldStatus;
import com.kaizenchandra.awseventbridgedemo.payments.domain.PaymentStatus;
import com.kaizenchandra.awseventbridgedemo.payments.domain.RefundStatus;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "booking")
public class BookingEntity {
    @Id
    public UUID id;
    public UUID showId;
    public String owner;
    public String seats;
    public long priceMinor;
    public String currency;
    public Instant expiresAt;
    @Column(insertable = false, updatable = false)
    public Instant createdAt;
    @Enumerated(EnumType.STRING)
    public BookingStatus status;
    @Enumerated(EnumType.STRING)
    public HoldStatus hold;
    @Enumerated(EnumType.STRING)
    public PaymentStatus payment;
    @Enumerated(EnumType.STRING)
    public RefundStatus refund;
    public long version;
    public String mode;
    public String requestKey;
    public String fingerprint;

    protected BookingEntity() {
    }

    public BookingEntity(Booking b, String key, String fp) {
        requestKey = key;
        fingerprint = fp;
        apply(b);
    }

    public void apply(Booking b) {
        id = b.id();
        showId = b.showId();
        owner = b.owner();
        seats = String.join(",", b.seats());
        priceMinor = b.total().minor();
        currency = b.total().currency();
        expiresAt = b.expiresAt();
        status = b.status();
        hold = b.hold();
        payment = b.payment();
        refund = b.refund();
        version = b.version();
        mode = b.mode();
    }

    public Booking domain() {
        return new Booking(id, showId, owner, List.of(seats.split(",")), new Money(priceMinor, currency), expiresAt, status, hold, payment, refund, version, mode);
    }
}
