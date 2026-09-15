package com.kaizenchandra.awseventbridgedemo.shared.adapter.out;

import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class JpaTransactions implements Transactions {
    private final TransactionTemplate template;
    private final io.micrometer.observation.ObservationRegistry observations;

    public JpaTransactions(PlatformTransactionManager manager, io.micrometer.observation.ObservationRegistry observations) {
        this.observations = observations;
        template = new TransactionTemplate(manager);
        template.setTimeout(5);
    }

    public <T> T execute(Supplier<T> operation) {
        if (reactor.core.scheduler.Schedulers.isInNonBlockingThread())
            throw new IllegalStateException("JPA_ON_EVENT_LOOP");
        return io.micrometer.observation.Observation.createNotStarted("cinema.transaction", observations).observe(() -> template.execute(status -> operation.get()));
    }
}
