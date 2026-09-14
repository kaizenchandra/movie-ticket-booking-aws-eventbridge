package com.kaizenchandra.awseventbridgedemo.scheduling.domain;

import java.time.Instant;
import java.util.UUID;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

public record Show(UUID id, UUID movieId, UUID screenId, Instant startsAt, Instant endsAt, Money price) {
}
