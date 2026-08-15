package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the diagnostic output of the {@code javac} compiler into a structured
 * {@link ValidationResult}.
 *
 * <p>When {@code javac} is asked to compile a file whose source contains
 * syntax errors, it emits messages in the following canonical form
 * (one per diagnostic):
 *
 * <pre>
 * File.java:3: error: ';' expected
 *         int x
 *               ^
 * </pre>
 *
 * <p>For releases that include column information the format is:
 *
 * <pre>
 * File.java:3:12: error: ';' expected
 * </pre>
 *
 * <p>Warnings are emitted with the word {@code warning:} instead of
 * {@code error:} and are <em>not</em> treated as validation failures, since
 * they do not affect syntactic correctness.
 *
 * <p>After each matched DIAGNOSTIC line javac emits up to two additional lines:
 * the echoed source line and a caret-marker line. These must be skipped by the
 * parser to avoid being mis-classified as standalone diagnostics.
 */
final class JavacOutputParser {

    /**
     * Matches a javac error or warning line, optionally including the column.
     * <ul>
     *   <li>group 1 — line number</li>
     *   <li>group 2 — optional column number</li>
     *   <li>group 3 — severity ({@code error} or {@code warning})</li>
     *   <li>group 4 — message text</li>
     * </ul>
     */
    private static final Pattern DIAGNOSTIC = Pattern.compile(
            "^\\S.*?:(\\d+)(?::(\\d+))?:\\s*(error|warning):\\s*(.+)$");

    /** Matches the summary line such as {@code 2 errors} or {@code 3 warnings}. */
    private static final Pattern SUMMARY = Pattern.compile(
            "^\\s*\\d+\\s+(errors?|warnings?)\\s*$");

    JavacOutputParser() { /* package-private */ }

    /**
     * Parses the combined output of a {@code javac} invocation.
     *
     * @param rawOutput the raw stderr (and optionally stdout) from {@code javac};
     *                  may be {@code null} or blank
     * @return an immutable validation result; never {@code null}
     */
    ValidationResult parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return ValidationResult.valid("javac reported no diagnostics.");
        }

        List<ValidationError> errors = new ArrayList<>();
        String[] lines = rawOutput.split("\\R");

        // After a matched DIAGNOSTIC line, javac emits up to two additional
        // lines: the echoed source and the caret marker.  We track how many
        // "echo" lines to skip so they are not mis-classified as diagnostics.
        int echoLinesRemaining = 0;

        for (String line : lines) {
            Matcher m = DIAGNOSTIC.matcher(line);
            if (m.find()) {
                int lineNo = Integer.parseInt(m.group(1));
                int col = m.group(2) != null ? Integer.parseInt(m.group(2)) : -1;
                String severity = m.group(3);
                String message = m.group(4).trim();
                if ("error".equals(severity)) {
                    errors.add(new ValidationError(lineNo, col, message, line));
                }
                // The next 2 non-blank lines are echoed source + caret marker.
                echoLinesRemaining = 2;
            } else if (SUMMARY.matcher(line).matches()) {
                // Summary line – skip, and reset echo counter.
                echoLinesRemaining = 0;
            } else if (line.isBlank()) {
                // Blank line resets the echo state.
                echoLinesRemaining = 0;
            } else if (echoLinesRemaining > 0) {
                // Source-echo or caret marker following a diagnostic — skip.
                echoLinesRemaining--;
            } else {
                // Truly unexpected non-blank line — preserve as a fallback
                // diagnostic so callers can investigate unknown output.
                errors.add(new ValidationError(1, -1, line.trim(), line));
            }
        }

        return errors.isEmpty()
                ? ValidationResult.valid("javac reported no syntax errors.")
                : ValidationResult.invalid("javac detected syntax errors.", errors);
    }
}