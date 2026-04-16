package com.bcad.h2h.iso8583.mapper;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import com.bcad.h2h.iso8583.dto.request.InquiryRequest;
import com.bcad.h2h.iso8583.dto.request.TransferRequest;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.iso.token.BcadTokenR1Builder;
import com.bcad.h2h.iso8583.util.IsoDateTimeUtil;
import com.bcad.h2h.iso8583.util.RrnGenerator;
import com.bcad.h2h.iso8583.util.StanGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonToIsoMapper Tests")
class JsonToIsoMapperTest {

    private JsonToIsoMapper mapper;

    @BeforeEach
    void setUp() {
        IsoDateTimeUtil dateTimeUtil = new IsoDateTimeUtil();
        StanGenerator stanGenerator = new StanGenerator();
        RrnGenerator rrnGenerator = new RrnGenerator();
        TcpSocketProperties properties = new TcpSocketProperties();
        properties.setTerminalId("BCAD0001");
        properties.setMerchantId("BCADIGITAL001  ");
        properties.setInstitutionId("014");

        mapper = new JsonToIsoMapper(dateTimeUtil, stanGenerator, rrnGenerator, properties);
    }

    @Test
    @DisplayName("Map InquiryRequest to ISO 0200")
    void mapInquiryRequest() {
        InquiryRequest request = InquiryRequest.builder()
                .transactionId("TXN001")
                .amount(new BigDecimal("100.00"))
                .fromAccountNo("1234567890")
                .toAccountNo("0987654321")
                .transactionTime(LocalDateTime.of(2025, 3, 7, 10, 30, 45))
                .currencyCode("360")
                .terminalId("BCAD0001")
                .merchantId("BCADIGITAL001  ")
                .build();

        IsoMessage msg = mapper.mapInquiryRequest(request);

        assertNotNull(msg);
        assertEquals("0200", msg.getMti());
        assertEquals("321000", msg.getField(3)); // non-bluGiro = 321000
        assertEquals("000000010000", msg.getField(4)); // 100.00 * 100 = 10000 padded to 12 digits
        assertNotNull(msg.getField(7)); // DE7: MMDDhhmmss
        assertNotNull(msg.getField(11)); // DE11: STAN
        assertNotNull(msg.getField(37)); // DE37: RRN (12 chars)

        // Verify RRN is 12 chars
        String rrn = msg.getField(37);
        assertNotNull(rrn);
        assertEquals(12, rrn.length());

        // Verify STAN is 6 chars
        String stan = msg.getField(11);
        assertNotNull(stan);
        assertEquals(6, stan.length());

        assertEquals("360", msg.getField(49));
        assertEquals("1234567890", msg.getField(102));
        assertEquals("0987654321", msg.getField(103));

        // DE35: Track2 = 0 + BankCode 501 + 12-digit account + =999
        assertEquals("0501001234567890=999", msg.getField(35));
        // DE60: Terminal Data = MOBILE/INTERNET (non-bluGiro)
        assertEquals("MOBILE/INTERNET", msg.getField(60));

        // DE32: Acquiring Institution ID (bank code)
        assertEquals("501", msg.getField(32));
        // DE43: Card Acceptor Name/Location (mandatory, 40 chars)
        assertNotNull(msg.getField(43), "DE43 must be present in 0200");
        assertEquals(40, msg.getField(43).length(), "DE43 must be exactly 40 chars");
        // DE15 and DE100 must NOT be in outgoing 0200
        assertNull(msg.getField(15), "DE15 must not be in 0200 request");
        assertNull(msg.getField(100), "DE100 must not be in 0200 request");
        // DE48: Additional Data Private
        assertNotNull(msg.getField(48));
        // DE41: Terminal ID padded to 16
        assertEquals(16, msg.getField(41).length());

        // Inquiry DOES have DE126 (Token R1 with empty names)
        assertTrue(msg.hasField(126), "Inquiry should have DE126");
        String inquiryToken = msg.getField(126);
        assertEquals(BcadTokenR1Builder.TOTAL_TOKEN_LENGTH, inquiryToken.length(),
                "Inquiry Token R1 must be exactly " + BcadTokenR1Builder.TOTAL_TOKEN_LENGTH + " chars");
    }

    @Test
    @DisplayName("Map TransferRequest to ISO 0200 with DE126 Token R1")
    void mapTransferRequestWithDE126() {
        TransferRequest request = TransferRequest.builder()
                .transactionId("TXN002")
                .amount(new BigDecimal("500.00"))
                .fromAccountNo("1234567890")
                .toAccountNo("0987654321")
                .transactionTime(LocalDateTime.of(2025, 3, 7, 10, 30, 45))
                .currencyCode("360")
                .terminalId("BCAD0001")
                .merchantId("BCADIGITAL001  ")
                .senderName("JOHN DOE")
                .beneficiaryName("JANE DOE")
                .description("PAYMENT FOR SERVICES")
                .originalStan("000001")
                .build();

        IsoMessage msg = mapper.mapTransferRequest(request);

        assertNotNull(msg);
        assertEquals("0200", msg.getMti());
        assertEquals("401000", msg.getField(3));
        assertEquals("000000050000", msg.getField(4)); // 500.00 * 100 = 50000

        // Verify DE126 Token R1 is present
        assertTrue(msg.hasField(126), "Transfer must have DE126");
        String token = msg.getField(126);
        assertNotNull(token);

        // Verify token length: header(22) + token data(270) = 292
        assertEquals(BcadTokenR1Builder.TOTAL_TOKEN_LENGTH, token.length(),
                "Token R1 must be exactly " + BcadTokenR1Builder.TOTAL_TOKEN_LENGTH + " chars");

        // Verify header starts with "& "
        assertTrue(token.startsWith("& "), "Token R1 must start with '& '");

        // Parse token and verify fields
        BcadTokenR1Builder.TokenR1 parsed = BcadTokenR1Builder.parse(token);
        assertEquals("JANE DOE", parsed.beneficiaryName());
        assertEquals("JOHN DOE", parsed.senderName());
        assertEquals("PAYMENT FOR SERVICES", parsed.description().trim());
        assertEquals("3", parsed.acquirerIndicator());
    }

    @Test
    @DisplayName("Map NetworkManagement Logon to ISO 0800")
    void mapNetworkManagementLogon() {
        LocalDateTime serverTime = LocalDateTime.now();
        IsoMessage msg = mapper.mapNetworkManagement("001", serverTime);

        assertNotNull(msg);
        assertEquals("0800", msg.getMti());
        assertEquals("001", msg.getField(70));
        assertNotNull(msg.getField(11)); // STAN
        assertNotNull(msg.getField(7));  // Transmission DateTime
    }

    @Test
    @DisplayName("Amount formatting: multiply by 100, 12 digits zero-padded")
    void amountFormatting() {
        InquiryRequest request = InquiryRequest.builder()
                .transactionId("TXN003")
                .amount(new BigDecimal("1234567.89"))
                .fromAccountNo("1234567890")
                .toAccountNo("0987654321")
                .transactionTime(LocalDateTime.now())
                .currencyCode("360")
                .terminalId("BCAD0001")
                .merchantId("BCADIGITAL001  ")
                .build();

        IsoMessage msg = mapper.mapInquiryRequest(request);

        // 1234567.89 * 100 = 123456789 → padded to 12 digits = "000123456789"
        assertEquals("000123456789", msg.getField(4));
    }
}
