package com.neel.syntaxvalidation.validator.python;

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
 * Validates Python 3.14 source code using a two-phase, non-executing strategy.
 *
 * <h2>Phase 1 &mdash; built-in {@link PythonSyntaxEngine} (always runs)</h2>
 * A fast, dependency-free structural pass that tokenises the source and runs a
 * pipeline of lexer + parser + pattern-based checks. This catches lexical
 * anomalies, delimiter imbalance and obvious keyword misuse with zero external
 * dependencies.
 *
 * <h2>Phase 2 &mdash; {@code python3} deep analysis (when available)</h2>
 * If the pure-Java engine reports no errors and a {@code python3} (or
 * {@code python}) binary is resolvable &mdash; either from an explicitly
 * supplied preferred path or from the system {@code PATH} &mdash; the source is
 * validated using {@code ast.parse()} so that the full Python parser can
 * surface every grammar-level error. The code is never executed.
 *
 * <p>If the Python interpreter is unavailable, the clean result from
 * phase&nbsp;1 stands, ensuring the validator always provides a best-effort
 * answer.
 *
 * <p>This class is the public entry point for Python validation and is
 * registered in the {@link com.neel.syntaxvalidation.validator.ValidatorFactory
 * ValidatorFactory}.
 */
public class PythonValidator extends AbstractLanguageValidator implements LanguageValidator {

    /** The bare binary name searched on {@code PATH}. */
    static final String BINARY_NAME = "python3";

    // PARSER instance kept for backward compatibility; parse() is now static.

    /**
     * Creates a validator that resolves {@code python3} (or {@code python})
     * from the system {@code PATH}.
     *
     * @param binaryResolver  the resolver used to discover the Python binary.
     * @param processExecutor the executor used to run the Python process.
     */
    public PythonValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this(null, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator with an explicit preferred binary path.
     *
     * @param preferredBinaryPath an explicit path to a Python executable,
     *                            or {@code null} to search the {@code PATH}.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     */
    public PythonValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                           ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Two-phase validation.
     *
     * <ol>
     *   <li>Run the built-in {@link PythonSyntaxEngine}. If it reports errors,
     *       return them immediately &mdash; no external process is needed.</li>
     *   <li>If the engine passes and Python is available, delegate to
     *       {@code super.validate()} (which invokes {@code python3 -c ...})
     *       for deeper analysis.</li>
     *   <li>If Python is unavailable, the engine's clean result stands.</li>
     * </ol>
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        // Phase 1 — built-in Python syntax engine (always runs, zero external deps)
        ValidationResult engineResult = PythonSyntaxEngine.validate(safeContent);
        if (!engineResult.isValid()) {
            return engineResult;
        }

        // Phase 2 — python3 deep analysis (when available)
        Optional<String> binary = resolveBinary();
        if (binary.isEmpty()) {
            return ValidationResult.valid(
                    "Python syntax is valid (validated by the built-in Python syntax engine; "
                            + "python3 not available for deeper analysis).");
        }

        // Python available — delegate to the full pipeline for maximum coverage.
        return super.validate(safeContent);
    }

    /**
     * Validates a Python source string directly using only the built-in
     * syntax engine (no file I/O, no external binary).
     *
     * @param source the Python source code to validate.
     * @return the {@link ValidationResult}.
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
        // Use python -c to validate syntax via ast.parse.
        // ast.parse raises SyntaxError, IndentationError, or TabError for invalid code.
        // We format the error in the same structure Python normally emits so that
        // PythonOutputParser can parse it.
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

        // Python writes syntax errors to stderr.
        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }

        ValidationResult parsed = PythonOutputParser.parse(output, result.stdout(), result.exitCode());
        if (!parsed.isValid()) {
            return parsed;
        }

        // Exit code 0 with no parseable errors means the file is syntactically valid.
        if (result.succeeded()) {
            return ValidationResult.valid("Python syntax is valid.");
        }

        // Non-zero exit but nothing parseable — surface a generic failure.
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

    /**
     * Escapes backslashes and single quotes in file paths for embedding
     * inside Python {@code -c} single-quoted strings.
     */
    private static String escapePath(String path) {
        if (path == null) return "";
        return path.replace("\\", "\\\\").replace("'", "\\'");
    }
}
