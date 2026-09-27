package com.example.media.stream;

import java.util.Objects;
import java.util.UUID;

public record StreamKey(StreamType type, UUID sourceId) {
    public StreamKey { Objects.requireNonNull(type); Objects.requireNonNull(sourceId); }
    public String streamUrl() { return "/streams/" + type.directory() + "/" + sourceId + "/index.m3u8"; }
}
