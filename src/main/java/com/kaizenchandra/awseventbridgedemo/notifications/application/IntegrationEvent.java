package com.kaizenchandra.awseventbridgedemo.notifications.application;

import java.time.Instant;
import java.util.UUID;

public record IntegrationEvent(UUID eventId, int schemaVersion, String type, UUID aggregateId, long aggregateVersion,
                               Instant occurredAt, UUID correlationId, UUID causationId, String status, UUID showId,
                               String traceParent) {
}
