package com.neel.syntaxvalidation.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs an external command and captures its standard output, standard error and
 * exit code.
 *
 * <p>Output streams are drained concurrently using virtual threads so that
 * processes producing large amounts of both {@code stdout} and {@code stderr}
 * cannot deadlock. A configurable timeout guards against runaway processes: if
 * the deadline is exceeded the entire process tree is destroyed forcibly and a
 * {@link ProcessResult} with {@code timedOut == true} is returned.
 *
 * <p><b>Important:</b> the stream readers are started <em>before</em> waiting,
 * and their results are collected <em>after</em> the wait completes. This
 * guarantees that the timeout is honoured even for processes that stream output
 * continuously (the readers block on EOF, which only happens once the process
 * exits or is killed).
 *
 * <p>The executor is stateless (apart from internal threading) and safe to share
 * across validators and threads.
 */
public class ProcessExecutor {

    /** Default per-process timeout of 30 seconds. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final Duration timeout;

    /** Creates an executor with the {@link #DEFAULT_TIMEOUT default timeout}. */
    public ProcessExecutor() {
        this(DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout the maximum time to wait for a process to finish.
     */
    public ProcessExecutor(Duration timeout) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Executes the given command and returns the captured result.
     *
     * @param command the command and its arguments; must not be {@code null} or empty.
     * @return a {@link ProcessResult} describing the outcome.
     * @throws IOException          if the process cannot be started or its output cannot be read.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }

        Process process = new ProcessBuilder(command).start();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdoutFuture = pool.submit(() -> readAll(process.getInputStream()));
            Future<String> stderrFuture = pool.submit(() -> readAll(process.getErrorStream()));

            boolean finished = process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!finished) {
                destroyTree(process);
            }

            String stdout = join(stdoutFuture);
            String stderr = join(stderrFuture);
            return finished
                    ? new ProcessResult(process.exitValue(), stdout, stderr, false)
                    : new ProcessResult(-1, stdout, stderr, true);
        }
    }

    /**
     * Kills a process and any descendants it spawned (e.g. {@code cmd /c ...}).
     */
    private static void destroyTree(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        process.destroyForcibly();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String join(Future<String> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Failed to read process output", cause);
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
