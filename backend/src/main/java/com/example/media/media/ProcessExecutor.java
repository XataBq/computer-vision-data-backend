package com.example.media.media;

import java.time.Duration;
import java.util.List;

public interface ProcessExecutor {
    byte[] execute(List<String> command, Duration timeout);
}
