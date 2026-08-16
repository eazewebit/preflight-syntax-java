package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.model.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the standard output of the {@code tsc} (TypeScript compiler) binary.
 * <p>
 * The TypeScript compiler produces diagnostics in the following formats:
 * <pre>
 * file.ts(line,col): error TS1234: Message text
 * </pre>
 * or when {@code --pretty false} is used:
 * <pre>
 * file.ts:line:col - error TS1234: Message text
 * </pre>
 * <p>
 * This parser extracts only <em>syntax error</em> diagnostics (TS1xxx errors) 
 * and converts them into {@link ValidationError} instances.
 * Type errors (TS2xxx) and other non-syntax errors are ignored.
 */
final class TscOutputParser {

    // Pattern for tsc output with --pretty false:
    // file.ts:line:col - error TS1xxx: message (only syntax errors)
    private static final Pattern PRETTY_FALSE_SYNTAX_PATTERN = Pattern.compile(
            "^.+?:(\\d+):(\\d+)\\s*-\\s*error\\s+TS1\\d{3}:\\s*(.+)$",
            Pattern.MULTILINE
    );

    // Pattern for tsc output without --pretty (default format):
    // file.ts(line,col): error TS1xxx: message (only syntax errors)
    private static final Pattern DEFAULT_SYNTAX_PATTERN = Pattern.compile(
            "^.+?\\((\\d+),(\\d+)\\):\\s*error\\s+TS1\\d{3}:\\s*(.+)$",
            Pattern.MULTILINE
    );

    // Pattern for tsc output with column ranges:
    // file.ts(line,col) - error TS1xxx: message (only syntax errors)
    private static final Pattern COLUMN_RANGE_SYNTAX_PATTERN = Pattern.compile(
            "^.+?\\((\\d+),(\\d+)\\)\\s*-\\s*error\\s+TS1\\d{3}:\\s*(.+)$",
            Pattern.MULTILINE
    );

    // General pattern for all errors (used to check if there are any errors at all)
    private static final Pattern ANY_ERROR_PATTERN = Pattern.compile(
            "^.+?:\\s*error\\s+TS\\d+:\\s*.+$",
            Pattern.MULTILINE
    );

    /**
     * Parse tsc compiler output and extract syntax error diagnostics.
     * <p>
     * Only TS1xxx errors (syntax errors) are extracted. TS2xxx errors (type errors)
     * and other non-syntax errors are ignored.
     *
     * @param output the raw output string from tsc
     * @return a list of {@link ValidationError}s (never {@code null}, may be empty)
     */
    List<ValidationError> parse(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }

        List<ValidationError> errors = new ArrayList<>();

        // Try pretty=false format first (syntax errors only)
        Matcher matcher = PRETTY_FALSE_SYNTAX_PATTERN.matcher(output);
        while (matcher.find()) {
            int line = Integer.parseInt(matcher.group(1));
            int column = Integer.parseInt(matcher.group(2));
            String message = matcher.group(3).trim();
            errors.add(new ValidationError(line, column, message, null));
        }

        // If no matches, try default format (syntax errors only)
        if (errors.isEmpty()) {
            matcher = DEFAULT_SYNTAX_PATTERN.matcher(output);
            while (matcher.find()) {
                int line = Integer.parseInt(matcher.group(1));
                int column = Integer.parseInt(matcher.group(2));
                String message = matcher.group(3).trim();
                errors.add(new ValidationError(line, column, message, null));
            }
        }

        // If still no matches, try column range format (syntax errors only)
        if (errors.isEmpty()) {
            matcher = COLUMN_RANGE_SYNTAX_PATTERN.matcher(output);
            while (matcher.find()) {
                int line = Integer.parseInt(matcher.group(1));
                int column = Integer.parseInt(matcher.group(2));
                String message = matcher.group(3).trim();
                errors.add(new ValidationError(line, column, message, null));
            }
        }

        return List.copyOf(errors);
    }
}
