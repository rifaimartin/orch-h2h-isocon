'# h2h-iso8583-bcad

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
mvn spring-boot:run

# Local development (with built-in TCP simulator on port 7000)
mvn spring-boot:run -Dspring-boot.run.profiles=local
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
mvn test

# Run satu test class
mvn test -Dtest=JsonToIsoMapperTest

# Run satu method
mvn test -Dtest=IsoDateTimeUtilTest#testTransmissionDateTime
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

## BIC ISO External Message Header (12 bytes)

Setiap pesan ISO **wajib** diawali dengan 12-byte BIC header berikut:

```
[BASE24(3)][ProductIndicator(2)][ReleaseNumber(2)][Status(3)][OriginatorCode(1)][ResponderCode(1)]
```

### Nilai per message type

| Message    | BASE24 | Product | Release | Status | Originator | Responder |
|------------|--------|---------|---------|--------|------------|-----------|
| 0200 req   | ISO    | 01      | 50      | 000    | 1          | 0         |
| 0210 resp  | ISO    | 01      | 50      | 000    | 1          | 3         |
| 0800 req   | ISO    | 00      | 50      | 000    | 6          | 0         |
| 0810 resp  | ISO    | 00      | 50      | 000    | 6          | 6         |

**Contoh 0200 request:** `ISO015000010`  
**Contoh 0210 response:** `ISO015000013`  
**Contoh 0800 request:** `ISO005000060`  
**Contoh 0810 response:** `ISO005000066`

---

## 0200 – Financial Transaction Request

### Bit Map Fields – Mandatory (M)

| Bit | Nama                              | Format     | Panjang | Contoh                      |
|-----|-----------------------------------|------------|---------|-----------------------------|
| 2   | Primary Account Number            | LLVAR n    | ..19    | `5029123456781234`          |
| 3   | Processing Code                   | n          | 6       | `321000` / `401000`         |
| 4   | Transaction Amount                | n          | 12      | `000200000000`              |
| 7   | Transmission Date and Time (GMT)  | MMDDhhmmss | 10      | `0209071903`                |
| 11  | Systems Trace Audit Number        | n          | 6       | `367656`                    |
| 12  | Local Transaction Time (WIB)      | HHmmss     | 6       | `141902`                    |
| 13  | Local Transaction Date            | MMDD       | 4       | `0209`                      |
| 17  | Capture Date                      | MMDD       | 4       | `0209`                      |
| 32  | Acquiring Institution ID Code     | LLVAR n    | ..11    | `501`                       |
| 35  | Track 2 Data                      | LLVAR z    | ..37    | `5029123456781234=999`      |
| 37  | Retrieval Reference Number        | an         | 12      | `418689`                    |
| 41  | Card Acceptor Terminal ID         | ans        | 16      | `BCAD0001        `          |
| 43  | Card Acceptor Name/Location       | ans        | 40      | `MOBILE/INTERNET BANKING   `|
| 49  | Transaction Currency Code         | n          | 3       | `360`                       |
| 60  | Terminal Data                     | LLLVAR ans | 15      | `MOBILE/INTERNET`           |
| 126 | Token Data for Transfer           | LLLVAR ans | 999     | lihat seksi Bit 126         |

### Bit Map Fields – Conditional (C)

| Bit | Nama                    | Keterangan                    |
|-----|-------------------------|-------------------------------|
| 48  | Additional Data         | Share group info              |
| 102 | Account Identification 1 | From Account (rekening debit) |
| 103 | Account Identification 2 | To Account (rekening kredit)  |

### ⚠️ Fields yang TIDAK BOLEH ada di 0200 request

| Bit | Nama                            | Ada di           |
|-----|---------------------------------|------------------|
| 15  | Settlement Date                 | 0210 resp only   |
| 38  | Authorization ID Response       | 0210 resp only   |
| 39  | Response Code                   | 0210 resp only   |
| 100 | Receiving Institution ID Code   | 0210 resp only   |

---

## 0210 – Financial Transaction Response

Fields tambahan vs 0200:

| Bit | Nama                            | Format  | Panjang | Keterangan              |
|-----|---------------------------------|---------|---------|-------------------------|
| 1   | Secondary Bit Map               | an      | 16      | Wajib ada               |
| 15  | Settlement Date                 | MMDD    | 4       | Tanggal settlement      |
| 38  | Authorization ID Response       | an      | 6       | Auth code dari BCA      |
| 39  | Response Code                   | an      | 2       | `00` = sukses           |
| 100 | Receiving Institution ID Code   | LLVAR n | ..11    | Kode bank issuer        |

---

## Bit 3 – Processing Code

```
Format: XXyyyy
XX   = transaction type
  32 = Inquiry nama penerima transfer
  40 = Transfer dari rekening primary

yy (from account type) / zz (to account type):
  10 = Tabungan (savings)
  20 = Giro / Checking
```

Contoh:
- `321000` = Inquiry, from tabungan, to tabungan
- `401000` = Transfer, from tabungan, to tabungan

---

## Bit 4 – Transaction Amount

- 12 digit numerik
- **2 digit terakhir adalah desimal**
- Contoh: `000200000000` = Rp 2.000.000,00

---

## Bit 7 – Transmission Date and Time

- Format: `MMDDhhmmss`
- Timezone: **GMT** (bukan WIB)
- Bit 12 (Local Transaction Time) menggunakan WIB

---

## Bit 43 – Card Acceptor Name/Location (40 chars fixed)

```
Posisi 1-22  : Nama pemilik terminal   → "MOBILE/INTERNET BANKING"
Posisi 23-35 : Kota                    → spasi jika tidak diisi
Posisi 36-38 : State                   → spasi jika tidak diisi
Posisi 39-40 : Negara                  → spasi jika tidak diisi
```

**Wajib selalu 40 karakter, pad dengan spasi.**

---

## Bit 48 – Additional Data

| Field              | Panjang |
|--------------------|---------|
| Share Group        | 24      |
| Term Tran Allowed  | 1       |
| Term ST            | 2       |
| Term Cnty          | 3       |
| Term Cntry         | 3       |
| Term Rte Group     | 11      |

---

## Bit 70 – Network Management Information Code

| Kode | Fungsi     |
|------|------------|
| 001  | Logon      |
| 002  | Logoff     |
| 201  | Cutover    |
| 301  | Echo Test  |

---

## Bit 126 – Token Data (field paling kritis)

Struktur posisi (1-indexed dari konten token):

| Posisi  | Panjang | Field                          | Nilai / Keterangan                        |
|---------|---------|--------------------------------|-------------------------------------------|
| 1–5     | 5       | Bit 126 length                 | Total panjang konten                      |
| 6–7     | 2       | Token Header                   | `& ` (ampersand + spasi)                  |
| 8–12    | 5       | Jumlah Token + 1               | Selalu `00002` (hanya ada R1)             |
| 13–17   | 5       | Token All Length               |                                           |
| 18–19   | 2       | Token Indicator                | `! ` (exclamation + spasi)               |
| 20–21   | 2       | Token ID                       | `R1`                                      |
| 22–26   | 5       | Token Length                   |                                           |
| 27      | 1       | Filler                         | Spasi `' '`                               |
| **28–30** | **3** | **Kode Bank Tujuan**           | BCA = `014`                               |
| 31–65   | 35      | Nama Pemilik Rekening Tujuan 1 | Pad spasi kanan                           |
| 66–100  | 35      | Nama Pemilik Rekening Tujuan 2 | Pad spasi kanan                           |
| 101–135 | 35      | Nama Pemilik Rekening Pengirim 1 | Pad spasi kanan                         |
| 136–170 | 35      | Nama Pemilik Rekening Pengirim 2 | Pad spasi kanan                         |
| 171–205 | 35      | Deskripsi Transaksi 1          | Pad spasi kanan                           |
| 206–240 | 35      | Deskripsi Transaksi 2          | Pad spasi kanan                           |
| 241–275 | 35      | Deskripsi Transaksi 3          | Pad spasi kanan                           |
| **276** | **1**   | **Acquirer Transfer Indicator** | **Selalu `3` untuk BCAD**               |
| **277** | **1**   | **Switch Transfer Indicator**  | **`0` = debit dan kredit**               |
| 278–280 | 3       | Kode Bank Issuer               | Kode BCAD                                 |
| 281–307 | 17      | Filler                         | Spasi                                     |

### Acquirer Transfer Indicator 1
```
1 = Acquirer only
2 = Acquirer dan Beneficiary
3 = Acquirer dan Issuer  ← BCAD selalu pakai ini
```

### Switch Transfer Indicator
```
0 = Debit dan Kredit  ← pakai ini (ACQUIRER=ISSUER → BENEFICIARY)
1 = Debit only
2 = Kredit only
```

### Fields wajib per tipe transaksi

**Inquiry:**
- Kode bank tujuan (pos 28–30)
- Acquirer Transfer Indicator (pos 276) = `3`

**Transfer:**
- Kode bank tujuan (pos 28–30)
- Nama pemilik rekening tujuan 1 (pos 31–65)
- Nama pemilik rekening pengirim 1 (pos 101–135)
- Deskripsi transaksi (pos 171–205)
- Acquirer Transfer Indicator (pos 276) = `3`
- Kode bank issuer (pos 278–280)

---

## Response Code – Bit 39

| Kode | Status  | Deskripsi                              |
|------|---------|----------------------------------------|
| 00   | Sukses  | Berhasil                               |
| 05   | Gagal   | Ditolak / decline                      |
| 12   | Gagal   | Transaksi tidak valid                  |
| 13   | Gagal   | Nilai transaksi tidak valid            |
| 68   | Suspend | Transaksi suspend – **NO reversal**    |
| 76   | Gagal   | Rekening tidak valid                   |
| 89   | Gagal   | Database problem                       |
| 91   | Gagal   | Issuer tidak dapat dihubungi           |
| 92   | Gagal   | Problem routing                        |

> ⚠️ RC 68: hold dana, tunggu file rekonsiliasi BCA. Tidak ada reversal.

---

## Cutover

- BCA melakukan cutover setiap hari pukul **23:30 WIB**
- Menandakan pergantian tanggal bisnis
- BCA kirim 0800 (code 201) → BCAD balas 0810

---

## Common Mistakes

| No | Mistake | Fix |
|----|---------|-----|
| 1 | BIC Header pakai `00` (format 0800) untuk 0200 | Gunakan `01` untuk Product Indicator di 0200/0210 |
| 2 | Field 43 tidak ada di 0200 | Selalu tambahkan, 40 chars padded |
| 3 | Field 15/38/39/100 ada di 0200 request | Hapus — hanya boleh ada di 0210 response |
| 4 | Acquirer Transfer Indicator bukan `3` | BCAD selalu `3` (acquirer + issuer) |
| 5 | Bit 126 posisi geser karena padding salah | Pad setiap sub-field ke panjang exact dengan spasi |
| 6 | Field 7 pakai WIB bukan GMT | Field 7 = GMT, Field 12 = WIB |
| 7 | Track 2 format salah | Format: `{PAN}=999` |
| 8 | Amount desimal salah | 2 digit terakhir = desimal |

---

## Sample Raw ISO Message

### 0200 Inquiry Transfer Request
```
ISO015000010
0200
[bitmap]
bit2=5029123456781234
bit3=321000
bit4=000200000000
bit7=0209071903
bit11=367656
bit12=141902
bit13=0209
bit17=0209
bit32=501
bit35=5029123456781234=999
bit37=418689
bit41=ABCD1234EFGH5678
bit43=MOBILE/INTERNET BANKING                 
bit48=A 40000036000000000001
bit49=360
bit60=MOBILE/INTERNET
bit102=...
bit103=1234567890
bit126=& 0000200292! R100270 014[spaces]3[spaces]
```

### 0800 Logon Request
```
ISO005000060
0800
bit7=0218070202
bit11=004099
bit70=001
```

DEPLOY UAT test
