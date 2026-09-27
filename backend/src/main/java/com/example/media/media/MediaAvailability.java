package com.example.media.media;

import com.example.media.config.MediaProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component("mediaTools")
public class MediaAvailability implements InitializingBean, HealthIndicator {
    private final ProcessExecutor executor;
    private final MediaProperties properties;
    private volatile boolean available;
    public MediaAvailability(ProcessExecutor executor, MediaProperties properties) { this.executor = executor; this.properties = properties; }
    @Override public void afterPropertiesSet() {
        check(properties.ffmpegPath(), "ffmpeg", "FFmpeg");
        check(properties.ffprobePath(), "ffprobe", "FFprobe");
        available = true;
    }
    private void check(String path, String expected, String label) {
        try {
            String output = new String(executor.execute(List.of(path, "-version"), Duration.ofSeconds(5)), StandardCharsets.UTF_8);
            if (!output.startsWith(expected + " version")) throw new IllegalStateException();
        } catch (RuntimeException ex) {
            // Omit OS exception/command details. Fail startup with an actionable but safe message.
            throw new IllegalStateException(label + " executable not found or unusable; configure media." + expected + "-path");
        }
    }
    @Override public Health health() { return available ? Health.up().build() : Health.down().build(); }
}
