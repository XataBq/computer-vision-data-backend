package com.example.media.stream;

import com.example.media.camera.*;
import com.example.media.video.*;
import com.example.media.storage.StorageService;
import com.example.media.media.HlsCommandFactory;
import com.example.media.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class SourceStreamService {
    private final CameraRepository cameras;
    private final VideoRepository videos;
    private final StorageService storage;
    private final HlsCommandFactory commands;
    private final ProcessManager processes;
    public SourceStreamService(CameraRepository cameras, VideoRepository videos, StorageService storage,
                               HlsCommandFactory commands, ProcessManager processes) {
        this.cameras = cameras; this.videos = videos; this.storage = storage; this.commands = commands; this.processes = processes;
    }
    public StreamResponse start(StreamType type, UUID id) {
        StreamKey key = new StreamKey(type, id);
        return processes.withSourceLock(key, () -> {
            if (type == StreamType.CAMERA) {
                Camera camera = camera(id);
                if (camera.getStatus() == CameraStatus.DISABLED)
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAMERA_DISABLED", "Camera is disabled");
                return processes.start(key, output -> commands.camera(camera.getSourceUrl(), camera.getSourceType(), output));
            }
            Video video = video(id);
            if (video.getStatus() != VideoStatus.READY)
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_NOT_READY", "Video is not ready");
            return processes.start(key, output -> commands.simulation(storage.resolveVideo(video.getStoragePath()), output));
        });
    }
    public StreamResponse stop(StreamType type, UUID id) {
        StreamKey key = new StreamKey(type, id);
        return processes.withSourceLock(key, () -> { requireSource(type, id); return processes.stop(key); });
    }
    public StreamResponse status(StreamType type, UUID id) {
        StreamKey key = new StreamKey(type, id);
        return processes.withSourceLock(key, () -> { requireSource(type, id); return processes.status(key); });
    }
    private void requireSource(StreamType type, UUID id) { if (type == StreamType.CAMERA) camera(id); else video(id); }
    private Camera camera(UUID id) {
        return cameras.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAMERA_NOT_FOUND", "Camera not found"));
    }
    private Video video(UUID id) {
        return videos.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", "Video not found"));
    }
}
