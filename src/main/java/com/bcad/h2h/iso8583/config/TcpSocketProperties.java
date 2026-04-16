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
    private String bankCode = "501";

    /**
     * Enable BIC ISO External Message Header (12 bytes) prepended before MTI.
     * When true, the header is dynamically generated per MTI type:
     *   0200 → ISO015000010, 0210 → ISO015000033,
     *   0800 → ISO005000060, 0810 → ISO005000066.
     * Set to false to disable (e.g., for local simulator).
     */
    private boolean bicHeaderEnabled = false;

    /**
     * Whether bitmap is encoded as hex ASCII string (16/32 chars) instead of binary bytes (8/16 bytes).
     * BASE24 BCAD variant uses hex ASCII bitmap.
     * true  = bitmap as hex ASCII, e.g. "8220000000000000" (16 chars = 8 bytes)
     * false = bitmap as binary bytes (default, standard ISO 8583)
     */
    private boolean hexBitmap = false;
}
