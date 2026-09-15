package com.kaizenchandra.awseventbridgedemo.payments.application;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

import java.util.UUID;

public interface PaymentProvider {
    Outcome charge(UUID idempotencyKey, Money amount, String scenario);

    boolean refund(UUID chargeKey, Money amount, String scenario);

    enum Outcome {SUCCESS, FAILURE, UNKNOWN}
}
