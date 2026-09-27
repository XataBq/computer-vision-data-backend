package com.example.media.media;

import com.example.media.camera.CameraSourceType;
import com.example.media.common.SourceUrls;
import com.example.media.config.RtspProperties;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.*;

/** Shared input policy for snapshots and HLS. No command or authenticated URL is logged. */
@Component
public class MediaInputArguments {
    private final RtspProperties rtsp;
    public MediaInputArguments(RtspProperties rtsp) { this.rtsp = rtsp; }
    public List<String> camera(String url, CameraSourceType type) {
        SourceUrls.validate(type, url);
        List<String> args = new ArrayList<>(List.of("-protocol_whitelist",
                type == CameraSourceType.RTSP ? "rtsp,tcp,udp,rtp" : "http,https,tcp,tls,crypto"));
        if (type == CameraSourceType.RTSP) args.addAll(List.of("-rtsp_transport", rtsp.transport()));
        args.addAll(List.of("-i", url));
        return List.copyOf(args);
    }
    public List<String> file(Path path) {
        return List.of("-protocol_whitelist", "file", "-i", path.toAbsolutePath().toString());
    }
}
