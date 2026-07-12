package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ValidationError}.
 */
@DisplayName("ValidationError")
class ValidationErrorTest {

    // ---------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("full constructor sets all fields")
        void fullConstructor() {
            ValidationError error = new ValidationError(5, 10, "Syntax error", "raw output");

            assertThat(error.getLine()).isEqualTo(5);
            assertThat(error.getColumn()).isEqualTo(10);
            assertThat(error.getMessage()).isEqualTo("Syntax error");
            assertThat(error.getToolOutput()).isEqualTo("raw output");
        }

        @Test
        @DisplayName("convenience constructor omits column and tool output")
        void convenienceConstructor() {
            ValidationError error = new ValidationError(3, "Missing semicolon");

            assertThat(error.getLine()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo(-1);
            assertThat(error.getMessage()).isEqualTo("Missing semicolon");
            assertThat(error.getToolOutput()).isNull();
        }

        @Test
        @DisplayName("accepts null toolOutput")
        void nullToolOutput() {
            ValidationError error = new ValidationError(1, 1, "Error", null);

            assertThat(error.getToolOutput()).isNull();
        }

        @Test
        @DisplayName("accepts negative line number (unknown)")
        void negativeLine() {
            ValidationError error = new ValidationError(-1, -1, "Unknown position", null);

            assertThat(error.getLine()).isEqualTo(-1);
            assertThat(error.getColumn()).isEqualTo(-1);
        }

        @Test
        @DisplayName("accepts zero line number")
        void zeroLine() {
            ValidationError error = new ValidationError(0, 0, "Zero position", null);

            assertThat(error.getLine()).isEqualTo(0);
            assertThat(error.getColumn()).isEqualTo(0);
        }

        @Test
        @DisplayName("rejects null message in full constructor")
        void rejectsNullMessage() {
            assertThatThrownBy(() -> new ValidationError(1, 1, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null message in convenience constructor")
        void rejectsNullMessageConvenience() {
            assertThatThrownBy(() -> new ValidationError(1, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("accepts empty message")
        void acceptsEmptyMessage() {
            ValidationError error = new ValidationError(1, 1, "", null);
            assertThat(error.getMessage()).isEmpty();
        }

        @Test
        @DisplayName("accepts large line numbers")
        void largeLineNumbers() {
            ValidationError error = new ValidationError(999999, 999999, "Large", null);
            assertThat(error.getLine()).isEqualTo(999999);
            assertThat(error.getColumn()).isEqualTo(999999);
        }
    }

    // ---------------------------------------------------------------
    // equals and hashCode
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equal errors are considered equal")
        void equalErrors() {
            ValidationError a = new ValidationError(5, 10, "Error", "output");
            ValidationError b = new ValidationError(5, 10, "Error", "output");

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different line makes errors unequal")
        void differentLine() {
            ValidationError a = new ValidationError(5, 10, "Error", "output");
            ValidationError b = new ValidationError(6, 10, "Error", "output");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different column makes errors unequal")
        void differentColumn() {
            ValidationError a = new ValidationError(5, 10, "Error", "output");
            ValidationError b = new ValidationError(5, 11, "Error", "output");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different message makes errors unequal")
        void differentMessage() {
            ValidationError a = new ValidationError(5, 10, "Error A", "output");
            ValidationError b = new ValidationError(5, 10, "Error B", "output");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different toolOutput makes errors unequal")
        void differentToolOutput() {
            ValidationError a = new ValidationError(5, 10, "Error", "output A");
            ValidationError b = new ValidationError(5, 10, "Error", "output B");

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("null toolOutput equality works")
        void nullToolOutputEquality() {
            ValidationError a = new ValidationError(5, 10, "Error", null);
            ValidationError b = new ValidationError(5, 10, "Error", null);

            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("is not equal to null")
        void notEqualToNull() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            assertThat(error).isNotEqualTo(null);
        }

        @Test
        @DisplayName("is not equal to different type")
        void notEqualToDifferentType() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            assertThat(error).isNotEqualTo("not an error");
        }

        @Test
        @DisplayName("is equal to itself (reflexive)")
        void reflexive() {
            ValidationError error = new ValidationError(5, 10, "Error", "output");
            assertThat(error).isEqualTo(error);
        }
    }

    // ---------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString includes line and column")
        void includesLineAndColumn() {
            ValidationError error = new ValidationError(5, 10, "Error", "output");
            String str = error.toString();

            assertThat(str).contains("line=5");
            assertThat(str).contains("column=10");
        }

        @Test
        @DisplayName("toString includes message")
        void includesMessage() {
            ValidationError error = new ValidationError(5, 10, "Syntax error", "output");
            String str = error.toString();

            assertThat(str).contains("Syntax error");
        }

        @Test
        @DisplayName("toString includes toolOutput length when present")
        void includesToolOutputLength() {
            ValidationError error = new ValidationError(5, 10, "Error", "some output");
            String str = error.toString();

            assertThat(str).contains("chars");
        }

        @Test
        @DisplayName("toString omits toolOutput when null")
        void omitsNullToolOutput() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            String str = error.toString();

            assertThat(str).doesNotContain("toolOutput");
        }

        @Test
        @DisplayName("toString starts with class name")
        void startsWithClassName() {
            ValidationError error = new ValidationError(1, 1, "Error", null);
            assertThat(error.toString()).startsWith("ValidationError{");
        }
    }

    // ---------------------------------------------------------------
    // Immutability
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("getLine returns same value on repeated calls")
        void lineImmutable() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            assertThat(error.getLine()).isEqualTo(error.getLine());
        }

        @Test
        @DisplayName("getColumn returns same value on repeated calls")
        void columnImmutable() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            assertThat(error.getColumn()).isEqualTo(error.getColumn());
        }

        @Test
        @DisplayName("getMessage returns same value on repeated calls")
        void messageImmutable() {
            ValidationError error = new ValidationError(5, 10, "Error", null);
            assertThat(error.getMessage()).isEqualTo(error.getMessage());
        }

        @Test
        @DisplayName("getToolOutput returns same value on repeated calls")
        void toolOutputImmutable() {
            ValidationError error = new ValidationError(5, 10, "Error", "output");
            assertThat(error.getToolOutput()).isEqualTo(error.getToolOutput());
        }
    }
}
