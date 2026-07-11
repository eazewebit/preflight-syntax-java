package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.model.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the diagnostic output emitted by {@code node --check} when it detects a
 * JavaScript syntax error.
 *
 * <p>Node.js reports errors in a stable format, for example:
 * <pre>
 * /tmp/syntax-check-123.js:3
 * foo bar
 *     ^^^
 *
 * SyntaxError: Unexpected identifier
 *     at wrapSafe (node:internal/modules/cjs/loader:...)
 * </pre>
 *
 * <p>This parser extracts the line and column (when present), the
 * {@code SyntaxError} message, and retains the raw tool output so it can be
 * surfaced to the caller. The parser is pure (no I/O) which makes it trivially
 * unit-testable.
 */
public class NodeCheckOutputParser {

    /** Matches the {@code SyntaxError: <message>} line. */
    private static final Pattern SYNTAX_ERROR_PATTERN = Pattern.compile("SyntaxError:\\s*(.*)");

    /**
     * Matches a trailing {@code :line} or {@code :line:column} location at the end
     * of a line, e.g. the {@code :3} in {@code C:\app\file.js:3}.
     */
    private static final Pattern LOCATION_PATTERN =
            Pattern.compile(":([0-9]+)(?::([0-9]+))?\\s*$");

    /**
     * Parses the given tool output into a list of detailed errors.
     *
     * @param output the raw {@code node} output (stdout and/or stderr).
     * @return a list of {@link ValidationError}s; never {@code null} but possibly
     *         empty when the output contains no recognisable error.
     */
    public List<ValidationError> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        Integer line = null;
        Integer column = null;
        String message = null;

        for (String raw : output.split("\\R")) {
            String current = raw.stripTrailing();
            if (message == null) {
                Matcher errorMatcher = SYNTAX_ERROR_PATTERN.matcher(current);
                if (errorMatcher.find()) {
                    message = errorMatcher.group(1).strip();
                    continue;
                }
            }
            if (line == null) {
                Matcher locMatcher = LOCATION_PATTERN.matcher(current);
                if (locMatcher.find()) {
                    line = Integer.parseInt(locMatcher.group(1));
                    if (locMatcher.groupCount() >= 2 && locMatcher.group(2) != null) {
                        column = Integer.parseInt(locMatcher.group(2));
                    }
                }
            }
        }

        List<ValidationError> errors = new ArrayList<>();
        if (message != null) {
            errors.add(new ValidationError(
                    line != null ? line : -1,
                    column != null ? column : -1,
                    message,
                    output.strip()));
        } else {
            // Node emitted a non-zero exit but no recognisable SyntaxError line.
            errors.add(new ValidationError(-1, -1,
                    "Unrecognised syntax error reported by Node.js.",
                    output.strip()));
        }
        return errors;
    }
}
