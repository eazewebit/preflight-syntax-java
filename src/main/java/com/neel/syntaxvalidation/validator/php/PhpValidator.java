package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;
import com.neel.syntaxvalidation.validator.LanguageValidator;

import java.nio.file.Path;
import java.util.List;

/**
 * Validates PHP source code using a two-phase, binary-first strategy.
 *
 * <h2>Phase 1 - php -l deep analysis (when available)</h2>
 * If a php binary is resolvable, the source is validated using PHP's
 * built-in lint mode. The code is never executed.
 *
 * <h2>Phase 2 - built-in PhpSyntaxEngine (fallback)</h2>
 * If php is <em>not</em> available or execution fails, a fast,
 * dependency-free structural pass runs as fallback.
 */
public class PhpValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "php";

    private static final PhpSyntaxEngine ENGINE = PhpSyntaxEngine.getInstance();

    /**
     * Creates a validator that resolves php from the system PATH.
     */
    public PhpValidator() {
        this(null, new BinaryResolver(), new ProcessExecutor());
    }

    public PhpValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this(null, binaryResolver, processExecutor);
    }

    public PhpValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary resolution.
     *
     * @param binaryManager       the binary manager (may be null for PATH-only resolution).
     * @param processExecutor     the process executor to use.
     */
    public PhpValidator(BinaryManager binaryManager, ProcessExecutor processExecutor) {
        super(null, BINARY_NAME, binaryManager, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager with an explicit preferred path.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryManager       the binary manager (may be null).
     * @param processExecutor     the process executor to use.
     */
    public PhpValidator(String preferredBinaryPath, BinaryManager binaryManager,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.PHP;
    }

    /**
     * Built-in PHP syntax engine fallback (Phase 2).
     * This method is called by the base class when binary validation is
     * unavailable or fails.
     */
    @Override
    protected ValidationResult validateWithBuiltInEngine(String content) {
        log.trace("[PHASE-2-FALLBACK] Using built-in PHP syntax engine for validation");
        ValidationResult engineResult = ENGINE.validate(content);
        if (!engineResult.isValid()) {
            log.trace("[PHASE-2-FALLBACK] Built-in engine found {} errors", engineResult.getErrors().size());
        }
        return engineResult;
    }

    /**
     * Validates a PHP source string directly using only the built-in syntax
     * engine (no file I/O, no external binary).
     *
     * @param source the PHP source code to validate.
     * @return the ValidationResult.
     */
    public ValidationResult validateSource(String source) {
        return ENGINE.validate(source == null ? "" : source);
    }

    @Override
    protected String getFileExtension() {
        return ".php";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "-l", tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "PHP syntax validation timed out.",
                    new ValidationError(-1, -1, "The PHP process did not finish in time.", result.stderr()));
        }

        String output = result.stdout();
        if (output == null || output.isBlank()) {
            output = result.stderr();
        }

        PhpOutputParser parser = new PhpOutputParser();
        ValidationResult parsed = parser.parse(output);
        if (parsed.isValid() && result.succeeded()) {
            return ValidationResult.valid("PHP syntax is valid (verified by php -l).");
        }
        if (!parsed.isValid()) {
            return parsed;
        }

        // Non-zero exit code with no parseable errors
        String message = (output == null || output.isBlank())
                ? "PHP exited with code " + result.exitCode() + " but produced no parseable output."
                : output.trim();
        return ValidationResult.invalid(message,
                new ValidationError(1, -1, message, null));
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "PHP binary ('" + BINARY_NAME + "') could not be found. "
                + "Either provide a valid preferred binary path or ensure PHP 8.1+ "
                + "is installed and available on the system PATH. "
                + "Falling back to the built-in PHP syntax engine.";
    }
}