package com.kaizenchandra.awseventbridgedemo.booking.application;

import java.util.*;
import java.time.Instant;

public interface TicketPort {
    List<Map<String, Object>> tickets(UUID booking);
}
