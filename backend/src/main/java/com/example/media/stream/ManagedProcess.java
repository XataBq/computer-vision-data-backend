package com.example.media.stream;

import java.nio.file.Path;
import java.time.Instant;

// Deliberately contains no command arguments or camera URL, and is never serialized to clients.
record ManagedProcess(Process process, StreamKey key, Instant startedAt, Path outputDirectory) {}
