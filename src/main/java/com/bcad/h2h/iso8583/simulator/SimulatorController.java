package com.bcad.h2h.iso8583.simulator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API untuk mengontrol skenario simulator BCA H2H.
 * Hanya aktif pada profile "local" atau "simulator".
 */
@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
@Profile({"local", "simulator"})
@Tag(name = "Simulator", description = "Kontrol skenario BCA H2H Simulator (local dev only)")
public class SimulatorController {

    private final BcaH2hSimulator simulator;

    @Operation(
        summary = "Set skenario response",
        description = """
            Override response code untuk **semua** transaksi berikutnya.
            
            | responseCode | Efek |
            |---|---|
            | `00`    | SUCCESS |
            | `68`    | SUSPEND (status unknown, do not reverse) |
            | `05`    | FAILED - Do not honor |
            | `51`    | FAILED - Insufficient funds |
            | `12`    | FAILED - Invalid transaction |
            | `91`    | FAILED - Issuer inoperative |
            | `reset` | Kembali ke mode account-based |
            
            **Mode account-based** (setelah reset):
            | Suffix `toAccountNo` | RC |
            |---|---|
            | `...0068` | 68 - SUSPEND |
            | `...0005` | 05 - Do not honor |
            | `...0051` | 51 - Insufficient funds |
            | `...0012` | 12 - Invalid transaction |
            | `...0091` | 91 - Issuer inoperative |
            | lainnya   | 00 - SUCCESS |
            """
    )
    @PostMapping("/scenario")
    public ResponseEntity<Map<String, String>> setScenario(@RequestBody Map<String, String> body) {
        String rc = body.get("responseCode");
        if (rc == null || rc.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'responseCode' wajib diisi"));
        }
        simulator.setForcedRc(rc.trim());
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "scenario", simulator.getForcedRc()
        ));
    }

    @Operation(summary = "Cek skenario aktif")
    @GetMapping("/scenario")
    public ResponseEntity<Map<String, String>> getScenario() {
        return ResponseEntity.ok(Map.of(
                "scenario", simulator.getForcedRc(),
                "info", "account-based".equals(simulator.getForcedRc())
                        ? "RC ditentukan oleh suffix toAccountNo"
                        : "Semua transaksi akan return RC=" + simulator.getForcedRc()
        ));
    }

    @Operation(
        summary = "Kirim Echo Test ke client (simulasi BCA-initiated echo)",
        description = """
            Mengirim 0800 (BIT70=301) **unsolicited** ke client yang sedang konek.
            
            Ini mensimulasikan BCA yang menginisiasi Echo Test ke BCAD.
            Client (TcpConnectionManager) harus **otomatis auto-reply** dengan 0810 tanpa
            melalui business logic / JSON mapping.
            
            Cek log untuk melihat round-trip:
            - `[SIM] Sent unsolicited 0800 Echo Test to client STAN=...`
            - `ISO reader thread` auto-reply 0810
            - `[SIM] Received 0810 Echo Reply ... echo round-trip OK`
            """
    )
    @PostMapping("/echo")
    public ResponseEntity<Map<String, String>> triggerEcho() {
        if (!simulator.isClientConnected()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "No client connected to simulator"));
        }
        try {
            simulator.sendUnsolicitedEcho();
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "0800 Echo Test sent to client - check logs for 0810 auto-reply"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @Operation(summary = "Reset skenario ke account-based")
    @DeleteMapping("/scenario")
    public ResponseEntity<Map<String, String>> resetScenario() {
        simulator.setForcedRc("reset");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "scenario", "account-based"
        ));
    }
}