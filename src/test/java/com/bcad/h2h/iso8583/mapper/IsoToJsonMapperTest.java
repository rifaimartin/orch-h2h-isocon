package com.bcad.h2h.iso8583.mapper;

import com.bcad.h2h.iso8583.dto.response.InquiryResponse;
import com.bcad.h2h.iso8583.iso.IsoMessage;
import com.bcad.h2h.iso8583.util.ResponseCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IsoToJsonMapper Tests")
class IsoToJsonMapperTest {

    private IsoToJsonMapper mapper;

    @BeforeEach
    void setUp() {
        ResponseCodeMapper responseCodeMapper = new ResponseCodeMapper();
        mapper = new IsoToJsonMapper(responseCodeMapper);
    }

    /**
     * Build a realistic DE126 token string (after LLLVAR length prefix is stripped by parser).
     * Layout (0-indexed):
     *   0-1   = "& " (token header)
     *   2-6   = "00002" (number of tokens + 1)
     *   7-11  = token all length
     *   12-13 = "! " (token indicator)
     *   14-15 = "R1" (token ID)
     *   16-20 = token length
     *   21    = filler (space)
     *   22-24 = kode bank tujuan (3)
     *   25-59 = nama pemilik rekening tujuan 1 (35)
     *   60-94 = nama pemilik rekening tujuan 2 (35)
     *   ... etc
     */
    private String buildDe126WithBeneficiaryName(String beneficiaryName) {
        StringBuilder sb = new StringBuilder();
        sb.append("& ");           // 0-1: token header
        sb.append("00002");        // 2-6: number of tokens
        sb.append("00252");        // 7-11: token all length
        sb.append("! ");           // 12-13: token indicator
        sb.append("R1");           // 14-15: token ID
        sb.append("00245");        // 16-20: token length
        sb.append(" ");            // 21: filler
        sb.append("014");          // 22-24: kode bank tujuan

        // 25-59: nama pemilik rekening tujuan 1 (35 chars, space-padded)
        String padded = String.format("%-35s", beneficiaryName);
        sb.append(padded);

        // 60-94: nama pemilik rekening tujuan 2 (35 chars)
        sb.append(String.format("%-35s", ""));

        // Fill remaining fields to make it realistic
        sb.append(String.format("%-35s", "SENDER NAME"));  // 95-129: nama pengirim 1
        sb.append(String.format("%-35s", ""));              // 130-164: nama pengirim 2
        sb.append(String.format("%-35s", "PAYMENT"));       // 165-199: deskripsi 1
        sb.append(String.format("%-35s", ""));              // 200-234: deskripsi 2
        sb.append(String.format("%-35s", ""));              // 235-269: deskripsi 3
        sb.append("3");                                      // 270: acquirer transfer indicator
        sb.append("0");                                      // 271: switch transfer indicator
        sb.append(String.format("%-3s", "501"));             // 272-274: kode bank issuer
        sb.append(String.format("%-17s", ""));               // 275-291: filler

        return sb.toString();
    }

    @Test
    @DisplayName("mapToInquiryResponse extracts beneficiary name from DE126 at correct offset 25-59")
    void mapToInquiryResponse_shouldExtractBeneficiaryNameFromCorrectOffset() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "00");
        msg.setField(11, "123456");
        msg.setField(37, "000000123456");
        msg.setField(7, "0209071903");

        String expectedName = "JOHN DOE";
        msg.setField(126, buildDe126WithBeneficiaryName(expectedName));

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-001");

        assertEquals(expectedName, response.getBeneficiaryName());
    }

    @Test
    @DisplayName("mapToInquiryResponse trims padded beneficiary name")
    void mapToInquiryResponse_shouldTrimPaddedBeneficiaryName() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "00");
        msg.setField(11, "123456");
        msg.setField(37, "000000123456");
        msg.setField(7, "0209071903");

        msg.setField(126, buildDe126WithBeneficiaryName("AHMAD RIZKY"));

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-002");

        assertEquals("AHMAD RIZKY", response.getBeneficiaryName());
    }

    @Test
    @DisplayName("mapToInquiryResponse returns empty beneficiary when DE126 is null")
    void mapToInquiryResponse_shouldReturnEmptyBeneficiaryWhenDe126Null() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "00");
        msg.setField(11, "123456");
        msg.setField(37, "000000123456");
        msg.setField(7, "0209071903");

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-003");

        assertEquals("", response.getBeneficiaryName());
    }

    @Test
    @DisplayName("mapToInquiryResponse returns empty beneficiary when DE126 too short")
    void mapToInquiryResponse_shouldReturnEmptyBeneficiaryWhenDe126TooShort() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "00");
        msg.setField(11, "123456");
        msg.setField(37, "000000123456");
        msg.setField(7, "0209071903");
        msg.setField(126, "& 00002");  // too short

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-004");

        assertEquals("", response.getBeneficiaryName());
    }

    @Test
    @DisplayName("mapToInquiryResponse maps response code and status correctly")
    void mapToInquiryResponse_shouldMapResponseCodeAndStatus() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "00");
        msg.setField(11, "654321");
        msg.setField(37, "000000654321");
        msg.setField(7, "0209141902");
        msg.setField(126, buildDe126WithBeneficiaryName("TEST USER"));

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-005");

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("00", response.getResponseCode());
        assertEquals("654321", response.getStan());
        assertEquals("000000654321", response.getRrn());
        assertEquals("TXN-005", response.getTransactionId());
    }

    @Test
    @DisplayName("mapToInquiryResponse handles RC 68 as SUSPEND")
    void mapToInquiryResponse_rc68_shouldMapToSuspend() {
        IsoMessage msg = new IsoMessage("0210");
        msg.setField(39, "68");
        msg.setField(11, "111111");
        msg.setField(37, "000000111111");
        msg.setField(7, "0209071903");
        msg.setField(126, buildDe126WithBeneficiaryName("SUSPEND USER"));

        InquiryResponse response = mapper.mapToInquiryResponse(msg, "TXN-006");

        assertEquals("SUSPEND", response.getStatus());
        assertEquals("68", response.getResponseCode());
    }
}
