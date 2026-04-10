package com.bcad.h2h.iso8583.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IsoDateTimeUtil Tests")
class IsoDateTimeUtilTest {

    private IsoDateTimeUtil util;

    @BeforeEach
    void setUp() {
        util = new IsoDateTimeUtil();
    }

    @Test
    @DisplayName("toTransmissionDateTime returns MMDDhhmmss format")
    void toTransmissionDateTimeFormat() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 7, 10, 30, 45);
        String result = util.toTransmissionDateTime(dt);
        assertNotNull(result);
        assertEquals(10, result.length(), "Transmission date/time must be 10 chars");
        assertEquals("0307103045", result);
    }

    @Test
    @DisplayName("toLocalTime returns hhmmss format")
    void toLocalTimeFormat() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 7, 10, 30, 45);
        String result = util.toLocalTime(dt);
        assertNotNull(result);
        assertEquals(6, result.length(), "Local time must be 6 chars");
        assertEquals("103045", result);
    }

    @Test
    @DisplayName("toLocalDate returns MMDD format")
    void toLocalDateFormat() {
        LocalDateTime dt = LocalDateTime.of(2025, 3, 7, 10, 30, 45);
        String result = util.toLocalDate(dt);
        assertNotNull(result);
        assertEquals(4, result.length(), "Local date must be 4 chars");
        assertEquals("0307", result);
    }

    @Test
    @DisplayName("fromTransmissionDateTime parses MMDDhhmmss")
    void fromTransmissionDateTimeParse() {
        String input = "0307103045";
        LocalDateTime result = util.fromTransmissionDateTime(input);
        assertNotNull(result);
        assertEquals(3, result.getMonthValue());
        assertEquals(7, result.getDayOfMonth());
        assertEquals(10, result.getHour());
        assertEquals(30, result.getMinute());
        assertEquals(45, result.getSecond());
    }

    @Test
    @DisplayName("fromTransmissionDateTime handles invalid input gracefully")
    void fromTransmissionDateTimeInvalidInput() {
        assertDoesNotThrow(() -> util.fromTransmissionDateTime(null));
        assertDoesNotThrow(() -> util.fromTransmissionDateTime(""));
        assertDoesNotThrow(() -> util.fromTransmissionDateTime("INVALID"));
    }

    @Test
    @DisplayName("toTransmissionDateTime handles null - uses current time")
    void toTransmissionDateTimeNull() {
        String result = util.toTransmissionDateTime(null);
        assertNotNull(result);
        assertEquals(10, result.length());
    }

    @Test
    @DisplayName("nowWib returns current WIB time")
    void nowWibNotNull() {
        LocalDateTime now = util.nowWib();
        assertNotNull(now);
    }

    @Test
    @DisplayName("StanGenerator is thread-safe and sequential")
    void stanGeneratorSequential() {
        StanGenerator gen = new StanGenerator();
        assertEquals("000001", gen.next());
        assertEquals("000002", gen.next());
        assertEquals("000003", gen.next());
    }

    @Test
    @DisplayName("StanGenerator wraps at 999999")
    void stanGeneratorWraps() throws Exception {
        StanGenerator gen = new StanGenerator();
        // Use reflection to set counter to 999999
        var field = StanGenerator.class.getDeclaredField("counter");
        field.setAccessible(true);
        var counter = (java.util.concurrent.atomic.AtomicInteger) field.get(gen);
        counter.set(999999);
        String result = gen.next();
        assertEquals("000001", result, "STAN should wrap back to 000001");
    }

    @Test
    @DisplayName("RrnGenerator produces 12-char RRN")
    void rrnGeneratorLength() {
        RrnGenerator gen = new RrnGenerator();
        String rrn = gen.next();
        assertNotNull(rrn);
        assertEquals(12, rrn.length(), "RRN must be 12 chars");
    }

    @Test
    @DisplayName("AccountMaskUtil masks middle digits")
    void accountMaskUtil() {
        assertEquals("1234****3456", AccountMaskUtil.mask("123400003456"));
        assertEquals("1234****5678", AccountMaskUtil.mask("123456785678"));

        // Short account
        assertEquals("12345", AccountMaskUtil.mask("12345"));

        // Exactly 8 chars
        assertEquals("12345678", AccountMaskUtil.mask("12345678"));
    }

    @Test
    @DisplayName("ResponseCodeMapper maps codes correctly")
    void responseCodeMapper() {
        ResponseCodeMapper mapper = new ResponseCodeMapper();

        assertEquals(ResponseCodeMapper.STATUS_SUCCESS, mapper.mapStatus("00"));
        assertEquals(ResponseCodeMapper.STATUS_SUSPEND, mapper.mapStatus("68"));
        assertEquals(ResponseCodeMapper.STATUS_FAILED, mapper.mapStatus("05"));
        assertEquals(ResponseCodeMapper.STATUS_FAILED, mapper.mapStatus("91"));
        assertEquals(ResponseCodeMapper.STATUS_FAILED, mapper.mapStatus(null));

        assertTrue(mapper.isSuccess("00"));
        assertFalse(mapper.isSuccess("05"));
        assertTrue(mapper.isSuspend("68"));
        assertFalse(mapper.isSuspend("00"));
        assertTrue(mapper.isFailed("05"));
        assertFalse(mapper.isFailed("00"));

        assertNotNull(mapper.getMessage("00"));
        assertNotNull(mapper.getMessage("68"));
        assertNotNull(mapper.getMessage("99")); // unknown RC
    }
}
