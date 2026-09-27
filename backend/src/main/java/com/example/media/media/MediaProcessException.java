package com.example.media.media;

public class MediaProcessException extends RuntimeException {
    public enum Reason { START_FAILED, EXIT_FAILED, TIMEOUT, BUSY, INTERRUPTED, OUTPUT_FAILED }
    private final Reason reason;
    public MediaProcessException(Reason reason) { super("Media process failed: " + reason); this.reason = reason; }
    public Reason reason() { return reason; }
}
