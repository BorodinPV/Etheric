package com.etheric.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared helpers for starting and stopping docker-compose from Maven, tests, or dev scripts.
 */
public final class DockerComposeSupport {

    private DockerComposeSupport() {
    }

    public static void up() {
        if (isSkipped()) {
            return;
        }
        int exitCode = execCompose(false, "up", "-d", "--wait");
        if (exitCode != 0) {
            throw new IllegalStateException("docker compose up exited with code " + exitCode);
        }
    }

    public static void upQuiet() {
        if (isSkipped()) {
            return;
        }
        runComposeQuiet("up", "-d", "--wait");
    }

    public static void down() {
        if (isSkipped()) {
            return;
        }
        int exitCode = execCompose(false, "down");
        if (exitCode != 0) {
            throw new IllegalStateException("docker compose down exited with code " + exitCode);
        }
    }

    public static void downQuiet() {
        if (isSkipped()) {
            return;
        }
        runComposeQuiet("down");
    }

    public static Path findComposeDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docker-compose.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "docker-compose.yml not found (user.dir=" + System.getProperty("user.dir") + ")");
    }

    public static boolean isSkipped() {
        return Boolean.parseBoolean(System.getProperty("skipDockerCompose", "false"));
    }

    private static void runComposeQuiet(String... args) {
        try {
            int exitCode = execCompose(true, args);
            if (exitCode != 0) {
                System.err.println("Warning: docker compose " + String.join(" ", args)
                        + " exited with code " + exitCode);
            }
        } catch (RuntimeException e) {
            System.err.println("Warning: docker compose " + String.join(" ", args)
                    + " failed: " + e.getMessage());
        }
    }

    private static int execCompose(boolean quietIo, String... args) {
        Path composeDir = findComposeDirectory();
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("compose");
        Collections.addAll(command, args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(composeDir.toFile());
        if (!quietIo) {
            builder.inheritIO();
        }
        try {
            Process process = builder.start();
            if (!process.waitFor(1, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("docker compose timed out: " + String.join(" ", args));
            }
            return process.exitValue();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run docker compose", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running docker compose", e);
        }
    }
}
