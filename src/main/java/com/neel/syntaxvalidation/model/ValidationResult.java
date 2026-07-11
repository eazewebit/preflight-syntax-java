package com.neel.syntaxvalidation.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, structured outcome of a syntax-validation run.
 *
 * <p>A result is either <em>valid</em> (the modified content passed the syntax
 * check) or <em>invalid</em> (one or more errors were detected). When invalid,
 * the result carries an overall explanation {@link #getMessage() message} and a
 * list of detailed {@link #getErrors() errors} that include line numbers,
 * descriptions, and tool output where applicable.
 *
 * <p>Use the static factory methods {@link #valid(String)} and
 * {@link #invalid(String)} / {@link #invalid(String, List)} to create instances.
 */
public final class ValidationResult {

    private final boolean valid;
    private final String message;
    private final List<ValidationError> errors;

    private ValidationResult(boolean valid, String message, List<ValidationError> errors) {
        this.valid = valid;
        this.message = message;
        this.errors = errors;
    }

    /** @return {@code true} if the content passed validation, {@code false} otherwise. */
    public boolean isValid() {
        return valid;
    }

    /** @return the overall explanation message; never {@code null}. */
    public String getMessage() {
        return message;
    }

    /**
     * @return an unmodifiable list of detailed errors; empty when the result is valid.
     */
    public List<ValidationError> getErrors() {
        return errors;
    }

    /** @return {@code true} if at least one detailed error was recorded. */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Creates a positive result.
     *
     * @param message a message describing the successful validation; must not be {@code null}.
     * @return a valid result with no errors.
     */
    public static ValidationResult valid(String message) {
        Objects.requireNonNull(message, "message");
        return new ValidationResult(true, message, Collections.emptyList());
    }

    /**
     * Creates a negative result with no detailed errors.
     *
     * @param message the explanation of why validation failed; must not be {@code null}.
     * @return an invalid result.
     */
    public static ValidationResult invalid(String message) {
        return invalid(message, Collections.emptyList());
    }

    /**
     * Creates a negative result carrying a single detailed error.
     *
     * @param message the overall explanation; must not be {@code null}.
     * @param error   the detailed error; must not be {@code null}.
     * @return an invalid result.
     */
    public static ValidationResult invalid(String message, ValidationError error) {
        Objects.requireNonNull(error, "error");
        return invalid(message, List.of(error));
    }

    /**
     * Creates a negative result carrying the given detailed errors.
     *
     * @param message the overall explanation; must not be {@code null}.
     * @param errors  the detailed errors; must not be {@code null}.
     * @return an invalid result.
     */
    public static ValidationResult invalid(String message, List<ValidationError> errors) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(errors, "errors");
        List<ValidationError> copy = Collections.unmodifiableList(new ArrayList<>(errors));
        return new ValidationResult(false, message, copy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValidationResult that)) {
            return false;
        }
        return valid == that.valid
                && Objects.equals(message, that.message)
                && Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, message, errors);
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + valid + ", message='" + message
                + "', errors=" + errors + "}";
    }
}
