package com.example.media.stream;

import java.time.Instant;
import java.util.UUID;

public record StreamResponse(UUID sourceId, StreamType type, Status status, boolean running,
                             String streamUrl, Instant startedAt) {
    public enum Status { RUNNING, STOPPED }
    public static StreamResponse running(StreamKey key, Instant startedAt) {
        return new StreamResponse(key.sourceId(), key.type(), Status.RUNNING, true, key.streamUrl(), startedAt);
    }
    public static StreamResponse stopped(StreamKey key) {
        return new StreamResponse(key.sourceId(), key.type(), Status.STOPPED, false, null, null);
    }
}
