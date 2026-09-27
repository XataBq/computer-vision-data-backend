package com.example.media.media;

import com.example.media.camera.CameraSourceType;
import com.example.media.config.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class HlsCommandFactoryTest {
    private final HlsCommandFactory commands = new HlsCommandFactory(
            new MediaProperties("custom-ffmpeg", "ffprobe", Duration.ofSeconds(15), Duration.ofSeconds(20), 4),
            new HlsProperties(3, 7, Duration.ofSeconds(10), Duration.ofSeconds(3), 4),
            new MediaInputArguments(new RtspProperties("udp")));
    @Test void simulationLoopsAtRealtimeAndUsesConfiguredHls() {
        var args = commands.simulation(Path.of("video with spaces.mp4"), Path.of("output"));
        assertThat(args).containsSequence("-re", "-stream_loop", "-1")
                .containsSequence("-c:v", "libx264").contains("-an")
                .containsSequence("-hls_time", "3").containsSequence("-hls_list_size", "7")
                .containsSequence("-force_key_frames", "expr:gte(t,n_forced*3)")
                .contains("delete_segments+temp_file+independent_segments").doesNotContain("-r", "-vf");
        assertThat(args.indexOf("-re")).isLessThan(args.indexOf("-i"));
    }
    @Test void rtspUsesConfiguredTransportAndPreservesSingleUrlArgument() {
        String url = "rtsp://user:secret@host/live?one=1&two=2";
        var args = commands.camera(url, CameraSourceType.RTSP, Path.of("output"));
        assertThat(args).containsSequence("-rtsp_transport", "udp").containsSequence("-i", url)
                .doesNotContain("-re", "-stream_loop", "cmd", "bash");
    }
    @Test void hlsInputCannotOpenLocalFiles() {
        var args = commands.camera("https://host/live.m3u8", CameraSourceType.HLS, Path.of("output"));
        assertThat(args).containsSequence("-protocol_whitelist", "http,https,tcp,tls,crypto").doesNotContain("-rtsp_transport");
    }
}
