package com.example.media.stream;

import com.example.media.storage.HlsStorage;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@Tag(name = "HLS Media")
public class HlsMediaController {
    private final HlsStorage storage;
    public HlsMediaController(HlsStorage storage) { this.storage = storage; }
    @GetMapping("/streams/cameras/{id}/{filename}")
    public ResponseEntity<Resource> camera(@PathVariable UUID id, @PathVariable String filename) {
        return media(StreamType.CAMERA, id, filename);
    }
    @GetMapping("/streams/videos/{id}/{filename}")
    public ResponseEntity<Resource> video(@PathVariable UUID id, @PathVariable String filename) {
        return media(StreamType.VIDEO_SIMULATION, id, filename);
    }
    private ResponseEntity<Resource> media(StreamType type, UUID id, String filename) {
        HlsStorage.MediaFile file = storage.open(new StreamKey(type, id), filename);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .contentLength(file.length()).body(new InputStreamResource(file.input()));
    }
}
