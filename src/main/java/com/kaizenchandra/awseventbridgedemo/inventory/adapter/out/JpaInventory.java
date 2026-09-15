package com.kaizenchandra.awseventbridgedemo.inventory.adapter.out;

import com.kaizenchandra.awseventbridgedemo.inventory.application.InventoryPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JpaInventory implements InventoryPort {
    private final Sql sql;
    private final Clock clock;

    public JpaInventory(Sql sql, Clock clock) {
        this.sql = sql;
        this.clock = clock;
    }

    private List<Map<String, Object>> mapped(String query, String[] keys, Object... args) {
        return sql.rows(query, args).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) m.put(keys[i], r[i]);
            return m;
        }).toList();
    }

    public List<Map<String, Object>> seats(UUID show) {
        var rows = mapped("SELECT s.label,CASE WHEN b.id IS NULL OR (b.hold='ACTIVE' AND b.expires_at<=?2) THEN 'AVAILABLE' WHEN b.hold='CONSUMED' THEN 'BOOKED' ELSE 'HELD' END FROM show_seat s LEFT JOIN booking b ON b.id=s.booking_id WHERE s.show_id=?1 ORDER BY s.label", new String[]{"label", "status"}, show, clock.instant());
        Problem.require(!rows.isEmpty(), "NOT_FOUND");
        return rows;
    }
}
