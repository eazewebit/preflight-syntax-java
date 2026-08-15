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
import java.util.Optional;

/**
 * Validates Python source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 - built-in PythonSyntaxEngine (always runs)</h2>
 * A fast, dependency-free structural pass.
 *
 * <h2>Phase 2 - python3 deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and a python3 binary is resolvable,
 * the source is validated using ast.parse() so that the full Python parser
 * can surface every grammar-level error. The code is never executed.
 *
 * If the Python interpreter is unavailable, the clean result from phase 1 stands.
 */
public class PythonValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "python3";

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
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1
        ValidationResult engineResult = PythonSyntaxEngine.validate(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.valid(
                    "Python syntax is valid (validated by the built-in Python syntax engine; "
                            + "python3 not available for deeper analysis).");
        }

        return super.validate(safeContent);
    }

    /**
     * Validates a Python source string directly using only the built-in syntax engine.
     *
     * @param source the Python source code to validate.
     * @return the ValidationResult.
     */
    public ValidationResult validateSource(String source) {
        return PythonSyntaxEngine.validate(source == null ? "" : source);
    }

    @Override
    public Language getLanguage() {
        return Language.PYTHON;
    }

    @Override
    protected String getFileExtension() {
        return ".py";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        String escapedPath = escapePath(tempFile.toString());
        return List.of(
                binaryPath,
                "-c",
                "import ast, sys; " +
                        "try: " +
                        "    ast.parse(open(r'" + escapedPath + "').read(), '<string>'); " +
                        "except SyntaxError as e: " +
                        "    print(f'  File \"<string>\", line {e.lineno}', file=sys.stderr); " +
                        "    if e.text: " +
                        "        print(e.text.rstrip(), file=sys.stderr); " +
                        "        print(' ' * ((e.offset or 1) - 1) + '^', file=sys.stderr); " +
                        "    print(f'SyntaxError: {e.msg}', file=sys.stderr); " +
                        "    sys.exit(1); " +
                        "except IndentationError as e: " +
                        "    print(f'  File \"<string>\", line {e.lineno}', file=sys.stderr); " +
                        "    if e.text: " +
                        "        print(e.text.rstrip(), file=sys.stderr); " +
                        "        print(' ' * ((e.offset or 1) - 1) + '^', file=sys.stderr); " +
                        "    print(f'IndentationError: {e.msg}', file=sys.stderr); " +
                        "    sys.exit(1); " +
                        "except TabError as e: " +
                        "    print(f'  File \"<string>\", line {e.lineno}', file=sys.stderr); " +
                        "    if e.text: " +
                        "        print(e.text.rstrip(), file=sys.stderr); " +
                        "        print(' ' * ((e.offset or 1) - 1) + '^', file=sys.stderr); " +
                        "    print(f'TabError: {e.msg}', file=sys.stderr); " +
                        "    sys.exit(1); " +
                        "except Exception as e: " +
                        "    print(f'{type(e).__name__}: {e}', file=sys.stderr); " +
                        "    sys.exit(1)"
        );
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "Python syntax validation timed out.",
                    new ValidationError(-1, -1, "The python process did not finish in time.", result.stderr()));
        }

        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        ValidationResult parsed = PythonOutputParser.parse(output, result.stdout(), result.exitCode());
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
