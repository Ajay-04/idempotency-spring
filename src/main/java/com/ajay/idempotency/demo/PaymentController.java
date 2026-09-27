package com.ajay.idempotency.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo endpoint: a fake "charge the customer" API.
 *
 * <p>{@code chargesExecuted} counts how many times the side effect actually ran.
 * Send the same request twice with the same {@code Idempotency-Key} and the
 * counter moves exactly once — the second request gets the replayed response.
 */
@RestController
public class PaymentController {

    private final AtomicInteger chargesExecuted = new AtomicInteger();

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> charge(@RequestBody Map<String, Object> body) {
        int n = chargesExecuted.incrementAndGet(); // the side effect we must not repeat
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(Map.of(
                "paymentId", paymentId,
                "amount", body.getOrDefault("amount", 0),
                "chargesExecuted", n));
    }
}
