package com.example.media.stream;

import com.example.media.config.HlsProperties;
import com.example.media.exception.ApiException;
import com.example.media.media.StreamingProcessLauncher;
import com.example.media.storage.HlsStorage;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessManagerTest {
    private final StreamingProcessLauncher launcher = mock(StreamingProcessLauncher.class);
    private final HlsStorage storage = mock(HlsStorage.class);
    private final HlsProperties properties = new HlsProperties(2, 5, Duration.ofMillis(150), Duration.ofMillis(50), 2);
    private final ProcessManager manager = new ProcessManager(launcher, storage, properties);
    private final StreamKey key = new StreamKey(StreamType.CAMERA, UUID.randomUUID());
    private final Path directory = Path.of("output");

    private static class Child {
        final Process process = mock(Process.class);
        final AtomicBoolean alive = new AtomicBoolean(true);
        final CompletableFuture<Process> exit = new CompletableFuture<>();
        Child(boolean graceful) throws Exception {
            when(process.isAlive()).thenAnswer(i -> alive.get());
            when(process.onExit()).thenReturn(exit);
            when(process.descendants()).thenAnswer(i -> Stream.empty());
            when(process.waitFor(anyLong(), any())).thenAnswer(i -> !alive.get());
            doAnswer(i -> { if (graceful) alive.set(false); return null; }).when(process).destroy();
            when(process.destroyForcibly()).thenAnswer(i -> { alive.set(false); return process; });
        }
        void exit() { alive.set(false); exit.complete(process); }
    }
    @BeforeEach void setup() {
        when(storage.prepare(any())).thenReturn(directory);
        when(storage.ready(any())).thenReturn(true);
    }
    @AfterEach void cleanup() { manager.shutdown(); }
    private Child launch() throws Exception {
        Child child = new Child(true);
        when(launcher.start(any())).thenReturn(child.process);
        return child;
    }
    private StreamResponse start() { return manager.start(key, output -> List.of("ffmpeg", output.toString())); }

    @Test void startsAndReturnsReadySession() throws Exception {
        launch();
        var response = start();
        assertThat(response.running()).isTrue();
        assertThat(response.streamUrl()).isEqualTo(key.streamUrl());
        assertThat(response.startedAt()).isNotNull();
        assertThat(manager.status(key)).isEqualTo(response);
    }
    @Test void duplicateStartDoesNotLaunchOrClearOutputAgain() throws Exception {
        launch();
        var first = start();
        assertThat(start()).isEqualTo(first);
        verify(launcher, times(1)).start(any());
        verify(storage, times(1)).prepare(key);
    }
    @Test void concurrentStartsCreateOnlyOneProcess() throws Exception {
        launch();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(this::start);
            var second = pool.submit(this::start);
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(second.get(2, TimeUnit.SECONDS));
        }
        verify(launcher, times(1)).start(any());
    }
    @Test void stopCleansProcessAndFiles() throws Exception {
        Child child = launch(); start();
        assertThat(manager.stop(key).running()).isFalse();
        assertThat(child.alive).isFalse();
        verify(child.process).destroy();
        verify(storage).cleanup(key);
        assertThat(manager.status(key).status()).isEqualTo(StreamResponse.Status.STOPPED);
    }
    @Test void stopAlreadyStoppedIsIdempotent() {
        assertThat(manager.stop(key)).isEqualTo(manager.stop(key));
        verifyNoInteractions(launcher);
    }
    @Test void escalatesToForceAfterGracePeriod() throws Exception {
        Child child = new Child(false);
        when(launcher.start(any())).thenReturn(child.process);
        start(); manager.stop(key);
        verify(child.process).destroyForcibly();
        assertThat(child.alive).isFalse();
    }
    @Test void unexpectedExitRemovesSessionAndOutput() throws Exception {
        Child child = launch(); start(); child.exit();
        assertThat(manager.status(key).running()).isFalse();
        verify(storage).cleanup(key);
    }
    @Test void lateOldExitDoesNotRemoveReplacement() throws Exception {
        Child first = launch(); start(); first.alive.set(false);
        Child second = new Child(true);
        when(launcher.start(any())).thenReturn(second.process);
        start(); first.exit.complete(first.process);
        assertThat(manager.status(key).running()).isTrue();
        verify(storage, never()).cleanup(key);
    }
    @Test void readinessTimeoutStopsAndCleans() throws Exception {
        Child child = launch();
        when(storage.ready(key)).thenReturn(false);
        assertThatThrownBy(this::start).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STREAM_START_TIMEOUT"));
        assertThat(child.alive).isFalse();
        assertThat(manager.status(key).running()).isFalse();
        verify(storage).cleanup(key);
    }
    @Test void earlyExitIs503EvenIfPlaylistExists() throws Exception {
        Child child = launch(); child.alive.set(false);
        assertThatThrownBy(this::start).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(503));
        assertThat(manager.status(key).running()).isFalse();
    }
    @Test void launchFailureDoesNotReserveSession() throws Exception {
        when(launcher.start(any())).thenThrow(new IOException("secret command details"));
        assertThatThrownBy(this::start).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getMessage()).doesNotContain("secret"));
        assertThat(manager.status(key).running()).isFalse();
        verify(storage).cleanup(key);
    }
    @Test void capacityRejectsAdditionalStream() throws Exception {
        launch(); start();
        manager.start(new StreamKey(StreamType.CAMERA, UUID.randomUUID()), p -> List.of("ffmpeg"));
        assertThatThrownBy(() -> manager.start(new StreamKey(StreamType.CAMERA, UUID.randomUUID()), p -> List.of("ffmpeg")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STREAM_CAPACITY_REACHED"));
        verify(launcher, times(2)).start(any());
    }
    @Test void shutdownStopsAllAndRejectsNewStarts() throws Exception {
        Child child = launch(); start();
        manager.shutdown();
        assertThat(child.alive).isFalse();
        assertThat(manager.status(key).running()).isFalse();
        assertThatThrownBy(this::start).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STREAM_SHUTTING_DOWN"));
    }
}
