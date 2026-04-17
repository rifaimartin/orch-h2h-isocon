package com.bcad.h2h.iso8583.iso;

import com.bcad.h2h.iso8583.config.TcpSocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IsoEncoder - buildBicHeader Tests")
class IsoEncoderBicHeaderTest {

    private IsoEncoder encoder;

    @BeforeEach
    void setUp() {
        TcpSocketProperties properties = new TcpSocketProperties();
        properties.setBicHeaderEnabled(true);
        encoder = new IsoEncoder(properties);
    }

    private String invokeBuildBicHeader(String mti) throws Exception {
        Method method = IsoEncoder.class.getDeclaredMethod("buildBicHeader", String.class);
        method.setAccessible(true);
        return (String) method.invoke(encoder, mti);
    }

    @Test
    @DisplayName("0200 request: originator=1, responder=0")
    void buildBicHeader_0200_shouldReturn_ISO015000010() throws Exception {
        String header = invokeBuildBicHeader("0200");
        assertEquals("ISO015000010", header);
        assertEquals(12, header.length());
    }

    @Test
    @DisplayName("0210 response: originator=1, responder=3 (was buggy: originator was 3)")
    void buildBicHeader_0210_shouldHaveCorrectOriginatorAndResponder() throws Exception {
        String header = invokeBuildBicHeader("0210");
        assertEquals("ISO015000013", header, "Full BIC header for 0210");
        assertEquals(12, header.length());
        // Header: ISO(3) + PI(2) + Release(2) + Status(3) + Orig(1) + Resp(1) = 12
        // Originator at index 10, Responder at index 11
        assertEquals('1', header.charAt(10), "Originator code for 0210 must be '1'");
        assertEquals('3', header.charAt(11), "Responder code for 0210 must be '3'");
    }

    @Test
    @DisplayName("0800 NMM request: originator=6, responder=0")
    void buildBicHeader_0800_shouldReturn_ISO005000060() throws Exception {
        String header = invokeBuildBicHeader("0800");
        assertEquals("ISO005000060", header);
    }

    @Test
    @DisplayName("0810 NMM response: originator=6, responder=6")
    void buildBicHeader_0810_shouldReturn_ISO005000066() throws Exception {
        String header = invokeBuildBicHeader("0810");
        assertEquals("ISO005000066", header);
    }

    @Test
    @DisplayName("BIC header disabled returns null")
    void buildBicHeader_disabled_shouldReturnNull() throws Exception {
        TcpSocketProperties props = new TcpSocketProperties();
        props.setBicHeaderEnabled(false);
        IsoEncoder disabledEncoder = new IsoEncoder(props);

        Method method = IsoEncoder.class.getDeclaredMethod("buildBicHeader", String.class);
        method.setAccessible(true);
        assertNull(method.invoke(disabledEncoder, "0200"));
    }
}
