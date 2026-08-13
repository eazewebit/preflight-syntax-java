package com.neel.syntaxvalidation.binary.manager;

/**
 * Callback interface for monitoring binary download and installation progress.
 *
 * <p>Implementations receive lifecycle events as a binary package is downloaded,
 * verified, and installed. All methods have empty default implementations so
 * that callers can override only the events they care about.
 *
 * <p><b>Thread safety:</b> implementations must be safe to call from any thread,
 * as download operations may run on background threads.
 *
 * <pre>{@code
 * BinaryManager manager = new BinaryManager(installDir);
 * manager.addProgressListener(new DownloadProgressListener() {
 *     @Override
 *     public void onDownloadStart(String binaryName, long totalBytes) {
 *         System.out.printf("Downloading %s (%d bytes)...%n", binaryName, totalBytes);
 *     }
 *
 *     @Override
 *     public void onProgress(String binaryName, long bytesDownloaded, long totalBytes) {
 *         int pct = (int) (bytesDownloaded * 100 / totalBytes);
 *         System.out.printf("\r%s: %d%%", binaryName, pct);
 *     }
 *
 *     @Override
 *     public void onDownloadComplete(String binaryName, long totalBytes) {
 *         System.out.printf("%n%s download complete.%n", binaryName);
 *     }
 * });
 * }</pre>
 */
public interface DownloadProgressListener {

    /**
     * Called when a download begins.
     *
     * @param binaryName the logical name of the binary being downloaded
     *                   (e.g. {@code "node"}, {@code "vnu"}).
     * @param totalBytes the expected total size in bytes, or {@code -1} if
     *                   the content length is unknown.
     */
    default void onDownloadStart(String binaryName, long totalBytes) { }

    /**
     * Called periodically during the download to report incremental progress.
     *
     * @param binaryName      the logical name of the binary.
     * @param bytesDownloaded the cumulative number of bytes downloaded so far.
     * @param totalBytes      the expected total size, or {@code -1} if unknown.
     */
    default void onProgress(String binaryName, long bytesDownloaded, long totalBytes) { }

    /**
     * Called when the download has finished successfully.
     *
     * @param binaryName the logical name of the binary.
     * @param totalBytes the total number of bytes downloaded.
     */
    default void onDownloadComplete(String binaryName, long totalBytes) { }

    /**
     * Called when extraction of an archive (zip/tar.gz) begins.
     *
     * @param binaryName the logical name of the binary.
     * @param archivePath the path to the archive being extracted.
     */
    default void onExtractStart(String binaryName, String archivePath) { }

    /**
     * Called when extraction completes.
     *
     * @param binaryName  the logical name of the binary.
     * @param extractedTo the directory the archive was extracted into.
     */
    default void onExtractComplete(String binaryName, String extractedTo) { }

    /**
     * Called when an npm package installation begins (for Node-based tools).
     *
     * @param packageName the npm package name (e.g. {@code "typescript"}, {@code "stylelint"}).
     */
    default void onNpmInstallStart(String packageName) { }

    /**
     * Called when an npm package installation completes.
     *
     * @param packageName the npm package name.
     * @param success     {@code true} if the installation succeeded.
     */
    default void onNpmInstallComplete(String packageName, boolean success) { }

    /**
     * Called when an error occurs during any phase of the operation.
     *
     * @param binaryName the logical name of the binary, or {@code null} if
     *                   the error is not associated with a specific binary.
     * @param message    a human-readable error description.
     * @param error      the underlying exception, or {@code null}.
     */
    default void onError(String binaryName, String message, Throwable error) { }

    /**
     * Called to report an informational message (non-error).
     *
     * @param binaryName the logical name of the binary, or {@code null}.
     * @param message    the informational message.
     */
    default void onInfo(String binaryName, String message) { }
}
