package com.ajay.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory {@link IdempotencyStore} for development and tests.
 *
 * <p>Records expire lazily on read, and a background sweeper removes expired
 * entries so the map cannot grow without bound. Not suitable for multi-instance
 * deployments — every instance keeps its own map, so use the Redis variant in
 * production (see README).
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, Entry> records = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sweeper;

    public InMemoryIdempotencyStore() {
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idempotency-sweeper");
            t.setDaemon(true);
            return t;
        });
        // Sweep twice as often as the shortest realistic TTL so expired
        // records don't linger long, without waking up pointlessly.
        this.sweeper.scheduleAtFixedRate(this::sweep, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public Optional<StoredResponse> get(String key) {
        Entry entry = records.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                records.remove(key, entry);
            }
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    @Override
    public void putIfAbsent(String key, StoredResponse response, Duration ttl) {
        records.putIfAbsent(key, new Entry(response, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public void delete(String key) {
        records.remove(key);
    }

    /** Visible for tests. */
    int size() {
        return records.size();
    }

    private void sweep() {
        records.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private record Entry(StoredResponse response, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
