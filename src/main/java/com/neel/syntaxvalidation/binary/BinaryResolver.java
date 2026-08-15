package com.neel.syntaxvalidation.binary;

import com.neel.syntaxvalidation.binary.manager.BinaryInfo;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the path to an external validation tool binary.
 *
 * <p>This class acts as a bridge between the validator layer and the
 * binary-management infrastructure.  Resolution follows a strict priority:
 * <ol>
 *   <li><b>Preferred path</b> - an explicitly supplied path is validated first.</li>
 *   <li><b>{@link BinaryManager}</b> - when a {@link BinaryManager} is
 *       wired in, the resolver delegates to
 *       {@link BinaryManager#getBinaryPath(BinaryInfo)} which can
 *       auto-download, cache, and verify managed binaries.</li>
 *   <li><b>System {@code PATH}</b> - as a final fallback, the resolver
 *       searches the host's {@code PATH} environment variable.</li>
 * </ol>
 *
 * <p>Two resolution APIs are provided:
 * <ul>
 *   <li>{@link #resolve(String, String)} - legacy {@code Optional&lt;String&gt;}
 *       for backward-compatible call-sites.</li>
 *   <li>{@link #resolvePath(String, String)} - preferred {@code Optional&lt;Path&gt;}
 *       that returns a {@link Path} and leverages the full
 *       {@link BinaryManager} pipeline.</li>
 * </ul>
 *
 * <p>Instances can be created in three ways:
 * <ul>
 *   <li>No-arg constructor - PATH-only resolution (legacy behaviour).</li>
 *   <li>{@link #BinaryResolver(BinaryManager)} - full managed resolution.</li>
 *   <li>{@link #BinaryResolver(String, String)} - with a preferred path and
 *       binary name (legacy behaviour).</li>
 * </ul>
 *
 * <p>This class is <b>immutable</b> and <b>thread-safe</b>.
 *
 * @since 1.0.0
 */
public class BinaryResolver {

    private static final Logger log = LoggerFactory.getLogger(BinaryResolver.class);

    /**
     * The optional {@link BinaryManager} used for managed binary resolution.
     * When present, the resolver delegates to it before falling back to the
     * system {@code PATH}.
     */
    private final BinaryManager binaryManager;

    /**
     * Creates a resolver that searches only the system {@code PATH}.
     * Equivalent to {@code new BinaryResolver(null)}.
     */
    public BinaryResolver() {
        this(null);
    }

    /**
     * Creates a resolver backed by the supplied {@link BinaryManager}.
     *
     * <p>When a binary is requested, the resolver first consults the manager
     * (which may auto-download the binary), then falls back to the system
     * {@code PATH} if the manager has no entry for the requested binary.
     *
     * @param binaryManager the binary manager (may be {@code null} for
     *                      PATH-only resolution).
     */
    public BinaryResolver(BinaryManager binaryManager) {
        this.binaryManager = binaryManager;
    }

    /**
     * Legacy resolution returning a String path.
     *
     * @param preferredPath optional explicit path provided by the caller.
     * @param binaryName    the canonical name of the binary to resolve.
     * @return the resolved path, or {@link Optional#empty()} if not found.
     */
    public Optional<String> resolve(String preferredPath, String binaryName) {
        return resolvePath(preferredPath, binaryName).map(Path::toString);
    }

    /**
     * Resolves the binary path with full {@link BinaryManager} support.
     *
     * <p>This is the <b>preferred</b> resolution method.  It returns a
     * {@link Path} which can be used directly by validators.
     *
     * @param preferredPath an explicit binary path (may be {@code null}).
     * @param binaryName    the bare binary name (e.g. {@code "node"}).
     * @return the resolved {@link Path}, or {@link Optional#empty()} if
     *         the binary cannot be found.
     */
    public Optional<Path> resolvePath(String preferredPath, String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return Optional.empty();
        }

        // 1. Preferred path takes absolute precedence.
        if (preferredPath != null && !preferredPath.isBlank()) {
            Path p = Path.of(preferredPath);
            if (Files.isExecutable(p) || Files.exists(p)) {
                log.info("[BINARY-RESOLVER] ✅ Resolved binary '{}' via preferred path: {}", binaryName, p.toAbsolutePath());
                return Optional.of(p);
            }
            // On Windows, try appending .exe
            if (isWindows()) {
                Path withExe = Path.of(preferredPath + ".exe");
                if (Files.exists(withExe)) {
                    log.info("[BINARY-RESOLVER] ✅ Resolved binary '{}' via preferred path (with .exe): {}", binaryName, withExe.toAbsolutePath());
                    return Optional.of(withExe);
                }
            }
        }

        // 2. Delegate to BinaryManager (managed download + cache).
        if (binaryManager != null) {
            BinaryInfo info = mapBinaryName(binaryName);
            if (info != null) {
                Optional<Path> managed = binaryManager.getBinaryPath(info);
                if (managed.isPresent()) {
                    log.info("[BINARY-RESOLVER] ✅ Resolved binary '{}' via BinaryManager: {}", binaryName, managed.get().toAbsolutePath());
                    return managed;
                }
            }
        }

        // 3. Fallback to system PATH.
        Optional<String> pathOnPath = searchPath(binaryName);
        if (pathOnPath.isPresent()) {
            Path resolved = Path.of(pathOnPath.get());
            log.info("[BINARY-RESOLVER] ✅ Resolved binary '{}' via system PATH: {}", binaryName, resolved.toAbsolutePath());
            return Optional.of(resolved);
        }

        log.info("[BINARY-RESOLVER] ❌ Binary '{}' not found via any resolution strategy", binaryName);
        return Optional.empty();
    }

    /**
     * Returns the underlying {@link BinaryManager}, if one was provided.
     *
     * @return the binary manager, or {@link Optional#empty()}.
     */
    public Optional<BinaryManager> getBinaryManager() {
        return Optional.ofNullable(binaryManager);
    }

    // ====================================================================
    //  Internal: binary-name -> BinaryInfo mapping
    // ====================================================================

    /**
     * Maps a bare binary name to the corresponding {@link BinaryInfo}
     * constant managed by {@link BinaryManager}.
     *
     * @param binaryName the bare name (e.g. {@code "javac"}, {@code "node"}).
     * @return the matching {@link BinaryInfo}, or {@code null} if no
     *         mapping exists.
     */
    private static BinaryInfo mapBinaryName(String binaryName) {
        if (binaryName == null) return null;
        return switch (binaryName.toLowerCase()) {
            case "javac", "java" -> BinaryInfo.JAVAC;
            case "node", "nodejs" -> BinaryInfo.NODE;
            case "tsc"           -> BinaryInfo.TSC;
            case "python", "python3", "py" -> BinaryInfo.PYTHON;
            case "php"           -> BinaryInfo.PHP;
            case "vnu", "vnu.jar" -> BinaryInfo.VNU;
            case "stylelint"     -> BinaryInfo.STYLELINT;
            default -> null;
        };
    }

    // ====================================================================
    //  Internal: system PATH search
    // ====================================================================

    /**
     * Searches the system {@code PATH} for the given binary name.
     *
     * @param binaryName the bare binary name.
     * @return the absolute path, or {@link Optional#empty()}.
     */
    private Optional<String> searchPath(String binaryName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }

        String separator = File.pathSeparator;
        for (String dir : pathEnv.split(separator)) {
            Path candidate = Path.of(dir, binaryName);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath().toString());
            }
            // On Windows, try common extensions.
            if (isWindows()) {
                for (String ext : new String[]{".exe", ".cmd", ".bat"}) {
                    Path withExt = Path.of(dir, binaryName + ext);
                    if (Files.isExecutable(withExt)) {
                        return Optional.of(withExt.toAbsolutePath().toString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}