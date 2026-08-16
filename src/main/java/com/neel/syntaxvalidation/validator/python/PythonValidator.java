package com.neel.syntaxvalidation.validator.python;

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
 * Validates Python source code using a two-phase, binary-first strategy.
 *
 * <h2>Phase 1 - python3 deep analysis (when available)</h2>
 * If a python3 binary is resolvable, the source is validated using ast.parse()
 * so that the full Python parser can surface every grammar-level error.
 * The code is never executed.
 *
 * <h2>Phase 2 - built-in PythonSyntaxEngine (fallback)</h2>
 * If python3 is <em>not</em> available or execution fails, a fast,
 * dependency-free structural pass runs as fallback.
 */
public class PythonValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "python3";

    /**
     * Creates a validator that resolves python3 from the system PATH.
     */
    public PythonValidator() {
        this(null, new BinaryResolver(), new ProcessExecutor());
    }

    public PythonValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this(null, binaryResolver, processExecutor);
    }

    public PythonValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                           ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary resolution.
     *
     * @param binaryManager       the binary manager (may be null for PATH-only resolution).
     * @param processExecutor     the process executor to use.
     */
    public PythonValidator(BinaryManager binaryManager, ProcessExecutor processExecutor) {
        super(null, BINARY_NAME, binaryManager, processExecutor);
    }

    @Override
    public Language getLanguage() {
        return Language.PYTHON;
    }

    /**
     * Built-in Python syntax engine fallback (Phase 2).
     * This method is called by the base class when binary validation is
     * unavailable or fails.
     */
    @Override
    protected ValidationResult validateWithBuiltInEngine(String content) {
        log.trace("[PHASE-2-FALLBACK] Using built-in PythonSyntaxEngine for validation");
        ValidationResult engineResult = PythonSyntaxEngine.validate(content);
        if (!engineResult.isValid()) {
            log.trace("[PHASE-2-FALLBACK] Built-in engine found {} errors", engineResult.getErrors().size());
        }
        return engineResult;
    }

    /**
     * Validates Python source code directly using the built-in engine.
     * This method provides direct access to the built-in engine without
     * going through the binary validation phase.
     *
     * @param source the Python source code to validate.
     * @return ValidationResult from the built-in engine.
     */
    public ValidationResult validateSource(String source) {
        return PythonSyntaxEngine.validate(source == null ? "" : source);
    }

    @Override
    protected String getFileExtension() {
        return ".py";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        String escapedPath = escapePath(tempFile.toString());
        // Use bare-minimum syntax check: ast.parse() only.
        // Python's default SyntaxError traceback is parseable by PythonOutputParser.
        // No try/except needed — Python exits 1 on SyntaxError with a clean traceback.
        return List.of(
                binaryPath,
                "-c",
                "import ast; ast.parse(open(r'" + escapedPath + "').read())"
        );
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        ValidationResult parsed = PythonOutputParser.parse(output, result.stdout(), result.exitCode());

        // If the parser detected a stub binary (e.g., Windows Store python3.exe),
        // throw an exception so the base class falls back to the built-in engine.
        if (!parsed.isValid() && isStubBinary(parsed)) {
            log.warn("[PHASE-1-BINARY] Detected stub/broken Python binary, falling back to built-in engine");
            throw new RuntimeException("Python binary is a stub or broken alias");
        }

        if (!parsed.isValid()) {
            return parsed;
        }

        if (result.succeeded()) {
            return ValidationResult.valid("Python syntax is valid.");
        }

        String message = (output == null || output.isBlank())
                ? "python exited with code " + result.exitCode() + " but produced no diagnostic output."
                : output.trim();
        return ValidationResult.invalid(message,
                List.of(new ValidationError(1, -1, message, null)));
    }

    /**
     * Checks if the validation result indicates a stub binary rather than
     * actual Python syntax errors.
     */
    private static boolean isStubBinary(ValidationResult result) {
        if (result.getErrors() == null) return false;
        return result.getErrors().stream()
                .anyMatch(e -> e.getMessage() != null && e.getMessage().startsWith("BINARY_STUB:"));
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "Python interpreter ('" + BINARY_NAME + "' or 'python') could not be found. "
                + "Either provide a valid preferred binary path or ensure Python 3.10+ "
                + "is installed and available on the system PATH. "
                + "Falling back to the built-in Python syntax engine.";
    }

    private static String escapePath(String path) {
        if (path == null) return "";
        return path.replace("\\", "\\\\").replace("'", "\\'");
    }
}
