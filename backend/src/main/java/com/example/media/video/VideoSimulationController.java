package com.example.media.video;

import com.example.media.stream.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/videos/{id}/simulation")
@Tag(name = "Video Simulations")
public class VideoSimulationController {
    private final SourceStreamService service;
    public VideoSimulationController(SourceStreamService service) { this.service = service; }
    @PostMapping("/start") @Operation(summary = "Start looped real-time HLS simulation and wait for readiness; idempotent")
    public StreamResponse start(@PathVariable UUID id) { return service.start(StreamType.VIDEO_SIMULATION, id); }
    @PostMapping("/stop") @Operation(summary = "Stop simulation and remove temporary stream files; idempotent")
    public StreamResponse stop(@PathVariable UUID id) { return service.stop(StreamType.VIDEO_SIMULATION, id); }
    @GetMapping("/status") @Operation(summary = "Get live video simulation status")
    public StreamResponse status(@PathVariable UUID id) { return service.status(StreamType.VIDEO_SIMULATION, id); }
}
