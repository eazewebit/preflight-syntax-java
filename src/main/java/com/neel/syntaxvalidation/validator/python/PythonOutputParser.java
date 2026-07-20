package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the standard output / error produced by the external
 * {@code python} binary when it validates syntax via
 * {@code python -c "import ast; ast.parse(open('<file>').read())"}.
 *
 * <p>Python's {@code ast.parse()} emits {@code SyntaxError} exceptions whose
 * textual representation follows a well-defined format:
 *
 * <pre>
 * Traceback (most recent call last):
 *   File "&lt;string&gt;", line N
 *     &lt;offending line&gt;
 *          ^
 * SyntaxError: &lt;description&gt;
 * </pre>
 *
 * <p>This parser extracts the line number, column (from the caret {@code ^}),
 * and the error description.  If the binary returns a non-zero exit code but
 * produces no parseable output, a generic error is synthesised.
 *
 * <p>This class is thread-safe (stateless).
 */
public final class PythonOutputParser {

    // Matches "File "...", line N" or "File "...", line N, in ..."
    private static final Pattern FILE_LINE_PATTERN = Pattern.compile(
            "File \"[^\"]*\", line (\\d+)");

    // Matches "SyntaxError: ..." or "IndentationError: ..." etc.
    private static final Pattern ERROR_TYPE_PATTERN = Pattern.compile(
            "(SyntaxError|IndentationError|TabError|EncodingError|TokenError|MemoryError|NameError|TypeError|ValueError):\\s*(.*)");

    // Matches the caret (^) indicator line to extract column
    private static final Pattern CARET_PATTERN = Pattern.compile("^(\\s*)\\^");

    /**
     * Parses the output from a Python syntax check process.
     *
     * @param stderr the standard error output from the Python process.
     * @param stdout the standard output from the Python process (may be empty).
     * @param exitCode the process exit code (0 = success, non-zero = failure).
     * @return a {@link ValidationResult} reflecting the parsed outcome.
     */
    public static ValidationResult parse(String stderr, String stdout, int exitCode) {
        if (exitCode == 0 && (stderr == null || stderr.isBlank())) {
            return ValidationResult.valid("Python syntax check passed.");
        }

        List<ValidationError> errors = new ArrayList<>();

        if (stderr != null && !stderr.isBlank()) {
            errors.addAll(parsePythonErrors(stderr));
        }

        if (errors.isEmpty() && exitCode != 0) {
            String fallbackMessage = (stderr != null && !stderr.isBlank())
                    ? stderr.trim()
                    : "Python syntax check failed with exit code " + exitCode + ".";
            errors.add(new ValidationError(1, 1, fallbackMessage, null));
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid("Python syntax check passed.");
        }

        return ValidationResult.invalid(
                "Python syntax check found " + errors.size() + " error(s).", errors);
    }

    /**
     * Parses Python error output to extract individual error messages.
     *
     * @param errorOutput the raw error output.
     * @return a list of parsed validation errors.
     */
    static List<ValidationError> parsePythonErrors(String errorOutput) {
        List<ValidationError> errors = new ArrayList<>();
        String[] lines = errorOutput.split("\n");

        int currentLine = -1;
        int currentCol = -1;
        boolean inTraceback = false;
        boolean foundErrorType = false;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Detect traceback start
            if (trimmed.startsWith("Traceback (most recent call last):")) {
                inTraceback = true;
                continue;
            }

            // Parse "File ..., line N"
            Matcher fileLineMatcher = FILE_LINE_PATTERN.matcher(trimmed);
            if (fileLineMatcher.find()) {
                try {
                    currentLine = Integer.parseInt(fileLineMatcher.group(1));
                    currentCol = -1; // reset
                } catch (NumberFormatException ignored) {
                    // skip
                }
                continue;
            }

            // Parse caret (^) indicator — indicates column position
            Matcher caretMatcher = CARET_PATTERN.matcher(lines[i]); // preserve original whitespace
            if (caretMatcher.find() && currentLine > 0) {
                currentCol = caretMatcher.group(1).length() + 1; // 1-based column
                continue;
            }

            // Parse error type and description
            Matcher errMatcher = ERROR_TYPE_PATTERN.matcher(trimmed);
            if (errMatcher.find()) {
                String errorType = errMatcher.group(1);
                String desc = errMatcher.group(2).trim();
                if (desc.isEmpty()) {
                    desc = errorType;
                } else {
                    desc = errorType + ": " + desc;
                }

                int errLine = currentLine > 0 ? currentLine : 1;
                int errCol = currentCol > 0 ? currentCol : 1;

                errors.add(new ValidationError(errLine, errCol, desc, null));

                // Reset for next error
                currentLine = -1;
                currentCol = -1;
                foundErrorType = true;
                continue;
            }

            // If we have a traceback but no error type yet, check for
            // direct error messages without traceback
            if (!inTraceback && !foundErrorType) {
                // Try to match direct error messages like "MemoryError: out of memory"
                Matcher directMatcher = ERROR_TYPE_PATTERN.matcher(trimmed);
                if (directMatcher.find()) {
                    String errorType = directMatcher.group(1);
                    String desc = directMatcher.group(2).trim();
                    if (desc.isEmpty()) {
                        desc = errorType;
                    } else {
                        desc = errorType + ": " + desc;
                    }
                    errors.add(new ValidationError(1, 1, desc, null));
                    foundErrorType = true;
                }
            }
        }

        // If we have a non-zero exit code but no parsed errors, try a more
        // aggressive extraction
        if (errors.isEmpty() && !errorOutput.isBlank()) {
            Pattern genericError = Pattern.compile("(\\w+Error):\\s*(.*)");
            Matcher m = genericError.matcher(errorOutput);
            if (m.find()) {
                errors.add(new ValidationError(1, 1, m.group(1) + ": " + m.group(2).trim(), null));
            }
        }

        return errors;
    }
}
