package com.example.media.media;

import com.example.media.config.MediaProperties;
import com.example.media.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FfprobeServiceTest {
    private final FfprobeService service = new FfprobeService(mock(ProcessExecutor.class),
            new MediaProperties("ffmpeg", "ffprobe", Duration.ofSeconds(15), Duration.ofSeconds(20), 4), new ObjectMapper());
    private String json(String rate, String brand) {
        return """
            {"streams":[{"codec_name":"h264","width":1920,"height":1080,"avg_frame_rate":"%s"}],
             "format":{"duration":"60.5","format_name":"mov,mp4,m4a,3gp,3g2,mj2","tags":{"major_brand":"%s"}}}
            """.formatted(rate, brand);
    }
    @Test void parsesFractionalFps() {
        var result = service.parse(json("30000/1001", "isom").getBytes(StandardCharsets.UTF_8));
        assertThat(result.fps()).isCloseTo(29.970, within(0.001));
        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.durationSeconds()).isEqualTo(60.5);
    }
    @Test void rejectsInvalidRateAndWrongContainer() {
        assertThatThrownBy(() -> service.parse(json("0/0", "isom").getBytes(StandardCharsets.UTF_8))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.parse(json("30/1", "qt  ").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status().value()).isEqualTo(415));
    }
    @Test void rejectsMissingVideoStream() {
        assertThatThrownBy(() -> service.parse(json("30/1", "isom").replace("\"width\":1920", "\"width\":0").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class);
    }
}
