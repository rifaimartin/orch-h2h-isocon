package com.bcad.h2h.iso8583.service;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.event.CutoverEvent;
import com.bcad.h2h.iso8583.event.InboundLogonEvent;
import com.bcad.h2h.iso8583.exception.TransportException;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.transport.TcpSocketClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the ISO 8583 session lifecycle:
 * - Auto-logon after TCP connects at startup
 * - Auto-logoff before TCP disconnects at shutdown
 * - Scheduled Echo Test every 60 seconds (keepalive)
 * - Listens for CutoverEvent to log/handle business date update
 *
 * TransactionService calls requireReady() to ensure logon succeeded before
 * sending any financial message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkSessionManager {

    private final TcpSocketClient tcpSocketClient;
    private final NetworkManagementService networkManagementService;
    private final TcpSocketProperties properties;

    private final AtomicBoolean loggedIn = new AtomicBoolean(false);

    /**
     * Flag that TCP is shutting down. When true, scheduledEchoTest skips execution
     * and no longer modifies loggedIn state, preventing race condition where echo test
     * sets loggedIn=false just before destroy() tries to send logoff.
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        Thread.ofVirtual().name("session-init").start(() -> {
            try {
                waitForTcpConnect();
                performLogon();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Session init interrupted");
            } catch (Exception e) {
                log.error("Initial logon failed: {}", e.getMessage());
            }
        });
    }

    @PreDestroy
    public void destroy() {
        shuttingDown.set(true);

        // FORCE logoff selama TCP masih terkoneksi, not depended pada state loggedIn.
        if (tcpSocketClient.isConnected()) {
            try {
                log.info("Sending Logoff on shutdown (loggedIn={})...", loggedIn.get());
                networkManagementService.logoff();
                log.info("Logoff sent successfully on shutdown");
            } catch (Exception e) {
                log.warn("Logoff failed on shutdown (ignored): {}", e.getMessage());
            } finally {
                loggedIn.set(false);
            }
        } else {
            log.warn("Skipping logoff on shutdown - TCP not connected");
            loggedIn.set(false);
        }
    }

    /**
     * Periodic Echo Test (BIT70=301) every 60 seconds.
     * Only sent when logged in. Failure marks session as not ready.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledEchoTest() {
        if (shuttingDown.get()) return;  // jangan ubah loggedIn saat shutdown berlangsung
        if (!loggedIn.get()) return;
        try {
            IsoMessage response = networkManagementService.echoTest();
            String rc = trimField(response.getField(39));
            if (!"00".equals(rc)) {
                log.warn("Echo Test returned RC={} - marking session not ready", rc);
                loggedIn.set(false);
            } else {
                log.debug("Echo Test OK");
            }
        } catch (Exception e) {
            log.warn("Echo Test failed - marking session not ready: {}", e.getMessage());
            loggedIn.set(false);
        }
    }

    /**
     * Listens for inbound Logon (0800/BIT70=001) from BCA.
     * When BCA initiates logon and we reply 0810 RC=00, session is considered active.
     */
    @EventListener
    public void onInboundLogon(InboundLogonEvent event) {
        loggedIn.set(true);
        log.info("Inbound logon from BCA acknowledged - session marked as ready (STAN={})",
                event.getMessage().getField(11));
    }

    /**
     * Listens for Cutover (0800/BIT70=201) dispatched by TcpConnectionManager.
     * Business date update is handled in NetworkManagementService.
     */
    @EventListener
    public void onCutover(CutoverEvent event) {
        log.info("Cutover acknowledged - business date updated to {}",
                networkManagementService.getCurrentBusinessDate());
    }

    /**
     * Call this before any financial transaction.
     * Throws if logon has not completed or has failed.
     */
    public void requireReady() {
        if (!loggedIn.get()) {
            throw new TransportException(
                    "Service not ready - logon pending or failed. Retry in a moment.",
                    properties.getHost(), properties.getPort());
        }
    }

    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    /**
     * Re-run logon (e.g. after reconnect). Called externally if needed.
     */
    public void performLogon() {
        try {
            log.info("Sending Logon (0800/BIT70=001)...");
            IsoMessage response = networkManagementService.logon();
            String rc = trimField(response.getField(39));
            if ("00".equals(rc)) {
                loggedIn.set(true);
                log.info("Logon successful - service ready for transactions");
            } else {
                loggedIn.set(false);
                log.error("Logon rejected by BCA: RC={}", rc);
            }
        } catch (Exception e) {
            loggedIn.set(false);
            log.error("Logon error: {}", e.getMessage());
        }
    }

    /**
     * Waits up to 30s for TCP connection to be established.
     */
    private void waitForTcpConnect() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!tcpSocketClient.isConnected() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }
        if (!tcpSocketClient.isConnected()) {
            throw new RuntimeException("TCP connection timeout - logon aborted");
        }
        log.debug("TCP connected - proceeding with logon");
    }

    private String trimField(String value) {
        return value != null ? value.trim() : null;
    }
}
