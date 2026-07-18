package com.neel.syntaxvalidation.validator.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verbose, exhaustive unit tests for {@link JavaLexer}.
 *
 * <p>Each lexical category is exercised with both valid and invalid inputs,
 * including edge cases for Java&nbsp;21–25 syntax (text blocks, records, sealed
 * types, switch expressions, lambdas and modern numeric literals).
 */
@DisplayName("JavaLexer")
class JavaLexerTest {

    private List<JavaToken> lex(String src) {
        return new JavaLexer(src).tokenize();
    }

    private List<JavaToken> lexNoEof(String src) {
        List<JavaToken> tokens = lex(src);
        // Strip trailing EOF for convenience in assertions.
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).type() == JavaTokenType.EOF) {
            return tokens.subList(0, tokens.size() - 1);
        }
        return tokens;
    }

    // ==================================================================
    //  Keywords & identifiers
    // ==================================================================

    @Nested
    @DisplayName("keywords and identifiers")
    class KeywordsAndIdentifiers {

        @Test
        void recognisesClassKeyword() {
            List<JavaToken> tokens = lexNoEof("class");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(tokens.get(0).text()).isEqualTo("class");
        }

        @Test
        void recognisesReservedLiteralsAsKeywords() {
            assertThat(lexNoEof("true").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("false").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("null").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
        }

        @Test
        void recognisesContextualKeywords() {
            assertThat(lexNoEof("var").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("yield").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("record").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("sealed").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("permits").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
        }

        @Test
        void recognisesUnderscoreAsKeyword() {
            assertThat(lexNoEof("_").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
        }

        @Test
        void recognisesModuleDirectives() {
            assertThat(lexNoEof("module").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("requires").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("exports").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(lexNoEof("transitive").get(0).type()).isEqualTo(JavaTokenType.KEYWORD);
        }

        @Test
        void simpleIdentifierIsNotKeyword() {
            List<JavaToken> tokens = lexNoEof("myVar");
            assertThat(tokens.get(0).type()).isEqualTo(JavaTokenType.IDENTIFIER);
            assertThat(tokens.get(0).text()).isEqualTo("myVar");
        }

        @Test
        void identifierMayStartWithDollarOrUnderscorePair() {
            assertThat(lexNoEof("$value").get(0).type()).isEqualTo(JavaTokenType.IDENTIFIER);
            assertThat(lexNoEof("_value").get(0).type()).isEqualTo(JavaTokenType.IDENTIFIER);
        }

        @Test
        void identifierWithDigits() {
            assertThat(lexNoEof("var123").get(0).type()).isEqualTo(JavaTokenType.IDENTIFIER);
        }

        @Test
        void unicodeIdentifier() {
            assertThat(lexNoEof("café").get(0).text()).isEqualTo("café");
        }
    }

    // ==================================================================
    //  Numbers
    // ==================================================================

    @Nested
    @DisplayName("numeric literals")
    class NumericLiterals {

        @ParameterizedTest
        @ValueSource(strings = {"0", "42", "1_000_000", "255L", "255l"})
        void decimalIntegers(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.NUMBER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0x1A", "0xFF", "0xDEAD_BEEF", "0x10L", "0Xff"})
        void hexLiterals(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.NUMBER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0b1010", "0b1111_0000", "0b1L"})
        void binaryLiterals(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.NUMBER);
        }

        @ParameterizedTest
        @ValueSource(strings = {"3.14", "3.14f", "3.14F", "3.14d", "3.14D", ".5", "1e10", "1E-10", "1.5e+3"})
        void floatingPointLiterals(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.NUMBER);
        }

        @Test
        void hexFloat() {
            assertThat(lexNoEof("0x1.0p10").get(0).type()).isEqualTo(JavaTokenType.NUMBER);
        }

        @Test
        void unterminatedHexDigitsIsError() {
            // 0x with no hex digits is illegal
            List<JavaToken> tokens = lexNoEof("0x ");
            assertThat(tokens.get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void unterminatedBinaryDigitsIsError() {
            assertThat(lexNoEof("0b ").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void numberWithPositionInfo() {
            List<JavaToken> tokens = lex("  42");
            JavaToken num = tokens.get(0);
            assertThat(num.type()).isEqualTo(JavaTokenType.NUMBER);
            assertThat(num.text()).isEqualTo("42");
            assertThat(num.line()).isEqualTo(1);
            assertThat(num.column()).isEqualTo(3);
        }
    }

    // ==================================================================
    //  Strings & characters
    // ==================================================================

    @Nested
    @DisplayName("string, char and text-block literals")
    class StringLiterals {

        @Test
        void simpleString() {
            assertThat(lexNoEof("\"hello\"").get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void emptyString() {
            assertThat(lexNoEof("\"\"").get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void stringWithEscapes() {
            assertThat(lexNoEof("\"\\n\\t\\\\\\\"\"").get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void stringWithUnicodeEscape() {
            assertThat(lexNoEof("\"\\u0041\"").get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void unterminatedStringIsError() {
            assertThat(lexNoEof("\"unterminated").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void newlineInStringIsError() {
            assertThat(lexNoEof("\"line\nbreak\"").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void textBlock() {
            String src = "\"\"\"\nhello\n\"\"\"";
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void textBlockWithEscapes() {
            String src = "\"\"\"\n\\n \\t \\\"\n\"\"\"";
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.STRING);
        }

        @Test
        void unterminatedTextBlockIsError() {
            assertThat(lexNoEof("\"\"\"\nnever ends").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void simpleCharLiteral() {
            assertThat(lexNoEof("'a'").get(0).type()).isEqualTo(JavaTokenType.CHAR);
        }

        @Test
        void escapedCharLiteral() {
            assertThat(lexNoEof("'\\n'").get(0).type()).isEqualTo(JavaTokenType.CHAR);
        }

        @Test
        void unicodeCharLiteral() {
            assertThat(lexNoEof("'\\u0041'").get(0).type()).isEqualTo(JavaTokenType.CHAR);
        }

        @Test
        void unterminatedCharLiteralIsError() {
            assertThat(lexNoEof("'a").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void newlineInCharLiteralIsError() {
            assertThat(lexNoEof("'\n'").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }
    }

    // ==================================================================
    //  Comments
    // ==================================================================

    @Nested
    @DisplayName("comments")
    class Comments {

        @Test
        void lineComment() {
            assertThat(lexNoEof("// hello").get(0).type()).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        void lineCommentToEndOfLine() {
            List<JavaToken> tokens = lexNoEof("int x; // comment\n");
            assertThat(tokens.get(tokens.size() - 1).type()).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        void blockComment() {
            assertThat(lexNoEof("/* hello */").get(0).type()).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        void multilineBlockComment() {
            String src = "/* line 1\n line 2 */";
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        void javadocComment() {
            assertThat(lexNoEof("/** doc */").get(0).type()).isEqualTo(JavaTokenType.COMMENT);
        }

        @Test
        void unterminatedBlockCommentIsError() {
            assertThat(lexNoEof("/* never ends").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }
    }

    // ==================================================================
    //  Punctuation / operators
    // ==================================================================

    @Nested
    @DisplayName("operators and separators")
    class Punctuation {

        @ParameterizedTest
        @ValueSource(strings = {"->", "::", "...", "++", "--", "==", "!=", "<=", ">=",
                "&&", "||", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>"})
        void multiCharOperators(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.PUNCTUATION);
            assertThat(lexNoEof(src).get(0).text()).isEqualTo(src);
        }

        @Test
        void shiftAssignment() {
            assertThat(lexNoEof(">>=").get(0).text()).isEqualTo(">>=");
            assertThat(lexNoEof("<<=").get(0).text()).isEqualTo("<<=");
        }

        @ParameterizedTest
        @ValueSource(strings = {"{", "}", "(", ")", "[", "]", ";", ",", ".", "+", "-",
                "*", "/", "%", "<", ">", "&", "|", "^", "~", "!", "=", "?", ":", "@"})
        void singleCharPunctuation(String src) {
            assertThat(lexNoEof(src).get(0).type()).isEqualTo(JavaTokenType.PUNCTUATION);
        }

        @Test
        void illegalCharacterIsError() {
            assertThat(lexNoEof("`").get(0).type()).isEqualTo(JavaTokenType.ERROR);
        }

        @Test
        void annotationAtSign() {
            assertThat(lexNoEof("@").get(0).type()).isEqualTo(JavaTokenType.PUNCTUATION);
            assertThat(lexNoEof("@").get(0).text()).isEqualTo("@");
        }
    }

    // ==================================================================
    //  Composite programs
    // ==================================================================

    @Nested
    @DisplayName("composite program tokenisation")
    class CompositePrograms {

        @Test
        void simpleClass() {
            List<JavaToken> tokens = lexNoEof("public class Foo { }");
            assertThat(tokens).hasSize(5);
            assertThat(tokens).extracting(JavaToken::type)
                    .containsExactly(JavaTokenType.KEYWORD, JavaTokenType.KEYWORD,
                            JavaTokenType.IDENTIFIER, JavaTokenType.PUNCTUATION,
                            JavaTokenType.PUNCTUATION);
        }

        @Test
        void recordDeclaration() {
            List<JavaToken> tokens = lexNoEof("public record Point(int x, int y) {}");
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void switchExpression() {
            String src = "var r = switch (day) { case MON -> \"M\"; default -> \"?\"; };";
            List<JavaToken> tokens = lexNoEof(src);
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void lambdaExpression() {
            List<JavaToken> tokens = lexNoEof("(x) -> x * 2");
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void sealedClassWithPermits() {
            String src = "public sealed class Shape permits Circle, Square {}";
            List<JavaToken> tokens = lexNoEof(src);
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void textBlockAssignment() {
            String src = "String json = \"\"\"\n  { \"key\": 1 }\n  \"\"\";";
            List<JavaToken> tokens = lexNoEof(src);
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void importModuleDeclaration() {
            List<JavaToken> tokens = lexNoEof("import module java.base;");
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void compactMainMethod() {
            List<JavaToken> tokens = lexNoEof("void main() { System.out.println(\"hi\"); }");
            assertThat(tokens).extracting(JavaToken::type).doesNotContain(JavaTokenType.ERROR);
        }

        @Test
        void alwaysEmitsEof() {
            List<JavaToken> tokens = lex("int x;");
            assertThat(tokens.get(tokens.size() - 1).type()).isEqualTo(JavaTokenType.EOF);
        }

        @Test
        void emptySourceEmitsOnlyEof() {
            List<JavaToken> tokens = lex("");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(JavaTokenType.EOF);
        }

        @Test
        void whitespaceOnlyEmitsOnlyEof() {
            List<JavaToken> tokens = lex("  \n\t\r\n  ");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).type()).isEqualTo(JavaTokenType.EOF);
        }
    }

    // ==================================================================
    //  Position tracking
    // ==================================================================

    @Nested
    @DisplayName("position tracking")
    class PositionTracking {

        @Test
        void tracksLineAndColumn() {
            List<JavaToken> tokens = lexNoEof("int x;\nint y;");
            assertThat(tokens.get(0).line()).isEqualTo(1);
            assertThat(tokens.get(0).column()).isEqualTo(1);
            // 'int' on line 2
            JavaToken line2Int = tokens.stream().filter(t -> "int".equals(t.text()) && t.line() == 2).findFirst().orElseThrow();
            assertThat(line2Int.line()).isEqualTo(2);
            assertThat(line2Int.column()).isEqualTo(1);
        }

        @Test
        void tracksColumnThroughTokens() {
            List<JavaToken> tokens = lexNoEof("ab cd");
            assertThat(tokens.get(0).text()).isEqualTo("ab");
            assertThat(tokens.get(0).column()).isEqualTo(1);
            assertThat(tokens.get(1).text()).isEqualTo("cd");
            assertThat(tokens.get(1).column()).isEqualTo(4);
        }
    }
}
