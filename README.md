# h2h-iso8583-bcad

> **Host-to-Host (H2H) Financial Integration Service**
> BCA Digital <-> BCA via ISO 8583 BASE24 (BCAD variant) over TCP socket

---

## Overview

Service ini bertindak sebagai jembatan antara sistem internal BCA Digital (JSON REST) dan jaringan H2H BCA (ISO 8583 BASE24).

```
Client (JSON/HTTP)
     |
     v
[ REST Controller ]
     |
     v
[ JSON -> ISO 8583 Mapper ]
     |
     v
[ ISO 8583 Encoder ]
     |
     v
[ TCP Socket ] <-> BCA HOST
     |
     v
[ ISO 8583 Decoder ]
     |
     v
[ ISO -> JSON Mapper ]
     |
     v
Client (JSON Response)
```

### Supported Message Types

| MTI  | Keterangan |
|------|-----------|
| 0200 | Inquiry / Transfer Request |
| 0210 | Inquiry / Transfer Response |
| 0800 | Logon / Logoff / Echo Test / Cutover Request |
| 0810 | Logon / Logoff / Echo Test / Cutover Response |

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.3.5**
- **jPOS 2.1.9** - ISO 8583 message handling
- **Maven** - build tool
- **TCP Socket** - stateful H2H transport
- **springdoc-openapi** - Swagger UI

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+

### Build

```bash
# Build (skip tests)
rtk mvn clean install -DskipTests

# Build with tests
rtk mvn clean install
```

### Run

```bash
# Production / staging (requires real BCA H2H host in application.yml)
rtk mvn spring-boot:run

# Local development (with built-in TCP simulator on port 7000)
rtk mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Swagger UI

Setelah aplikasi running, buka:

```
http://localhost:8080/swagger-ui
```

---

## Configuration

`src/main/resources/application.yml`

```yaml
h2h:
  bca:
    host: 127.0.0.1      # BCA H2H host
    port: 7000           # BCA H2H port
    connect-timeout-ms: 5000
    read-timeout-ms: 30000
    reconnect-delay-ms: 5000
    max-retries: 3
```

---

## Local Simulator

Ketika dijalankan dengan profile `local`, aplikasi menjalankan TCP server simulator di port 7000 yang mensimulasikan respons BCA H2H.

### Skenario berdasarkan nomor rekening (DE103)

| Suffix rekening tujuan | RC   | Status  |
|------------------------|------|---------|
| `...0000` (default)    | `00` | SUCCESS |
| `...0068`              | `68` | SUSPEND |
| `...0005`              | `05` | FAILED  |
| `...0051`              | `51` | FAILED (insufficient funds) |
| `...0012`              | `12` | FAILED (invalid transaction) |
| `...0091`              | `91` | FAILED (issuer inoperative) |
| `...0092`              | `92` | FAILED (routing error) |

### Override via REST API

```bash
# Set forced response code untuk semua transaksi
POST /api/v1/simulator/scenario
{ "responseCode": "68" }

# Lihat skenario aktif
GET /api/v1/simulator/scenario

# Reset ke mode account-based
DELETE /api/v1/simulator/scenario
```

---

### Audit Log

ISO 8583 message di-log dalam format jPOS XML ke file `logs/h2h-iso8583-bcad-audit.log`.

**Fitur Logging:**
- **Masking Otomatis:** Data sensitif (PAN, Track Data, PIN, Account ID) dimasking secara otomatis di file audit.
- **Traceability:** Setiap log transaksi menyertakan `transactionId` dari request untuk memudahkan penelusuran antara log aplikasi dan log audit.
- **Async Logging:** Menggunakan `AsyncAppender` untuk meminimalkan dampak I/O terhadap performa transaksi.
- **Retention:** Log audit disimpan selama 90 hari untuk kebutuhan rekonsiliasi.

Contoh format log audit:

```xml
<log realm="h2h-iso8583-bcad/127.0.0.1:7000" at="2026-04-03T09:16:24.578">
  <send>
    <isomsg direction="outgoing">
      <field id="0" value="0200"/>
      <field id="3" value="310000"/>
      <field id="4" value="000050000000"/>
      ...
    </isomsg>
  </send>
</log>
```

Log files:

| File | Isi | Retensi |
|------|-----|---------|
| `logs/h2h-iso8583-bcad.log` | Semua log aplikasi | 30 hari |
| `logs/h2h-iso8583-bcad-error.log` | Error only | 30 hari |
| `logs/h2h-iso8583-bcad-audit.log` | ISO 8583 inbound/outbound XML | 90 hari |

---

## Project Structure

```
src/main/java/com/bcad/h2h/iso8583/
+-- controller/         # REST endpoints (JSON)
+-- dto/
|   +-- request/        # InquiryRequest, TransferRequest
|   +-- response/       # InquiryResponse, TransferResponse, ApiResponse
+-- mapper/             # JsonToIsoMapper, IsoToJsonMapper
+-- iso/
|   +-- IsoMessage.java
|   +-- IsoEncoder.java
|   +-- IsoDecoder.java
|   +-- packager/       # BcadIsoPackager, FieldDefinition
|   +-- token/          # BcadTokenR1Builder (DE126)
+-- service/            # TransactionService, NetworkManagementService
+-- transport/          # TcpSocketClient, TcpConnectionManager
+-- simulator/          # BcaH2hSimulator, SimulatorController (profile: local)
+-- config/             # AppConfig, TcpSocketProperties, OpenApiConfig
+-- exception/          # GlobalExceptionHandler, TransportException, ...
+-- util/               # IsoAuditLogger, IsoDateTimeUtil, StanGenerator, ...
```

---

## Tests

```bash
# Run semua tests
rtk mvn test

# Run satu test class
rtk mvn test -Dtest=JsonToIsoMapperTest

# Run satu method
rtk mvn test -Dtest=IsoDateTimeUtilTest#testTransmissionDateTime
```

---

## Response Code Handling

| RC | Status | Keterangan |
|----|--------|-----------|
| `00` | SUCCESS | Transaksi berhasil |
| `05`,`12`,`13`,`76`,`89`,`91`,`92` | FAILED | Transaksi ditolak |
| `68` | **SUSPEND** | Status tidak diketahui - jangan di-reverse, tunggu rekonsiliasi |

---

## Network Management (BIT 70)

| Kode | Fungsi |
|------|--------|
| `001` | Logon |
| `002` | Logoff |
| `301` | Echo Test |
| `201` | Cutover (daily at 23:30 WIB) |

mvn spring-boot:run -Dspring-boot.run.profiles=local

update - update


R1 TOKEN :
Token Data (270 chars): | Field | Length | Total | |-------|--------|-------| | Bank Code To | 3 | 3 | | Beneficiary Name 1 | 35 | 38 | | Beneficiary Name 2 | 35 | 73 | | Sender Name 1 | 35 | 108 | | Sender Name 2 | 35 | 143 | | Description 1 | 35 | 178 | | Description 2 | 35 | 213 | | Description 3 | 35 | 248 | | Acquirer Indicator | 1 | 249 | | Switch Indicator | 1 | 250 | | Issuer Bank Code | 3 | 253 | | Filler | 17 | 270 |
Total = 22 + 270 = 292 chars ✅