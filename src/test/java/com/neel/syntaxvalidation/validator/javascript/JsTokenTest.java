package com.neel.syntaxvalidation.validator.javascript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link JsToken}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Construction and field access</li>
 *   <li>{@code toString} format</li>
 *   <li>Edge cases for all token types</li>
 *   <li>Line and column boundary values</li>
 * </ul>
 */
@DisplayName("JsToken")
class JsTokenTest {

    // =========================================================================
    //  CONSTRUCTION AND FIELDS
    // =========================================================================

    @Nested
    @DisplayName("construction and fields")
    class ConstructionAndFields {

        @Test
        @DisplayName("stores type correctly")
        void storesType() {
            JsToken token = new JsToken(JsTokenType.KEYWORD, "const", 1, 1);
            assertThat(token.type).isEqualTo(JsTokenType.KEYWORD);
        }

        @Test
        @DisplayName("stores text correctly")
        void storesText() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "myVar", 1, 5);
            assertThat(token.text).isEqualTo("myVar");
        }

        @Test
        @DisplayName("stores line correctly")
        void storesLine() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "x", 42, 1);
            assertThat(token.line).isEqualTo(42);
        }

        @Test
        @DisplayName("stores column correctly")
        void storesColumn() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "x", 1, 15);
            assertThat(token.column).isEqualTo(15);
        }

        @Test
        @DisplayName("stores all fields together")
        void storesAllFields() {
            JsToken token = new JsToken(JsTokenType.STRING, "'hello'", 3, 7);
            assertThat(token.type).isEqualTo(JsTokenType.STRING);
            assertThat(token.text).isEqualTo("'hello'");
            assertThat(token.line).isEqualTo(3);
            assertThat(token.column).isEqualTo(7);
        }
    }

    // =========================================================================
    //  TOSTRING FORMAT
    // =========================================================================

    @Nested
    @DisplayName("toString format")
    class ToStringFormat {

        @Test
        @DisplayName("toString follows TYPE[text]@line:column format")
        void formatCheck() {
            JsToken token = new JsToken(JsTokenType.KEYWORD, "const", 1, 1);
            assertThat(token.toString()).isEqualTo("KEYWORD[const]@1:1");
        }

        @Test
        @DisplayName("toString includes type name")
        void includesType() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "foo", 2, 5);
            assertThat(token.toString()).startsWith("IDENTIFIER");
        }

        @Test
        @DisplayName("toString includes text in brackets")
        void includesText() {
            JsToken token = new JsToken(JsTokenType.NUMBER, "42", 1, 1);
            assertThat(token.toString()).contains("[42]");
        }

        @Test
        @DisplayName("toString includes line and column with @ and : separators")
        void includesPosition() {
            JsToken token = new JsToken(JsTokenType.PUNCTUATION, "=>", 10, 20);
            assertThat(token.toString()).contains("@10:20");
        }

        @Test
        @DisplayName("toString with ERROR type")
        void errorType() {
            JsToken token = new JsToken(JsTokenType.ERROR, "unexpected token", 5, 3);
            assertThat(token.toString()).isEqualTo("ERROR[unexpected token]@5:3");
        }

        @Test
        @DisplayName("toString with EOF type")
        void eofType() {
            JsToken token = new JsToken(JsTokenType.EOF, "", 1, 1);
            assertThat(token.toString()).isEqualTo("EOF[]@1:1");
        }
    }

    // =========================================================================
    //  ALL TOKEN TYPES
    // =========================================================================

    @Nested
    @DisplayName("all token types")
    class AllTokenTypes {

        @Test
        @DisplayName("NUMBER token")
        void numberToken() {
            JsToken token = new JsToken(JsTokenType.NUMBER, "42", 1, 10);
            assertThat(token.type).isEqualTo(JsTokenType.NUMBER);
            assertThat(token.text).isEqualTo("42");
        }

        @Test
        @DisplayName("STRING token")
        void stringToken() {
            JsToken token = new JsToken(JsTokenType.STRING, "'hello world'", 2, 5);
            assertThat(token.type).isEqualTo(JsTokenType.STRING);
        }

        @Test
        @DisplayName("TEMPLATE token")
        void templateToken() {
            JsToken token = new JsToken(JsTokenType.TEMPLATE, "`hello ${name}`", 3, 1);
            assertThat(token.type).isEqualTo(JsTokenType.TEMPLATE);
        }

        @Test
        @DisplayName("REGEX token")
        void regexToken() {
            JsToken token = new JsToken(JsTokenType.REGEX, "/pattern/gi", 1, 20);
            assertThat(token.type).isEqualTo(JsTokenType.REGEX);
        }

        @Test
        @DisplayName("IDENTIFIER token")
        void identifierToken() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "myVariable", 4, 8);
            assertThat(token.type).isEqualTo(JsTokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("KEYWORD token")
        void keywordToken() {
            JsToken token = new JsToken(JsTokenType.KEYWORD, "function", 1, 1);
            assertThat(token.type).isEqualTo(JsTokenType.KEYWORD);
        }

        @Test
        @DisplayName("PUNCTUATION token")
        void punctuationToken() {
            JsToken token = new JsToken(JsTokenType.PUNCTUATION, "=>", 5, 12);
            assertThat(token.type).isEqualTo(JsTokenType.PUNCTUATION);
        }

        @Test
        @DisplayName("COMMENT token")
        void commentToken() {
            JsToken token = new JsToken(JsTokenType.COMMENT, "// a comment", 6, 1);
            assertThat(token.type).isEqualTo(JsTokenType.COMMENT);
        }

        @Test
        @DisplayName("ERROR token")
        void errorToken() {
            JsToken token = new JsToken(JsTokenType.ERROR, "unterminated string", 7, 5);
            assertThat(token.type).isEqualTo(JsTokenType.ERROR);
            assertThat(token.text).isEqualTo("unterminated string");
        }

        @Test
        @DisplayName("EOF token")
        void eofToken() {
            JsToken token = new JsToken(JsTokenType.EOF, "", 10, 1);
            assertThat(token.type).isEqualTo(JsTokenType.EOF);
        }
    }

    // =========================================================================
    //  EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("line 1 column 1 (start of file)")
        void startOfFile() {
            JsToken token = new JsToken(JsTokenType.KEYWORD, "var", 1, 1);
            assertThat(token.line).isEqualTo(1);
            assertThat(token.column).isEqualTo(1);
        }

        @Test
        @DisplayName("very large line number")
        void largeLineNumber() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "x", 999999, 1);
            assertThat(token.line).isEqualTo(999999);
        }

        @Test
        @DisplayName("very large column number")
        void largeColumnNumber() {
            JsToken token = new JsToken(JsTokenType.IDENTIFIER, "x", 1, 999999);
            assertThat(token.column).isEqualTo(999999);
        }

        @Test
        @DisplayName("empty text is valid")
        void emptyText() {
            JsToken token = new JsToken(JsTokenType.EOF, "", 1, 1);
            assertThat(token.text).isEmpty();
        }

        @Test
        @DisplayName("text with special characters")
        void specialCharacters() {
            JsToken token = new JsToken(JsTokenType.STRING, "'àáâãäå'", 1, 1);
            assertThat(token.text).isEqualTo("'àáâãäå'");
        }

        @Test
        @DisplayName("text with unicode characters")
        void unicodeCharacters() {
            JsToken token = new JsToken(JsTokenType.STRING, "'你好世界'", 1, 1);
            assertThat(token.text).isEqualTo("'你好世界'");
        }

        @Test
        @DisplayName("multiline text content in token")
        void multilineText() {
            JsToken token = new JsToken(JsTokenType.COMMENT, "/* multi\nline */", 1, 1);
            assertThat(token.text).contains("multi");
            assertThat(token.text).contains("line");
        }
    }
}