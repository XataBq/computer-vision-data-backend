package com.example.media.video;

import com.example.media.video.dto.VideoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/videos")
@Tag(name = "Videos")
public class VideoController {
    private final VideoService service;
    public VideoController(VideoService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and validate an MP4 video")
    public ResponseEntity<VideoResponse> upload(@RequestPart("file") MultipartFile file) {
        VideoResponse video = service.upload(file);
        return ResponseEntity.created(URI.create("/api/videos/" + video.id())).body(video);
    }
    @GetMapping public List<VideoResponse> list() { return service.list(); }
    @GetMapping("/{id}") public VideoResponse get(@PathVariable UUID id) { return service.get(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
    @GetMapping(value = "/{id}/snapshot", produces = MediaType.IMAGE_JPEG_VALUE)
    @Operation(summary = "Get a JPEG frame at the requested second", tags = "Snapshots")
    public ResponseEntity<byte[]> snapshot(@PathVariable UUID id, @RequestParam double second) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(service.snapshot(id, second));
    }
}
