package com.kaizenchandra.awseventbridgedemo;

import com.kaizenchandra.awseventbridgedemo.notifications.adapter.in.SharedSnapshots;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SharedSnapshotsTest {
    @Test
    void hundredClientsShareOneReadPerTickAndDisconnectEvictsSnapshot() {
        var boundary = mock(BlockingBoundary.class);
        when(boundary.call(any())).thenAnswer(invocation -> Mono.fromSupplier(invocation.getArgument(0, Supplier.class)));
        var shared = new SharedSnapshots(boundary);
        var reads = new AtomicInteger();
        StepVerifier.withVirtualTime(() -> Flux.merge(java.util.stream.IntStream.range(0, 100)
                        .mapToObj(i -> shared.watch("show:1", reads::incrementAndGet).take(2)).toList()))
                .expectNextCount(100).thenAwait(Duration.ofSeconds(2)).expectNextCount(100).verifyComplete();
        assertThat(reads).hasValue(2);
        StepVerifier.withVirtualTime(() -> shared.watch("show:1", reads::incrementAndGet).take(1))
                .expectNext(3).verifyComplete();
    }

    @Test
    void userTopicsNeverShareSnapshots() {
        var boundary = mock(BlockingBoundary.class);
        when(boundary.call(any())).thenAnswer(invocation -> Mono.fromSupplier(invocation.getArgument(0, Supplier.class)));
        var shared = new SharedSnapshots(boundary);
        StepVerifier.withVirtualTime(() -> Flux.zip(shared.watch("user:alice", () -> "alice"), shared.watch("user:bob", () -> "bob")).take(1))
                .assertNext(pair -> {
                    assertThat(pair.getT1()).isEqualTo("alice");
                    assertThat(pair.getT2()).isEqualTo("bob");
                }).verifyComplete();
    }
}
