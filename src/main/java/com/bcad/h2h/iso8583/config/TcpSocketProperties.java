package com.bcad.h2h.iso8583.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "h2h.bca")
public class TcpSocketProperties {
    private String host = "127.0.0.1";
    private int port = 7000;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 30000;
    private int reconnectDelayMs = 5000;
    private int maxRetries = 3;
    private String terminalId = "BCAD0001";
    private String merchantId = "BCADIGITAL001 ";
    private String institutionId = "014";

    /**
     * ISO 8583 message header (BASE24 TPDU header).
     * Prepended between length prefix and MTI in the wire format.
     * Format: "ISO" + version(2) + status(2) + source(3) + dest(3) = 12 bytes.
     * Example: "ISO005000060"
     * Set to empty string to disable (e.g., for local simulator).
     */
    private String isoHeader = "";

    /**
     * Whether bitmap is encoded as hex ASCII string (16/32 chars) instead of binary bytes (8/16 bytes).
     * BASE24 BCAD variant uses hex ASCII bitmap.
     * true  = bitmap as hex ASCII, e.g. "8220000000000000" (16 chars = 8 bytes)
     * false = bitmap as binary bytes (default, standard ISO 8583)
     */
    private boolean hexBitmap = false;
}
