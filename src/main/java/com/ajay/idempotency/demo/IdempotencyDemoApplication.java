package com.ajay.idempotency.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ajay.idempotency")
public class IdempotencyDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdempotencyDemoApplication.class, args);
    }
}
