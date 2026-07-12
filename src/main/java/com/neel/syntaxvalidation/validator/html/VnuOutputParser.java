package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output produced by the Nu Html Checker ({@code vnu.jar}) when
 * invoked with the {@code --format json} flag.
 *
 * <p>vnu.jar emits a JSON object with a {@code "messages"} array.  Each
 * message contains at least:
 * <ul>
 *   <li>{@code "type"} — {@code "error"} or {@code "info"}/"warning"}.</li>
 *   <li>{@code "message"} — the human-readable diagnostic text.</li>
 *   <li>{@code "lastLine"} (optional) — the 1-based line number.</li>
 *   <li>{@code "lastColumn"} (optional) — the 1-based column number.</li>
 *   <li>{@code "extract"} (optional) — the offending source extract.</li>
 * </ul>
 *
 * <p>This parser is lenient: if the JSON cannot be parsed (e.g. because vnu.jar
 * emitted plain-text output instead), it falls back to line-by-line text
 * extraction to salvage as many diagnostics as possible.
 *
 * <p><b>Thread-safety.</b> The parser is stateless and safe for concurrent use.
 */
final class VnuOutputParser {

    private VnuOutputParser() {
    }

    // ------------------------------------------------------------------
    //  JSON extraction patterns
    // ------------------------------------------------------------------

    /**
     * Matches a single message object inside the JSON "messages" array.
     * This is a heuristic regex — we deliberately avoid a full JSON parser
     * to keep the library dependency-free.
     */
    private static final Pattern MESSAGE_BLOCK_PATTERN =
            Pattern.compile(
                    "\\{\\s*\"type\"\\s*:\\s*\"(error|warning|info)\".*?}",
                    Pattern.DOTALL
            );

    /**
     * Extracts a JSON string value for a given key within a message block.
     * Handles escaped quotes inside the value.
     */
    private static final Pattern STRING_VALUE_PATTERN =
            Pattern.compile(
                    "\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"",
                    Pattern.DOTALL
            );

    /**
     * Extracts a JSON integer value for a given key within a message block.
     */
    private static final Pattern INT_VALUE_PATTERN =
            Pattern.compile(
                    "\"(\\w+)\"\\s*:\\s*(\\d+)",
                    Pattern.DOTALL
            );

    /**
     * Plain-text fallback pattern for vnu output that is not JSON.
     * Typical text format:
     * {@code "file.html":10.5-10.15: error: Some message}
     * or:
     * {@code file.html:10.5: error: Some message}
     */
    private static final Pattern TEXT_FALLBACK_PATTERN =
            Pattern.compile(
                    ".*?(?:error|warning|info)\\s*:\\s*(.*)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern TEXT_LINE_COL_PATTERN =
            Pattern.compile(
                    "(\\d+)\\.(\\d+)(?:-(\\d+)\\.(\\d+))?",
                    Pattern.DOTALL
            );

    /**
     * Parses the raw output from vnu.jar into a {@link ValidationResult}.
     *
     * @param rawOutput the full stdout/stderr captured from a vnu.jar invocation.
     * @return a {@link ValidationResult} containing any errors found.
     */
    static ValidationResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return ValidationResult.valid(
                    "vnu.jar produced no output — the HTML is syntactically valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Try JSON parsing first
        int jsonStart = rawOutput.indexOf('{');
        int jsonEnd = rawOutput.lastIndexOf('}');

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            String jsonPayload = rawOutput.substring(jsonStart, jsonEnd + 1);
            parseJsonMessages(jsonPayload, errors);
        }

        // If JSON parsing found nothing, try plain-text fallback
        if (errors.isEmpty()) {
            parsePlainTextOutput(rawOutput, errors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid(
                    "vnu.jar produced no errors — the HTML is syntactically valid.");
        }

        return ValidationResult.invalid(
                "HTML validation by vnu.jar detected " + errors.size() + " error(s).",
                errors);
    }

    // ------------------------------------------------------------------
    //  JSON-based parsing (heuristic, no external JSON library)
    // ------------------------------------------------------------------

    private static void parseJsonMessages(String json, List<ValidationError> errors) {
        Matcher blockMatcher = MESSAGE_BLOCK_PATTERN.matcher(json);

        while (blockMatcher.find()) {
            String type = blockMatcher.group(1);
            String block = blockMatcher.group();

            // Only consider "error" type messages — warnings and info are
            // not syntax errors.
            if (!"error".equalsIgnoreCase(type)) {
                continue;
            }

            String message = extractStringValue(block, "message");
            int line = extractIntValue(block, "lastLine");
            int col = extractIntValue(block, "lastColumn");

            if (line <= 0) {
                line = extractIntValue(block, "firstLine");
            }
            if (col <= 0) {
                col = extractIntValue(block, "firstColumn");
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
        return null;
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
        String[] lines = rawOutput.split("\\R");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Check if this line contains an error indicator
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (!lower.contains("error")) {
                continue;
            }

            // Try to extract line/column info
            int lineNum = 1;
            int colNum = 1;
            Matcher lcMatcher = TEXT_LINE_COL_PATTERN.matcher(trimmed);
            if (lcMatcher.find()) {
                try {
                    lineNum = Integer.parseInt(lcMatcher.group(1));
                    colNum = Integer.parseInt(lcMatcher.group(2));
                } catch (NumberFormatException e) {
                    // keep defaults
                }
            }

            // Extract the message portion after "error:"
            String message = trimmed;
            int errorIdx = lower.indexOf("error:");
            if (errorIdx >= 0) {
                message = trimmed.substring(errorIdx + 6).trim();
            }

            if (!message.isBlank()) {
                errors.add(new ValidationError(lineNum, colNum, message, null));
            }
        }
    }
}
