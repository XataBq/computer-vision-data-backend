package com.example.media.camera;

import com.example.media.camera.dto.*;
import com.example.media.common.SourceUrls;
import com.example.media.exception.ApiException;
import com.example.media.media.FfmpegService;
import com.example.media.stream.*;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class CameraService {
    private static final Logger log = LoggerFactory.getLogger(CameraService.class);
    private final CameraRepository repository;
    private final FfmpegService ffmpeg;
    private final ProcessManager processes;
    private final TransactionTemplate transactions;
    public CameraService(CameraRepository repository, FfmpegService ffmpeg, ProcessManager processes, TransactionTemplate transactions) {
        this.repository = repository; this.ffmpeg = ffmpeg; this.processes = processes; this.transactions = transactions;
    }
    @Transactional
    public CameraResponse create(CreateCameraRequest request) {
        SourceUrls.validate(request.sourceType(), request.sourceUrl());
        Camera camera = repository.saveAndFlush(new Camera(request.name().trim(), request.sourceType(), request.sourceUrl()));
        log.info("Camera created: id={}", camera.getId());
        return CameraResponse.from(camera);
    }
    public List<CameraResponse> list() { return repository.findAll().stream().map(CameraResponse::from).toList(); }
    public CameraResponse get(UUID id) { return CameraResponse.from(find(id)); }
    public void delete(UUID id) {
        StreamKey key = new StreamKey(StreamType.CAMERA, id);
        processes.withSourceLock(key, () -> {
            Camera camera = find(id);
            processes.stop(key);
            transactions.executeWithoutResult(status -> { repository.delete(camera); repository.flush(); });
            return null;
        });
    }
    public byte[] snapshot(UUID id) {
        Camera camera = find(id);
        if (camera.getStatus() == CameraStatus.DISABLED)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAMERA_DISABLED", "Camera is disabled");
        return ffmpeg.cameraSnapshot(camera.getSourceUrl(), camera.getSourceType());
    }
    private Camera find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAMERA_NOT_FOUND", "Camera not found"));
    }
}
