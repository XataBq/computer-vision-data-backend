package com.example.media.media;

import com.example.media.camera.CameraSourceType;
import com.example.media.config.*;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.*;

@Component
public class HlsCommandFactory {
    private final MediaProperties media;
    private final HlsProperties hls;
    private final MediaInputArguments inputs;
    public HlsCommandFactory(MediaProperties media, HlsProperties hls, MediaInputArguments inputs) {
        this.media = media; this.hls = hls; this.inputs = inputs;
    }
    public List<String> camera(String url, CameraSourceType type, Path output) {
        return command(inputs.camera(url, type), output);
    }
    public List<String> simulation(Path video, Path output) {
        List<String> args = new ArrayList<>(List.of("-re", "-stream_loop", "-1"));
        args.addAll(inputs.file(video));
        return command(args, output);
    }
    private List<String> command(List<String> input, Path output) {
        List<String> args = new ArrayList<>(List.of(media.ffmpegPath(), "-hide_banner", "-loglevel", "warning", "-nostdin", "-y"));
        args.addAll(input);
        args.addAll(List.of("-map", "0:v:0", "-an", "-c:v", "libx264", "-preset", "veryfast",
                // Closed GOPs and timed keyframes allow independent segments without fixing source FPS.
                "-flags", "+cgop", "-force_key_frames", "expr:gte(t,n_forced*" + hls.segmentDuration() + ")",
                "-f", "hls", "-hls_segment_type", "mpegts", "-hls_time", Integer.toString(hls.segmentDuration()),
                "-hls_list_size", Integer.toString(hls.playlistSize()), "-hls_flags", "delete_segments+temp_file+independent_segments",
                "-hls_segment_filename", output.resolve("segment_%06d.ts").toString(), output.resolve("index.m3u8").toString()));
        return List.copyOf(args);
    }
}
