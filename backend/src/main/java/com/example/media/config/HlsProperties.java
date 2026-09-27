package com.example.media.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("media.hls")
public record HlsProperties(
        @Min(1) @Max(30) @DefaultValue("2") int segmentDuration,
        @Min(2) @Max(100) @DefaultValue("5") int playlistSize,
        @NotNull @DefaultValue("10s") Duration startupTimeout,
        @NotNull @DefaultValue("3s") Duration stopTimeout,
        @Min(1) @Max(64) @DefaultValue("4") int maxStreams) {
    @AssertTrue(message = "HLS timeouts must be positive and at most 2 minutes")
    public boolean isTimeoutValid() { return valid(startupTimeout) && valid(stopTimeout); }
    private static boolean valid(Duration value) {
        return value != null && value.toMillis() > 0 && value.compareTo(Duration.ofMinutes(2)) <= 0;
    }
}
