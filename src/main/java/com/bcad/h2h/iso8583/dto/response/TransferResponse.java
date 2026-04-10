package com.bcad.h2h.iso8583.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response hasil Transfer Dana dari BCA H2H")
public class TransferResponse {

    @Schema(description = "ID transaksi dari request", example = "TXN-20260403-002")
    private String transactionId;

    @Schema(description = "Status transaksi", example = "SUCCESS", allowableValues = {"SUCCESS", "FAILED", "SUSPEND"})
    private String status;

    @Schema(description = "Response code ISO 8583 BIT 39", example = "00")
    private String responseCode;

    @Schema(description = "Pesan deskripsi response code", example = "Approved / Success")
    private String responseMessage;

    @Schema(description = "System Trace Audit Number (DE11)", example = "000001")
    private String stan;

    @Schema(description = "Retrieval Reference Number (DE37)", example = "202604030001")
    private String rrn;

    @Schema(description = "Transmission Date & Time DE7 (MMDDhhmmss)", example = "0403103045")
    private String transmissionDateTime;

    @Schema(description = "Settlement Date DE15 (MMDD)", example = "0403")
    private String settlementDate;
}
