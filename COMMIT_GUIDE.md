# Commit Guide — orch-h2h-isocon

---

## Commit 1 — Tomcat Thread Pool

```
feat(performance): increase Tomcat thread pool to 400 for high-TPS H2H
```

```bash
git add src/main/resources/application.yml
```

---

## Commit 2 — RRN & STAN dari Upstream

```
feat(iso8583): accept RRN & STAN from upstream (fund-transfer), fallback to generator

- InquiryRequest & TransferRequest: tambah optional field rrn, stan
- JsonToIsoMapper: pakai req.getRrn()/req.getStan() jika ada, fallback ke RrnGenerator/StanGenerator
- Tujuan: sumber kebenaran RRN & STAN ada di fund-transfer, isocon hanya forward ke ISO 8583
```

```bash
git add src/main/java/com/bcad/h2h/iso8583/dto/request/InquiryRequest.java
git add src/main/java/com/bcad/h2h/iso8583/dto/request/TransferRequest.java
git add src/main/java/com/bcad/h2h/iso8583/mapper/JsonToIsoMapper.java
```

---

## Yang JANGAN di-add

```bash
# Log files
logs/

# Claude & plugin settings
.claude/
.claude-plugin/
```
