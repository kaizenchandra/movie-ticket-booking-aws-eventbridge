package com.kaizenchandra.awseventbridgedemo.inventory.application;

import java.util.*;
import java.time.Instant;

public interface InventoryPort {
    List<Map<String, Object>> seats(UUID show);
}
