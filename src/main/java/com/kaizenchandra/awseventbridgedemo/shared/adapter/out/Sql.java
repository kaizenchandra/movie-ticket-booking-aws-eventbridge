package com.kaizenchandra.awseventbridgedemo.shared.adapter.out;

import jakarta.persistence.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Sql {
    @PersistenceContext
    private EntityManager em;

    public Query query(String sql, Object... args) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < args.length; i++) q.setParameter(i + 1, args[i]);
        return q;
    }

    public int update(String sql, Object... args) {
        return query(sql, args).executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> rows(String sql, Object... args) {
        return query(sql, args).getResultList();
    }

    public EntityManager em() {
        return em;
    }

    public static UUID uuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    public static java.time.Instant instant(Object o) {
        return o instanceof java.time.Instant i ? i : o instanceof java.sql.Timestamp t ? t.toInstant() : ((java.time.OffsetDateTime) o).toInstant();
    }
}
