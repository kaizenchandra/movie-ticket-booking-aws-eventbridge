package com.kaizenchandra.awseventbridgedemo.payments.application;

import com.kaizenchandra.awseventbridgedemo.booking.application.BookingStore;
import com.kaizenchandra.awseventbridgedemo.booking.application.Bookings;
import com.kaizenchandra.awseventbridgedemo.booking.domain.Booking;
import com.kaizenchandra.awseventbridgedemo.payments.domain.PaymentStatus;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;

import java.util.UUID;

public class PaymentCallbacks {
    private final Transactions tx;
    private final BookingStore store;
    private final Bookings bookings;
    private final SimulatorControl simulator;

    public PaymentCallbacks(Transactions tx, BookingStore store, Bookings bookings, SimulatorControl simulator) {
        this.tx = tx;
        this.store = store;
        this.bookings = bookings;
        this.simulator = simulator;
    }

    public Booking receive(UUID id, boolean success) {
        var booking = tx.execute(() -> store.get(id));
        Problem.require(booking.payment() != PaymentStatus.NONE, "PAYMENT_NOT_INITIATED");
        // The simulated provider records its side effect before the separate booking transaction.
        // A crash between these calls is repaired by normal durable reconciliation.
        boolean effective = simulator.settle(id, booking.total(), booking.mode(), success);
        return tx.execute(() -> bookings.outcome(id, effective));
    }
}
