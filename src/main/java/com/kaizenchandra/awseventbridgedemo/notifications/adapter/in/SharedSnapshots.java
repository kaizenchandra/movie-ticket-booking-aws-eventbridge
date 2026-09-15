package com.kaizenchandra.awseventbridgedemo.notifications.adapter.in;

import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Per-replica fanout: one in-flight read and one cached snapshot per subscribed topic.
 */
@Component
public class SharedSnapshots {
    private final BlockingBoundary boundary;
    private final Map<String, Entry> entries = new HashMap<>();

    public SharedSnapshots(BlockingBoundary boundary) {
        this.boundary = boundary;
    }

    public Flux<Object> watch(String key, Supplier<?> read) {
        return Flux.defer(() -> {
            Entry entry;
            synchronized (entries) {
                entry = entries.computeIfAbsent(key, ignored -> new Entry(read));
                entry.users++;
            }
            return entry.flux.onBackpressureLatest().doFinally(signal -> {
                synchronized (entries) {
                    if (--entry.users == 0) entries.remove(key, entry);
                }
            });
        });
    }

    private final class Entry {
        final Flux<Object> flux;
        int users;

        Entry(Supplier<?> read) {
            flux = Flux.interval(Duration.ZERO, Duration.ofSeconds(2)).onBackpressureDrop()
                    .concatMap(tick -> boundary.call(() -> (Object) read.get()), 1)
                    .replay(1).refCount(1);
        }
    }
}
