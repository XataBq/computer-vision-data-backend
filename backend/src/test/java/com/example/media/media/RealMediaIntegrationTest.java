package com.example.media.media;

import com.example.media.camera.*;
import com.example.media.config.*;
import com.example.media.exception.*;
import com.example.media.storage.*;
import com.example.media.stream.*;
import com.example.media.video.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in, real binaries/files/HTTP HLS input; only DB repositories and transaction manager are mocked. */
@Tag("media-integration")
class RealMediaIntegrationTest {
    @TempDir Path root;
    @Test @Timeout(90)
    void uploadSnapshotsLoopedSimulationAndHlsCamera() throws Exception {
        String ffmpeg = System.getenv().getOrDefault("FFMPEG_PATH", "ffmpeg");
        String ffprobe = System.getenv().getOrDefault("FFPROBE_PATH", "ffprobe");
        MediaProperties media = new MediaProperties(ffmpeg, ffprobe, Duration.ofSeconds(20), Duration.ofSeconds(10), 4);
        ExternalProcessRunner runner = new ExternalProcessRunner(media);
        // An explicitly enabled profile fails if tools are missing; it never silently skips a broken setup.
        new MediaAvailability(runner, media).afterPropertiesSet();
        Path fixture = root.resolve("fixture.mp4");
        runner.execute(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-f", "lavfi", "-i", "testsrc=size=160x120:rate=10", "-f", "lavfi", "-i", "sine=frequency=440",
                "-t", "2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", fixture.toString()), Duration.ofSeconds(15));
        var inputs = new MediaInputArguments(new RtspProperties("tcp"));
        var snapshots = new FfmpegService(runner, media, inputs);
        var probe = new FfprobeService(runner, media, new ObjectMapper());
        var storage = new LocalStorageService(new StorageProperties(root));
        var hlsStorage = new HlsStorage(new StorageProperties(root));
        var hls = new HlsProperties(1, 3, Duration.ofSeconds(20), Duration.ofSeconds(2), 4);
        var commands = new HlsCommandFactory(media, hls, inputs);
        List<Process> children = new CopyOnWriteArrayList<>();
        StreamingProcessLauncher launcher = args -> {
            Process process = new ExternalStreamingProcessLauncher().start(args);
            children.add(process); return process;
        };
        var manager = new ProcessManager(launcher, hlsStorage, hls);
        var cameras = mock(CameraRepository.class);
        var videos = mock(VideoRepository.class);
        Map<UUID, Video> rows = new ConcurrentHashMap<>();
        when(videos.saveAndFlush(any())).thenAnswer(i -> { Video v = i.getArgument(0); rows.put(v.getId(), v); return v; });
        when(videos.findById(any())).thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        doAnswer(i -> { rows.remove(((Video) i.getArgument(0)).getId()); return null; }).when(videos).delete(any(Video.class));
        var txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        var tx = new TransactionTemplate(txManager);
        var videoService = new VideoService(videos, storage, probe, snapshots, tx, manager);
        var cameraService = new CameraService(cameras, snapshots, manager, tx);
        var streams = new SourceStreamService(cameras, videos, storage, commands, manager);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VideoController(videoService), new CameraController(cameraService),
                        new VideoSimulationController(streams), new CameraStreamController(streams), new HlsMediaController(hlsStorage))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        HttpServer server = null;
        try {
            String response = mvc.perform(multipart("/api/videos").file(new MockMultipartFile("file", "clip.mp4", "video/mp4", Files.readAllBytes(fixture))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.width").value(160)).andReturn().getResponse().getContentAsString();
            UUID id = UUID.fromString(new ObjectMapper().readTree(response).path("id").asText());
            for (String second : List.of("0", "1")) {
                byte[] jpeg = mvc.perform(get("/api/videos/{id}/snapshot", id).param("second", second))
                        .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andReturn().getResponse().getContentAsByteArray();
                assertJpeg(jpeg);
            }
            StreamKey key = new StreamKey(StreamType.VIDEO_SIMULATION, id);
            mvc.perform(post("/api/videos/{id}/simulation/start", id)).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(true));
            mvc.perform(post("/api/videos/{id}/simulation/start", id)).andExpect(status().isOk());
            assertThat(children).hasSize(1);
            Path directory = hlsStorage.directory(key);
            // Beyond the two-second original: confirms looping and a sliding playlist, not one-shot conversion.
            await(() -> {
                try { return Files.readString(directory.resolve("index.m3u8")).contains("segment_000005.ts"); }
                catch (Exception ex) { return false; }
            }, Duration.ofSeconds(15));
            String playlist = mvc.perform(get(key.streamUrl())).andExpect(status().isOk())
                    .andExpect(content().contentType("application/vnd.apple.mpegurl")).andReturn().getResponse().getContentAsString();
            assertThat(playlist).doesNotContain("#EXT-X-ENDLIST");
            List<String> segmentNames = playlist.lines().filter(line -> line.endsWith(".ts")).toList();
            assertThat(segmentNames.size()).isBetween(1, 3);
            String segment = segmentNames.getLast();
            byte[] ts = mvc.perform(get(key.streamUrl().replace("index.m3u8", segment))).andExpect(status().isOk())
                    .andExpect(content().contentType("video/mp2t")).andReturn().getResponse().getContentAsByteArray();
            assertThat(ts[0]).isEqualTo((byte) 0x47);
            JsonNode segmentInfo = new ObjectMapper().readTree(runner.execute(List.of(ffprobe, "-v", "error", "-show_entries",
                    "stream=codec_name,codec_type", "-of", "json", directory.resolve(segment).toString()), Duration.ofSeconds(5)));
            assertThat(segmentInfo.path("streams").size()).isEqualTo(1);
            assertThat(segmentInfo.path("streams").path(0).path("codec_name").asText()).isEqualTo("h264");
            try (var files = Files.list(directory)) { assertThat(files.filter(p -> p.toString().endsWith(".ts")).count()).isLessThanOrEqualTo(7); }

            // Feed a local real-time HLS source into the real camera pipeline; no public camera needed.
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    String name = exchange.getRequestURI().getPath().substring(1);
                    var file = hlsStorage.open(key, name);
                    try (var input = file.input()) {
                        exchange.getResponseHeaders().set("Content-Type", file.contentType());
                        exchange.sendResponseHeaders(200, file.length());
                        input.transferTo(exchange.getResponseBody());
                    }
                } catch (ApiException ex) { exchange.sendResponseHeaders(ex.status().value(), -1); }
                finally { exchange.close(); }
            });
            server.start();
            UUID cameraId = UUID.randomUUID();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/index.m3u8";
            when(cameras.findById(cameraId)).thenReturn(Optional.of(new Camera("Fixture", CameraSourceType.HLS, url)));
            byte[] cameraJpeg = mvc.perform(get("/api/cameras/{id}/snapshot", cameraId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertJpeg(cameraJpeg);
            mvc.perform(post("/api/cameras/{id}/hls/start", cameraId)).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(true));
            mvc.perform(get(new StreamKey(StreamType.CAMERA, cameraId).streamUrl())).andExpect(status().isOk());
            mvc.perform(post("/api/cameras/{id}/hls/stop", cameraId)).andExpect(status().isOk()).andExpect(jsonPath("$.running").value(false));
            mvc.perform(post("/api/videos/{id}/simulation/stop", id)).andExpect(status().isOk());
            mvc.perform(post("/api/videos/{id}/simulation/stop", id)).andExpect(status().isOk());
            assertThat(directory).doesNotExist();
            assertThat(storage.resolveVideo(id + ".mp4")).exists();
            // Restart then delete proves stop-before-delete on a real running process.
            mvc.perform(post("/api/videos/{id}/simulation/start", id)).andExpect(status().isOk());
            mvc.perform(delete("/api/videos/{id}", id)).andExpect(status().isNoContent());
            assertThat(storage.resolveVideo(id + ".mp4")).doesNotExist();
            assertThat(directory).doesNotExist();
        } finally {
            manager.shutdown();
            if (server != null) server.stop(0);
        }
        assertThat(children).allMatch(process -> !process.isAlive());
    }
    private void assertJpeg(byte[] bytes) throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(160);
        assertThat(image.getHeight()).isEqualTo(120);
    }
    private void await(BooleanSupplier ready, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!ready.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(100);
        assertThat(ready.getAsBoolean()).as("real media became ready").isTrue();
    }
}
