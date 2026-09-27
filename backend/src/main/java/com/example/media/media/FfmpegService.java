package com.example.media.media;

import com.example.media.camera.CameraSourceType;
import com.example.media.config.MediaProperties;
import com.example.media.exception.ApiException;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class FfmpegService {
    private static final Logger log = LoggerFactory.getLogger(FfmpegService.class);
    private static final long MAX_JPEG_BYTES = 64L * 1024 * 1024;
    private final ProcessExecutor executor;
    private final MediaProperties properties;
    private final MediaInputArguments inputs;
    public FfmpegService(ProcessExecutor executor, MediaProperties properties, MediaInputArguments inputs) {
        this.executor = executor; this.properties = properties; this.inputs = inputs;
    }

    public byte[] cameraSnapshot(String url, CameraSourceType type) {
        return snapshot(inputs.camera(url, type), null);
    }
    public byte[] videoSnapshot(Path file, double second) { return snapshot(inputs.file(file), second); }

    private byte[] snapshot(List<String> inputArguments, Double second) {
        Path output = null;
        try {
            output = Files.createTempFile("media-snapshot-", ".jpg");
            List<String> command = new ArrayList<>(List.of(properties.ffmpegPath(), "-hide_banner", "-loglevel", "error", "-nostdin"));
            if (second != null) command.addAll(List.of("-ss", Double.toString(second)));
            command.addAll(inputArguments);
            command.addAll(List.of("-map", "0:v:0", "-frames:v", "1", "-c:v", "mjpeg", "-q:v", "2", "-y", output.toString()));
            executor.execute(List.copyOf(command), properties.snapshotTimeout());
            long size = Files.size(output);
            if (size < 4 || size > MAX_JPEG_BYTES) throw unavailable("SNAPSHOT_FAILED", "Could not extract a JPEG frame");
            byte[] jpeg = Files.readAllBytes(output);
            if ((jpeg[0] & 255) != 255 || (jpeg[1] & 255) != 216 || (jpeg[jpeg.length - 2] & 255) != 255 || (jpeg[jpeg.length - 1] & 255) != 217)
                throw unavailable("SNAPSHOT_FAILED", "Could not extract a JPEG frame");
            return jpeg;
        } catch (MediaProcessException ex) {
            throw unavailable(ex.reason() == MediaProcessException.Reason.TIMEOUT ? "SNAPSHOT_TIMEOUT" : "SNAPSHOT_UNAVAILABLE", "Snapshot is temporarily unavailable");
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SNAPSHOT_IO_ERROR", "Cannot access snapshot temporary storage");
        } finally {
            if (output != null) try { Files.deleteIfExists(output); } catch (IOException ex) { log.warn("Could not remove temporary snapshot"); }
        }
    }
    private ApiException unavailable(String code, String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message); }
}
