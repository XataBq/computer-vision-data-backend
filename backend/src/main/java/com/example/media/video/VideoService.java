package com.example.media.video;

import com.example.media.exception.ApiException;
import com.example.media.media.*;
import com.example.media.storage.StorageService;
import com.example.media.stream.*;
import com.example.media.video.dto.VideoResponse;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@Service
public class VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoService.class);
    private final VideoRepository repository;
    private final StorageService storage;
    private final FfprobeService probe;
    private final FfmpegService ffmpeg;
    private final TransactionTemplate transactions;
    private final ProcessManager processes;
    public VideoService(VideoRepository repository, StorageService storage, FfprobeService probe,
                        FfmpegService ffmpeg, TransactionTemplate transactions, ProcessManager processes) {
        this.repository = repository; this.storage = storage; this.probe = probe; this.ffmpeg = ffmpeg; this.transactions = transactions;
        this.processes = processes;
    }
    public VideoResponse upload(MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPLOAD", "Video file is empty");
        String filename = filename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".mp4"))
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_CONTAINER", "Upload an MP4 video");
        UUID id = UUID.randomUUID();
        StorageService.StoredVideo stored = storage.saveVideo(id, file);
        try {
            VideoMetadata metadata = probe.probe(storage.resolveVideo(stored.key()));
            // Commit before returning, so even a commit failure triggers file cleanup.
            VideoResponse response = transactions.execute(status -> VideoResponse.from(repository.saveAndFlush(
                    new Video(id, filename, stored.key(), stored.sizeBytes(), metadata))));
            log.info("Video uploaded: id={}, sizeBytes={}", id, stored.sizeBytes());
            return response;
        } catch (RuntimeException ex) {
            try { storage.deleteVideo(stored.key()); }
            catch (RuntimeException cleanup) { log.error("Upload cleanup failed: videoId={}", id); }
            throw ex;
        }
    }
    public List<VideoResponse> list() { return repository.findAll().stream().map(VideoResponse::from).toList(); }
    public VideoResponse get(UUID id) { return VideoResponse.from(find(id)); }
    public void delete(UUID id) {
        StreamKey key = new StreamKey(StreamType.VIDEO_SIMULATION, id);
        processes.withSourceLock(key, () -> {
            Video video = find(id);
            processes.stop(key);
            // Retain the row if storage deletion fails. A failed DB commit leaves a retryable row.
            transactions.executeWithoutResult(status -> {
                storage.deleteVideo(video.getStoragePath());
                repository.delete(video);
                repository.flush();
            });
            return null;
        });
    }
    public byte[] snapshot(UUID id, double second) {
        Video video = find(id);
        if (video.getStatus() != VideoStatus.READY) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_NOT_READY", "Video is not ready");
        if (!Double.isFinite(second) || second < 0 || second > video.getDurationSeconds())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SECOND", "second must be between 0 and video duration");
        // Duration denotes the end boundary, not the timestamp of an existing frame.
        double seek = second == video.getDurationSeconds() ? Math.max(0, second - 1.0 / video.getFps()) : second;
        return ffmpeg.videoSnapshot(storage.resolveVideo(video.getStoragePath()), seek);
    }
    private Video find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", "Video not found"));
    }
    private static String filename(String input) {
        if (input == null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILENAME", "A filename is required");
        String name = input.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(Character::isISOControl))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILENAME", "Invalid original filename");
        return name;
    }
}
