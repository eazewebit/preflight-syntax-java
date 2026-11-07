package com.neel.syntaxvalidation.modification;

import com.neel.syntaxvalidation.model.LineReplacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies a {@link com.neel.syntaxvalidation.model.ModificationRequest}'s line
 * range replacement, producing a brand-new result. The original is never mutated.
 *
 * <p>Line numbers are 1-based and inclusive. Bounds are enforced strictly so that a
 * hallucinated or stale line number fails loudly instead of silently corrupting the
 * file:
 * <ul>
 *   <li>a {@code fromLine} beyond the last line is rejected with an
 *       {@link IllegalArgumentException};</li>
 *   <li>a {@code toLine} past the end is clamped gracefully, removing everything
 *       from {@code fromLine} onward.</li>
 * </ul>
 *
 * <p>Two flavours of the same operation are offered:
 * <ul>
 *   <li><b>List-based</b> ({@link #apply(List, int, int, String)}) &mdash; content only;</li>
 *   <li><b>{@link Document}-based</b> ({@link #applyToDocument(Document, int, int, String)}) &mdash;
 *       byte-faithful. Kept lines carry their own original terminators, so an edit never
 *       rewrites a line it did not touch (mixed CRLF/LF files, CR-only files, missing
 *       final newline and BOM all survive). Newly inserted lines use the document's
 *       dominant separator; the last inserted line inherits the terminator that followed
 *       the replaced range.</li>
 * </ul>
 * Both flavours share one bounds-validation implementation, so their accept/reject
 * semantics cannot drift apart.
 */
public class ModificationApplier {

    // ==================================================================
    //  List-based API (content only)
    // ==================================================================

    /**
     * Replaces the inclusive 1-based range {@code [fromLine, toLine]} of
     * {@code originalLines} with the given replacement text, returning the
     * resulting lines.
     *
     * @param originalLines the original file lines (without terminators); must not be {@code null}.
     * @param fromLine      the 1-based starting line (inclusive); must be {@code >= 1} and within
     *                      the file (i.e. {@code <= originalLines.size()}).
     * @param toLine        the 1-based ending line (inclusive); must be {@code >= fromLine}; a
     *                      value past the end is clamped gracefully.
     * @param replacement   the replacement text; {@code null} is treated as an empty string.
     * @return a new, unmodifiable list of lines after the modification.
     * @throws IllegalArgumentException if {@code fromLine < 1}, if {@code toLine < fromLine}, or
     *         if {@code fromLine} points beyond the last line (guards against stale or hallucinated
     *         line numbers silently appending instead of erroring).
     */
    public List<String> apply(List<String> originalLines, int fromLine, int toLine, String replacement) {
        Objects.requireNonNull(originalLines, "originalLines");
        validateBounds(fromLine, toLine, originalLines.size());

        List<String> result = new ArrayList<>(originalLines.size() + 8);

        // Lines strictly before the target region (indices 0 .. fromLine-2).
        int keepBefore = Math.min(fromLine - 1, originalLines.size());
        for (int i = 0; i < keepBefore; i++) {
            result.add(originalLines.get(i));
        }

        // Insert the replacement, splitting on any line terminator.
        String safeReplacement = replacement == null ? "" : replacement;
        if (!safeReplacement.isEmpty()) {
            for (String line : splitReplacement(safeReplacement)) {
                result.add(line);
            }
        }

        // Lines strictly after the target region (indices toLine .. size-1).
        // A toLine past the end simply yields no tail lines (graceful clamp).
        for (int i = toLine; i < originalLines.size(); i++) {
            result.add(originalLines.get(i));
        }

        return List.copyOf(result);
    }

    /**
     * Applies multiple line-range replacements to the given source lines, returning
     * the fully modified result. Replacements are applied in reverse order (bottom-to-top)
     * to preserve line numbers for subsequent replacements.
     *
     * @param originalLines the original file lines (without terminators); must not be {@code null}.
     * @param replacements  the list of replacements to apply; must not be {@code null} or empty.
     * @return a new, unmodifiable list of lines after all modifications.
     * @throws IllegalArgumentException if any replacement has invalid bounds
     */
    public List<String> applyAll(List<String> originalLines, List<LineReplacement> replacements) {
        Objects.requireNonNull(originalLines, "originalLines");
        requireNonEmpty(replacements);

        List<String> current = new ArrayList<>(originalLines);
        for (LineReplacement r : sortedDescending(replacements)) {
            current = new ArrayList<>(apply(current, r.fromLine(), r.toLine(), r.replacement()));
        }

        return List.copyOf(current);
    }

    // ==================================================================
    //  Document-based API (byte-faithful)
    // ==================================================================

    /**
     * Replaces the inclusive 1-based range {@code [fromLine, toLine]} of a
     * {@link Document}, preserving every byte outside the edited region.
     *
     * <p>Terminator bookkeeping:
     * <ul>
     *   <li>lines before and after the region keep their own original terminators;</li>
     *   <li>inserted lines are separated by {@link Document#dominantSeparator()};</li>
     *   <li>the last inserted line inherits the terminator that followed the last replaced
     *       line, so a file without a final newline stays without one (and vice versa);</li>
     *   <li>a pure deletion leaves the surrounding terminators untouched.</li>
     * </ul>
     *
     * @param original    the source document; must not be {@code null}.
     * @param fromLine    the 1-based starting line (inclusive); must exist in the document.
     * @param toLine      the 1-based ending line (inclusive); past EOF is clamped gracefully.
     * @param replacement the replacement text; {@code null} is treated as an empty string.
     * @return a new document holding the modified content and terminators.
     * @throws IllegalArgumentException if the bounds are invalid (same rules as
     *         {@link #apply(List, int, int, String)}).
     */
    public Document applyToDocument(Document original, int fromLine, int toLine, String replacement) {
        Objects.requireNonNull(original, "original");
        validateBounds(fromLine, toLine, original.size());

        List<String> src = original.lines();
        String dominant = original.dominantSeparator();
        boolean bom = original.hasLeadingBom();

        List<String> lines = new ArrayList<>(src.size() + 8);
        List<String> terminators = new ArrayList<>(src.size() + 8);

        // 1. Lines strictly before the region: content and terminators untouched.
        for (int i = 0; i < fromLine - 1; i++) {
            lines.add(src.get(i));
            terminators.add(original.terminator(i));
        }

        // 2. The replacement. Its final break adopts the terminator that followed the
        //    last replaced line (clamped to EOF) so trailing-newline state is preserved.
        String safeReplacement = replacement == null ? "" : replacement;
        if (!safeReplacement.isEmpty()) {
            List<String> newLines = splitReplacement(safeReplacement);
            String boundaryTerminator = original.terminator(Math.min(toLine, src.size()) - 1);
            for (int i = 0; i < newLines.size(); i++) {
                boolean lastInserted = (i == newLines.size() - 1);
                lines.add(newLines.get(i));
                terminators.add(lastInserted ? boundaryTerminator : dominant);
            }
        }

        // 3. Lines strictly after the region: content and terminators untouched.
        for (int i = toLine; i < src.size(); i++) {
            lines.add(src.get(i));
            terminators.add(original.terminator(i));
        }

        return Document.of(lines, terminators, bom);
    }

    /**
     * Applies multiple replacements to a {@link Document}, bottom-to-top, preserving
     * byte fidelity for every untouched line (see
     * {@link #applyToDocument(Document, int, int, String)}).
     *
     * @param original     the source document; must not be {@code null}.
     * @param replacements the replacements to apply; must not be {@code null} or empty.
     * @return a new document with all modifications applied.
     * @throws IllegalArgumentException if any replacement has invalid bounds
     */
    public Document applyAllToDocument(Document original, List<LineReplacement> replacements) {
        Objects.requireNonNull(original, "original");
        requireNonEmpty(replacements);

        Document current = original;
        for (LineReplacement r : sortedDescending(replacements)) {
            current = applyToDocument(current, r.fromLine(), r.toLine(), r.replacement());
        }
        return current;
    }

    // ==================================================================
    //  Shared helpers
    // ==================================================================

    /**
     * Single source of truth for range validation, shared by the List- and
     * Document-based paths so their semantics cannot diverge.
     */
    private static void validateBounds(int fromLine, int toLine, int size) {
        if (fromLine < 1) {
            throw new IllegalArgumentException("fromLine must be >= 1, but was " + fromLine);
        }
        if (toLine < fromLine) {
            throw new IllegalArgumentException(
                    "toLine (" + toLine + ") must be >= fromLine (" + fromLine + ")");
        }
        if (fromLine > size) {
            throw new IllegalArgumentException(
                    "fromLine (" + fromLine + ") is out of bounds: the file has only "
                            + size + " line(s). A replacement must target at least one "
                            + "existing line; refusing to silently append past the end.");
        }
    }

    /** Splits replacement text on any Unicode line break, keeping trailing empty parts. */
    private static List<String> splitReplacement(String replacement) {
        List<String> parts = new ArrayList<>();
        for (String part : replacement.split("\\R", -1)) {
            parts.add(part);
        }
        return parts;
    }

    private static void requireNonEmpty(List<LineReplacement> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.isEmpty()) {
            throw new IllegalArgumentException(
                    "replacements must not be empty; at least one LineReplacement is required");
        }
    }

    private static List<LineReplacement> sortedDescending(List<LineReplacement> replacements) {
        List<LineReplacement> sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(LineReplacement::fromLine).reversed());
        return sorted;
    }
}