package com.ajay.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage contract for idempotency records.
 *
 * <p>Client contract: the caller generates one UUID per <em>logical operation</em>
 * (not per HTTP attempt) and sends it as the {@code Idempotency-Key} header.
 * Retrying the same request with the same key is always safe.
 *
 * <p>Production implementations: Redis with {@code SET key value NX EX ttl},
 * or a database table with a unique constraint on the key column.
 */
public interface IdempotencyStore {

    /**
     * Returns the stored response for {@code key}, or empty if the key was
     * never seen or its record has expired.
     */
    Optional<StoredResponse> get(String key);

    /**
     * Stores {@code response} under {@code key} for {@code ttl}.
     * If the key already exists, the existing record is kept (first write wins),
     * so concurrent duplicate requests converge on a single outcome.
     */
    void putIfAbsent(String key, StoredResponse response, Duration ttl);

    /** Removes the record for {@code key}, if any. Mainly useful in tests. */
    void delete(String key);
}
