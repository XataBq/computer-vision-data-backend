package com.example.media.media;

import com.example.media.config.MediaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaAvailabilityTest {
    private final ProcessExecutor executor = mock(ProcessExecutor.class);
    private final MediaAvailability health = new MediaAvailability(executor,
            new MediaProperties("ffmpeg", "ffprobe", Duration.ofSeconds(15), Duration.ofSeconds(20), 4));
    @Test void validatesOnceAndCachesHealth() {
        when(executor.execute(eq(List.of("ffmpeg", "-version")), any())).thenReturn("ffmpeg version test".getBytes(StandardCharsets.UTF_8));
        when(executor.execute(eq(List.of("ffprobe", "-version")), any())).thenReturn("ffprobe version test".getBytes(StandardCharsets.UTF_8));
        health.afterPropertiesSet();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        verify(executor, times(2)).execute(any(), any());
    }
    @Test void missingFfmpegFailsFastWithSafeMessage() {
        when(executor.execute(any(), any())).thenThrow(new MediaProcessException(MediaProcessException.Reason.START_FAILED));
        assertThatThrownBy(health::afterPropertiesSet).isInstanceOf(IllegalStateException.class).hasMessageContaining("FFmpeg executable not found");
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
    }
    @Test void missingFfprobeFailsFast() {
        when(executor.execute(eq(List.of("ffmpeg", "-version")), any())).thenReturn("ffmpeg version test".getBytes(StandardCharsets.UTF_8));
        when(executor.execute(eq(List.of("ffprobe", "-version")), any())).thenThrow(new MediaProcessException(MediaProcessException.Reason.START_FAILED));
        assertThatThrownBy(health::afterPropertiesSet).hasMessageContaining("FFprobe executable not found");
    }
}
