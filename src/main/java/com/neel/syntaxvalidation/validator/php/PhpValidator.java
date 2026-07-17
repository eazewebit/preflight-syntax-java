package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates PHP syntax using a two-phase approach.
 *
 * <h2>Phase 1 &mdash; Built-in PHP 8.x syntax engine</h2>
 * A pure-Java {@link PhpSyntaxEngine} runs first, with zero external
 * dependencies. It tokenises the source and applies a suite of structural
 * and grammar checks covering namespaces, traits, generators, match
 * expressions, named arguments, constructor promotion, union/intersection/DNF
 * types, enums, readonly properties, and much more. If the engine detects
 * errors they are returned immediately.
 *
 * <h2>Phase 2 &mdash; PHP CLI deep analysis</h2>
 * When {@code php} (version 8.0+) is available on the system, the full
 * {@code php -l} pipeline runs as a second pass, catching subtler
 * syntax errors that a pure-Java engine cannot reasonably detect.
 *
 * <p>If PHP CLI is <em>not</em> available, the built-in engine's clean
 * result stands on its own, providing meaningful syntax validation in
 * any environment.
 *
 * <p>Both the {@link BinaryResolver} and {@link ProcessExecutor} collaborators
 * are injectable to ease unit testing.
 *
 * @since 1.1.0
 */
public class PhpValidator extends AbstractLanguageValidator {

    /** The bare name of the PHP binary searched on the {@code PATH}. */
    public static final String BINARY_NAME = "php";

    /** Maximum major version required for external PHP binary. */
    private static final int MIN_PHP_MAJOR = 8;

    private static final PhpOutputParser PARSER = new PhpOutputParser();

    /**
     * Creates a validator that resolves {@code php} from the system {@code PATH}.
     */
    public PhpValidator() {
        this(null);
    }

    /**
     * @param preferredBinaryPath an explicit path to the {@code php} binary, or
     *                            {@code null} to resolve from the {@code PATH}.
     */
    public PhpValidator(String preferredBinaryPath) {
        super(preferredBinaryPath, BINARY_NAME);
    }

    /**
     * Full constructor used by the {@link com.neel.syntaxvalidation.validator.ValidatorFactory}
     * and tests.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryResolver      the binary resolver.
     * @param processExecutor     the process executor.
     */
    public PhpValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME,
                Objects.requireNonNull(binaryResolver, "binaryResolver"),
                Objects.requireNonNull(processExecutor, "processExecutor"));
    }

    @Override
    public Language getLanguage() {
        return Language.PHP;
    }

    /**
     * Two-phase validation.
     *
     * <ol>
     *   <li>Run the built-in {@link PhpSyntaxEngine}. If it reports
     *       errors, return them immediately &mdash; no external process is
     *       needed.</li>
     *   <li>If the engine passes and PHP CLI is available, delegate to
     *       {@code super.validate()} (which invokes {@code php -l}) for
     *       deeper analysis.</li>
     *   <li>If PHP CLI is unavailable, the engine's clean result stands.</li>
     * </ol>
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1 — built-in PHP 8.x syntax engine (always runs, zero external deps)
        ValidationResult engineResult = PhpSyntaxEngine.validateStatic(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2 — PHP CLI deep analysis (when available)
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            // PHP CLI unavailable — the engine's clean result stands.
            return ValidationResult.valid(
                    "PHP syntax is valid (validated by the built-in PHP 8.x syntax engine; "
                            + "PHP CLI not available for deeper analysis).");
        }

        // PHP CLI available — delegate to the full pipeline for maximum coverage.
        return super.validate(safeContent);
    }

    @Override
    protected String getFileExtension() {
        return ".php";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "-l",
                "-d", "display_errors=1",
                "-d", "error_reporting=E_ALL",
                tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "PHP syntax validation timed out.",
                    new ValidationError(-1, -1, "The PHP process did not finish in time.", result.stderr()));
        }

        // php -l writes diagnostics to stdout; some versions may also write to stderr
        String combined = mergeOutputs(result.stdout(), result.stderr());

        // If php -l succeeds, it writes a success message to stdout
        if (result.succeeded() && combined.contains("No syntax errors detected")) {
            return ValidationResult.valid("PHP syntax is valid.");
        }

        // Parse the error output
        ValidationResult parsed = PARSER.parse(combined);
        if (!parsed.isValid()) {
            return parsed;
        }

        // If the exit code was non-zero but we couldn't parse specific errors,
        // return a generic failure
        if (!result.succeeded()) {
            String message = combined.isBlank()
                    ? "PHP syntax validation failed with exit code " + result.exitCode()
                    : combined.trim();
            return ValidationResult.invalid(message,
                    List.of(new ValidationError(1, -1, message, null)));
        }

        return ValidationResult.valid("PHP syntax is valid.");
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "PHP CLI ('" + BINARY_NAME + "') could not be found. "
                + "Either provide a valid preferred binary path or ensure PHP 8.0+ is installed "
                + "and available on the system PATH. "
                + "Falling back to the built-in PHP 8.x syntax engine.";
    }

    /**
     * Validates a PHP source string directly (without file I/O).
     * Useful for testing and inline validation.
     *
     * @param source the PHP source code to validate.
     * @return the {@link ValidationResult}.
     */
    public ValidationResult validateSource(String source) {
        return PhpSyntaxEngine.validateStatic(source == null ? "" : source);
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    private static String mergeOutputs(String stdout, String stderr) {
        if (stdout == null || stdout.isBlank()) {
            return stderr != null ? stderr : "";
        }
        if (stderr == null || stderr.isBlank()) {
            return stdout;
        }
        return stdout + "\n" + stderr;
    }
}
