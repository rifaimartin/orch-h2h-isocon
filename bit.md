Header & Control:
BIC ISO External Message Header (*)
Message Type = 0200
Primary Bitmap

Data Transaksi:
Bit 2 – Primary Account Number (PAN)
Bit 3 – Processing Code

Inquiry pakai prefix 32xxyy

Bit 4 – Transaction Amount
Bit 7 – Transmission Date & Time (MMDDhhmmss)
Bit 11 – STAN
Bit 12 – Local Transaction Time
Bit 13 – Local Transaction Date
Bit 17 – Capture Date
Bit 32 – Acquiring Institution ID
Bit 35 – Track 2 Data
Bit 37 – Retrieval Reference Number
Bit 41 – Terminal ID
Bit 43 – Card Acceptor Name/Location
Bit 49 – Currency Code (360)
Bit 60 – Terminal Data
Bit 126 – Token Data

Optional
Bit 48 – Additional Data
Bit 102 – Account Identification 1
Bit 103 – Account Identification 2


# ISO8583 BCA Digital — Bit Info
**Inquiry Transfer** dan **Transfer** (MTI 0200 / 0210).

## Header & Control
- **BASE24 Header (12 char)**  
  `ISO + 9 digit length`
- **MTI**
  - `0200` = Request
  - `0210` = Response
- **Primary Bitmap**
  Menentukan bit yang hadir di message.

---

## Bit List

### Bit 2 — Primary Account Number (PAN)
- **Tipe:** LLVAR  
- **Isi:** Rekening / kartu sumber  
- **Mandatory:** ✅

---

### Bit 3 — Processing Code
- **Tipe:** Fixed (6)  
- **Isi:**
  - `32xxyy` → Inquiry
  - `40xxyy` → Transfer  
- **Mandatory:** ✅

---

### Bit 4 — Transaction Amount
- **Tipe:** Fixed (12)  
- **Isi:** Nilai transfer (Rupiah, 2 digit desimal)  
- **Mandatory:** ✅

---

### Bit 7 — Transmission Date & Time
- **Tipe:** Fixed (10)  
- **Format:** MMDDhhmmss (GMT)  
- **Mandatory:** ✅

---

### Bit 11 — STAN
- **Tipe:** Fixed (6)  
- **Isi:** Nomor unik transaksi  
- **Catatan:** Harus sama di request & response  
- **Mandatory:** ✅

---

### Bit 12 — Local Transaction Time
- **Tipe:** Fixed (6)  
- **Format:** hhmmss (WIB)  
- **Mandatory:** ✅

---

### Bit 13 — Local Transaction Date
- **Tipe:** Fixed (4)  
- **Format:** MMDD  
- **Mandatory:** ✅

---

### Bit 17 — Capture Date
- **Tipe:** Fixed (4)  
- **Format:** MMDD (tanggal bisnis)  
- **Mandatory:** ✅

---

### Bit 32 — Acquiring Institution ID
- **Tipe:** LLVAR  
- **Isi:** Kode BCA Digital  
- **Mandatory:** ✅

---

### Bit 35 — Track 2 Data
- **Tipe:** LLVAR  
- **Isi:** PAN + filler  
- **Mandatory:** ✅

---

### Bit 37 — RRN
- **Tipe:** Fixed (12)  
- **Isi:** Referensi transaksi end-to-end  
- **Catatan:** Wajib konsisten req–resp  
- **Mandatory:** ✅

---

### Bit 41 — Terminal ID
- **Tipe:** Fixed (16)  
- **Isi:** Channel transaksi  
- **Mandatory:** ✅

---

### Bit 43 — Card Acceptor Name / Location
- **Tipe:** Fixed (40)  
- **Isi:** Nama channel  
- **Mandatory:** ✅

---

### Bit 48 — Additional Data
- **Tipe:** LLLVAR  
- **Isi:** Data tambahan terminal  
- **Mandatory:** ❌

---

### Bit 49 — Currency Code
- **Tipe:** Fixed (3)  
- **Isi:** `360` (IDR)  
- **Mandatory:** ✅

---

### Bit 60 — Terminal Data
- **Tipe:** LLLVAR  
- **Isi:** Jenis terminal (MOBILE/INTERNET)  
- **Mandatory:** ✅

---

### Bit 102 — Account Identification 1
- **Tipe:** LLVAR  
- **Isi:** Rekening sumber  
- **Mandatory:** ❌

---

### Bit 103 — Account Identification 2
- **Tipe:** LLVAR  
- **Isi:** Rekening tujuan  
- **Mandatory:** ❌

---

### Bit 126 — Token Data (R1)
- **Tipe:** LLLVAR  
- **Isi:** Detail transfer (bank, nama, deskripsi)  
- **Mandatory:** ✅

**Inquiry wajib:**
- Kode bank tujuan
- Acquirer Transfer Indicator

**Transfer wajib:**
- Kode bank tujuan
- Nama penerima
- Nama pengirim
- Deskripsi transaksi
- Acquirer Transfer Indicator
- Kode bank issuer

---

## Catatan Penting
- **STAN (11) & RRN (37) wajib ada dan konsisten**
- Jika MTI kebaca aneh → cek **BASE24 header offset**
- RC `68` (suspend) **tidak boleh reversal**

---