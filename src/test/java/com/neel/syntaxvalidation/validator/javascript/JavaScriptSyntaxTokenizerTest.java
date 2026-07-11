package com.neel.syntaxvalidation.validator.javascript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link JavaScriptSyntaxTokenizer}.
 *
 * <p>These tests verify that the lexer correctly classifies tokens, tracks
 * positions, and handles the trickiest disambiguation cases (regex vs.
 * division, template interpolations, string escapes).
 */
@DisplayName("JavaScriptSyntaxTokenizer")
class JavaScriptSyntaxTokenizerTest {

    private List<JsToken> tokenize(String source) {
        return new JavaScriptSyntaxTokenizer(source).tokenize();
    }

    private List<JsToken> significant(String source) {
        return tokenize(source).stream()
                .filter(t -> t.type != JsTokenType.COMMENT && t.type != JsTokenType.EOF)
                .toList();
    }

    @Nested
    @DisplayName("basic tokenisation")
    class BasicTokenisation {

        @Test
        @DisplayName("identifiers and keywords")
        void identifiersAndKeywords() {
            List<JsToken> tokens = significant("const x = 1;");
            assertThat(tokens.get(0).type).isEqualTo(JsTokenType.KEYWORD);
            assertThat(tokens.get(0).text).isEqualTo("const");
            assertThat(tokens.get(1).type).isEqualTo(JsTokenType.IDENTIFIER);
            assertThat(tokens.get(1).text).isEqualTo("x");
        }

        @Test
        @DisplayName("numbers")
        void numbers() {
            List<JsToken> tokens = significant("0xFF 0b101 0o17 3.14 1_000n .5");
            assertThat(tokens).allMatch(t -> t.type == JsTokenType.NUMBER);
        }

        @Test
        @DisplayName("strings")
        void strings() {
            List<JsToken> tokens = significant("'single' \"double\"");
            assertThat(tokens).hasSize(2);
            assertThat(tokens).allMatch(t -> t.type == JsTokenType.STRING);
        }

        @Test
        @DisplayName("template literal")
        void templateLiteral() {
            List<JsToken> tokens = significant("`hello ${world}!`");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type).isEqualTo(JsTokenType.TEMPLATE);
        }

        @Test
        @DisplayName("multi-character operators")
        void multiCharOperators() {
            List<JsToken> tokens = significant("=> === !== **= ... ??");
            assertThat(tokens.get(0).text).isEqualTo("=>");
            assertThat(tokens.get(1).text).isEqualTo("===");
            assertThat(tokens.get(2).text).isEqualTo("!==");
            assertThat(tokens.get(3).text).isEqualTo("**=");
            assertThat(tokens.get(4).text).isEqualTo("...");
            assertThat(tokens.get(5).text).isEqualTo("??");
        }

        @Test
        @DisplayName("optional chaining is a single token")
        void optionalChainingToken() {
            List<JsToken> tokens = significant("a?.b");
            assertThat(tokens.get(1).text).isEqualTo("?.");
        }

        @Test
        @DisplayName("hashbang is skipped")
        void hashbangSkipped() {
            List<JsToken> tokens = significant("#!/usr/bin/env node\nconst x = 1;");
            assertThat(tokens.get(0).text).isEqualTo("const");
        }
    }

    @Nested
    @DisplayName("regex vs division disambiguation")
    class RegexDisambiguation {

        @Test
        @DisplayName("slash after identifier is division")
        void divisionAfterIdentifier() {
            List<JsToken> tokens = significant("a / b");
            // / should be PUNCTUATION (division), not REGEX
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.PUNCTUATION && t.text.equals("/"));
            assertThat(tokens).noneMatch(t -> t.type == JsTokenType.REGEX);
        }

        @Test
        @DisplayName("slash at start of file is regex")
        void regexAtStart() {
            List<JsToken> tokens = significant("/pattern/g");
            assertThat(tokens.get(0).type).isEqualTo(JsTokenType.REGEX);
        }

        @Test
        @DisplayName("slash after return is regex")
        void regexAfterReturn() {
            List<JsToken> tokens = significant("return /re/;");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.REGEX);
        }

        @Test
        @DisplayName("slash after comma is regex")
        void regexAfterComma() {
            List<JsToken> tokens = significant("[1, /re/]");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.REGEX);
        }

        @Test
        @DisplayName("slash after closing paren is division")
        void divisionAfterParen() {
            List<JsToken> tokens = significant("foo() / 2");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.PUNCTUATION && t.text.equals("/"));
            assertThat(tokens).noneMatch(t -> t.type == JsTokenType.REGEX);
        }

        @Test
        @DisplayName("regex with character class containing slash-like chars")
        void regexWithCharClass() {
            List<JsToken> tokens = significant("const r = /[/]/;");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.REGEX);
        }
    }

    @Nested
    @DisplayName("template literal interpolation")
    class TemplateInterpolation {

        @Test
        @DisplayName("simple interpolation produces one template token")
        void simpleInterpolation() {
            List<JsToken> tokens = significant("`${x}`");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type).isEqualTo(JsTokenType.TEMPLATE);
        }

        @Test
        @DisplayName("brackets in interpolation are not emitted as separate tokens")
        void interpolationBracketsNotEmitted() {
            List<JsToken> tokens = significant("`${arr[0](x)}`");
            // Should be a single TEMPLATE token — no stray ( ) [ ] tokens
            assertThat(tokens).hasSize(1);
        }

        @Test
        @DisplayName("nested templates")
        void nestedTemplates() {
            List<JsToken> tokens = significant("`${`inner ${x}`}`");
            assertThat(tokens).hasSize(1);
        }

        @Test
        @DisplayName("multiple interpolations in one template")
        void multipleInterpolations() {
            List<JsToken> tokens = significant("`${a}${b}${c}`");
            assertThat(tokens).hasSize(1);
        }
    }

    @Nested
    @DisplayName("error tokens")
    class ErrorTokens {

        @Test
        @DisplayName("unterminated string")
        void unterminatedString() {
            List<JsToken> tokens = tokenize("\"oops");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.ERROR
                    && t.text.toLowerCase().contains("unterminated"));
        }

        @Test
        @DisplayName("unterminated block comment")
        void unterminatedBlockComment() {
            List<JsToken> tokens = tokenize("/* never ends");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.ERROR);
        }

        @Test
        @DisplayName("unterminated template")
        void unterminatedTemplate() {
            List<JsToken> tokens = tokenize("`no closing backtick");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.ERROR);
        }

        @Test
        @DisplayName("continues scanning after error")
        void continuesAfterError() {
            List<JsToken> tokens = significant("\"bad\nconst x = 1;");
            assertThat(tokens).anyMatch(t -> t.type == JsTokenType.KEYWORD && t.text.equals("const"));
        }
    }

    @Nested
    @DisplayName("position tracking")
    class PositionTracking {

        @Test
        @DisplayName("first token starts at line 1 column 1")
        void firstTokenPosition() {
            List<JsToken> tokens = tokenize("const x;");
            JsToken first = tokens.get(0);
            assertThat(first.line).isEqualTo(1);
            assertThat(first.column).isEqualTo(1);
        }

        @Test
        @DisplayName("multi-line source tracks line numbers")
        void multiLinePositions() {
            List<JsToken> tokens = tokenize("const a = 1;\nconst b = 2;\nconst c = 3;");
            JsToken thirdLineKeyword = tokens.stream()
                    .filter(t -> t.text.equals("const") && t.line == 3)
                    .findFirst()
                    .orElse(null);
            assertThat(thirdLineKeyword).isNotNull();
            assertThat(thirdLineKeyword.column).isEqualTo(1);
        }

        @Test
        @DisplayName("EOF token is always present")
        void eofPresent() {
            List<JsToken> tokens = tokenize("x");
            assertThat(tokens.getLast().type).isEqualTo(JsTokenType.EOF);
        }

        @Test
        @DisplayName("empty source produces only EOF")
        void emptySourceOnlyEof() {
            List<JsToken> tokens = tokenize("");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type).isEqualTo(JsTokenType.EOF);
        }
    }
}
