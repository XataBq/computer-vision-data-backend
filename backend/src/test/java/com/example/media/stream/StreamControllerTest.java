package com.example.media.stream;

import com.example.media.camera.CameraStreamController;
import com.example.media.video.VideoSimulationController;
import com.example.media.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StreamControllerTest {
    private final SourceStreamService service = mock(SourceStreamService.class);
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CameraStreamController(service), new VideoSimulationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private String path(StreamType type) {
        return type == StreamType.CAMERA ? "/api/cameras/" + id + "/hls" : "/api/videos/" + id + "/simulation";
    }
    @ParameterizedTest @EnumSource(StreamType.class) void startEndpoint(StreamType type) throws Exception {
        StreamKey key = new StreamKey(type, id);
        when(service.start(type, id)).thenReturn(StreamResponse.running(key, Instant.now()));
        mvc.perform(post(path(type) + "/start")).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.status").value("RUNNING")).andExpect(jsonPath("$.streamUrl").value(key.streamUrl()));
    }
    @ParameterizedTest @EnumSource(StreamType.class) void stopEndpoint(StreamType type) throws Exception {
        when(service.stop(type, id)).thenReturn(StreamResponse.stopped(new StreamKey(type, id)));
        mvc.perform(post(path(type) + "/stop")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("STOPPED"));
    }
    @ParameterizedTest @EnumSource(StreamType.class) void statusEndpoint(StreamType type) throws Exception {
        when(service.status(type, id)).thenReturn(StreamResponse.stopped(new StreamKey(type, id)));
        mvc.perform(get(path(type) + "/status")).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(false));
    }
    @ParameterizedTest @EnumSource(StreamType.class) void startupFailureIs503(StreamType type) throws Exception {
        when(service.start(type, id)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STREAM_START_TIMEOUT", "Stream readiness timeout"));
        mvc.perform(post(path(type) + "/start")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("STREAM_START_TIMEOUT"));
    }
}
