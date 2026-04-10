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
        assertEquals("310000", msg.getField(3));
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

        // Inquiry should NOT have DE126
        assertFalse(msg.hasField(126), "Inquiry should not have DE126");
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
        assertEquals("400000", msg.getField(3));
        assertEquals("000000050000", msg.getField(4)); // 500.00 * 100 = 50000

        // Verify DE126 Token R1 is present
        assertTrue(msg.hasField(126), "Transfer must have DE126");
        String token = msg.getField(126);
        assertNotNull(token);

        // Verify token length: 30 + 30 + 18 + 1 + 1 = 80
        assertEquals(80, token.length(), "Token R1 must be exactly 80 chars");

        // Parse token and verify fields
        BcadTokenR1Builder.TokenR1 parsed = BcadTokenR1Builder.parse(token);
        assertEquals("JANE DOE", parsed.beneficiaryName());
        assertEquals("JOHN DOE", parsed.senderName());
        assertEquals("PAYMENT FOR SERVIC", parsed.description().trim());
        assertEquals("D", parsed.acquirerIndicator());
        assertEquals("I", parsed.issuerIndicator());
    }

    @Test
    @DisplayName("Map NetworkManagement Logon to ISO 0800")
    void mapNetworkManagementLogon() {
        IsoMessage msg = mapper.mapNetworkManagement("001", java.time.LocalDateTime.now());

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
