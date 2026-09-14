package com.kaizenchandra.awseventbridgedemo.shared.domain;

import java.util.Currency;

public record Money(long minor, String currency) {
    public Money {
        Problem.require(minor >= 0, "INVALID_PRICE");
        Currency.getInstance(currency);
    }

    public Money times(int count) {
        return new Money(Math.multiplyExact(minor, count), currency);
    }
}
