package com.example.media.video;

import com.example.media.exception.ApiException;
import com.example.media.media.*;
import com.example.media.storage.StorageService;
import com.example.media.stream.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoServiceTest {
    private final VideoRepository repository = mock(VideoRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final FfprobeService probe = mock(FfprobeService.class);
    private final FfmpegService ffmpeg = mock(FfmpegService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ProcessManager processes = mock(ProcessManager.class);
    private final VideoService service = new VideoService(repository, storage, probe, ffmpeg, new TransactionTemplate(manager), processes);
    private final VideoMetadata metadata = new VideoMetadata(60, 1920, 1080, 30, "h264");
    private MockMultipartFile uploadFixture() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(storage.saveVideo(any(), any())).thenReturn(new StorageService.StoredVideo("key.mp4", 12));
        when(storage.resolveVideo("key.mp4")).thenReturn(Path.of("video.mp4"));
        return new MockMultipartFile("file", "../sample.mp4", "video/mp4", new byte[]{1});
    }
    @Test void uploadSavesMetadataAndSanitizesName() {
        var file = uploadFixture();
        when(probe.probe(any())).thenReturn(metadata);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.upload(file);
        assertThat(result.originalFilename()).isEqualTo("sample.mp4");
        assertThat(result.durationSeconds()).isEqualTo(60);
        verify(manager).commit(any());
    }
    @Test void invalidVideoDeletesStoredFileAndDoesNotSave() {
        var file = uploadFixture();
        when(probe.probe(any())).thenThrow(new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_VIDEO", "Invalid video"));
        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(ApiException.class);
        verify(storage).deleteVideo("key.mp4");
        verifyNoInteractions(repository);
    }
    @Test void failedCommitAlsoCleansUpUpload() {
        var file = uploadFixture();
        when(probe.probe(any())).thenReturn(metadata);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(TransactionSystemException.class);
        verify(storage).deleteVideo("key.mp4");
    }
    @Test void validatesSecondBeforeInvokingFfmpeg() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new Video(id, "video.mp4", "key.mp4", 12, metadata)));
        for (double second : new double[]{-1, 61, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThatThrownBy(() -> service.snapshot(id, second)).isInstanceOf(ApiException.class);
        }
        verifyNoInteractions(ffmpeg);
    }
    @Test void durationBoundarySeeksLastFrame() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(new Video(id, "video.mp4", "key.mp4", 12, metadata)));
        when(storage.resolveVideo("key.mp4")).thenReturn(Path.of("video.mp4"));
        service.snapshot(id, 60);
        verify(ffmpeg).videoSnapshot(Path.of("video.mp4"), 60 - 1.0 / 30);
    }
    @Test void deletionRemovesFileAndRow() {
        when(processes.withSourceLock(any(), any())).thenAnswer(i -> ((Supplier<?>) i.getArgument(1)).get());
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID id = UUID.randomUUID();
        Video video = new Video(id, "video.mp4", "key.mp4", 12, metadata);
        when(repository.findById(id)).thenReturn(Optional.of(video));
        service.delete(id);
        verify(storage).deleteVideo("key.mp4");
        verify(repository).delete(video);
        var order = inOrder(processes, storage, repository);
        order.verify(processes).stop(new StreamKey(StreamType.VIDEO_SIMULATION, id));
        order.verify(storage).deleteVideo("key.mp4");
        order.verify(repository).delete(video);
    }
}
