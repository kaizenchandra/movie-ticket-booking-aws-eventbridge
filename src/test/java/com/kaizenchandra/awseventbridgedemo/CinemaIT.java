package com.kaizenchandra.awseventbridgedemo;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import java.security.interfaces.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.*;
import com.kaizenchandra.awseventbridgedemo.booking.application.*;
import com.kaizenchandra.awseventbridgedemo.booking.domain.*;
import com.kaizenchandra.awseventbridgedemo.catalog.application.*;
import com.kaizenchandra.awseventbridgedemo.shared.application.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.*;
import com.kaizenchandra.awseventbridgedemo.shared.domain.*;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.in.BlockingBoundary;
import com.kaizenchandra.awseventbridgedemo.payments.application.*;
import com.kaizenchandra.awseventbridgedemo.payments.domain.*;
import com.kaizenchandra.awseventbridgedemo.notifications.adapter.out.*;
import com.kaizenchandra.awseventbridgedemo.notifications.adapter.in.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"workers.enabled=false", "payment.callback-secret=test-callback-secret", "aws.endpoint=http://localhost:4566"})
@ActiveProfiles("test")
@Testcontainers
class CinemaIT {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.6");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    static class MutableClock extends Clock {
        volatile Instant now = Instant.now();

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId z) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    static final MutableClock time = new MutableClock();
    static KeyPair keys;

    static {
        try {
            var g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            keys = g.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @org.springframework.context.annotation.Primary
        Clock testClock() {
            return time;
        }

        @Bean
        ReactiveJwtDecoder testDecoder() {
            var d = NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
            d.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("https://test.local"), j -> j.getAudience().contains("cinema") ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success() : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error("audience"))));
            return d;
        }
    }

    @Autowired
    Transactions tx;
    @Autowired
    Bookings bookings;
    @Autowired
    BookingStore store;
    @Autowired
    CatalogPort catalog;
    @Autowired
    com.kaizenchandra.awseventbridgedemo.scheduling.application.SchedulingPort scheduling;
    @Autowired
    com.kaizenchandra.awseventbridgedemo.booking.application.TicketPort tickets;
    @Autowired
    Sql sql;
    @Autowired
    BlockingBoundary boundary;
    @Autowired
    PaymentReconciler reconciler;
    @Autowired
    OutboxDelivery outbox;
    @Autowired
    NotificationConsumer consumer;
    @MockitoBean
    EventBridgeClient events;
    @MockitoBean
    software.amazon.awssdk.services.sqs.SqsClient sqs;
    @LocalServerPort
    int port;
    WebTestClient web;
    UUID show;

    String token(String user) {
        return token(user, "cinema", Instant.now().plusSeconds(600));
    }

    String token(String user, String audience, Instant expiry) {
        try {
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().issuer("https://test.local").audience(audience).subject(user).expirationTime(Date.from(expiry)).claim("roles", user.equals("admin") ? List.of("ADMIN", "CUSTOMER") : List.of("CUSTOMER")).build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        time.now = Instant.now();
        web = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(10)).build();
        tx.execute(() -> {
            sql.update("TRUNCATE notification,consumer_receipt,outbox,ticket,show_seat,simulated_charge,booking,showtime,seat,screen,cinema,movie CASCADE");
            var m = catalog.movie("Test", 100);
            var c = catalog.cinema("Cinema", "City");
            var s = catalog.screen(c, "Screen", List.of("A1", "A2", "A3", "A4"));
            show = scheduling.show(m, s, time.instant().plusSeconds(10000), time.instant().plusSeconds(17000), 1000, "INR");
            return true;
        });
        reset(events);
    }

    Booking hold(String key, String... seats) {
        return tx.execute(() -> bookings.reserve("alice", key, show, List.of(seats)));
    }

    @Test
    void bookingPaymentTicketsAndRefund() {
        var b = hold("happy-path", "A1", "A2");
        tx.execute(() -> bookings.pay("alice", b.id(), "REFUND_RETRY"));
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(tx.execute(() -> tickets.tickets(b.id()))).hasSize(2);
        reconciler.run();
        assertThat(tx.execute(() -> tickets.tickets(b.id()))).hasSize(2);
        tx.execute(() -> bookings.cancel("alice", b.id()));
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).refund()).isEqualTo(RefundStatus.PENDING);
        time.now = time.now.plusSeconds(4);
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).refund()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(tx.execute(() -> tickets.tickets(b.id()))).allMatch(t -> Boolean.TRUE.equals(t.get("revoked")));
    }

    @Test
    void disjointSeatsProceedWhileAnotherReservationHasNotCommitted() throws Exception {
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tx.execute(() -> {
                var b = bookings.reserve("alice", "independent-one", show, List.of("A1"));
                locked.countDown();
                try {
                    if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException e) { throw new RuntimeException(e); }
                return b;
            }));
            try {
                assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> hold("independent-two", "A2"));
                assertThat(second.get(1, TimeUnit.SECONDS).seats()).containsExactly("A2");
            } finally { release.countDown(); }
            assertThat(first.get(2, TimeUnit.SECONDS).seats()).containsExactly("A1");
        }
    }

    @Test
    void overlappingExpiredMultiSeatOwnersAreReclaimedWithoutDeadlock() throws Exception {
        hold("expired-pair-one", "A1", "A2");
        hold("expired-pair-two", "A3", "A4");
        time.now = time.now.plusSeconds(301);
        var results = race(2, i -> i == 0 ? hold("reclaim-pair-one", "A1", "A3") : hold("reclaim-pair-two", "A2", "A4"));
        assertThat(results).allMatch(Booking.class::isInstance);
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM show_seat WHERE booking_id IS NOT NULL").getSingleResult()).toString()).isEqualTo("4");
    }

    @Test
    void sameSeatExactlyOneWinner() throws Exception {
        var result = race(12, i -> hold("concurrent-" + i, "A1"));
        assertThat(result.stream().filter(Booking.class::isInstance).count()).isEqualTo(1);
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM booking").getSingleResult()).toString()).isEqualTo("1");
    }

    @Test
    void overlappingSeatsAreAtomic() throws Exception {
        var result = race(2, i -> i == 0 ? hold("overlap-one", "A1", "A2") : hold("overlap-two", "A2", "A3"));
        assertThat(result.stream().filter(Booking.class::isInstance).count()).isEqualTo(1);
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM show_seat WHERE booking_id IS NOT NULL").getSingleResult()).toString()).isEqualTo("2");
    }

    @Test
    void keysSurviveRetriesAndRejectDifferentPayload() throws Exception {
        var result = race(4, i -> hold("same-key-123", "A1"));
        assertThat(result).allMatch(Booking.class::isInstance);
        assertThat(result.stream().map(x -> ((Booking) x).id()).distinct().count()).isEqualTo(1);
        assertThatThrownBy(() -> hold("same-key-123", "A2")).hasMessage("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void expirySurvivesNewApplicationService() {
        var b = hold("restart-hold", "A1");
        time.now = time.now.plusSeconds(301);
        var restarted = new Bookings(store, time, Duration.ofSeconds(300));
        tx.execute(() -> {
            restarted.expireShow(show);
            return true;
        });
        assertThat(tx.execute(() -> store.get(b.id())).status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(hold("after-restart", "A1")).isNotNull();
    }

    @Test
    void paymentExpiryRaceNeverConfirmsExpiredHold() throws Exception {
        var b = hold("expiry-race", "A1");
        tx.execute(() -> bookings.pay("alice", b.id(), "DELAY"));
        time.now = time.now.plusSeconds(301);
        race(2, i -> tx.execute(() -> {
            if (i == 0) bookings.expireShow(show);
            else bookings.outcome(b.id(), true);
            return true;
        }));
        var result = tx.execute(() -> store.get(b.id()));
        assertThat(result.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(result.refund()).isEqualTo(RefundStatus.PENDING);
        assertThat(tx.execute(() -> tickets.tickets(b.id()))).isEmpty();
    }

    @Test
    void paymentCancellationRaceAlwaysRefunds() throws Exception {
        var b = hold("cancel-race", "A1");
        tx.execute(() -> bookings.pay("alice", b.id(), "SUCCESS"));
        race(2, i -> tx.execute(() -> i == 0 ? bookings.cancel("alice", b.id()) : bookings.outcome(b.id(), true)));
        var result = tx.execute(() -> store.get(b.id()));
        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.refund()).isEqualTo(RefundStatus.PENDING);
        assertThat(hold("reuse-cancelled", "A1")).isNotNull();
    }

    @Test
    void delayedProviderIsReconciledAndCompensated() {
        var b = hold("delayed-provider", "A1");
        tx.execute(() -> bookings.pay("alice", b.id(), "DELAY"));
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).payment()).isEqualTo(PaymentStatus.UNKNOWN);
        time.now = time.now.plusSeconds(361);
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).refund()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void duplicateOutOfOrderEventsAreMonotonic() {
        var b = hold("events-case", "A1");
        tx.execute(() -> bookings.cancel("alice", b.id()));
        var payloads = tx.execute(() -> sql.rows("SELECT payload,aggregate_version FROM outbox ORDER BY aggregate_version"));
        String first = envelope((String) payloads.getFirst()[0]), last = envelope((String) payloads.getLast()[0]);
        consumer.accept(last);
        consumer.accept(first);
        consumer.accept(last);
        assertThat(tx.execute(() -> sql.query("SELECT status FROM notification").getSingleResult())).isEqualTo("CANCELLED");
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM consumer_receipt").getSingleResult()).toString()).isEqualTo("2");
        assertThatThrownBy(() -> consumer.accept("{}"));
    }

    String envelope(String detail) {
        return "{\"source\":\"cinema.booking\",\"detail-type\":\"BookingChanged.v1\",\"detail\":" + detail + "}";
    }

    @Test
    void outboxLeaseRecoversPublicationCrashAndRejectsStaleMark() {
        hold("outbox-case", "A1");
        var first = outbox.claim().getFirst();
        assertThat(outbox.claim()).isEmpty();
        time.now = time.now.plusSeconds(61);
        var second = outbox.claim().getFirst();
        assertThat(second.id()).isEqualTo(first.id());
        outbox.mark(first, null);
        assertThat(tx.execute(() -> sql.query("SELECT delivered_at FROM outbox").getSingleResult())).isNull();
        outbox.mark(second, null);
        assertThat(outbox.claim()).isEmpty();
    }

    @Test
    void outboxRetriesFailedEntriesAndOutages() {
        hold("publish-case", "A1");
        when(events.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder().failedEntryCount(1).entries(PutEventsResultEntry.builder().errorCode("ThrottlingException").build()).build());
        outbox.publish();
        assertThat(tx.execute(() -> sql.query("SELECT delivered_at FROM outbox").getSingleResult())).isNull();
        time.now = time.now.plusSeconds(10);
        when(events.putEvents(any(PutEventsRequest.class))).thenThrow(new RuntimeException("outage"));
        assertThatThrownBy(outbox::publish);
        time.now = time.now.plusSeconds(10);
        when(events.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder().failedEntryCount(0).entries(PutEventsResultEntry.builder().eventId("accepted").build()).build());
        outbox.publish();
        assertThat(tx.execute(() -> sql.query("SELECT delivered_at FROM outbox").getSingleResult())).isNotNull();
    }

    @Test
    void apiOwnershipRolesValidationAndJwt() {
        var b = hold("owned-booking", "A1");
        web.get().uri("/api/bookings/" + b.id()).headers(h -> h.setBearerAuth(token("bob"))).exchange().expectStatus().isNotFound();
        web.post().uri("/api/bookings/" + b.id() + "/cancel").headers(h -> h.setBearerAuth(token("bob"))).exchange().expectStatus().isNotFound();
        web.post().uri("/api/admin/movies").headers(h -> h.setBearerAuth(token("alice"))).bodyValue(Map.of("title", "x", "durationMinutes", 10)).exchange().expectStatus().isForbidden();
        web.get().uri("/api/movies?size=1000").headers(h -> h.setBearerAuth(token("alice"))).exchange().expectStatus().isBadRequest();
        web.get().uri("/api/movies").headers(h -> h.setBearerAuth(token("alice", "wrong", Instant.now().plusSeconds(500)))).exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/movies").headers(h -> h.setBearerAuth(token("alice", "cinema", Instant.now().minusSeconds(120)))).exchange().expectStatus().isUnauthorized();
        web.get().uri("/api/movies").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void httpHappyPath() {
        var response = web.post().uri("/api/bookings").headers(h -> {
            h.setBearerAuth(token("alice"));
            h.set("Idempotency-Key", "http-happy-path");
        }).bodyValue(Map.of("showId", show, "seats", List.of("A1"))).exchange().expectStatus().isOk().expectBody(Booking.class).returnResult().getResponseBody();
        web.post().uri("/api/bookings/" + response.id() + "/payment").headers(h -> h.setBearerAuth(token("alice"))).bodyValue(Map.of("mode", "SUCCESS")).exchange().expectStatus().isOk();
        reconciler.run();
        web.get().uri("/api/bookings/" + response.id() + "/tickets").headers(h -> h.setBearerAuth(token("alice"))).exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void liveReconnectRefreshesAndDoesNotExposeOwners() {
        String uri = "/api/live/shows/" + show;
        var first = web.get().uri(uri).headers(h -> h.setBearerAuth(token("alice"))).exchange().expectStatus().isOk().returnResult(String.class).getResponseBody();
        StepVerifier.create(first.take(1)).assertNext(s -> assertThat(s).contains("AVAILABLE").doesNotContain("alice")).verifyComplete();
        hold("live-refresh", "A1");
        var next = web.get().uri(uri).headers(h -> {
            h.setBearerAuth(token("bob"));
            h.set("Last-Event-ID", "missed-event");
        }).exchange().expectStatus().isOk().returnResult(String.class).getResponseBody();
        StepVerifier.create(next.take(1)).assertNext(s -> assertThat(s).contains("HELD")).verifyComplete();
    }

    @Test
    void blockingBoundaryRunsEntireTransactionOffEventLoop() {
        StepVerifier.create(boundary.call(() -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(Thread.currentThread().getName()).startsWith("jpa-");
            return catalog.movies("", 0, 10);
        }).subscribeOn(Schedulers.parallel())).expectNextCount(1).verifyComplete();
        StepVerifier.create(Mono.fromCallable(() -> tx.execute(() -> true)).subscribeOn(Schedulers.parallel())).expectErrorMatches(e -> e.getMessage().equals("JPA_ON_EVENT_LOOP")).verify();
    }

    @Test
    void consumerAcknowledgesOnlyCommittedValidMessages() {
        var b = hold("sqs-ack-test", "A1");
        String payload = tx.execute(() -> (String) sql.query("SELECT payload FROM outbox WHERE aggregate_id=?1", b.id()).getSingleResult());
        when(sqs.receiveMessage(any(software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest.class))).thenReturn(
                software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse.builder().messages(
                        software.amazon.awssdk.services.sqs.model.Message.builder().messageId("good").receiptHandle("good-receipt").body(envelope(payload)).build(),
                        software.amazon.awssdk.services.sqs.model.Message.builder().messageId("bad").receiptHandle("bad-receipt").body("{}").build()).build());
        consumer.poll();
        verify(sqs, times(1)).deleteMessage(org.mockito.ArgumentMatchers.<java.util.function.Consumer<software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.Builder>>any());
        verify(sqs, times(1)).changeMessageVisibility(org.mockito.ArgumentMatchers.<java.util.function.Consumer<software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest.Builder>>any());
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM consumer_receipt").getSingleResult()).toString()).isEqualTo("1");
    }

    @Test
    void mixedEventBridgeResultsOnlyMarkSuccessfulEntry() {
        hold("mixed-outbox-1", "A1");
        hold("mixed-outbox-2", "A2");
        when(events.putEvents(any(PutEventsRequest.class))).thenReturn(PutEventsResponse.builder().failedEntryCount(1).entries(
                PutEventsResultEntry.builder().eventId("success").build(), PutEventsResultEntry.builder().errorCode("ThrottlingException").build()).build());
        outbox.publish();
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM outbox WHERE delivered_at IS NOT NULL").getSingleResult()).toString()).isEqualTo("1");
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM outbox WHERE delivered_at IS NULL").getSingleResult()).toString()).isEqualTo("1");
    }

    @Test
    void callbackSignatureRejectsForgeryAndDuplicateDoesNotIssueExtraTicket() throws Exception {
        var b = hold("callback-test", "A1");
        tx.execute(() -> bookings.pay("alice", b.id(), "SUCCESS"));
        String body = "{\"bookingId\":\"" + b.id() + "\",\"success\":true}";
        long timestamp = time.instant().getEpochSecond();
        web.post().uri("/api/simulator/callback").header("X-Timestamp", Long.toString(timestamp)).header("X-Signature", "00").header("Content-Type", "application/json").bodyValue(body).exchange().expectStatus().isBadRequest();
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-callback-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        for (int i = 0; i < 2; i++)
            web.post().uri("/api/simulator/callback").header("X-Timestamp", Long.toString(timestamp)).header("X-Signature", signature).header("Content-Type", "application/json").bodyValue(body).exchange().expectStatus().isOk();
        assertThat(tx.execute(() -> tickets.tickets(b.id()))).hasSize(1);
        tx.execute(() -> bookings.cancel("alice", b.id()));
        reconciler.run();
        assertThat(tx.execute(() -> store.get(b.id())).refund()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void failedMessageRollsBackDeduplicationReceipt() {
        var b = hold("rollback-event", "A1");
        String payload = tx.execute(() -> (String) sql.query("SELECT payload FROM outbox WHERE aggregate_id=?1", b.id()).getSingleResult());
        String missingAggregate = payload.replace(b.id().toString(), UUID.randomUUID().toString());
        assertThatThrownBy(() -> consumer.accept(envelope(missingAggregate)));
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM consumer_receipt").getSingleResult()).toString()).isEqualTo("0");
    }

    @Test
    void traceContextIsPersistedForAsynchronousDelivery() {
        String traceId = "11111111111111111111111111111111";
        var context = io.opentelemetry.api.trace.SpanContext.create(traceId, "2222222222222222", io.opentelemetry.api.trace.TraceFlags.getSampled(), io.opentelemetry.api.trace.TraceState.getDefault());
        Booking b;
        try (var scope = io.opentelemetry.api.trace.Span.wrap(context).makeCurrent()) {
            b = hold("traced-booking", "A1");
        }
        String payload = tx.execute(() -> (String) sql.query("SELECT payload FROM outbox WHERE aggregate_id=?1", b.id()).getSingleResult());
        assertThat(payload).contains(traceId);
        consumer.accept(envelope(payload));
        web.get().uri("/api/notifications").headers(h -> h.setBearerAuth(token("alice"))).exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(1);
        web.get().uri("/api/notifications").headers(h -> h.setBearerAuth(token("bob"))).exchange().expectStatus().isOk().expectBody().jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void simulatorFailureReleasesAndDuplicateSuccessKeepsOneCharge() {
        var failure = hold("provider-failure", "A1");
        tx.execute(() -> bookings.pay("alice", failure.id(), "FAILURE"));
        reconciler.run();
        assertThat(tx.execute(() -> store.get(failure.id())).status()).isEqualTo(BookingStatus.FAILED);
        var duplicate = hold("provider-duplicate", "A1");
        tx.execute(() -> bookings.pay("alice", duplicate.id(), "DUPLICATE"));
        reconciler.run();
        reconciler.run();
        assertThat(tx.execute(() -> tickets.tickets(duplicate.id()))).hasSize(1);
        assertThat(tx.execute(() -> sql.query("SELECT count(*) FROM simulated_charge WHERE id=?1", duplicate.id()).getSingleResult()).toString()).isEqualTo("1");
    }

    @Test
    void chunkedBodyCannotBypassRequestSizeLimit() {
        String payload = "{\"showId\":\"" + show + "\",\"seats\":[\"A1\"],\"padding\":\"" + "x".repeat(70000) + "\"}";
        var buffer = new org.springframework.core.io.buffer.DefaultDataBufferFactory().wrap(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        web.post().uri("/api/bookings").headers(h -> {
                    h.setBearerAuth(token("alice"));
                    h.set("Idempotency-Key", "oversized-chunked");
                })
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(reactor.core.publisher.Flux.just(buffer), org.springframework.core.io.buffer.DataBuffer.class)
                .exchange().expectStatus().isEqualTo(413).expectBody().jsonPath("$.code").isEqualTo("REQUEST_TOO_LARGE");
    }

    List<Object> race(int count, java.util.function.IntFunction<Object> work) throws Exception {
        try (var executor = Executors.newFixedThreadPool(count)) {
            var gate = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int n = i;
                futures.add(executor.submit(() -> {
                    gate.await();
                    try {
                        return work.apply(n);
                    } catch (Problem e) {
                        return e.code;
                    }
                }));
            }
            gate.countDown();
            List<Object> result = new ArrayList<>();
            for (var f : futures) result.add(f.get(20, TimeUnit.SECONDS));
            return result;
        }
    }
}
