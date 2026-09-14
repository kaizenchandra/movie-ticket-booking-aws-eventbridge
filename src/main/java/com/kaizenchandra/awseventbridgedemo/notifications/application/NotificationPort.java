package com.kaizenchandra.awseventbridgedemo.notifications.application;
import java.util.*;
public interface NotificationPort { List<Map<String,Object>> inbox(String owner,int page,int size); }
