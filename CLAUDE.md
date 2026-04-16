# CLAUDE.md – h2h-iso8583-bcad

## Project Overview
Spring Boot 3.3.5 + jPOS 2.1.9 service for H2H ISO 8583 integration between BCA Digital (acquirer) and BCA (issuer).  
Package root: `com.bcad.h2h.iso8583`  
Java: 21

---

## Tech Stack
- **Framework**: Spring Boot 3.3.5
- **ISO 8583 Library**: jPOS 2.1.9
- **Build**: Maven
- **Lombok**: yes (annotation processor required)
- **Layers**: config / controller / dto / event / exception / iso / mapper / service / simulator / transport / util

---

## BCA Digital ISO 8583 Spec – Critical Rules

### Message Types Used
| MTI  | Direction        | Purpose                        |
|------|------------------|--------------------------------|
| 0200 | BCAD → BCA       | Inquiry Transfer / Transfer Request |
| 0210 | BCA → BCAD       | Inquiry Transfer / Transfer Response |
| 0800 | BCA ↔ BCAD       | Logon / Logoff / Echo Test / Cutover Request |
| 0810 | BCA ↔ BCAD       | Logon / Logoff / Echo Test / Cutover Response |

---

## BIC ISO External Message Header (12 bytes, prepended to every message)

Every ISO message MUST be prefixed with this 12-byte BIC header:

| Field             | 0200/0210 | 0800/0810 |
|-------------------|-----------|-----------|
| BASE24 Header     | ISO       | ISO       |
| Product Indicator | 01        | 00        |
| Release Number    | 50        | 50        |
| Status            | 000       | 000       |
| Originator Code   | 1 (req) / 3 (resp) | 6 |
| Responder Code    | 0 (req) / 3 (resp) | 0 (req) / 6 (resp) |

---

## 0200 – Financial Transaction Request (Inquiry Transfer & Transfer)

### Mandatory Fields (STATUS = M)
| Bit | Name                                  | Format     | Length | Example                    |
|-----|---------------------------------------|------------|--------|----------------------------|
| 2   | Primary Account Number                | LLVAR n    | ..19   | `5029123456781234`         |
| 3   | Processing Code                       | n          | 6      | `321000` (inquiry) / `401000` (transfer) |
| 4   | Transaction Amount                    | n          | 12     | `000200000000`             |
| 7   | Transmission Date and Time            | MMDDhhmmss | 10     | `0209071903`               |
| 11  | Systems Trace Audit Number            | n          | 6      | `367656`                   |
| 12  | Local Transaction Time                | HHmmss     | 6      | `141902`                   |
| 13  | Local Transaction Date                | MMDD       | 4      | `0209`                     |
| 17  | Capture Date                          | MMDD       | 4      | `0209`                     |
| 32  | Acquiring Institution ID Code         | LLVAR n    | ..11   | `501`                      |
| 35  | Track 2 Data                          | LLVAR z    | ..37   | `5029123456781234=999`     |
| 37  | Retrieval Reference Number            | an         | 12     | `418689`                   |
| 41  | Card Acceptor Terminal Identification | ans        | 16     | `ABCD1234EFGH5678`         |
| **43** | **Card Acceptor Name/Location**    | **ans**    | **40** | **`MOBILE/INTERNET BANKING`** |
| 49  | Transaction Currency Code             | n          | 3      | `360`                      |
| 60  | Terminal Data                         | LLLVAR ans | 15     | `MOBILE/INTERNET`          |
| 126 | Token Data for Transfer               | LLLVAR ans | 999    | see Bit 126 section        |

### Conditional Fields (STATUS = C)
| Bit | Name                      | Notes                              |
|-----|---------------------------|------------------------------------|
| 48  | Additional Data           | Share group info                   |
| 102 | Account Identification 1  | From Account (debit source)        |
| 103 | Account Identification 2  | To Account (credit destination)    |

### Fields NOT present in 0200 request
- **Bit 1** (Secondary Bitmap) – only in 0210
- **Bit 15** (Settlement Date) – only in 0210 response
- **Bit 38** (Authorization ID Response) – only in 0210 response
- **Bit 39** (Response Code) – only in 0210 response
- **Bit 100** (Receiving Institution ID) – only in 0210 response

> ⚠️ Do NOT include fields 15, 38, 39, or 100 in outgoing 0200 requests.

---

## 0210 – Financial Transaction Response

### Additional fields vs 0200
| Bit | Name                              | Notes                  |
|-----|-----------------------------------|------------------------|
| 1   | Secondary Bit Map                 | Required               |
| 15  | Settlement Date (MMDD)            | Added in response      |
| 38  | Authorization Identification Response | 6 chars           |
| 39  | Response Code                     | See response codes     |
| 100 | Receiving Institution ID Code     | LLVAR n ..11           |

---

## Bit 3 – Processing Code Values
```
32xxyy = Inquiry (nama penerima transfer)
40xxyy = Transfer (debit from primary account)

xx = from account type
yy = to account type
  10 = tabungan (savings)
  20 = giro/checking
```

---

## Bit 43 – Card Acceptor Name/Location (40 chars, fixed)
```
Position 1-22  : Terminal owner name   → "MOBILE/INTERNET BANKING"
Position 23-35 : City                  → spaces if not used
Position 36-38 : State                 → spaces if not used
Position 39-40 : Country               → spaces if not used
```
Always pad to exactly 40 characters.

---

## Bit 126 – Token Data Structure (critical, most common source of errors)

Full layout (0-indexed from start of token content):

| Position | Length | Field                          | Notes                                      |
|----------|--------|--------------------------------|--------------------------------------------|
| 1–5      | 5      | Bit 126 length                 | Total length of token content              |
| 6–7      | 2      | Token Header                   | Always `& ` (ampersand + space)            |
| 8–12     | 5      | Number of tokens + 1           | Only R1 exists → always `00002`            |
| 13–17    | 5      | Token All Length                |                                            |
| 18–19    | 2      | Token Indicator                | Always `! ` (exclamation + space)          |
| 20–21    | 2      | Token ID                       | Always `R1`                                |
| 22–26    | 5      | Token Length                   |                                            |
| 27       | 1      | Filler                         | Single space `' '`                         |
| 28–30    | 3      | **Kode Bank Tujuan**           | BCA = `014`                                |
| 31–65    | 35     | Nama Pemilik Rekening Tujuan 1 | Space-padded                               |
| 66–100   | 35     | Nama Pemilik Rekening Tujuan 2 | Space-padded                               |
| 101–135  | 35     | Nama Pemilik Rekening Pengirim 1 | Space-padded                             |
| 136–170  | 35     | Nama Pemilik Rekening Pengirim 2 | Space-padded                             |
| 171–205  | 35     | Deskripsi Transaksi 1          | Space-padded                               |
| 206–240  | 35     | Deskripsi Transaksi 2          | Space-padded                               |
| 241–275  | 35     | Deskripsi Transaksi 3          | Space-padded                               |
| 276      | 1      | **Acquirer Transfer Indicator** | Must be `3` for BCAD (acquirer + issuer)  |
| 277      | 1      | **Switch Transfer Indicator**  | `0` = debit dan kredit (ACQUIRER=ISSUER)   |
| 278–290  | 3      | **Kode Bank Issuer**           | BCAD issuer code                           |
| 291–307  | 17     | Filler                         | Spaces                                     |

### Acquirer Transfer Indicator values
```
1 = Acquirer only
2 = Acquirer dan Bene
3 = Acquirer dan Issuer  ← BCAD must always use this
```

### Switch Transfer Indicator values
```
0 = debit dan kredit  ← use this when ACQUIRER=ISSUER (BCAD) → BENEFICIARY (BCA)
1 = debit only
2 = kredit only
```

### Required Bit 126 fields per transaction type
**Inquiry:**
- Kode bank tujuan (pos 28–30)
- Acquirer Transfer Indicator (pos 276) = `3`

**Transfer:**
- Kode bank tujuan (pos 28–30)
- Nama pemilik rekening tujuan 1 (pos 31–65)
- Nama pemilik rekening pengirim 1 (pos 101–135)
- Deskripsi Transaksi (pos 171–205)
- Acquirer Transfer Indicator (pos 276) = `3`
- Kode bank issuer (pos 278–290)

---

## Bit 48 – Additional Data Layout
| Field                | Length |
|----------------------|--------|
| Share Group          | 24     |
| Term Tran Allowed    | 1      |
| Term ST              | 2      |
| Term Cnty            | 3      |
| Term Cntry           | 3      |
| Term Rte Group       | 11     |

---

## Response Codes (Bit 39 in 0210)
| Code | Status  | Description                          |
|------|---------|--------------------------------------|
| 00   | Sukses  | Berhasil                             |
| 05   | Gagal   | Ditolak/decline                      |
| 12   | Gagal   | Transaksi tidak valid                |
| 13   | Gagal   | Nilai transaksi tidak valid          |
| 68   | Suspend | Transaksi suspens – NO reversal      |
| 76   | Gagal   | Rekening tidak valid                 |
| 89   | Gagal   | Database problem                     |
| 91   | Gagal   | Issuer tidak dapat dihubungi         |
| 92   | Gagal   | Problem routing                      |

> ⚠️ RC 68 (Suspend): hold funds, wait for BCA reconciliation file. No reversal scheme exists.

---

## Network Management Messages (0800/0810)

### Bit 70 – Network Management Information Code
```
001 = Logon
002 = Logoff
201 = Cutover
301 = Echo Test
```

### Cutover
- Triggered daily at **23:30 WIB** by BCA
- Direction: BCA → BCAD (0800), BCAD → BCA (0810)
- Contains the business date that just ended

---

## Transaction Flow Rules

### Normal Inquiry + Transfer (4 messages)
```
BCAD → BCA : 0200 (inquiry, Processing Code 32xxxx)
BCA  → BCAD: 0210 (inquiry response, RC=00)
BCAD → BCA : 0200 (transfer confirm, Processing Code 40xxxx)
BCA  → BCAD: 0210 (transfer response, RC=00)
```

### Declined on Inquiry
- Stop after receiving non-00 RC on inquiry 0210. Do not proceed to transfer.

### Declined on Transfer
- No reversal. Log and reconcile.

### Late Response / Timeout
- No reversal scheme on H2H transfer.
- If RC 68 received: hold funds, reconcile via BCA settlement file.

---

## Common Mistakes to Avoid

1. **Missing Field 43** – always mandatory in 0200, fixed 40 chars
2. **Including Field 15/38/39/100 in 0200 request** – these are response-only fields
3. **Acquirer Transfer Indicator = 'D' or wrong value** – must be `'3'` for BCAD
4. **Bit 126 position misalignment** – always pad each sub-field to exact length with spaces
5. **BIC Header wrong Originator/Responder code** – check per message type table above
6. **Field 4 (Amount) decimal** – last 2 digits are decimal (e.g. `000050000000` = Rp 500,000.00)
7. **Field 7 (Transmission Date Time) in GMT** – not WIB; field 12 is WIB
8. **Track 2 (Field 35)** – must match format `{PAN}=999`

---

## jPOS Implementation Notes

- Use `ISOMsg` for building messages
- Use `GenericPackager` with a custom XML packager config that matches BCA field definitions
- BIC 12-byte header must be handled separately (prepend before send, strip before parse)
- Field 126 is LLLVAR ans 999 – build as a fixed-layout string, pad all sub-fields with spaces to exact lengths
- For LLVAR fields (2, 32, 35, 102, 103): jPOS handles length prefix automatically via packager
- Always validate outgoing 0200 messages against mandatory field list before sending

---

## File Structure Convention
```
iso/         → ISOMsg builders, packager config, field constants
service/     → business logic (inquiry, transfer, NMM)
transport/   → TCP socket connection to BCA host
simulator/   → BCA simulator for local testing
mapper/      → DTO ↔ ISOMsg mapping
config/      → Spring config, connection pool
util/        → Bit 126 builder, BIC header builder, padding utils
```
