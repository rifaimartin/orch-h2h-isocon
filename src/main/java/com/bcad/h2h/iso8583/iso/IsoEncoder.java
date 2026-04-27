package com.bcad.h2h.iso8583.iso;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.exception.IsoMessageParseException;
import com.bcad.h2h.iso8583.iso.packager.BcadIsoPackager;
import com.bcad.h2h.iso8583.iso.packager.FieldDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class IsoEncoder {

    private static final BcadIsoPackager PACKAGER = BcadIsoPackager.getInstance();
    private final TcpSocketProperties properties;

    public byte[] encode(IsoMessage message) {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();

            // Write BIC ISO External Message Header (12 bytes) based on MTI
            String bicHeader = buildBicHeader(message.getMti());
            if (bicHeader != null) {
                body.write(bicHeader.getBytes(StandardCharsets.ISO_8859_1));
            }

            // Write MTI (4 bytes ASCII)
            body.write(message.getMti().getBytes(StandardCharsets.ISO_8859_1));

            // Build bitmaps
            TreeSet<Integer> fieldNums = new TreeSet<>(message.getBitmapFields());
            boolean needSecondary = fieldNums.stream().anyMatch(f -> f > 64);

            byte[] primaryBitmap = new byte[8];
            byte[] secondaryBitmap = new byte[8];

            if (needSecondary) {
                primaryBitmap[0] |= (byte) 0x80;
            }

            for (int fieldNum : fieldNums) {
                if (fieldNum < 1 || fieldNum > 128) continue;
                if (fieldNum == 1 || fieldNum == 65) continue;

                if (fieldNum <= 64) {
                    int bitPos = fieldNum - 1;
                    int byteIndex = bitPos / 8;
                    int bitIndex = 7 - (bitPos % 8);
                    primaryBitmap[byteIndex] |= (byte) (1 << bitIndex);
                } else {
                    int bitPos = fieldNum - 65;
                    int byteIndex = bitPos / 8;
                    int bitIndex = 7 - (bitPos % 8);
                    secondaryBitmap[byteIndex] |= (byte) (1 << bitIndex);
                }
            }

            // Write bitmap — hex ASCII (BASE24 BCAD) or binary (standard ISO 8583)
            if (properties.isHexBitmap()) {
                // Hex ASCII: each bitmap byte -> 2 hex chars, e.g. 0x82 -> "82"
                body.write(bytesToHex(primaryBitmap).getBytes(StandardCharsets.ISO_8859_1));
                if (needSecondary) {
                    body.write(bytesToHex(secondaryBitmap).getBytes(StandardCharsets.ISO_8859_1));
                }
            } else {
                // Binary: raw 8/16 bytes
                body.write(primaryBitmap);
                if (needSecondary) {
                    body.write(secondaryBitmap);
                }
            }

            // Write each field
            for (int fieldNum : fieldNums) {
                if (fieldNum == 1 || fieldNum == 65) continue;

                String value = message.getField(fieldNum);
                if (value == null) continue;

                FieldDefinition def = PACKAGER.getFieldDefinition(fieldNum);
                if (def == null) {
                    log.warn("No field definition for DE{}, skipping", fieldNum);
                    continue;
                }

                byte[] fieldBytes = encodeField(fieldNum, def, value);
                body.write(fieldBytes);
            }

            byte[] bodyBytes = body.toByteArray();

            log.info("=== ENCODED BODY (ASCII) ===");
            log.info("[{}]", new String(bodyBytes, StandardCharsets.ISO_8859_1));
            log.info("body length={}", bodyBytes.length);

            // Prepend 2-byte big-endian length header
            ByteArrayOutputStream finalMsg = new ByteArrayOutputStream();
            int length = bodyBytes.length;
            finalMsg.write((length >> 8) & 0xFF);
            finalMsg.write(length & 0xFF);
            finalMsg.write(bodyBytes);

            byte[] result = finalMsg.toByteArray();
            log.debug("Encoded ISO message MTI={} length={} HEX[{}]",
                    message.getMti(), length, bytesToHex(result));
            return result;

        } catch (IOException e) {
            throw new IsoMessageParseException("Failed to encode ISO message: " + e.getMessage(), e);
        }
    }

    /**
     * Builds 12-byte BIC ISO External Message Header per BCA spec.
     * Format: BASE24("ISO") + ProductIndicator(2) + Release("50") + Status("000") + Originator(1) + Responder(1)
     *
     * 0200 (request):  ISO 01 50 000 1 0 → "ISO015000010"
     * 0210 (response): ISO 01 50 000 1 3 → "ISO015000013"
     * 0800 (request):  ISO 00 50 000 6 0 → "ISO005000060"
     * 0810 (response): ISO 00 50 000 6 6 → "ISO005000066"
     */
    private String buildBicHeader(String mti) {
        if (!properties.isBicHeaderEnabled()) {
            return null;
        }
        String productIndicator;
        String originatorCode;
        String responderCode;

        switch (mti) {
            case "0200" -> { productIndicator = "01"; originatorCode = "1"; responderCode = "0"; }
            case "0210" -> { productIndicator = "01"; originatorCode = "1"; responderCode = "3"; }
            case "0800" -> { productIndicator = "00"; originatorCode = "6"; responderCode = "0"; }
            case "0810" -> { productIndicator = "00"; originatorCode = "6"; responderCode = "6"; }
            default -> {
                log.warn("Unknown MTI {} for BIC header, falling back to NMM format", mti);
                productIndicator = "00"; originatorCode = "6"; responderCode = "0";
            }
        }
        return "ISO" + productIndicator + "50" + "000" + originatorCode + responderCode;
    }

    private byte[] encodeField(int fieldNum, FieldDefinition def, String value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        switch (def.getType()) {
            case NUMERIC -> {
                String padded = padLeft(value, def.getMaxLength(), '0');
                if (padded.length() > def.getMaxLength()) {
                    padded = padded.substring(padded.length() - def.getMaxLength());
                }
                out.write(padded.getBytes(StandardCharsets.ISO_8859_1));
            }
            case ALPHA -> {
                String padded = padRight(value, def.getMaxLength(), ' ');
                if (padded.length() > def.getMaxLength()) {
                    padded = padded.substring(0, def.getMaxLength());
                }
                out.write(padded.getBytes(StandardCharsets.ISO_8859_1));
            }
            case ALPHANUM -> {
                String padded = padRight(value, def.getMaxLength(), ' ');
                if (padded.length() > def.getMaxLength()) {
                    padded = padded.substring(0, def.getMaxLength());
                }
                out.write(padded.getBytes(StandardCharsets.ISO_8859_1));
            }
            case LLVAR -> {
                byte[] data = value.getBytes(StandardCharsets.ISO_8859_1);
                String lenStr = String.format("%02d", data.length);
                out.write(lenStr.getBytes(StandardCharsets.ISO_8859_1));
                out.write(data);
            }
            case LLLVAR -> {
                byte[] data = value.getBytes(StandardCharsets.ISO_8859_1);
                String lenStr = String.format("%03d", data.length);
                out.write(lenStr.getBytes(StandardCharsets.ISO_8859_1));
                out.write(data);
            }
            case BINARY -> {
                byte[] data = hexToBytes(value);
                out.write(data);
            }
            default -> out.write(value.getBytes(StandardCharsets.ISO_8859_1));
        }

        return out.toByteArray();
    }

    private String padLeft(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value;
        StringBuilder sb = new StringBuilder();
        for (int i = value.length(); i < length; i++) sb.append(padChar);
        sb.append(value);
        return sb.toString();
    }

    private String padRight(String value, int length, char padChar) {
        if (value == null) value = "";
        if (value.length() >= length) return value;
        StringBuilder sb = new StringBuilder(value);
        for (int i = value.length(); i < length; i++) sb.append(padChar);
        return sb.toString();
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
