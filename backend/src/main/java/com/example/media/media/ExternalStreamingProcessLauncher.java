package com.example.media.media;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class ExternalStreamingProcessLauncher implements StreamingProcessLauncher {
    @Override public Process start(List<String> arguments) throws IOException {
        // OS-level redirection has no pipe to fill and no unbounded log containing camera secrets.
        Process process = new ProcessBuilder(arguments)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            process.getOutputStream().close();
            return process;
        } catch (IOException ex) {
            process.destroyForcibly();
            throw new IOException("Cannot initialize media process");
        }
    }
}
