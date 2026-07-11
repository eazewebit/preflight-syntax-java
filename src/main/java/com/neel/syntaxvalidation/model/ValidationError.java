package com.neel.syntaxvalidation.model;

import java.util.Objects;

/**
 * An immutable, single diagnostic produced by a syntax validator.
 *
 * <p>Each error captures, where available, the 1-based line and column of the
 * problem, a human-readable description, and the raw output emitted by the
 * underlying validation tool. Fields that a tool cannot determine are reported
 * as {@code -1}.
 */
public final class ValidationError {

    private final int line;
    private final int column;
    private final String message;
    private final String toolOutput;

    /**
     * @param line       1-based line number, or {@code -1} when unknown.
     * @param column     1-based column number, or {@code -1} when unknown.
     * @param message    human-readable description of the error; must not be {@code null}.
     * @param toolOutput the raw tool output associated with the error, or {@code null}.
     */
    public ValidationError(int line, int column, String message, String toolOutput) {
        this.line = line;
        this.column = column;
        this.message = Objects.requireNonNull(message, "message");
        this.toolOutput = toolOutput;
    }

    /**
     * Convenience constructor that omits a column and tool output.
     *
     * @param line    1-based line number, or {@code -1} when unknown.
     * @param message human-readable description of the error.
     */
    public ValidationError(int line, String message) {
        this(line, -1, message, null);
    }

    /** @return the 1-based line number, or {@code -1} when unknown. */
    public int getLine() {
        return line;
    }

    /** @return the 1-based column number, or {@code -1} when unknown. */
    public int getColumn() {
        return column;
    }

    /** @return the human-readable description; never {@code null}. */
    public String getMessage() {
        return message;
    }

    /** @return the raw tool output, or {@code null} when not applicable. */
    public String getToolOutput() {
        return toolOutput;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidationError that)) {
            return false;
        }
        return line == that.line
                && column == that.column
                && Objects.equals(message, that.message)
                && Objects.equals(toolOutput, that.toolOutput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column, message, toolOutput);
    }

    @Override
    public String toString() {
        return "ValidationError{line=" + line + ", column=" + column
                + ", message='" + message + "'"
                + (toolOutput != null ? ", toolOutput=<" + toolOutput.length() + " chars>" : "")
                + "}";
    }
}
