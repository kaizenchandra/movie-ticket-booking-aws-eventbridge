package com.kaizenchandra.awseventbridgedemo.shared.domain;

public class Problem extends RuntimeException {
    public final String code;

    public Problem(String code) {
        super(code);
        this.code = code;
    }

    public static void require(boolean condition, String code) {
        if (!condition) throw new Problem(code);
    }
}
