package com.neel.syntaxvalidation.validator.html;

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
 * Validates HTML source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 - built-in HtmlSyntaxEngine (always runs)</h2>
 * A fast, dependency-free structural pass.
 *
 * <h2>Phase 2 - vnu deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and vnu is resolvable,
 * the source is validated using the W3C's official HTML checker.
 *
 * If vnu is unavailable, the clean result from phase 1 stands.
 */
public class HtmlValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "vnu";

    private static final HtmlSyntaxEngine ENGINE = HtmlSyntaxEngine.getInstance();

    public HtmlValidator() {
        this((String) null);
    }

    public HtmlValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    public HtmlValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary resolution.
     *
     * @param binaryManager the binary manager (may be null for PATH-only resolution).
     */
    public HtmlValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a validator backed by a BinaryManager with an explicit preferred path and process executor.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryManager       the binary manager (may be null).
     * @param processExecutor     the process executor to use.
     */
    public HtmlValidator(String preferredBinaryPath, BinaryManager binaryManager,
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
                    "HTML syntax is valid (validated by the built-in HTML syntax engine; "
                            + "vnu not available for deeper analysis).");
        }

        try {
            return super.validate(safeContent);
        } catch (RuntimeException e) {
            return ValidationResult.valid(
                    "HTML syntax is valid (validated by the built-in HTML syntax engine; "
                            + "vnu execution failed: " + e.getMessage() + ").");
        }
    }

    @Override
    public Language getLanguage() {
        return Language.HTML;
    }

    @Override
    protected String getFileExtension() {
        return ".html";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "--format", "text", tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "HTML syntax validation timed out.",
                    new ValidationError(-1, -1, "The vnu process did not finish in time.", result.stderr()));
        }

        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        if (output == null || output.isBlank()) {
            if (result.succeeded()) {
                return ValidationResult.valid("HTML syntax is valid (verified by vnu).");
            }
            return ValidationResult.invalid(
                    "HTML validation failed.",
                    new ValidationError(1, -1, "The HTML validator exited with code " + result.exitCode(), null));
        }

        ValidationResult parsed = VnuOutputParser.parse(output);
        if (parsed.isValid()) {
            return ValidationResult.valid("HTML syntax is valid (verified by vnu).");
        }
        return parsed;
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "Nu Html Checker ('vnu') binary not found. "
                + "Provide a path via the 'vnu.path' system property or ensure it's on PATH. "
                + "Falling back to the built-in HTML syntax engine.";
    }
}
