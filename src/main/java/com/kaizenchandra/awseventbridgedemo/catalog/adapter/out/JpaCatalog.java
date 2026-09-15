package com.kaizenchandra.awseventbridgedemo.catalog.adapter.out;

import com.kaizenchandra.awseventbridgedemo.catalog.application.CatalogPort;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.shared.domain.Problem;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class JpaCatalog implements CatalogPort {
    private final Sql sql;

    public JpaCatalog(Sql sql) {
        this.sql = sql;
    }

    private List<Map<String, Object>> mapped(String query, String[] keys, Object... args) {
        return sql.rows(query, args).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) m.put(keys[i], r[i]);
            return m;
        }).toList();
    }

    public List<Map<String, Object>> movies(String title, int page, int size) {
        return mapped("SELECT id,title,duration_minutes FROM movie WHERE lower(title) LIKE lower(?1) ORDER BY title,id LIMIT ?2 OFFSET ?3", new String[]{"id", "title", "durationMinutes"}, "%" + title + "%", size, page * size);
    }

    public List<Map<String, Object>> cinemas() {
        return mapped("SELECT c.id,c.name,c.city,s.id,s.name FROM cinema c LEFT JOIN screen s ON s.cinema_id=c.id ORDER BY c.name,s.name LIMIT 500", new String[]{"id", "name", "city", "screenId", "screenName"});
    }


    public UUID movie(String title, int duration) {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO movie VALUES(?1,?2,?3)", id, title, duration);
        return id;
    }

    public UUID cinema(String name, String city) {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO cinema VALUES(?1,?2,?3)", id, name, city);
        return id;
    }

    public UUID screen(UUID cinema, String name, List<String> seats) {
        UUID id = UUID.randomUUID();
        sql.update("INSERT INTO screen VALUES(?1,?2,?3)", id, cinema, name);
        for (var label : seats) sql.update("INSERT INTO seat VALUES(?1,?2)", id, label);
        return id;
    }

    public void updateMovie(UUID id, String title, int duration) {
        Problem.require(sql.update("UPDATE movie SET title=?2,duration_minutes=?3 WHERE id=?1", id, title, duration) == 1, "NOT_FOUND");
    }

    public void updateCinema(UUID id, String name, String city) {
        Problem.require(sql.update("UPDATE cinema SET name=?2,city=?3 WHERE id=?1", id, name, city) == 1, "NOT_FOUND");
    }

    public void updateScreen(UUID id, String name, List<String> seats) {
        Problem.require(!sql.query("SELECT id FROM screen WHERE id=?1 FOR UPDATE", id).getResultList().isEmpty(), "NOT_FOUND");
        Problem.require(((Number) sql.query("SELECT count(*) FROM showtime WHERE screen_id=?1", id).getSingleResult()).intValue() == 0, "SCREEN_IN_USE");
        sql.update("UPDATE screen SET name=?2 WHERE id=?1", id, name);
        sql.update("DELETE FROM seat WHERE screen_id=?1", id);
        for (var s : seats) sql.update("INSERT INTO seat VALUES(?1,?2)", id, s);
    }

}
