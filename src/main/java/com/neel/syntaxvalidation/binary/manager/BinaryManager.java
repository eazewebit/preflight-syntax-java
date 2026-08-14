package com.neel.syntaxvalidation.binary.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that acts as a lightweight package manager for external binary
 * dependencies required by the Syntax Validation Library.
 *
 * <p>The {@code BinaryManager} provides three core capabilities:
 * <ol>
 *   <li><b>Status reporting</b> – query which binaries are currently installed,
 *       their detected versions, and whether they meet minimum requirements.</li>
 *   <li><b>Download &amp; installation</b> – download binaries from their official
 *       sources, extract archives, and install npm packages.</li>
 *   <li><b>Progress monitoring</b> – register {@link DownloadProgressListener}s
 *       to receive real-time callbacks during download and installation.</li>
 * </ol>
 *
 * <h3>Quick Start</h3>
 * <pre>{@code
 * // 1. Create a manager with a local installation directory
 * BinaryManager manager = new BinaryManager(Path.of(".binaries"));
 *
 * // 2. Add a progress listener for user feedback
 * manager.addProgressListener(new DownloadProgressListener() {
 *     public void onProgress(String name, long downloaded, long total) {
 *         System.out.printf("\r%s: %d%%", name, (downloaded * 100 / total));
 *     }
 *     public void onDownloadComplete(String name, long total) {
 *         System.out.printf("%n%s complete.%n", name);
 *     }
 * });
 *
 * // 3. Check what's already available
 * for (BinaryStatus status : manager.getAllStatuses()) {
 *     System.out.println(status.formatReport());
 * }
 *
 * // 4. Download a specific binary
 * manager.downloadAndInstall(BinaryInfo.VNU);
 *
 * // 5. Install an npm-based tool
 * manager.installNpmPackage(BinaryInfo.TSC);
 *
 * // 6. Download all missing binaries
 * manager.downloadAllMissing();
 * }</pre>
 *
 * <h3>Supported Binaries</h3>
 * <table border="1" cellpadding="4">
 *   <tr><th>ID</th><th>Type</th><th>Languages Enabled</th></tr>
 *   <tr><td>node</td><td>Archive download</td><td>JavaScript, TypeScript</td></tr>
 *   <tr><td>javac</td><td>System (pre-installed)</td><td>Java</td></tr>
 *   <tr><td>tsc</td><td>npm package</td><td>TypeScript</td></tr>
 *   <tr><td>python</td><td>System / installer</td><td>Python</td></tr>
 *   <tr><td>php</td><td>Archive download</td><td>PHP</td></tr>
 *   <tr><td>vnu</td><td>JAR download</td><td>HTML</td></tr>
 *   <tr><td>stylelint</td><td>npm package</td><td>CSS</td></tr>
 * </table>
 *
 * <p>This class is thread-safe.
 */
public class BinaryManager {

    private static final Logger log = LoggerFactory.getLogger(BinaryManager.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private static final Pattern SEMVER_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

    private final Path installDir;
    private final List<DownloadProgressListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<BinaryInfo, BinaryStatus> statusCache = new LinkedHashMap<>();

    /**
     * Creates a new {@code BinaryManager} that will download and store binaries
     * under the given directory.
     *
     * @param installDir the root directory for binary installations.
     *                   Created automatically if it does not exist.
     * @throws IOException if the directory cannot be created.
     */
    public BinaryManager(Path installDir) throws IOException {
        this.installDir = Objects.requireNonNull(installDir, "installDir");
        Files.createDirectories(installDir);
    }

    // ====================================================================
    //  Progress listener management
    // ====================================================================

    /**
     * Registers a progress listener to receive download/installation events.
     *
     * @param listener the listener to add; must not be {@code null}.
     */
    public void addProgressListener(DownloadProgressListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
    }

    /**
     * Removes a previously registered progress listener.
     *
     * @param listener the listener to remove.
     * @return {@code true} if the listener was found and removed.
     */
    public boolean removeProgressListener(DownloadProgressListener listener) {
        return listeners.remove(listener);
    }

    /** Removes all registered progress listeners. */
    public void clearProgressListeners() {
        listeners.clear();
    }

    // ====================================================================
    //  Status queries
    // ====================================================================

    /**
     * Returns the installation directory used by this manager.
     */
    public Path getInstallDir() {
        return installDir;
    }

    /**
     * Checks the current status of a single binary.
     *
     * <p>This method attempts to resolve the binary on the system PATH first,
     * and falls back to the local installation directory. It then extracts
     * the version string (by running the binary with a version flag) and
     * compares it against the minimum requirement.
     *
     * @param info the binary to check.
     * @return a {@link BinaryStatus} describing the binary's availability.
     */
    public BinaryStatus getStatus(BinaryInfo info) {
        Objects.requireNonNull(info, "info");

        BinaryStatus.Builder builder = BinaryStatus.builder(info);

        // --- 1. Try system PATH first ---
        Path resolvedPath = resolveFromPath(info);

        if (resolvedPath == null) {
            // --- 2. Fall back to local installation dir ---
            Path localPath = info.getInstalledPath(installDir);
            if (Files.isRegularFile(localPath)) {
                resolvedPath = localPath;
            }
        }

        if (resolvedPath == null) {
            builder.available(false)
                   .diagnosticMessage("Binary '" + info.getCommandName()
                           + "' not found on PATH or in " + installDir);
            return builder.build();
        }

        builder.available(true).resolvedPath(resolvedPath);

        // --- 3. Detect version ---
        String version = detectVersion(info, resolvedPath);
        if (version != null) {
            builder.detectedVersion(version);
            boolean satisfied = info.getMinimumVersion()
                    .map(min -> compareVersions(version, min) >= 0)
                    .orElse(true);
            builder.versionSatisfied(satisfied);
        } else {
            builder.versionSatisfied(false);
            builder.diagnosticMessage("Could not detect version for " + info.getCommandName());
        }

        BinaryStatus status = builder.build();
        statusCache.put(info, status);
        return status;
    }

    /**
     * Returns the status of all known binary dependencies.
     *
     * @return a list of {@link BinaryStatus} objects, one per {@link BinaryInfo#ALL}.
     */
    public List<BinaryStatus> getAllStatuses() {
        List<BinaryStatus> statuses = new ArrayList<>();
        for (BinaryInfo info : BinaryInfo.ALL) {
            statuses.add(getStatus(info));
        }
        return statuses;
    }

    /**
     * Returns a formatted multi-line report summarizing the status of all binaries.
     *
     * @return a human-readable report string.
     */
    public String getFullReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║          Binary Dependency Status Report                ║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");

        List<BinaryStatus> statuses = getAllStatuses();
        int available = 0;
        for (BinaryStatus status : statuses) {
            if (status.isAvailable()) available++;
            sb.append("╠──────────────────────────────────────────────────────────╣\n");
            sb.append(status.formatReport());
        }
        sb.append("╚══════════════════════════════════════════════════════════╝\n");
        sb.append(String.format("\nSummary: %d / %d binaries available%n", available, statuses.size()));

        // Show enabled language summary
        Set<String> enabled = new LinkedHashSet<>();
        Set<String> disabled = new LinkedHashSet<>();
        for (BinaryStatus status : statuses) {
            String[] langs = status.getBinaryInfo().getEnabledLanguages();
            if (status.isAvailable() && status.isVersionSatisfied()) {
                Collections.addAll(enabled, langs);
            } else {
                Collections.addAll(disabled, langs);
            }
        }
        disabled.removeAll(enabled);
        if (!enabled.isEmpty()) {
            sb.append("Languages enabled  : ").append(String.join(", ", enabled)).append('\n');
        }
        if (!disabled.isEmpty()) {
            sb.append("Languages disabled : ").append(String.join(", ", disabled)).append('\n');
        }

        return sb.toString();
    }

    // ====================================================================
    //  Download & install
    // ====================================================================

    /**
     * Downloads and installs the specified binary.
     *
     * <p>The behavior depends on the binary type:
     * <ul>
     *   <li><b>npm packages</b> ({@link BinaryInfo#isNpmPackage()}): delegates
     *       to {@link #installNpmPackage(BinaryInfo)}.</li>
     *   <li><b>JAR files</b> (e.g. {@code vnu}): downloads and extracts the
     *       archive to the install directory.</li>
     *   <li><b>Archive downloads</b> (zip, tar.gz): downloads and extracts,
     *       placing executables in the expected locations.</li>
     * </ul>
     *
     * @param info the binary to download and install.
     * @return the resolved path to the installed executable.
     * @throws IOException          if the download or extraction fails.
     * @throws IllegalStateException if no download URL is configured for the binary.
     */
    public Path downloadAndInstall(BinaryInfo info) throws IOException {
        Objects.requireNonNull(info, "info");

        if (info.isNpmPackage()) {
            return installNpmPackage(info);
        }

        String url = info.getDownloadUrl()
                .orElseThrow(() -> new IllegalStateException(
                        "No download URL configured for binary: " + info.getId()));

        fireInfo(info.getId(), "Starting download from: " + url);

        // Determine archive type
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        // If the URL has no recognizable extension (e.g. redirect URLs like
        // Adoptium's ".../eclipse"), use a generic name and detect type by magic bytes
        if (!fileName.contains(".")) {
            fileName = info.getId() + "-download";
        }
        Path downloadTarget = installDir.resolve(fileName);

        // Download
        downloadFile(url, downloadTarget, info.getId());

        // Detect archive type: prefer file extension, fall back to magic bytes
        String archiveType = detectArchiveType(downloadTarget, fileName, url);

        // Extract if it's an archive
        if ("zip".equals(archiveType)) {
            fireExtractStart(info.getId(), downloadTarget.toString());
            extractZip(downloadTarget, installDir);
            fireExtractComplete(info.getId(), installDir.toString());
            // Clean up the archive
            Files.deleteIfExists(downloadTarget);
        } else if ("tar.gz".equals(archiveType)) {
            fireExtractStart(info.getId(), downloadTarget.toString());
            extractTarGz(downloadTarget, installDir);
            fireExtractComplete(info.getId(), installDir.toString());
            Files.deleteIfExists(downloadTarget);
        } else if ("tar.xz".equals(archiveType)) {
            fireExtractStart(info.getId(), downloadTarget.toString());
            extractTarXz(downloadTarget, installDir);
            fireExtractComplete(info.getId(), installDir.toString());
            Files.deleteIfExists(downloadTarget);
        }
        // .jar files are used directly without extraction

        // Set executable permissions on Unix
        setExecutablePermissions(info);

        // Verify installation
        Path installedPath = info.getInstalledPath(installDir);
        if (Files.exists(installedPath)) {
            fireInfo(info.getId(), "Successfully installed to: " + installedPath);
            return installedPath;
        }

        // For archives that extract into subdirectories, try to find the executable
        Path found = findInSubdirectory(info);
        if (found != null) {
            // Move/reorganize if needed
            reorganizeExtracted(info, found);
            Path finalPath = info.getInstalledPath(installDir);
            if (Files.exists(finalPath)) {
                fireInfo(info.getId(), "Successfully installed to: " + finalPath);
                return finalPath;
            }
        }

        throw new IOException("Download completed but executable not found at expected location: "
                + installedPath);
    }

    /**
     * Installs an npm package globally or locally within the install directory.
     *
     * <p>Requires that Node.js ({@link BinaryInfo#NODE}) is available on the
     * system. If Node.js is not found, an {@link IOException} is thrown with
     * a diagnostic message.
     *
     * @param info the npm-based binary to install.
     * @return the resolved path to the installed executable.
     * @throws IOException if Node.js is not available or the installation fails.
     */
    public Path installNpmPackage(BinaryInfo info) throws IOException {
        Objects.requireNonNull(info, "info");
        if (!info.isNpmPackage()) {
            throw new IllegalArgumentException(info.getId() + " is not an npm package");
        }

        String packageName = info.getNpmPackageName()
                .orElseThrow(() -> new IllegalStateException("No npm package name for " + info.getId()));

        // Verify Node.js availability
        BinaryStatus nodeStatus = getStatus(BinaryInfo.NODE);
        if (!nodeStatus.isAvailable()) {
            throw new IOException("Node.js is required to install '" + packageName
                    + "' but was not found. Please install Node.js first.");
        }

        fireNpmInstallStart(packageName);
        fireInfo(info.getId(), "Installing npm package: " + packageName);

        Path nodeModulesDir = installDir.resolve("node_modules");

        // Ensure package.json exists in install dir for local install
        Path pkgJson = installDir.resolve("package.json");
        if (!Files.exists(pkgJson)) {
            Files.writeString(pkgJson, "{}", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        // Run npm install
        String npmCmd = isWindows() ? "npm.cmd" : "npm";
        ProcessBuilder pb = new ProcessBuilder(npmCmd, "install", packageName)
                .directory(installDir.toFile())
                .redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + "\n" + b);
            }
            int exitCode = process.waitFor();
            boolean success = exitCode == 0;
            fireNpmInstallComplete(packageName, success);

            if (!success) {
                throw new IOException("npm install failed with exit code " + exitCode + ": " + output);
            }

            fireInfo(info.getId(), "npm package installed successfully: " + packageName);

            Path installedPath = info.getInstalledPath(installDir);
            if (Files.exists(installedPath)) {
                return installedPath;
            }

            // For Windows, the .cmd shim may be in node_modules/.bin
            Path binPath = installDir.resolve("node_modules").resolve(".bin")
                    .resolve(info.getWindowsExecutable());
            if (Files.exists(binPath)) {
                return binPath;
            }

            throw new IOException("npm install completed but executable not found: " + installedPath);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("npm install was interrupted", e);
        }
    }

    /**
     * Downloads and installs all binaries that are currently not available or
     * whose version does not meet the minimum requirement.
     *
     * <p>Skip binaries that have no download URL configured (e.g. {@code javac},
     * which must be obtained through a JDK installer).
     *
     * @return a list of {@link BinaryStatus} after the installation attempt.
     * @throws IOException if any download fails.
     */
    public List<BinaryStatus> downloadAllMissing() throws IOException {
        List<BinaryStatus> results = new ArrayList<>();

        for (BinaryInfo info : BinaryInfo.ALL) {
            BinaryStatus status = getStatus(info);

            if (status.isAvailable() && status.isVersionSatisfied()) {
                fireInfo(info.getId(), "Already available and version satisfied – skipping.");
                results.add(status);
                continue;
            }

            if (!info.getDownloadUrl().isPresent() && !info.isNpmPackage()) {
                fireInfo(info.getId(), "No download URL configured. Please install manually.");
                results.add(status);
                continue;
            }

            try {
                fireInfo(info.getId(), "Downloading...");
                downloadAndInstall(info);
                results.add(getStatus(info));
            } catch (IOException e) {
                fireError(info.getId(), "Failed to download: " + e.getMessage(), e);
                results.add(getStatus(info));
            }
        }

        return results;
    }

    /**
     * Downloads all missing binaries in the background using a virtual thread.
     *
     * @return a future-like {@link DownloadSession} that can be used to monitor
     *         progress and wait for completion.
     */
    public DownloadSession downloadAllMissingAsync() {
        DownloadSession session = new DownloadSession(this);
        session.start();
        return session;
    }

    // ====================================================================
    //  Internal: download
    // ====================================================================

    private void downloadFile(String fileUrl, Path target, String binaryName) throws IOException {
        Files.createDirectories(target.getParent());

        URL url = URI.create(fileUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);

        // Handle redirects
        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            conn = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
        }

        long totalBytes = conn.getContentLengthLong();
        fireDownloadStart(binaryName, totalBytes);

        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                Files.write(target, java.util.Arrays.copyOf(buffer, bytesRead),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                downloaded += bytesRead;
                fireProgress(binaryName, downloaded, totalBytes);
            }

            fireDownloadComplete(binaryName, downloaded);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Detects the archive type of a downloaded file.
     *
     * <p>Prefers the file extension; falls back to magic-byte inspection for
     * URLs that redirect to archives without a recognizable extension
     * (e.g. Adoptium's {@code .../eclipse} endpoint).
     *
     * @param file     the downloaded file.
     * @param fileName the file name derived from the URL.
     * @param url      the original download URL.
     * @return {@code "zip"}, {@code "tar.gz"}, {@code "tar.xz"}, or {@code null}
     *         if the file is not a recognized archive.
     * @throws IOException if the file cannot be read.
     */
    private String detectArchiveType(Path file, String fileName, String url) throws IOException {
        // Check extension first
        if (fileName.endsWith(".zip") || fileName.endsWith(".jar_") || url.contains("vnu.jar_")) {
            return "zip";
        }
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            return "tar.gz";
        }
        if (fileName.endsWith(".tar.xz")) {
            return "tar.xz";
        }
        // Fall back to magic bytes
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.read(header);
            if (read < 4) return null;
        }
        // ZIP: 50 4B 03 04
        if (header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04) {
            return "zip";
        }
        // GZIP: 1F 8B
        if (header[0] == (byte) 0x1F && header[1] == (byte) 0x8B) {
            return "tar.gz";
        }
        // XZ: FD 37
        if (header[0] == (byte) 0xFD && header[1] == 0x37) {
            return "tar.xz";
        }
        return null;
    }

    // ====================================================================
    //  Internal: extraction
    // ====================================================================

    private void extractZip(Path archive, Path destDir) throws IOException {
        fireInfo(null, "Extracting ZIP: " + archive.getFileName());
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new BufferedInputStream(Files.newInputStream(archive)))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void extractTarGz(Path archive, Path destDir) throws IOException {
        fireInfo(null, "Extracting TAR.GZ: " + archive.getFileName());
        try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(
                Files.newInputStream(archive));
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
                     new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzis)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Tar entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(tis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void extractTarXz(Path archive, Path destDir) throws IOException {
        fireInfo(null, "Extracting TAR.XZ: " + archive.getFileName());
        try (org.apache.commons.compress.compressors.xz.XZCompressorInputStream xzis =
                     new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(
                             Files.newInputStream(archive));
             org.apache.commons.compress.archivers.tar.TarArchiveInputStream tis =
                     new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzis)) {
            org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Tar entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(tis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ====================================================================
    //  Internal: path resolution & version detection
    // ====================================================================

    private Path resolveFromPath(BinaryInfo info) {
        String cmd = isWindows() ? info.getWindowsExecutable() : info.getCommandName();

        // Try direct which/command lookup
        try {
            String whichCmd = isWindows() ? "where" : "which";
            ProcessBuilder pb = new ProcessBuilder(whichCmd, cmd)
                    .redirectErrorStream(true);
            Process p = pb.start();
            List<String> paths = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    paths.add(line);
                }
            }
            int exitCode = p.waitFor();
            // Only accept the result if the exit code is 0 and paths were found
            if (exitCode == 0 && !paths.isEmpty()) {
                // Collect all valid executable candidates
                List<Path> candidates = new ArrayList<>();
                for (String path : paths) {
                    if (path == null || path.isBlank()
                            || path.startsWith("INFO:") || path.contains("Could not find")) {
                        continue;
                    }
                    Path resolved = Path.of(path.trim());
                    if (Files.isExecutable(resolved)) {
                        candidates.add(resolved);
                    }
                }
                if (!candidates.isEmpty()) {
                    if (candidates.size() == 1) {
                        return candidates.get(0);
                    }
                    // Multiple candidates: prefer the one with the highest version
                    Path best = candidates.get(0);
                    String bestVersion = detectVersion(info, best);
                    for (int i = 1; i < candidates.size(); i++) {
                        String v = detectVersion(info, candidates.get(i));
                        if (v != null && (bestVersion == null
                                || compareVersions(v, bestVersion) > 0)) {
                            best = candidates.get(i);
                            bestVersion = v;
                        }
                    }
                    return best;
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("Failed to resolve '{}' via {} command: {}", cmd,
                    isWindows() ? "where" : "which", e.getMessage());
        }

        // For JAR files, check if java -jar vnu.jar --version works
        if ("vnu".equals(info.getId())) {
            Path jarPath = installDir.resolve("vnu.jar");
            if (Files.isRegularFile(jarPath)) {
                return jarPath;
            }
        }

        return null;
    }

    private String detectVersion(BinaryInfo info, Path resolvedPath) {
        // Special handling for JAR files
        if ("vnu".equals(info.getId())) {
            return detectVnuVersion(resolvedPath);
        }

        String[] versionArgs = getVersionCommand(info, resolvedPath);
        if (versionArgs == null) return null;

        try {
            ProcessBuilder pb = new ProcessBuilder(versionArgs)
                    .redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + "\n" + b);
            }
            int exitCode = p.waitFor();

            Matcher matcher = SEMVER_PATTERN.matcher(output);
            if (matcher.find()) {
                String major = matcher.group(1);
                String minor = matcher.group(2);
                String patch = matcher.group(3);
                if (minor == null) { return major + ".0.0"; }
                return major + "." + minor + (patch != null ? "." + patch : ".0");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("Failed to detect version for {}: {}", info.getId(), e.getMessage());
        }
        return null;
    }

    private String detectVnuVersion(Path jarPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-jar", jarPath.toString(), "--version")
                    .redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + "\n" + b);
            }
            p.waitFor();

            Matcher matcher = SEMVER_PATTERN.matcher(output);
            if (matcher.find()) {
                String major = matcher.group(1);
                String minor = matcher.group(2);
                String patch = matcher.group(3);
                if (minor == null) { return major + ".0.0"; }
                return major + "." + minor + (patch != null ? "." + patch : ".0");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("Failed to detect vnu version: {}", e.getMessage());
        }
        return null;
    }

    private String[] getVersionCommand(BinaryInfo info, Path resolvedPath) {
        String path = resolvedPath.toString();
        switch (info.getId()) {
            case "node":
                return new String[]{path, "--version"};
            case "javac":
                return new String[]{path, "-version"};
            case "tsc":
                return new String[]{path, "--version"};
            case "python":
                return new String[]{path, "--version"};
            case "php":
                return new String[]{path, "-v"};
            case "stylelint":
                return new String[]{path, "--version"};
            default:
                return null;
        }
    }

    // ====================================================================
    //  Internal: helpers
    // ====================================================================

    private void setExecutablePermissions(BinaryInfo info) {
        if (isWindows()) return;
        Path path = info.getInstalledPath(installDir);
        if (Files.exists(path)) {
            try {
                Set<PosixFilePermission> perms = new HashSet<>(Files.getPosixFilePermissions(path));
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(path, perms);
            } catch (IOException | UnsupportedOperationException e) {
                log.debug("Could not set executable permissions on {}: {}", path, e.getMessage());
            }
        }
    }

    private Path findInSubdirectory(BinaryInfo info) {
        if ("javac".equals(info.getId())) {
            // JDK archives extract into jdk-XX+XX/ or jdk-XX.X.X+XX/
            // The javac executable is at <subdir>/bin/javac.exe (Windows) or <subdir>/bin/javac (Unix)
            String execName = isWindows() ? info.getWindowsExecutable() : info.getCommandName();
            try (var stream = Files.list(installDir)) {
                return stream.filter(p -> Files.isDirectory(p)
                                && p.getFileName().toString().startsWith("jdk"))
                        .map(d -> d.resolve("bin").resolve(execName))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                return null;
            }
        }
        if ("node".equals(info.getId())) {
            // Node archives extract into node-vXX.XX.XX-{os}-{arch}/
            try (var stream = Files.list(installDir)) {
                return stream.filter(p -> Files.isDirectory(p)
                                && p.getFileName().toString().startsWith("node-"))
                        .map(d -> d.resolve(isWindows() ? info.getWindowsExecutable() : "bin/" + info.getCommandName()))
                        .filter(Files::isRegularFile)
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                return null;
            }
        }
        if ("php".equals(info.getId())) {
            Path phpExe = installDir.resolve(info.getWindowsExecutable());
            if (Files.isRegularFile(phpExe)) return phpExe;
            Path phpBin = installDir.resolve("bin").resolve(info.getCommandName());
            if (Files.isRegularFile(phpBin)) return phpBin;
        }
        return null;
    }

    private void reorganizeExtracted(BinaryInfo info, Path foundExecutable) throws IOException {
        // For Node.js and JDK archives: move contents from subdirectory to installDir
        if ("node".equals(info.getId()) || "javac".equals(info.getId())) {
            try (var stream = Files.list(installDir)) {
                String prefix = "javac".equals(info.getId()) ? "jdk-" : "node-";
                Optional<Path> nodeDir = stream.filter(p -> Files.isDirectory(p)
                                && p.getFileName().toString().startsWith(prefix))
                        .findFirst();
                if (nodeDir.isPresent()) {
                    Path src = nodeDir.get();
                    Files.walkFileTree(src, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Path target = installDir.resolve(src.relativize(dir));
                            Files.createDirectories(target);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.move(file, installDir.resolve(src.relativize(file)),
                                    StandardCopyOption.REPLACE_EXISTING);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    // Remove empty subdirectory
                    deleteRecursively(src);
                }
            }
        }
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    /**
     * Compares two semver version strings.
     *
     * @return negative if v1 &lt; v2, zero if equal, positive if v1 &gt; v2.
     */
    static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (p1 != p2) return Integer.compare(p1, p2);
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        // Strip non-numeric suffixes like "-beta", "+build"
        String numeric = part.replaceAll("[^0-9].*", "");
        try {
            return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ====================================================================
    //  Internal: event dispatching
    // ====================================================================

    private void fireDownloadStart(String binaryName, long totalBytes) {
        for (DownloadProgressListener l : listeners) {
            try { l.onDownloadStart(binaryName, totalBytes); } catch (Exception e) {
                log.warn("Listener error on onDownloadStart", e);
            }
        }
    }

    private void fireProgress(String binaryName, long bytesDownloaded, long totalBytes) {
        for (DownloadProgressListener l : listeners) {
            try { l.onProgress(binaryName, bytesDownloaded, totalBytes); } catch (Exception e) {
                log.warn("Listener error on onProgress", e);
            }
        }
    }

    private void fireDownloadComplete(String binaryName, long totalBytes) {
        for (DownloadProgressListener l : listeners) {
            try { l.onDownloadComplete(binaryName, totalBytes); } catch (Exception e) {
                log.warn("Listener error on onDownloadComplete", e);
            }
        }
    }

    private void fireExtractStart(String binaryName, String archivePath) {
        for (DownloadProgressListener l : listeners) {
            try { l.onExtractStart(binaryName, archivePath); } catch (Exception e) {
                log.warn("Listener error on onExtractStart", e);
            }
        }
    }

    private void fireExtractComplete(String binaryName, String extractedTo) {
        for (DownloadProgressListener l : listeners) {
            try { l.onExtractComplete(binaryName, extractedTo); } catch (Exception e) {
                log.warn("Listener error on onExtractComplete", e);
            }
        }
    }

    private void fireNpmInstallStart(String packageName) {
        for (DownloadProgressListener l : listeners) {
            try { l.onNpmInstallStart(packageName); } catch (Exception e) {
                log.warn("Listener error on onNpmInstallStart", e);
            }
        }
    }

    private void fireNpmInstallComplete(String packageName, boolean success) {
        for (DownloadProgressListener l : listeners) {
            try { l.onNpmInstallComplete(packageName, success); } catch (Exception e) {
                log.warn("Listener error on onNpmInstallComplete", e);
            }
        }
    }

    private void fireError(String binaryName, String message, Throwable error) {
        for (DownloadProgressListener l : listeners) {
            try { l.onError(binaryName, message, error); } catch (Exception e) {
                log.warn("Listener error on onError", e);
            }
        }
    }

    private void fireInfo(String binaryName, String message) {
        for (DownloadProgressListener l : listeners) {
            try { l.onInfo(binaryName, message); } catch (Exception e) {
                log.warn("Listener error on onInfo", e);
            }
        }
        log.info("[{}] {}", binaryName != null ? binaryName : "BinaryManager", message);
    }
}
