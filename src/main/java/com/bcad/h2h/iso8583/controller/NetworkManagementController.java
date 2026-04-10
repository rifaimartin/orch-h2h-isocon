package com.bcad.h2h.iso8583.controller;

import com.bcad.h2h.iso8583.dto.response.ApiResponse;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.service.NetworkManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/network")
@RequiredArgsConstructor
@Tag(name = "Network Management", description = "API untuk manajemen koneksi H2H ke BCA (Logon/Logoff/Echo/Cutover) — ISO 8583 MTI 0800/0810")
public class NetworkManagementController {

    private final NetworkManagementService networkManagementService;

    @Operation(summary = "Logon ke BCA", description = "Kirim 0800 BIT70=001. Harus dilakukan sebelum transaksi finansial.")
    @PostMapping("/logon")
    public ResponseEntity<ApiResponse<Map<String, String>>> logon() {
        log.info("Received logon request");
        IsoMessage response = networkManagementService.logon();
        return ResponseEntity.ok(ApiResponse.success(
                buildNetworkResponse(response),
                "Logon " + ("00".equals(trimField(response.getField(39))) ? "successful" : "failed")));
    }

    @Operation(summary = "Logoff dari BCA", description = "Kirim 0800 BIT70=002. Menutup sesi H2H.")
    @PostMapping("/logoff")
    public ResponseEntity<ApiResponse<Map<String, String>>> logoff() {
        log.info("Received logoff request");
        IsoMessage response = networkManagementService.logoff();
        return ResponseEntity.ok(ApiResponse.success(
                buildNetworkResponse(response),
                "Logoff " + ("00".equals(trimField(response.getField(39))) ? "successful" : "failed")));
    }

    @Operation(summary = "Echo Test ke BCA", description = "Kirim 0800 BIT70=301. Cek koneksi dan liveness ke BCA.")
    @PostMapping("/echo")
    public ResponseEntity<ApiResponse<Map<String, String>>> echoTest() {
        log.info("Received echo test request");
        IsoMessage response = networkManagementService.echoTest();
        return ResponseEntity.ok(ApiResponse.success(
                buildNetworkResponse(response),
                "Echo test " + ("00".equals(trimField(response.getField(39))) ? "successful" : "failed")));
    }

    private Map<String, String> buildNetworkResponse(IsoMessage response) {
        return Map.of(
                "mti", response.getMti() != null ? response.getMti() : "",
                "stan", trimField(response.getField(11)),
                "rrn", trimField(response.getField(37)),
                "responseCode", trimField(response.getField(39)),
                "networkCode", trimField(response.getField(70))
        );
    }

    private String trimField(String value) {
        return value != null ? value.trim() : "";
    }
}
