package com.example.media.camera;

import com.example.media.camera.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/cameras")
@Tag(name = "Cameras")
public class CameraController {
    private final CameraService service;
    public CameraController(CameraService service) { this.service = service; }
    @PostMapping
    @Operation(summary = "Create an RTSP or HLS camera")
    public ResponseEntity<CameraResponse> create(@Valid @RequestBody CreateCameraRequest request) {
        CameraResponse camera = service.create(request);
        return ResponseEntity.created(URI.create("/api/cameras/" + camera.id())).body(camera);
    }
    @GetMapping public List<CameraResponse> list() { return service.list(); }
    @GetMapping("/{id}") public CameraResponse get(@PathVariable UUID id) { return service.get(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
    @GetMapping(value = "/{id}/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Get a current JPEG frame at source resolution", tags = "Snapshots")
    public ResponseEntity<byte[]> snapshot(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(service.snapshot(id));
    }
}
