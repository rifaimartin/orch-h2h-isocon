package com.bcad.h2h.iso8583.mapper;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.dto.request.InquiryRequest;
import com.bcad.h2h.iso8583.dto.request.TransferRequest;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.iso.token.BcadTokenR1Builder;
import com.bcad.h2h.iso8583.util.IsoDateTimeUtil;
import com.bcad.h2h.iso8583.util.RrnGenerator;
import com.bcad.h2h.iso8583.util.StanGenerator;
//import com.bcad.h2h.iso8583.service.NetworkManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JsonToIsoMapper {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    private final IsoDateTimeUtil isoDateTimeUtil;
    private final StanGenerator stanGenerator;
    private final RrnGenerator rrnGenerator;
    private final TcpSocketProperties props;

    /**
     * Maps InquiryRequest to ISO 8583 MTI 0200.
     * Processing Code: 322000 = bluGiro (prefix 77), 321000 = otherwise.
     * DE2 (PAN): 0 + KodeBank 501 + Rekening Pengirim 12 digit.
     */
    public IsoMessage mapInquiryRequest(InquiryRequest req) {
        LocalDateTime txnTime = req.getTransactionTime();
        LocalDateTime serverTime = LocalDateTime.now(WIB);
        ZonedDateTime gmtTime = serverTime.atZone(WIB).withZoneSameInstant(ZoneId.of("UTC"));
        String fromAccount = req.getFromAccountNo();
        String currency = req.getCurrencyCode() != null ? req.getCurrencyCode() : "360";

        IsoMessage msg = new IsoMessage("0200");
        msg.setField(2,   buildPan(fromAccount));
        msg.setField(3,   isBluGiro(fromAccount) ? "322000" : "321000");
        msg.setField(4,   formatAmount(req.getAmount()));
        msg.setField(7,   isoDateTimeUtil.toTransmissionDateTime(gmtTime.toLocalDateTime()));
        msg.setField(11,  req.getStan() != null ? req.getStan() : stanGenerator.next());
        msg.setField(12,  isoDateTimeUtil.toLocalTime(serverTime));
        msg.setField(13,  isoDateTimeUtil.toLocalDate(serverTime));
        msg.setField(17,  isoDateTimeUtil.toLocalDate(serverTime));
        msg.setField(32,  props.getBankCode());
        msg.setField(35,  buildTrack2(fromAccount));
        msg.setField(37,  req.getRrn() != null ? req.getRrn() : rrnGenerator.next());
        msg.setField(41,  padRight(props.getTerminalId(), 16));
        msg.setField(43,  buildCardAcceptorNameLocation());
        msg.setField(48,  buildAdditionalData(currency));
        msg.setField(49,  currency);
        msg.setField(60,  getTerminalData(fromAccount));
        msg.setField(102, req.getFromAccountNo());
        msg.setField(103, req.getToAccountNo());

        String tokenR1 = new BcadTokenR1Builder(
                null,  // beneficiaryName
                null,  // senderName
                null,  // description
                "3",   // acquirerIndicator
                " ",   // switchIndicator — space for inquiry per BCA spec
                null).build();  // issuerBankCode — not required for inquiry
        msg.setField(126, tokenR1);

        log.info("=== DEBUG 0200 INQUIRY FIELDS ===");
        log.info("DE2  = [{}] len={}", msg.getField(2), msg.getField(2).length());
        log.info("DE3  = [{}] len={}", msg.getField(3), msg.getField(3).length());
        log.info("DE4  = [{}] len={}", msg.getField(4), msg.getField(4).length());
        log.info("DE7  = [{}] len={}", msg.getField(7), msg.getField(7).length());
        log.info("DE11 = [{}] len={}", msg.getField(11), msg.getField(11).length());
        log.info("DE12 = [{}] len={}", msg.getField(12), msg.getField(12).length());
        log.info("DE13 = [{}] len={}", msg.getField(13), msg.getField(13).length());
        log.info("DE17 = [{}] len={}", msg.getField(17), msg.getField(17).length());
        log.info("DE32 = [{}] len={}", msg.getField(32), msg.getField(32).length());
        log.info("DE35 = [{}] len={}", msg.getField(35), msg.getField(35).length());
        log.info("DE37 = [{}] len={}", msg.getField(37), msg.getField(37).length());
        log.info("DE41 = [{}] len={}", msg.getField(41), msg.getField(41).length());
        log.info("DE43 = [{}] len={}", msg.getField(43), msg.getField(43).length());
        log.info("DE48 = [{}] len={}", msg.getField(48), msg.getField(48).length());
        log.info("DE49 = [{}] len={}", msg.getField(49), msg.getField(49).length());
        log.info("DE60 = [{}] len={}", msg.getField(60), msg.getField(60).length());
        log.info("DE102= [{}] len={}", msg.getField(102), msg.getField(102).length());
        log.info("DE103= [{}] len={}", msg.getField(103), msg.getField(103).length());
        log.info("DE126= [{}] len={}", msg.getField(126), msg.getField(126).length());
        log.info("=================================");
        log.debug("Mapped InquiryRequest -> {}", msg);
        return msg;
    }

    /**
     * Maps TransferRequest to ISO 8583 MTI 0200.
     * Processing Code 400000 = Funds Transfer.
     * DE126 (Token R1) is built as raw fixed-width ISO payload — never trimmed/JSON-encoded.
     */
    public IsoMessage mapTransferRequest(TransferRequest req) {
        String fromAccount = req.getFromAccountNo();
        LocalDateTime txnTime = req.getTransactionTime();
        LocalDateTime serverTime = LocalDateTime.now(WIB);
        ZonedDateTime gmtTime = serverTime.atZone(WIB).withZoneSameInstant(ZoneId.of("UTC"));
        String currency = req.getCurrencyCode() != null ? req.getCurrencyCode() : "360";

        IsoMessage msg = new IsoMessage("0200");
        msg.setField(2,   buildPan(fromAccount));
        msg.setField(3,   "401000");
        msg.setField(4,   formatAmount(req.getAmount()));
        msg.setField(7,   isoDateTimeUtil.toTransmissionDateTime(gmtTime.toLocalDateTime()));
        msg.setField(11,  req.getStan() != null ? req.getStan() : stanGenerator.next());
        msg.setField(12,  isoDateTimeUtil.toLocalTime(serverTime));
        msg.setField(13,  isoDateTimeUtil.toLocalDate(serverTime));
        msg.setField(17,  isoDateTimeUtil.toLocalDate(serverTime));
        msg.setField(32,  props.getBankCode());
        msg.setField(35,  buildTrack2(fromAccount));
        msg.setField(37,  req.getRrn() != null ? req.getRrn() : rrnGenerator.next());
        msg.setField(41,  padRight(props.getTerminalId(), 16));
        msg.setField(43,  buildCardAcceptorNameLocation());
        msg.setField(48,  buildAdditionalData(currency));
        msg.setField(49,  currency);
        msg.setField(60,  getTerminalData(fromAccount));
        msg.setField(102, req.getFromAccountNo());
        msg.setField(103, req.getToAccountNo());

        String tokenR1 = new BcadTokenR1Builder(
                req.getBeneficiaryName(),
                req.getSenderName(),
                req.getDescription(),
                "3",                    // acquirerIndicator
                "1",                    // switchIndicator — "1" (debit) for transfer per BCA backend validation
                props.getBankCode()).build();  // issuerBankCode — required for transfer
        msg.setField(126, tokenR1);

        log.info("TransferRequest fields: 32={}, 35={}, 60={}",
                msg.getField(32), msg.getField(35), msg.getField(60));
        log.info("TransferRequest DE48=[{}] len={}", msg.getField(48), msg.getField(48) != null ? msg.getField(48).length() : 0);
        log.info("TransferRequest DE126=[{}] len={}", msg.getField(126), msg.getField(126) != null ? msg.getField(126).length() : 0);
        log.debug("Mapped TransferRequest -> {}", msg);
        return msg;
    }

    /**
     * Creates a 0800 Network Management request.
     * @param networkCode 001=Logon, 002=Logoff, 301=Echo, 201=Cutover
     */
    public IsoMessage mapNetworkManagement(String networkCode, LocalDateTime txnTime) {
        LocalDateTime gmtTime = txnTime.atZone(WIB).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        IsoMessage msg = new IsoMessage("0800");
        msg.setField(7,   isoDateTimeUtil.toTransmissionDateTime(gmtTime));
        msg.setField(11,  stanGenerator.next());
        msg.setField(70,  networkCode);
        
//        // DE123 (Station ID/Identity) - set to terminal ID as fallback
//        if (NetworkManagementService.CODE_LOGON.equals(networkCode)) {
//            msg.setField(123, props.getTerminalId());
//        }
        
        log.debug("Mapped NetworkManagement code={} -> {}", networkCode, msg);
        return msg;
    }

    /**
     * Builds DE43 Card Acceptor Name/Location (fixed 40 chars).
     * Pos 1-22: "MOBILE/INTERNET BANKING", Pos 23-40: spaces.
     */
    private String buildCardAcceptorNameLocation() {
        return padRight("MOBILE/INTERNET BANKING", 40);
    }

    /** Converts BigDecimal amount to 12-digit zero-padded string (multiply by 100, no decimal). */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "000000000000";
        long cents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        return String.format("%012d", cents);
    }

    /**
     * Left-pads value with zeros to the given length.
     */
    private String padLeft(String value, int length) {
        if (value == null) value = "";
        value = value.trim();
        if (value.length() >= length) return value;
        return "0".repeat(length - value.length()) + value;
    }

    private String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return String.format("%-" + length + "s", value);
    }

    private String buildPan(String fromAccount) {
        String paddedAccount = padLeft(fromAccount, 12);
        return "0501" + paddedAccount; // 0 + KodeBank 501 + 12 digit rekening
    }

    private boolean isBluGiro(String accountNo) {
        if (accountNo == null || accountNo.length() < 2) {
            return false;
        }
        return accountNo.startsWith("77");
    }

    private String buildTrack2(String fromAccount) {
        String paddedAccount = padLeft(fromAccount, 12);
        return "0501" + paddedAccount + "=9912";
    }

    private LocalDateTime convertToWib(LocalDateTime dateTime) {
        if (dateTime == null) return LocalDateTime.now();
        // Assume input is UTC, convert to WIB
        ZonedDateTime utcTime = dateTime.atZone(ZoneId.of("UTC"));
        ZonedDateTime wibTime = utcTime.withZoneSameInstant(WIB);
        return wibTime.toLocalDateTime();
    }


    /**
     * Gets Terminal Data (DE60) for Inquiry/Transfer.
     * bluGiro accounts (prefix 77): "INTERNET"
     * Other accounts: "MOBILE/INTERNET"
     */
    private String getTerminalData(String fromAccount) {
        return isBluGiro(fromAccount) ? "INTERNET" : "MOBILE/INTERNET";
    }

    /**
     * Builds DE48 Additional Data Private.
     * Format: "A " + fee indicator + currency code + fee amount (12 digits).
     */
    private String buildAdditionalData(String currencyCode) {
        // Exact 44-char value per BCA spec
        return "A                       40000036000000000001";
    }
}
