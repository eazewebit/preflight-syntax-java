package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output produced by <a href="https://stylelint.io/">stylelint</a>
 * when invoked with the {@code --formatter json} flag.
 *
 * <p>Stylelint emits a JSON array where each element corresponds to a file.
 * Each element contains:
 * <ul>
 *   <li>{@code "source"} — the file path that was linted.</li>
 *   <li>{@code "warnings"} — an array of warning objects, each with:
 *     <ul>
 *       <li>{@code "line"} — the 1-based line number.</li>
 *       <li>{@code "column"} — the 1-based column number.</li>
 *       <li>{@code "text"} — the human-readable diagnostic text.</li>
 *       <li>{@code "severity"} — {@code "error"} or {@code "warning"}.</li>
 *       <li>{@code "rule"} — the stylelint rule that triggered the warning.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This parser is lenient: if the JSON cannot be parsed, it falls back to
 * line-by-line text extraction to salvage diagnostics from plain-text output.
 *
 * <p><b>Thread-safety.</b> The parser is stateless and safe for concurrent use.
 */
final class StylelintOutputParser {

    private StylelintOutputParser() {
    }

    // ------------------------------------------------------------------
    //  JSON extraction patterns
    // ------------------------------------------------------------------

    /**
     * Matches a warning object inside the stylelint JSON output.
     */
    private static final Pattern WARNING_BLOCK_PATTERN =
            Pattern.compile(
                    "\\{\\s*\"line\"\\s*:\\s*(\\d+).*?}",
                    Pattern.DOTALL
            );

    /**
     * Extracts a string value for a given key within a warning block.
     */
    private static final Pattern STRING_VALUE_PATTERN =
            Pattern.compile(
                    "\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                    Pattern.DOTALL
            );

    /**
     * Extracts an integer value for a given key within a warning block.
     */
    private static final Pattern INT_VALUE_PATTERN =
            Pattern.compile(
                    "\"(\\w+)\"\\s*:\\s*(\\d+)",
                    Pattern.DOTALL
            );

    /**
     * Plain-text fallback pattern for stylelint output that is not JSON.
     * Typical format:
     * {@code  10:5  error  Some message  rule-name}
     */
    private static final Pattern TEXT_LINE_PATTERN =
            Pattern.compile(
                    "^\\s*(\\d+):(\\d+)\\s+(error|warning)\\s+(.+?)\\s+(\\S+)$",
                    Pattern.MULTILINE
            );

    // ------------------------------------------------------------------
    //  Parsing
    // ------------------------------------------------------------------

    /**
     * Parses the raw output from stylelint into a {@link ValidationResult}.
     *
     * @param rawOutput the full stdout captured from a stylelint invocation.
     * @return a {@link ValidationResult} containing any errors found.
     */
    static ValidationResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return ValidationResult.valid(
                    "stylelint produced no output — the CSS is syntactically valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Try JSON parsing first
        String trimmed = rawOutput.strip();
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJsonWarnings(trimmed, errors);
        }

        // If JSON parsing found nothing, try plain-text fallback
        if (errors.isEmpty()) {
            parsePlainTextOutput(rawOutput, errors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid(
                    "stylelint produced no errors — the CSS is syntactically valid.");
        }

        return ValidationResult.invalid(
                "CSS validation by stylelint detected " + errors.size() + " error(s).",
                errors);
    }

    // ------------------------------------------------------------------
    //  JSON-based parsing (heuristic, no external JSON library)
    // ------------------------------------------------------------------

    private static void parseJsonWarnings(String json, List<ValidationError> errors) {
        Matcher blockMatcher = WARNING_BLOCK_PATTERN.matcher(json);

        while (blockMatcher.find()) {
            String block = blockMatcher.group();

            String severity = extractStringValue(block, "severity");
            String message = extractStringValue(block, "text");
            int line = extractIntValue(block, "line");
            int col = extractIntValue(block, "column");

            // Only consider "error" severity — warnings are not syntax errors
            if (!"error".equalsIgnoreCase(severity)) {
                continue;
            }

            if (message == null || message.isBlank()) {
                continue;
            }

            errors.add(new ValidationError(
                    Math.max(line, 1),
                    Math.max(col, 1),
                    message,
                    null
            ));
        }
    }

    private static String extractStringValue(String block, String key) {
        Matcher m = STRING_VALUE_PATTERN.matcher(block);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return unescapeJson(m.group(2));
            }
        }
        return "";
    }

    private static int extractIntValue(String block, String key) {
        Matcher m = INT_VALUE_PATTERN.matcher(block);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                try {
                    return Integer.parseInt(m.group(2));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    // ------------------------------------------------------------------
    //  Plain-text fallback parsing
    // ------------------------------------------------------------------

    private static void parsePlainTextOutput(String rawOutput, List<ValidationError> errors) {
        Matcher lineMatcher = TEXT_LINE_PATTERN.matcher(rawOutput);

        while (lineMatcher.find()) {
            int line;
            int col;
            String severity = lineMatcher.group(3);
            String message = lineMatcher.group(4);

            try {
                line = Integer.parseInt(lineMatcher.group(1));
                col = Integer.parseInt(lineMatcher.group(2));
            } catch (NumberFormatException e) {
                continue;
            }

            if (!"error".equalsIgnoreCase(severity)) {
                continue;
            }

            errors.add(new ValidationError(line, col, message, null));
        }
    }
}
