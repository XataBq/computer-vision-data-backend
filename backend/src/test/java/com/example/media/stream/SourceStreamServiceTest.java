package com.example.media.stream;

import com.example.media.camera.*;
import com.example.media.video.*;
import com.example.media.media.*;
import com.example.media.storage.StorageService;
import com.example.media.exception.ApiException;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SourceStreamServiceTest {
    private final CameraRepository cameras = mock(CameraRepository.class);
    private final VideoRepository videos = mock(VideoRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final HlsCommandFactory commands = mock(HlsCommandFactory.class);
    private final ProcessManager manager = mock(ProcessManager.class);
    private final SourceStreamService service = new SourceStreamService(cameras, videos, storage, commands, manager);
    private final UUID id = UUID.randomUUID();
    @BeforeEach void locks() {
        when(manager.withSourceLock(any(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(1)).get());
    }
    @Test void missingSourcesReturn404BeforeLaunching() {
        for (StreamType type : StreamType.values()) {
            assertThatThrownBy(() -> service.start(type, id)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
            assertThatThrownBy(() -> service.stop(type, id)).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> service.status(type, id)).isInstanceOf(ApiException.class);
        }
        verify(manager, never()).start(any(), any());
    }
    @Test void cameraPassesOriginalSourceOnlyToCommandFactory() {
        String url = "rtsp://user:secret@host/live";
        when(cameras.findById(id)).thenReturn(Optional.of(new Camera("Camera", CameraSourceType.RTSP, url)));
        executeCommandFactory();
        service.start(StreamType.CAMERA, id);
        verify(commands).camera(url, CameraSourceType.RTSP, Path.of("output"));
    }
    @Test void videoUsesInternalStoragePath() {
        when(videos.findById(id)).thenReturn(Optional.of(new Video(id, "../../user.mp4", "internal.mp4", 100, new VideoMetadata(2, 160, 120, 10, "h264"))));
        when(storage.resolveVideo("internal.mp4")).thenReturn(Path.of("internal.mp4"));
        executeCommandFactory();
        service.start(StreamType.VIDEO_SIMULATION, id);
        verify(commands).simulation(Path.of("internal.mp4"), Path.of("output"));
    }
    @Test void disabledCameraDoesNotStart() {
        Camera camera = mock(Camera.class);
        when(camera.getStatus()).thenReturn(CameraStatus.DISABLED);
        when(cameras.findById(id)).thenReturn(Optional.of(camera));
        assertThatThrownBy(() -> service.start(StreamType.CAMERA, id)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CAMERA_DISABLED"));
        verify(manager, never()).start(any(), any());
    }
    @Test void failedVideoDoesNotStart() {
        Video video = mock(Video.class);
        when(video.getStatus()).thenReturn(VideoStatus.FAILED);
        when(videos.findById(id)).thenReturn(Optional.of(video));
        assertThatThrownBy(() -> service.start(StreamType.VIDEO_SIMULATION, id)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VIDEO_NOT_READY"));
        verify(manager, never()).start(any(), any());
    }
    @SuppressWarnings("unchecked")
    private void executeCommandFactory() {
        when(manager.start(any(), any())).thenAnswer(i -> {
            ((Function<Path, List<String>>) i.getArgument(1)).apply(Path.of("output"));
            return StreamResponse.stopped(i.getArgument(0));
        });
    }
}
