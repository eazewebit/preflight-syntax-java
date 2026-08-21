package com.neel.syntaxvalidation.modification;

import com.neel.syntaxvalidation.model.LineReplacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Applies a {@link com.neel.syntaxvalidation.model.ModificationRequest}'s line
 * range replacement to a list of source lines, producing a brand-new list.
 *
 * <p>Line numbers are 1-based and inclusive. The original list is never mutated;
 * a defensive copy is always returned. Line ranges that exceed the bounds of the
 * file are clamped gracefully:
 * <ul>
 *   <li>a {@code fromLine} past the end replaces nothing before the insertion;</li>
 *   <li>a {@code toLine} past the end removes everything from {@code fromLine} onward.</li>
 * </ul>
 */
public class ModificationApplier {

    /**
     * Replaces the inclusive 1-based range {@code [fromLine, toLine]} of
     * {@code originalLines} with the given replacement text, returning the
     * resulting lines.
     *
     * @param originalLines the original file lines (without terminators); must not be {@code null}.
     * @param fromLine      the 1-based starting line (inclusive); must be {@code >= 1}.
     * @param toLine        the 1-based ending line (inclusive); must be {@code >= fromLine}.
     * @param replacement   the replacement text; {@code null} is treated as an empty string.
     * @return a new, unmodifiable list of lines after the modification.
     */
    public List<String> apply(List<String> originalLines, int fromLine, int toLine, String replacement) {
        Objects.requireNonNull(originalLines, "originalLines");
        if (fromLine < 1) {
            throw new IllegalArgumentException("fromLine must be >= 1, but was " + fromLine);
        }
        if (toLine < fromLine) {
            throw new IllegalArgumentException(
                    "toLine (" + toLine + ") must be >= fromLine (" + fromLine + ")");
        }

        List<String> result = new ArrayList<>(originalLines.size() + 8);
        String safeReplacement = replacement == null ? "" : replacement;

        // Lines strictly before the target region (indices 0 .. fromLine-2).
        int keepBefore = Math.min(fromLine - 1, originalLines.size());
        for (int i = 0; i < keepBefore; i++) {
            result.add(originalLines.get(i));
        }

        // Insert the replacement, splitting on any line terminator.
        if (!safeReplacement.isEmpty()) {
            for (String line : safeReplacement.split("\\R", -1)) {
                result.add(line);
            }
        }

        // Lines strictly after the target region (indices toLine .. size-1).
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
     * <p>Each {@link LineReplacement} is applied using the single-replacement
     * {@link #apply(List, int, int, String)} method. The replacements are sorted by
     * {@code fromLine} in descending order before application so that earlier line
     * numbers remain stable.
     *
     * @param originalLines the original file lines (without terminators); must not be {@code null}.
     * @param replacements  the list of replacements to apply; must not be {@code null} or empty.
     * @return a new, unmodifiable list of lines after all modifications.
     * @throws IllegalArgumentException if any replacement has invalid bounds
     */
    public List<String> applyAll(List<String> originalLines, List<LineReplacement> replacements) {
        Objects.requireNonNull(originalLines, "originalLines");
        Objects.requireNonNull(replacements, "replacements");
        if (replacements.isEmpty()) {
            throw new IllegalArgumentException(
                    "replacements must not be empty; at least one LineReplacement is required");
        }

        // Sort by fromLine descending so we apply from bottom to top,
        // preserving earlier line numbers.
        List<LineReplacement> sorted = new ArrayList<>(replacements);
        sorted.sort(Comparator.comparingInt(LineReplacement::fromLine).reversed());

        List<String> current = new ArrayList<>(originalLines);
        for (LineReplacement r : sorted) {
            current = new ArrayList<>(apply(current, r.fromLine(), r.toLine(), r.replacement()));
        }

        return List.copyOf(current);
    }
}
