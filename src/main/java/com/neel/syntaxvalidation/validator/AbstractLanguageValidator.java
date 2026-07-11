package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
 *   <li>{@link #binaryNotFoundMessage()} &ndash; the error shown when the tool is missing.</li>
 * </ul>
 *
 * <p><b>Binary resolution strategy.</b> When a preferred binary path is supplied
 * it takes precedence; otherwise the {@link BinaryResolver} falls back to the
 * system {@code PATH}. If neither is available, validation fails gracefully with
 * the message produced by {@link #binaryNotFoundMessage()}.
 */
public abstract class AbstractLanguageValidator implements LanguageValidator {

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
        this(preferredBinaryPath, binaryName, new BinaryResolver(), new ProcessExecutor());
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
     * Resolves the tool binary according to the configured strategy.
     *
     * @return the resolved path, or empty if the tool is unavailable.
     */
    protected final Optional<String> resolveBinary() {
        return binaryResolver.resolve(preferredBinaryPath, binaryName);
    }

    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.invalid(binaryNotFoundMessage());
        }

        Path tempFile = null;
        try {
            tempFile = createTempFile();
            Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
            List<String> command = buildCommand(binary.get(), tempFile);
            ProcessResult result = processExecutor.execute(command);
            return parseOutput(result, tempFile);
        } catch (IOException e) {
            return ValidationResult.invalid("Validation failed due to an I/O error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ValidationResult.invalid("Validation was interrupted before it could complete.");
        } finally {
            if (tempFile != null) {
                deleteQuietly(tempFile);
            }
        }
    }

    /**
     * @return the file extension (including the dot) for the temporary file, e.g. {@code ".js"}.
     */
    protected abstract String getFileExtension();

    /**
     * Builds the command line used to invoke the validation tool.
     *
     * @param binaryPath the resolved binary path.
     * @param tempFile   the temporary file holding the content to validate.
     * @return the command and its arguments.
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
     * @return the explanation returned when the validation tool cannot be located.
     */
    protected abstract String binaryNotFoundMessage();

    /**
     * Creates a uniquely named temporary file bearing this validator's extension.
     */
    protected Path createTempFile() throws IOException {
        return Files.createTempFile("syntax-check-", getFileExtension());
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup of temp files
        }
    }
}
