package com.bcad.h2h.iso8583.util;

import org.springframework.stereotype.Component;

@Component
public class AccountMaskUtil {

    /**
     * Mask account number: show first 4 + last 4 chars, middle masked.
     * e.g. "1234567890123456" -> "1234********3456"
     */
    public static String mask(String accountNo) {
        if (accountNo == null || accountNo.length() <= 8) {
            return accountNo;
        }
        String first4 = accountNo.substring(0, 4);
        String last4 = accountNo.substring(accountNo.length() - 4);
        String masked = "*".repeat(accountNo.length() - 8);
        return first4 + masked + last4;
    }
}
