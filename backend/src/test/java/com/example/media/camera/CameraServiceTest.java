package com.example.media.camera;

import com.example.media.camera.dto.CreateCameraRequest;
import com.example.media.exception.ApiException;
import com.example.media.media.FfmpegService;
import com.example.media.stream.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraServiceTest {
    private final CameraRepository repository = mock(CameraRepository.class);
    private final FfmpegService ffmpeg = mock(FfmpegService.class);
    private final ProcessManager processes = mock(ProcessManager.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final CameraService service = new CameraService(repository, ffmpeg, processes, new TransactionTemplate(transactions));
    @Test void createMasksCredentials() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(new CreateCameraRequest("Camera", CameraSourceType.RTSP, "rtsp://user:secret@host/live"));
        assertThat(result.sourceUrl()).doesNotContain("secret", "user").contains("***:***");
        assertThat(result.status()).isEqualTo(CameraStatus.ACTIVE);
    }
    @Test void missingCameraIs404() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.status().value()).isEqualTo(404));
    }
    @Test void snapshotDelegatesOriginalSource() {
        UUID id = UUID.randomUUID();
        String url = "rtsp://user:secret@host/live";
        when(repository.findById(id)).thenReturn(Optional.of(new Camera("Camera", CameraSourceType.RTSP, url)));
        when(ffmpeg.cameraSnapshot(url, CameraSourceType.RTSP)).thenReturn(new byte[]{1, 2});
        assertThat(service.snapshot(id)).containsExactly(1, 2);
    }
    @Test void deletesOnlyAfterStreamStops() {
        UUID id = UUID.randomUUID();
        Camera camera = new Camera("Camera", CameraSourceType.RTSP, "rtsp://host/live");
        when(repository.findById(id)).thenReturn(Optional.of(camera));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(processes.withSourceLock(any(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(1)).get());
        service.delete(id);
        var order = inOrder(processes, repository, transactions);
        order.verify(processes).stop(new StreamKey(StreamType.CAMERA, id));
        order.verify(repository).delete(camera);
        order.verify(transactions).commit(any());
    }
}
