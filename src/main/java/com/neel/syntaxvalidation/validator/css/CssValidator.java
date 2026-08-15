package com.neel.syntaxvalidation.validator.css;

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
import java.util.Optional;

/**
 * Validates CSS source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 - built-in CssSyntaxEngine (always runs)</h2>
 * A fast, dependency-free structural pass.
 *
 * <h2>Phase 2 - stylelint deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and stylelint is resolvable,
 * the source is validated using stylelint for a comprehensive CSS lint.
 *
 * If stylelint is unavailable, the clean result from phase 1 stands.
 */
public class CssValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "stylelint";

    private static final CssSyntaxEngine ENGINE = CssSyntaxEngine.getInstance();

    public CssValidator() {
        this((String) null);
    }

    public CssValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    public CssValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary resolution.
     *
     * @param binaryManager the binary manager (may be null for PATH-only resolution).
     */
    public CssValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a validator backed by a BinaryManager with an explicit preferred path and process executor.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryManager       the binary manager (may be null).
     * @param processExecutor     the process executor to use.
     */
    public CssValidator(String preferredBinaryPath, BinaryManager binaryManager,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
    }

    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1
        ValidationResult engineResult = ENGINE.validate(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.valid(
                    "CSS syntax is valid (validated by the built-in CSS syntax engine; "
                            + "stylelint not available for deeper analysis).");
        }

        return super.validate(safeContent);
    }

    @Override
    public Language getLanguage() {
        return Language.CSS;
    }

    @Override
    protected String getFileExtension() {
        return ".css";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "--formatter", "json", "--stdin", "--stdin-filename", "file.css");
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "CSS syntax validation timed out.",
                    new ValidationError(-1, -1, "The stylelint process did not finish in time.", result.stderr()));
        }

        if (result.succeeded()) {
            return ValidationResult.valid("CSS syntax is valid (verified by stylelint).");
        }

        String output = result.stderr().isBlank() ? result.stdout() : result.stderr();
        return StylelintOutputParser.parse(output);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "stylelint binary not found. Install stylelint (npm install -g stylelint) "
                + "or provide a path via the 'stylelint.path' system property. "
                + "Falling back to the built-in CSS syntax engine.";
    }
}
