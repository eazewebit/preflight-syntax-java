package com.neel.syntaxvalidation.modification;

import java.util.List;
import java.util.Objects;

/**
 * Immutable description of a file's line-ending convention.
 *
 * <p>Captures two independent facts that must survive a read &rarr; modify &rarr;
 * write round-trip so that an edit never rewrites lines it did not touch:
 * <ul>
 *   <li><b>separator</b> &mdash; the dominant terminator used in the file
 *       ({@code "\r\n"} for CRLF files, otherwise {@code "\n"}); and</li>
 *   <li><b>trailing newline</b> &mdash; whether the original content ended with a
 *       line terminator.</li>
 * </ul>
 *
 * <p>{@link #detect(String)} derives the convention from raw file text, and
 * {@link #join(List)} re-renders a list of (terminator-free) lines back into text
 * using that exact convention. This is what keeps CRLF projects from being
 * silently converted to LF and prevents the final newline from being stripped.
 */
public final class LineEnding {

    /** Unix line feed. */
    public static final String LF = "\n";

    /** Windows carriage-return + line-feed pair. */
    public static final String CRLF = "\r\n";

    private final String separator;
    private final boolean trailingNewline;

    /**
     * @param separator       the terminator to use when joining lines; must not be {@code null}.
     * @param trailingNewline whether a terminator should follow the last line.
     */
    public LineEnding(String separator, boolean trailingNewline) {
        this.separator = Objects.requireNonNull(separator, "separator");
        this.trailingNewline = trailingNewline;
    }

    /**
     * Derives the convention from raw file text.
     *
     * <p>The separator is {@code "\r\n"} when CRLF pairs are present and at least
     * as numerous as lone line feeds, otherwise {@code "\n"}. The trailing-newline
     * flag reflects whether the content ends with any terminator. Empty or
     * {@code null} input yields LF without a trailing newline.
     *
     * @param rawContent the full file text (may be {@code null}).
     * @return the detected convention; never {@code null}.
     */
    public static LineEnding detect(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return new LineEnding(LF, false);
        }

        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < rawContent.length(); i++) {
            char c = rawContent.charAt(i);
            if (c == '\n') {
                lf++;
            } else if (c == '\r') {
                // A CR immediately followed by LF forms one CRLF pair, not a lone CR.
                if (i + 1 < rawContent.length() && rawContent.charAt(i + 1) == '\n') {
                    crlf++;
                    i++; // consume the paired LF
                }
            }
        }

        int loneLf = lf - crlf;
        String separator = (crlf > 0 && crlf >= loneLf) ? CRLF : LF;
        boolean trailingNewline = endsWithTerminator(rawContent);
        return new LineEnding(separator, trailingNewline);
    }

    /** @return the terminator used between lines. */
    public String separator() {
        return separator;
    }

    /** @return {@code true} if a terminator should follow the last line. */
    public boolean hasTrailingNewline() {
        return trailingNewline;
    }

    /**
     * Re-renders the given lines using this convention.
     *
     * <p>Lines are joined with {@link #separator()}; a final terminator is appended
     * only when {@link #hasTrailingNewline()} is set. An empty list renders to an
     * empty string (an empty file has no trailing newline).
     *
     * @param lines the terminator-free lines; must not be {@code null}.
     * @return the reconstructed text.
     */
    public String join(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(lines.get(i));
        }
        if (trailingNewline) {
            sb.append(separator);
        }
        return sb.toString();
    }

    private static boolean endsWithTerminator(String content) {
        char last = content.charAt(content.length() - 1);
        return last == '\n' || last == '\r';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LineEnding other)) {
            return false;
        }
        return trailingNewline == other.trailingNewline
                && separator.equals(other.separator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(separator, trailingNewline);
    }

    @Override
    public String toString() {
        return "LineEnding{separator=" + (CRLF.equals(separator) ? "CRLF" : "LF")
                + ", trailingNewline=" + trailingNewline + "}";
    }
}
