package com.neel.syntaxvalidation.validator.typescript;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TypeScriptSyntaxTokenizer}.
 */
@DisplayName("TypeScriptSyntaxTokenizer")
class TypeScriptSyntaxTokenizerTest {

    private TypeScriptSyntaxTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new TypeScriptSyntaxTokenizer();
    }

    @Nested
    @DisplayName("empty and null input")
    class EmptyAndNullInput {

        @Test
        @DisplayName("should return single EOF token for null input")
        void shouldReturnSingleEofTokenForNullInput() {
            List<TsToken> tokens = tokenizer.tokenize(null);

            assertThat(tokens).hasSize(1);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.EOF);
        }

        @Test
        @DisplayName("should return single EOF token for empty string")
        void shouldReturnSingleEofTokenForEmptyString() {
            List<TsToken> tokens = tokenizer.tokenize("");

            assertThat(tokens).hasSize(1);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.EOF);
        }
    }

    @Nested
    @DisplayName("whitespace")
    class Whitespace {

        @Test
        @DisplayName("should tokenize spaces as whitespace")
        void shouldTokenizeSpacesAsWhitespace() {
            List<TsToken> tokens = tokenizer.tokenize("   ");

            assertThat(tokens).hasSize(2); // WHITESPACE + EOF
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.WHITESPACE);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("   ");
        }

        @Test
        @DisplayName("should tokenize newlines and track line numbers")
        void shouldTokenizeNewlinesAndTrackLineNumbers() {
            List<TsToken> tokens = tokenizer.tokenize("a\nb");

            // Should have IDENTIFIER, WHITESPACE, IDENTIFIER, EOF
            assertThat(tokens).hasSize(4);
            assertThat(tokens.get(0).line()).isEqualTo(1);
            assertThat(tokens.get(2).line()).isEqualTo(2);
        }

        @Test
        @DisplayName("should track column numbers correctly")
        void shouldTrackColumnNumbersCorrectly() {
            List<TsToken> tokens = tokenizer.tokenize("  foo");

            assertThat(tokens).hasSize(3); // WHITESPACE, IDENTIFIER, EOF
            assertThat(tokens.get(0).column()).isEqualTo(1);
            assertThat(tokens.get(1).column()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        @DisplayName("should tokenize single-line comments")
        void shouldTokenizeSingleLineComments() {
            List<TsToken> tokens = tokenizer.tokenize("// this is a comment");

            assertThat(tokens).hasSize(2); // LINE_COMMENT + EOF
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.LINE_COMMENT);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("// this is a comment");
        }

        @Test
        @DisplayName("should tokenize block comments spanning multiple lines")
        void shouldTokenizeBlockCommentsSpanningMultipleLines() {
            String source = "/* multi\nline\ncomment */";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens).hasSize(2); // BLOCK_COMMENT + EOF
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.BLOCK_COMMENT);
            assertThat(tokens.getFirst().lexeme()).isEqualTo(source);
        }

        @Test
        @DisplayName("should track line numbers inside block comments")
        void shouldTrackLineNumbersInsideBlockComments() {
            List<TsToken> tokens = tokenizer.tokenize("/*\n\n*/foo");

            // BLOCK_COMMENT, WHITESPACE(?), IDENTIFIER, EOF
            assertThat(tokens.stream().anyMatch(t ->
                    t.type() == TsTokenType.BLOCK_COMMENT && t.line() == 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("string literals")
    class StringLiterals {

        @Test
        @DisplayName("should tokenize single-quoted strings")
        void shouldTokenizeSingleQuotedStrings() {
            List<TsToken> tokens = tokenizer.tokenize("'hello world'");

            assertThat(tokens).hasSize(2); // STRING + EOF
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.STRING);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("'hello world'");
        }

        @Test
        @DisplayName("should tokenize double-quoted strings")
        void shouldTokenizeDoubleQuotedStrings() {
            List<TsToken> tokens = tokenizer.tokenize("\"hello\"");

            assertThat(tokens).hasSize(2); // STRING + EOF
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.STRING);
        }

        @Test
        @DisplayName("should handle escaped quotes inside strings")
        void shouldHandleEscapedQuotesInsideStrings() {
            List<TsToken> tokens = tokenizer.tokenize("'it\\'s a test'");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.STRING);
        }

        @Test
        @DisplayName("should handle escape sequences in strings")
        void shouldHandleEscapeSequencesInStrings() {
            List<TsToken> tokens = tokenizer.tokenize("'line1\\nline2'");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.STRING);
        }
    }

    @Nested
    @DisplayName("numbers")
    class Numbers {

        @Test
        @DisplayName("should tokenize integer literals")
        void shouldTokenizeIntegerLiterals() {
            List<TsToken> tokens = tokenizer.tokenize("42");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.NUMBER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("42");
        }

        @Test
        @DisplayName("should tokenize floating-point literals")
        void shouldTokenizeFloatingPointLiterals() {
            List<TsToken> tokens = tokenizer.tokenize("3.14");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.NUMBER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("3.14");
        }

        @Test
        @DisplayName("should tokenize hexadecimal literals")
        void shouldTokenizeHexadecimalLiterals() {
            List<TsToken> tokens = tokenizer.tokenize("0xFF");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.NUMBER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("0xFF");
        }

        @Test
        @DisplayName("should tokenize BigInt literals")
        void shouldTokenizeBigIntLiterals() {
            List<TsToken> tokens = tokenizer.tokenize("123n");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.NUMBER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("123n");
        }

        @Test
        @DisplayName("should tokenize numbers with underscores")
        void shouldTokenizeNumbersWithUnderscores() {
            List<TsToken> tokens = tokenizer.tokenize("1_000_000");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.NUMBER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("1_000_000");
        }
    }

    @Nested
    @DisplayName("identifiers and keywords")
    class IdentifiersAndKeywords {

        @Test
        @DisplayName("should tokenize identifiers")
        void shouldTokenizeIdentifiers() {
            List<TsToken> tokens = tokenizer.tokenize("myVariable");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("myVariable");
        }

        @Test
        @DisplayName("should tokenize variable declaration keywords")
        void shouldTokenizeVariableDeclarationKeywords() {
            assertThat(tokenizer.tokenize("var").getFirst().type()).isEqualTo(TsTokenType.VARIABLE_DECL);
            assertThat(tokenizer.tokenize("let").getFirst().type()).isEqualTo(TsTokenType.VARIABLE_DECL);
            assertThat(tokenizer.tokenize("const").getFirst().type()).isEqualTo(TsTokenType.VARIABLE_DECL);
        }

        @Test
        @DisplayName("should tokenize TypeScript type keywords")
        void shouldTokenizeTypeScriptTypeKeywords() {
            assertThat(tokenizer.tokenize("string").getFirst().type()).isEqualTo(TsTokenType.TYPE_KEYWORD);
            assertThat(tokenizer.tokenize("number").getFirst().type()).isEqualTo(TsTokenType.TYPE_KEYWORD);
            assertThat(tokenizer.tokenize("boolean").getFirst().type()).isEqualTo(TsTokenType.TYPE_KEYWORD);
            assertThat(tokenizer.tokenize("interface").getFirst().type()).isEqualTo(TsTokenType.TYPE_KEYWORD);
            assertThat(tokenizer.tokenize("type").getFirst().type()).isEqualTo(TsTokenType.TYPE_KEYWORD);
            assertThat(tokenizer.tokenize("enum").getFirst().type()).isEqualTo(TsTokenType.ENUM);
        }

        @Test
        @DisplayName("should tokenize import/export keywords")
        void shouldTokenizeImportExportKeywords() {
            assertThat(tokenizer.tokenize("import").getFirst().type()).isEqualTo(TsTokenType.IMPORT);
            assertThat(tokenizer.tokenize("export").getFirst().type()).isEqualTo(TsTokenType.EXPORT);
            assertThat(tokenizer.tokenize("from").getFirst().type()).isEqualTo(TsTokenType.FROM);
            assertThat(tokenizer.tokenize("default").getFirst().type()).isEqualTo(TsTokenType.DEFAULT);
        }

        @Test
        @DisplayName("should tokenize boolean literals")
        void shouldTokenizeBooleanLiterals() {
            assertThat(tokenizer.tokenize("true").getFirst().type()).isEqualTo(TsTokenType.BOOLEAN);
            assertThat(tokenizer.tokenize("false").getFirst().type()).isEqualTo(TsTokenType.BOOLEAN);
        }

        @Test
        @DisplayName("should tokenize null literal")
        void shouldTokenizeNullLiteral() {
            assertThat(tokenizer.tokenize("null").getFirst().type()).isEqualTo(TsTokenType.NULL_LITERAL);
        }

        @Test
        @DisplayName("should tokenize identifiers with underscores and dollar signs")
        void shouldTokenizeIdentifiersWithSpecialChars() {
            assertThat(tokenizer.tokenize("_private").getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
            assertThat(tokenizer.tokenize("$element").getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
            assertThat(tokenizer.tokenize("__proto__").getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
        }
    }

    @Nested
    @DisplayName("operators")
    class Operators {

        @Test
        @DisplayName("should tokenize arrow operator")
        void shouldTokenizeArrowOperator() {
            List<TsToken> tokens = tokenizer.tokenize("=>");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.OPERATOR);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("=>");
        }

        @Test
        @DisplayName("should tokenize comparison operators")
        void shouldTokenizeComparisonOperators() {
            assertThat(tokenizer.tokenize("===").getFirst().type()).isEqualTo(TsTokenType.OPERATOR);
            assertThat(tokenizer.tokenize("!==").getFirst().type()).isEqualTo(TsTokenType.OPERATOR);
            assertThat(tokenizer.tokenize(">=").getFirst().type()).isEqualTo(TsTokenType.OPERATOR);
            assertThat(tokenizer.tokenize("<=").getFirst().type()).isEqualTo(TsTokenType.OPERATOR);
        }

        @Test
        @DisplayName("should tokenize question mark and colon")
        void shouldTokenizeQuestionMarkAndColon() {
            assertThat(tokenizer.tokenize("?").getFirst().type()).isEqualTo(TsTokenType.QUESTION);
            assertThat(tokenizer.tokenize(":").getFirst().type()).isEqualTo(TsTokenType.COLON);
        }

        @Test
        @DisplayName("should tokenize spread operator")
        void shouldTokenizeSpreadOperator() {
            List<TsToken> tokens = tokenizer.tokenize("...");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.SPREAD);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("...");
        }
    }

    @Nested
    @DisplayName("delimiters")
    class Delimiters {

        @Test
        @DisplayName("should tokenize all delimiter types")
        void shouldTokenizeAllDelimiterTypes() {
            assertThat(tokenizer.tokenize("(").getFirst().type()).isEqualTo(TsTokenType.LPAREN);
            assertThat(tokenizer.tokenize(")").getFirst().type()).isEqualTo(TsTokenType.RPAREN);
            assertThat(tokenizer.tokenize("{").getFirst().type()).isEqualTo(TsTokenType.LBRACE);
            assertThat(tokenizer.tokenize("}").getFirst().type()).isEqualTo(TsTokenType.RBRACE);
            assertThat(tokenizer.tokenize("[").getFirst().type()).isEqualTo(TsTokenType.LBRACKET);
            assertThat(tokenizer.tokenize("]").getFirst().type()).isEqualTo(TsTokenType.RBRACKET);
            assertThat(tokenizer.tokenize(";").getFirst().type()).isEqualTo(TsTokenType.SEMICOLON);
            assertThat(tokenizer.tokenize(",").getFirst().type()).isEqualTo(TsTokenType.COMMA);
            assertThat(tokenizer.tokenize(".").getFirst().type()).isEqualTo(TsTokenType.DOT);
        }
    }

    @Nested
    @DisplayName("complex TypeScript syntax")
    class ComplexTypeScriptSyntax {

        @Test
        @DisplayName("should tokenize interface declaration")
        void shouldTokenizeInterfaceDeclaration() {
            String source = "interface User { name: string; age: number; }";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.TYPE_KEYWORD && t.lexeme().equals("interface"))).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.IDENTIFIER && t.lexeme().equals("User"))).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.LBRACE)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.RBRACE)).isTrue();
        }

        @Test
        @DisplayName("should tokenize generic type parameters")
        void shouldTokenizeGenericTypeParameters() {
            String source = "function identity<T>(arg: T): T { return arg; }";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.FUNCTION)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.IDENTIFIER && t.lexeme().equals("identity"))).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.LPAREN)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.RPAREN)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.COLON)).isTrue();
        }

        @Test
        @DisplayName("should tokenize type alias declaration")
        void shouldTokenizeTypeAliasDeclaration() {
            String source = "type Result<T> = { data: T; error?: string; };";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.TYPE_KEYWORD && t.lexeme().equals("type"))).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.OPERATOR && t.lexeme().equals("="))).isTrue();
        }

        @Test
        @DisplayName("should tokenize import statement")
        void shouldTokenizeImportStatement() {
            String source = "import { useState } from 'react';";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.IMPORT)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.FROM)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.STRING)).isTrue();
        }

        @Test
        @DisplayName("should tokenize async/await")
        void shouldTokenizeAsyncAwait() {
            String source = "async function fetchData() { await fetch(url); }";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.ASYNC)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.AWAIT)).isTrue();
        }

        @Test
        @DisplayName("should tokenize enum declaration")
        void shouldTokenizeEnumDeclaration() {
            String source = "enum Color { Red, Green, Blue }";
            List<TsToken> tokens = tokenizer.tokenize(source);

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.ENUM)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.IDENTIFIER && t.lexeme().equals("Color"))).isTrue();
        }
    }

    @Nested
    @DisplayName("JSX mode")
    class JsxMode {

        @Test
        @DisplayName("should tokenize JSX tags when JSX mode is enabled")
        void shouldTokenizeJsxTagsWhenJsxModeEnabled() {
            tokenizer.enableJsxMode();
            List<TsToken> tokens = tokenizer.tokenize("<div>hello</div>");

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.JSX_TAG_OPEN)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.JSX_TAG_CLOSE)).isTrue();
        }

        @Test
        @DisplayName("should tokenize self-closing JSX tags")
        void shouldTokenizeSelfClosingJsxTags() {
            tokenizer.enableJsxMode();
            List<TsToken> tokens = tokenizer.tokenize("<br />");

            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.JSX_TAG_OPEN)).isTrue();
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.JSX_SELF_CLOSE)).isTrue();
        }

        @Test
        @DisplayName("should disable JSX mode")
        void shouldDisableJsxMode() {
            tokenizer.enableJsxMode();
            tokenizer.disableJsxMode();

            // After disabling, < should not be tokenized as JSX_TAG_OPEN
            List<TsToken> tokens = tokenizer.tokenize("<div>");
            assertThat(tokens.stream().anyMatch(t -> t.type() == TsTokenType.JSX_TAG_OPEN)).isFalse();
        }
    }

    @Nested
    @DisplayName("template literals")
    class TemplateLiterals {

        @Test
        @DisplayName("should tokenize simple template literals")
        void shouldTokenizeSimpleTemplateLiterals() {
            List<TsToken> tokens = tokenizer.tokenize("`hello world`");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.TEMPLATE_LITERAL);
        }

        @Test
        @DisplayName("should tokenize template literals with interpolation")
        void shouldTokenizeTemplateLiteralsWithInterpolation() {
            List<TsToken> tokens = tokenizer.tokenize("`hello ${name}`");

            // We should get template literal tokens for the parts
            assertThat(tokens.stream().anyMatch(t ->
                    t.type() == TsTokenType.TEMPLATE_LITERAL)).isTrue();
        }
    }

    @Nested
    @DisplayName("EOF handling")
    class EofHandling {

        @Test
        @DisplayName("should always end with EOF token")
        void shouldAlwaysEndWithEofToken() {
            List<TsToken> tokens = tokenizer.tokenize("let x = 1;");

            assertThat(tokens.getLast().type()).isEqualTo(TsTokenType.EOF);
        }

        @Test
        @DisplayName("should have correct EOF position for multi-line input")
        void shouldHaveCorrectEofPositionForMultiLineInput() {
            List<TsToken> tokens = tokenizer.tokenize("a\nb\nc");

            TsToken eof = tokens.getLast();
            assertThat(eof.type()).isEqualTo(TsTokenType.EOF);
            assertThat(eof.line()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle single character input")
        void shouldHandleSingleCharacterInput() {
            List<TsToken> tokens = tokenizer.tokenize("x");

            assertThat(tokens).hasSize(2);
            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
            assertThat(tokens.getFirst().lexeme()).isEqualTo("x");
        }

        @Test
        @DisplayName("should handle very long identifiers")
        void shouldHandleVeryLongIdentifiers() {
            String longName = "a".repeat(10000);
            List<TsToken> tokens = tokenizer.tokenize(longName);

            assertThat(tokens.getFirst().type()).isEqualTo(TsTokenType.IDENTIFIER);
            assertThat(tokens.getFirst().lexeme()).hasSize(10000);
        }

        @Test
        @DisplayName("should skip unknown characters gracefully")
        void shouldSkipUnknownCharactersGracefully() {
            // Use a character that's not a letter, digit, operator, or delimiter
            List<TsToken> tokens = tokenizer.tokenize("@#$%");

            // Should produce some tokens and end with EOF
            assertThat(tokens.getLast().type()).isEqualTo(TsTokenType.EOF);
        }

        @Test
        @DisplayName("should handle consecutive operators")
        void shouldHandleConsecutiveOperators() {
            List<TsToken> tokens = tokenizer.tokenize("+= -= *= /=");

            // Each operator should be a separate OPERATOR token (with whitespace between)
            long operatorCount = tokens.stream()
                    .filter(t -> t.type() == TsTokenType.OPERATOR)
                    .count();
            assertThat(operatorCount).isEqualTo(4);
        }
    }
}
