package com.kaizenchandra.awseventbridgedemo.booking.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TicketPort {
    List<Map<String, Object>> tickets(UUID booking);
}
