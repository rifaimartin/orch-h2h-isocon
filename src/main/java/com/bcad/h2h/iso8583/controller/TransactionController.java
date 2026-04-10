package com.bcad.h2h.iso8583.controller;

import com.bcad.h2h.iso8583.dto.request.InquiryRequest;
import com.bcad.h2h.iso8583.dto.request.TransferRequest;
import com.bcad.h2h.iso8583.dto.response.ApiResponse;
import com.bcad.h2h.iso8583.dto.response.InquiryResponse;
import com.bcad.h2h.iso8583.dto.response.TransferResponse;
import com.bcad.h2h.iso8583.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/bca/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "API untuk Inquiry dan Transfer via H2H ISO 8583 ke BCA")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
        summary = "Inquiry Transfer",
        description = """
            Kirim pesan 0200 Inquiry ke BCA via ISO 8583.
            Digunakan untuk validasi rekening tujuan sebelum transfer.
            
            - RC 00 → SUCCESS
            - RC 68 → SUSPEND (status tidak diketahui, jangan di-reverse)
            - RC lainnya → FAILED
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inquiry berhasil diproses",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request tidak valid"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error / transport error")
    })
    @PostMapping("/inquiry")
    public ResponseEntity<ApiResponse<InquiryResponse>> inquiry(
            @Valid @RequestBody InquiryRequest request) {
        log.info("Received inquiry request: txnId={}", request.getTransactionId());

        InquiryResponse response = transactionService.inquiry(request);

        ApiResponse<InquiryResponse> apiResponse;
        if ("SUCCESS".equals(response.getStatus())) {
            apiResponse = ApiResponse.success(response, "Inquiry successful");
        } else if ("SUSPEND".equals(response.getStatus())) {
            apiResponse = ApiResponse.failure("Inquiry status unknown (SUSPEND)", response);
        } else {
            apiResponse = ApiResponse.failure("Inquiry failed: " + response.getResponseMessage(), response);
        }

        return ResponseEntity.ok(apiResponse);
    }

    @Operation(
        summary = "Transfer Dana",
        description = """
            Kirim pesan 0200 Transfer ke BCA via ISO 8583.
            Pastikan inquiry berhasil (RC 00) sebelum memanggil endpoint ini.
            
            ⚠️ RC 68 (SUSPEND): status tidak diketahui — JANGAN di-reverse, tunggu rekonsiliasi.
            ⚠️ Jangan auto-retry jika timeout.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transfer berhasil diproses",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request tidak valid"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error / transport error")
    })
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {
        log.info("Received transfer request: txnId={}", request.getTransactionId());

        TransferResponse response = transactionService.transfer(request);

        ApiResponse<TransferResponse> apiResponse;
        if ("SUCCESS".equals(response.getStatus())) {
            apiResponse = ApiResponse.success(response, "Transfer successful");
        } else if ("SUSPEND".equals(response.getStatus())) {
            apiResponse = ApiResponse.failure("Transfer status unknown (SUSPEND) - do not retry", response);
        } else {
            apiResponse = ApiResponse.failure("Transfer failed: " + response.getResponseMessage(), response);
        }

        return ResponseEntity.ok(apiResponse);
    }
}
