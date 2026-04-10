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
}
