package com.bcad.h2h.iso8583.iso.token;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BcadTokenR1Builder {

    private static final int BENEFICIARY_NAME_LEN  = 30;
    private static final int SENDER_NAME_LEN        = 30;
    private static final int DESCRIPTION_LEN        = 18;
    private static final int ACQUIRER_INDICATOR_LEN = 1;
    private static final int ISSUER_INDICATOR_LEN   = 1;

    public static final int TOTAL_TOKEN_LENGTH =
            BENEFICIARY_NAME_LEN + SENDER_NAME_LEN + DESCRIPTION_LEN
                    + ACQUIRER_INDICATOR_LEN + ISSUER_INDICATOR_LEN;

    private final String beneficiaryName;
    private final String senderName;
    private final String description;
    private final String acquirerIndicator;
    private final String issuerIndicator;

    @Builder
    public BcadTokenR1Builder(
            String beneficiaryName,
            String senderName,
            String description,
            String acquirerIndicator,
            String issuerIndicator) {
        this.beneficiaryName   = beneficiaryName   != null ? beneficiaryName   : "";
        this.senderName        = senderName        != null ? senderName        : "";
        this.description       = description       != null ? description       : "";
        this.acquirerIndicator = acquirerIndicator != null ? acquirerIndicator : "D";
        this.issuerIndicator   = issuerIndicator   != null ? issuerIndicator   : "I";
    }

    /**
     * Builds the raw Token R1 string (fixed-format, 80 chars):
     * Beneficiary Name (30) | Sender Name (30) | Description (18) | Acquirer (1) | Issuer (1)
     */
    public String build() {
        String token = padRight(beneficiaryName, BENEFICIARY_NAME_LEN)
                + padRight(senderName, SENDER_NAME_LEN)
                + padRight(description, DESCRIPTION_LEN)
                + padRight(acquirerIndicator, ACQUIRER_INDICATOR_LEN)
                + padRight(issuerIndicator, ISSUER_INDICATOR_LEN);

        log.debug("Built Token R1: length={}, value=[{}]", token.length(), token);
        return token;
    }

    /**
     * Parses a raw Token R1 string back into its component fields.
     */
    public static TokenR1 parse(String rawToken) {
        if (rawToken == null || rawToken.length() < TOTAL_TOKEN_LENGTH) {
            log.warn("Token R1 too short: length={}, expected>={}",
                    rawToken != null ? rawToken.length() : 0, TOTAL_TOKEN_LENGTH);
            return TokenR1.empty();
        }

        int pos = 0;
        String beneficiaryName = rawToken.substring(pos, pos + BENEFICIARY_NAME_LEN).stripTrailing();
        pos += BENEFICIARY_NAME_LEN;

        String senderName = rawToken.substring(pos, pos + SENDER_NAME_LEN).stripTrailing();
        pos += SENDER_NAME_LEN;

        String description = rawToken.substring(pos, pos + DESCRIPTION_LEN).stripTrailing();
        pos += DESCRIPTION_LEN;

        String acquirerIndicator = rawToken.substring(pos, pos + ACQUIRER_INDICATOR_LEN);
        pos += ACQUIRER_INDICATOR_LEN;

        String issuerIndicator = rawToken.substring(pos, pos + ISSUER_INDICATOR_LEN);

        return new TokenR1(beneficiaryName, senderName, description, acquirerIndicator, issuerIndicator);
    }

    private String padRight(String value, int length) {
        if (value == null) value = "";
        if (value.length() >= length) return value.substring(0, length);
        return String.format("%-" + length + "s", value);
    }

    public record TokenR1(
            String beneficiaryName,
            String senderName,
            String description,
            String acquirerIndicator,
            String issuerIndicator) {

        public static TokenR1 empty() {
            return new TokenR1("", "", "", "", "");
        }
    }
}
