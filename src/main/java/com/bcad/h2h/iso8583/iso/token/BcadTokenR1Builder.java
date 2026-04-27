package com.bcad.h2h.iso8583.iso.token;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds BIT 126 Token R1 for BCAD H2H ISO 8583.
 *
 * Structure (292 chars total):
 * Header (22): & (1) SP(1) 00002(5) 00292(5) !(1) SP(1) R1(2) 00270(5) SP(1)
 * TokenData (270): BankCode(3) BeneName1(35) BeneName2(35) SenderName1(35) SenderName2(35)
 *                  Desc1(35) Desc2(35) Desc3(35) Acquirer(1) Switch(1) IssuerBank(3) Filler(17)
 */
@Slf4j
public class BcadTokenR1Builder {

    // Header constants
    private static final String TOKEN_HEADER    = "& ";       // 2 chars
    private static final String TOKEN_COUNT     = "00002";    // 5 chars
    private static final String TOKEN_ALL_LEN   = "00292";    // 5 chars
    private static final String TOKEN_INDICATOR = "! ";       // 2 chars
    private static final String TOKEN_ID        = "R1";       // 2 chars
    private static final String TOKEN_DATA_LEN  = "00270";    // 5 chars
    private static final String FILLER_HEADER   = " ";        // 1 char
    // Header total = 2+5+5+2+2+5+1 = 22 chars

    // Token data field lengths
    private static final int BANK_CODE_TO_LEN       = 3;
    private static final int BENEFICIARY_NAME1_LEN  = 35;
    private static final int BENEFICIARY_NAME2_LEN  = 35;
    private static final int SENDER_NAME1_LEN       = 35;
    private static final int SENDER_NAME2_LEN       = 35;
    private static final int DESCRIPTION1_LEN       = 35;
    private static final int DESCRIPTION2_LEN       = 35;
    private static final int DESCRIPTION3_LEN       = 35;
    private static final int ACQUIRER_INDICATOR_LEN  = 1;
    private static final int SWITCH_INDICATOR_LEN    = 1;
    private static final int ISSUER_BANK_CODE_LEN    = 3;
    private static final int FILLER_LEN              = 17;
    // Token data total = 3+35+35+35+35+35+35+35+1+1+3+17 = 270 chars

    public static final int TOTAL_TOKEN_LENGTH = 292;

    private final String beneficiaryName;
    private final String senderName;
    private final String description;
    private final String acquirerIndicator;
    private final String switchIndicator;
    private final String issuerBankCode;
    private final String bankCodeTo;

    @Builder
    public BcadTokenR1Builder(
            String beneficiaryName,
            String senderName,
            String description,
            String acquirerIndicator,
            String switchIndicator,
            String issuerBankCode) {
        this.beneficiaryName   = beneficiaryName   != null ? beneficiaryName   : "";
        this.senderName        = senderName        != null ? senderName        : "";
        this.description       = description       != null ? description       : "";
        this.acquirerIndicator = acquirerIndicator != null ? acquirerIndicator : "3";
        this.switchIndicator   = switchIndicator   != null ? switchIndicator   : " ";
        this.issuerBankCode    = issuerBankCode    != null ? issuerBankCode    : "";
        this.bankCodeTo        = "014"; // BCA bank code (tujuan)
    }

    /**
     * Builds the complete Token R1 string (292 chars):
     * Header(22) + TokenData(270)
     */
    public String build() {
        // Header (22 chars)
        String header = TOKEN_HEADER       // "& "      (2)
                + TOKEN_COUNT              // "00002"   (5)
                + TOKEN_ALL_LEN            // "00292"   (5)
                + TOKEN_INDICATOR          // "! "      (2)
                + TOKEN_ID                 // "R1"      (2)
                + TOKEN_DATA_LEN           // "00270"   (5)
                + FILLER_HEADER;           // " "       (1)

        // Token data (270 chars)
        String tokenData = padRight(bankCodeTo, BANK_CODE_TO_LEN)                  // Bank Code To (3)
                + padRight(beneficiaryName, BENEFICIARY_NAME1_LEN)                 // Beneficiary Name 1 (35)
                + padRight("", BENEFICIARY_NAME2_LEN)                              // Beneficiary Name 2 (35)
                + padRight(senderName, SENDER_NAME1_LEN)                           // Sender Name 1 (35)
                + padRight("", SENDER_NAME2_LEN)                                   // Sender Name 2 (35)
                + padRight(description, DESCRIPTION1_LEN)                          // Description 1 (35)
                + padRight("", DESCRIPTION2_LEN)                                   // Description 2 (35)
                + padRight("", DESCRIPTION3_LEN)                                   // Description 3 (35)
                + padRight(acquirerIndicator, ACQUIRER_INDICATOR_LEN)              // Acquirer Indicator (1) — must be "3" for BCAD
                + padRight(switchIndicator, SWITCH_INDICATOR_LEN)                  // Switch Indicator (1) — space per BCA spec
                + padRight(issuerBankCode, ISSUER_BANK_CODE_LEN)                   // Issuer Bank Code (3)
                + padRight("", FILLER_LEN);                                        // Filler (17)

        String token = header + tokenData;
        log.debug("Built Token R1: length={}, value=[{}]", token.length(), token);
        return token;
    }

    /**
     * Parses a raw Token R1 string (292 chars) back into its component fields.
     * Skips header (22) + bank code (3), starts parsing from pos 25.
     */
    public static TokenR1 parse(String rawToken) {
        if (rawToken == null || rawToken.length() < TOTAL_TOKEN_LENGTH) {
            log.warn("Token R1 too short: length={}, expected={}",
                    rawToken != null ? rawToken.length() : 0, TOTAL_TOKEN_LENGTH);
            return TokenR1.empty();
        }

        // Skip header (22) + bank code (3) = start at pos 25
        int pos = 22 + BANK_CODE_TO_LEN;

        String beneficiaryName = rawToken.substring(pos, pos + BENEFICIARY_NAME1_LEN).stripTrailing();
        pos += BENEFICIARY_NAME1_LEN + BENEFICIARY_NAME2_LEN;

        String senderName = rawToken.substring(pos, pos + SENDER_NAME1_LEN).stripTrailing();
        pos += SENDER_NAME1_LEN + SENDER_NAME2_LEN;

        String description = rawToken.substring(pos, pos + DESCRIPTION1_LEN).stripTrailing();
        pos += DESCRIPTION1_LEN + DESCRIPTION2_LEN + DESCRIPTION3_LEN;

        String acquirerIndicator = rawToken.substring(pos, pos + ACQUIRER_INDICATOR_LEN);

        return new TokenR1(beneficiaryName, senderName, description, acquirerIndicator, "");
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
