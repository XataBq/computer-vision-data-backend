package com.example.media.camera;

import com.example.media.stream.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/cameras/{id}/hls")
@Tag(name = "Camera Streams")
public class CameraStreamController {
    private final SourceStreamService service;
    public CameraStreamController(SourceStreamService service) { this.service = service; }
    @PostMapping("/start") @Operation(summary = "Start HLS and wait for the first ready segment; idempotent")
    public StreamResponse start(@PathVariable UUID id) { return service.start(StreamType.CAMERA, id); }
    @PostMapping("/stop") @Operation(summary = "Stop HLS and remove temporary stream files; idempotent")
    public StreamResponse stop(@PathVariable UUID id) { return service.stop(StreamType.CAMERA, id); }
    @GetMapping("/status") @Operation(summary = "Get live HLS process status")
    public StreamResponse status(@PathVariable UUID id) { return service.status(StreamType.CAMERA, id); }
}
