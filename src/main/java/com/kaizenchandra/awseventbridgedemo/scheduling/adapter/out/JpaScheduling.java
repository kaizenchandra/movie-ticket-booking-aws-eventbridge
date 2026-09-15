package com.kaizenchandra.awseventbridgedemo.scheduling.adapter.out;

import com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JpaScheduling implements SchedulingPort {
    private final Sql sql;

    public JpaScheduling(Sql sql) {
        this.sql = sql;
    }

    private List<Map<String, Object>> mapped(String query, String[] keys, Object... args) {
        return sql.rows(query, args).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) m.put(keys[i], r[i]);
            return m;
        }).toList();
    }

    public List<Map<String, Object>> shows(UUID movie, String city, Instant from, int page, int size) {
        return mapped("SELECT sh.id,m.title,c.name,c.city,sh.starts_at,sh.ends_at,sh.price_minor,sh.currency FROM showtime sh JOIN movie m ON m.id=sh.movie_id JOIN screen s ON s.id=sh.screen_id JOIN cinema c ON c.id=s.cinema_id WHERE sh.starts_at>=?1 AND (CAST(?2 AS uuid) IS NULL OR sh.movie_id=CAST(?2 AS uuid)) AND lower(c.city) LIKE lower(?3) ORDER BY sh.starts_at,sh.id LIMIT ?4 OFFSET ?5", new String[]{"id", "title", "cinema", "city", "startsAt", "endsAt", "priceMinor", "currency"}, from, movie, "%" + city + "%", size, page * size);
    }

    public UUID show(UUID movie, UUID screen, Instant start, Instant end, long price, String currency) {
        new Money(price, currency);
        Problem.require(end.isAfter(start), "INVALID_SHOW_TIME");
        Problem.require(!sql.query("SELECT id FROM screen WHERE id=?1 FOR UPDATE", screen).getResultList().isEmpty(), "NOT_FOUND");
        Problem.require(((Number) sql.query("SELECT count(*) FROM showtime WHERE screen_id=?1 AND starts_at<?2 AND ends_at>?3", screen, end, start).getSingleResult()).intValue() == 0, "SHOW_OVERLAP");
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO showtime VALUES(?1,?2,?3,?4,?5,?6,?7)", id, movie, screen, start, end, price, currency);
        Problem.require(sql.update("INSERT INTO show_seat SELECT ?1,label,NULL FROM seat WHERE screen_id=?2", id, screen) > 0, "INVALID_SEATS");
        return id;
    }

    public void deleteShow(UUID id) {
        Problem.require(!sql.query("SELECT id FROM showtime WHERE id=?1 FOR UPDATE", id).getResultList().isEmpty(), "NOT_FOUND");
        Problem.require(((Number) sql.query("SELECT count(*) FROM booking WHERE show_id=?1", id).getSingleResult()).intValue() == 0, "SHOW_HAS_BOOKINGS");
        sql.update("DELETE FROM show_seat WHERE show_id=?1", id);
        sql.update("DELETE FROM showtime WHERE id=?1", id);
    }
}
