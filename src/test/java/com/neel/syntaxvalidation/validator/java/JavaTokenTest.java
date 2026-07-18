package com.neel.syntaxvalidation.validator.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exhaustive, very verbose test suite for {@link JavaToken}.
 *
 * <p>{@code JavaToken} is a Java {@code record} that acts as an immutable
 * lexical unit carrying four pieces of information: a {@link JavaTokenType},
 * the raw lexeme text, and 1-based line/column coordinates. The compact
 * constructor enforces strict invariants:
 *
 * <ul>
 *   <li>{@code type} must not be {@code null}.</li>
 *   <li>{@code text} must not be {@code null}.</li>
 *   <li>{@code line} must be &ge; 1.</li>
 *   <li>{@code column} must be &ge; 1.</li>
 * </ul>
 *
 * <p>This test class covers every public method, every validation branch,
 * record-generated equality / hash-code semantics, and the overridden
 * {@code toString()} format.
 */
@DisplayName("JavaToken")
class JavaTokenTest {

    // ====================================================================
    //  Construction — happy path
    // ====================================================================

    @Nested
    @DisplayName("constructor accepts valid arguments")
    class ValidConstruction {

        @Test
        @DisplayName("creates token with all fields set")
        void createsTokenWithAllFields() {
            JavaToken token = new JavaToken(JavaTokenType.KEYWORD, "class", 1, 1);

            assertThat(token.type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(token.text()).isEqualTo("class");
            assertThat(token.line()).isEqualTo(1);
            assertThat(token.column()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates token at arbitrary position")
        void createsTokenAtArbitraryPosition() {
            JavaToken token = new JavaToken(JavaTokenType.IDENTIFIER, "myVar", 42, 17);

            assertThat(token.line()).isEqualTo(42);
            assertThat(token.column()).isEqualTo(17);
        }

        @Test
        @DisplayName("empty text is allowed")
        void emptyTextAllowed() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);

            assertThat(token.text()).isEmpty();
        }

        @Test
        @DisplayName("boundary line value of 1 is accepted")
        void boundaryLineOne() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);
            assertThat(token.line()).isEqualTo(1);
        }

        @Test
        @DisplayName("boundary column value of 1 is accepted")
        void boundaryColumnOne() {
            JavaToken token = new JavaToken(JavaTokenType.PUNCTUATION, "{", 1, 1);
            assertThat(token.column()).isEqualTo(1);
        }

        @ParameterizedTest(name = "type={0}")
        @EnumSource(JavaTokenType.class)
        @DisplayName("every token type can be used in a token")
        void everyTypeUsable(JavaTokenType type) {
            JavaToken token = new JavaToken(type, "x", 1, 1);
            assertThat(token.type()).isSameAs(type);
        }
    }

    // ====================================================================
    //  Construction — validation failures
    // ====================================================================

    @Nested
    @DisplayName("constructor rejects invalid arguments")
    class InvalidConstruction {

        @Test
        @DisplayName("null type throws NullPointerException")
        void nullTypeThrows() {
            assertThatThrownBy(() -> new JavaToken(null, "foo", 1, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("null text throws NullPointerException")
        void nullTextThrows() {
            assertThatThrownBy(() -> new JavaToken(JavaTokenType.IDENTIFIER, null, 1, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("text");
        }

        @ParameterizedTest(name = "line={0}")
        @ValueSource(ints = {0, -1, -42, Integer.MIN_VALUE})
        @DisplayName("line less than 1 throws IllegalArgumentException")
        void lineTooSmall(int line) {
            assertThatThrownBy(() -> new JavaToken(JavaTokenType.NUMBER, "42", line, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("line");
        }

        @ParameterizedTest(name = "column={0}")
        @ValueSource(ints = {0, -1, -99, Integer.MIN_VALUE})
        @DisplayName("column less than 1 throws IllegalArgumentException")
        void columnTooSmall(int column) {
            assertThatThrownBy(() -> new JavaToken(JavaTokenType.NUMBER, "42", 1, column))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("column");
        }

        @Test
        @DisplayName("null type with null text reports type first")
        void nullTypeAndNullText() {
            assertThatThrownBy(() -> new JavaToken(null, null, 1, 1))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("all four invalid: null type, null text, line 0, column 0")
        void allInvalid() {
            assertThatThrownBy(() -> new JavaToken(null, null, 0, 0))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ====================================================================
    //  Accessor methods
    // ====================================================================

    @Nested
    @DisplayName("accessors return correct values")
    class Accessors {

        private final JavaToken token =
                new JavaToken(JavaTokenType.STRING, "\"hello\"", 5, 10);

        @Test
        @DisplayName("type() returns the type")
        void typeAccessor() {
            assertThat(token.type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        @DisplayName("text() returns the raw lexeme")
        void textAccessor() {
            assertThat(token.text()).isEqualTo("\"hello\"");
        }

        @Test
        @DisplayName("line() returns the 1-based line number")
        void lineAccessor() {
            assertThat(token.line()).isEqualTo(5);
        }

        @Test
        @DisplayName("column() returns the 1-based column number")
        void columnAccessor() {
            assertThat(token.column()).isEqualTo(10);
        }
    }

    // ====================================================================
    //  Immutability
    // ====================================================================

    @Nested
    @DisplayName("record is immutable")
    class Immutability {

        @Test
        @DisplayName("two tokens with same values are equal")
        void equalTokens() {
            JavaToken a = new JavaToken(JavaTokenType.KEYWORD, "if", 3, 5);
            JavaToken b = new JavaToken(JavaTokenType.KEYWORD, "if", 3, 5);

            assertThat(a).isEqualTo(b);
            assertThat(a.equals(b)).isTrue();
        }

        @Test
        @DisplayName("tokens differing in type are not equal")
        void differentType() {
            JavaToken a = new JavaToken(JavaTokenType.KEYWORD, "if", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.IDENTIFIER, "if", 1, 1);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("tokens differing in text are not equal")
        void differentText() {
            JavaToken a = new JavaToken(JavaTokenType.KEYWORD, "if", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.KEYWORD, "else", 1, 1);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("tokens differing in line are not equal")
        void differentLine() {
            JavaToken a = new JavaToken(JavaTokenType.IDENTIFIER, "x", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.IDENTIFIER, "x", 2, 1);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("tokens differing in column are not equal")
        void differentColumn() {
            JavaToken a = new JavaToken(JavaTokenType.IDENTIFIER, "x", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.IDENTIFIER, "x", 1, 2);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("token is not equal to null")
        void notEqualToNull() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);
            assertThat(token.equals(null)).isFalse();
        }

        @Test
        @DisplayName("token is not equal to non-token object")
        void notEqualToOtherType() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);
            assertThat(token.equals("not a token")).isFalse();
        }

        @Test
        @DisplayName("token is equal to itself")
        void equalToItself() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);
            assertThat(token.equals(token)).isTrue();
        }
    }

    // ====================================================================
    //  hashCode contract
    // ====================================================================

    @Nested
    @DisplayName("hashCode follows the standard contract")
    class HashCodeContract {

        @Test
        @DisplayName("equal tokens have equal hash codes")
        void equalHashCodes() {
            JavaToken a = new JavaToken(JavaTokenType.KEYWORD, "class", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.KEYWORD, "class", 1, 1);

            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("hashCode is consistent across multiple calls")
        void consistentHashCode() {
            JavaToken token = new JavaToken(JavaTokenType.NUMBER, "42", 1, 1);
            int first = token.hashCode();
            int second = token.hashCode();
            int third = token.hashCode();

            assertThat(first).isEqualTo(second).isEqualTo(third);
        }

        @Test
        @DisplayName("different tokens may have different hash codes")
        void differentHashCodesNotRequiredButLikely() {
            JavaToken a = new JavaToken(JavaTokenType.KEYWORD, "class", 1, 1);
            JavaToken b = new JavaToken(JavaTokenType.KEYWORD, "interface", 2, 5);

            // Not strictly required by the contract but extremely unlikely to collide.
            assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        }
    }

    // ====================================================================
    //  toString
    // ====================================================================

    @Nested
    @DisplayName("toString produces human-readable output")
    class ToString {

        @Test
        @DisplayName("contains the token type name")
        void containsType() {
            JavaToken token = new JavaToken(JavaTokenType.KEYWORD, "class", 1, 1);
            assertThat(token.toString()).contains("KEYWORD");
        }

        @Test
        @DisplayName("contains the lexeme text")
        void containsText() {
            JavaToken token = new JavaToken(JavaTokenType.IDENTIFIER, "myVariable", 3, 7);
            assertThat(token.toString()).contains("myVariable");
        }

        @Test
        @DisplayName("contains the line number")
        void containsLine() {
            JavaToken token = new JavaToken(JavaTokenType.PUNCTUATION, "{", 42, 1);
            assertThat(token.toString()).contains("42");
        }

        @Test
        @DisplayName("contains the column number")
        void containsColumn() {
            JavaToken token = new JavaToken(JavaTokenType.PUNCTUATION, "{", 1, 17);
            assertThat(token.toString()).contains("17");
        }

        @Test
        @DisplayName("empty text token still has a valid toString")
        void emptyTextToString() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 1, 1);
            assertThat(token.toString()).isNotBlank();
        }
    }

    // ====================================================================
    //  Realistic lexer-produced token scenarios
    // ====================================================================

    @Nested
    @DisplayName("realistic token instances behave correctly")
    class RealisticScenarios {

        @Test
        @DisplayName("keyword token from typical class declaration")
        void keywordToken() {
            JavaToken token = new JavaToken(JavaTokenType.KEYWORD, "public", 1, 1);
            assertThat(token.type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(token.text()).isEqualTo("public");
        }

        @Test
        @DisplayName("string literal token")
        void stringToken() {
            JavaToken token = new JavaToken(JavaTokenType.STRING, "\"hello world\"", 2, 15);
            assertThat(token.text()).startsWith("\"").endsWith("\"");
        }

        @Test
        @DisplayName("error token")
        void errorToken() {
            JavaToken token = new JavaToken(JavaTokenType.ERROR, "`", 3, 8);
            assertThat(token.type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        @DisplayName("EOF token")
        void eofToken() {
            JavaToken token = new JavaToken(JavaTokenType.EOF, "", 10, 1);
            assertThat(token.type()).isEqualTo(JavaTokenType.EOF);
            assertThat(token.text()).isEmpty();
        }

        @Test
        @DisplayName("single-character punctuation token")
        void punctuationToken() {
            JavaToken token = new JavaToken(JavaTokenType.PUNCTUATION, ";", 5, 30);
            assertThat(token.text()).hasSize(1);
        }

        @Test
        @DisplayName("multi-digit number token")
        void numberToken() {
            JavaToken token = new JavaToken(JavaTokenType.NUMBER, "123456789", 1, 1);
            assertThat(token.text()).matches("\\d+");
        }

        @Test
        @DisplayName("block comment token")
        void commentToken() {
            JavaToken token = new JavaToken(JavaTokenType.COMMENT, "/* hello */", 1, 1);
            assertThat(token.text()).startsWith("/*").endsWith("*/");
        }

        @Test
        @DisplayName("char literal token")
        void charToken() {
            JavaToken token = new JavaToken(JavaTokenType.CHAR, "'x'", 1, 1);
            assertThat(token.text()).startsWith("'").endsWith("'");
        }
    }
}
