package com.neel.syntaxvalidation.validator.python;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link PythonLexer}.
 *
 * <p>Covers all token types, edge cases, indentation handling,
 * string literals (including f-strings, t-strings, raw strings,
 * byte strings), numeric literals, operators, and error recovery.
 */
@DisplayName("PythonLexer")
class PythonLexerTest {

    // ==================================================================
    //  Basic tokenization
    // ==================================================================

    @Nested
    @DisplayName("basic tokenization")
    class BasicTokenization {

        @Test
        @DisplayName("empty source produces only EOF")
        void emptySource() {
            List<PythonToken> tokens = new PythonLexer("").tokenize();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.EOF);
        }

        @Test
        @DisplayName("null source produces only EOF")
        void nullSource() {
            List<PythonToken> tokens = new PythonLexer(null).tokenize();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.EOF);
        }

        @Test
        @DisplayName("whitespace-only source produces only EOF")
        void whitespaceOnly() {
            List<PythonToken> tokens = new PythonLexer("   \t  \r  ").tokenize();
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.EOF);
        }

        @Test
        @DisplayName("single newline produces NEWLINE + EOF")
        void singleNewline() {
            List<PythonToken> tokens = new PythonLexer("\n").tokenize();
            assertThat(tokens).hasSize(2);
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.NEWLINE);
            assertThat(tokens.get(1).type()).isEqualTo(PythonTokenType.EOF);
        }
    }

    // ==================================================================
    //  Identifiers and keywords
    // ==================================================================

    @Nested
    @DisplayName("identifiers and keywords")
    class IdentifiersAndKeywords {

        @ParameterizedTest
        @ValueSource(strings = {
                "False", "None", "True", "and", "as", "assert", "async", "await",
                "break", "class", "continue", "def", "del", "elif", "else",
                "except", "finally", "for", "from", "global", "if", "import",
                "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
                "return", "try", "while", "with", "yield"
        })
        @DisplayName("hard keywords produce correct token types")
        void hardKeywords(String keyword) {
            List<PythonToken> tokens = new PythonLexer(keyword + "\n").tokenize();
            assertThat(tokens.get(0).text()).isEqualTo(keyword);
            assertThat(tokens.get(0).type()).isNotEqualTo(PythonTokenType.IDENTIFIER);
        }

        @Test
        @DisplayName("regular identifier produces IDENTIFIER token")
        void regularIdentifier() {
            List<PythonToken> tokens = new PythonLexer("my_variable\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).isEqualTo("my_variable");
        }

        @Test
        @DisplayName("identifier with leading underscore")
        void leadingUnderscore() {
            List<PythonToken> tokens = new PythonLexer("_private\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).isEqualTo("_private");
        }

        @Test
        @DisplayName("double underscore identifier")
        void doubleUnderscore() {
            List<PythonToken> tokens = new PythonLexer("__init__\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).isEqualTo("__init__");
        }

        @Test
        @DisplayName("identifier starting with digit is integer")
        void identifierStartingWithDigit() {
            List<PythonToken> tokens = new PythonLexer("123abc\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.INTEGER_LITERAL);
        }

        @Test
        @DisplayName("unicode identifier")
        void unicodeIdentifier() {
            List<PythonToken> tokens = new PythonLexer("café\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).isEqualTo("café");
        }
    }

    // ==================================================================
    //  Soft keywords
    // ==================================================================

    @Nested
    @DisplayName("soft keywords")
    class SoftKeywords {

        @Test
        @DisplayName("'match' is tokenized as MATCH")
        void matchKeyword() {
            List<PythonToken> tokens = new PythonLexer("match x:\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.MATCH);
        }

        @Test
        @DisplayName("'case' is tokenized as CASE")
        void caseKeyword() {
            List<PythonToken> tokens = new PythonLexer("case 1:\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.CASE);
        }

        @Test
        @DisplayName("'type' is tokenized as TYPE")
        void typeKeyword() {
            List<PythonToken> tokens = new PythonLexer("type Alias = int\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.TYPE);
        }
    }

    // ==================================================================
    //  Numeric literals
    // ==================================================================

    @Nested
    @DisplayName("numeric literals")
    class NumericLiterals {

        @ParameterizedTest
        @CsvSource({
                "42, INTEGER_LITERAL",
                "0, INTEGER_LITERAL",
                "1_000_000, INTEGER_LITERAL",
                "0xff, INTEGER_LITERAL",
                "0o77, INTEGER_LITERAL",
                "0b1010, INTEGER_LITERAL",
                "0xFF_FF, INTEGER_LITERAL"
        })
        @DisplayName("integer literals")
        void integerLiterals(String source, String expectedType) {
            List<PythonToken> tokens = new PythonLexer(source + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.valueOf(expectedType));
            assertThat(tokens.get(0).text()).isEqualTo(source);
        }

        @ParameterizedTest
        @CsvSource({
                "3.14, FLOAT_LITERAL",
                "1e10, FLOAT_LITERAL",
                "1.5e-3, FLOAT_LITERAL",
                "1_000.5, FLOAT_LITERAL",
                ".5, FLOAT_LITERAL",
                "1., FLOAT_LITERAL"
        })
        @DisplayName("float literals")
        void floatLiterals(String source, String expectedType) {
            List<PythonToken> tokens = new PythonLexer(source + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.valueOf(expectedType));
            assertThat(tokens.get(0).text()).isEqualTo(source);
        }

        @ParameterizedTest
        @CsvSource({
                "3j, COMPLEX_LITERAL",
                "1.5j, COMPLEX_LITERAL",
                "3.14J, COMPLEX_LITERAL"
        })
        @DisplayName("complex literals")
        void complexLiterals(String source, String expectedType) {
            List<PythonToken> tokens = new PythonLexer(source + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.valueOf(expectedType));
            assertThat(tokens.get(0).text()).isEqualTo(source);
        }
    }

    // ==================================================================
    //  String literals
    // ==================================================================

    @Nested
    @DisplayName("string literals")
    class StringLiterals {

        @Test
        @DisplayName("single-quoted string")
        void singleQuoted() {
            List<PythonToken> tokens = new PythonLexer("'hello'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
            assertThat(tokens.get(0).text()).isEqualTo("'hello'");
        }

        @Test
        @DisplayName("double-quoted string")
        void doubleQuoted() {
            List<PythonToken> tokens = new PythonLexer("\"world\"\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
            assertThat(tokens.get(0).text()).isEqualTo("\"world\"");
        }

        @Test
        @DisplayName("triple-quoted string")
        void tripleQuoted() {
            List<PythonToken> tokens = new PythonLexer("\"\"\"multi\nline\"\"\"\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }

        @Test
        @DisplayName("raw string")
        void rawString() {
            List<PythonToken> tokens = new PythonLexer("r'path\\to\\file'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }

        @Test
        @DisplayName("byte string")
        void byteString() {
            List<PythonToken> tokens = new PythonLexer("b'data'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }

        @Test
        @DisplayName("f-string")
        void fString() {
            List<PythonToken> tokens = new PythonLexer("f'hello {name}'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }

        @Test
        @DisplayName("t-string (PEP 750)")
        void tString() {
            List<PythonToken> tokens = new PythonLexer("t'hello {name}'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }

        @Test
        @DisplayName("unterminated string produces lexer error")
        void unterminatedString() {
            List<PythonToken> tokens = new PythonLexer("'hello\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == null && t.text().contains("UnterminatedString"));
        }

        @Test
        @DisplayName("empty string")
        void emptyString() {
            List<PythonToken> tokens = new PythonLexer("''\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
            assertThat(tokens.get(0).text()).isEqualTo("''");
        }

        @Test
        @DisplayName("string with escape sequences")
        void escapedString() {
            List<PythonToken> tokens = new PythonLexer("'hello\\nworld'\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.STRING_LITERAL);
        }
    }

    // ==================================================================
    //  Operators and delimiters
    // ==================================================================

    @Nested
    @DisplayName("operators and delimiters")
    class OperatorsAndDelimiters {

        @ParameterizedTest
        @CsvSource({
                "+, PLUS",
                "-, MINUS",
                "*, STAR",
                "/, SLASH",
                "%, PERCENT",
                "@, AT",
                "&, AMPERSAND",
                "|, PIPE",
                "^, CARET",
                "~, TILDE",
                "(, LEFT_PAREN",
                "), RIGHT_PAREN",
                "[, LEFT_BRACKET",
                "], RIGHT_BRACKET",
                "{, LEFT_BRACE",
                "}, RIGHT_BRACE",
                ">, GREATER",
                "<, LESS",
                "., DOT",
                "',', COMMA",
                ":, COLON",
                ";, SEMICOLON"
        })
        @DisplayName("single-character tokens")
        void singleCharTokens(String source, String expectedType) {
            List<PythonToken> tokens = new PythonLexer(source + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.valueOf(expectedType));
            assertThat(tokens.get(0).text()).isEqualTo(source);
        }

        @ParameterizedTest
        @CsvSource({
                "==, EQUAL_EQUAL",
                "!=, NOT_EQUAL",
                "<=, LESS_EQUAL",
                ">=, GREATER_EQUAL",
                "+=, PLUS_EQUAL",
                "-=, MINUS_EQUAL",
                "*=, STAR_EQUAL",
                "/=, SLASH_EQUAL",
                "%=, PERCENT_EQUAL",
                "&=, AMPERSAND_EQUAL",
                "|=, PIPE_EQUAL",
                "^=, CARET_EQUAL",
                "@=, AT_EQUAL",
                "//, DOUBLE_SLASH",
                "**, DOUBLE_STAR",
                "<<, LEFT_SHIFT",
                ">>, RIGHT_SHIFT",
                "->, ARROW",
                "..., ELLIPSIS_LITERAL"
        })
        @DisplayName("multi-character operators")
        void multiCharOperators(String source, String expectedType) {
            List<PythonToken> tokens = new PythonLexer(source + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.valueOf(expectedType));
            assertThat(tokens.get(0).text()).isEqualTo(source);
        }

        @Test
        @DisplayName("walrus operator :=")
        void walrusOperator() {
            List<PythonToken> tokens = new PythonLexer(":=\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.COLON_EQUAL);
        }

        @Test
        @DisplayName("double-star-equal **=")
        void doubleStarEqual() {
            List<PythonToken> tokens = new PythonLexer("**=\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.DOUBLE_STAR_EQUAL);
        }

        @Test
        @DisplayName("double-slash-equal //=")
        void doubleSlashEqual() {
            List<PythonToken> tokens = new PythonLexer("//=\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.DOUBLE_SLASH_EQUAL);
        }

        @Test
        @DisplayName("left-shift-equal <<=")
        void leftShiftEqual() {
            List<PythonToken> tokens = new PythonLexer("<<=\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.LEFT_SHIFT_EQUAL);
        }

        @Test
        @DisplayName("right-shift-equal >>=")
        void rightShiftEqual() {
            List<PythonToken> tokens = new PythonLexer(">>=\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.RIGHT_SHIFT_EQUAL);
        }
    }

    // ==================================================================
    //  Indentation
    // ==================================================================

    @Nested
    @DisplayName("indentation handling")
    class Indentation {

        @Test
        @DisplayName("INDENT and DEDENT for simple block")
        void simpleBlock() {
            List<PythonToken> tokens = new PythonLexer("if True:\n    pass\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.INDENT);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.DEDENT);
        }

        @Test
        @DisplayName("nested indentation")
        void nestedIndentation() {
            List<PythonToken> tokens = new PythonLexer("if True:\n    if False:\n        pass\n").tokenize();
            long indentCount = tokens.stream().filter(t -> t.type() == PythonTokenType.INDENT).count();
            long dedentCount = tokens.stream().filter(t -> t.type() == PythonTokenType.DEDENT).count();
            assertThat(indentCount).isEqualTo(2);
            assertThat(dedentCount).isEqualTo(2);
        }

        @Test
        @DisplayName("blank lines inside blocks are skipped")
        void blankLinesInsideBlocks() {
            List<PythonToken> tokens = new PythonLexer("if True:\n\n    pass\n\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.INDENT);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.DEDENT);
        }

        @Test
        @DisplayName("comment-only lines inside blocks are skipped")
        void commentOnlyLinesInsideBlocks() {
            List<PythonToken> tokens = new PythonLexer("if True:\n    # comment\n    pass\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.INDENT);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.DEDENT);
        }
    }

    // ==================================================================
    //  Line and column tracking
    // ==================================================================

    @Nested
    @DisplayName("line and column tracking")
    class LineAndColumnTracking {

        @Test
        @DisplayName("first token is at line 1, column 1")
        void firstTokenPosition() {
            List<PythonToken> tokens = new PythonLexer("x\n").tokenize();
            assertThat(tokens.get(0).line()).isEqualTo(1);
            assertThat(tokens.get(0).column()).isEqualTo(1);
        }

        @Test
        @DisplayName("token on second line")
        void secondLine() {
            List<PythonToken> tokens = new PythonLexer("\ny\n").tokenize();
            PythonToken nameToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("y"))
                    .findFirst().orElseThrow();
            assertThat(nameToken.line()).isEqualTo(2);
            assertThat(nameToken.column()).isEqualTo(1);
        }

        @Test
        @DisplayName("indented token has correct column")
        void indentedTokenColumn() {
            List<PythonToken> tokens = new PythonLexer("if True:\n    x = 1\n").tokenize();
            PythonToken nameToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("x"))
                    .findFirst().orElseThrow();
            assertThat(nameToken.line()).isEqualTo(2);
            assertThat(nameToken.column()).isEqualTo(5);
        }
    }

    // ==================================================================
    //  Comments
    // ==================================================================

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        @DisplayName("inline comment is skipped")
        void inlineComment() {
            List<PythonToken> tokens = new PythonLexer("x = 1 # comment\n").tokenize();
            assertThat(tokens).noneMatch(t -> t.text().contains("#"));
        }

        @Test
        @DisplayName("standalone comment line")
        void standaloneComment() {
            List<PythonToken> tokens = new PythonLexer("# standalone\n").tokenize();
            assertThat(tokens).hasSize(2);
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.NEWLINE);
            assertThat(tokens.get(1).type()).isEqualTo(PythonTokenType.EOF);
        }
    }

    // ==================================================================
    //  Line continuation
    // ==================================================================

    @Nested
    @DisplayName("line continuation")
    class LineContinuation {

        @Test
        @DisplayName("backslash-newline continues line")
        void backslashNewline() {
            List<PythonToken> tokens = new PythonLexer("x = 1 + \\\n2\n").tokenize();
            PythonToken plusToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.PLUS).findFirst().orElseThrow();
            assertThat(plusToken.line()).isEqualTo(1);
        }
    }

    // ==================================================================
    //  Parenthesis nesting
    // ==================================================================

    @Nested
    @DisplayName("parenthesis nesting")
    class ParenthesisNesting {

        @Test
        @DisplayName("newlines inside parentheses are ignored")
        void newlineInsideParens() {
            List<PythonToken> tokens = new PythonLexer("x = (\n1\n+\n2\n)\n").tokenize();
            long newlineCount = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.NEWLINE).count();
            assertThat(newlineCount).isEqualTo(1);
        }

        @Test
        @DisplayName("nested parentheses")
        void nestedParens() {
            List<PythonToken> tokens = new PythonLexer("((()))\n").tokenize();
            long lparCount = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.LEFT_PAREN).count();
            long rparCount = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.RIGHT_PAREN).count();
            assertThat(lparCount).isEqualTo(3);
            assertThat(rparCount).isEqualTo(3);
        }
    }

    // ==================================================================
    //  Complex expressions
    // ==================================================================

    @Nested
    @DisplayName("complex expressions")
    class ComplexExpressions {

        @Test
        @DisplayName("list comprehension")
        void listComprehension() {
            List<PythonToken> tokens = new PythonLexer("[x for x in range(10)]\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.LEFT_BRACKET);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.RIGHT_BRACKET);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.KW_FOR);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.KW_IN);
        }

        @Test
        @DisplayName("dictionary literal")
        void dictLiteral() {
            List<PythonToken> tokens = new PythonLexer("{'a': 1, 'b': 2}\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.LEFT_BRACE);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.RIGHT_BRACE);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.COLON);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.COMMA);
        }

        @Test
        @DisplayName("function call with keyword arguments")
        void functionCallKwargs() {
            List<PythonToken> tokens = new PythonLexer("func(a=1, b=2, *args, **kwargs)\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.LEFT_PAREN);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.RIGHT_PAREN);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.EQUAL);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.STAR);
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.DOUBLE_STAR);
        }
    }

    // ==================================================================
    //  Token position tracking
    // ==================================================================

    @Nested
    @DisplayName("token position tracking")
    class TokenPositionTracking {

        @Test
        @DisplayName("first token has correct line and column")
        void firstTokenPosition() {
            List<PythonToken> tokens = new PythonLexer("hello\n").tokenize();
            assertThat(tokens.get(0).line()).isEqualTo(1);
            assertThat(tokens.get(0).column()).isEqualTo(1);
        }

        @Test
        @DisplayName("second token has correct line and column")
        void secondTokenPosition() {
            List<PythonToken> tokens = new PythonLexer("hello world\n").tokenize();
            PythonToken nameToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("world"))
                    .findFirst().orElseThrow();
            assertThat(nameToken.line()).isEqualTo(1);
            assertThat(nameToken.column()).isEqualTo(7);
        }

        @Test
        @DisplayName("token on second line has correct line number")
        void secondLineToken() {
            List<PythonToken> tokens = new PythonLexer("x\ny\n").tokenize();
            PythonToken nameToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("y"))
                    .findFirst().orElseThrow();
            assertThat(nameToken.line()).isEqualTo(2);
            assertThat(nameToken.column()).isEqualTo(1);
        }

        @Test
        @DisplayName("indented token has correct column")
        void indentedTokenColumn() {
            List<PythonToken> tokens = new PythonLexer("if True:\n    x = 1\n").tokenize();
            PythonToken nameToken = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("x"))
                    .findFirst().orElseThrow();
            assertThat(nameToken.line()).isEqualTo(2);
            assertThat(nameToken.column()).isEqualTo(5);
        }
    }

    // ==================================================================
    //  Edge cases
    // ==================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("source with only comments")
        void onlyComments() {
            List<PythonToken> tokens = new PythonLexer("# comment 1\n# comment 2\n").tokenize();
            assertThat(tokens).allMatch(t ->
                    t.type() == PythonTokenType.NEWLINE || t.type() == PythonTokenType.EOF);
        }

        @Test
        @DisplayName("multiple consecutive newlines")
        void multipleNewlines() {
            List<PythonToken> tokens = new PythonLexer("\n\n\n").tokenize();
            long newlineCount = tokens.stream()
                    .filter(t -> t.type() == PythonTokenType.NEWLINE).count();
            assertThat(newlineCount).isEqualTo(3);
        }

        @Test
        @DisplayName("Windows-style line endings (CRLF)")
        void crlfLineEndings() {
            List<PythonToken> tokens = new PythonLexer("x\r\ny\r\n").tokenize();
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("x"));
            assertThat(tokens).anyMatch(t -> t.type() == PythonTokenType.IDENTIFIER && t.text().equals("y"));
        }

        @Test
        @DisplayName("very long identifier")
        void veryLongIdentifier() {
            String longName = "a".repeat(1000);
            List<PythonToken> tokens = new PythonLexer(longName + "\n").tokenize();
            assertThat(tokens.get(0).type()).isEqualTo(PythonTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).hasSize(1000);
        }
    }
}
