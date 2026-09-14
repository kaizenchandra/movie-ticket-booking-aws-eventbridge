package com.kaizenchandra.awseventbridgedemo.payments.adapter.out;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.util.*;
import java.time.*;

import com.kaizenchandra.awseventbridgedemo.payments.application.PaymentProvider;
import com.kaizenchandra.awseventbridgedemo.shared.application.Transactions;
import com.kaizenchandra.awseventbridgedemo.shared.adapter.out.Sql;
import com.kaizenchandra.awseventbridgedemo.shared.domain.*;

@Component
@Profile({"local", "test"})
public class LocalPaymentSimulator implements PaymentProvider {
    private final Transactions tx;
    private final Sql sql;
    private final Clock clock;

    public LocalPaymentSimulator(Transactions tx, Sql sql, Clock clock) {
        this.tx = tx;
        this.sql = sql;
        this.clock = clock;
    }

    private void outsideTransaction() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PROVIDER_IN_TRANSACTION");
    }

    public Outcome charge(UUID id, Money amount, String scenario) {
        outsideTransaction();
        return tx.execute(() -> {
            sql.update("INSERT INTO simulated_charge(id,price_minor,currency,scenario,ready_at) VALUES(?1,?2,?3,?4,?5) ON CONFLICT DO NOTHING", id, amount.minor(), amount.currency(), scenario, clock.instant().plusSeconds(scenario.equals("DELAY") ? 360 : 0));
            var r = sql.rows("SELECT price_minor,currency,scenario,ready_at FROM simulated_charge WHERE id=?1", id).getFirst();
            Problem.require(((Number) r[0]).longValue() == amount.minor() && r[1].equals(amount.currency()) && r[2].equals(scenario), "PROVIDER_KEY_CONFLICT");
            if (clock.instant().isBefore(Sql.instant(r[3]))) return Outcome.UNKNOWN;
            return scenario.equals("FAILURE") ? Outcome.FAILURE : Outcome.SUCCESS;
        });
    }

    public boolean refund(UUID id, Money amount, String scenario) {
        outsideTransaction();
        return tx.execute(() -> {
            var rows = sql.rows("SELECT price_minor,currency,refunded,refund_attempts FROM simulated_charge WHERE id=?1 FOR UPDATE", id);
            Problem.require(!rows.isEmpty(), "CHARGE_NOT_FOUND");
            var r = rows.getFirst();
            Problem.require(((Number) r[0]).longValue() == amount.minor() && r[1].equals(amount.currency()), "PROVIDER_KEY_CONFLICT");
            if ((Boolean) r[2]) return true;
            sql.update("UPDATE simulated_charge SET refund_attempts=refund_attempts+1 WHERE id=?1", id);
            if (scenario.equals("REFUND_RETRY") && ((Number) r[3]).intValue() == 0) return false;
            sql.update("UPDATE simulated_charge SET refunded=true WHERE id=?1", id);
            return true;
        });
    }
}
