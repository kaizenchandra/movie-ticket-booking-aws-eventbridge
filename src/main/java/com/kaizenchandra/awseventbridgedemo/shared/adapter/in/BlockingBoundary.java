package com.kaizenchandra.awseventbridgedemo.shared.adapter.in;

import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class BlockingBoundary {
    private final Transactions tx;
    private final Scheduler databaseScheduler;

    public BlockingBoundary(Transactions tx, Scheduler databaseScheduler) {
        this.tx = tx;
        this.databaseScheduler = databaseScheduler;
    }

    public <T> Mono<T> call(Supplier<T> work) {
        return Mono.deferContextual(context -> Mono.fromCallable(() -> tx.execute(work)).subscribeOn(databaseScheduler)).timeout(Duration.ofSeconds(8));
    }
}
