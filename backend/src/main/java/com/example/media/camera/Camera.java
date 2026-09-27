package com.example.media.camera;

import com.example.media.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "cameras")
public class Camera extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CameraSourceType sourceType;
    @Column(nullable = false, length = 4096)
    private String sourceUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CameraStatus status;

    protected Camera() {}
    public Camera(String name, CameraSourceType sourceType, String sourceUrl) {
        this.name = name; this.sourceType = sourceType; this.sourceUrl = sourceUrl;
        this.status = CameraStatus.ACTIVE;
    }
    public String getName() { return name; }
    public CameraSourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public CameraStatus getStatus() { return status; }
}
