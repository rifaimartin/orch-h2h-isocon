package com.bcad.h2h.iso8583;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class H2hIso8583Application {
    public static void main(String[] args) {
        SpringApplication.run(H2hIso8583Application.class, args);
    }
}
