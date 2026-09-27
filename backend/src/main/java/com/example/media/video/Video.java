package com.example.media.video;

import com.example.media.common.BaseEntity;
import com.example.media.media.VideoMetadata;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "videos")
public class Video extends BaseEntity {
    @Column(nullable = false, length = 255) private String originalFilename;
    @Column(nullable = false, unique = true, length = 255) private String storagePath;
    @Column(nullable = false, length = 100) private String mimeType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private double durationSeconds;
    @Column(nullable = false) private int width;
    @Column(nullable = false) private int height;
    @Column(nullable = false) private double fps;
    @Column(nullable = false, length = 100) private String codec;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private VideoStatus status;

    protected Video() {}
    public Video(UUID id, String filename, String storagePath, long size, VideoMetadata metadata) {
        this.id = id; this.originalFilename = filename; this.storagePath = storagePath;
        this.mimeType = "video/mp4"; this.sizeBytes = size;
        this.durationSeconds = metadata.durationSeconds(); this.width = metadata.width();
        this.height = metadata.height(); this.fps = metadata.fps(); this.codec = metadata.codec();
        this.status = VideoStatus.READY;
    }
    public String getOriginalFilename() { return originalFilename; }
    public String getStoragePath() { return storagePath; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public double getDurationSeconds() { return durationSeconds; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public double getFps() { return fps; }
    public String getCodec() { return codec; }
    public VideoStatus getStatus() { return status; }
}
