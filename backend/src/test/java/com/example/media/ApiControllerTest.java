package com.example.media;

import com.example.media.camera.*;
import com.example.media.camera.dto.CameraResponse;
import com.example.media.exception.*;
import com.example.media.video.*;
import com.example.media.video.dto.VideoResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiControllerTest {
    private final CameraService cameras = mock(CameraService.class);
    private final VideoService videos = mock(VideoService.class);
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CameraController(cameras), new VideoController(videos))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    private CameraResponse camera() { return new CameraResponse(id, "Camera", CameraSourceType.RTSP, "rtsp://host/live", CameraStatus.ACTIVE, Instant.now(), Instant.now()); }
    @Test void createsAndGetsCamera() throws Exception {
        when(cameras.create(any())).thenReturn(camera());
        when(cameras.get(id)).thenReturn(camera());
        mvc.perform(post("/api/cameras").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Camera","sourceType":"RTSP","sourceUrl":"rtsp://host/live"}
                    """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/cameras/" + id));
        mvc.perform(get("/api/cameras/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
    }
    @Test void validatesRequestWithoutEchoingSecrets() throws Exception {
        mvc.perform(post("/api/cameras").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","sourceType":"RTSP","sourceUrl":"rtsp://secret:secret@host/live"}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        verifyNoInteractions(cameras);
    }
    @Test void returnsJpegSnapshots() throws Exception {
        byte[] jpeg = {(byte) 255, (byte) 216, (byte) 255, (byte) 217};
        when(cameras.snapshot(id)).thenReturn(jpeg);
        when(videos.snapshot(id, 30)).thenReturn(jpeg);
        mvc.perform(get("/api/cameras/{id}/snapshot", id)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(content().bytes(jpeg));
        mvc.perform(get("/api/videos/{id}/snapshot", id).param("second", "30")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG)).andExpect(content().bytes(jpeg));
    }
    @Test void uploadsVideo() throws Exception {
        when(videos.upload(any())).thenReturn(new VideoResponse(id, "video.mp4", "video/mp4", 100, 60, 1920, 1080, 30,
                "h264", VideoStatus.READY, Instant.now(), Instant.now()));
        mvc.perform(multipart("/api/videos").file(new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[]{1})))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.durationSeconds").value(60));
    }
    @Test void snapshotErrorsAreJson() throws Exception {
        when(cameras.snapshot(id)).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SNAPSHOT_TIMEOUT", "Snapshot unavailable"));
        mvc.perform(get("/api/cameras/{id}/snapshot", id)).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.code").value("SNAPSHOT_TIMEOUT"));
    }
    @Test void handlesMissingAndMalformedParameters() throws Exception {
        mvc.perform(get("/api/videos/{id}/snapshot", id)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/cameras/not-a-uuid")).andExpect(status().isBadRequest());
        when(cameras.get(id)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "CAMERA_NOT_FOUND", "Camera not found"));
        mvc.perform(get("/api/cameras/{id}", id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.path").value("/api/cameras/" + id));
    }
    @Test void unexpectedErrorDoesNotExposeCause() throws Exception {
        when(cameras.get(id)).thenThrow(new IllegalStateException("rtsp://user:secret@host/live"));
        mvc.perform(get("/api/cameras/{id}", id)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
