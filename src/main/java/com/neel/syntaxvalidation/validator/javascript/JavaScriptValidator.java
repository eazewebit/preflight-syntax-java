package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

import java.nio.file.Path;
import java.util.List;

/**
 * Validates JavaScript syntax using a two-phase, binary-first approach.
 *
 * <h2>Phase 1 &mdash; Node.js deep analysis (when available)</h2>
 * When Node.js is available on the system &mdash; either from an explicitly
 * supplied preferred path, from a {@link BinaryManager}, or from the system
 * {@code PATH} &mdash; the full {@code node --check} pipeline runs as the
 * first pass, catching the most comprehensive set of syntax errors.
 *
 * <h2>Phase 2 &mdash; Built-in ES6+ syntax engine (fallback)</h2>
 * If Node.js is <em>not</em> available or execution fails, a pure-Java
 * {@link JavaScriptSyntaxEngine} runs as fallback, with zero external
 * dependencies. It tokenises the source and applies a suite of structural
 * checks covering arrow functions, destructuring, async/await, template
 * literals, optional chaining, spread syntax, modern class definitions,
 * and much more.
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
        this((String) null);
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

    /**
     * Creates a validator backed by a {@link BinaryManager} for managed binary
     * resolution (download, cache, version-check).
     *
     * @param binaryManager the binary manager (may be {@code null} for
     *                      PATH-only resolution).
     */
    public JavaScriptValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} with an explicit
     * preferred path and process executor.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryManager       the binary manager (may be {@code null}).
     * @param processExecutor     the process executor to use.
     */
    public JavaScriptValidator(String preferredBinaryPath, BinaryManager binaryManager,
                               ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.JAVASCRIPT;
    }

    /**
     * Built-in ES6+ syntax engine fallback (Phase 2).
     * This method is called by the base class when binary validation is
     * unavailable or fails.
     */
    @Override
    protected ValidationResult validateWithBuiltInEngine(String content) {
        log.trace("[PHASE-2-FALLBACK] Using built-in ES6+ syntax engine for validation");
        ValidationResult engineResult = SYNTAX_ENGINE.validate(content);
        if (!engineResult.isValid()) {
            log.trace("[PHASE-2-FALLBACK] Built-in engine found {} errors", engineResult.getErrors().size());
        }
        return engineResult;
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
                + "and available on the system PATH. "
                + "Falling back to the built-in ES6+ syntax engine.";
    }
}