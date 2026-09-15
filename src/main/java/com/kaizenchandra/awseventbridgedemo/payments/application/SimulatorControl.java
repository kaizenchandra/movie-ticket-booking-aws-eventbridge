package com.kaizenchandra.awseventbridgedemo.payments.application;

import java.util.UUID;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

/**
 * Local-provider settlement control; never installed by the production profile.
 */
public interface SimulatorControl {
    boolean settle(UUID id, Money amount, String scenario, boolean success);
}
