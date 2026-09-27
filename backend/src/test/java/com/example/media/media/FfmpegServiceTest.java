package com.example.media.media;

import com.example.media.camera.CameraSourceType;
import com.example.media.config.MediaProperties;
import com.example.media.config.RtspProperties;
import com.example.media.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class FfmpegServiceTest {
    private final MediaProperties properties = new MediaProperties("ffmpeg", "ffprobe", Duration.ofSeconds(15), Duration.ofSeconds(20), 4);
    private final MediaInputArguments inputs = new MediaInputArguments(new RtspProperties("tcp"));
    @Test void buildsArgumentListAndDeletesTemporaryFile() throws Exception {
        AtomicReference<List<String>> args = new AtomicReference<>();
        byte[] jpeg = {(byte) 255, (byte) 216, (byte) 255, (byte) 217};
        ProcessExecutor runner = (command, timeout) -> {
            args.set(command);
            try { Files.write(Path.of(command.getLast()), jpeg); } catch (Exception ex) { throw new RuntimeException(ex); }
            return new byte[0];
        };
        String url = "rtsp://user:secret@host/live?x=hello&y=world";
        assertThat(new FfmpegService(runner, properties, inputs).cameraSnapshot(url, CameraSourceType.RTSP)).isEqualTo(jpeg);
        assertThat(args.get()).containsSequence("-i", url).containsSequence("-frames:v", "1").doesNotContain("-vf", "-r", "cmd", "/bin/sh");
        assertThat(Path.of(args.get().getLast())).doesNotExist();
    }
    @Test void timeoutCleansUpAndMapsTo503() {
        AtomicReference<Path> output = new AtomicReference<>();
        ProcessExecutor runner = (command, timeout) -> {
            output.set(Path.of(command.getLast()));
            throw new MediaProcessException(MediaProcessException.Reason.TIMEOUT);
        };
        assertThatThrownBy(() -> new FfmpegService(runner, properties, inputs).videoSnapshot(Path.of("sample.mp4"), 30))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status().value()).isEqualTo(503));
        assertThat(output.get()).doesNotExist();
    }
}
