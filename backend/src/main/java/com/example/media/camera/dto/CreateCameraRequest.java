package com.example.media.camera.dto;

import com.example.media.camera.CameraSourceType;
import jakarta.validation.constraints.*;

public record CreateCameraRequest(@NotBlank @Size(max = 200) String name,
                                  @NotNull CameraSourceType sourceType,
                                  @NotBlank @Size(max = 4096) String sourceUrl) {
    @Override public String toString() { return "CreateCameraRequest[redacted]"; }
}
