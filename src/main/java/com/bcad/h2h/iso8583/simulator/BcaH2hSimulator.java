package com.bcad.h2h.iso8583.simulator;

import com.bcad.h2h.iso8583.iso.IsoDecoder;
import com.bcad.h2h.iso8583.iso.IsoEncoder;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@Profile({"local", "simulator", "dev", "uat"})
@RequiredArgsConstructor
public class BcaH2hSimulator {

    private final IsoDecoder isoDecoder;
    private final IsoEncoder isoEncoder;

    @Value("${h2h.bca.port:7000}")
    private int port;

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Null = pakai account-based logic. Non-null = override semua dengan RC ini. */
    private final AtomicReference<String> forcedRc = new AtomicReference<>(null);

    /** Output stream ke client yang sedang aktif konek - untuk kirim unsolicited message */
    private volatile OutputStream activeClientOut = null;
    private final ReentrantLock clientWriteLock = new ReentrantLock();

    /** Sequence STAN untuk unsolicited message dari simulator */
    private final AtomicInteger simStan = new AtomicInteger(900000);

    /** Map suffix akun -> RC yang dikembalikan */
    private static final Map<String, String> ACCOUNT_SUFFIX_RC = Map.of(
            "0068", "68",  // SUSPEND
            "0005", "05",  // Do not honor
            "0051", "51",  // Insufficient funds
            "0012", "12",  // Invalid transaction
            "0013", "13",  // Invalid amount
            "0091", "91",  // Issuer inoperative
            "0092", "92"   // Network not found
    );

    public void setForcedRc(String rc) {
        if ("reset".equalsIgnoreCase(rc)) {
            forcedRc.set(null);
            log.info("[SIM] Scenario reset to account-based mode");
        } else {
            forcedRc.set(rc);
            log.info("[SIM] Scenario forced: all transactions will return RC={}", rc);
        }
    }

    public String getForcedRc() {
        String rc = forcedRc.get();
        return rc != null ? rc : "account-based";
    }

    /**
     * Kirim 0800 (BIT70=301) unsolicited ke client yang sedang konek.
     * Mensimulasikan BCA yang initiate Echo Test ke BCAD.
     * Client (TcpConnectionManager) harus auto-reply dengan 0810.
     */
    public void sendUnsolicitedEcho() {
        clientWriteLock.lock();
        try {
            if (activeClientOut == null) {
                throw new IllegalStateException("No client currently connected to simulator");
            }

            String now  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
            String stan = String.format("%06d", simStan.getAndIncrement());

            IsoMessage echo = new IsoMessage("0800");
            echo.setField(7,  now);
            echo.setField(11, stan);
            echo.setField(70, "301");

            byte[] encoded = isoEncoder.encode(echo);
            activeClientOut.write(encoded);
            activeClientOut.flush();

            log.info("[SIM] Sent unsolicited 0800 Echo Test to client STAN={}", stan);
        } catch (IOException e) {
            log.error("[SIM] Failed to send unsolicited echo: {}", e.getMessage());
            activeClientOut = null;
            throw new RuntimeException("Failed to send echo: " + e.getMessage(), e);
        } finally {
            clientWriteLock.unlock();
        }
    }

    /**
     * Kirim 0800 (BIT70=001) unsolicited ke client yang sedang konek.
     * Mensimulasikan BCA yang initiate Logon ke BCAD.
     * Client (TcpConnectionManager) harus auto-reply dengan 0810.
     */
    public void sendUnsolicitedLogon() {
        clientWriteLock.lock();
        try {
            if (activeClientOut == null) {
                throw new IllegalStateException("No client currently connected to simulator");
            }

            String now  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
            String stan = String.format("%06d", simStan.getAndIncrement());

            IsoMessage logon = new IsoMessage("0800");
            logon.setField(7,  now);
            logon.setField(11, stan);
            logon.setField(70, "001");

            byte[] encoded = isoEncoder.encode(logon);
            activeClientOut.write(encoded);
            activeClientOut.flush();

            log.info("[SIM] Sent unsolicited 0800 Logon to client STAN={}", stan);
        } catch (IOException e) {
            log.error("[SIM] Failed to send unsolicited logon: {}", e.getMessage());
            activeClientOut = null;
            throw new RuntimeException("Failed to send logon: " + e.getMessage(), e);
        } finally {
            clientWriteLock.unlock();
        }
    }

    /**
     * Kirim 0800 (BIT70=002) unsolicited ke client yang sedang konek.
     * Mensimulasikan BCA yang initiate Logoff ke BCAD.
     * Client (TcpConnectionManager) harus auto-reply dengan 0810.
     */
    public void sendUnsolicitedLogoff() {
        clientWriteLock.lock();
        try {
            if (activeClientOut == null) {
                throw new IllegalStateException("No client currently connected to simulator");
            }

            String now  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
            String stan = String.format("%06d", simStan.getAndIncrement());

            IsoMessage logoff = new IsoMessage("0800");
            logoff.setField(7,  now);
            logoff.setField(11, stan);
            logoff.setField(70, "002");

            byte[] encoded = isoEncoder.encode(logoff);
            activeClientOut.write(encoded);
            activeClientOut.flush();

            log.info("[SIM] Sent unsolicited 0800 Logoff to client STAN={}", stan);
        } catch (IOException e) {
            log.error("[SIM] Failed to send unsolicited logoff: {}", e.getMessage());
            activeClientOut = null;
            throw new RuntimeException("Failed to send logoff: " + e.getMessage(), e);
        } finally {
            clientWriteLock.unlock();
        }
    }

    public boolean isClientConnected() {
        return activeClientOut != null;
    }

    @PostConstruct
    public void start() {
        try {
            // Buka ServerSocket SYNCHRONOUS agar port sudah listening
            // sebelum TcpSocketClient mencoba connect
            serverSocket = new ServerSocket(port);
            running.set(true);
            log.info("=======================================================");
            log.info("  BCA H2H Simulator started on port {}", port);
            log.info("  Scenario API: POST /api/v1/simulator/scenario");
            log.info("  Special accounts: suffix 0068=SUSPEND, 0005=FAIL");
            log.info("=======================================================");
        } catch (IOException e) {
            log.error("[SIM] Failed to start on port {}: {}", port, e.getMessage());
            return;
        }

        // Accept loop di background thread
        executor.submit(() -> {
            while (running.get()) {
                try {
                    Socket client = serverSocket.accept();
                    log.info("[SIM] Client connected: {}", client.getRemoteSocketAddress());
                    executor.submit(() -> handleClient(client));
                } catch (IOException e) {
                    if (running.get()) log.error("[SIM] Accept error: {}", e.getMessage());
                }
            }
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException e) {
            log.warn("[SIM] Error closing server socket: {}", e.getMessage());
        }
        executor.shutdownNow();
        log.info("[SIM] Simulator stopped");
    }

    private void handleClient(Socket client) {
        try (client;
             InputStream in  = client.getInputStream();
             OutputStream out = client.getOutputStream()) {

            // Simpan referensi output stream untuk unsolicited echo dari simulator
            clientWriteLock.lock();
            try { activeClientOut = out; } finally { clientWriteLock.unlock(); }
            log.info("[SIM] Client registered for unsolicited messages: {}", client.getRemoteSocketAddress());

            while (!client.isClosed()) {
                byte[] lenBuf = new byte[2];
                if (in.readNBytes(lenBuf, 0, 2) < 2) break;

                int msgLen = ((lenBuf[0] & 0xFF) << 8) | (lenBuf[1] & 0xFF);
                byte[] body = new byte[msgLen];
                if (in.readNBytes(body, 0, msgLen) < msgLen) break;

                byte[] fullMsg = new byte[2 + msgLen];
                fullMsg[0] = lenBuf[0];
                fullMsg[1] = lenBuf[1];
                System.arraycopy(body, 0, fullMsg, 2, msgLen);

                try {
                    IsoMessage request = isoDecoder.decode(fullMsg);
                    logRequest(request);

                    // 0810 = reply dari client (hasil auto-reply TcpConnectionManager)
                    if ("0810".equals(request.getMti())) {
                        String bit70 = request.getField(70) != null ? request.getField(70).trim() : "";
                        if ("301".equals(bit70)) {
                            log.info("[SIM] Received 0810 Echo Reply STAN={} RC={} - echo round-trip OK",
                                    request.getField(11), request.getField(39));
                        } else if ("001".equals(bit70)) {
                            log.info("[SIM] Received 0810 Logon Reply STAN={} RC={} - logon round-trip OK",
                                    request.getField(11), request.getField(39));
                        } else if ("002".equals(bit70)) {
                            log.info("[SIM] Received 0810 Logoff Reply STAN={} RC={} - logoff round-trip OK",
                                    request.getField(11), request.getField(39));
                        } else {
                            log.info("[SIM] Received 0810 network response BIT70={} STAN={}",
                                    bit70, request.getField(11));
                        }
                        continue; // tidak perlu reply lagi
                    }

                    IsoMessage response = buildResponse(request);
                    logResponse(response);

                    clientWriteLock.lock();
                    try {
                        out.write(isoEncoder.encode(response));
                        out.flush();
                    } finally {
                        clientWriteLock.unlock();
                    }
                } catch (Exception e) {
                    log.error("[SIM] Error processing message: {}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.info("[SIM] Client disconnected: {}", e.getMessage());
        } finally {
            clientWriteLock.lock();
            try { activeClientOut = null; } finally { clientWriteLock.unlock(); }
            log.info("[SIM] Client unregistered");
        }
    }

    private IsoMessage buildResponse(IsoMessage req) {
        String mti  = req.getMti() != null ? req.getMti() : "";
        String now  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd"));
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));

        IsoMessage resp = new IsoMessage();

        switch (mti) {
            case "0200" -> {
                resp.setMti("0210");
                resp.setField(2,  req.getField(2));
                resp.setField(3,  req.getField(3));
                resp.setField(4,  req.getField(4));
                resp.setField(7,  now);
                resp.setField(11, req.getField(11));
                resp.setField(12, time);
                resp.setField(13, date);
                resp.setField(15, date);
                resp.setField(17, date);
                resp.setField(32, req.getField(32));
                resp.setField(35, req.getField(35));
                resp.setField(37, req.getField(37));
                resp.setField(41, req.getField(41));
                resp.setField(42, req.getField(42));
                resp.setField(48, req.getField(48));
                resp.setField(49, req.getField(49));
                resp.setField(60, req.getField(60));
                resp.setField(100, "501");
                resp.setField(102, req.getField(102));
                resp.setField(103, req.getField(103));
                if (req.hasField(126)) resp.setField(126, req.getField(126));

                String rc = resolveRc(req.getField(103));
                resp.setField(39, rc);
                if ("00".equals(rc)) resp.setField(38, "SIM001");
                log.info("[SIM] Scenario: DE103={} -> RC={}", req.getField(103), rc);
            }
            case "0800" -> {
                resp.setMti("0810");
                resp.setField(7,  now);
                resp.setField(11, req.getField(11));
                resp.setField(39, "00");
                resp.setField(70, req.getField(70));
            }
            default -> {
                resp.setMti("0810");
                resp.setField(11, req.getField(11));
                resp.setField(39, "12");
            }
        }

        return resp;
    }

    /**
     * Tentukan RC berdasarkan:
     * 1. forcedRc (set via REST API) - prioritas tertinggi
     * 2. Suffix akun tujuan (DE103)
     * 3. Default: 00
     */
    private String resolveRc(String toAccount) {
        // 1. Override via REST API
        String forced = forcedRc.get();
        if (forced != null) return forced;

        // 2. Account-based
        if (toAccount != null && toAccount.length() >= 4) {
            String suffix = toAccount.substring(toAccount.length() - 4);
            String rc = ACCOUNT_SUFFIX_RC.get(suffix);
            if (rc != null) return rc;
        }

        // 3. Default success
        return "00";
    }

    private void logRequest(IsoMessage msg) {
        log.info("[SIM] <- MTI={} DE3={} DE11={} DE37={} DE103={}",
                msg.getMti(), msg.getField(3), msg.getField(11),
                msg.getField(37), msg.getField(103));
    }

    private void logResponse(IsoMessage msg) {
        log.info("[SIM] -> MTI={} DE39={} DE11={} DE37={}",
                msg.getMti(), msg.getField(39), msg.getField(11), msg.getField(37));
    }
}
