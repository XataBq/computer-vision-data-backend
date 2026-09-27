package com.example.media.common;

import com.example.media.camera.CameraSourceType;
import com.example.media.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.net.URISyntaxException;

public final class SourceUrls {
    private SourceUrls() {}
    public static void validate(CameraSourceType type, String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            boolean supported = type == CameraSourceType.RTSP ? "rtsp".equalsIgnoreCase(scheme)
                    : type == CameraSourceType.HLS && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
            if (!supported || uri.getHost() == null || uri.getFragment() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) throw new URISyntaxException("", "Invalid source");
        } catch (URISyntaxException | NullPointerException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_URL", "Source URL must match RTSP or HTTP(S) HLS source type and contain a host");
        }
    }
    public static String sanitize(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            String authority = uri.getRawAuthority();
            if (authority == null) return "[redacted]";
            int at = authority.lastIndexOf('@');
            if (at >= 0) authority = "***:***@" + authority.substring(at + 1);
            // Query strings frequently carry access tokens; do not expose them either.
            return uri.getScheme() + "://" + authority + (uri.getRawPath() == null ? "" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?***");
        } catch (URISyntaxException ex) { return "[redacted]"; }
    }
}
