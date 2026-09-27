package com.example.media.stream;

import com.example.media.config.HlsProperties;
import com.example.media.exception.ApiException;
import com.example.media.media.StreamingProcessLauncher;
import com.example.media.storage.HlsStorage;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

@Component
public class ProcessManager {
    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);
    private final ConcurrentHashMap<StreamKey, ManagedProcess> processes = new ConcurrentHashMap<>();
    // Fixed stripes avoid retaining one lock for every source ever seen.
    private final Object[] locks = new Object[64];
    private final Object lifecycle = new Object();
    private final StreamingProcessLauncher launcher;
    private final HlsStorage storage;
    private final HlsProperties properties;
    private volatile boolean shuttingDown;

    public ProcessManager(StreamingProcessLauncher launcher, HlsStorage storage, HlsProperties properties) {
        this.launcher = launcher; this.storage = storage; this.properties = properties;
        Arrays.setAll(locks, i -> new Object());
    }
    /** Shares a lock with start/stop through DB commit, preventing a start/delete race. */
    public <T> T withSourceLock(StreamKey key, Supplier<T> action) {
        synchronized (locks[Math.floorMod(key.hashCode(), locks.length)]) { return action.get(); }
    }
    public StreamResponse start(StreamKey key, Function<Path, List<String>> command) {
        return withSourceLock(key, () -> {
            if (shuttingDown) throw unavailable("STREAM_SHUTTING_DOWN", "Application is shutting down");
            ManagedProcess existing = processes.get(key);
            if (existing != null && existing.process().isAlive()) return StreamResponse.running(key, existing.startedAt());
            if (existing != null) processes.remove(key, existing);
            Path directory = storage.prepare(key);
            ManagedProcess managed;
            try {
                synchronized (lifecycle) {
                    if (shuttingDown) throw unavailable("STREAM_SHUTTING_DOWN", "Application is shutting down");
                    if (processes.size() >= properties.maxStreams()) throw unavailable("STREAM_CAPACITY_REACHED", "All stream slots are occupied");
                    Process process = launcher.start(command.apply(directory));
                    managed = new ManagedProcess(process, key, Instant.now(), directory);
                    processes.put(key, managed);
                }
            } catch (IOException ex) {
                storage.cleanup(key);
                log.warn("Stream launch failed: type={}, sourceId={}", key.type(), key.sourceId());
                throw unavailable("STREAM_START_FAILED", "Cannot start FFmpeg stream");
            } catch (RuntimeException ex) { storage.cleanup(key); throw ex; }
            managed.process().onExit().whenComplete((process, error) -> exited(managed));
            log.info("Stream starting: type={}, sourceId={}", key.type(), key.sourceId());
            try {
                awaitReady(managed);
                log.info("Stream ready: type={}, sourceId={}", key.type(), key.sourceId());
                return StreamResponse.running(key, managed.startedAt());
            } catch (RuntimeException ex) {
                stop(key);
                throw ex;
            }
        });
    }
    private void awaitReady(ManagedProcess managed) {
        long deadline = System.nanoTime() + properties.startupTimeout().toNanos();
        while (System.nanoTime() < deadline) {
            if (shuttingDown) throw unavailable("STREAM_SHUTTING_DOWN", "Application is shutting down");
            if (!managed.process().isAlive()) throw unavailable("STREAM_EXITED", "FFmpeg exited before stream became ready");
            if (storage.ready(managed.key()) && managed.process().isAlive()) return;
            try { Thread.sleep(50); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw unavailable("STREAM_START_INTERRUPTED", "Stream startup interrupted");
            }
        }
        log.warn("Stream readiness timeout: type={}, sourceId={}", managed.key().type(), managed.key().sourceId());
        throw unavailable("STREAM_START_TIMEOUT", "Stream did not become ready before timeout");
    }
    public StreamResponse status(StreamKey key) {
        return withSourceLock(key, () -> {
            ManagedProcess managed = processes.get(key);
            if (managed != null && managed.process().isAlive()) return StreamResponse.running(key, managed.startedAt());
            if (managed != null) exited(managed);
            return StreamResponse.stopped(key);
        });
    }
    public StreamResponse stop(StreamKey key) {
        return withSourceLock(key, () -> {
            ManagedProcess managed = processes.get(key);
            if (managed != null) {
                terminate(managed.process());
                processes.remove(key, managed);
                log.info("Stream stopped: type={}, sourceId={}", key.type(), key.sourceId());
            }
            storage.cleanup(key);
            return StreamResponse.stopped(key);
        });
    }
    private void exited(ManagedProcess managed) {
        withSourceLock(managed.key(), () -> {
            // A callback from an old generation must never remove the replacement session or its files.
            if (!managed.process().isAlive() && processes.remove(managed.key(), managed)) {
                log.info("Stream exited: type={}, sourceId={}, exitCode={}", managed.key().type(), managed.key().sourceId(), managed.process().exitValue());
                try { storage.cleanup(managed.key()); }
                catch (RuntimeException ex) { log.error("Stream cleanup failed: sourceId={}", managed.key().sourceId()); }
            }
            return null;
        });
    }
    private void terminate(Process process) {
        boolean interrupted = Thread.interrupted();
        List<ProcessHandle> children = process.descendants().toList();
        try {
            children.forEach(ProcessHandle::destroy);
            process.destroy();
            try { process.waitFor(properties.stopTimeout().toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException ex) { interrupted = true; }
            children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
                long deadline = System.nanoTime() + properties.stopTimeout().toNanos();
                while (process.isAlive() && System.nanoTime() < deadline) {
                    try { process.waitFor(50, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException ex) { interrupted = true; }
                }
            }
            if (process.isAlive() || children.stream().anyMatch(ProcessHandle::isAlive))
                throw unavailable("STREAM_STOP_FAILED", "FFmpeg process could not be stopped");
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
    @PreDestroy
    public void shutdown() {
        synchronized (lifecycle) { shuttingDown = true; }
        for (StreamKey key : List.copyOf(processes.keySet())) {
            try { stop(key); }
            catch (RuntimeException ex) { log.error("Stream shutdown failed: type={}, sourceId={}", key.type(), key.sourceId()); }
        }
    }
    private ApiException unavailable(String code, String message) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message); }
}
