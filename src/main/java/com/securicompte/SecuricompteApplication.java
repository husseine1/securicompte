package com.securicompte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SecuricompteApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecuricompteApplication.class, args);
    }
}
