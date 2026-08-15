package com.neel.syntaxvalidation.validator.typescript;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Validates TypeScript ({@code .ts}), TSX ({@code .tsx}), and JSX ({@code .jsx}) source
 * code using a two-phase, binary-first strategy.
 *
 * <h2>Phase 1 &mdash; {@code tsc} deep analysis (when available)</h2>
 * If a {@code tsc} binary is resolvable &mdash; either from an explicitly
 * supplied preferred path, from a {@link BinaryManager}, or from the system
 * {@code PATH} &mdash; the source is validated using the TypeScript compiler
 * with {@code --noEmit} for full type-aware syntax checking. The code is
 * never executed.
 *
 * <h2>Phase 2 &mdash; built-in {@link TypeScriptSyntaxEngine} (fallback)</h2>
 * If {@code tsc} is <em>not</em> available or execution fails, a fast,
 * dependency-free structural pass runs as fallback. It tokenises the source
 * and checks for unbalanced delimiters, unclosed strings, and other obvious
 * syntax errors. This requires zero external dependencies.
 *
 * <p>This class is the public entry point for TypeScript validation and is
 * registered in the {@link com.neel.syntaxvalidation.validator.ValidatorFactory
 * ValidatorFactory}.
 */
public class TypeScriptValidator extends AbstractLanguageValidator implements LanguageValidator {

    /** The bare binary name searched on {@code PATH}. */
    static final String BINARY_NAME = "tsc";

    private final TypeScriptSyntaxEngine syntaxEngine;
    private final TscOutputParser outputParser;
    private final boolean jsxMode;

    /**
     * Creates a validator that resolves {@code tsc} from the system {@code PATH}.
     *
     * @param binaryResolver  the resolver used to discover the tsc binary.
     * @param processExecutor the executor used to run the tsc process.
     */
    public TypeScriptValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this(null, binaryResolver, processExecutor, false);
    }

    /**
     * Creates a validator in JSX mode for TSX/JSX files.
     *
     * @param binaryResolver  the resolver used to discover the tsc binary.
     * @param processExecutor the executor used to run the tsc process.
     * @return a new TypeScriptValidator configured for JSX/TSX files.
     */
    public static TypeScriptValidator createJsxValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        return new TypeScriptValidator(null, binaryResolver, processExecutor, true);
    }

    /**
     * Creates a validator that resolves {@code tsc} from the system {@code PATH}.
     *
     * @param language the specific language variant (TYPESCRIPT)
     */
    public TypeScriptValidator(Language language) {
        this(null, new BinaryResolver(), new ProcessExecutor(), false);
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} for managed binary
     * resolution (download, cache, version-check).
     *
     * @param binaryManager the binary manager (may be {@code null} for
     *                      PATH-only resolution).
     */
    public TypeScriptValidator(BinaryManager binaryManager) {
        this(null, binaryManager, new ProcessExecutor(), false);
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} in JSX mode.
     *
     * @param binaryManager the binary manager (may be {@code null}).
     * @return a new TypeScriptValidator configured for JSX/TSX files.
     */
    public static TypeScriptValidator createJsxValidator(BinaryManager binaryManager) {
        return new TypeScriptValidator(null, binaryManager, new ProcessExecutor(), true);
    }

    /**
     * Creates a validator with explicit components for testing.
     *
     * @param preferredBinaryPath an explicit path to a {@code tsc} executable,
     *                            or {@code null} to search the {@code PATH}.
     * @param binaryResolver      the binary resolver to use.
     * @param processExecutor     the process executor to use.
     * @param jsxMode             whether to enable JSX mode for TSX/JSX files.
     */
    protected TypeScriptValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                       ProcessExecutor processExecutor, boolean jsxMode) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
        this.syntaxEngine = new TypeScriptSyntaxEngine();
        this.outputParser = new TscOutputParser();
        this.jsxMode = jsxMode;
        if (jsxMode) {
            syntaxEngine.enableJsxMode();
        }
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} with explicit
     * components.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryManager       the binary manager (may be {@code null}).
     * @param processExecutor     the process executor to use.
     * @param jsxMode             whether to enable JSX mode for TSX/JSX files.
     */
    protected TypeScriptValidator(String preferredBinaryPath, BinaryManager binaryManager,
                       ProcessExecutor processExecutor, boolean jsxMode) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
        this.syntaxEngine = new TypeScriptSyntaxEngine();
        this.outputParser = new TscOutputParser();
        this.jsxMode = jsxMode;
        if (jsxMode) {
            syntaxEngine.enableJsxMode();
        }
    }

    @Override
    public Language getLanguage() {
        return Language.TYPESCRIPT;
    }

    /**
     * Built-in TypeScript syntax engine fallback (Phase 2).
     * This method is called by the base class when binary validation is
     * unavailable or fails.
     */
    @Override
    protected ValidationResult validateWithBuiltInEngine(String content) {
        log.trace("[PHASE-2-FALLBACK] Using built-in TypeScript syntax engine for validation");
        
        // Auto-detect JSX content and enable JSX mode if needed.
        if (jsxMode || containsJsxContent(content)) {
            syntaxEngine.enableJsxMode();
        }
        
        ValidationResult engineResult = syntaxEngine.validate(content);
        if (!engineResult.isValid()) {
            log.trace("[PHASE-2-FALLBACK] Built-in engine found {} errors", engineResult.getErrors().size());
        }
        return engineResult;
    }

    /**
     * Checks if the content contains JSX-like syntax patterns.
     * This allows automatic JSX mode activation for .jsx and .tsx files.
     */
    private static boolean containsJsxContent(String content) {
        // Quick check for common JSX patterns
        return content.contains("</") ||
               content.contains("/>") ||
               (content.contains("<") && content.contains(">") &&
                (content.contains("className") || content.contains("onClick") ||
                 content.contains("onChange") || content.contains("htmlFor") ||
                 content.contains("dangerouslySetInnerHTML")));
    }

    @Override
    protected String getFileExtension() {
        return jsxMode ? ".tsx" : ".ts";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        List<String> command = new ArrayList<>();

        // Check if the binary is npx (for npx tsc)
        if (binaryPath.contains("npx") || binaryPath.contains("npx.cmd") || binaryPath.contains("npx.exe")) {
            command.add(binaryPath);
            command.add("tsc");
        } else {
            command.add(binaryPath);
        }

        command.add("--noEmit");
        command.add("--pretty");
        command.add("false");
        command.add("--strict");
        command.add("--target");
        command.add("ES2020");
        command.add("--module");
        command.add("ESNext");
        command.add("--moduleResolution");
        command.add("bundler");

        if (jsxMode) {
            command.add("--jsx");
            command.add("react-jsx");
        }

        command.add("--skipLibCheck");
        command.add(tempFile.toAbsolutePath().toString());

        return command;
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.succeeded()) {
            return ValidationResult.valid("TypeScript syntax is valid (verified by tsc).");
        }

        String output = result.stderr().isEmpty() ? result.stdout() : result.stderr();

        List<ValidationError> errors = outputParser.parse(output);

        if (errors.isEmpty()) {
            // Non-zero exit code but couldn't parse specific errors
            if (!output.isBlank()) {
                return ValidationResult.invalid(
                        "TypeScript syntax errors found.",
                        new ValidationError(1, -1, output.trim(), null));
            }
            return ValidationResult.valid("TypeScript syntax is valid.");
        }

        String message = "TypeScript syntax errors found: " + errors.size() + " error(s).";
        return ValidationResult.invalid(message, errors);
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "tsc binary not found. Install TypeScript globally (npm install -g typescript) "
                + "or provide a path via the 'tsc.path' system property. "
                + "Falling back to the built-in TypeScript syntax engine.";
    }
}