package com.example.media.camera.dto;

import com.example.media.camera.*;
import com.example.media.common.SourceUrls;
import java.time.Instant;
import java.util.UUID;

public record CameraResponse(UUID id, String name, CameraSourceType sourceType, String sourceUrl,
                             CameraStatus status, Instant createdAt, Instant updatedAt) {
    public static CameraResponse from(Camera camera) {
        return new CameraResponse(camera.getId(), camera.getName(), camera.getSourceType(),
                SourceUrls.sanitize(camera.getSourceUrl()), camera.getStatus(), camera.getCreatedAt(), camera.getUpdatedAt());
    }
}
