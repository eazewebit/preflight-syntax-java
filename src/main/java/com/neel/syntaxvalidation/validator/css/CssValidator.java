package com.neel.syntaxvalidation.validator.css;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
/**
 * Validates CSS source code using a two-phase, binary-first strategy.
 *
 * <h2>Phase 1 - stylelint deep analysis (when available)</h2>
 * If stylelint is resolvable, the source is validated using stylelint
 * for a comprehensive CSS lint. The code is never executed.
 *
 * <h2>Phase 2 - built-in CssSyntaxEngine (fallback)</h2>
 * If stylelint is <em>not</em> available or execution fails, a fast,
 * dependency-free structural pass runs as fallback.
 *
 * <h2>Dual-engine validation</h2>
 * When the stylelint binary is available, both the binary and the built-in
 * {@link CssSyntaxEngine} are run. Results are merged: if either engine
 * detects errors, the validation fails. This ensures structural CSS errors
 * (e.g. unmatched braces, declarations outside rule blocks) that stylelint
 * may silently accept are still caught.
 */
public class CssValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "stylelint";

    private static final CssSyntaxEngine ENGINE = CssSyntaxEngine.getInstance();

    public CssValidator() {
        this((String) null);
    }

    public CssValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    public CssValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a BinaryManager for managed binary resolution.
     *
     * @param binaryManager the binary manager (may be null for PATH-only resolution).
     */
    public CssValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a validator backed by a BinaryManager with an explicit preferred path and process executor.
     *
     * @param preferredBinaryPath an explicit binary path, or null.
     * @param binaryManager       the binary manager (may be null for PATH-only resolution).
     * @param processExecutor     the process executor.
     */
    public CssValidator(String preferredBinaryPath, BinaryManager binaryManager,
                        ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryManager, processExecutor);
    }

    /**
     * Validates CSS content using <b>both</b> the stylelint binary (when available)
     * <b>and</b> the built-in {@link CssSyntaxEngine}. Results from both engines
     * are merged: if either detects errors, the combined set of errors is returned.
     *
     * <p>This override is necessary because the base class's binary-first
     * short-circuit logic (returning immediately on binary success) causes the
     * built-in structural engine to be skipped — even when stylelint's limited
     * rule set misses real syntax errors like unmatched braces or declarations
     * outside rule blocks.
     *
     * @param content  the CSS source to validate.
     * @param fileName the filename for the temp file used by stylelint.
     * @return a merged {@link ValidationResult} from both engines.
     */
    @Override
    public ValidationResult validate(String content, String fileName) {
        String safeContent = content == null ? "" : content;

        // Phase 1 - Binary validation
        Optional<String> binary = resolveBinary();
        if (binary.isPresent()) {
            String binaryPath = binary.get();
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [PHASE-1-BINARY] CSS validation: Using BINARY");
            log.info("║ Binary name: {}", BINARY_NAME);
            log.info("║ Binary path: {}", binaryPath);
            log.info("╚══════════════════════════════════════════════════════════════");

            ValidationResult binaryResult = null;
            Path tempDir = null;
            try {
                tempDir = Files.createTempDirectory("syntax-check-");
                prepareTempDirectory(tempDir);
                Path tempFile = tempDir.resolve(fileName);
                Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);
                List<String> command = buildCommand(binaryPath, tempFile);
                log.debug("[PHASE-1-BINARY] Executing command: {}", command);
                ProcessResult result = getProcessExecutor().execute(command);
                binaryResult = parseOutput(result, tempFile);
            } catch (IOException e) {
                log.error("[PHASE-1-BINARY] ❌ CSS validation: Binary I/O error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[PHASE-1-BINARY] ❌ CSS validation: Binary validation interrupted");
            } catch (Exception e) {
                log.warn("[PHASE-1-BINARY] ❌ CSS validation: Binary execution failed", e);
            } finally {
                deleteQuietly(tempDir);
            }

            // Phase 2 - Built-in engine (always runs when binary is available)
            log.info("[PHASE-2-BUILTIN] CSS validation: Running built-in CssSyntaxEngine alongside binary");
            ValidationResult builtInResult = ENGINE.validate(safeContent);

            // Merge results from both engines
            return mergeResults(binaryResult, builtInResult);
        }

        // Binary not found — fall back to built-in engine only
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ [PHASE-1-BINARY] CSS validation: Binary NOT FOUND");
        log.info("║ Binary name: {}", BINARY_NAME);
        log.info("║ Searching: System PATH and BinaryManager");
        log.info("╚══════════════════════════════════════════════════════════════");

        ValidationResult builtInResult = ENGINE.validate(safeContent);
        if (builtInResult != null) {
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [PHASE-2-FALLBACK] CSS validation: Using BUILT-IN ENGINE");
            log.info("║ Reason: Binary not available");
            log.info("╚══════════════════════════════════════════════════════════════");
            return builtInResult;
        }

        logBuiltInEngineFallback("CSS", "Binary not available and no built-in engine");
        return ValidationResult.invalid(binaryNotFoundMessage());
    }

    /**
     * Merges validation results from the binary engine and the built-in engine.
     * If either engine detected errors, the combined result is invalid with all
     * errors collected. If both passed, the result is valid with a merged message.
     *
     * @param binaryResult  the result from stylelint (may be null if binary failed).
     * @param builtInResult the result from CssSyntaxEngine (may be null).
     * @return the merged validation result.
     */
    private ValidationResult mergeResults(ValidationResult binaryResult,
                                          ValidationResult builtInResult) {
        boolean binaryValid = binaryResult == null || binaryResult.isValid();
        boolean builtInValid = builtInResult == null || builtInResult.isValid();

        if (binaryValid && builtInValid) {
            String message = (binaryResult != null ? binaryResult.getMessage() : "stylelint unavailable")
                    + " & "
                    + (builtInResult != null ? builtInResult.getMessage() : "built-in engine unavailable");
            log.info("[PHASE-1-BINARY] ✅ CSS validation: Binary validation PASSED");
            log.info("[PHASE-2-BUILTIN] ✅ CSS validation: Built-in engine PASSED");
            return ValidationResult.valid(message);
        }

        // Collect all errors from both engines
        List<ValidationError> mergedErrors = new ArrayList<>();
        String binaryMessage = "";
        String builtInMessage = "";

        if (!binaryValid && binaryResult != null) {
            binaryMessage = binaryResult.getMessage();
            mergedErrors.addAll(binaryResult.getErrors());
            log.info("[PHASE-1-BINARY] ❌ CSS validation: Binary found {} errors",
                    binaryResult.getErrors().size());
        } else {
            log.info("[PHASE-1-BINARY] ✅ CSS validation: Binary validation PASSED");
        }

        if (!builtInValid && builtInResult != null) {
            builtInMessage = builtInResult.getMessage();
            mergedErrors.addAll(builtInResult.getErrors());
            log.info("[PHASE-2-BUILTIN] ❌ CSS validation: Built-in engine found {} errors",
                    builtInResult.getErrors().size());
        } else {
            log.info("[PHASE-2-BUILTIN] ✅ CSS validation: Built-in engine PASSED");
        }

        // Sort merged errors by position
        mergedErrors.sort((a, b) -> {
            int lineCmp = Integer.compare(a.getLine(), b.getLine());
            return lineCmp != 0 ? lineCmp : Integer.compare(a.getColumn(), b.getColumn());
        });

        String mergedMessage = String.format(
                "CSS validation detected %d error(s). [%s] [%s]",
                mergedErrors.size(),
                binaryMessage.isEmpty() ? "stylelint OK" : binaryMessage,
                builtInMessage.isEmpty() ? "built-in engine OK" : builtInMessage
        );

        return ValidationResult.invalid(mergedMessage, mergedErrors);
    }

    @Override
    protected void prepareTempDirectory(Path tempDir) throws IOException {
        // MINIMAL stylelint config — syntax-only validation.
        // Only rules that detect actual CSS syntax/structural errors.
        // NO style/formatting/ordering rules. Suitable for embedded CSS in HTML/JS.
        String configJson = """
                {
                    "rules": {
                        "no-empty-source": [true, {"severity": "error"}],
                        "no-invalid-double-slash-comments": [true, {"severity": "error"}],
                        "no-invalid-position-at-import-rule": [true, {"severity": "error"}],
                        "block-no-empty": [true, {"severity": "error"}],
                        "color-no-invalid-hex": [true, {"severity": "error"}],
                        "function-calc-no-unspaced-operator": [true, {"severity": "error"}],
                        "function-linear-gradient-no-nonstandard-direction": [true, {"severity": "error"}],
                        "string-no-newline": [true, {"severity": "error"}],
                        "no-irregular-whitespace": [true, {"severity": "error"}],
                        "unit-no-unknown": [true, {"severity": "error"}]
                    }
                }
                """;

        Path configFile = tempDir.resolve(".stylelintrc.json");
        Files.writeString(configFile, configJson, StandardCharsets.UTF_8);
        log.debug("[PHASE-1-BINARY] Created stylelint config: {}", configFile);
    }

    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        // Pass temp file path as positional argument (not --stdin) since ProcessExecutor
        // does not pipe stdin. The .stylelintrc.json created by prepareTempDirectory()
        // ensures stylelint finds a config in the temp directory.
        return List.of(binaryPath, "--formatter", "json", tempFile.toAbsolutePath().toString());
    }

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        if (result.timedOut()) {
            return ValidationResult.invalid(
                    "CSS syntax validation timed out.",
                    new ValidationError(-1, -1, "The stylelint process did not finish in time.", result.stderr()));
        }

        // Always parse stdout first — stylelint's --formatter json writes errors there.
        // This catches cases where stylelint exits 0 but still reports issues, or where
        // the exit code is non-zero but stderr is empty (errors only in stdout JSON).
        String stdout = result.stdout();
        if (stdout != null && !stdout.isBlank()) {
            ValidationResult parsed = StylelintOutputParser.parse(stdout);
            if (!parsed.isValid()) {
                return parsed;
            }
        }

        // If stdout had no errors but the process failed, check stderr for fatal errors.
        if (!result.succeeded()) {
            String stderr = result.stderr();
            if (stderr != null && !stderr.isBlank()) {
                return StylelintOutputParser.parse(stderr);
            }
            return ValidationResult.invalid(
                    "CSS syntax validation failed (exit code " + result.exitCode() + ").",
                    new ValidationError(-1, -1,
                            "stylelint exited with code " + result.exitCode(), result.stderr()));
        }

        return ValidationResult.valid("CSS syntax is valid (verified by stylelint).");
    }

    @Override
    protected String getFileExtension() {
        return ".css";
    }

    @Override
    public Language getLanguage() {
        return Language.CSS;
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "stylelint binary not found. Install stylelint (npm install -g stylelint) "
                + "or provide a path via the 'stylelint.path' system property. "
                + "Falling back to the built-in CSS syntax engine.";
    }
}
