package com.neel.syntaxvalidation.binary.manager;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Static convenience methods for common binary management operations.
 *
 * <p>This class provides quick, one-liner access to the most frequent
 * {@link BinaryManager} operations without requiring explicit instantiation.
 *
 * <h3>Quick Examples</h3>
 * <pre>{@code
 * // Check and print full status report
 * BinaryUtils.printStatusReport();
 *
 * // Download a single binary with progress
 * BinaryUtils.download(BinaryInfo.VNU, progress -> {
 *     System.out.printf("Downloading: %d%%%n", progress);
 * });
 *
 * // Download all missing with a simple progress callback
 * BinaryUtils.downloadAllMissing(status -> {
 *     System.out.println(status.formatReport());
 * });
 *
 * // Quick check: is a language supported on this system?
 * boolean javaOk = BinaryUtils.isLanguageSupported("Java");
 * }</pre>
 */
public final class BinaryUtils {

    /** Default installation directory for binary dependencies. */
    private static final Path DEFAULT_INSTALL_DIR = Paths.get(".binaries");

    private BinaryUtils() { }

    // ====================================================================
    //  Status queries
    // ====================================================================

    /**
     * Checks the availability of all known binaries and returns their statuses.
     *
     * @return a list of status reports.
     * @throws IOException if the default install directory cannot be created.
     */
    public static List<BinaryStatus> checkAll() throws IOException {
        return new BinaryManager().getAllStatuses();
    }

    /**
     * Checks the availability of a specific binary.
     *
     * @param info the binary to check.
     * @return the status report.
     * @throws IOException if the default install directory cannot be created.
     */
    public static BinaryStatus check(BinaryInfo info) throws IOException {
        return new BinaryManager().getStatus(info);
    }

    /**
     * Returns a full formatted status report for all binaries.
     *
     * @return a human-readable multi-line report string.
     * @throws IOException if the default install directory cannot be created.
     */
    public static String getStatusReport() throws IOException {
        return new BinaryManager().getFullReport();
    }

    /**
     * Prints a full status report to {@code System.out}.
     *
     * @throws IOException if the default install directory cannot be created.
     */
    public static void printStatusReport() throws IOException {
        System.out.println(getStatusReport());
    }

    /**
     * Checks whether a specific programming language is fully supported
     * (i.e. all required binaries are available and version-satisfied).
     *
     * @param language the language name (e.g. {@code "Java"}, {@code "JavaScript"}).
     * @return {@code true} if all required binaries for the language are available.
     * @throws IOException if the default install directory cannot be created.
     */
    public static boolean isLanguageSupported(String language) throws IOException {
        BinaryManager manager = new BinaryManager();
        for (BinaryInfo info : BinaryInfo.ALL) {
            for (String lang : info.getEnabledLanguages()) {
                if (lang.equalsIgnoreCase(language)) {
                    BinaryStatus status = manager.getStatus(info);
                    if (!status.isAvailable() || !status.isVersionSatisfied()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ====================================================================
    //  Download operations
    // ====================================================================

    /**
     * Downloads and installs a single binary with progress reporting.
     *
     * @param info          the binary to download.
     * @param progressPct   a callback receiving progress percentages (0–100),
     *                      or {@code null} for no progress reporting.
     * @return the path to the installed binary.
     * @throws IOException if the download or installation fails.
     */
    public static Path download(BinaryInfo info, Consumer<Integer> progressPct) throws IOException {
        BinaryManager manager = new BinaryManager();

        if (progressPct != null) {
            manager.addProgressListener(new DownloadProgressListener() {
                @Override
                public void onProgress(String name, long downloaded, long total) {
                    if (total > 0) {
                        progressPct.accept((int) (downloaded * 100 / total));
                    }
                }
            });
        }

        return manager.downloadAndInstall(info);
    }

    /**
     * Downloads and installs a single binary without progress reporting.
     *
     * @param info the binary to download.
     * @return the path to the installed binary.
     * @throws IOException if the download or installation fails.
     */
    public static Path download(BinaryInfo info) throws IOException {
        return download(info, null);
    }

    /**
     * Downloads all missing binaries with a completion callback for each.
     *
     * @param onEachComplete called after each binary is processed (whether
     *                       successful or not), receiving its status.
     * @return the final status list after all downloads.
     * @throws IOException if any download fails fatally.
     */
    public static List<BinaryStatus> downloadAllMissing(Consumer<BinaryStatus> onEachComplete)
            throws IOException {
        BinaryManager manager = new BinaryManager();
        List<BinaryStatus> results = manager.downloadAllMissing();
        if (onEachComplete != null) {
            results.forEach(onEachComplete);
        }
        return results;
    }

    /**
     * Downloads all missing binaries and prints progress to {@code System.out}.
     *
     * @return the final status list.
     * @throws IOException if any download fails fatally.
     */
    public static List<BinaryStatus> downloadAllMissing() throws IOException {
        return downloadAllMissing(status -> {
            String icon = status.isAvailable() ? "✓" : "✗";
            System.out.printf("  [%s] %s%n", icon, status.getBinaryInfo().getId());
        });
    }

    /**
     * Downloads all missing binaries asynchronously and waits for completion.
     *
     * @param timeout the maximum time to wait for all downloads.
     * @return the final status list.
     * @throws TimeoutException   if the timeout expires.
     * @throws IOException        if a download fails.
     * @throws InterruptedException if the current thread is interrupted.
     */
    public static List<BinaryStatus> downloadAllMissingWithTimeout(Duration timeout)
            throws TimeoutException, IOException, InterruptedException {
        BinaryManager manager = new BinaryManager();
        DownloadSession session = manager.downloadAllMissingAsync();

        // Print progress periodically
        while (!session.isDone()) {
            System.out.println(session.getProgressString());
            try { Thread.sleep(1000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        return session.awaitCompletion(timeout);
    }

    // ====================================================================
    //  Diagnostics
    // ====================================================================

    /**
     * Returns a list of binaries that need to be downloaded/installed for a
     * specific language to be fully supported.
     *
     * @param language the language name (e.g. {@code "Python"}).
     * @return a list of {@link BinaryInfo} for missing or outdated binaries.
     * @throws IOException if the default install directory cannot be created.
     */
    public static List<BinaryInfo> getMissingBinaries(String language) throws IOException {
        BinaryManager manager = new BinaryManager();
        java.util.ArrayList<BinaryInfo> missing = new java.util.ArrayList<>();
        for (BinaryInfo info : BinaryInfo.ALL) {
            for (String lang : info.getEnabledLanguages()) {
                if (lang.equalsIgnoreCase(language)) {
                    BinaryStatus status = manager.getStatus(info);
                    if (!status.isAvailable() || !status.isVersionSatisfied()) {
                        missing.add(info);
                    }
                }
            }
        }
        return missing;
    }

    /**
     * Returns a human-readable summary of what needs to be installed for a
     * specific language.
     *
     * @param language the language name.
     * @return a diagnostic string, or {@code null} if everything is satisfied.
     * @throws IOException if the default install directory cannot be created.
     */
    public static String getDiagnostics(String language) throws IOException {
        List<BinaryInfo> missing = getMissingBinaries(language);
        if (missing.isEmpty()) {
            return null; // everything is OK
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Missing dependencies for ").append(language).append(":\n");
        for (BinaryInfo info : missing) {
            sb.append("  - ").append(info.getId());
            info.getMinimumVersion().ifPresent(v -> sb.append(" (min version: ").append(v).append(")"));
            info.getDownloadUrl().ifPresent(url -> sb.append("\n    Download: ").append(url));
            if (info.isNpmPackage()) {
                info.getNpmPackageName().ifPresent(pkg -> sb.append("\n    Install: npm install -g ").append(pkg));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
