package dev.redisinjava.protocol;

/** Raised on malformed client input; the server closes the connection. */
public final class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
