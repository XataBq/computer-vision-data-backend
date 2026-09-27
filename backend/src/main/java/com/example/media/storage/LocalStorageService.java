package com.example.media.storage;

import com.example.media.config.StorageProperties;
import com.example.media.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {
    private final Path videos;
    public LocalStorageService(StorageProperties properties) {
        videos = properties.root().toAbsolutePath().normalize().resolve("uploads/videos");
        try { Files.createDirectories(videos); } catch (IOException ex) { throw failure(); }
    }
    @Override public StoredVideo saveVideo(UUID id, MultipartFile file) {
        String key = id + ".mp4";
        Path destination = resolveVideo(key);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(videos, "upload-", ".tmp");
            long size;
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) { size = input.transferTo(output); }
            Files.move(temporary, destination);
            return new StoredVideo(key, size);
        } catch (IOException ex) { throw failure(); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
    @Override public Path resolveVideo(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.mp4")) throw failure();
        Path result = videos.resolve(key).normalize();
        if (!result.startsWith(videos) || Files.isSymbolicLink(result)) throw failure();
        return result;
    }
    @Override public void deleteVideo(String key) {
        try { Files.deleteIfExists(resolveVideo(key)); } catch (IOException ex) { throw failure(); }
    }
    private static ApiException failure() { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "Cannot access video storage"); }
}
