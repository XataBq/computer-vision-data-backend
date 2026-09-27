package com.example.media.media;

import java.io.IOException;
import java.util.List;

public interface StreamingProcessLauncher {
    Process start(List<String> arguments) throws IOException;
}
