package com.example.media.common;

import com.example.media.camera.CameraSourceType;
import com.example.media.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class SourceUrlsTest {
    @Test void acceptsSupportedSources() {
        SourceUrls.validate(CameraSourceType.RTSP, "rtsp://user:secret@localhost:554/live");
        SourceUrls.validate(CameraSourceType.HLS, "https://example.com/live.m3u8");
    }
    @ParameterizedTest @ValueSource(strings = {"file:///etc/passwd", "ftp://host/video", "http://host/live", "rtsp:///live", "rtsp://host:70000/live"})
    void rejectsInvalidRtsp(String url) {
        assertThatThrownBy(() -> SourceUrls.validate(CameraSourceType.RTSP, url)).isInstanceOf(ApiException.class);
    }
    @Test void masksCredentialsAndTokens() {
        assertThat(SourceUrls.sanitize("rtsp://user:secret@host:554/live?token=secret"))
                .isEqualTo("rtsp://***:***@host:554/live?***");
    }
}
