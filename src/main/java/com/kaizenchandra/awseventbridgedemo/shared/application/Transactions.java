package com.kaizenchandra.awseventbridgedemo.shared.application;

import java.util.function.Supplier;

public interface Transactions {
    <T> T execute(Supplier<T> operation);
}
