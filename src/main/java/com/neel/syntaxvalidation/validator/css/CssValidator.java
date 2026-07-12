package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
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
/**
 * A {@link com.neel.syntaxvalidation.validator.LanguageValidator} implementation
 * that validates CSS source files using a dual-strategy approach.
 *
 * <h2>Validation strategy</h2>
 * <ol>
 *   <li><b>External binary (primary)</b> &mdash; invokes
 *       <a href="https://stylelint.io/">stylelint</a> with
 *       {@code --formatter json} to perform deep, rule-based CSS validation.
 *       The binary is resolved via {@link BinaryResolver} using the binary key
 *       {@code "stylelint"}.</li>
 *   <li><b>Embedded engine (fallback)</b> &mdash; when stylelint is not
 *       available, the built-in {@link CssSyntaxEngine} performs a suite of
 *       structural checks: brace balance, comment syntax, selector
 *       validation, declaration validation, at-rule checks, and URL function
 *       validation.</li>
 * </ol>
 *
 * <p>If the external binary fails to execute (e.g. not installed via npm or
 * Node.js is absent), the validator silently falls back to the embedded engine
 * and includes a diagnostic message in the validation result.
 *
 * <h2>Binary resolution</h2>
 * <p>The external binary is located by {@link BinaryResolver} which searches
 * (in order):
 * <ol>
 *   <li>The system property {@code syntaxvalidation.bin.stylelint}.</li>
 *   <li>The environment variable {@code SYNTAX_VALIDATION_STYLELINT}.</li>
 *   <li>The system PATH for an executable named {@code "stylelint"}.</li>
 * </ol>
 *
 * @see CssSyntaxEngine
 * @see StylelintOutputParser
 */
public class CssValidator extends AbstractLanguageValidator {

    /** Default maximum time (in seconds) to wait for stylelint to finish. */
    private static final long STYLELINT_TIMEOUT_SECONDS = 60;

    /** Binary name used by {@link BinaryResolver} to search on PATH. */
    private static final String BINARY_NAME = "stylelint";

    private final BinaryResolver binaryResolver;
    private final ProcessExecutor processExecutor;

    /**
     * Creates a new CSS validator with default dependencies.
     */
    public CssValidator() {
        this(null, new BinaryResolver(), new ProcessExecutor());
    }

    /**
     * Creates a new CSS validator with an explicit binary path.
     *
     * @param preferredBinaryPath the explicit path to stylelint, or {@code null}
     *                            to resolve from PATH.
     */
    public CssValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    /**
     * Creates a new CSS validator with explicit dependencies (useful for
     * testing and dependency injection).
     *
     * @param preferredBinaryPath the explicit path to stylelint, or {@code null}.
     * @param binaryResolver      the resolver used to locate the stylelint binary.
     * @param processExecutor     the executor used to run external processes.
     */
    public CssValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME,
                Objects.requireNonNull(binaryResolver, "binaryResolver"),
                Objects.requireNonNull(processExecutor, "processExecutor"));
        this.binaryResolver = binaryResolver;
        this.processExecutor = processExecutor;
    }

    @Override
    public Language getLanguage() {
        return Language.CSS;
    }

    // ------------------------------------------------------------------
    //  Override validate() to add embedded-engine fallback
    // ------------------------------------------------------------------

    /**
     * Validates the given CSS content. Attempts the external stylelint binary
     * first; if the binary is unavailable, falls back to the embedded
     * {@link CssSyntaxEngine}.
     *
     * @param content the CSS source to validate.
     * @return a structured {@link ValidationResult}.
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Attempt to resolve the external binary
        Optional<String> binary = resolveBinary();

        if (binary.isPresent()) {
            // Try external binary first
            Path tempFile = null;
            try {
                tempFile = createTempFile();
                Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
                List<String> command = buildCommand(binary.get(), tempFile);
                ProcessResult result = processExecutor.execute(command);
                return parseOutput(result, tempFile);
            } catch (IOException e) {
                // Fall through to embedded engine
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Fall through to embedded engine
            } finally {
                if (tempFile != null) {
                    deleteQuietly(tempFile);
                }
            }
        }

        // External binary not available or failed — fall back to embedded engine
        ValidationResult embeddedResult = CssSyntaxEngine.getInstance().validate(safeContent);

        if (embeddedResult.isValid()) {
            return ValidationResult.valid(
                    (binary.isEmpty() ? binaryNotFoundMessage() + " " : "")
                            + embeddedResult.getMessage());
        }

        // Combine the fallback message with the embedded errors
        return ValidationResult.invalid(
                (binary.isEmpty() ? binaryNotFoundMessage() + " " : "")
                        + embeddedResult.getMessage(),
                embeddedResult.getErrors());
    }

    // ------------------------------------------------------------------
    //  AbstractLanguageValidator contract
    // ------------------------------------------------------------------

    @Override
    protected String getFileExtension() {
        return ".css";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // stylelint supports --stdin and --stdin-filename for piping content,
        // but since AbstractLanguageValidator writes to a temp file, we use
        // the file path directly.
        //
        // The command is:  stylelint --formatter json <file>
        return List.of(binaryPath,
                "--formatter", "json",
                "--stdin-filename", "input.css",
                tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        // stylelint writes JSON to stdout; stderr contains startup errors.
        String combined = mergeOutputs(result.stdout(), result.stderr());
        return StylelintOutputParser.parse(combined);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "stylelint is not installed or could not be resolved — "
                + "falling back to the built-in CSS syntax engine. "
                + "For full CSS validation, install stylelint via npm: "
                + "'npm install -g stylelint stylelint-config-standard' "
                + "and ensure 'stylelint' is on your PATH, or set the "
                + "SYNTAX_VALIDATION_STYLELINT environment variable or the "
                + "syntaxvalidation.bin.stylelint system property to its path.";
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
        // stylelint writes results to stdout; stderr typically has error info
        // Prefer stdout if it looks like JSON
        String trimmedStdout = stdout.stripLeading();
        if (trimmedStdout.startsWith("[") || trimmedStdout.startsWith("{")) {
            return stdout;
        }
        // If stderr looks like JSON, use that
        String trimmedStderr = stderr.stripLeading();
        if (trimmedStderr.startsWith("[") || trimmedStderr.startsWith("{")) {
            return stderr;
        }
        return stdout;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup of temp files
        }
    }
}
