package com.kaizenchandra.awseventbridgedemo;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

import com.kaizenchandra.awseventbridgedemo.booking.domain.*;
import com.kaizenchandra.awseventbridgedemo.shared.domain.*;
import com.kaizenchandra.awseventbridgedemo.payments.domain.*;

class BookingTest {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    Booking held() {
        return Booking.hold(UUID.randomUUID(), UUID.randomUUID(), "alice", List.of("A1", "A2"), new Money(15000, "INR"), now.plusSeconds(10));
    }

    @Test
    void snapshotsPriceAndIssuesOnlyOneConfirmation() {
        var b = held().initiate(now, "SUCCESS").outcome(true, now);
        assertThat(b.total().minor()).isEqualTo(30000);
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.outcome(true, now)).isSameAs(b);
        assertThat(b.outcome(false, now)).isSameAs(b);
    }

    @Test
    void expiryBoundaryCompensates() {
        var b = held().initiate(now, "DELAY").outcome(true, now.plusSeconds(10));
        assertThat(b.status()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(b.refund()).isEqualTo(RefundStatus.PENDING);
        assertThat(b.ownsSeats()).isFalse();
    }

    @Test
    void cancellationAndLateSuccessCompensate() {
        var b = held().initiate(now, "SUCCESS").cancel(now, now.plusSeconds(50)).outcome(true, now);
        assertThat(b.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(b.refund()).isEqualTo(RefundStatus.PENDING);
        assertThat(b.refunded().refunded().refund()).isEqualTo(RefundStatus.SUCCEEDED);
    }

    @Test
    void lateFailureDoesNotUndoSuccess() {
        var b = held().initiate(now, "SUCCESS").outcome(true, now);
        assertThat(b.outcome(false, now)).isEqualTo(b);
    }

    @Test
    void successAfterDefinitiveFailureRefunds() {
        var b = held().initiate(now, "SUCCESS").outcome(false, now).outcome(true, now);
        assertThat(b.status()).isEqualTo(BookingStatus.FAILED);
        assertThat(b.refund()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void rejectsInvalidSeatsAndExpiredPayment() {
        assertThatThrownBy(() -> Booking.hold(UUID.randomUUID(), UUID.randomUUID(), "alice", List.of("A1", "A1"), new Money(1, "INR"), now)).isInstanceOf(Problem.class);
        assertThatThrownBy(() -> held().initiate(now.plusSeconds(11), "SUCCESS")).hasMessage("HOLD_EXPIRED");
    }

    @Test
    void retryModeCannotChangeCharge() {
        var b = held().initiate(now, "SUCCESS");
        assertThatThrownBy(() -> b.initiate(now, "FAILURE")).hasMessage("IDEMPOTENCY_CONFLICT");
    }
}
