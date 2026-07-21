package com.neel.syntaxvalidation.validator.typescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TsTokenType}.
 */
@DisplayName("TsTokenType")
class TsTokenTypeTest {

    @Test
    @DisplayName("should have all literal types")
    void shouldHaveAllLiteralTypes() {
        EnumSet<TsTokenType> literals = EnumSet.of(
                TsTokenType.NUMBER, TsTokenType.STRING, TsTokenType.TEMPLATE_LITERAL,
                TsTokenType.BOOLEAN, TsTokenType.NULL_LITERAL, TsTokenType.REGEX
        );
        assertThat(TsTokenType.values()).containsAll(literals);
    }

    @Test
    @DisplayName("should have all delimiter types")
    void shouldHaveAllDelimiterTypes() {
        EnumSet<TsTokenType> delimiters = EnumSet.of(
                TsTokenType.LPAREN, TsTokenType.RPAREN,
                TsTokenType.LBRACE, TsTokenType.RBRACE,
                TsTokenType.LBRACKET, TsTokenType.RBRACKET,
                TsTokenType.SEMICOLON, TsTokenType.COMMA, TsTokenType.DOT
        );
        assertThat(TsTokenType.values()).containsAll(delimiters);
    }

    @Test
    @DisplayName("should have all JSX types")
    void shouldHaveAllJsxTypes() {
        EnumSet<TsTokenType> jsxTypes = EnumSet.of(
                TsTokenType.JSX_TAG_OPEN, TsTokenType.JSX_TAG_CLOSE,
                TsTokenType.JSX_SELF_CLOSE, TsTokenType.JSX_ATTR_EQ
        );
        assertThat(TsTokenType.values()).containsAll(jsxTypes);
    }

    @Test
    @DisplayName("should have all TypeScript-specific types")
    void shouldHaveAllTsSpecificTypes() {
        EnumSet<TsTokenType> tsTypes = EnumSet.of(
                TsTokenType.TYPE_KEYWORD, TsTokenType.IMPORT, TsTokenType.FROM,
                TsTokenType.EXPORT, TsTokenType.DEFAULT, TsTokenType.AS,
                TsTokenType.TYPEOF, TsTokenType.KEYOF, TsTokenType.EXTENDS,
                TsTokenType.IMPLEMENTS, TsTokenType.DECLARE, TsTokenType.NAMESPACE,
                TsTokenType.ENUM, TsTokenType.GENERIC_OPEN, TsTokenType.GENERIC_CLOSE
        );
        assertThat(TsTokenType.values()).containsAll(tsTypes);
    }

    @ParameterizedTest
    @EnumSource(TsTokenType.class)
    @DisplayName("each type should have a non-null name")
    void eachTypeShouldHaveNonNullName(TsTokenType type) {
        assertThat(type.name()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("should have expected total number of types")
    void shouldHaveExpectedTotalNumberOfTypes() {
        // As of the current implementation, we expect a specific count
        assertThat(TsTokenType.values().length).isGreaterThanOrEqualTo(50);
    }
}
