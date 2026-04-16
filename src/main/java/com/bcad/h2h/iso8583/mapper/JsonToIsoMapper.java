package com.bcad.h2h.iso8583.mapper;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.dto.request.InquiryRequest;
import com.bcad.h2h.iso8583.dto.request.TransferRequest;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.iso.token.BcadTokenR1Builder;
import com.bcad.h2h.iso8583.util.IsoDateTimeUtil;
import com.bcad.h2h.iso8583.util.RrnGenerator;
import com.bcad.h2h.iso8583.util.StanGenerator;
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
        LocalDateTime serverTime = LocalDateTime.now();
        String fromAccount = req.getFromAccountNo();
        
        IsoMessage msg = new IsoMessage("0200");
        msg.setField(2,   buildPan(fromAccount));
        msg.setField(3,   isBluGiro(fromAccount) ? "322000" : "321000");
        msg.setField(4,   formatAmount(req.getAmount()));
        msg.setField(7,   isoDateTimeUtil.toTransmissionDateTime(serverTime));
        msg.setField(11,  req.getStan() != null ? req.getStan() : stanGenerator.next());
        msg.setField(12,  isoDateTimeUtil.toLocalTime(convertToWib(txnTime)));
        msg.setField(13,  isoDateTimeUtil.toLocalDate(convertToWib(txnTime)));
        msg.setField(15,  isoDateTimeUtil.toLocalDate(txnTime.plusDays(1)));
        msg.setField(17,  isoDateTimeUtil.toLocalDate(txnTime));
        msg.setField(35,  buildTrack2(fromAccount));
        msg.setField(37,  req.getRrn() != null ? req.getRrn() : rrnGenerator.next());
        msg.setField(41,  padRight(props.getTerminalId(), 8));
//        msg.setField(42,  padRight(props.getMerchantId(), 15));
        msg.setField(49,  req.getCurrencyCode() != null ? req.getCurrencyCode() : "360");
        msg.setField(60,  getTerminalData(fromAccount));
        msg.setField(102, req.getFromAccountNo());
        msg.setField(103, req.getToAccountNo());

        String tokenR1 = new BcadTokenR1Builder(
                null,  // beneficiaryName
                null,  // senderName
                null,  // description
                "D",
                "I").build();
        msg.setField(126, tokenR1);

        log.info("InquiryRequest fields: 35={}, 60={}", msg.getField(35), msg.getField(60));
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
        LocalDateTime serverTime = LocalDateTime.now();

        IsoMessage msg = new IsoMessage("0200");
        msg.setField(2,   buildPan(fromAccount));
        msg.setField(3,   isBluGiro(fromAccount) ? "322000" : "321000");
        msg.setField(3,   "400000");
        msg.setField(4,   formatAmount(req.getAmount()));
        msg.setField(7,   isoDateTimeUtil.toTransmissionDateTime(serverTime));
        msg.setField(11,  req.getStan() != null ? req.getStan() : stanGenerator.next());
        msg.setField(12,  isoDateTimeUtil.toLocalTime(convertToWib(txnTime)));
        msg.setField(13,  isoDateTimeUtil.toLocalDate(convertToWib(txnTime)));
        msg.setField(15,  isoDateTimeUtil.toLocalDate(txnTime.plusDays(1)));
        msg.setField(17,  isoDateTimeUtil.toLocalDate(txnTime));
        msg.setField(35,  buildTrack2(fromAccount));
        msg.setField(37,  req.getRrn() != null ? req.getRrn() : rrnGenerator.next());
        msg.setField(41,  padRight(props.getTerminalId(), 8));
//        msg.setField(42,  padRight(props.getMerchantId(), 15));
        msg.setField(49,  req.getCurrencyCode() != null ? req.getCurrencyCode() : "360");
        msg.setField(60,  getTerminalData(fromAccount));
        msg.setField(102, req.getFromAccountNo());
        msg.setField(103, req.getToAccountNo());

        // Always set Token R1 (may be empty)
        String tokenR1 = new BcadTokenR1Builder(
                req.getBeneficiaryName(),
                req.getSenderName(),
                req.getDescription(),
                "D",
                "I").build();
        msg.setField(126, tokenR1);

        log.info("TransferRequest fields: 35={}, 60={}, 126={}", msg.getField(35), msg.getField(60), msg.getField(126) != null ? "SET" : "NULL");
        log.debug("Mapped TransferRequest -> {}", msg);
        return msg;
    }

    /**
     * Creates a 0800 Network Management request.
     * @param networkCode 001=Logon, 002=Logoff, 301=Echo, 201=Cutover
     */
    public IsoMessage mapNetworkManagement(String networkCode, LocalDateTime txnTime) {
        IsoMessage msg = new IsoMessage("0800");
        msg.setField(7,  isoDateTimeUtil.toTransmissionDateTime(txnTime));
        msg.setField(11, stanGenerator.next());
        msg.setField(70, networkCode);
        log.debug("Mapped NetworkManagement code={} -> {}", networkCode, msg);
        return msg;
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
        return "0501" + paddedAccount + "=999";
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
     * Other accounts: "MOBILE"
     */
    private String getTerminalData(String fromAccount) {
        return isBluGiro(fromAccount) ? "INTERNET" : "MOBILE";
    }
}
