package jp.yuya.dev.developerdungeon.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
class DockerGateway {
    private static final int MAX_OUTPUT_BYTES_PER_STREAM = 32 * 1024;
    private final RunnerProperties properties;

    DockerGateway(RunnerProperties properties) { this.properties = properties; }

    ProcessResult run(List<String> arguments, Duration timeout) {
        var command = new ArrayList<String>();
        if (!java.nio.file.Path.of(properties.dockerExecutable()).isAbsolute() || !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(properties.dockerExecutable()))) {
            throw new IllegalStateException("docker executable is invalid");
        }
        command.add(properties.dockerExecutable());
        command.addAll(arguments);
        var processBuilder = new ProcessBuilder(command);
        Map<String, String> environment = processBuilder.environment();
        environment.clear();
        environment.put("PATH", System.getenv().getOrDefault("PATH", ""));
        environment.put("SystemRoot", System.getenv().getOrDefault("SystemRoot", "C:\\Windows"));
        try {
            var process = processBuilder.start();
            var stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));
            var stderr = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("docker command timed out");
            }
            var capturedOut = stdout.join(); var capturedErr = stderr.join();
            return new ProcessResult(process.exitValue(), capturedOut.value(), capturedErr.value(), capturedOut.truncated() || capturedErr.truncated());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker command interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("docker command failed", exception);
        }
    }

    private LimitedOutput readLimited(InputStream input) {
        try (input; var captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            boolean truncated = false;
            for (int read; (read = input.read(buffer)) >= 0;) {
                int remaining = MAX_OUTPUT_BYTES_PER_STREAM - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(read, remaining));
                if (read > remaining) truncated = true;
            }
            return new LimitedOutput(captured.toString(StandardCharsets.UTF_8), truncated);
        } catch (IOException exception) {
            throw new IllegalStateException("docker output read failed", exception);
        }
    }

    record ProcessResult(int exitCode, String stdout, String stderr, boolean outputTruncated) { }
    private record LimitedOutput(String value, boolean truncated) { }
}
