package com.kaizenchandra.awseventbridgedemo.inventory.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InventoryPort {
    List<Map<String, Object>> seats(UUID show);
}
