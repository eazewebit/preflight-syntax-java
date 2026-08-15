package com.neel.syntaxvalidation.validator.html;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates <b>HTML</b> documents using a two-phase strategy.
 *
 * <h2>Phase 1 - Nu Html Checker ({@code vnu.jar})</h2>
 * Runs the WHATWG Nu Html Checker via {@code java -jar vnu.jar --format json}
 * for authoritative validation. When the binary path points to a {@code .jar}
 * file, the command is automatically prefixed with the {@code java} runtime
 * (resolved from the running JVM's {@code java.home}).
 *
 * <h2>Phase 2 - Built-in engine</h2>
 * Falls back to a lightweight regex-based checker when the binary is
 * unavailable or execution fails.
 */
public class HtmlValidator extends AbstractLanguageValidator implements LanguageValidator {

    static final String BINARY_NAME = "vnu";

    private static final HtmlSyntaxEngine ENGINE = HtmlSyntaxEngine.getInstance();

    // ----------------------------------------------------------------
    //  Constructors
    // ----------------------------------------------------------------

    /** No-arg constructor for language-only queries. */
    public HtmlValidator() {
        this((String) null);
    }

    public HtmlValidator(String preferredBinaryPath) {
        this(preferredBinaryPath, new BinaryResolver(), new ProcessExecutor());
    }

    public HtmlValidator(String preferredBinaryPath, BinaryResolver binaryResolver,
                         ProcessExecutor processExecutor) {
        super(preferredBinaryPath, BINARY_NAME, binaryResolver, processExecutor);
    }

    /**
     * Creates a validator backed by a {@link BinaryManager} for managed binary resolution.
     *
     * @param binaryManager the binary manager (may be {@code null} for PATH-only resolution).
     */
    public HtmlValidator(BinaryManager binaryManager) {
        super(null, BINARY_NAME, binaryManager, new ProcessExecutor());
    }

    // ----------------------------------------------------------------
    //  LanguageValidator
    // ----------------------------------------------------------------

    @Override
    public Language getLanguage() {
        return Language.HTML;
    }

    @Override
    public ValidationResult validate(String content) {
        return validate(content, "validate.html");
    }

    @Override
    public ValidationResult validate(String content, String fileName) {
        String safeContent = content == null ? "" : content;

        // Phase 1 - Binary validation
        Optional<String> binary = resolveBinary();
        if (binary.isPresent()) {
            String binaryPath = binary.get();
            log.info("[PHASE-1-BINARY] HTML validation: Using BINARY");
            log.info("Binary name: {}", BINARY_NAME);
            log.info("Binary path: {}", binaryPath);

            try {
                Path tempDir = Files.createTempDirectory("html-validate-");
                try {
                    Path tempFile = tempDir.resolve(fileName);
                    Files.writeString(tempFile, safeContent, StandardCharsets.UTF_8);

                    List<String> command = buildCommand(binaryPath, tempFile);
                    log.debug("[PHASE-1-BINARY] Executing command: {}", command);

                    ProcessResult result = getProcessExecutor().execute(command);
                    ValidationResult binaryResult = parseVnuJsonOutput(result);

                    if (binaryResult != null) {
                        if (binaryResult.isValid()) {
                            log.info("[PHASE-1-BINARY] HTML validation: Binary validation PASSED");
                            return binaryResult;
                        }
                        log.info("[PHASE-1-BINARY] HTML validation: Binary found {} error(s)",
                                binaryResult.getErrors().size());
                        return binaryResult;
                    }
                } finally {
                    deleteQuietly(tempDir);
                }
            } catch (IOException e) {
                log.error("[PHASE-1-BINARY] HTML validation: Binary I/O error", e);
            } catch (Exception e) {
                log.error("[PHASE-1-BINARY] HTML validation: Binary execution failed", e);
            }

            log.info("[PHASE-2-FALLBACK] HTML validation: Binary returned no result, falling back");
        } else {
            log.info("[PHASE-2-FALLBACK] HTML validation: Binary not available");
        }

        log.info("[PHASE-2-FALLBACK] HTML validation: Using BUILT-IN ENGINE");
        return validateWithBuiltInEngine(safeContent);
    }

    // ----------------------------------------------------------------
    //  Binary execution helpers
    // ----------------------------------------------------------------

    /**
     * Builds the CLI command for the Nu Html Checker.
     *
     * <p>When the binary path points to a {@code .jar} file, the command is
     * prefixed with the {@code java} runtime so that the JAR is executed via
     * {@code java -jar vnu.jar ...} rather than being invoked directly (which
     * fails on Windows with "not a valid Win32 application").
     *
     * @param binaryPath resolved path to {@code vnu.jar}
     * @param tempFile   temp file containing the HTML to validate
     * @return the full CLI command
     */
    @Override
    protected List<String> buildCommand(String binaryPath, Path tempFile) {
        if (binaryPath != null && binaryPath.toLowerCase().endsWith(".jar")) {
            String javaExe = resolveJavaExecutable();
            log.info("[HTML-VALIDATOR] JAR detected - using Java runtime: {}", javaExe);
            return List.of(javaExe, "-jar", binaryPath, "--format", "json",
                    tempFile.toAbsolutePath().toString());
        }
        return List.of(binaryPath, "--format", "json", tempFile.toAbsolutePath().toString());
    }

    /**
     * Resolves the path to the {@code java} executable.
     *
     * <p>Prefers the JVM that is currently running this code (via
     * {@code java.home}), falling back to a bare {@code "java"} command that
     * relies on the system {@code PATH}.
     *
     * @return absolute path to {@code java} (or {@code java.exe} on Windows),
     *         or simply {@code "java"} if the runtime cannot be located.
     */
    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String exeName = isWindows ? "java.exe" : "java";
            Path javaBin = Path.of(javaHome, "bin", exeName);
            if (Files.exists(javaBin)) {
                return javaBin.toAbsolutePath().toString();
            }
            // Some JDK distributions place java.home in the jre/ subdirectory;
            // try one level up as well.
            Path parent = Path.of(javaHome).getParent();
            if (parent != null) {
                Path parentJavaBin = parent.resolve("bin").resolve(exeName);
                if (Files.exists(parentJavaBin)) {
                    return parentJavaBin.toAbsolutePath().toString();
                }
            }
        }
        // Last resort: hope 'java' is on PATH
        return "java";
    }

    // ----------------------------------------------------------------
    //  AbstractLanguageValidator overrides
    // ----------------------------------------------------------------

    @Override
    protected ValidationResult parseOutput(ProcessResult result, Path tempFile) {
        // Not directly used - validate(String, String) handles the full flow.
        // Provided to satisfy the abstract contract.
        ValidationResult parsed = parseVnuJsonOutput(result);
        return parsed != null ? parsed
                : ValidationResult.invalid("HTML validation: unable to parse output.");
    }

    @Override
    protected String getFileExtension() {
        return ".html";
    }

    @Override
    protected String binaryNotFoundMessage() {
        return "vnu.jar binary not found. Install the Nu Html Checker or provide a path via "
                + "the 'vnu.path' system property. Falling back to the built-in HTML syntax engine.";
    }

    // ----------------------------------------------------------------
    //  vnu.jar JSON output parsing
    // ----------------------------------------------------------------

    /**
     * Parses the JSON output emitted by {@code vnu.jar --format json}.
     *
     * <p>Expected format:
     * <pre>{@code
     * {
     *   "messages": [
     *     { "type": "error", "lastLine": 10, "lastColumn": 5, "message": "..." },
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param result the process result from running vnu
     * @return parsed ValidationResult, or {@code null} if the output cannot be parsed
     */
    private ValidationResult parseVnuJsonOutput(ProcessResult result) {
        // vnu writes JSON to stderr when using --format json
        String output = result.stderr();
        if (output == null || output.isBlank()) {
            output = result.stdout();
        }
        if (output == null || output.isBlank()) {
            // No output at all - vnu may have exited 0 with no errors
            if (result.succeeded()) {
                return ValidationResult.valid("HTML is valid (verified by vnu).");
            }
            return null;
        }

        // Try JSON parsing first
        try {
            return parseVnuJson(output.trim());
        } catch (Exception e) {
            log.debug("[HTML-VALIDATOR] JSON parsing failed, trying text fallback: {}", e.getMessage());
        }

        // Fallback: parse text output (line:col: error: msg)
        return parseVnuTextOutput(output);
    }

    /** Very simple JSON parser for vnu output - avoids external JSON library dependency. */
    private ValidationResult parseVnuJson(String json) {
        List<ValidationError> errors = new ArrayList<>();

        // Quick check: is there a "messages" array?
        int messagesIdx = json.indexOf("\"messages\"");
        if (messagesIdx < 0) {
            // No messages array - treat as valid
            return ValidationResult.valid("HTML is valid (verified by vnu).");
        }

        // Extract each message block using a simple state machine
        String messagesSection = json.substring(messagesIdx);
        int arrayStart = messagesSection.indexOf('[');
        int arrayEnd = messagesSection.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < 0 || arrayEnd <= arrayStart) {
            return ValidationResult.valid("HTML is valid (verified by vnu).");
        }

        String arrayContent = messagesSection.substring(arrayStart + 1, arrayEnd).trim();
        if (arrayContent.isEmpty()) {
            return ValidationResult.valid("HTML is valid (verified by vnu).");
        }

        // Split on },{ to get individual message objects
        String[] messageBlocks = arrayContent.split("\\}\\s*,\\s*\\{");

        for (String block : messageBlocks) {
            String type = extractJsonValue(block, "type");
            if (!"error".equalsIgnoreCase(type)) {
                continue; // Skip non-error messages (warnings, info)
            }

            int lastLine = extractJsonInt(block, "lastLine");
            int lastColumn = extractJsonInt(block, "lastColumn");
            String message = extractJsonValue(block, "message");

            if (message != null && !message.isBlank()) {
                errors.add(new ValidationError(
                        lastLine > 0 ? lastLine : -1,
                        lastColumn > 0 ? lastColumn : -1,
                        message,
                        block.trim()));
            }
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid("HTML is valid (verified by vnu).");
        }

        return ValidationResult.invalid(
                String.format("HTML validation detected %d error(s).", errors.size()),
                errors);
    }

    /** Extracts a string value for the given key from a simple JSON object fragment. */
    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (!m.find()) return null;
        int valueStart = m.end();
        // Find the closing quote, handling escaped quotes
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Extracts an integer value for the given key from a simple JSON object fragment. */
    private static int extractJsonInt(String json, String key) {
        String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)";
        Matcher m = Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** Fallback text output parser: {@code "file.html:10.5: error: Some message"} */
    private static final Pattern VNU_TEXT_PATTERN =
            Pattern.compile(":(\\d+)(?:\\.(\\d+))?:\\s+error:\\s+(.+)");

    private ValidationResult parseVnuTextOutput(String output) {
        List<ValidationError> errors = new ArrayList<>();

        for (String line : output.lines().toList()) {
            Matcher m = VNU_TEXT_PATTERN.matcher(line);
            if (m.find()) {
                int lineNum = Integer.parseInt(m.group(1));
                int col = m.group(2) != null ? Integer.parseInt(m.group(2)) : -1;
                String msg = m.group(3).trim();
                errors.add(new ValidationError(lineNum, col, msg, line));
            }
        }

        if (errors.isEmpty() && !output.isBlank()) {
            errors.add(new ValidationError(-1, -1, output.trim(), output.trim()));
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid("HTML is valid (verified by vnu).");
        }

        return ValidationResult.invalid(
                String.format("HTML validation detected %d error(s).", errors.size()),
                errors);
    }

    // ----------------------------------------------------------------
    //  Built-in engine
    // ----------------------------------------------------------------

    /**
     * Runs the built-in HTML syntax engine as a fallback.
     *
     * @param content the HTML content to validate
     * @return the validation result from the built-in engine
     */
    public ValidationResult validateWithBuiltInEngine(String content) {
        return ENGINE.validate(content);
    }
}