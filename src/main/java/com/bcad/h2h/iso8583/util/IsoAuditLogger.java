package com.bcad.h2h.iso8583.util;

import com.bcad.h2h.iso8583.iso.IsoMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Audit logger untuk raw ISO 8583 messages dalam format jPOS XML.
 *
 * Format output:
 * <pre>
 * {@code
 * <log realm="h2h-iso8583-bcad/10.0.38.150:7000" at="2026-04-03T10:30:45.123">
 *   <send>
 *     <isomsg direction="outgoing">
 *       <field id="0" value="0200"/>
 *       <field id="3" value="310000"/>
 *       ...
 *     </isomsg>
 *   </send>
 * </log>
 * }
 * </pre>
 *
 * Log ditulis ke file terpisah: logs/h2h-iso8583-bcad-audit.log
 * Retained selama 90 hari.
 *
 * ⚠️ JANGAN log data sensitif (PIN, CVV, password).
 * ✅ HARUS log: MTI, STAN (DE11), RRN (DE37), RC (DE39), semua field ISO.
 */
public class IsoAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("ISO_AUDIT");
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static final String REALM;

    static {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            host = "unknown";
        }
        REALM = "h2h-iso8583-bcad/" + host;
    }

    private IsoAuditLogger() {}

    /**
     * Log outbound ISO message (request ke BCA).
     *
     * @param msg      IsoMessage yang akan dikirim
     * @param destHost host BCA tujuan
     * @param destPort port BCA tujuan
     */
    public static void logOutbound(IsoMessage msg, String destHost, int destPort) {
        String realm = "h2h-iso8583-bcad/" + destHost + ":" + destPort;
        AUDIT.info(buildXml(realm, "send", "outgoing", msg));
    }

    /**
     * Log inbound ISO message (response dari BCA).
     *
     * @param msg      IsoMessage yang diterima
     * @param srcHost  host BCA asal
     * @param srcPort  port BCA asal
     */
    public static void logInbound(IsoMessage msg, String srcHost, int srcPort) {
        String realm = "h2h-iso8583-bcad/" + srcHost + ":" + srcPort;
        AUDIT.info(buildXml(realm, "receive", "incoming", msg));
    }

    /**
     * Log SUSPEND — RC=68 atau timeout.
     * ⚠️ Kondisi kritis: status tidak diketahui, jangan auto-reverse.
     */
    public static void logSuspend(String context, String stan, String rrn, String reason) {
        AUDIT.warn(buildSuspendXml(context, stan, rrn, reason));
    }

    /**
     * Log error pada level ISO/transport.
     */
    public static void logError(String context, String stan, String rrn, String error) {
        AUDIT.error(buildErrorXml(context, stan, rrn, error));
    }

    /**
     * Log raw ISO wire-format message in hex + parsed representation.
     * Shows: [ISO Header][MTI][BMP:bitmap_hex][Data Elements ASCII]
     * Useful for debugging with BCA — shows exactly what was sent/received on the wire.
     *
     * @param direction "SEND" or "RECV"
     * @param rawBytes  the full encoded ISO bytes (including 2-byte length header)
     * @param destHost  host BCA
     * @param destPort  port BCA
     */
    public static void logRawIso(String direction, byte[] rawBytes, String destHost, int destPort) {
        if (rawBytes == null || rawBytes.length < 2) return;
        String ts = LocalDateTime.now().format(TS_FMT);
        String realm = "h2h-iso8583-bcad/" + destHost + ":" + destPort;

        // Skip 2-byte length header for display
        int bodyLen = rawBytes.length - 2;
        byte[] body = new byte[bodyLen];
        System.arraycopy(rawBytes, 2, body, 0, bodyLen);

        // Build full hex string (for copy-paste to BCA)
        StringBuilder hex = new StringBuilder();
        for (byte b : body) {
            hex.append(String.format("%02X", b));
        }

        // Build parsed wire representation: Header + MTI + [BMP:hex] + fields(ASCII)
        StringBuilder wire = new StringBuilder();
        int pos = 0;

        // Check for ISO header (starts with "ISO", typically 12 bytes)
        if (bodyLen > 15 && body[0] == 'I' && body[1] == 'S' && body[2] == 'O') {
            // Find where MTI starts (first '0' after header that looks like 0xxx)
            int headerLen = 12; // standard BASE24 ISO header length
            wire.append(new String(body, 0, headerLen, java.nio.charset.StandardCharsets.ISO_8859_1));
            pos = headerLen;
        }

        // MTI (4 bytes ASCII)
        if (pos + 4 <= bodyLen) {
            wire.append(new String(body, pos, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
            pos += 4;
        }

        // Bitmap — detect if hex ASCII or binary
        // Hex ASCII bitmap: first 16 chars are all [0-9A-Fa-f]
        // Binary bitmap: first byte is typically 0x82, 0xA2, etc. (non-printable)
        if (pos + 16 <= bodyLen) {
            boolean isHexAscii = isHexAsciiRange(body, pos, 16);

            if (isHexAscii) {
                // Hex ASCII bitmap: read 16 chars primary, check for secondary
                String primaryHex = new String(body, pos, 16, java.nio.charset.StandardCharsets.ISO_8859_1);
                wire.append("[BMP:").append(primaryHex);
                pos += 16;

                // Check secondary: first hex char >= '8' means bit 1 set
                char firstChar = primaryHex.charAt(0);
                boolean hasSecondary = "89ABCDEFabcdef".indexOf(firstChar) >= 0;
                if (hasSecondary && pos + 16 <= bodyLen) {
                    String secondaryHex = new String(body, pos, 16, java.nio.charset.StandardCharsets.ISO_8859_1);
                    wire.append(secondaryHex);
                    pos += 16;
                }
                wire.append("]");
            } else {
                // Binary bitmap: read 8 bytes primary, convert to hex for display
                wire.append("[BMP:");
                boolean hasSecondary = (body[pos] & 0x80) != 0;
                for (int i = pos; i < pos + 8; i++) {
                    wire.append(String.format("%02X", body[i]));
                }
                pos += 8;

                if (hasSecondary && pos + 8 <= bodyLen) {
                    for (int i = pos; i < pos + 8; i++) {
                        wire.append(String.format("%02X", body[i]));
                    }
                    pos += 8;
                }
                wire.append("]");
            }
        }

        // Remaining data elements (ASCII)
        if (pos < bodyLen) {
            wire.append(new String(body, pos, bodyLen - pos, java.nio.charset.StandardCharsets.ISO_8859_1));
        }

        String logEntry = String.format(
                "\n<log realm=\"%s\" at=\"%s\">\n  <raw direction=\"%s\" length=\"%d\">\n" +
                "    <hex>%s</hex>\n" +
                "    <wire>%s</wire>\n" +
                "  </raw>\n</log>",
                realm, ts, direction, bodyLen, hex, escape(wire.toString()));

        AUDIT.info(logEntry);
    }

    // -------------------------------------------------------------------------

    private static String buildXml(String realm, String tag, String direction, IsoMessage msg) {
        String ts = LocalDateTime.now().format(TS_FMT);
        StringBuilder sb = new StringBuilder();
        sb.append("\n<log realm=\"").append(realm).append("\" at=\"").append(ts).append("\">\n");
        sb.append("  <").append(tag).append(">\n");
        sb.append("    <isomsg direction=\"").append(direction).append("\">\n");

        if (msg != null) {
            // DE0 = MTI
            sb.append("      <field id=\"0\" value=\"").append(escape(msg.getMti())).append("\"/>\n");

            // Semua field lain, sorted by field number
            msg.getBitmapFields().forEach(id -> {
                String value = msg.getField(id);
                if (value != null) {
                    sb.append("      <field id=\"").append(id)
                      .append("\" value=\"").append(escape(value)).append("\"/>\n");
                }
            });
        }

        sb.append("    </isomsg>\n");
        sb.append("  </").append(tag).append(">\n");
        sb.append("</log>");
        return sb.toString();
    }

    private static String buildSuspendXml(String context, String stan, String rrn, String reason) {
        String ts = LocalDateTime.now().format(TS_FMT);
        return "\n<log realm=\"" + REALM + "\" at=\"" + ts + "\">\n" +
               "  <suspend context=\"" + escape(context) + "\"" +
               " stan=\"" + escape(stan) + "\"" +
               " rrn=\"" + escape(rrn) + "\"" +
               " reason=\"" + escape(reason) + "\"" +
               " action=\"WAIT_RECONCILIATION\"/>\n" +
               "</log>";
    }

    private static String buildErrorXml(String context, String stan, String rrn, String error) {
        String ts = LocalDateTime.now().format(TS_FMT);
        return "\n<log realm=\"" + REALM + "\" at=\"" + ts + "\">\n" +
               "  <error context=\"" + escape(context) + "\"" +
               " stan=\"" + escape(stan) + "\"" +
               " rrn=\"" + escape(rrn) + "\">\n" +
               "    " + escape(error) + "\n" +
               "  </error>\n" +
               "</log>";
    }

    /** Escape karakter XML di value field (except &). */
    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;");
    }

    /** Check if a range of bytes are all valid hex ASCII characters [0-9A-Fa-f]. */
    private static boolean isHexAsciiRange(byte[] data, int offset, int length) {
        if (offset + length > data.length) return false;
        for (int i = offset; i < offset + length; i++) {
            char c = (char) (data[i] & 0xFF);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
