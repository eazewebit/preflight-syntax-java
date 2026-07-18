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
 * <p>The parser is tolerant of unexpected output: anything it cannot match is
 * preserved as a generic diagnostic so that no information is silently lost.
 */
final class JavacOutputParser {

    /**
     * Matches a javac error or warning line, optionally including the column.
     * <ul>
     *   <li>group&nbsp;1 &mdash; line number</li>
     *   <li>group&nbsp;2 &mdash; optional column number</li>
     *   <li>group&nbsp;3 &mdash; severity ({@code error} or {@code warning})</li>
     *   <li>group&nbsp;4 &mdash; message text</li>
     * </ul>
     */
    private static final Pattern DIAGNOSTIC = Pattern.compile(
            "^\\S.*?:(\\d+)(?::(\\d+))?:\\s*(error|warning):\\s*(.+)$");

    /** Matches the summary line such as {@code 2 errors}, {@code 1 error}, or {@code 3 warnings}. */
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
                // Warnings are intentionally skipped.
            } else if (!SUMMARY.matcher(line).matches() && !line.isBlank()
                    && !isSourceEcho(line)) {
                // Preserve truly unexpected lines so callers can investigate.
                errors.add(new ValidationError(1, -1, line.trim(), line));
            }
        }

        return errors.isEmpty()
                ? ValidationResult.valid("javac reported no syntax errors.")
                : ValidationResult.invalid("javac detected syntax errors.", errors);
    }

    /**
     * Heuristically detects source-echo lines (indented code followed by a caret
     * marker) so they are not treated as standalone diagnostics.
     */
    private static boolean isSourceEcho(String line) {
        return line.isBlank() || line.trim().startsWith("^") || line.startsWith("    ");
    }
}
