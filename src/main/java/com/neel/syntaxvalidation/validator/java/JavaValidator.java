package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;
import com.neel.syntaxvalidation.validator.LanguageValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Validates Java source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 &mdash; built-in {@link JavaSyntaxEngine} (always runs)</h2>
 * A fast, dependency-free structural pass tokenises the source and runs a
 * pipeline of isolated {@code checker} components. This catches lexical
 * anomalies, delimiter imbalance and obvious keyword misuse with zero external
 * dependencies.
 *
 * <h2>Phase 2 &mdash; {@code javac} deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and a {@code javac} binary is
 * resolvable &mdash; either from an explicitly supplied preferred path or from
 * the system {@code PATH} &mdash; the source is compiled to a temporary
 * directory with {@code -proc:none} (annotation processing disabled) so that
 * the full Java compiler front-end can surface semantic and residual syntax
 * errors. The code is never executed.
 *
 * <p>If {@code javac} is unavailable, the clean result from phase&nbsp;1 stands,
 * ensuring the validator always provides a best-effort answer.
 *
 * <p>This class is the public entry point for Java validation and is registered
 * in the {@link com.neel.syntaxvalidation.validator.ValidatorFactory
 * ValidatorFactory}.
 */
public class JavaValidator extends AbstractLanguageValidator implements LanguageValidator {

    /** The bare binary name searched on {@code PATH}. */
    static final String BINARY_NAME = "javac";

    private static final JavacOutputParser PARSER = new JavacOutputParser();
    private static final JavaSyntaxEngine ENGINE = new JavaSyntaxEngine();

    /**
     * Creates a validator that resolves {@code javac} from the system
     * {@code PATH}.
     */
    public JavaValidator() {
        this(null);
    }

    /**
     * Creates a validator with an explicit preferred binary path.
     *
     * @param preferredBinaryPath an explicit path to a {@code javac} executable,
     *                            or {@code null} to search the {@code PATH}.
     */
    public JavaValidator(String preferredBinaryPath) {
        super(preferredBinaryPath, BINARY_NAME);
    }

    /**
     * Creates a validator with explicit collaborators, primarily for testing.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     */
    public JavaValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Two-phase validation.
     *
     * <ol>
     *   <li>Run the built-in {@link JavaSyntaxEngine}. If it reports errors,
     *       return them immediately &mdash; no external process is needed.</li>
     *   <li>If the engine passes and {@code javac} is available, delegate to
     *       {@code super.validate()} (which invokes {@code javac}) for deeper
     *       analysis.</li>
     *   <li>If {@code javac} is unavailable, the engine's clean result stands.</li>
     * </ol>
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1 — built-in Java syntax engine (always runs, zero external deps)
        ValidationResult engineResult = JavaSyntaxEngine.validateStatic(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2 — javac deep analysis (when available)
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.valid(
                    "Java syntax is valid (validated by the built-in Java syntax engine; "
                            + "javac not available for deeper analysis).");
        }

        // javac available — delegate to the full pipeline for maximum coverage.
        return super.validate(safeContent);
    }

    /**
     * Validates a Java source string directly using only the built-in syntax
     * engine (no file I/O, no external binary).
     *
     * @param source the Java source code to validate.
     * @return the {@link ValidationResult}.
     */
    public ValidationResult validateSource(String source) {
        return JavaSyntaxEngine.validateStatic(source == null ? "" : source);
    }

    @Override
    public Language getLanguage() {
        return Language.JAVA;
    }

    @Override
    protected String getFileExtension() {
        return ".java";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // -proc:none    disables annotation processing (faster, fewer deps).
        // -nowarn       suppresses warnings; we only care about errors.
        // -d /dev/null  compiles to a throwaway location; the code is never executed.
        return List.of(binaryPath, "-proc:none", "-nowarn", "-d", tempDirFlag(), tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "Java syntax validation timed out.",
                    new ValidationError(-1, -1, "The javac process did not finish in time.", result.stderr()));
        }

        // javac writes diagnostics to stderr.
        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        ValidationResult parsed = PARSER.parse(output);
        if (!parsed.isValid()) {
            return parsed;
        }

        // Exit code 0 with no parseable errors means the file is syntactically valid.
        if (result.succeeded()) {
            return ValidationResult.valid("Java syntax is valid.");
        }

        // Non-zero exit but nothing parseable — surface a generic failure.
        String message = (output == null || output.isBlank())
                ? "javac exited with code " + result.exitCode() + " but produced no diagnostic output."
                : output.trim();
        return ValidationResult.invalid(message,
                List.of(new ValidationError(1, -1, message, null)));
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "Java compiler ('" + BINARY_NAME + "') could not be found. "
                + "Either provide a valid preferred binary path or ensure a JDK (Java 21+ recommended) "
                + "is installed and available on the system PATH. "
                + "Falling back to the built-in Java syntax engine.";
    }

    /**
     * Returns the platform-appropriate throwaway compilation target.
     *
     * <p>On POSIX systems {@code /dev/null} is a valid directory argument;
     * on Windows a real temporary directory is created implicitly by javac.
     * To keep things simple and portable we always compile into the system
     * temp directory, which is cleaned up by the OS.
     */
    private String tempDirFlag() {
        return System.getProperty("java.io.tmpdir");
    }
}
