package com.bcad.h2h.iso8583.service;

import com.bcad.h2h.iso8583.event.CutoverEvent;
import com.bcad.h2h.iso8583.iso.IsoDecoder;
import com.bcad.h2h.iso8583.iso.IsoEncoder;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.mapper.JsonToIsoMapper;
import com.bcad.h2h.iso8583.transport.TcpSocketClient;
import com.bcad.h2h.iso8583.util.IsoDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkManagementService {

    // BIT 70 Network Management Codes
    public static final String CODE_LOGON = "001";
    public static final String CODE_LOGOFF = "002";
    public static final String CODE_ECHO_TEST = "301";
    public static final String CODE_CUTOVER = "201";

    private final JsonToIsoMapper jsonToIsoMapper;
    private final IsoEncoder isoEncoder;
    private final IsoDecoder isoDecoder;
    private final TcpSocketClient tcpSocketClient;
    private final IsoDateTimeUtil dateTimeUtil;

    private final AtomicReference<LocalDateTime> currentBusinessDate =
            new AtomicReference<>(LocalDateTime.now(java.time.ZoneId.of("Asia/Jakarta")));

    /**
     * Send Logon request (0800 BIT70=001). Expect 0810 response.
     */
    public IsoMessage logon() {
        log.info("Sending Logon (0800 BIT70=001)");
        return sendNetworkManagement(CODE_LOGON);
    }

    /**
     * Send Logoff request (0800 BIT70=002). Expect 0810 response.
     */
    public IsoMessage logoff() {
        log.info("Sending Logoff (0800 BIT70=002)");
        return sendNetworkManagement(CODE_LOGOFF);
    }

    /**
     * Send Echo Test request (0800 BIT70=301). Expect 0810 response.
     */
    public IsoMessage echoTest() {
        log.debug("Sending Echo Test (0800 BIT70=301)");
        return sendNetworkManagement(CODE_ECHO_TEST);
    }

    /**
     * Handle incoming Cutover request from BCA (0800 BIT70=201).
     * Updates internal business date and sends 0810 response.
     */
    public IsoMessage handleCutover(IsoMessage incomingCutover) {
        log.info("Handling Cutover (0800 BIT70=201) from BCA");

        // Update business date
        LocalDateTime newBusinessDate = dateTimeUtil.nowWib();
        LocalDateTime previousDate = currentBusinessDate.getAndSet(newBusinessDate);
        log.info("Business date updated: {} -> {}", previousDate, newBusinessDate);

        // Build 0810 response (echo back)
        IsoMessage response = new IsoMessage("0810");
        response.setField(7, incomingCutover.getField(7));
        response.setField(11, incomingCutover.getField(11));
        response.setField(12, incomingCutover.getField(12));
        response.setField(13, incomingCutover.getField(13));
        response.setField(37, incomingCutover.getField(37));
        response.setField(39, "00");
        response.setField(70, CODE_CUTOVER);

        try {
            byte[] responseBytes = isoEncoder.encode(response);
            tcpSocketClient.send(responseBytes);
            log.info("Sent 0810 Cutover response with RC=00");
        } catch (Exception e) {
            log.error("Failed to send cutover response: {}", e.getMessage(), e);
        }

        return response;
    }

    /**
     * Called when BCA sends Cutover (0800/BIT70=201).
     * TCP auto-reply (0810) is already handled by TcpConnectionManager.
     * This listener updates the internal business date.
     */
    @EventListener
    public void onCutover(CutoverEvent event) {
        LocalDateTime newDate = dateTimeUtil.nowWib();
        LocalDateTime prev = currentBusinessDate.getAndSet(newDate);
        log.info("Cutover received - business date updated: {} -> {}", prev.toLocalDate(), newDate.toLocalDate());
    }

    /**
     * Get current business date.
     */
    public LocalDateTime getCurrentBusinessDate() {
        return currentBusinessDate.get();
    }

    private IsoMessage sendNetworkManagement(String networkCode) {
        IsoMessage requestMsg = jsonToIsoMapper.mapNetworkManagement(networkCode, dateTimeUtil.nowWib());
        byte[] requestBytes = isoEncoder.encode(requestMsg);

        log.debug("Sending 0800 networkCode={} stan={}", networkCode, requestMsg.getField(11));

        byte[] responseBytes = tcpSocketClient.send(requestBytes);
        IsoMessage responseMsg = isoDecoder.decode(responseBytes);

        log.info("Received 0810 networkCode={} stan={} rc={}",
                networkCode, responseMsg.getField(11), responseMsg.getField(39));

        return responseMsg;
    }
}
