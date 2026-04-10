package com.bcad.h2h.iso8583.iso;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ISO Message Parser Tests")
class IsoMessageParserTest {

    private IsoEncoder encoder;
    private IsoDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new IsoEncoder();
        decoder = new IsoDecoder();
    }

    @Test
    @DisplayName("Parse 0200 Inquiry message - success")
    void parse0200InquirySuccess() {
        IsoMessage msg = new IsoMessage("0200");
        msg.setField(3, "310000");
        msg.setField(4, "000000010000");
        msg.setField(7, "0307103045");
        msg.setField(11, "000001");
        msg.setField(12, "103045");
        msg.setField(13, "0307");
        msg.setField(15, "0307");
        msg.setField(17, "0307");
        msg.setField(37, "250307000001");
        msg.setField(41, "BCAD0001");
        msg.setField(42, "BCADIGITAL001  ");
        msg.setField(49, "360");
        msg.setField(102, "1234567890");
        msg.setField(103, "0987654321");

        assertNotNull(msg.getMti());
        assertEquals("0200", msg.getMti());
        assertEquals("310000", msg.getField(3));
        assertEquals("000000010000", msg.getField(4));
        assertEquals("000001", msg.getField(11));
        assertTrue(msg.hasField(102));
        assertTrue(msg.hasField(103));
    }

    @Test
    @DisplayName("Parse 0200 Transfer message with DE126 Token R1")
    void parse0200TransferSuccess() {
        IsoMessage msg = new IsoMessage("0200");
        msg.setField(3, "400000");
        msg.setField(4, "000000100000");
        msg.setField(7, "0307103045");
        msg.setField(11, "000002");
        msg.setField(12, "103045");
        msg.setField(13, "0307");
        msg.setField(15, "0307");
        msg.setField(17, "0307");
        msg.setField(37, "250307000002");
        msg.setField(41, "BCAD0001");
        msg.setField(42, "BCADIGITAL001  ");
        msg.setField(49, "360");
        msg.setField(102, "1234567890");
        msg.setField(103, "0987654321");
        // Token R1: 30+30+18+1+1 = 80 chars minimum
        String token = String.format("%-30s%-30s%-18s%s%s",
                "JOHN DOE", "JANE DOE", "PAYMENT", "D", "I");
        msg.setField(126, token);

        assertNotNull(msg.getMti());
        assertEquals("0200", msg.getMti());
        assertEquals("400000", msg.getField(3));
        assertTrue(msg.hasField(126));
        String storedToken = msg.getField(126);
        assertNotNull(storedToken);
        assertEquals(80, storedToken.length());
        assertTrue(storedToken.startsWith("JOHN DOE"));
    }

    @Test
    @DisplayName("Parse 0210 Response message")
    void parse0210Response() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(3, "310000");
        msg.setField(4, "000000010000");
        msg.setField(7, "0307103045");
        msg.setField(11, "000001");
        msg.setField(12, "103045");
        msg.setField(13, "0307");
        msg.setField(15, "0307");
        msg.setField(37, "250307000001");
        msg.setField(39, "00");
        msg.setField(41, "BCAD0001");
        msg.setField(42, "BCADIGITAL001  ");
        msg.setField(49, "360");

        assertEquals("0210", msg.getMti());
        assertEquals("00", msg.getField(39));
        assertTrue(msg.hasField(39));
    }

    @Test
    @DisplayName("Encode then decode round-trip")
    void encodeThenDecodeRoundTrip() {
        IsoMessage original = new IsoMessage("0200");
        original.setField(3, "310000");
        original.setField(4, "000000010000");
        original.setField(7, "0307103045");
        original.setField(11, "000001");
        original.setField(12, "103045");
        original.setField(13, "0307");
        original.setField(15, "0307");
        original.setField(17, "0307");
        original.setField(37, "250307000001");
        original.setField(41, "BCAD0001");
        original.setField(42, "BCADIGITAL001  ");
        original.setField(49, "360");
        original.setField(102, "1234567890");
        original.setField(103, "0987654321");

        // Encode
        byte[] encoded = encoder.encode(original);
        assertNotNull(encoded);
        assertTrue(encoded.length > 2);

        // Verify 2-byte length header
        int declaredLength = ((encoded[0] & 0xFF) << 8) | (encoded[1] & 0xFF);
        assertEquals(encoded.length - 2, declaredLength);

        // Decode
        IsoMessage decoded = decoder.decode(encoded);
        assertNotNull(decoded);
        assertEquals("0200", decoded.getMti());
        assertEquals("310000", decoded.getField(3).trim());
        assertEquals("000000010000", decoded.getField(4).trim());
        assertEquals("000001", decoded.getField(11).trim());
    }

    @Test
    @DisplayName("IsoMessage field operations")
    void isoMessageFieldOperations() {
        IsoMessage msg = new IsoMessage("0800");
        assertFalse(msg.hasField(70));

        msg.setField(70, "001");
        assertTrue(msg.hasField(70));
        assertEquals("001", msg.getField(70));

        msg.removeField(70);
        assertFalse(msg.hasField(70));
        assertNull(msg.getField(70));
    }
}
