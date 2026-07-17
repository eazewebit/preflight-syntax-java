package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the standard output of {@code php -l} (lint mode) into a
 * structured {@link ValidationResult}.
 *
 * <p>The {@code php -l} command produces output of the form:</p>
 * <pre>
 * Parse error: syntax error, unexpected '$x' (T_VARIABLE), expecting ';' in /path/to/file.php on line 42
 * </pre>
 * <p>or, when no errors are found:</p>
 * <pre>
 * No syntax errors detected in /path/to/file.php
 * </pre>
 *
 * <p>This parser handles both patterns and also gracefully handles empty
 * or unexpected output.</p>
 *
 * @since 1.1.0
 */
final class PhpOutputParser {

    /**
     * Pattern that matches PHP lint error messages.
     */
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "^(Parse error|Fatal error|Warning|Notice|Deprecated|Error)\\s*:\\s*(.+?)\\s+in\\s+.+?\\s+on\\s+line\\s+(\\d+)",
            Pattern.MULTILINE
    );

    /**
     * Simpler pattern for messages that don't follow the standard format.
     */
    private static final Pattern SIMPLE_ERROR_PATTERN = Pattern.compile(
            "^(Parse error|Fatal error|Warning|Notice|Deprecated|Error)\\s*:\\s*(.+)",
            Pattern.MULTILINE
    );

    /**
     * Alternative pattern for PHP 8.x formatted output.
     */
    private static final Pattern ALT_ERROR_PATTERN = Pattern.compile(
            "^(.+?):\\s+(.+?)\\s+on\\s+line\\s+(\\d+)",
            Pattern.MULTILINE
    );

    /** Status line when PHP lint passes. */
    private static final String NO_SYNTAX_ERRORS = "No syntax errors detected";

    PhpOutputParser() { /* package-private */ }

    /**
     * Parses the raw output of {@code php -l}.
     *
     * @param rawOutput the combined stdout+stderr from the PHP lint process.
     * @return a {@link ValidationResult}.
     */
    ValidationResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return ValidationResult.valid("Empty output is treated as valid.");
        }

        if (rawOutput.contains(NO_SYNTAX_ERRORS)) {
            return ValidationResult.valid("PHP syntax is valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Try the primary regex first
        Matcher primary = ERROR_PATTERN.matcher(rawOutput);
        boolean found = false;
        while (primary.find()) {
            found = true;
            String severity = primary.group(1);
            String message = primary.group(2);
            int line = Integer.parseInt(primary.group(3));
            errors.add(new ValidationError(line, -1, severity + ": " + message, null));
        }

        if (!found) {
            // Try the alternative pattern
            Matcher alt = ALT_ERROR_PATTERN.matcher(rawOutput);
            while (alt.find()) {
                found = true;
                String message = alt.group(1) + ": " + alt.group(2);
                int line = Integer.parseInt(alt.group(3));
                errors.add(new ValidationError(line, -1, message, null));
            }
        }

        if (!found) {
            // Fall back to the simple pattern (no line number)
            Matcher simple = SIMPLE_ERROR_PATTERN.matcher(rawOutput);
            while (simple.find()) {
                found = true;
                String severity = simple.group(1);
                String message = simple.group(2).trim();
                errors.add(new ValidationError(1, -1, severity + ": " + message, null));
            }
        }

        if (!found && !rawOutput.isBlank()) {
            errors.add(new ValidationError(1, -1, rawOutput.trim(), null));
        }

        return errors.isEmpty()
                ? ValidationResult.valid("PHP syntax is valid.")
                : ValidationResult.invalid("PHP syntax errors detected.", errors);
    }
}
