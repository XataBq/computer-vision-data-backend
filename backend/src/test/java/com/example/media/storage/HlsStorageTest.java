package com.example.media.storage;

import com.example.media.config.StorageProperties;
import com.example.media.exception.ApiException;
import com.example.media.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HlsStorageTest {
    @TempDir Path root;
    private final StreamKey key = new StreamKey(StreamType.CAMERA, UUID.randomUUID());
    @Test void clearsOnlyRequestedSourceAndLeavesUploads() throws Exception {
        var storage = new HlsStorage(new StorageProperties(root));
        Path own = storage.prepare(key);
        Path other = storage.prepare(new StreamKey(StreamType.VIDEO_SIMULATION, key.sourceId()));
        Path upload = Files.createDirectories(root.resolve("uploads/videos")).resolve("original.mp4");
        Files.writeString(upload, "original");
        Files.writeString(own.resolve("index.m3u8"), "old");
        Files.writeString(other.resolve("index.m3u8"), "other");
        storage.prepare(key);
        assertThat(own.resolve("index.m3u8")).doesNotExist();
        assertThat(other.resolve("index.m3u8")).hasContent("other");
        assertThat(upload).hasContent("original");
    }
    @Test void readinessRequiresPlaylistAndNonemptySegment() throws Exception {
        var storage = new HlsStorage(new StorageProperties(root));
        Path directory = storage.prepare(key);
        Files.writeString(directory.resolve("index.m3u8"), "#EXTM3U\n#EXTINF:2,\nsegment_000001.ts\n");
        assertThat(storage.ready(key)).isFalse();
        Files.write(directory.resolve("segment_000001.ts"), new byte[]{0x47});
        assertThat(storage.ready(key)).isTrue();
        storage.cleanup(key);
        assertThat(storage.ready(key)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"../secret", "..\\secret", "/etc/passwd", "index.m3u8.tmp", "segment_000001.ts.tmp", "original.mp4"})
    void rejectsUnpublishedNames(String filename) {
        var storage = new HlsStorage(new StorageProperties(root));
        assertThatThrownBy(() -> storage.open(key, filename)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
    }
}
