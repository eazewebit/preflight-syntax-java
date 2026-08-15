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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates Java source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 - built-in JavaSyntaxEngine (always runs)</h2>
 * A fast, dependency-free structural pass.
 *
 * <h2>Phase 2 - javac deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and a javac binary is resolvable,
 * the source is compiled to a temporary directory with -proc:none so that
 * the full Java compiler front-end can surface semantic and residual syntax
 * errors. The code is never executed.
 *
 * If javac is unavailable, the clean result from phase 1 stands.
 */
public class JavaValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "javac";

    private static final Pattern PUBLIC_CLASS_PATTERN =
            Pattern.compile("public\\s+class\\s+(\\w+)");

    private static final JavacOutputParser PARSER = new JavacOutputParser();
    private static final JavaSyntaxEngine ENGINE = new JavaSyntaxEngine();

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
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary
     * resolution (download, cache, version-check).
     *
     * @param binaryManager the binary manager (may be null for PATH-only resolution).
     */
    public JavaValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
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
    }

    /**
     * Two-phase validation.
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1 - built-in Java syntax engine
        ValidationResult engineResult = JavaSyntaxEngine.validateStatic(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2 - javac deep analysis (when available)
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.valid(
                    "Java syntax is valid (validated by the built-in Java syntax engine; "
                            + "javac not available for deeper analysis).");
        }

        String javacContent = stripPublicModifier(safeContent);
        return super.validate(javacContent);
    }

    /**
     * Validates a Java source string directly using only the built-in syntax engine.
     *
     * @param source the Java source code to validate.
     * @return the ValidationResult.
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

    private String stripPublicModifier(String content) {
        return content.replaceAll("(?m)^\\s*public\\s+class\\s+", "class ");
    }

    protected Path createTempFile(String content) throws IOException {
        String publicClassName = extractPublicClassName(content);
        if (publicClassName != null) {
            Path tempDir = Files.createTempDirectory("java-syntax-check-");
            return tempDir.resolve(publicClassName + ".java");
        } else {
            return createTempFile();
        }
    }

    private String extractPublicClassName(String content) {
        Matcher matcher = PUBLIC_CLASS_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        return List.of(binaryPath, "-proc:none", "-nowarn", "-d", tempDirFlag(), tempFile.toString());
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

    private String tempDirFlag() {
        return System.getProperty("java.io.tmpdir");
    }
}
