package com.neel.syntaxvalidation.validator.java;

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
 * Validates Java source code using a three-phase, non-executing strategy.
 *
 * <h2>Phase 1 - JavacTask.parse() AST check (primary)</h2>
 * Uses the OpenJDK {@code com.sun.source.util.JavacTask.parse()} API to
 * perform an AST-level syntax check without full compilation. This catches
 * unclosed brackets, bad tokens, missing semicolons, malformed control
 * structures, and invalid keywords &mdash; with zero false positives from
 * missing imports or unresolved symbols.
 *
 * <h2>Phase 2 - javac CLI deep analysis (when available)</h2>
 * If a javac binary is resolvable, the source is compiled to a temporary
 * directory with {@code -proc:none} so that the full Java compiler front-end
 * can surface residual errors. The code is never executed.
 *
 * <h2>Phase 3 - Built-in JavaSyntaxEngine (fallback)</h2>
 * If neither the programmatic compiler nor CLI javac is available, a fast,
 * dependency-free structural pass is used as final fallback.
 */
public class JavaValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "javac";

    private static final JavacOutputParser PARSER = new JavacOutputParser();

    /** Programmatic AST-level syntax validator (JavacTask.parse). */
    private final JavacSyntaxValidator javacSyntaxValidator;

    /**
     * Creates a validator that resolves javac from the system PATH.
     */
    public JavaValidator() {
        this((String) null);
    }

    /**
     * Creates a validator with an explicit preferred binary path.
     *
     * @param preferredBinaryPath an explicit path to a javac executable,
     *                            or null to search the PATH.
     */
    public JavaValidator(String preferredBinaryPath) {
        super(preferredBinaryPath, BINARY_NAME);
        this.javacSyntaxValidator = new JavacSyntaxValidator();
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary
     * resolution (download, cache, version-check).
     *
     * @param binaryManager the binary manager (may be null for PATH-only resolution).
     */
    public JavaValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
        this.javacSyntaxValidator = new JavacSyntaxValidator();
    }

    /**
     * Creates a validator with explicit collaborators, primarily for testing.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     */
    public JavaValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
        this.javacSyntaxValidator = new JavacSyntaxValidator();
    }

    /**
     * Creates a validator with explicit collaborators and an explicit
     * {@link JavacSyntaxValidator} instance, primarily for testing.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     * @param javacSyntaxValidator the programmatic javac validator to use.
     */
    public JavaValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor,
                         JavacSyntaxValidator javacSyntaxValidator) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
        this.javacSyntaxValidator = javacSyntaxValidator;
    }

    @Override
    public Language getLanguage() {
        return Language.JAVA;
    }

    @Override
    protected String getFileExtension() {
        return ".java";
    }

    /**
     * Validates Java syntax after stripping the {@code public} modifier from
     * class declarations so that the temporary file name (which defaults to
     * {@code source.java}) does not need to match the public class name
     * required by {@code javac}.
     *
     * <p>This overload is used when no original filename is available. The
     * temporary file is written to {@code source.java} inside a random temp
     * directory.
     */
    @Override
    public ValidationResult validate(String content) {
        String prepared = (content != null) ? stripPublicModifier(content) : "";
        // Use JavacTask.parse() first — it does not require filename matching.
        ValidationResult astResult = validateWithAstParser(prepared, null);
        if (astResult != null) {
            return astResult;
        }
        // Fall back to CLI binary or built-in engine.
        return super.validate(prepared);
    }

    /**
     * Validates Java syntax with the original filename preserved.
     *
     * <p>When the original filename is supplied (e.g. {@code "Foo.java"}),
     * the {@code public} modifier is <em>not</em> stripped because the
     * temporary file will bear the correct name for javac.
     *
     * @param content  the source text to check.
     * @param fileName the original file name (e.g. {@code "Foo.java"}).
     */
    @Override
    public ValidationResult validate(String content, String fileName) {
        // Use JavacTask.parse() first — AST-level check with zero false positives.
        ValidationResult astResult = validateWithAstParser(content, fileName);
        if (astResult != null) {
            return astResult;
        }
        // Fall back to CLI binary or built-in engine.
        return super.validate(content, fileName);
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // Use the workspace directory (tempFile's parent) as -d so .class
        // files are written there and cleaned up together with the workspace.
        return List.of(binaryPath, "-proc:none", "-nowarn",
                "-d", tempFile.getParent().toString(), tempFile.toString());
    }

    /**
     * Phase 3 — Built-in lexer-based structural engine fallback.
     * Called when neither the programmatic compiler nor CLI javac is available.
     */
    @Override
    protected ValidationResult validateWithBuiltInEngine(String content) {
        log.trace("[PHASE-3-FALLBACK] Using built-in JavaSyntaxEngine for validation");
        return JavaSyntaxEngine.validateStatic(content == null ? "" : content);
    }

    /**
     * Validates Java source code exclusively through the programmatic
     * {@link JavacSyntaxValidator} (AST parse phase only).
     *
     * <p>This bypasses the CLI binary and built-in lexer engine entirely.
     * Use this method when you want the strictest syntax-only check without
     * any filesystem I/O.
     *
     * @param source the Java source code to validate.
     * @return the validation result; never {@code null}.
     */
    public ValidationResult validateSource(String source) {
        return javacSyntaxValidator.validate(source == null ? "" : source);
    }

    /**
     * Validates Java source code exclusively through the programmatic
     * {@link JavacSyntaxValidator} (AST parse phase only), preserving the
     * original file name.
     *
     * @param source   the Java source code to validate.
     * @param fileName the original file name (e.g. {@code "Foo.java"}).
     * @return the validation result; never {@code null}.
     */
    public ValidationResult validateSource(String source, String fileName) {
        return javacSyntaxValidator.validate(source == null ? "" : source, fileName);
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "Java syntax validation timed out.",
                    new ValidationError(-1, -1, "The javac process did not finish in time.", result.stderr()));
        }

        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        ValidationResult parsed = PARSER.parse(output);
        if (!parsed.isValid()) {
            return parsed;
        }

        if (result.succeeded()) {
            return ValidationResult.valid("Java syntax is valid.");
        }

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

    // ------------------------------------------------------------------
    //  Internal: Phase 1 — JavacTask.parse() AST validation
    // ------------------------------------------------------------------

    /**
     * Attempts AST-level syntax validation using the programmatic javac
     * compiler. Returns {@code null} if the compiler is not available,
     * allowing the caller to fall through to CLI / built-in engine phases.
     */
    private ValidationResult validateWithAstParser(String content, String fileName) {
        if (!javacSyntaxValidator.isCompilerAvailable()) {
            log.info("[PHASE-1-AST] JavacTask.parse() not available — no system Java compiler. Falling through.");
            return null;
        }
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ [PHASE-1-AST] Java validation: Using JavacTask.parse() API");
        log.info("╚══════════════════════════════════════════════════════════════");
        ValidationResult result = javacSyntaxValidator.validate(content == null ? "" : content, fileName);
        log.info("[PHASE-1-AST] Result: valid={}, errors={}", result.isValid(), result.getErrors().size());
        return result;
    }

    private String stripPublicModifier(String content) {
        return content.replaceAll("(?m)^\\s*public\\s+class\\s+", "class ");
    }
}
