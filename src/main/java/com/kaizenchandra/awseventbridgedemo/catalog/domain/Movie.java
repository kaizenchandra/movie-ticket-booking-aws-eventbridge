package com.kaizenchandra.awseventbridgedemo.catalog.domain;

import java.util.UUID;

public record Movie(UUID id, String title, int durationMinutes) {
}
