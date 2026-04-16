package com.bcad.h2h.iso8583.service;

import com.bcad.h2h.iso8583.dto.request.InquiryRequest;
import com.bcad.h2h.iso8583.dto.request.TransferRequest;
import com.bcad.h2h.iso8583.dto.response.InquiryResponse;
import com.bcad.h2h.iso8583.dto.response.TransferResponse;
import com.bcad.h2h.iso8583.exception.TransportException;
import com.bcad.h2h.iso8583.iso.IsoDecoder;
import com.bcad.h2h.iso8583.iso.IsoEncoder;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.mapper.IsoToJsonMapper;
import com.bcad.h2h.iso8583.mapper.JsonToIsoMapper;
import com.bcad.h2h.iso8583.transport.TcpSocketClient;
import com.bcad.h2h.iso8583.util.AccountMaskUtil;
import com.bcad.h2h.iso8583.util.IsoAuditLogger;
import com.bcad.h2h.iso8583.util.ResponseCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final JsonToIsoMapper jsonToIsoMapper;
    private final IsoToJsonMapper isoToJsonMapper;
    private final IsoEncoder isoEncoder;
    private final IsoDecoder isoDecoder;
    private final TcpSocketClient tcpSocketClient;
    private final TcpSocketProperties props;
    private final NetworkSessionManager sessionManager;

    /**
     * Process an inquiry request.
     * Flow: Build 0200 Inquiry ISO -> Send via TCP -> Decode 0210 -> Map to InquiryResponse
     */
    public InquiryResponse inquiry(InquiryRequest request) {
        log.info("Processing inquiry: txnId={} fromAcc={} toAcc={} amount={}",
                request.getTransactionId(),
                AccountMaskUtil.mask(request.getFromAccountNo()),
                AccountMaskUtil.mask(request.getToAccountNo()),
                request.getAmount());

        sessionManager.requireReady();

        try {
            // Build ISO 0200 inquiry message
            IsoMessage requestMsg = jsonToIsoMapper.mapInquiryRequest(request);
            byte[] requestBytes = isoEncoder.encode(requestMsg);

            log.debug("Sending 0200 Inquiry: stan={} rrn={}",
                    requestMsg.getField(11), requestMsg.getField(37));
            IsoAuditLogger.logOutbound(requestMsg, props.getHost(), props.getPort());
            IsoAuditLogger.logRawIso("SEND", requestBytes, props.getHost(), props.getPort());

            // Send and receive via TCP
            byte[] responseBytes = tcpSocketClient.send(requestBytes);

            // Decode 0210 response
            IsoMessage responseMsg = isoDecoder.decode(responseBytes);
            IsoAuditLogger.logRawIso("RECV", responseBytes, props.getHost(), props.getPort());
            IsoAuditLogger.logInbound(responseMsg, props.getHost(), props.getPort());

            log.info("Received 0210 Inquiry response: stan={} rrn={} rc={}",
                    responseMsg.getField(11), responseMsg.getField(37), responseMsg.getField(39));

            // Map to DTO
            return isoToJsonMapper.mapToInquiryResponse(responseMsg, request.getTransactionId());

        } catch (TransportException e) {
            log.error("Transport error during inquiry: txnId={} error={}",
                    request.getTransactionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("Timeout during inquiry - marking SUSPEND: txnId={}", request.getTransactionId());
                IsoAuditLogger.logSuspend("INQUIRY", "UNKNOWN", "UNKNOWN", "Timeout - no response from BCA");
                return InquiryResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(ResponseCodeMapper.STATUS_SUSPEND)
                        .responseCode("68")
                        .responseMessage("Timeout - status unknown, do not reverse")
                        .build();
            }
            log.error("Unexpected error during inquiry: txnId={} error={}",
                    request.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Inquiry failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process a transfer request.
     * Flow: Build 0200 Transfer ISO -> Send via TCP -> Decode 0210 -> Map to TransferResponse
     * RC 68 -> SUSPEND (do NOT reverse), timeout -> SUSPEND
     */
    public TransferResponse transfer(TransferRequest request) {
        log.info("Processing transfer: txnId={} fromAcc={} toAcc={} amount={}",
                request.getTransactionId(),
                AccountMaskUtil.mask(request.getFromAccountNo()),
                AccountMaskUtil.mask(request.getToAccountNo()),
                request.getAmount());

        sessionManager.requireReady();

        try {
            IsoMessage requestMsg = jsonToIsoMapper.mapTransferRequest(request);
            byte[] requestBytes = isoEncoder.encode(requestMsg);

            log.debug("Sending 0200 Transfer: stan={} rrn={}",
                    requestMsg.getField(11), requestMsg.getField(37));
            IsoAuditLogger.logOutbound(requestMsg, props.getHost(), props.getPort());
            IsoAuditLogger.logRawIso("SEND", requestBytes, props.getHost(), props.getPort());

            // Send and receive via TCP
            byte[] responseBytes = tcpSocketClient.send(requestBytes);

            // Decode 0210 response
            IsoMessage responseMsg = isoDecoder.decode(responseBytes);
            IsoAuditLogger.logRawIso("RECV", responseBytes, props.getHost(), props.getPort());
            IsoAuditLogger.logInbound(responseMsg, props.getHost(), props.getPort());

            String rc = responseMsg.getField(39);
            log.info("Received 0210 Transfer response: stan={} rrn={} rc={}",
                    responseMsg.getField(11), responseMsg.getField(37), rc);

            // RC 68 = SUSPEND - do NOT reverse
            if ("68".equals(rc != null ? rc.trim() : null)) {
                log.warn("Transfer SUSPEND (RC=68): txnId={} stan={} - DO NOT REVERSE",
                        request.getTransactionId(), responseMsg.getField(11));
                IsoAuditLogger.logSuspend("TRANSFER", responseMsg.getField(11), responseMsg.getField(37), "RC=68");
            }

            return isoToJsonMapper.mapToTransferResponse(responseMsg, request.getTransactionId());

        } catch (TransportException e) {
            log.error("Transport error during transfer: txnId={} error={}",
                    request.getTransactionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            if (isTimeout(e)) {
                log.warn("Timeout during transfer - marking SUSPEND: txnId={} - DO NOT REVERSE",
                        request.getTransactionId());
                IsoAuditLogger.logSuspend("TRANSFER", "UNKNOWN", "UNKNOWN", "Timeout - no response from BCA");
                return TransferResponse.builder()
                        .transactionId(request.getTransactionId())
                        .status(ResponseCodeMapper.STATUS_SUSPEND)
                        .responseCode("68")
                        .responseMessage("Timeout - status unknown, do not reverse")
                        .build();
            }
            log.error("Unexpected error during transfer: txnId={} error={}",
                    request.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Transfer failed: " + e.getMessage(), e);
        }
    }

    private boolean isTimeout(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException) return true;
            if (cause.getMessage() != null && cause.getMessage().toLowerCase().contains("timeout")) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
