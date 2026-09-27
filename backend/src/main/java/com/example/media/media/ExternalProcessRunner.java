package com.example.media.media;

import com.example.media.config.MediaProperties;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static com.example.media.media.MediaProcessException.Reason.*;

@Component
public class ExternalProcessRunner implements ProcessExecutor {
    private static final Logger log = LoggerFactory.getLogger(ExternalProcessRunner.class);
    private static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private final Semaphore slots;
    public ExternalProcessRunner(MediaProperties properties) { slots = new Semaphore(properties.maxConcurrentProcesses()); }

    @Override public byte[] execute(List<String> command, Duration timeout) {
        if (!slots.tryAcquire()) throw new MediaProcessException(BUSY);
        Process process = null;
        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        log.info("Media operation started");
        try {
            // Never pass through a shell or log command arguments / stderr (they can contain passwords).
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.getOutputStream().close();
            Process running = process;
            Future<byte[]> output = reader.submit(() -> readBounded(running.getInputStream()));
            long deadline = System.nanoTime() + timeout.toNanos();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new MediaProcessException(TIMEOUT);
            if (process.exitValue() != 0) throw new MediaProcessException(EXIT_FAILED);
            byte[] result = output.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            log.info("Media operation completed");
            return result;
        } catch (IOException ex) {
            throw new MediaProcessException(START_FAILED);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MediaProcessException(INTERRUPTED);
        } catch (TimeoutException ex) {
            throw new MediaProcessException(TIMEOUT);
        } catch (ExecutionException ex) {
            throw new MediaProcessException(OUTPUT_FAILED);
        } catch (MediaProcessException ex) {
            log.warn("Media operation failed: reason={}", ex.reason());
            throw ex;
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                if (process.isAlive()) process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                try { process.getInputStream().close(); } catch (IOException ignored) { }
            }
            reader.shutdownNow();
            slots.release();
        }
    }
    private byte[] readBounded(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            boolean overflow = false;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count <= MAX_OUTPUT_BYTES) output.write(buffer, 0, count);
                else overflow = true; // Keep draining so the child can never block on a full pipe.
            }
            if (overflow) throw new IOException("Output limit exceeded");
            return output.toByteArray();
        }
    }
}
