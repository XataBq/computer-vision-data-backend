package com.example.media.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties("media")
public record MediaProperties(
        @NotBlank @DefaultValue("ffmpeg") String ffmpegPath,
        @NotBlank @DefaultValue("ffprobe") String ffprobePath,
        @NotNull @DefaultValue("15s") Duration snapshotTimeout,
        @NotNull @DefaultValue("20s") Duration probeTimeout,
        @Min(1) @Max(64) @DefaultValue("4") int maxConcurrentProcesses) {
    @AssertTrue(message = "Media timeouts must be positive and at most 10 minutes")
    public boolean isTimeoutValid() {
        return valid(snapshotTimeout) && valid(probeTimeout);
    }
    private static boolean valid(Duration value) {
        return value != null && value.toMillis() > 0 && value.compareTo(Duration.ofMinutes(10)) <= 0;
    }
}
