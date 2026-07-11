package com.neel.syntaxvalidation.modification;

import java.util.ArrayList;
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
}
