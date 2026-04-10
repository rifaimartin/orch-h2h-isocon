package com.bcad.h2h.iso8583.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request untuk Inquiry Transfer ke BCA H2H")
public class InquiryRequest {

    @NotBlank(message = "Transaction ID is required")
    @Schema(description = "ID unik transaksi dari client", example = "TXN-20260403-001")
    private String transactionId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Schema(description = "Jumlah transaksi dalam IDR", example = "500000.00")
    private BigDecimal amount;

    @NotBlank(message = "From account number is required")
    @Schema(description = "Nomor rekening pengirim (DE102)", example = "1234567890")
    private String fromAccountNo;

    @NotBlank(message = "To account number is required")
    @Schema(description = "Nomor rekening penerima (DE103)", example = "0987654321")
    private String toAccountNo;

    @NotNull(message = "Transaction time is required")
    @Schema(description = "Waktu transaksi (akan dikonversi ke format ISO 8583 DE7/DE12/DE13)", example = "2026-04-03T10:30:00")
    private LocalDateTime transactionTime;

    @Builder.Default
    @Schema(description = "Kode mata uang ISO 4217 (default: 360 = IDR)", example = "360")
    private String currencyCode = "360";

    @NotBlank(message = "Terminal ID is required")
    @Schema(description = "Terminal ID (DE41, 8 karakter)", example = "BCAD0001")
    private String terminalId;

    @NotBlank(message = "Merchant ID is required")
    @Schema(description = "Merchant ID (DE42, 15 karakter)", example = "BCADIGITAL001  ")
    private String merchantId;
}
