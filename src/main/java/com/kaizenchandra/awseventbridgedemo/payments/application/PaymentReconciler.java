package com.kaizenchandra.awseventbridgedemo.payments.application;

import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;

public class PaymentReconciler {
    private final Transactions tx;
    private final BookingStore store;
    private final Bookings bookings;
    private final PaymentProvider provider;

    public PaymentReconciler(Transactions tx, BookingStore store, Bookings bookings, PaymentProvider provider) {
        this.tx = tx;
        this.store = store;
        this.bookings = bookings;
        this.provider = provider;
    }

    public void run() {
        for (var id : tx.execute(store::pendingPayments)) {
            try {
                var b = tx.execute(() -> store.get(id));
                var outcome = provider.charge(id, b.total(), b.mode());
                tx.execute(() -> outcome == PaymentProvider.Outcome.UNKNOWN ? bookings.uncertain(id) : bookings.outcome(id, outcome == PaymentProvider.Outcome.SUCCESS));
                if (outcome == PaymentProvider.Outcome.UNKNOWN) tx.execute(() -> {
                    store.defer(id, false);
                    return true;
                });
                if (b.mode().equals("DUPLICATE") && outcome == PaymentProvider.Outcome.SUCCESS)
                    tx.execute(() -> bookings.outcome(id, true));
            } catch (RuntimeException e) {
                tx.execute(() -> {
                    bookings.uncertain(id);
                    store.defer(id, false);
                    return true;
                });
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Payment requires reconciliation: {0}", id);
            }
        }
        for (var id : tx.execute(store::pendingRefunds)) {
            try {
                var b = tx.execute(() -> store.get(id));
                if (provider.refund(id, b.total(), b.mode())) tx.execute(() -> bookings.refunded(id));
                else tx.execute(() -> {
                    store.defer(id, true);
                    return true;
                });
            } catch (RuntimeException e) {
                tx.execute(() -> {
                    store.defer(id, true);
                    return true;
                });
                System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Refund requires reconciliation: {0}", id);
            }
        }
    }
}
