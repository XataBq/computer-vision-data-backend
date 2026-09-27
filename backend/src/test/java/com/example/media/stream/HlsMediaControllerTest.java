package com.example.media.stream;

import com.example.media.config.StorageProperties;
import com.example.media.exception.GlobalExceptionHandler;
import com.example.media.storage.HlsStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HlsMediaControllerTest {
    @TempDir Path root;
    @Test void servesPlaylistAndSegmentsWithCorrectTypesAndNoCache() throws Exception {
        var storage = new HlsStorage(new StorageProperties(root));
        var mvc = MockMvcBuilders.standaloneSetup(new HlsMediaController(storage)).setControllerAdvice(new GlobalExceptionHandler()).build();
        for (StreamType type : StreamType.values()) {
            var key = new StreamKey(type, UUID.randomUUID());
            Path directory = storage.prepare(key);
            Files.writeString(directory.resolve("index.m3u8"), "#EXTM3U\n#EXTINF:2,\nsegment_000001.ts\n");
            Files.write(directory.resolve("segment_000001.ts"), new byte[]{0x47, 1, 2});
            mvc.perform(get(key.streamUrl())).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.apple.mpegurl"))
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get(key.streamUrl().replace("index.m3u8", "segment_000001.ts")))
                    .andExpect(status().isOk()).andExpect(content().contentType("video/mp2t")).andExpect(content().bytes(new byte[]{0x47, 1, 2}));
            mvc.perform(get(key.streamUrl().replace("index.m3u8", "segment_000001.ts.tmp"))).andExpect(status().isNotFound());
            storage.cleanup(key);
            mvc.perform(get(key.streamUrl())).andExpect(status().isNotFound());
        }
    }
}
