package com.neel.syntaxvalidation.validator.html;

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
 * A {@link com.neel.syntaxvalidation.validator.LanguageValidator} implementation
 * that validates HTML source files using a dual-strategy approach.
 *
 * <h2>Validation strategy</h2>
 * <ol>
 *   <li><b>External binary (primary)</b> &mdash; invokes the
 *       <a href="https://validator.github.io/validator/">Nu Html Checker</a>
 *       ({@code vnu.jar}) with {@code --format json} to perform deep,
 *       specification-level HTML validation.  The checker is resolved via
 *       {@link BinaryResolver} using the binary key {@code "vnu"} (or
 *       {@code "vnu.jar"}).</li>
 *   <li><b>Embedded engine (fallback)</b> &mdash; when vnu.jar is not available
 *       on the host, the built-in {@link HtmlSyntaxEngine} performs a suite of
 *       structural checks: tag matching, attribute validation, void-element
 *       misuse, DOCTYPE checks, comment syntax, and CDATA validation.</li>
 * </ol>
 *
 * <p>If the external binary fails to execute (e.g. not found on the PATH or
 * the JDK is absent), the validator silently falls back to the embedded engine
 * and includes a diagnostic message in the validation result.
 *
 * <h2>Binary resolution</h2>
 * <p>The external binary is located by {@link BinaryResolver} which searches
 * (in order):
 * <ol>
 *   <li>The system property {@code syntaxvalidation.bin.vnu}.</li>
 *   <li>The environment variable {@code SYNTAX_VALIDATION_VNU}.</li>
 *   <li>The system PATH for an executable named {@code "vnu"} or
 *       {@code "vnu.jar"}.</li>
 * </ol>
 *
 * @see HtmlSyntaxEngine
 * @see VnuOutputParser
 */
public class HtmlValidator extends AbstractLanguageValidator {

    /** Default maximum time (in seconds) to wait for vnu.jar to finish. */
    private static final long VNU_TIMEOUT_SECONDS = 120;

    /** Binary name used by {@link BinaryResolver} to search on PATH. */
    private static final String BINARY_NAME = "vnu";

    private final BinaryResolver binaryResolver;
    private final ProcessExecutor processExecutor;

    /**
     * Creates a new HTML validator with default dependencies.
     */
    public HtmlValidator() {
        this(null, new BinaryResolver(), new ProcessExecutor());
    }

    /**
     * Creates a new HTML validator with an explicit binary path.
     *
     * @param preferredBinaryPath the explicit path to vnu.jar, or {@code null}
     *                            to resolve from PATH.
     */
    public HtmlValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    /**
     * Creates a new HTML validator with explicit dependencies (useful for
     * testing and dependency injection).
     *
     * @param preferredBinaryPath the explicit path to vnu.jar, or {@code null}.
     * @param binaryResolver      the resolver used to locate the vnu binary.
     * @param processExecutor     the executor used to run external processes.
     */
    public HtmlValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME,
                Objects.requireNonNull(binaryResolver, "binaryResolver"),
                Objects.requireNonNull(processExecutor, "processExecutor"));
        this.binaryResolver = binaryResolver;
        this.processExecutor = processExecutor;
    }

    @Override
    public Language getLanguage() {
        return Language.HTML;
    }

    // ------------------------------------------------------------------
    //  Override validate() to add embedded-engine fallback
    // ------------------------------------------------------------------

    /**
     * Validates the given HTML content. Attempts the external vnu.jar binary
     * first; if the binary is unavailable, falls back to the embedded
     * {@link HtmlSyntaxEngine}.
     *
     * @param content the HTML source to validate.
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

                // Check for timeout
                if (result.timedOut()) {
                    return ValidationResult.invalid(
                            "HTML validation timed out — the vnu.jar process exceeded the deadline.",
                            List.of());
                }

                ValidationResult externalResult = parseOutput(result, tempFile);

                // If the external binary returned valid, trust it
                if (externalResult.isValid()) {
                    return externalResult;
                }

                // If the external binary returned errors, use those
                return externalResult;
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
        ValidationResult embeddedResult = HtmlSyntaxEngine.getInstance().validate(safeContent);

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
        return ".html";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // vnu.jar is a Java archive; it requires the java runtime to execute.
        // The command is:  java -jar /path/to/vnu.jar --format json <file>
        //
        // However, if the binary path already ends in .jar and the system
        // can execute it directly (e.g. via a wrapper script), we try that
        // first.
        String pathLower = binaryPath.toLowerCase();

        if (pathLower.endsWith(".jar")) {
            return List.of("java", "-jar", binaryPath,
                    "--format", "json", tempFile.toString());
        }

        // Assume the resolved binary is a wrapper script or native binary
        return List.of(binaryPath,
                "--format", "json", tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        // vnu.jar may write JSON to either stdout or stderr depending on
        // the version. We combine both and let the parser handle it.
        String combined = mergeOutputs(result.stdout(), result.stderr());
        return VnuOutputParser.parse(combined);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "vnu.jar (Nu Html Checker) is not installed or could not be resolved — "
                + "falling back to the built-in HTML syntax engine. "
                + "For full HTML validation, install vnu.jar from "
                + "https://github.com/validator/validator/releases "
                + "and set the SYNTAX_VALIDATION_VNU environment variable or the "
                + "syntaxvalidation.bin.vnu system property to its path.";
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
        // vnu sometimes prints to stderr even on success; prefer stderr if it
        // looks like JSON, otherwise prefer stdout.
        if (stderr.stripLeading().startsWith("{")) {
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
