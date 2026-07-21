package com.neel.syntaxvalidation.validator.typescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link TsToken}.
 */
@DisplayName("TsToken")
class TsTokenTest {

    @Test
    @DisplayName("should store type, lexeme, line, and column")
    void shouldStoreAllFields() {
        TsToken token = new TsToken(TsTokenType.IDENTIFIER, "hello", 10, 5);

        assertThat(token.type()).isEqualTo(TsTokenType.IDENTIFIER);
        assertThat(token.lexeme()).isEqualTo("hello");
        assertThat(token.line()).isEqualTo(10);
        assertThat(token.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("should reject null type")
    void shouldRejectNullType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TsToken(null, "x", 1, 1))
                .withMessageContaining("type must not be null");
    }

    @Test
    @DisplayName("should reject null lexeme")
    void shouldRejectNullLexeme() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TsToken(TsTokenType.EOF, null, 1, 1))
                .withMessageContaining("lexeme must not be null");
    }

    @Test
    @DisplayName("should reject negative line number")
    void shouldRejectNegativeLine() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TsToken(TsTokenType.IDENTIFIER, "x", -1, 1))
                .withMessageContaining("line must not be negative");
    }

    @Test
    @DisplayName("should reject negative column number")
    void shouldRejectNegativeColumn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TsToken(TsTokenType.IDENTIFIER, "x", 1, -1))
                .withMessageContaining("column must not be negative");
    }

    @Test
    @DisplayName("should allow zero line and column")
    void shouldAllowZeroLineAndColumn() {
        TsToken token = new TsToken(TsTokenType.EOF, "", 0, 0);
        assertThat(token.line()).isZero();
        assertThat(token.column()).isZero();
    }

    @Test
    @DisplayName("should allow empty lexeme")
    void shouldAllowEmptyLexeme() {
        TsToken token = new TsToken(TsTokenType.EOF, "", 1, 1);
        assertThat(token.lexeme()).isEmpty();
    }

    @Test
    @DisplayName("should support equality and hashCode")
    void shouldSupportEqualityAndHashCode() {
        TsToken a = new TsToken(TsTokenType.NUMBER, "42", 1, 1);
        TsToken b = new TsToken(TsTokenType.NUMBER, "42", 1, 1);
        TsToken c = new TsToken(TsTokenType.STRING, "'42'", 1, 1);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("should support toString")
    void shouldSupportToString() {
        TsToken token = new TsToken(TsTokenType.IDENTIFIER, "foo", 3, 7);
        String str = token.toString();

        assertThat(str).contains("IDENTIFIER", "foo", "3", "7");
    }

    @ParameterizedTest
    @EnumSource(TsTokenType.class)
    @DisplayName("should accept all token types")
    void shouldAcceptAllTokenTypes(TsTokenType type) {
        TsToken token = new TsToken(type, "lexeme", 1, 1);
        assertThat(token.type()).isEqualTo(type);
    }
}
