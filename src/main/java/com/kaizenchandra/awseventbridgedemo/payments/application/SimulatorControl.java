package com.kaizenchandra.awseventbridgedemo.payments.application;

import com.kaizenchandra.awseventbridgedemo.shared.domain.Money;

import java.util.UUID;

/**
 * Local-provider settlement control; never installed by the production profile.
 */
public interface SimulatorControl {
    boolean settle(UUID id, Money amount, String scenario, boolean success);
}
