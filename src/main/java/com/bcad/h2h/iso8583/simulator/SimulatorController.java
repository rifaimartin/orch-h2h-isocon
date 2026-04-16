package com.bcad.h2h.iso8583.simulator;

import com.bcad.h2h.iso8583.event.InboundLogonEvent;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.service.NetworkManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * REST API untuk mengontrol skenario simulator BCA H2H.
 * Aktif pada profile non-production.
 *
 * Jika client terkonek ke simulator TCP (local), message dikirim via TCP.
 * Jika tidak (dev/uat), event dipublish langsung secara internal.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulator")
@RequiredArgsConstructor
@Profile({"local", "simulator", "dev", "uat"})
@Tag(name = "Simulator", description = "Kontrol skenario BCA H2H Simulator (non-production)")
public class SimulatorController {

    private final BcaH2hSimulator simulator;
    private final ApplicationEventPublisher eventPublisher;
    private final NetworkManagementService networkManagementService;

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
        if (simulator.isClientConnected()) {
            try {
                simulator.sendUnsolicitedEcho();
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "mode", "tcp",
                        "message", "0800 Echo Test sent to client via TCP - check logs for 0810 auto-reply"
                ));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
            }
        }

        // Fallback: kirim echo outbound via NetworkManagementService (H2H → BCA)
        try {
            IsoMessage response = networkManagementService.echoTest();
            String rc = response.getField(39) != null ? response.getField(39).trim() : "";
            return ResponseEntity.ok(Map.of(
                    "status", "00".equals(rc) ? "ok" : "failed",
                    "mode", "outbound",
                    "responseCode", rc,
                    "message", "Echo test sent to BCA (outbound) - RC=" + rc
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }


    @Operation(
        summary = "Kirim Logon ke client (simulasi BCA-initiated logon)",
        description = """
            Mengirim 0800 (BIT70=001) **unsolicited** ke client yang sedang konek.
            
            Ini mensimulasikan BCA yang menginisiasi Logon ke BCAD.
            Client (TcpConnectionManager) harus **otomatis auto-reply** dengan 0810 tanpa
            melalui business logic / JSON mapping.
            
            Cek log untuk melihat round-trip:
            - `[SIM] Sent unsolicited 0800 Logon to client STAN=...`
            - `ISO reader thread` auto-reply 0810
            - `[SIM] Received 0810 Logon Reply ... logon round-trip OK`
            """
    )
    @PostMapping("/logon")
    public ResponseEntity<Map<String, String>> triggerLogon() {
        if (simulator.isClientConnected()) {
            try {
                simulator.sendUnsolicitedLogon();
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "mode", "tcp",
                        "message", "0800 Logon sent to client via TCP - check logs for 0810 auto-reply"
                ));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
            }
        }

        // Fallback: publish InboundLogonEvent langsung (simulasi internal)
        log.info("[SIM] No TCP client - publishing InboundLogonEvent internally");
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        IsoMessage fakeLogon = new IsoMessage("0800");
        fakeLogon.setField(7, now);
        fakeLogon.setField(11, "SIM001");
        fakeLogon.setField(70, "001");
        eventPublisher.publishEvent(new InboundLogonEvent(this, fakeLogon));
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "mode", "internal",
                "message", "Inbound logon simulated internally - session marked as ready"
        ));
    }

    @Operation(
        summary = "Kirim Logoff ke client (simulasi BCA-initiated logoff)",
        description = """
            Mengirim 0800 (BIT70=002) **unsolicited** ke client yang sedang konek.
            
            Ini mensimulasikan BCA yang menginisiasi Logoff ke BCAD.
            Client (TcpConnectionManager) harus **otomatis auto-reply** dengan 0810 tanpa
            melalui business logic / JSON mapping.
            
            Cek log untuk melihat round-trip:
            - `[SIM] Sent unsolicited 0800 Logoff to client STAN=...`
            - `ISO reader thread` auto-reply 0810
            - `[SIM] Received 0810 Logoff Reply ... logoff round-trip OK`
            """
    )
    @PostMapping("/logoff")
    public ResponseEntity<Map<String, String>> triggerLogoff() {
        if (simulator.isClientConnected()) {
            try {
                simulator.sendUnsolicitedLogoff();
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "mode", "tcp",
                        "message", "0800 Logoff sent to client via TCP - check logs for 0810 auto-reply"
                ));
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
            }
        }

        // Fallback: kirim logoff outbound via NetworkManagementService (H2H → BCA)
        try {
            IsoMessage response = networkManagementService.logoff();
            String rc = response.getField(39) != null ? response.getField(39).trim() : "";
            return ResponseEntity.ok(Map.of(
                    "status", "00".equals(rc) ? "ok" : "failed",
                    "mode", "outbound",
                    "responseCode", rc,
                    "message", "Logoff sent to BCA (outbound) - RC=" + rc
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
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