package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.AbstractLanguageValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates TypeScript syntax using a two-phase, binary-first approach.
 *
 * <h2>Phase 1 &mdash; tsc deep analysis (when available)</h2>
 * When the TypeScript compiler ({@code tsc}) is available on the system
 * &mdash; either from an explicitly supplied preferred path, from a
 * {@link BinaryManager}, or from the system {@code PATH} &mdash; the full
 * {@code tsc --noEmit} pipeline runs as the first pass, catching the most
 * comprehensive set of syntax errors.
 *
 * <h2>Phase 2 &mdash; Built-in TypeScript syntax engine (fallback)</h2>
 * If {@code tsc} is <em>not</em> available or execution fails, a pure-Java
 * {@link TypeScriptSyntaxEngine} runs as fallback, with zero external
 * dependencies.
 */
public class TypeScriptValidator extends AbstractLanguageValidator {

    /** The bare name of the tsc binary searched on the {@code PATH}. */
    public static final String BINARY_NAME = "tsc";

    private static final TscOutputParser PARSER = new TscOutputParser();
    private static final TypeScriptSyntaxEngine SYNTAX_ENGINE = new TypeScriptSyntaxEngine();

    // ---- configuration properties (non-null defaults) ----
    private String target = "ESNext";
    private String module = "ESNext";
    private String moduleResolution = "bundler";
    private final boolean jsxMode;

    /**
     * Creates a validator that resolves {@code tsc} from the system {@code PATH}.
     */
    public TypeScriptValidator() {
        this((String) null, false);
    }

    /**
     * Creates a validator for the given language (ignoring the language value;
     * always validates TypeScript). Resolves {@code tsc} from the system
     * {@code PATH}.
     *
     * @param language the language (unused, present for API symmetry).
     */
    public TypeScriptValidator(Language language) {
        this((String) null, false);
    }

    /**
     * @param preferredBinaryPath an explicit path to the {@code tsc} binary, or
     *                            {@code null} to resolve from the {@code PATH}.
     */
    public TypeScriptValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, false);
    }

    /**
     * Full constructor used by the {@link com.neel.syntaxvalidation.validator.ValidatorFactory}
     * and tests.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryResolver      the binary resolver.
     * @param processExecutor     the process executor.
     */
    public TypeScriptValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                               ProcessExecutor processExecutor) {
        this(preferredBinaryPath, false, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} for managed binary
     * resolution (download, cache, version-check).
     *
     * @param binaryManager the binary manager (may be {@code null} for
     *                      PATH-only resolution).
     */
    public TypeScriptValidator(BinaryManager binaryManager) {
        this(null, false, binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} with an explicit
     * preferred path and process executor.
     *
     * @param preferredBinaryPath an explicit binary path, or {@code null}.
     * @param binaryManager       the binary manager (may be {@code null}).
     * @param processExecutor     the process executor to use.
     */
    public TypeScriptValidator(String preferredBinaryPath, BinaryManager binaryManager,
                               ProcessExecutor processExecutor) {
        this(preferredBinaryPath, false, binaryManager, processExecutor);
    }

    /**
     * Creates a validator with the given binary resolver and process executor.
     * Resolves {@code tsc} from the system {@code PATH}.
     *
     * @param binaryResolver  the binary resolver.
     * @param processExecutor the process executor.
     */
    public TypeScriptValidator(BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        this(null, false, binaryResolver, processExecutor);
    }

    /**
     * Private canonical constructor.
     */
    private TypeScriptValidator(String preferredBinaryPath, boolean jsxMode) {
        super(preferredBinaryPath, BINARY_NAME);
        this.jsxMode = jsxMode;
    }

    /**
     * Private constructor with explicit collaborators.
     */
    private TypeScriptValidator(String preferredBinaryPath, boolean jsxMode,
                                BinaryResolver binaryResolver, ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
        this.jsxMode = jsxMode;
    }

    /**
     * Private constructor with binary manager.
     */
    private TypeScriptValidator(String preferredBinaryPath, boolean jsxMode,
                                BinaryManager binaryManager, ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
        this.jsxMode = jsxMode;
    }

    /**
     * Creates a JSX-aware validator that uses {@code .tsx} as the default
     * temp-file extension and passes {@code --jsx react-jsx} to tsc.
     *
     * @param binaryResolver  the binary resolver.
     * @param processExecutor the process executor.
     * @return a new {@code TypeScriptValidator} in JSX mode.
     */
    public static TypeScriptValidator createJsxValidator(BinaryResolver binaryResolver,
                                                         ProcessExecutor processExecutor) {
        return new TypeScriptValidator(null, true, binaryResolver, processExecutor);
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
        ValidationResult engineResult = SYNTAX_ENGINE.validate(content);
        if (!engineResult.isValid()) {
            log.trace("[PHASE-2-FALLBACK] Built-in engine found {} errors", engineResult.getErrors().size());
        }
        return engineResult;
    }

    @Override
    public String getFileExtension() {
        return jsxMode ? ".tsx" : ".ts";
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        List<String> command = new ArrayList<>();
        command.add(binaryPath);
        command.add("--noEmit");
        command.add("--pretty");
        command.add("false");
        command.add("--noResolve");
        command.add("--target");
        command.add(target);
        command.add("--module");
        command.add(module);
        command.add("--moduleResolution");
        command.add(moduleResolution);

        if (jsxMode || tempFile.toString().endsWith(".tsx") || tempFile.toString().endsWith(".jsx")) {
            command.add("--jsx");
            command.add("react-jsx");
            command.add("--allowJs");
        }

        command.add("--skipLibCheck");
        command.add(tempFile.toAbsolutePath().toString());

        return command;
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "TypeScript syntax validation timed out.",
                    new ValidationError(-1, -1, "The tsc process did not finish in time.", result.stderr()));
        }
        if (result.succeeded()) {
            return ValidationResult.valid("TypeScript syntax is valid (verified by tsc).");
        }

        String output = result.stderr().isBlank() ? result.stdout() : result.stderr();
        List<ValidationError> errors = PARSER.parse(output);

        if (errors.isEmpty()) {
            // Non-zero exit code but couldn't parse specific errors
            if (!output.isBlank()) {
                return ValidationResult.invalid(
                        "TypeScript syntax errors found.",
                        new ValidationError(1, -1, output.trim(), null));
            }
            return ValidationResult.valid("TypeScript syntax is valid.");
        }

        String summary = errors.isEmpty()
                ? "TypeScript syntax validation failed."
                : "TypeScript syntax validation failed with " + errors.size() + " error(s).";
        return ValidationResult.invalid(summary, errors);
    }

    @Override
    public String binaryNotFoundMessage() {
        return "tsc binary not found. Install TypeScript globally (npm install -g typescript) "
                + "or provide a path via the 'tsc.path' system property. "
                + "Falling back to the built-in TypeScript syntax engine.";
    }

    // ---- configuration setters ----

    /**
     * Sets the ECMAScript target version used when invoking tsc.
     * @param target e.g. "ES2015", "ES2020", "ESNext".
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * Sets the module system used when invoking tsc.
     * @param module e.g. "CommonJS", "ESNext".
     */
    public void setModule(String module) {
        this.module = module;
    }

    /**
     * Sets the module resolution strategy used when invoking tsc.
     * @param moduleResolution e.g. "node", "node16", "bundler".
     */
    public void setModuleResolution(String moduleResolution) {
        this.moduleResolution = moduleResolution;
    }

    public String getTarget() {
        return target;
    }

    public String getModule() {
        return module;
    }

    public String getModuleResolution() {
        return moduleResolution;
    }
}
