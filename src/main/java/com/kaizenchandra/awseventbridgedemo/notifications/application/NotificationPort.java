package com.kaizenchandra.awseventbridgedemo.notifications.application;

import java.util.List;
import java.util.Map;

public interface NotificationPort {
    List<Map<String, Object>> inbox(String owner, int page, int size);
}
