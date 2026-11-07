package com.neel.syntaxvalidation.modification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, byte-faithful model of a text document.
 *
 * <p>A document is a list of terminator-free lines plus the <em>exact</em> line
 * terminator that followed each one, and a flag recording whether the original
 * text started with a UTF-8 BOM. This per-line terminator storage is what makes
 * an edit round-trip lossless: re-rendering reproduces every byte outside the
 * edited region, including
 * <ul>
 *   <li>mixed line endings (a lone LF inside a CRLF file stays a lone LF),</li>
 *   <li>CR-only ("classic Mac") files, which would otherwise be rewritten to LF,</li>
 *   <li>the presence or absence of the final newline, and</li>
 *   <li>a leading UTF-8 BOM.</li>
 * </ul>
 *
 * <p>The BOM is never part of {@link #lines()}: it is stripped at parse time so
 * that line&nbsp;1 content and language detection are not polluted, and it is
 * re-emitted by {@link #render()} so the bytes written back match the original.
 * Use {@link #renderWithoutBom()} when feeding text to external validators.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class Document {

    /** The Unicode BOM character that {@code Files.readString} leaves in place. */
    public static final char BOM_CHAR = '\uFEFF';

    private final List<String> lines;
    private final List<String> terminators;
    private final boolean leadingBom;

    private Document(List<String> lines, List<String> terminators, boolean leadingBom) {
        if (lines.size() != terminators.size()) {
            throw new IllegalArgumentException(
                    "lines (" + lines.size() + ") and terminators (" + terminators.size()
                            + ") must have the same size");
        }
        this.lines = List.copyOf(lines);
        this.terminators = List.copyOf(terminators);
        this.leadingBom = leadingBom;
    }

    /**
     * Parses raw file text, recording each line's exact terminator and stripping a
     * leading UTF-8 BOM into a flag.
     *
     * @param raw the full document text (may be empty, must not be {@code null}).
     * @return the parsed document.
     */
    public static Document parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        boolean bom = !raw.isEmpty() && raw.charAt(0) == BOM_CHAR;
        String text = bom ? raw.substring(1) : raw;

        List<String> lines = new ArrayList<>();
        List<String> terminators = new ArrayList<>();
        if (text.isEmpty()) {
            return new Document(lines, terminators, bom);
        }

        int length = text.length();
        int start = 0;
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                lines.add(text.substring(start, i));
                String terminator;
                if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    terminator = LineEnding.CRLF;
                    i += 2;
                } else if (c == '\r') {
                    terminator = "\r";
                    i += 1;
                } else {
                    terminator = LineEnding.LF;
                    i += 1;
                }
                terminators.add(terminator);
                start = i;
            } else {
                i++;
            }
        }
        if (start < length) {
            // Final line with no trailing terminator.
            lines.add(text.substring(start));
            terminators.add("");
        }
        return new Document(lines, terminators, bom);
    }

    /**
     * Builds a document whose lines all share one convention (used when the exact
     * per-line terminators of the source are unknown).
     *
     * @param lines       terminator-free lines.
     * @param lineEnding  the convention to apply to every break.
     * @return the synthesized document (no BOM).
     */
    public static Document of(List<String> lines, LineEnding lineEnding) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(lineEnding, "lineEnding");
        List<String> terminators = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            boolean last = (i == lines.size() - 1);
            terminators.add(last && !lineEnding.hasTrailingNewline() ? "" : lineEnding.separator());
        }
        return new Document(lines, terminators, false);
    }

    /**
     * Builds a document from explicit per-line terminators.
     *
     * @param lines        terminator-free lines.
     * @param terminators  the terminator that follows each line ("" for none).
     * @param leadingBom   whether the rendered text should start with a UTF-8 BOM.
     * @return the document.
     */
    public static Document of(List<String> lines, List<String> terminators, boolean leadingBom) {
        return new Document(lines, terminators, leadingBom);
    }

    /** @return an unmodifiable view of the terminator-free lines. */
    public List<String> lines() {
        return lines;
    }

    /** @return the number of lines (0 for an empty document). */
    public int size() {
        return lines.size();
    }

    /** @return {@code true} when the document has no lines at all. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** @return {@code true} when the original text started with a UTF-8 BOM. */
    public boolean hasLeadingBom() {
        return leadingBom;
    }

    /**
     * @param index 0-based line index.
     * @return the terminator that follows that line ("" when it is an unterminated
     *         final line).
     * @throws IndexOutOfBoundsException if {@code index} is out of range.
     */
    public String terminator(int index) {
        return terminators.get(index);
    }

    /** @return an unmodifiable view of every terminator, parallel to {@link #lines()}. */
    public List<String> terminators() {
        return Collections.unmodifiableList(terminators);
    }

    /**
     * @return the dominant separator (the most frequent break in this document;
     *         ties resolve CRLF &rarr; LF &rarr; CR). {@code "\n"} for a document
     *         with no breaks.
     */
    public String dominantSeparator() {
        int crlf = 0;
        int lf = 0;
        int cr = 0;
        for (String t : terminators) {
            if (LineEnding.CRLF.equals(t)) {
                crlf++;
            } else if (LineEnding.LF.equals(t)) {
                lf++;
            } else if ("\r".equals(t)) {
                cr++;
            }
        }
        // Most frequent break wins; ties resolve CRLF then LF then CR, and a
        // document with no breaks at all defaults to LF.
        if (crlf > 0 && crlf >= lf && crlf >= cr) {
            return LineEnding.CRLF;
        }
        if (lf > 0 && lf >= cr) {
            return LineEnding.LF;
        }
        if (cr > 0) {
            return "\r";
        }
        return LineEnding.LF;
    }

    /** @return {@code true} when the document ends with a line terminator. */
    public boolean hasTrailingNewline() {
        return !terminators.isEmpty() && !terminators.get(terminators.size() - 1).isEmpty();
    }

    /**
     * @return the coarse line-ending convention of this document (dominant separator
     *         + trailing-newline flag). Note that joining lines through this value
     *         normalizes mixed endings &mdash; prefer {@link #render()} for output.
     */
    public LineEnding lineEnding() {
        return new LineEnding(dominantSeparator(), hasTrailingNewline());
    }

    /**
     * Renders the document exactly as it would appear on disk: every line followed by
     * its own recorded terminator, prefixed with a BOM when {@link #hasLeadingBom()}.
     *
     * @return the byte-faithful text.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        if (leadingBom) {
            sb.append(BOM_CHAR);
        }
        appendBody(sb);
        return sb.toString();
    }

    /**
     * Renders the document without a leading BOM &mdash; the form to hand to external
     * validators, which must not see a BOM in front of line&nbsp;1.
     *
     * @return the text with no BOM prefix.
     */
    public String renderWithoutBom() {
        StringBuilder sb = new StringBuilder();
        appendBody(sb);
        return sb.toString();
    }

    private void appendBody(StringBuilder sb) {
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i)).append(terminators.get(i));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Document other)) {
            return false;
        }
        return leadingBom == other.leadingBom
                && lines.equals(other.lines)
                && terminators.equals(other.terminators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines, terminators, leadingBom);
    }

    @Override
    public String toString() {
        return "Document{lines=" + lines.size() + ", bom=" + leadingBom
                + ", separator=" + dominantSeparator().replace("\r", "\\r").replace("\n", "\\n")
                + ", trailingNewline=" + hasTrailingNewline() + "}";
    }
}