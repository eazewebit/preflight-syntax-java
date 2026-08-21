package com.neel.syntaxvalidation.model;

import java.util.Objects;

/**
 * Immutable description of a single line-range replacement within a file.
 *
 * <p>A replacement targets a contiguous, inclusive 1-based line range
 * {@code [fromLine, toLine]} and supplies replacement text that will
 * substitute those lines.
 *
 * <p>Use the static {@link #builder()} method to obtain a
 * {@link Builder} instance.
 *
 * @param fromLine             start of the target range (1-based, inclusive)
 * @param toLine               end of the target range (1-based, inclusive)
 * @param replacement          one or more replacement lines ({@code \n} separates them)
 * @param expectedOriginalLines optional verbatim text expected at the target range;
 *                              the library checks that the current on-disk content at
 *                              {@code [fromLine, toLine]} matches this value before
 *                              applying the replacement, guarding against stale edits
 */
public record LineReplacement(
        int fromLine,
        int toLine,
        String replacement,
        String expectedOriginalLines
) {

    /**
     * Compact constructor – validates all fields.
     */
    public LineReplacement {
        Objects.requireNonNull(replacement, "replacement must not be null");
        if (fromLine < 1) {
            throw new IllegalArgumentException(
                    "fromLine must be >= 1 but was " + fromLine);
        }
        if (toLine < fromLine) {
            throw new IllegalArgumentException(
                    "toLine (" + toLine + ") must be >= fromLine (" + fromLine + ")");
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
     * Fluent builder for {@link LineReplacement}.
     */
    public static final class Builder {

        private int fromLine;
        private int toLine;
        private String replacement;
        private String expectedOriginalLines;

        private Builder() { }

        /**
         * Sets the start of the target range (1-based, inclusive).
         *
         * @param line the start line number
         * @return this builder
         */
        public Builder fromLine(final int line) {
            this.fromLine = line;
            return this;
        }

        /**
         * Sets the end of the target range (1-based, inclusive).
         *
         * @param line the end line number
         * @return this builder
         */
        public Builder toLine(final int line) {
            this.toLine = line;
            return this;
        }

        /**
         * Sets the replacement text.
         *
         * @param text one or more lines ({@code \n} separates them);
         *             must not be {@code null}.
         * @return this builder
         */
        public Builder replacement(final String text) {
            this.replacement = text;
            return this;
        }

        /**
         * Sets the optional verbatim text expected at the target range.
         *
         * @param text the expected original content, or {@code null}
         * @return this builder
         */
        public Builder expectedOriginalLines(final String text) {
            this.expectedOriginalLines = text;
            return this;
        }

        /**
         * Builds and returns the {@link LineReplacement}.
         *
         * @return a new, immutable replacement descriptor.
         */
        public LineReplacement build() {
            return new LineReplacement(
                    fromLine,
                    toLine,
                    replacement,
                    expectedOriginalLines
            );
        }
    }
}
