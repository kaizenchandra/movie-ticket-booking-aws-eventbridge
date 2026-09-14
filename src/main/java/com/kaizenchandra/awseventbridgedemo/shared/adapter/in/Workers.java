package com.kaizenchandra.awseventbridgedemo.shared.adapter.in;

import org.springframework.stereotype.Component;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.OperationalMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.*;
import java.time.Clock;

import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.payments.application.PaymentReconciler;
import com.kaizenchandra.awseventbridgedemo.notifications.adapter.out.OutboxDelivery;
import com.kaizenchandra.awseventbridgedemo.notifications.adapter.in.NotificationConsumer;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;

@Component
@ConditionalOnProperty(name = "workers.enabled", havingValue = "true", matchIfMissing = true)
public class Workers implements SmartLifecycle {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, Thread.ofPlatform().name("worker-", 0).factory());
    private volatile boolean running;
    private final boolean awsEnabled;
    private final OperationalMetrics metrics;
    private final Bookings bookings;
    private final BookingStore store;
    private final Transactions tx;
    private final Clock clock;
    private final PaymentReconciler payments;
    private final OutboxDelivery outbox;
    private final NotificationConsumer consumer;

    public Workers(Bookings bookings, BookingStore store, Transactions tx, Clock clock, PaymentReconciler payments, OutboxDelivery outbox, NotificationConsumer consumer, OperationalMetrics metrics, @org.springframework.beans.factory.annotation.Value("${workers.aws-enabled:true}") boolean awsEnabled) {
        this.awsEnabled = awsEnabled;
        this.metrics = metrics;
        this.bookings = bookings;
        this.store = store;
        this.tx = tx;
        this.clock = clock;
        this.payments = payments;
        this.outbox = outbox;
        this.consumer = consumer;
    }

    public void start() {
        running = true;
        repeat(() -> {
            for (var id : tx.execute(() -> store.expiredShows(clock.instant())))
                tx.execute(() -> {
                    bookings.expireShow(id);
                    return true;
                });
        }, 1);
        repeat(payments::run, 3);
        repeat(() -> {
            metrics.refresh();
            if (awsEnabled) outbox.publish();
        }, 1);
        if (awsEnabled) repeat(consumer::poll, 1);
    }

    private void repeat(Runnable task, long delay) {
        executor.scheduleWithFixedDelay(() -> {
            try {
                task.run();
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("worker retry type={}", e.getClass().getSimpleName());
            }
        }, 1, delay, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(25, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
