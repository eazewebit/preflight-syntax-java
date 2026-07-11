package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Validates JavaScript syntax using a two-phase approach.
 *
 * <h2>Phase 1 &mdash; Built-in ES6+ syntax engine</h2>
 * A pure-Java {@link JavaScriptSyntaxEngine} runs first, with zero external
 * dependencies. It tokenises the source and applies a suite of structural checks
 * covering arrow functions, destructuring, async/await, template literals,
 * optional chaining, spread syntax, modern class definitions, and much more.
 * If the engine detects errors they are returned immediately.
 *
 * <h2>Phase 2 &mdash; Node.js deep analysis</h2>
 * When Node.js is available on the system, the full {@code node --check} pipeline
 * runs as a second pass, catching subtler semantic-adjacent syntax errors that a
 * pure-Java engine cannot reasonably detect (e.g. duplicate parameter names,
 * invalid label syntax, or contextual grammar violations).
 *
 * <p>If Node.js is <em>not</em> available, the built-in engine's clean result
 * stands on its own, providing meaningful syntax validation in any environment.
 *
 * <p>Both the {@link BinaryResolver} and {@link ProcessExecutor} collaborators
 * are injectable to ease unit testing.
 */
public class JavaScriptValidator extends AbstractLanguageValidator {

    /** The bare name of the Node.js binary searched on the {@code PATH}. */
    public static final String BINARY_NAME = "node";

    private static final NodeCheckOutputParser PARSER = new NodeCheckOutputParser();
    private static final JavaScriptSyntaxEngine SYNTAX_ENGINE = JavaScriptSyntaxEngine.getInstance();

    /**
     * Creates a validator that resolves {@code node} from the system {@code PATH}.
     */
    public JavaScriptValidator() {
        this(null);
    }

    /**
     * @param preferredBinaryPath an explicit path to the {@code node} binary, or
     *                            {@code null} to resolve from the {@code PATH}.
     */
    public JavaScriptValidator(String preferredBinaryPath) {
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
    public JavaScriptValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                               ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.JAVASCRIPT;
    }

    /**
     * Two-phase validation.
     *
     * <ol>
     *   <li>Run the built-in {@link JavaScriptSyntaxEngine}. If it reports
     *       errors, return them immediately &mdash; no external process is
     *       needed.</li>
     *   <li>If the engine passes and Node.js is available, delegate to
     *       {@code super.validate()} (which invokes {@code node --check}) for
     *       deeper analysis.</li>
     *   <li>If Node.js is unavailable, the engine's clean result stands.</li>
     * </ol>
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1 — built-in ES6+ syntax engine (always runs, zero external deps)
        ValidationResult engineResult = SYNTAX_ENGINE.validate(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2 — Node.js deep analysis (when available)
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            // Node.js unavailable — the engine's clean result stands.
            return ValidationResult.valid(
                    "JavaScript syntax is valid (validated by the built-in ES6+ syntax engine; "
                            + "Node.js not available for deeper analysis).");
        }

        // Node.js available — delegate to the full pipeline for maximum coverage.
        return super.validate(safeContent);
    }

    @Override
    protected String getFileExtension() {
        return ".js";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "--check", tempFile.toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "Node.js syntax validation timed out.",
                    new ValidationError(-1, -1, "The Node.js process did not finish in time.", result.stderr()));
        }
        if (result.succeeded()) {
            return ValidationResult.valid("JavaScript syntax is valid.");
        }

        String output = result.stderr().isBlank() ? result.stdout() : result.stderr();
        List<ValidationError> errors = PARSER.parse(output);
        String summary = errors.isEmpty()
                ? "JavaScript syntax validation failed."
                : "JavaScript syntax validation failed with " + errors.size() + " error(s).";
        return ValidationResult.invalid(summary, errors);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "Node.js binary ('" + BINARY_NAME + "') could not be found. "
                + "Either provide a valid preferred binary path or ensure Node.js is installed "
                + "and available on the system PATH.";
    }
}
