package com.example.media.storage;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.UUID;

public interface StorageService {
    record StoredVideo(String key, long sizeBytes) {}
    StoredVideo saveVideo(UUID id, MultipartFile file);
    Path resolveVideo(String key);
    void deleteVideo(String key);
}
