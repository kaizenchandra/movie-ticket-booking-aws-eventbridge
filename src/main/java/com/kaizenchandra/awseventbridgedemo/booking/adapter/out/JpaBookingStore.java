package com.kaizenchandra.awseventbridgedemo.booking.adapter.out;

import org.springframework.stereotype.Repository;
import jakarta.persistence.*;

import java.util.*;
import java.time.*;

import com.kaizenchandra.awseventbridgedemo.booking.application.BookingStore;
import com.kaizenchandra.awseventbridgedemo.booking.domain.*;
import com.kaizenchandra.awseventbridgedemo.scheduling.domain.Show;
import com.kaizenchandra.awseventbridgedemo.shared.domain.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.booking.application.BookingEvents;

@Repository
public class JpaBookingStore implements BookingStore {
    private final Clock clock;
    private final Sql sql;
    private final BookingRepository repo;
    private final BookingEvents outbox;

    public JpaBookingStore(Sql sql, BookingRepository repo, BookingEvents outbox, Clock clock) {
        this.clock = clock;
        this.sql = sql;
        this.repo = repo;
        this.outbox = outbox;
    }

    public Show lockShow(UUID id) {
        var rows = sql.rows("SELECT id,movie_id,screen_id,starts_at,ends_at,price_minor,currency FROM showtime WHERE id=?1 FOR UPDATE", id);
        Problem.require(!rows.isEmpty(), "NOT_FOUND");
        var r = rows.getFirst();
        // Clear previously loaded entities after waiting for the show lock: READ COMMITTED must see the winning transaction.
        sql.em().clear();
        return new Show(Sql.uuid(r[0]), Sql.uuid(r[1]), Sql.uuid(r[2]), Sql.instant(r[3]), Sql.instant(r[4]), new Money(((Number) r[5]).longValue(), (String) r[6]));
    }

    public Booking get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new Problem("NOT_FOUND")).domain();
    }

    public List<Booking> active(UUID show) {
        return sql.em().createQuery("from BookingEntity where showId=:show and hold='ACTIVE'", BookingEntity.class).setParameter("show", show).getResultList().stream().map(BookingEntity::domain).toList();
    }

    public void lockKey(String owner, String key) {
        sql.query("SELECT pg_advisory_xact_lock(hashtextextended(?1,0))", owner + ":" + key).getSingleResult();
    }

    public Optional<Booking> byKey(String owner, String key) {
        return sql.em().createQuery("from BookingEntity where owner=:owner and requestKey=:key", BookingEntity.class).setParameter("owner", owner).setParameter("key", key).getResultStream().findFirst().map(BookingEntity::domain);
    }

    public String fingerprint(UUID id) {
        return repo.findById(id).orElseThrow().fingerprint;
    }

    public void insert(Booking b, String key, String fingerprint) {
        repo.saveAndFlush(new BookingEntity(b, key, fingerprint));
        sql.update("UPDATE booking SET next_payment_attempt=?2,next_refund_attempt=?2 WHERE id=?1", b.id(), clock.instant());
        assign(b);
        outbox.append(b);
    }

    public void save(Booking before, Booking after) {
        var entity = repo.findById(after.id()).orElseThrow();
        entity.apply(after);
        repo.flush();
        if (!after.ownsSeats()) sql.update("UPDATE show_seat SET booking_id=NULL WHERE booking_id=?1", after.id());
        if (after.status() == BookingStatus.CONFIRMED) for (String seat : after.seats())
            sql.update("INSERT INTO ticket(id,booking_id,label) VALUES(?1,?2,?3) ON CONFLICT(booking_id,label) DO NOTHING", UUID.nameUUIDFromBytes((after.id() + ":" + seat).getBytes(java.nio.charset.StandardCharsets.UTF_8)), after.id(), seat);
        if (after.status() == BookingStatus.CANCELLED)
            sql.update("UPDATE ticket SET revoked=true WHERE booking_id=?1", after.id());
        outbox.append(after);
    }

    private void assign(Booking b) {
        for (String seat : b.seats())
            Problem.require(sql.update("UPDATE show_seat SET booking_id=?1 WHERE show_id=?2 AND label=?3 AND booking_id IS NULL", b.id(), b.showId(), seat) == 1, "SEAT_UNAVAILABLE");
    }

    public boolean seatsExist(UUID show, List<String> seats) {
        return ((Number) sql.query("SELECT count(*) FROM show_seat WHERE show_id=?1 AND label IN (?2)", show, seats).getSingleResult()).intValue() == seats.size();
    }

    public boolean occupied(UUID show, List<String> seats) {
        return ((Number) sql.query("SELECT count(*) FROM show_seat WHERE show_id=?1 AND label IN (?2) AND booking_id IS NOT NULL", show, seats).getSingleResult()).intValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> ids(String query, Object... args) {
        return sql.query(query, args).getResultList().stream().map(x -> Sql.uuid(x)).toList();
    }

    public List<UUID> expiredShows(Instant now) {
        return ids("SELECT DISTINCT show_id FROM booking WHERE hold='ACTIVE' AND expires_at<=?1 LIMIT 100", now);
    }

    public void defer(UUID id, boolean refund) {
        String prefix = refund ? "refund" : "payment";
        sql.update("UPDATE booking SET " + prefix + "_attempts=" + prefix + "_attempts+1,next_" + prefix + "_attempt=CAST(?2 AS timestamptz) + make_interval(secs => CAST(LEAST(300,POWER(2,LEAST(" + prefix + "_attempts+1,8))) AS double precision)) WHERE id=?1", id, clock.instant());
    }

    public List<UUID> pendingPayments() {
        return ids("SELECT id FROM booking WHERE payment IN ('PENDING','UNKNOWN') AND next_payment_attempt<=?1 ORDER BY next_payment_attempt LIMIT 50", clock.instant());
    }

    public List<UUID> pendingRefunds() {
        return ids("SELECT id FROM booking WHERE refund='PENDING' AND next_refund_attempt<=?1 ORDER BY next_refund_attempt LIMIT 50", clock.instant());
    }

    public List<Booking> history(String owner, int page, int size) {
        return sql.em().createQuery("from BookingEntity where owner=:owner order by id", BookingEntity.class).setParameter("owner", owner).setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(BookingEntity::domain).toList();
    }
}
