package com.example.media.storage;

import com.example.media.config.StorageProperties;
import com.example.media.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HlsPlaylistReplacementTest {
    @TempDir Path root;
    @Test void responseLengthAndBodyReferToSamePlaylistGeneration() throws Exception {
        var storage = new HlsStorage(new StorageProperties(root));
        var key = new StreamKey(StreamType.CAMERA, UUID.randomUUID());
        Path directory = storage.prepare(key);
        Path playlist = directory.resolve("index.m3u8");
        String old = "#EXTM3U\n";
        Files.writeString(playlist, old);
        var response = storage.open(key, "index.m3u8");
        try (var input = response.input()) {
            Path replacement = directory.resolve("index.m3u8.tmp");
            Files.writeString(replacement, "#EXTM3U\n#EXTINF:2,\nsegment_000001.ts\n");
            Files.move(replacement, playlist, StandardCopyOption.REPLACE_EXISTING);
            byte[] body = input.readAllBytes();
            assertThat(body.length).isEqualTo(response.length());
            assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(old);
        }
    }
}
