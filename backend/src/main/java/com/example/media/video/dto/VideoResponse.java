package com.example.media.video.dto;

import com.example.media.video.*;
import java.time.Instant;
import java.util.UUID;

public record VideoResponse(UUID id, String originalFilename, String mimeType, long sizeBytes,
                            double durationSeconds, int width, int height, double fps, String codec,
                            VideoStatus status, Instant createdAt, Instant updatedAt) {
    public static VideoResponse from(Video v) {
        return new VideoResponse(v.getId(), v.getOriginalFilename(), v.getMimeType(), v.getSizeBytes(),
                v.getDurationSeconds(), v.getWidth(), v.getHeight(), v.getFps(), v.getCodec(), v.getStatus(), v.getCreatedAt(), v.getUpdatedAt());
    }
}
