package com.bcad.h2h.iso8583.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class IsoDateTimeUtil {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    private static final DateTimeFormatter TRANSMISSION_DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final DateTimeFormatter LOCAL_TIME_FMT =
            DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter LOCAL_DATE_FMT =
            DateTimeFormatter.ofPattern("MMdd");

    /**
     * DE7: Transmission Date & Time — MMDDhhmmss (10 chars)
     * Input: LocalDateTime (treated as WIB)
     */
    public String toTransmissionDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now(WIB);
        }
        ZonedDateTime zdt = dateTime.atZone(WIB);
        return zdt.format(TRANSMISSION_DATE_TIME_FMT);
    }

    /**
     * DE12: Local Transaction Time — hhmmss (6 chars)
     */
    public String toLocalTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now(WIB);
        }
        ZonedDateTime zdt = dateTime.atZone(WIB);
        return zdt.format(LOCAL_TIME_FMT);
    }

    /**
     * DE13, DE15, DE17: Local Transaction/Settlement/Capture Date — MMDD (4 chars)
     */
    public String toLocalDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now(WIB);
        }
        ZonedDateTime zdt = dateTime.atZone(WIB);
        return zdt.format(LOCAL_DATE_FMT);
    }

    /**
     * Convert ISO 8583 MMDDhhmmss back to LocalDateTime.
     * Year is assumed to be current year.
     */
    public LocalDateTime fromTransmissionDateTime(String mmddhhmmss) {
        if (mmddhhmmss == null || mmddhhmmss.length() != 10) {
            log.warn("Invalid transmission date/time: {}", mmddhhmmss);
            return LocalDateTime.now(WIB);
        }
        try {
            int month = Integer.parseInt(mmddhhmmss.substring(0, 2));
            int day = Integer.parseInt(mmddhhmmss.substring(2, 4));
            int hour = Integer.parseInt(mmddhhmmss.substring(4, 6));
            int minute = Integer.parseInt(mmddhhmmss.substring(6, 8));
            int second = Integer.parseInt(mmddhhmmss.substring(8, 10));
            int year = LocalDateTime.now(WIB).getYear();
            return LocalDateTime.of(year, month, day, hour, minute, second);
        } catch (Exception e) {
            log.warn("Failed to parse transmission date/time '{}': {}", mmddhhmmss, e.getMessage());
            return LocalDateTime.now(WIB);
        }
    }

    /**
     * Get current WIB time as LocalDateTime.
     */
    public LocalDateTime nowWib() {
        return LocalDateTime.now(WIB);
    }
}
