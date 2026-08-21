package com.neel.syntaxvalidation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable description of a proposed batch source-code modification.
 *
 * <p>A batch request targets a single file and contains an ordered list of
 * {@link LineReplacement} entries. All replacements are applied together
 * to produce the final modified content.
 *
 * <p>The batch ensures replacements do not overlap (validates that ranges
 * are disjoint before construction).
 *
 * <p>Use the static {@link #builder()} method to obtain a {@link Builder}
 * instance.
 *
 * @param filePath    absolute or workspace-relative path to the source file
 * @param replacements ordered list of line-range replacements to apply
 */
public record BatchModificationRequest(
        String filePath,
        List<LineReplacement> replacements
) {

    /**
     * Compact constructor – validates all fields and ensures no overlapping ranges.
     */
    public BatchModificationRequest {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(replacements, "replacements must not be null");

        if (replacements.isEmpty()) {
            throw new IllegalArgumentException(
                    "replacements must not be empty; at least one LineReplacement is required");
        }

        // Defensive copy
        replacements = List.copyOf(replacements);

        // Validate no overlapping ranges
        validateNoOverlaps(replacements);
    }

    /**
     * Checks that no two replacement ranges overlap.
     *
     * @param entries the list of replacements to validate
     * @throws IllegalArgumentException if any ranges overlap
     */
    private static void validateNoOverlaps(List<LineReplacement> entries) {
        // Sort by fromLine for overlap detection
        List<LineReplacement> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> Integer.compare(a.fromLine(), b.fromLine()));

        for (int i = 1; i < sorted.size(); i++) {
            LineReplacement prev = sorted.get(i - 1);
            LineReplacement curr = sorted.get(i);

            if (prev.toLine() >= curr.fromLine()) {
                throw new IllegalArgumentException(
                        "Overlapping replacement ranges: [" + prev.fromLine() + ", "
                                + prev.toLine() + "] and [" + curr.fromLine() + ", "
                                + curr.toLine() + "]");
            }
        }
    }

    // ----------------------------------------------------------------
    //  Builder
    // ----------------------------------------------------------------

    /**
     * Returns a fresh {@link Builder}.
     *
     * @return a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link BatchModificationRequest}.
     */
    public static final class Builder {

        private String filePath;
        private final List<LineReplacement> replacements = new ArrayList<>();

        private Builder() { }

        /**
         * Sets the target file path.
         *
         * @param path absolute or workspace-relative path to the source file
         * @return this builder
         */
        public Builder filePath(final String path) {
            this.filePath = path;
            return this;
        }

        /**
         * Adds a single line replacement to the batch.
         *
         * @param replacement the replacement to add; must not be {@code null}
         * @return this builder
         */
        public Builder addReplacement(final LineReplacement replacement) {
            Objects.requireNonNull(replacement, "replacement must not be null");
            this.replacements.add(replacement);
            return this;
        }

        /**
         * Adds all replacements from the provided list to the batch.
         *
         * @param replacements the replacements to add; must not be {@code null}
         * @return this builder
         */
        public Builder addAllReplacements(final List<LineReplacement> replacements) {
            Objects.requireNonNull(replacements, "replacements must not be null");
            this.replacements.addAll(replacements);
            return this;
        }

        /**
         * Builds and returns the {@link BatchModificationRequest}.
         *
         * @return a new, immutable batch modification descriptor.
         * @throws IllegalArgumentException if any validation fails
         */
        public BatchModificationRequest build() {
            return new BatchModificationRequest(
                    filePath,
                    Collections.unmodifiableList(new ArrayList<>(replacements))
            );
        }
    }
}
