package com.bcad.h2h.iso8583.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StanGenerator {

    private static final int MAX_STAN = 999999;
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Generate next 6-digit STAN. Thread-safe, wraps at 999999.
     */
    public String next() {
        int value = counter.updateAndGet(current -> {
            int next = current + 1;
            return next > MAX_STAN ? 1 : next;
        });
        return String.format("%06d", value);
    }

    /**
     * Peek at current value without incrementing.
     */
    public String current() {
        return String.format("%06d", counter.get());
    }
}
