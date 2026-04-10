package com.bcad.h2h.iso8583.transport;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.iso.IsoEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcpSocketClient {

    private final TcpConnectionManager connectionManager;
    private final TcpSocketProperties properties;

    @PostConstruct
    public void init() {
        log.info("Initializing TCP socket client to {}:{}", properties.getHost(), properties.getPort());
        // Connect di background thread agar tidak blocking startup
        // (simulator juga start di @PostConstruct — perlu beri waktu bind dulu)
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(500); // beri waktu simulator bind port
                connectionManager.connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Initial connection failed: {}. Will retry on first request.", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down TCP socket client");
        connectionManager.disconnect();
    }

    /**
     * Send raw ISO bytes and receive response.
     * The byte array should already include the 2-byte length header.
     *
     * @param requestBytes full ISO message bytes (with length header)
     * @return full ISO response bytes (with length header)
     */
    public byte[] send(byte[] requestBytes) {
        log.debug("Sending ISO message {} bytes HEX[{}]",
                requestBytes.length, IsoEncoder.bytesToHex(requestBytes));

        byte[] responseBytes = connectionManager.sendAndReceive(requestBytes);

        log.debug("Received ISO response {} bytes HEX[{}]",
                responseBytes.length, IsoEncoder.bytesToHex(responseBytes));

        return responseBytes;
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public void reconnect() {
        log.info("Reconnecting TCP socket...");
        connectionManager.reconnect();
    }
}
