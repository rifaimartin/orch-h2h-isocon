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

    /** Escape karakter XML di value field. */
    private static String escape(String value) {
        if (value == null) return "";
        return value
                .replace("&",  "&amp;")
                .replace("<",  "&lt;")
                .replace(">",  "&gt;")
                .replace("\"", "&quot;");
    }
}
