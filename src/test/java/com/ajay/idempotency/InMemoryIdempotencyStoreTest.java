package com.ajay.idempotency;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryIdempotencyStoreTest {

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

    private static StoredResponse response(String body) {
        return new StoredResponse(200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTrip() {
        store.putIfAbsent("k1", response("{\"ok\":true}"), Duration.ofMinutes(5));
        assertTrue(store.get("k1").isPresent());
        assertEquals("{\"ok\":true}",
                new String(store.get("k1").get().getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void missingKeyReturnsEmpty() {
        assertTrue(store.get("nope").isEmpty());
    }

    @Test
    void firstWriteWins() {
        store.putIfAbsent("k2", response("first"), Duration.ofMinutes(5));
        store.putIfAbsent("k2", response("second"), Duration.ofMinutes(5));
        assertEquals("first",
                new String(store.get("k2").get().getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void expiredRecordsAreInvisible() throws InterruptedException {
        store.putIfAbsent("k3", response("x"), Duration.ofMillis(50));
        Thread.sleep(120);
        assertTrue(store.get("k3").isEmpty());
    }

    @Test
    void deleteRemovesRecord() {
        store.putIfAbsent("k4", response("x"), Duration.ofMinutes(5));
        store.delete("k4");
        assertTrue(store.get("k4").isEmpty());
    }
}
