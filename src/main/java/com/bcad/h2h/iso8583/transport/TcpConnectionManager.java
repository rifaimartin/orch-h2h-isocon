package com.bcad.h2h.iso8583.transport;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.event.CutoverEvent;
import com.bcad.h2h.iso8583.exception.TransportException;
import com.bcad.h2h.iso8583.iso.IsoDecoder;
import com.bcad.h2h.iso8583.iso.IsoEncoder;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages TCP connection to BCA H2H host.
 *
 * Architecture:
 * - A dedicated reader thread continuously reads inbound messages from BCA.
 * - Outbound requests register a CompletableFuture keyed by STAN (DE11).
 * - The reader thread routes each inbound message:
 *   (a) Unsolicited 0800 echo -> auto-respond with 0810 immediately (no business logic).
 *   (b) Response (0210/0810) -> complete the matching CompletableFuture by STAN.
 *
 * This ensures unsolicited BCA-initiated echo tests never corrupt the request/response flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TcpConnectionManager {

    private final TcpSocketProperties properties;
    private final IsoDecoder isoDecoder;
    private final IsoEncoder isoEncoder;
    private final ApplicationEventPublisher eventPublisher;

    private volatile Socket socket;
    private volatile InputStream inputStream;
    private volatile OutputStream outputStream;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean readerRunning = new AtomicBoolean(false);

    // STAN (DE11) -> pending response future
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> pendingRequests = new ConcurrentHashMap<>();

    // Guards outputStream writes - multiple writers possible (sendAndReceive + echo auto-reply)
    private final ReentrantLock writeLock = new ReentrantLock();

    // Guards connect/disconnect lifecycle
    private final ReentrantLock connectLock = new ReentrantLock();

    public void connect() {
        connectLock.lock();
        try {
            if (connected.get() && socket != null && !socket.isClosed()) {
                log.debug("Already connected to {}:{}", properties.getHost(), properties.getPort());
                return;
            }
            doConnect();
        } finally {
            connectLock.unlock();
        }
    }

    private void doConnect() {
        int maxAttempts = properties.getMaxRetries() > 0 ? properties.getMaxRetries() : 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.info("Connecting to BCA {}:{} (attempt {})", properties.getHost(), properties.getPort(), attempt);
                Socket s = new Socket();
                s.connect(new InetSocketAddress(properties.getHost(), properties.getPort()),
                        properties.getConnectTimeoutMs());
                s.setSoTimeout(0); // reader thread uses blocking read - timeout handled via CompletableFuture.get
                s.setKeepAlive(true);
                s.setTcpNoDelay(true);

                this.socket = s;
                this.inputStream = s.getInputStream();
                this.outputStream = s.getOutputStream();
                this.connected.set(true);

                startReaderThread();
                log.info("Connected to BCA {}:{}", properties.getHost(), properties.getPort());
                return;
            } catch (IOException e) {
                log.warn("Connection attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(properties.getReconnectDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new TransportException("Connection interrupted", properties.getHost(), properties.getPort(), ie);
                    }
                }
            }
        }
        throw new TransportException("Failed to connect after " + maxAttempts + " attempts",
                properties.getHost(), properties.getPort());
    }

    /**
     * Starts a virtual-thread reader loop that continuously reads inbound messages
     * and dispatches them: echo auto-reply or pending future completion.
     */
    private void startReaderThread() {
        readerRunning.set(true);
        Thread.ofVirtual().name("iso-reader").start(() -> {
            log.info("ISO reader thread started");
            while (readerRunning.get() && connected.get()) {
                try {
                    byte[] rawMsg = readNextMessage();
                    dispatchInbound(rawMsg);
                } catch (IOException e) {
                    if (readerRunning.get()) {
                        log.error("Reader thread IO error - connection lost: {}", e.getMessage());
                        handleConnectionLost();
                    }
                    break;
                } catch (Exception e) {
                    log.error("Reader thread unexpected error: {}", e.getMessage(), e);
                }
            }
            log.info("ISO reader thread stopped");
        });
    }

    /**
     * Routes an inbound raw message:
     * - MTI 0800 (BIT70=301) -> echo auto-reply with 0810, no business processing
     * - MTI 0800 (other BIT70) -> log unsolicited network management, no processing
     * - MTI 0210/0810 -> complete pending future by STAN (DE11)
     */
    private void dispatchInbound(byte[] rawMsg) {
        IsoMessage msg;
        try {
            msg = isoDecoder.decode(rawMsg);
        } catch (Exception e) {
            log.error("Failed to decode inbound message: {} HEX[{}]", e.getMessage(),
                    IsoEncoder.bytesToHex(rawMsg));
            return;
        }

        String mti = msg.getMti();
        String stan = trimField(msg.getField(11));

        if ("0800".equals(mti)) {
            String bit70 = trimField(msg.getField(70));
            log.info("Received unsolicited 0800 from BCA: BIT70={} STAN={}", bit70, stan);
            if ("301".equals(bit70)) {
                autoReplyEcho(msg);
            } else if ("201".equals(bit70)) {
                autoReplyCutover(msg);
                eventPublisher.publishEvent(new CutoverEvent(this, msg));
            } else {
                log.warn("Unsolicited 0800 BIT70={} - no handler, ignoring", bit70);
            }
            return;
        }

        // Response to our outbound request - correlate by STAN
        if (stan != null && !stan.isBlank()) {
            CompletableFuture<byte[]> future = pendingRequests.remove(stan);
            if (future != null) {
                future.complete(rawMsg);
            } else {
                log.warn("Received MTI={} STAN={} but no pending request found", mti, stan);
            }
        } else {
            log.warn("Received MTI={} with no STAN - cannot correlate", mti);
        }
    }

    /**
     * Builds and sends a 0810 echo response mirroring STAN (DE11) and DE7 from inbound 0800.
     * Must not create any business transaction or JSON mapping.
     */
    private void autoReplyEcho(IsoMessage inbound) {
        try {
            IsoMessage response = new IsoMessage("0810");
            response.setField(7,  inbound.getField(7));   // DE7  transmission date/time (mirror)
            response.setField(11, inbound.getField(11));  // DE11 STAN (mirror)
            response.setField(39, "00");                  // DE39 RC = approved
            response.setField(70, "301");                 // DE70 echo test

            byte[] encoded = isoEncoder.encode(response);
            writeToSocket(encoded);
            log.info("Auto-replied 0810 echo to BCA STAN={}", trimField(inbound.getField(11)));
        } catch (Exception e) {
            log.error("Failed to auto-reply 0810 echo: {}", e.getMessage(), e);
        }
    }

    /**
     * Builds and sends a 0810 cutover response to BCA.
     * Called automatically when BCA sends unsolicited 0800/BIT70=201 (daily at 23:30 WIB).
     */
    private void autoReplyCutover(IsoMessage inbound) {
        try {
            IsoMessage response = new IsoMessage("0810");
            response.setField(7,  inbound.getField(7));   // DE7  transmission date/time (mirror)
            response.setField(11, inbound.getField(11));  // DE11 STAN (mirror)
            response.setField(39, "00");                  // DE39 RC = approved
            response.setField(70, "201");                 // DE70 cutover

            byte[] encoded = isoEncoder.encode(response);
            writeToSocket(encoded);
            log.info("Auto-replied 0810 cutover to BCA STAN={}", trimField(inbound.getField(11)));
        } catch (Exception e) {
            log.error("Failed to auto-reply 0810 cutover: {}", e.getMessage(), e);
        }
    }

    public void disconnect() {
        connectLock.lock();
        try {
            readerRunning.set(false);
            connected.set(false);
            failAllPending(new TransportException("Disconnected", properties.getHost(), properties.getPort()));
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                    log.info("Disconnected from BCA {}:{}", properties.getHost(), properties.getPort());
                } catch (IOException e) {
                    log.warn("Error closing socket: {}", e.getMessage());
                }
            }
            socket = null;
            inputStream = null;
            outputStream = null;
        } finally {
            connectLock.unlock();
        }
    }

    /**
     * Sends ISO request and waits for matching response keyed by STAN (DE11).
     * Does NOT hold a lock during wait - reader thread delivers the response asynchronously.
     */
    public byte[] sendAndReceive(byte[] requestData) {
        ensureConnected();

        IsoMessage req;
        try {
            req = isoDecoder.decode(requestData);
        } catch (Exception e) {
            throw new TransportException("Failed to decode outbound request: " + e.getMessage(),
                    properties.getHost(), properties.getPort(), e);
        }

        String stan = trimField(req.getField(11));
        if (stan == null || stan.isBlank()) {
            throw new TransportException("Outbound request has no STAN (DE11)",
                    properties.getHost(), properties.getPort());
        }

        CompletableFuture<byte[]> future = new CompletableFuture<>();
        pendingRequests.put(stan, future);

        try {
            log.debug("Sending {} bytes STAN={} HEX[{}]", requestData.length, stan,
                    IsoEncoder.bytesToHex(requestData));
            writeToSocket(requestData);

            long timeoutMs = properties.getReadTimeoutMs() > 0 ? properties.getReadTimeoutMs() : 30_000;
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            pendingRequests.remove(stan);
            throw new TransportException("Timeout waiting for response STAN=" + stan,
                    properties.getHost(), properties.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pendingRequests.remove(stan);
            throw new TransportException("Interrupted waiting for response STAN=" + stan,
                    properties.getHost(), properties.getPort(), e);
        } catch (Exception e) {
            pendingRequests.remove(stan);
            if (e.getCause() instanceof TransportException te) throw te;
            throw new TransportException("Send/receive error STAN=" + stan + ": " + e.getMessage(),
                    properties.getHost(), properties.getPort(), e);
        }
    }

    private void writeToSocket(byte[] data) {
        writeLock.lock();
        try {
            if (outputStream == null) {
                throw new TransportException("Not connected - outputStream is null",
                        properties.getHost(), properties.getPort());
            }
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            connected.set(false);
            throw new TransportException("Write error: " + e.getMessage(),
                    properties.getHost(), properties.getPort(), e);
        } finally {
            writeLock.unlock();
        }
    }

    private byte[] readNextMessage() throws IOException {
        // Read 2-byte length header
        byte[] lenHeader = readExact(2);
        int msgLength = ((lenHeader[0] & 0xFF) << 8) | (lenHeader[1] & 0xFF);

        if (msgLength <= 0 || msgLength > 65535) {
            throw new IOException("Invalid message length: " + msgLength);
        }

        byte[] body = readExact(msgLength);

        byte[] fullMsg = new byte[2 + msgLength];
        fullMsg[0] = lenHeader[0];
        fullMsg[1] = lenHeader[1];
        System.arraycopy(body, 0, fullMsg, 2, msgLength);

        log.debug("Read {} bytes HEX[{}]", fullMsg.length, IsoEncoder.bytesToHex(fullMsg));
        return fullMsg;
    }

    private byte[] readExact(int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = inputStream.read(buffer, totalRead, length - totalRead);
            if (read < 0) {
                connected.set(false);
                throw new IOException("Connection closed by remote host after " + totalRead + "/" + length + " bytes");
            }
            totalRead += read;
        }
        return buffer;
    }

    private void handleConnectionLost() {
        connected.set(false);
        readerRunning.set(false);
        failAllPending(new TransportException("Connection lost", properties.getHost(), properties.getPort()));
    }

    private void failAllPending(TransportException cause) {
        if (!pendingRequests.isEmpty()) {
            log.warn("Failing {} pending request(s) due to: {}", pendingRequests.size(), cause.getMessage());
            pendingRequests.forEach((stan, future) -> future.completeExceptionally(cause));
            pendingRequests.clear();
        }
    }

    private void ensureConnected() {
        if (!connected.get() || socket == null || socket.isClosed()) {
            log.warn("Not connected. Attempting reconnect...");
            connectLock.lock();
            try {
                if (!connected.get() || socket == null || socket.isClosed()) {
                    doConnect();
                }
            } finally {
                connectLock.unlock();
            }
        }
    }

    private String trimField(String value) {
        return value != null ? value.trim() : null;
    }

    public boolean isConnected() {
        return connected.get() && socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void reconnect() {
        disconnect();
        connect();
    }
}
