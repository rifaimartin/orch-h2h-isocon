package com.bcad.h2h.iso8583.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("H2H ISO 8583 BCAD — BCA Digital Integration API")
                        .description("""
                                Host-to-Host (H2H) Financial Integration Service antara BCA Digital dan BCA.
                                
                                Service ini menerima JSON REST API dari client, lalu mengkonversi ke pesan ISO 8583 BASE24 (BCAD variant) 
                                dan mengirimkannya ke BCA via TCP socket.
                                
                                **Alur Transaksi:**
                                1. Kirim 0200 Inquiry → Terima 0210
                                2. Jika inquiry berhasil, kirim 0200 Transfer → Terima 0210
                                
                                **Response Code (BIT 39):**
                                | Code | Status  | Keterangan |
                                |------|---------|------------|
                                | 00   | SUCCESS | Approved   |
                                | 68   | SUSPEND | Status tidak diketahui — jangan di-reverse |
                                | lainnya | FAILED | Ditolak  |
                                """)
                        .version("1.0.0-SNAPSHOT")
                        .contact(new Contact()
                                .name("BCA Digital — Platform Team")
                                .email("platform@bcadigital.co.id"))
                        .license(new License()
                                .name("Internal — BCA Digital")
                                .url("https://www.bluebirdgroup.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080/orch/h2h-isocon").description("Local"),
                        new Server().url("https://dskube-dev.dgcpdev.com/orch/h2h-isocon").description("DEV"),
                        new Server().url("https://dskube-uat.dgcpdev.com/orch/h2h-isocon").description("UAT")
                ));
    }
}
