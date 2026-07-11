package com.neel.syntaxvalidation.model;

import java.util.Objects;

/**
 * Immutable description of a proposed source-code modification.
 * <p>
 * A request targets a contiguous, inclusive 1-based line range
 * ({@code [fromLine, toLine]}) within a file and specifies the replacement text
 * that should be substituted for those lines. The library applies this change
 * against an in-memory copy of the file and then validates the result.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * ModificationRequest request = ModificationRequest.builder()
 *         .filePath("src/script.js")
 *         .fromLine(3)
 *         .toLine(5)
 *         .replacement("function add(a, b) {\n  return a + b;\n}")
 *         .build();
 * }</pre>
 *
 * <p>Instances are created exclusively through the {@link Builder}, which
 * validates all invariants at construction time.
 */
public final class ModificationRequest {

    private final String filePath;
    private final int fromLine;
    private final int toLine;
    private final String replacement;

    private ModificationRequest(Builder builder) {
        this.filePath = builder.filePath;
        this.fromLine = builder.fromLine;
        this.toLine = builder.toLine;
        this.replacement = builder.replacement;
    }

    /**
     * @return the path of the file to modify (absolute or relative).
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @return the 1-based starting line of the region to replace (inclusive).
     */
    public int getFromLine() {
        return fromLine;
    }

    /**
     * @return the 1-based ending line of the region to replace (inclusive).
     */
    public int getToLine() {
        return toLine;
    }

    /**
     * @return the replacement text; never {@code null} (defaults to an empty string).
     */
    public String getReplacement() {
        return replacement;
    }

    /**
     * @return a new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ModificationRequest that)) {
            return false;
        }
        return fromLine == that.fromLine
                && toLine == that.toLine
                && Objects.equals(filePath, that.filePath)
                && Objects.equals(replacement, that.replacement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, fromLine, toLine, replacement);
    }

    @Override
    public String toString() {
        return "ModificationRequest{filePath='" + filePath + "', fromLine=" + fromLine
                + ", toLine=" + toLine + ", replacement=<" + replacement.length() + " chars>}";
    }

    /**
     * Fluent builder for {@link ModificationRequest}.
     */
    public static final class Builder {

        private String filePath;
        private int fromLine;
        private int toLine;
        private String replacement = "";

        private Builder() {
        }

        /**
         * @param filePath the path of the file to modify; must not be {@code null} or blank.
         * @return this builder.
         */
        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * @param fromLine the 1-based starting line (inclusive); must be {@code >= 1}.
         * @return this builder.
         */
        public Builder fromLine(int fromLine) {
            this.fromLine = fromLine;
            return this;
        }

        /**
         * @param toLine the 1-based ending line (inclusive); must be {@code >= fromLine}.
         * @return this builder.
         */
        public Builder toLine(int toLine) {
            this.toLine = toLine;
            return this;
        }

        /**
         * @param replacement the replacement text; {@code null} is treated as an empty string.
         * @return this builder.
         */
        public Builder replacement(String replacement) {
            this.replacement = replacement == null ? "" : replacement;
            return this;
        }

        /**
         * Builds the request after validating all invariants.
         *
         * @return a new immutable {@link ModificationRequest}.
         * @throws IllegalStateException if any invariant is violated.
         */
        public ModificationRequest build() {
            if (filePath == null || filePath.isBlank()) {
                throw new IllegalStateException("filePath must not be null or blank");
            }
            if (fromLine < 1) {
                throw new IllegalStateException("fromLine must be >= 1, but was " + fromLine);
            }
            if (toLine < fromLine) {
                throw new IllegalStateException(
                        "toLine (" + toLine + ") must be >= fromLine (" + fromLine + ")");
            }
            return new ModificationRequest(this);
        }
    }
}
