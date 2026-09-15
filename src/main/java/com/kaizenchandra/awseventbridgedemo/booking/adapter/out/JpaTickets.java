package com.kaizenchandra.awseventbridgedemo.booking.adapter.out;

import com.kaizenchandra.awseventbridgedemo.booking.application.TicketPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JpaTickets implements TicketPort {
    private final Sql sql;

    public JpaTickets(Sql sql) {
        this.sql = sql;
    }

    private List<Map<String, Object>> mapped(String query, String[] keys, Object... args) {
        return sql.rows(query, args).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) m.put(keys[i], r[i]);
            return m;
        }).toList();
    }

    public List<Map<String, Object>> tickets(UUID booking) {
        return mapped("SELECT id,label,revoked FROM ticket WHERE booking_id=?1 ORDER BY label", new String[]{"id", "seat", "revoked"}, booking);
    }
}
