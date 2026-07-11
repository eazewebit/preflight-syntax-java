package com.neel.syntaxvalidation.binary;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the path to an external validation tool's binary using a two-tier
 * strategy:
 * <ol>
 *   <li>If a <em>preferred path</em> is supplied, it is used when it points to an
 *       existing, executable file.</li>
 *   <li>Otherwise the resolver searches the system {@code PATH} for the supplied
 *       binary name, accounting for platform-specific executable suffixes
 *       (e.g. {@code .exe}, {@code .cmd}, {@code .bat} on Windows).</li>
 * </ol>
 *
 * <p>The resolver is stateless and safe to share across validators and threads.
 */
public class BinaryResolver {

    private static final boolean WINDOWS =
            File.separatorChar == '\\';

    private static final String[] WINDOWS_EXTENSIONS = {".exe", ".cmd", ".bat", ""};
    private static final String[] UNIX_EXTENSIONS = {""};

    /**
     * Resolves a binary path.
     *
     * @param preferredPath an explicit path to the binary, or {@code null}/{@code blank}
     *                      to skip straight to the {@code PATH} search.
     * @param binaryName    the bare binary name to look for on {@code PATH} (e.g. {@code "node"}).
     * @return the resolved path, or {@link Optional#empty()} if the binary cannot be found.
     */
    public Optional<String> resolve(String preferredPath, String binaryName) {
        if (preferredPath != null && !preferredPath.isBlank()) {
            Path candidate = Path.of(preferredPath.trim());
            if (isExecutable(candidate)) {
                return Optional.of(candidate.toString());
            }
        }
        return findOnPath(binaryName);
    }

    private Optional<String> findOnPath(String binaryName) {
        if (binaryName == null || binaryName.isBlank()) {
            return Optional.empty();
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return Optional.empty();
        }

        String[] extensions = WINDOWS ? WINDOWS_EXTENSIONS : UNIX_EXTENSIONS;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            Path dirPath = Path.of(dir);
            for (String ext : extensions) {
                Path candidate = dirPath.resolve(binaryName + ext);
                if (isExecutable(candidate)) {
                    return Optional.of(candidate.toString());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks that a path points to an existing file that can be executed.
     * <p>On Windows, where the execute bit is not meaningful, this falls back to
     * checking that the path is a regular file.
     */
    private static boolean isExecutable(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        return WINDOWS || Files.isExecutable(path);
    }
}
