package com.bcad.h2h.iso8583.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RrnGenerator {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MAX_SEQ = 999999;

    private final AtomicInteger sequence = new AtomicInteger(0);
    private volatile String lastDate = "";

    /**
     * Generate a 12-character RRN in format: YYMMDDssssss
     * Resets sequence daily.
     */
    public synchronized String next() {
        String today = LocalDate.now(WIB).format(DATE_FMT);

        if (!today.equals(lastDate)) {
            sequence.set(0);
            lastDate = today;
        }

        int seq = sequence.updateAndGet(v -> v >= MAX_SEQ ? 1 : v + 1);
        return today + String.format("%06d", seq);
    }
}
