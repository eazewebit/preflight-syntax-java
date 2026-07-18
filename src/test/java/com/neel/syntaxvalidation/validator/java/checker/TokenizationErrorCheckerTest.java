package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaLexer;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenizationErrorChecker}.
 */
@DisplayName("TokenizationErrorChecker")
class TokenizationErrorCheckerTest {

    private final TokenizationErrorChecker checker = new TokenizationErrorChecker();

    private List<ValidationError> check(String src) {
        List<JavaToken> tokens = new JavaLexer(src).tokenize();
        List<ValidationError> errors = new ArrayList<>();
        checker.check(tokens, errors);
        return errors;
    }

    @Test
    @DisplayName("clean source produces no errors")
    void cleanSourceNoErrors() {
        assertThat(check("class Foo { }")).isEmpty();
    }

    @Test
    @DisplayName("unterminated string is reported")
    void unterminatedString() {
        List<ValidationError> errors = check("\"oops");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("Lexical error");
    }

    @Test
    @DisplayName("unterminated block comment is reported")
    void unterminatedBlockComment() {
        assertThat(check("/* never ends")).isNotEmpty();
    }

    @Test
    @DisplayName("illegal character is reported")
    void illegalCharacter() {
        assertThat(check("`")).isNotEmpty();
    }

    @Test
    @DisplayName("unterminated char literal is reported")
    void unterminatedChar() {
        assertThat(check("'a")).isNotEmpty();
    }

    @Test
    @DisplayName("multiple errors are all reported")
    void multipleErrors() {
        assertThat(check("\"bad\n`bad2")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("long error text is truncated in message")
    void longErrorTextTruncated() {
        String longBad = "\"" + "a".repeat(100);
        List<ValidationError> errors = check(longBad);
        assertThat(errors).hasSize(1);
        ValidationError err = errors.get(0);
        assertThat(err.getMessage()).contains("\u2026");
    }

    @Test
    @DisplayName("error preserves line and column")
    void errorPreservesPosition() {
        List<ValidationError> errors = check("  \"oops");
        assertThat(errors.get(0).getLine()).isEqualTo(1);
        assertThat(errors.get(0).getColumn()).isEqualTo(3);
    }
}
