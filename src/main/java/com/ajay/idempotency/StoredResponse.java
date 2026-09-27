package com.ajay.idempotency;

import java.util.Objects;

/**
 * The response captured for an idempotency key, replayed verbatim when the
 * same key is seen again.
 */
public final class StoredResponse {

    private final int status;
    private final String contentType;
    private final byte[] body;

    public StoredResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = Objects.requireNonNull(body, "body").clone();
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body.clone();
    }
}
