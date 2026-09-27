package com.example.media.media;

import com.example.media.config.MediaProperties;
import com.example.media.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Service
public class FfprobeService {
    private final ProcessExecutor executor;
    private final MediaProperties properties;
    private final ObjectMapper mapper;
    public FfprobeService(ProcessExecutor executor, MediaProperties properties, ObjectMapper mapper) {
        this.executor = executor; this.properties = properties; this.mapper = mapper;
    }
    public VideoMetadata probe(Path file) {
        byte[] output;
        try {
            output = executor.execute(List.of(properties.ffprobePath(), "-v", "error", "-protocol_whitelist", "file",
                    "-select_streams", "v:0", "-show_entries",
                    "stream=codec_name,width,height,avg_frame_rate,r_frame_rate:stream_disposition=attached_pic:format=duration,format_name:format_tags=major_brand",
                    "-of", "json", file.toAbsolutePath().toString()), properties.probeTimeout());
        } catch (MediaProcessException ex) {
            if (ex.reason() == MediaProcessException.Reason.EXIT_FAILED) throw invalid();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROBE_UNAVAILABLE", "Video analysis is temporarily unavailable");
        }
        return parse(output);
    }
    public VideoMetadata parse(byte[] output) {
        try {
            JsonNode root = mapper.readTree(output);
            JsonNode format = root.path("format");
            String formats = format.path("format_name").asText();
            String brand = format.path("tags").path("major_brand").asText().trim();
            // FFprobe uses one demuxer for MOV/MP4/3GP; check the container brand as well.
            if (!Arrays.asList(formats.split(",")).contains("mp4")
                    || !(brand.startsWith("iso") || brand.startsWith("mp4") || Set.of("avc1", "M4V", "M4VH", "M4VP", "dash", "MSNV").contains(brand)))
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_CONTAINER", "Only MP4 video containers are supported");
            JsonNode stream = root.path("streams").path(0);
            double duration = format.path("duration").asDouble(Double.NaN);
            int width = stream.path("width").asInt();
            int height = stream.path("height").asInt();
            double fps = rate(stream.path("avg_frame_rate").asText());
            if (!positive(fps)) fps = rate(stream.path("r_frame_rate").asText());
            String codec = stream.path("codec_name").asText();
            if (!positive(duration) || width <= 0 || height <= 0 || !positive(fps) || codec.isBlank() || codec.length() > 100
                    || stream.path("disposition").path("attached_pic").asInt() == 1) throw invalid();
            return new VideoMetadata(duration, width, height, fps, codec);
        } catch (IOException | IllegalArgumentException | NullPointerException ex) { throw invalid(); }
    }
    private static double rate(String value) {
        try {
            String[] parts = value.split("/", -1);
            return parts.length == 2 ? Double.parseDouble(parts[0]) / Double.parseDouble(parts[1])
                    : parts.length == 1 ? Double.parseDouble(value) : Double.NaN;
        } catch (NumberFormatException ex) { return Double.NaN; }
    }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private ApiException invalid() { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_VIDEO", "File does not contain a supported valid video stream"); }
}
