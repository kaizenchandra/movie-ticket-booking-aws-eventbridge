package com.kaizenchandra.awseventbridgedemo.booking.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A business fact; delivery IDs, schema versions and tracing belong to adapters.
 */
public record BookingChanged(UUID bookingId, UUID showId, long version,
                             BookingStatus status, Instant occurredAt) {
}
