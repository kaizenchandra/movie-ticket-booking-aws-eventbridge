package com.kaizenchandra.awseventbridgedemo.scheduling.application;

import java.util.*;
import java.time.Instant;

public interface SchedulingPort {
    List<Map<String, Object>> shows(UUID movie, String city, Instant from, int page, int size);

    UUID show(UUID movie, UUID screen, Instant start, Instant end, long price, String currency);

    void deleteShow(UUID id);
}
