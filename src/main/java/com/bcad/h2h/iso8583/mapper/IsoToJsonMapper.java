package com.bcad.h2h.iso8583.mapper;

import com.bcad.h2h.iso8583.dto.response.InquiryResponse;
import com.bcad.h2h.iso8583.dto.response.TransferResponse;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.util.ResponseCodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IsoToJsonMapper {

    private final ResponseCodeMapper responseCodeMapper;

    /**
     * Maps a 0210 ISO response to InquiryResponse DTO.
     * RC 68 is treated as SUSPEND — never retry or reverse.
     */
    public InquiryResponse mapToInquiryResponse(IsoMessage msg, String transactionId) {
        String rc     = msg.getField(39);
        String status = responseCodeMapper.mapStatus(rc);

        // DE126 Token R1: beneficiary name (nama tujuan 1) at offset 25–59 (0-indexed after length prefix stripped)
        String beneficiaryName = "";
        String de126 = msg.getField(126);
        if (de126 != null && de126.length() >= 60) {
            beneficiaryName = de126.substring(25, 60).trim();
        }

        log.info("Mapping 0210 Inquiry RC={} STATUS={} STAN={} RRN={}",
                rc, status, msg.getField(11), msg.getField(37));

        return InquiryResponse.builder()
                .transactionId(transactionId)
                .status(status)
                .responseCode(rc)
                .responseMessage(responseCodeMapper.getMessage(rc))
                .beneficiaryName(beneficiaryName)
                .stan(msg.getField(11))
                .rrn(msg.getField(37))
                .transmissionDateTime(msg.getField(7))
                .build();
    }

    /**
     * Maps a 0210 ISO response to TransferResponse DTO.
     * RC 68 (SUSPEND): status unknown, resolved via reconciliation.
     */
    public TransferResponse mapToTransferResponse(IsoMessage msg, String transactionId) {
        String rc     = msg.getField(39);
        String status = responseCodeMapper.mapStatus(rc);

        log.info("Mapping 0210 Transfer RC={} STATUS={} STAN={} RRN={}",
                rc, status, msg.getField(11), msg.getField(37));

        return TransferResponse.builder()
                .transactionId(transactionId)
                .status(status)
                .responseCode(rc)
                .responseMessage(responseCodeMapper.getMessage(rc))
                .stan(msg.getField(11))
                .rrn(msg.getField(37))
                .transmissionDateTime(msg.getField(7))
                .settlementDate(msg.getField(15))
                .build();
    }
}
