package com.kaizenchandra.awseventbridgedemo.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.*;

import reactor.core.scheduler.*;
import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.payments.application.*;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;

@Configuration
public class Wiring {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler databaseScheduler(
            @Value("${booking.database-workers:8}") int workers,
            @Value("${booking.database-queue:64}") int queue,
            @Value("${spring.datasource.hikari.maximum-pool-size:16}") int connections) {
        if (workers < 1 || queue < 1 || workers + 4 > connections)
            throw new IllegalArgumentException("Database workers must leave at least four pool connections for background work");
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
        var executor = new java.util.concurrent.ThreadPoolExecutor(workers, workers, 0,
                java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<>(queue),
                Thread.ofPlatform().name("jpa-", 0).factory(), new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        return Schedulers.fromExecutorService(executor, "jpa");
    }

    @Bean
    public Bookings bookings(BookingStore store, Clock clock, @Value("${booking.hold-seconds:300}") long seconds) {
        return new Bookings(store, clock, Duration.ofSeconds(seconds));
    }

    @Bean
    public PaymentReconciler reconciler(Transactions tx, BookingStore store, Bookings bookings, PaymentProvider provider) {
        return new PaymentReconciler(tx, store, bookings, provider);
    }

    @Bean
    public tools.jackson.databind.json.JsonMapper jsonMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    }

    @Bean
    @Profile({"local", "test"})
    public PaymentCallbacks paymentCallbacks(Transactions tx, BookingStore store, Bookings bookings, SimulatorControl simulator) {
        return new PaymentCallbacks(tx, store, bookings, simulator);
    }
}
