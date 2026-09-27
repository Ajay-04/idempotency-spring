package com.ajay.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implements the {@code Idempotency-Key} contract for POST/PUT/PATCH endpoints:
 *
 * <ol>
 *   <li>Request arrives with an {@code Idempotency-Key} header.</li>
 *   <li>Key seen before → the stored response is replayed, the handler never runs.</li>
 *   <li>Key is new → the request executes normally; if it succeeds (2xx), the
 *       response is stored under the key for {@code idempotency.ttl}.</li>
 *   <li>Two requests racing with the same unseen key → the loser waits for the
 *       winner's outcome instead of executing the side effect twice.</li>
 * </ol>
 *
 * <p>Only successful (2xx) responses are cached. A cached 500 would turn a
 * transient failure into a permanent one, and failures must stay retryable.
 */
@Component
public class IdempotencyFilter extends OncePerRequestFilter {

    static final String HEADER = "Idempotency-Key";

    /** Keys currently being executed, so racing duplicates can wait for the outcome. */
    private final Map<String, CompletableFuture<StoredResponse>> inFlight = new ConcurrentHashMap<>();

    private final IdempotencyStore store;
    private final Duration ttl;
    private final Duration inFlightTimeout;

    public IdempotencyFilter(IdempotencyStore store,
                             @Value("${idempotency.ttl:PT24H}") Duration ttl,
                             @Value("${idempotency.inflight-timeout:PT30S}") Duration inFlightTimeout) {
        this.store = store;
        this.ttl = ttl;
        this.inFlightTimeout = inFlightTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);

        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Fast path: key already completed.
        Optional<StoredResponse> stored = store.get(key);
        if (stored.isPresent()) {
            replay(response, stored.get());
            return;
        }

        // 2. Race path: another request is executing this key right now.
        CompletableFuture<StoredResponse> ours = new CompletableFuture<>();
        CompletableFuture<StoredResponse> existing = inFlight.putIfAbsent(key, ours);
        if (existing != null) {
            waitForInFlight(existing, response, chain, request);
            return;
        }

        // 3. We won the race: execute, then publish the outcome.
        try {
            ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
            chain.doFilter(request, wrapped);

            byte[] body = wrapped.getContentAsByteArray();
            int status = wrapped.getStatusCode();
            if (status >= 200 && status < 300) {
                StoredResponse outcome =
                        new StoredResponse(status, wrapped.getContentType(), body);
                store.putIfAbsent(key, outcome, ttl);
                ours.complete(outcome);
            } else {
                // Don't cache failures — and don't publish them either.
                ours.completeExceptionally(
                        new IllegalStateException("not cached, status=" + status));
            }
            wrapped.copyBodyToResponse();
        } catch (Exception e) {
            ours.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, ours);
        }
    }

    private void waitForInFlight(CompletableFuture<StoredResponse> existing,
                                 HttpServletResponse response,
                                 FilterChain chain,
                                 HttpServletRequest request)
            throws ServletException, IOException {
        try {
            // Wait for the winner, but not forever: if it hangs, we'd rather
            // execute than deadlock the duplicate behind it.
            StoredResponse outcome = existing.get(inFlightTimeout.toSeconds(), TimeUnit.SECONDS);
            replay(response, outcome);
        } catch (TimeoutException e) {
            chain.doFilter(request, response);
        } catch (Exception e) {
            // Winner failed or was interrupted: the duplicate becomes the new
            // attempt rather than inheriting the failure.
            chain.doFilter(request, response);
        }
    }

    private void replay(HttpServletResponse response, StoredResponse stored) throws IOException {
        response.setStatus(stored.getStatus());
        if (stored.getContentType() != null) {
            response.setContentType(stored.getContentType());
        }
        response.getOutputStream().write(stored.getBody());
    }
}
