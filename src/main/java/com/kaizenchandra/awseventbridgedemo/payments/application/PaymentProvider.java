package com.kaizenchandra.awseventbridgedemo.payments.application;

import java.util.UUID;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

public interface PaymentProvider {
    enum Outcome {SUCCESS, FAILURE, UNKNOWN}

    Outcome charge(UUID idempotencyKey, Money amount, String scenario);

    boolean refund(UUID chargeKey, Money amount, String scenario);
}
