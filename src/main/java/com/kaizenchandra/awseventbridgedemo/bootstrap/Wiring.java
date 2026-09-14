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
    public Scheduler databaseScheduler() {
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
        return Schedulers.newBoundedElastic(8, 16, "jpa", 60);
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
}
