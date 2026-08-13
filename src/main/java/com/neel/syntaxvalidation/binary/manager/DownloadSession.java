package com.neel.syntaxvalidation.binary.manager;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an asynchronous download session for multiple binaries.
 *
 * <p>A {@code DownloadSession} is created by
 * {@link BinaryManager#downloadAllMissingAsync()} and runs on a virtual thread.
 * It provides:
 * <ul>
 *   <li>Overall progress tracking ({@link #getCompletedCount()}, {@link #getTotalCount()})</li>
 *   <li>Aggregate bytes downloaded ({@link #getBytesDownloaded()})</li>
 *   <li>Blocking wait with timeout ({@link #awaitCompletion(Duration)})</li>
 *   <li>Cancellation support ({@link #cancel()})</li>
 *   <li>A formatted progress string ({@link #getProgressString()})</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * DownloadSession session = manager.downloadAllMissingAsync();
 *
 * // Periodically print progress
 * while (!session.isDone()) {
 *     System.out.println(session.getProgressString());
 *     Thread.sleep(500);
 * }
 *
 * // Or simply wait for completion
 * session.awaitCompletion(Duration.ofMinutes(10));
 * System.out.println(session.getSummary());
 * }</pre>
 *
 * <p>This class is thread-safe.
 */
public class DownloadSession {

    private enum State { RUNNING, COMPLETED, CANCELLED, FAILED }

    private final BinaryManager manager;
    private final List<BinaryInfo> downloadQueue = new CopyOnWriteArrayList<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicLong bytesDownloaded = new AtomicLong(0);
    private final Instant startTime = Instant.now();
    private final CompletableFuture<List<BinaryStatus>> future;
    private volatile State state = State.RUNNING;
    private volatile String currentBinary = null;

    DownloadSession(BinaryManager manager) {
        this.manager = manager;
        this.future = new CompletableFuture<>();
    }

    /**
     * Starts the download session on a virtual thread.
     */
    void start() {
        Thread.ofVirtual().name("binary-download-session").start(() -> {
            try {
                // Add our internal tracking listener
                DownloadProgressListener tracker = new DownloadProgressListener() {
                    @Override
                    public void onDownloadStart(String binaryName, long totalBytes) {
                        currentBinary = binaryName;
                    }

                    @Override
                    public void onProgress(String binaryName, long downloaded, long total) {
                        bytesDownloaded.addAndGet(downloaded - (bytesDownloaded.get()));
                    }

                    @Override
                    public void onDownloadComplete(String binaryName, long total) {
                        completedCount.incrementAndGet();
                        currentBinary = null;
                    }

                    @Override
                    public void onError(String binaryName, String message, Throwable error) {
                        failedCount.incrementAndGet();
                    }
                };
                manager.addProgressListener(tracker);

                List<BinaryStatus> results = manager.downloadAllMissing();

                if (state == State.CANCELLED) {
                    future.cancel(true);
                } else {
                    state = State.COMPLETED;
                    future.complete(results);
                }

                manager.removeProgressListener(tracker);
            } catch (Exception e) {
                state = State.FAILED;
                future.completeExceptionally(e);
            }
        });
    }

    /** Returns the total number of binaries to download. */
    public int getTotalCount() {
        return downloadQueue.size();
    }

    /** Returns the number of binaries successfully downloaded. */
    public int getCompletedCount() {
        return completedCount.get();
    }

    /** Returns the number of binaries that failed to download. */
    public int getFailedCount() {
        return failedCount.get();
    }

    /** Returns the total bytes downloaded so far. */
    public long getBytesDownloaded() {
        return bytesDownloaded.get();
    }

    /** Returns whether the session is done (completed, failed, or cancelled). */
    public boolean isDone() {
        return state != State.RUNNING;
    }

    /** Returns whether the session was cancelled. */
    public boolean isCancelled() {
        return state == State.CANCELLED;
    }

    /**
     * Cancels the download session.
     * Binaries that are currently being downloaded may finish, but no new
     * downloads will be started.
     */
    public void cancel() {
        state = State.CANCELLED;
    }

    /**
     * Blocks until all downloads are complete or the timeout expires.
     *
     * @param timeout the maximum time to wait.
     * @return the list of binary statuses.
     * @throws TimeoutException if the timeout expires.
     * @throws IOException      if a download fails.
     * @throws InterruptedException if the current thread is interrupted.
     */
    public List<BinaryStatus> awaitCompletion(Duration timeout)
            throws TimeoutException, IOException, InterruptedException {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Download session failed", cause);
        } catch (TimeoutException e) {
            throw e;
        }
    }

    /**
     * Returns a formatted progress string showing current download state.
     *
     * @return a human-readable progress string.
     */
    public String getProgressString() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[BinaryManager] %s", state));
        if (currentBinary != null) {
            sb.append(" | Downloading: ").append(currentBinary);
        }
        sb.append(String.format(" | Completed: %d | Failed: %d | Elapsed: %ds",
                completedCount.get(), failedCount.get(), elapsed.getSeconds()));
        long bytes = bytesDownloaded.get();
        if (bytes > 0) {
            sb.append(String.format(" | Downloaded: %s", formatBytes(bytes)));
        }
        return sb.toString();
    }

    /**
     * Returns a summary of the download session results.
     *
     * @return a human-readable summary string.
     */
    public String getSummary() {
        Duration elapsed = Duration.between(startTime, Instant.now());
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║              Download Session Summary                    ║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("  Status      : %s%n", state));
        sb.append(String.format("  Completed   : %d%n", completedCount.get()));
        sb.append(String.format("  Failed      : %d%n", failedCount.get()));
        sb.append(String.format("  Downloaded  : %s%n", formatBytes(bytesDownloaded.get())));
        sb.append(String.format("  Duration    : %d seconds%n", elapsed.getSeconds()));
        sb.append("╚══════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
