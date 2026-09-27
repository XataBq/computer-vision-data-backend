package com.example.media.storage;

import com.example.media.config.StorageProperties;
import com.example.media.exception.ApiException;
import com.example.media.stream.StreamKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

@Component
public class HlsStorage {
    private final Path root;
    public HlsStorage(StorageProperties properties) {
        root = properties.root().toAbsolutePath().normalize().resolve("streams");
    }
    public Path prepare(StreamKey key) {
        cleanup(key);
        Path directory = directory(key);
        try { Files.createDirectories(directory); return directory; }
        catch (IOException ex) { throw storageError(); }
    }
    public void cleanup(StreamKey key) {
        Path directory = directory(key);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var files = Files.walk(directory)) {
            // Exact type/UUID subtree only; walk does not follow symlinks.
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        } catch (IOException ex) { throw storageError(); }
    }
    public Path directory(StreamKey key) {
        Path directory = root.resolve(key.type().directory()).resolve(key.sourceId().toString()).normalize();
        if (!directory.startsWith(root) || directory.equals(root)) throw storageError();
        rejectSymlinks(directory);
        return directory;
    }
    public boolean ready(StreamKey key) {
        Path directory = directory(key);
        Path playlist = directory.resolve("index.m3u8");
        try {
            if (!Files.isRegularFile(playlist, LinkOption.NOFOLLOW_LINKS) || Files.size(playlist) > 128 * 1024) return false;
            String text = Files.readString(playlist, StandardCharsets.UTF_8);
            if (!text.startsWith("#EXTM3U") || !text.contains("#EXTINF:")) return false;
            for (String line : text.lines().toList()) {
                if (line.matches("segment_[0-9]{6,}\\.ts")) {
                    Path segment = directory.resolve(line);
                    if (Files.isRegularFile(segment, LinkOption.NOFOLLOW_LINKS) && Files.size(segment) > 0) return true;
                }
            }
            return false;
        } catch (IOException ex) { return false; } // Atomic replacement/deletion may race a readiness read.
    }
    public record MediaFile(InputStream input, long length, String contentType) {}
    public MediaFile open(StreamKey key, String filename) {
        if (!filename.equals("index.m3u8") && !filename.matches("segment_[0-9]{6,}\\.ts")) throw notFound();
        Path file = directory(key).resolve(filename);
        rejectSymlinks(file);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile()) throw notFound();
            FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            try {
                // FFmpeg atomically replaces the playlist. Derive Content-Length from the same open
                // file handle as the response body, never from an earlier version's path attributes.
                return new MediaFile(Channels.newInputStream(channel), channel.size(),
                        filename.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
            } catch (IOException | RuntimeException ex) { channel.close(); throw ex; }
        } catch (NoSuchFileException ex) { throw notFound(); }
        catch (IOException ex) { throw storageError(); }
    }
    private void rejectSymlinks(Path path) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw storageError();
        }
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "STREAM_FILE_NOT_FOUND", "Stream file not found"); }
    private ApiException storageError() { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STREAM_STORAGE_ERROR", "Cannot access stream storage"); }
}
