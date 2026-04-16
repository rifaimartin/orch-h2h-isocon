package com.bcad.h2h.iso8583.iso.packager;

import java.util.HashMap;
import java.util.Map;

import static com.bcad.h2h.iso8583.iso.packager.FieldDefinition.FieldType.*;

public class BcadIsoPackager {

    private static final Map<Integer, FieldDefinition> FIELD_DEFINITIONS = new HashMap<>();

    static {
        FIELD_DEFINITIONS.put(2,   new FieldDefinition(2,   LLVAR,   19,  "Primary Account Number"));
        FIELD_DEFINITIONS.put(3,   new FieldDefinition(3,   NUMERIC, 6,   "Processing Code"));
        FIELD_DEFINITIONS.put(4,   new FieldDefinition(4,   NUMERIC, 12,  "Amount Transaction"));
        FIELD_DEFINITIONS.put(5,   new FieldDefinition(5,   NUMERIC, 12,  "Amount Settlement"));
        FIELD_DEFINITIONS.put(6,   new FieldDefinition(6,   NUMERIC, 12,  "Amount Cardholder Billing"));
        FIELD_DEFINITIONS.put(7,   new FieldDefinition(7,   NUMERIC, 10,  "Transmission Date/Time"));
        FIELD_DEFINITIONS.put(8,   new FieldDefinition(8,   NUMERIC, 8,   "Amount Cardholder Billing Fee"));
        FIELD_DEFINITIONS.put(9,   new FieldDefinition(9,   NUMERIC, 8,   "Conversion Rate Settlement"));
        FIELD_DEFINITIONS.put(10,  new FieldDefinition(10,  NUMERIC, 8,   "Conversion Rate Cardholder Billing"));
        FIELD_DEFINITIONS.put(11,  new FieldDefinition(11,  NUMERIC, 6,   "STAN"));
        FIELD_DEFINITIONS.put(12,  new FieldDefinition(12,  NUMERIC, 6,   "Local Transaction Time"));
        FIELD_DEFINITIONS.put(13,  new FieldDefinition(13,  NUMERIC, 4,   "Local Transaction Date"));
        FIELD_DEFINITIONS.put(14,  new FieldDefinition(14,  NUMERIC, 4,   "Expiration Date"));
        FIELD_DEFINITIONS.put(15,  new FieldDefinition(15,  NUMERIC, 4,   "Settlement Date"));
        FIELD_DEFINITIONS.put(16,  new FieldDefinition(16,  NUMERIC, 4,   "Currency Conversion Date"));
        FIELD_DEFINITIONS.put(17,  new FieldDefinition(17,  NUMERIC, 4,   "Capture Date"));
        FIELD_DEFINITIONS.put(18,  new FieldDefinition(18,  NUMERIC, 4,   "Merchant Type"));
        FIELD_DEFINITIONS.put(19,  new FieldDefinition(19,  NUMERIC, 3,   "Acquiring Institution Country Code"));
        FIELD_DEFINITIONS.put(20,  new FieldDefinition(20,  NUMERIC, 3,   "PAN Extended Country Code"));
        FIELD_DEFINITIONS.put(21,  new FieldDefinition(21,  NUMERIC, 3,   "Forwarding Institution Country Code"));
        FIELD_DEFINITIONS.put(22,  new FieldDefinition(22,  NUMERIC, 3,   "POS Entry Mode"));
        FIELD_DEFINITIONS.put(23,  new FieldDefinition(23,  NUMERIC, 3,   "Card Sequence Number"));
        FIELD_DEFINITIONS.put(24,  new FieldDefinition(24,  NUMERIC, 3,   "Function Code"));
        FIELD_DEFINITIONS.put(25,  new FieldDefinition(25,  NUMERIC, 2,   "POS Condition Code"));
        FIELD_DEFINITIONS.put(26,  new FieldDefinition(26,  NUMERIC, 2,   "POS PIN Capture Code"));
        FIELD_DEFINITIONS.put(27,  new FieldDefinition(27,  NUMERIC, 1,   "Authorization ID Response Length"));
        FIELD_DEFINITIONS.put(28,  new FieldDefinition(28,  ALPHANUM,9,   "Amount Transaction Fee"));
        FIELD_DEFINITIONS.put(29,  new FieldDefinition(29,  ALPHANUM,9,   "Amount Settlement Fee"));
        FIELD_DEFINITIONS.put(30,  new FieldDefinition(30,  ALPHANUM,9,   "Amount Transaction Processing Fee"));
        FIELD_DEFINITIONS.put(31,  new FieldDefinition(31,  ALPHANUM,9,   "Amount Settlement Processing Fee"));
        FIELD_DEFINITIONS.put(32,  new FieldDefinition(32,  LLVAR,   11,  "Acquiring Institution ID"));
        FIELD_DEFINITIONS.put(33,  new FieldDefinition(33,  LLVAR,   11,  "Forwarding Institution ID"));
        FIELD_DEFINITIONS.put(34,  new FieldDefinition(34,  LLVAR,   28,  "PAN Extended"));
        FIELD_DEFINITIONS.put(35,  new FieldDefinition(35,  LLVAR,   37,  "Track 2 Data"));
        FIELD_DEFINITIONS.put(36,  new FieldDefinition(36,  LLLVAR,  104, "Track 3 Data"));
        FIELD_DEFINITIONS.put(37,  new FieldDefinition(37,  ALPHANUM,12,  "RRN"));
        FIELD_DEFINITIONS.put(38,  new FieldDefinition(38,  ALPHANUM,6,   "Authorization ID"));
        FIELD_DEFINITIONS.put(39,  new FieldDefinition(39,  ALPHANUM,2,   "Response Code"));
        FIELD_DEFINITIONS.put(40,  new FieldDefinition(40,  ALPHANUM,3,   "Service Restriction Code"));
        FIELD_DEFINITIONS.put(41,  new FieldDefinition(41,  ALPHANUM,16,  "Terminal ID"));
        FIELD_DEFINITIONS.put(42,  new FieldDefinition(42,  ALPHANUM,15,  "Merchant ID"));
        FIELD_DEFINITIONS.put(43,  new FieldDefinition(43,  ALPHA,   40,  "Card Acceptor Name/Location"));
        FIELD_DEFINITIONS.put(44,  new FieldDefinition(44,  LLVAR,   25,  "Additional Response Data"));
        FIELD_DEFINITIONS.put(45,  new FieldDefinition(45,  LLVAR,   76,  "Track 1 Data"));
        FIELD_DEFINITIONS.put(46,  new FieldDefinition(46,  LLLVAR,  999, "Additional Data ISO"));
        FIELD_DEFINITIONS.put(47,  new FieldDefinition(47,  LLLVAR,  999, "Additional Data National"));
        FIELD_DEFINITIONS.put(48,  new FieldDefinition(48,  LLLVAR,  999, "Additional Data Private"));
        FIELD_DEFINITIONS.put(49,  new FieldDefinition(49,  NUMERIC, 3,   "Currency Code Transaction"));
        FIELD_DEFINITIONS.put(50,  new FieldDefinition(50,  NUMERIC, 3,   "Currency Code Settlement"));
        FIELD_DEFINITIONS.put(51,  new FieldDefinition(51,  NUMERIC, 3,   "Currency Code Cardholder Billing"));
        FIELD_DEFINITIONS.put(52,  new FieldDefinition(52,  BINARY,  8,   "PIN Data"));
        FIELD_DEFINITIONS.put(53,  new FieldDefinition(53,  NUMERIC, 16,  "Security Related Control Info"));
        FIELD_DEFINITIONS.put(54,  new FieldDefinition(54,  LLLVAR,  120, "Additional Amounts"));
        FIELD_DEFINITIONS.put(55,  new FieldDefinition(55,  LLLVAR,  999, "ICC Data"));
        FIELD_DEFINITIONS.put(56,  new FieldDefinition(56,  LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(57,  new FieldDefinition(57,  LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(58,  new FieldDefinition(58,  LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(59,  new FieldDefinition(59,  LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(60,  new FieldDefinition(60,  LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(61,  new FieldDefinition(61,  LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(62,  new FieldDefinition(62,  LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(63,  new FieldDefinition(63,  LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(64,  new FieldDefinition(64,  BINARY,  8,   "MAC"));
        FIELD_DEFINITIONS.put(65,  new FieldDefinition(65,  BINARY,  8,   "Bitmap Extended"));
        FIELD_DEFINITIONS.put(66,  new FieldDefinition(66,  NUMERIC, 1,   "Settlement Code"));
        FIELD_DEFINITIONS.put(67,  new FieldDefinition(67,  NUMERIC, 2,   "Extended Payment Code"));
        FIELD_DEFINITIONS.put(68,  new FieldDefinition(68,  NUMERIC, 3,   "Receiving Institution Country Code"));
        FIELD_DEFINITIONS.put(69,  new FieldDefinition(69,  NUMERIC, 3,   "Settlement Institution Country Code"));
        FIELD_DEFINITIONS.put(70,  new FieldDefinition(70,  NUMERIC, 3,   "Network Management Info Code"));
        FIELD_DEFINITIONS.put(71,  new FieldDefinition(71,  NUMERIC, 4,   "Message Number"));
        FIELD_DEFINITIONS.put(72,  new FieldDefinition(72,  NUMERIC, 4,   "Message Number Last"));
        FIELD_DEFINITIONS.put(73,  new FieldDefinition(73,  NUMERIC, 6,   "Date Action"));
        FIELD_DEFINITIONS.put(74,  new FieldDefinition(74,  NUMERIC, 10,  "Credits Number"));
        FIELD_DEFINITIONS.put(75,  new FieldDefinition(75,  NUMERIC, 10,  "Credits Reversal Number"));
        FIELD_DEFINITIONS.put(76,  new FieldDefinition(76,  NUMERIC, 10,  "Debits Number"));
        FIELD_DEFINITIONS.put(77,  new FieldDefinition(77,  NUMERIC, 10,  "Debits Reversal Number"));
        FIELD_DEFINITIONS.put(78,  new FieldDefinition(78,  NUMERIC, 10,  "Transfer Number"));
        FIELD_DEFINITIONS.put(79,  new FieldDefinition(79,  NUMERIC, 10,  "Transfer Reversal Number"));
        FIELD_DEFINITIONS.put(80,  new FieldDefinition(80,  NUMERIC, 10,  "Inquiries Number"));
        FIELD_DEFINITIONS.put(81,  new FieldDefinition(81,  NUMERIC, 10,  "Authorizations Number"));
        FIELD_DEFINITIONS.put(82,  new FieldDefinition(82,  NUMERIC, 12,  "Credits Processing Fee Amount"));
        FIELD_DEFINITIONS.put(83,  new FieldDefinition(83,  NUMERIC, 12,  "Credits Transaction Fee Amount"));
        FIELD_DEFINITIONS.put(84,  new FieldDefinition(84,  NUMERIC, 12,  "Debits Processing Fee Amount"));
        FIELD_DEFINITIONS.put(85,  new FieldDefinition(85,  NUMERIC, 12,  "Debits Transaction Fee Amount"));
        FIELD_DEFINITIONS.put(86,  new FieldDefinition(86,  NUMERIC, 16,  "Credits Amount"));
        FIELD_DEFINITIONS.put(87,  new FieldDefinition(87,  NUMERIC, 16,  "Credits Reversal Amount"));
        FIELD_DEFINITIONS.put(88,  new FieldDefinition(88,  NUMERIC, 16,  "Debits Amount"));
        FIELD_DEFINITIONS.put(89,  new FieldDefinition(89,  NUMERIC, 16,  "Debits Reversal Amount"));
        FIELD_DEFINITIONS.put(90,  new FieldDefinition(90,  NUMERIC, 42,  "Original Data Elements"));
        FIELD_DEFINITIONS.put(91,  new FieldDefinition(91,  ALPHANUM,1,   "File Update Code"));
        FIELD_DEFINITIONS.put(92,  new FieldDefinition(92,  NUMERIC, 2,   "File Security Code"));
        FIELD_DEFINITIONS.put(93,  new FieldDefinition(93,  NUMERIC, 5,   "Response Indicator"));
        FIELD_DEFINITIONS.put(94,  new FieldDefinition(94,  ALPHANUM,7,   "Service Indicator"));
        FIELD_DEFINITIONS.put(95,  new FieldDefinition(95,  ALPHANUM,42,  "Replacement Amounts"));
        FIELD_DEFINITIONS.put(96,  new FieldDefinition(96,  BINARY,  8,   "Message Security Code"));
        FIELD_DEFINITIONS.put(97,  new FieldDefinition(97,  ALPHANUM,17,  "Amount Net Settlement"));
        FIELD_DEFINITIONS.put(98,  new FieldDefinition(98,  ALPHANUM,25,  "Payee"));
        FIELD_DEFINITIONS.put(99,  new FieldDefinition(99,  LLVAR,   11,  "Settlement Institution ID"));
        FIELD_DEFINITIONS.put(100, new FieldDefinition(100, LLVAR,   11,  "Receiving Institution ID"));
        FIELD_DEFINITIONS.put(101, new FieldDefinition(101, LLVAR,   17,  "File Name"));
        FIELD_DEFINITIONS.put(102, new FieldDefinition(102, LLVAR,   28,  "Account ID 1 (From Account)"));
        FIELD_DEFINITIONS.put(103, new FieldDefinition(103, LLVAR,   28,  "Account ID 2 (To Account)"));
        FIELD_DEFINITIONS.put(104, new FieldDefinition(104, LLLVAR,  100, "Transaction Description"));
        FIELD_DEFINITIONS.put(105, new FieldDefinition(105, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(106, new FieldDefinition(106, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(107, new FieldDefinition(107, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(108, new FieldDefinition(108, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(109, new FieldDefinition(109, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(110, new FieldDefinition(110, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(111, new FieldDefinition(111, LLLVAR,  999, "Reserved ISO"));
        FIELD_DEFINITIONS.put(112, new FieldDefinition(112, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(113, new FieldDefinition(113, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(114, new FieldDefinition(114, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(115, new FieldDefinition(115, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(116, new FieldDefinition(116, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(117, new FieldDefinition(117, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(118, new FieldDefinition(118, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(119, new FieldDefinition(119, LLLVAR,  999, "Reserved National"));
        FIELD_DEFINITIONS.put(120, new FieldDefinition(120, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(121, new FieldDefinition(121, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(122, new FieldDefinition(122, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(123, new FieldDefinition(123, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(124, new FieldDefinition(124, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(125, new FieldDefinition(125, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(126, new FieldDefinition(126, LLLVAR,  999, "Token R1 (BCAD)"));
        FIELD_DEFINITIONS.put(127, new FieldDefinition(127, LLLVAR,  999, "Reserved Private"));
        FIELD_DEFINITIONS.put(128, new FieldDefinition(128, BINARY,  8,   "MAC 2"));
    }

    public FieldDefinition getFieldDefinition(int fieldNumber) {
        return FIELD_DEFINITIONS.get(fieldNumber);
    }

    public boolean hasField(int fieldNumber) {
        return FIELD_DEFINITIONS.containsKey(fieldNumber);
    }

    public static BcadIsoPackager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        private static final BcadIsoPackager INSTANCE = new BcadIsoPackager();
    }
}
