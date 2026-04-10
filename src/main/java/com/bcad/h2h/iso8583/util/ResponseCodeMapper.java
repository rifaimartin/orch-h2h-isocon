package com.bcad.h2h.iso8583.util;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ResponseCodeMapper {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUSPEND = "SUSPEND";

    private static final Set<String> FAILED_CODES = Set.of("05", "12", "13", "76", "89", "91", "92");
    private static final String SUSPEND_CODE = "68";
    private static final String SUCCESS_CODE = "00";

    private static final Map<String, String> RC_MESSAGES = Map.ofEntries(
            Map.entry("00", "Approved / Success"),
            Map.entry("05", "Do not honor"),
            Map.entry("12", "Invalid transaction"),
            Map.entry("13", "Invalid amount"),
            Map.entry("14", "Invalid card number"),
            Map.entry("30", "Format error"),
            Map.entry("31", "Bank not supported"),
            Map.entry("33", "Expired card"),
            Map.entry("34", "Suspected fraud"),
            Map.entry("36", "Restricted card"),
            Map.entry("38", "PIN tries exceeded"),
            Map.entry("40", "Requested function not supported"),
            Map.entry("41", "Lost card"),
            Map.entry("43", "Stolen card"),
            Map.entry("51", "Insufficient funds"),
            Map.entry("54", "Expired card"),
            Map.entry("55", "Incorrect PIN"),
            Map.entry("57", "Transaction not permitted to cardholder"),
            Map.entry("58", "Transaction not permitted to terminal"),
            Map.entry("61", "Exceeds withdrawal limit"),
            Map.entry("62", "Restricted card"),
            Map.entry("65", "Exceeds withdrawal frequency limit"),
            Map.entry("68", "Response received too late / Status unknown"),
            Map.entry("75", "PIN tries exceeded"),
            Map.entry("76", "Invalid / non-existent account"),
            Map.entry("89", "Invalid authorization life cycle"),
            Map.entry("91", "Issuer or switch inoperative"),
            Map.entry("92", "Financial institution or intermediate network not found"),
            Map.entry("94", "Duplicate transaction"),
            Map.entry("96", "System malfunction")
    );

    /**
     * Map response code to status string.
     */
    public String mapStatus(String responseCode) {
        if (responseCode == null) return STATUS_FAILED;
        return switch (responseCode.trim()) {
            case "00" -> STATUS_SUCCESS;
            case "68" -> STATUS_SUSPEND;
            default -> STATUS_FAILED;
        };
    }

    /**
     * Get human-readable message for response code.
     */
    public String getMessage(String responseCode) {
        if (responseCode == null) return "Unknown response code";
        return RC_MESSAGES.getOrDefault(responseCode.trim(), "Response code: " + responseCode);
    }

    public boolean isSuccess(String responseCode) {
        return SUCCESS_CODE.equals(responseCode != null ? responseCode.trim() : null);
    }

    public boolean isSuspend(String responseCode) {
        return SUSPEND_CODE.equals(responseCode != null ? responseCode.trim() : null);
    }

    public boolean isFailed(String responseCode) {
        if (responseCode == null) return true;
        String rc = responseCode.trim();
        return !SUCCESS_CODE.equals(rc) && !SUSPEND_CODE.equals(rc);
    }
}
