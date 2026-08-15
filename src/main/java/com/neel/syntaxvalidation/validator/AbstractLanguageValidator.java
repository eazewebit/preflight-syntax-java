package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared scaffolding for validators that check syntax by invoking an external
 * command-line tool against a temporary file.
 *
 * <p>This base class implements the <em>Template Method</em> pattern: it owns
 * the common algorithm &mdash; binary resolution, temporary-file creation,
 * process execution and graceful error handling &mdash; while delegating the
 * language-specific steps to concrete subclasses:
 * <ul>
 *   <li>{@link #getFileExtension()} &ndash; the temp-file extension;</li>
 *   <li>{@link #buildCommand(String, Path)} &ndash; the command line;</li>
 *   <li>{@link #parseOutput(ProcessResult, Path)} &ndash; interpreting the tool output;</li>
 *   <li>{@link #binaryNotFoundMessage()} &ndash; the error shown when the tool is missing;</li>
 *   <li>{@link #validateWithBuiltInEngine(String)} &ndash; optional built-in engine fallback.</li>
 * </ul>
 *
 * <p><b>Binary resolution strategy.</b> When a preferred binary path is supplied
 * it takes precedence; otherwise the {@link BinaryResolver} falls back to the
 * system {@code PATH}. If a {@link BinaryManager} is wired in via the
 * {@link #AbstractLanguageValidator(String, String, BinaryManager, ProcessExecutor)}
 * constructor, the resolver delegates to it before checking {@code PATH},
 * enabling automatic download and caching of managed binaries.
 *
 * <p>If neither is available, validation falls back to the built-in engine
 * via {@link #validateWithBuiltInEngine(String)}.
 *
 * <p><b>Validation order (Binary-First):</b>
 * <ol>
 *   <li>Phase 1 - External binary (when available)</li>
 *   <li>Phase 2 - Built-in engine (fallback when binary unavailable or fails)</li>
 * </ol>
 */
public abstract class AbstractLanguageValidator implements LanguageValidator {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String preferredBinaryPath;
    private final String binaryName;
    private final BinaryResolver binaryResolver;
    private final ProcessExecutor processExecutor;

    /**
     * Creates a validator using default collaborators and the default process timeout.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null} to use the {@code PATH}.
     * @param binaryName          the bare tool name searched on {@code PATH} (e.g. {@code "node"}).
     */
    protected AbstractLanguageValidator(String preferredBinaryPath, String binaryName) {
        this(preferredBinaryPath, binaryName, (BinaryManager) null, new ProcessExecutor());
    }

    /**
     * Creates a validator with explicit collaborators, primarily for testing.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryName          the bare tool name searched on {@code PATH}.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     */
    protected AbstractLanguageValidator(String preferredBinaryPath, String binaryName,
                                        BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this.preferredBinaryPath = preferredBinaryPath;
        this.binaryName = Objects.requireNonNull(binaryName, "binaryName");
        this.binaryResolver = Objects.requireNonNull(binaryResolver, "binaryResolver");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} for managed binary
     * resolution (download, cache, version-check).
     *
     * <p>The {@link BinaryResolver} is constructed internally with the given
     * manager, so callers do not need to wire it manually.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryName          the bare tool name searched on {@code PATH}.
     * @param binaryManager       the binary manager (may be {@code null} for
     *                            PATH-only resolution).
     * @param processExecutor     the process executor to use.
     */
    protected AbstractLanguageValidator(String preferredBinaryPath, String binaryName,
                                        BinaryManager binaryManager, ProcessExecutor processExecutor) {
        this.preferredBinaryPath = preferredBinaryPath;
        this.binaryName = Objects.requireNonNull(binaryName, "binaryName");
        this.binaryResolver = new BinaryResolver(binaryManager);
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
    }

    /**
     * Resolves the tool binary according to the configured strategy.
     *
     * @return the resolved path as a string, or empty if the tool is unavailable.
     */
    protected final Optional<String> resolveBinary() {
        return binaryResolver.resolve(preferredBinaryPath, binaryName);
    }

    /**
     * Resolves the tool binary as a {@link Path}.
     *
     * <p>This method leverages the full {@link BinaryManager} pipeline when
     * available, including automatic download and caching.
     *
     * @return the resolved {@link Path}, or empty if the tool is unavailable.
     */
    protected final Optional<Path> resolveBinaryPath() {
        return binaryResolver.resolvePath(preferredBinaryPath, binaryName);
    }

    /**
     * Returns the underlying {@link BinaryResolver}.
     *
     * @return the binary resolver (never {@code null}).
     */
    protected final BinaryResolver getBinaryResolver() {
        return binaryResolver;
    }

    /**
     * Returns the underlying {@link ProcessExecutor}.
     *
     * @return the process executor (never {@code null}).
     */
    protected final ProcessExecutor getProcessExecutor() {
        return processExecutor;
    }

    /**
     * Deletes a file or directory silently, ignoring any errors.
     * If the path is a directory, its immediate children are deleted first.
     *
     * @param path the file or directory to delete.
     */
    protected void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(child -> {
                        try {
                            Files.deleteIfExists(child);
                        } catch (IOException ignored) {
                            // best-effort cleanup
                        }
                    });
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup of temp files
        }
    }

    /**
     * Logs validation phase information for debugging and monitoring.
     *
     * @param language the language being validated
     * @param phase    the validation phase (1 or 2)
     * @param message  the log message
     */
    protected void logValidationPhase(String language, int phase, String message) {
        log.debug("[PHASE-{}] {} validation: {}", phase, language, message);
    }

    /**
     * Logs a warning when falling back to built-in engine.
     *
     * @param language the language being validated
     * @param reason   the reason for fallback
     */
    protected void logBuiltInEngineFallback(String language, String reason) {
        log.warn("[PHASE-2-FALLBACK] {} validation: {}", language, reason);
    }

    /**
     * Hook method for subclasses to prepare the temporary directory before
     * the source file is written. This is useful for validators that need
     * additional files in the temp directory (e.g., configuration files).
     *
     * <p>Default implementation does nothing. Subclasses can override this
     * to create configuration files, setup scripts, or other resources
     * needed by the external tool.
     *
     * @param tempDir the temporary directory that was created
     * @throws IOException if preparation fails
     */
    protected void prepareTempDirectory(Path tempDir) throws IOException {
        // No-op by default
    }

    /**
     * Two-phase validation: Binary-first approach.
     *
     * <p>The content is written to a temporary file bearing the supplied
     * {@code fileName} inside a temporary directory. The directory and all
     * its contents are deleted after validation completes.
     *
     * <ol>
     *   <li>Phase 1 - External binary (when available)</li>
     *   <li>Phase 2 - Built-in engine (fallback when binary unavailable or fails)</li>
     * </ol>
     *
     * @param content  the source text to check; {@code null} is treated as empty.
     * @param fileName the file name to use for the temporary file.
     */
    @Override
    public ValidationResult validate(String content, String fileName) {
        String safeContent = content == null ? "" : content;

        // Phase 1 - Binary validation (check availability first)
        Optional<String> binary = resolveBinary();
        if (binary.isPresent()) {
            String binaryPath = binary.get();
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [PHASE-1-BINARY] {} validation: Using BINARY", getLanguage().name());
            log.info("║ Binary name: {}", binaryName);
            log.info("║ Binary path: {}", binaryPath);
            log.info("╚══════════════════════════════════════════════════════════════");
            Path tempDir = null;
            try {
                tempDir = Files.createTempDirectory("syntax-check-");
                prepareTempDirectory(tempDir);
                Path tempFile = tempDir.resolve(fileName);
                Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
                List<String> command = buildCommand(binaryPath, tempFile);
                log.debug("[PHASE-1-BINARY] Executing command: {}", command);
                ProcessResult result = processExecutor.execute(command);

                ValidationResult binaryResult = parseOutput(result, tempFile);
                if (binaryResult.isValid()) {
                    log.info("[PHASE-1-BINARY] ✅ {} validation: Binary validation PASSED", getLanguage().name());
                    return binaryResult;
                }
                // Binary found errors, return them
                log.info("[PHASE-1-BINARY] ❌ {} validation: Binary found {} errors",
                         getLanguage().name(), binaryResult.getErrors().size());
                return binaryResult;
            } catch (IOException e) {
                log.error("[PHASE-1-BINARY] ❌ {} validation: Binary I/O error, falling back to built-in engine",
                         getLanguage().name(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[PHASE-1-BINARY] ❌ {} validation: Binary validation interrupted, falling back to built-in engine",
                        getLanguage().name());
            } catch (Exception e) {
                log.warn("[PHASE-1-BINARY] ❌ {} validation: Binary execution failed, falling back to built-in engine",
                        getLanguage().name(), e);
            } finally {
                deleteQuietly(tempDir);
            }
        } else {
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [PHASE-1-BINARY] {} validation: Binary NOT FOUND", getLanguage().name());
            log.info("║ Binary name: {}", binaryName);
            log.info("║ Searching: System PATH and BinaryManager");
            log.info("╚══════════════════════════════════════════════════════════════");
        }

        // Phase 2 - Built-in engine fallback
        ValidationResult builtInResult = validateWithBuiltInEngine(safeContent);
        if (builtInResult != null) {
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [PHASE-2-FALLBACK] {} validation: Using BUILT-IN ENGINE", getLanguage().name());
            log.info("║ Reason: {}", binary.isPresent() ? "Binary execution failed" : "Binary not available");
            log.info("╚══════════════════════════════════════════════════════════════");
            return builtInResult;
        }

        // No built-in engine available
        logBuiltInEngineFallback(getLanguage().name(), "Binary not available and no built-in engine");
        return ValidationResult.invalid(binaryNotFoundMessage());
    }

    /**
     * Two-phase validation using a default filename derived from
     * {@link #getFileExtension()}.
     *
     * <p>Delegates to {@link #validate(String, String)} with a filename of
     * {@code "source" + getFileExtension()}.
     */
    @Override
    public ValidationResult validate(String content) {
        return validate(content, "source" + getFileExtension());
    }

    /**
     * Returns the file extension (including the dot) used for temporary files.
     *
     * @return e.g. {@code ".java"}, {@code ".py"}.
     */
    protected abstract String getFileExtension();

    /**
     * Builds the command-line invocation for the external tool.
     *
     * @param binaryPath the resolved path to the tool binary.
     * @param tempFile   the temporary file containing the content to validate.
     * @return the command as a list of strings.
     */
    protected abstract List<String> buildCommand(String binaryPath, Path tempFile);

    /**
     * Interprets the captured tool output, producing the final result.
     *
     * @param result   the captured {@link ProcessResult}.
     * @param tempFile the temporary file that was validated.
     * @return a structured {@link ValidationResult}.
     */
    protected abstract ValidationResult parseOutput(ProcessResult result, Path tempFile);

    /**
     * Built-in engine validation (Phase 2 fallback).
     *
     * <p>Default implementation returns {@code null}, indicating no built-in
     * engine is available. Subclasses with built-in engines should override
     * this method.
     *
     * @param content the content to validate
     * @return ValidationResult from the built-in engine, or null if no built-in engine
     */
    protected ValidationResult validateWithBuiltInEngine(String content) {
        return null;
    }

    /**
     * @return the explanation returned when the validation tool cannot be located.
     */
    protected abstract String binaryNotFoundMessage();
}
