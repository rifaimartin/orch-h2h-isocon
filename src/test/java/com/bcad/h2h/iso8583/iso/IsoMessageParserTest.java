package com.bcad.h2h.iso8583.iso;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
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
        TcpSocketProperties props = new TcpSocketProperties();
        props.setBicHeaderEnabled(false); // no BIC header for unit tests
        encoder = new IsoEncoder(props);
        decoder = new IsoDecoder(props);
    }

    @Test
    @DisplayName("Parse 0200 Inquiry message with PIN - success")
    void parse0200InquiryWithPinSuccess() {
        IsoMessage msg = new IsoMessage("0200");
        msg.setField(3, "310000");
        msg.setField(11, "000001");
        msg.setField(52, "0123456789ABCDEF");
        msg.setField(102, "1234567890");
        msg.setField(103, "0987654321");

        assertNotNull(msg.getMti());
        assertEquals("0200", msg.getMti());
        assertEquals("0123456789ABCDEF", msg.getField(52));
        assertTrue(msg.hasField(52));
        
        // Test Encoding DE52 (Binary/Fixed 8 bytes -> 16 hex chars)
        byte[] encoded = encoder.encode(msg);
        assertNotNull(encoded);
        
        IsoMessage decoded = decoder.decode(encoded);
        assertEquals("0123456789ABCDEF", decoded.getField(52));
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

    @Test
    @DisplayName("Encode/decode round-trip WITH ISO header + hex bitmap (BASE24 BCAD)")
    void encodeThenDecodeWithIsoHeader() {
        TcpSocketProperties headerProps = new TcpSocketProperties();
        headerProps.setBicHeaderEnabled(true);
        headerProps.setHexBitmap(true);
        IsoEncoder headerEncoder = new IsoEncoder(headerProps);
        IsoDecoder headerDecoder = new IsoDecoder(headerProps);

        // Build 0800 Logon
        IsoMessage original = new IsoMessage("0800");
        original.setField(7, "0415163148");
        original.setField(11, "000001");
        original.setField(70, "001");

        byte[] encoded = headerEncoder.encode(original);
        assertNotNull(encoded);

        // Verify ISO header "ISO005000060" is present after 2-byte length
        String bodyStr = new String(encoded, 2, encoded.length - 2, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(bodyStr.startsWith("ISO005000060"), "ISO header must be prepended");

        // Verify bitmap is hex ASCII (all printable), NOT binary
        // After header(12) + MTI(4) = offset 16 from body start
        String bitmapArea = bodyStr.substring(16, 48); // 32 chars for full hex bitmap
        assertTrue(bitmapArea.matches("[0-9A-F]+"), "Bitmap must be hex ASCII: " + bitmapArea);

        // Verify entire body is printable ASCII (no binary bytes)
        for (int i = 2; i < encoded.length; i++) {
            assertTrue(encoded[i] >= 0x20 && encoded[i] < 0x7F,
                    "Byte at pos " + i + " must be printable ASCII, got: 0x" + String.format("%02X", encoded[i]));
        }

        // Verify 2-byte length includes header
        int declaredLength = ((encoded[0] & 0xFF) << 8) | (encoded[1] & 0xFF);
        assertEquals(encoded.length - 2, declaredLength);

        // Decode must skip the header and parse correctly
        IsoMessage decoded = headerDecoder.decode(encoded);
        assertNotNull(decoded);
        assertEquals("0800", decoded.getMti());
        assertEquals("0415163148", decoded.getField(7).trim());
        assertEquals("000001", decoded.getField(11).trim());
        assertEquals("001", decoded.getField(70).trim());
    }

    @Test
    @DisplayName("BCA Logon format: ISO0050000600800[hex bitmap]...")
    void bcaLogonFormat() {
        TcpSocketProperties bcaProps = new TcpSocketProperties();
        bcaProps.setBicHeaderEnabled(true);
        bcaProps.setHexBitmap(true);
        bcaProps.setTerminalId("BCAD0001");
        IsoEncoder bcaEncoder = new IsoEncoder(bcaProps);

        // Build 0800 Logon — same as BCA example
        IsoMessage logon = new IsoMessage("0800");
        logon.setField(7, "0218073507");  // DE7:  MMDDhhmmss
        logon.setField(11, "000050");     // DE11: STAN
        logon.setField(70, "001");        // DE70: Logon
        logon.setField(123, "BCAD0001");  // DE123: Station ID (match mapNetworkManagement)

        byte[] encoded = bcaEncoder.encode(logon);
        // Skip 2-byte length header for wire content
        String wire = new String(encoded, 2, encoded.length - 2, java.nio.charset.StandardCharsets.ISO_8859_1);

        // Must match BCA expected format:
        // ISO0050000600800 8220000000000000 0400000000000000 0218073507 000050 001
        assertEquals("ISO005000060", wire.substring(0, 12), "ISO header");
        assertEquals("0800", wire.substring(12, 16), "MTI");
        assertEquals("8220000000000000", wire.substring(16, 32), "Primary bitmap hex");
        assertEquals("0400000000000020", wire.substring(32, 48), "Secondary bitmap hex");
        // DE7(10) + DE11(6) + DE70(3) = 19
        assertEquals("0218073507", wire.substring(48, 58), "DE7");
        assertEquals("000050", wire.substring(58, 64), "DE11");
        assertEquals("001", wire.substring(64, 67), "DE70");

        // DE123 check
        assertTrue(wire.length() > 67, "Should have DE123");
        String de123Part = wire.substring(67);
        // LLLVAR "BCAD0001" -> "008BCAD0001"
        assertEquals("008BCAD0001", de123Part, "DE123 format");
    }

    @Test
    @DisplayName("Bitmap must include bit 43 when field 43 is set (Card Acceptor Name/Location)")
    void bitmap_shouldIncludeBit43_whenField43IsSet() {
        TcpSocketProperties bcaProps = new TcpSocketProperties();
        bcaProps.setBicHeaderEnabled(true);
        bcaProps.setHexBitmap(true);
        IsoEncoder bcaEncoder = new IsoEncoder(bcaProps);
        IsoDecoder bcaDecoder = new IsoDecoder(bcaProps);

        // Build a 0200 with field 43 (mandatory per BCAD spec)
        IsoMessage msg = new IsoMessage("0200");
        msg.setField(3, "321000");
        msg.setField(4, "000200000000");
        msg.setField(7, "0209071903");
        msg.setField(11, "367656");
        msg.setField(12, "141902");
        msg.setField(13, "0209");
        msg.setField(17, "0209");
        msg.setField(37, "250209418689");
        msg.setField(41, "ABCD1234EFGH5678");
        msg.setField(43, "MOBILE/INTERNET BANKING                 "); // 40 chars
        msg.setField(49, "360");

        // Verify field 43 is in getBitmapFields()
        assertTrue(msg.getBitmapFields().contains(43), "getBitmapFields() must include 43");

        byte[] encoded = bcaEncoder.encode(msg);
        String wire = new String(encoded, 2, encoded.length - 2, StandardCharsets.ISO_8859_1);

        // Extract primary bitmap hex (after 12-byte BIC header + 4-byte MTI)
        String primaryBitmapHex = wire.substring(16, 32);

        // Byte 5 of primary bitmap (bits 41-48) must have bit 43 set
        // Bit 43 → byteIndex=5, bitMask=0x20
        int byte5 = Integer.parseInt(primaryBitmapHex.substring(10, 12), 16);
        assertTrue((byte5 & 0x20) != 0,
                "Bit 43 must be set in primary bitmap byte[5], got: 0x" + String.format("%02X", byte5));

        // Round-trip: decode and verify field 43 comes back
        IsoMessage decoded = bcaDecoder.decode(encoded);
        assertNotNull(decoded.getField(43), "DE43 must survive encode/decode round-trip");
        assertEquals(40, decoded.getField(43).length(), "DE43 must be exactly 40 chars after decode");
        assertTrue(decoded.getField(43).startsWith("MOBILE/INTERNET BANKING"),
                "DE43 content must be preserved");
    }
}