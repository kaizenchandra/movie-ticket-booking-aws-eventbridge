package com.kaizenchandra.awseventbridgedemo.scheduling.domain;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

public record Show(UUID id, UUID movieId, UUID screenId, Instant startsAt, Instant endsAt, Money price) {
}
