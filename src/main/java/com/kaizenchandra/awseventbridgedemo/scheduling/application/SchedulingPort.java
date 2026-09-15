package com.kaizenchandra.awseventbridgedemo.scheduling.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SchedulingPort {
    List<Map<String, Object>> shows(UUID movie, String city, Instant from, int page, int size);

    UUID show(UUID movie, UUID screen, Instant start, Instant end, long price, String currency);

    void deleteShow(UUID id);
}
