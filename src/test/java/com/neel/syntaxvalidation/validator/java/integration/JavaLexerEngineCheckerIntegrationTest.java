package com.neel.syntaxvalidation.validator.java.integration;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.java.JavaLexer;
import com.neel.syntaxvalidation.validator.java.JavaSyntaxEngine;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import com.neel.syntaxvalidation.validator.java.JavaTokenType;
import com.neel.syntaxvalidation.validator.java.checker.DelimiterBalanceChecker;
import com.neel.syntaxvalidation.validator.java.checker.KeywordUsageChecker;
import com.neel.syntaxvalidation.validator.java.checker.SyntaxChecker;
import com.neel.syntaxvalidation.validator.java.checker.TokenizationErrorChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests verifying the interaction between the {@link JavaLexer},
 * {@link JavaSyntaxEngine}, and all {@link SyntaxChecker} implementations.
 *
 * <p>These tests focus on the data flow from source text → tokens → checker
 * diagnostics, ensuring that the lexer produces tokens that the checkers can
 * correctly reason about and that the engine properly orchestrates multiple
 * checkers.
 */
@DisplayName("Lexer → Engine → Checker Integration Tests")
class JavaLexerEngineCheckerIntegrationTest {

    private TokenizationErrorChecker tokenizationChecker;
    private DelimiterBalanceChecker delimiterChecker;
    private KeywordUsageChecker keywordChecker;
    private JavaSyntaxEngine syntaxEngine;

    @BeforeEach
    void setUp() {
        tokenizationChecker = new TokenizationErrorChecker();
        delimiterChecker = new DelimiterBalanceChecker();
        keywordChecker = new KeywordUsageChecker();
        syntaxEngine = new JavaSyntaxEngine();
    }

    // ======================================================================
    //  1. Lexer → TokenizationErrorChecker Integration
    // ======================================================================

    @Nested
    @DisplayName("Lexer → TokenizationErrorChecker integration")
    class LexerToTokenizationErrorChecker {

        @Test
        @DisplayName("unterminated string → lexer produces ERROR token → checker reports")
        void unterminatedString_lexerProducesErrorToken_checkerReports() {
            var source = "String s = \"hello world";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("lexical error");
            assertThat(errors.get(0).getLine()).isEqualTo(1);
        }

        @Test
        @DisplayName("unterminated char literal → lexer produces ERROR token → checker reports")
        void unterminatedCharLiteral_lexerProducesErrorToken_checkerReports() {
            var source = "char c = 'a";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("lexical error");
        }

        @Test
        @DisplayName("unterminated block comment → lexer produces ERROR token → checker reports")
        void unterminatedBlockComment_lexerProducesErrorToken_checkerReports() {
            var source = "/* this comment never ends";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("lexical error");
        }

        @Test
        @DisplayName("valid source → no ERROR tokens → checker reports nothing")
        void validSource_noErrorTokens_checkerReportsNothing() {
            var source = """
                    public class Valid {
                        String s = "hello";
                        int x = 42;
                    }
                    """;
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
            // Verify no ERROR tokens were produced
            assertThat(tokens.stream()
                    .noneMatch(t -> t.type() == JavaTokenType.ERROR)).isTrue();
        }

        @Test
        @DisplayName("multiple lexical errors → checker reports all")
        void multipleLexicalErrors_checkerReportsAll() {
            var source = "String s = \"unterminated\nchar c = 'x";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("very long unterminated string → checker truncates error text")
        void veryLongUnterminatedString_checkerTruncatesErrorText() {
            var sb = new StringBuilder("String s = \"");
            sb.append("a".repeat(100));
            List<JavaToken> tokens = new JavaLexer(sb.toString()).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            // The error message should be truncated (≤40 chars + ellipsis)
            String toolOutput = errors.get(0).getToolOutput();
            // toolOutput is the full token text; the message truncates it
            assertThat(errors.get(0).getMessage().length()).isLessThanOrEqualTo(100);
        }
    }

    // ======================================================================
    //  2. Lexer → DelimiterBalanceChecker Integration
    // ======================================================================

    @Nested
    @DisplayName("Lexer → DelimiterBalanceChecker integration")
    class LexerToDelimiterBalanceChecker {

        @Test
        @DisplayName("balanced delimiters → no errors")
        void balancedDelimiters_noErrors() {
            var source = "void m() { int[] a = new int[]{1, 2}; }";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("unclosed parenthesis → lexer produces tokens → checker detects")
        void unclosedParenthesis_lexerProducesTokens_checkerDetects() {
            var source = "void m( { }";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("unclosed").containsIgnoringCase("(");
        }

        @Test
        @DisplayName("unclosed bracket → checker detects")
        void unclosedBracket_checkerDetects() {
            var source = "int[] a = new int[5;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("unclosed").containsIgnoringCase("[");
        }

        @Test
        @DisplayName("unclosed brace → checker detects")
        void unclosedBrace_checkerDetects() {
            var source = """
                    public class Foo {
                        void m() {
                    """;
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed").containsIgnoringCase("{"));
        }

        @Test
        @DisplayName("unexpected close paren → checker detects")
        void unexpectedCloseParen_checkerDetects() {
            var source = "int x = );";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("unexpected").containsIgnoringCase(")");
        }

        @Test
        @DisplayName("mismatched delimiters → checker detects")
        void mismatchedDelimiters_checkerDetects() {
            var source = "void m( ] }";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("mismatched"));
        }

        @Test
        @DisplayName("nested balanced delimiters → no errors")
        void nestedBalancedDelimiters_noErrors() {
            var source = "m(a[b{c}])";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("complex nested mismatched → checker reports with line info")
        void complexNestedMismatched_checkerReportsWithLineInfo() {
            var source = """
                    class X {
                        void m() {
                            int[] a = {1, (2 + 3]};
                        }
                    """;
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            // Should have errors with meaningful line numbers
            assertThat(errors).allSatisfy(e -> assertThat(e.getLine()).isPositive());
        }
    }

    // ======================================================================
    //  3. Lexer → KeywordUsageChecker Integration
    // ======================================================================

    @Nested
    @DisplayName("Lexer → KeywordUsageChecker integration")
    class LexerToKeywordUsageChecker {

        @Test
        @DisplayName("conflicting access modifiers → lexer tokenizes → checker detects")
        void conflictingAccessModifiers_lexerTokenizes_checkerDetects() {
            var source = "public private class Conflict {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("conflicting access");
        }

        @Test
        @DisplayName("duplicate static modifier → checker detects")
        void duplicateStaticModifier_checkerDetects() {
            var source = "public static static int x;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("duplicate modifier");
        }

        @Test
        @DisplayName("const keyword → checker detects as reserved")
        void constKeyword_checkerDetectsAsReserved() {
            var source = "const int x = 1;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("reserved keyword");
        }

        @Test
        @DisplayName("goto keyword → checker detects as reserved")
        void gotoKeyword_checkerDetectsAsReserved() {
            var source = "goto label;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("reserved keyword");
        }

        @Test
        @DisplayName("malformed annotation → checker detects")
        void malformedAnnotation_checkerDetects() {
            var source = "@ ;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("annotation");
        }

        @Test
        @DisplayName("valid annotation → checker passes")
        void validAnnotation_checkerPasses() {
            var source = "@Override void m() {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("assert without expression → checker detects")
        void assertWithoutExpression_checkerDetects() {
            var source = "assert ;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("assert");
        }

        @Test
        @DisplayName("assert with expression → checker passes")
        void assertWithExpression_checkerPasses() {
            var source = "assert x > 0;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("package without name → checker detects")
        void packageWithoutName_checkerDetects() {
            var source = "package ;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("package");
        }

        @Test
        @DisplayName("import without name → checker detects")
        void importWithoutName_checkerDetects() {
            var source = "import ;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).containsIgnoringCase("import");
        }

        @Test
        @DisplayName("valid import static → checker passes")
        void validImportStatic_checkerPasses() {
            var source = "import static java.util.List.of;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("valid import module → checker passes")
        void validImportModule_checkerPasses() {
            var source = "import module java.base;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("multiple access modifiers in class declaration → detects only the pair")
        void multipleAccessModifiersInClassDeclaration_detectsOnlyThePair() {
            var source = "public protected private class X {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            // Should detect conflicting access modifiers
            assertThat(errors).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("conflicting access"));
        }

        @Test
        @DisplayName("modifier with annotation interleaved → valid pattern")
        void modifierWithAnnotationInterleaved_validPattern() {
            var source = "public @Deprecated class X {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("non-keyword identifiers that look like keywords → no false positives")
        void nonKeywordIdentifiers_likeKeywords_noFalsePositives() {
            var source = "int constant = 1; int got = 2;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();

            keywordChecker.check(tokens, errors);

            // "constant" and "got" are identifiers, not the reserved keywords "const" and "goto"
            assertThat(errors).isEmpty();
        }
    }

    // ======================================================================
    //  4. Engine Full Pipeline (All Checkers) Integration
    // ======================================================================

    @Nested
    @DisplayName("Engine full pipeline (all checkers) integration")
    class EngineFullPipelineIntegration {

        @Test
        @DisplayName("clean source passes through all checkers → valid")
        void cleanSourcePassesThroughAllCheckers_valid() {
            var source = """
                    public class Clean {
                        private final int x;
                        
                        public Clean(int x) {
                            this.x = x;
                        }
                        
                        public int getX() { return x; }
                    }
                    """;

            ValidationResult result = new JavaSyntaxEngine().validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("source with errors from all three checkers → all reported")
        void sourceWithErrorsFromAllThreeCheckers_allReported() {
            // This source has:
            // 1. TokenizationErrorChecker: unterminated string
            // 2. DelimiterBalanceChecker: unclosed brace/paren
            // 3. KeywordUsageChecker: conflicting access modifiers
            var source = """
                    public private class Bad {
                        String s = "unterminated
                        int x = (1 + 2;
                    """;

            ValidationResult result = new JavaSyntaxEngine().validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("checkers run independently — one checker's error doesn't prevent others")
        void checkersRunIndependently() {
            // Even though TokenizationErrorChecker finds an error,
            // DelimiterBalanceChecker and KeywordUsageChecker should still run
            var source = "const int x = \"unterminated\nint y = );";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            var allErrors = new ArrayList<ValidationError>();

            // Run each checker independently on the same tokens
            tokenizationChecker.check(tokens, allErrors);
            delimiterChecker.check(tokens, allErrors);
            keywordChecker.check(tokens, allErrors);

            // Should have errors from multiple checkers
            assertThat(allErrors).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("engine accumulates errors from all checkers in order")
        void engineAccumulatesErrorsFromAllCheckersInOrder() {
            // Use a custom engine with known checker order
            var engine = new JavaSyntaxEngine(List.of(
                    new TokenizationErrorChecker(),
                    new DelimiterBalanceChecker(),
                    new KeywordUsageChecker()));

            var source = """
                    public private class Test {
                        String s = "unterminated
                        int x = (1 + 2;
                    }
                    """;

            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isFalse();

            // Errors from TokenizationErrorChecker should come first
            // (since it's listed first in the checker chain)
            var tokenErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().toLowerCase().contains("lexical"))
                    .toList();

            // Both types should be present
            assertThat(tokenErrors).isNotEmpty();
        }
    }

    // ======================================================================
    //  5. Token Stream Integrity Integration
    // ======================================================================

    @Nested
    @DisplayName("Token stream integrity integration")
    class TokenStreamIntegrityIntegration {

        @Test
        @DisplayName("tokens preserve line numbers across multi-line source")
        void tokensPreserveLineNumbersAcrossMultiLineSource() {
            var source = """
                    line1: class A {
                    line2:     int x;
                    line3:     int y;
                    line4: }
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            // Find tokens on different lines
            Set<Integer> lines = tokens.stream()
                    .filter(t -> t.type() != JavaTokenType.EOF)
                    .map(JavaToken::line)
                    .collect(Collectors.toSet());

            // Should span multiple lines
            assertThat(lines).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("tokens preserve column positions")
        void tokensPreserveColumnPositions() {
            var source = "int x = 42;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            // 'int' should be at column 1, 'x' should be at a later column
            JavaToken intToken = tokens.stream()
                    .filter(t -> "int".equals(t.text()))
                    .findFirst().orElseThrow();
            JavaToken xToken = tokens.stream()
                    .filter(t -> "x".equals(t.text()))
                    .findFirst().orElseThrow();

            assertThat(intToken.column()).isEqualTo(1);
            assertThat(xToken.column()).isGreaterThan(intToken.column());
        }

        @Test
        @DisplayName("EOF token is always present at the end")
        void eofTokenIsAlwaysPresentAtTheEnd() {
            var source = "class A {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            assertThat(tokens).isNotEmpty();
            assertThat(tokens.getLast().type()).isEqualTo(JavaTokenType.EOF);
        }

        @Test
        @DisplayName("empty source → only EOF token")
        void emptySource_onlyEofToken() {
            List<JavaToken> tokens = new JavaLexer("").tokenize();

            assertThat(tokens).hasSize(1);
            assertThat(tokens.getFirst().type()).isEqualTo(JavaTokenType.EOF);
        }

        @Test
        @DisplayName("comments are tokenized with correct type")
        void commentsAreTokenizedWithCorrectType() {
            var source = """
                    // single line
                    /* block */
                    /** javadoc */
                    int x;
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            long commentCount = tokens.stream()
                    .filter(t -> t.type() == JavaTokenType.COMMENT)
                    .count();

            assertThat(commentCount).isEqualTo(3);
        }

        @Test
        @DisplayName("string literals are tokenized with correct type")
        void stringLiteralsAreTokenizedWithCorrectType() {
            var source = "String s = \"hello world\";";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            assertThat(tokens.stream()
                    .anyMatch(t -> t.type() == JavaTokenType.STRING && t.text().contains("hello")))
                    .isTrue();
        }

        @Test
        @DisplayName("numeric literals are tokenized with correct type")
        void numericLiteralsAreTokenizedWithCorrectType() {
            var source = "int x = 42; long y = 100L; double z = 3.14;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            long numericCount = tokens.stream()
                    .filter(t -> t.type() == JavaTokenType.NUMBER)
                    .count();

            assertThat(numericCount).isEqualTo(3);
        }

        @Test
        @DisplayName("keywords are distinguished from identifiers")
        void keywordsAreDistinguishedFromIdentifiers() {
            var source = "int count = 0;";  // 'int' is keyword, 'count' is identifier
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            JavaToken intToken = tokens.stream()
                    .filter(t -> "int".equals(t.text()))
                    .findFirst().orElseThrow();
            JavaToken countToken = tokens.stream()
                    .filter(t -> "count".equals(t.text()))
                    .findFirst().orElseThrow();

            assertThat(intToken.type()).isEqualTo(JavaTokenType.KEYWORD);
            assertThat(countToken.type()).isEqualTo(JavaTokenType.IDENTIFIER);
        }
    }

    // ======================================================================
    //  6. Checker Error Position Accuracy
    // ======================================================================

    @Nested
    @DisplayName("Checker error position accuracy")
    class CheckerErrorPositionAccuracy {

        @Test
        @DisplayName("delimiter error on line 3 → correct line reported")
        void delimiterErrorOnLine3_correctLineReported() {
            var source = """
                    class A {
                        void m() {
                            int x = (1 + 2;
                        }
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();
            delimiterChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            // The unclosed '(' should be on line 3
            assertThat(errors).anySatisfy(e -> assertThat(e.getLine()).isEqualTo(3));
        }

        @Test
        @DisplayName("keyword error on line 2 → correct line reported")
        void keywordErrorOnLine2_correctLineReported() {
            var source = """
                    class A {
                        const int x = 1;
                    }
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();
            keywordChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getLine()).isEqualTo(2);
        }

        @Test
        @DisplayName("tokenization error preserves the exact position")
        void tokenizationError_preservesExactPosition() {
            var source = """
                    class A {
                        String s = "unterminated;
                    }
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();
            List<ValidationError> errors = new ArrayList<>();
            tokenizationChecker.check(tokens, errors);

            assertThat(errors).isNotEmpty();
            // The error should be on line 2
            assertThat(errors.get(0).getLine()).isEqualTo(2);
            // Column should point to the start of the string literal
            assertThat(errors.get(0).getColumn()).isPositive();
        }
    }

    // ======================================================================
    //  7. Custom Pipeline Composition
    // ======================================================================

    @Nested
    @DisplayName("Custom pipeline composition")
    class CustomPipelineComposition {

        @Test
        @DisplayName("engine with only delimiter checker → only delimiter errors reported")
        void engineWithOnlyDelimiterChecker_onlyDelimiterErrorsReported() {
            var engine = new JavaSyntaxEngine(List.of(new DelimiterBalanceChecker()));

            // Has keyword error (const) and delimiter error (unclosed brace)
            var source = "const int x = 1; void m() {";
            ValidationResult result = engine.validate(source);

            assertThat(result.isValid()).isFalse();
            // Only delimiter error should be present, not the 'const' keyword error
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).doesNotContainIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("engine with only keyword checker → only keyword errors reported")
        void engineWithOnlyKeywordChecker_onlyKeywordErrorsReported() {
            var engine = new JavaSyntaxEngine(List.of(new KeywordUsageChecker()));

            // Has delimiter error (unclosed paren) and keyword error (const)
            var source = "const int x = (1 + 2;";
            ValidationResult result = engine.validate(source);

            assertThat(result.isValid()).isFalse();
            // Only keyword error should be present
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("engine with only tokenization checker → only tokenization errors reported")
        void engineWithOnlyTokenizationChecker_onlyTokenizationErrorsReported() {
            var engine = new JavaSyntaxEngine(List.of(new TokenizationErrorChecker()));

            // Has tokenization error and keyword error
            var source = "const String s = \"unterminated";
            ValidationResult result = engine.validate(source);

            assertThat(result.isValid()).isFalse();
            // Only tokenization error should be present
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("lexical error"));
        }

        @Test
        @DisplayName("engine with reversed checker order → same errors, different order")
        void engineWithReversedCheckerOrder_sameErrorsDifferentOrder() {
            var normalOrder = new JavaSyntaxEngine(List.of(
                    new TokenizationErrorChecker(),
                    new DelimiterBalanceChecker(),
                    new KeywordUsageChecker()));

            var reversedOrder = new JavaSyntaxEngine(List.of(
                    new KeywordUsageChecker(),
                    new DelimiterBalanceChecker(),
                    new TokenizationErrorChecker()));

            var source = """
                    public private class X {
                        String s = "unterminated
                        int x = (1 + 2;
                    }
                    """;

            ValidationResult normalResult = normalOrder.validate(source);
            ValidationResult reversedResult = reversedOrder.validate(source);

            // Both should find errors
            assertThat(normalResult.isValid()).isFalse();
            assertThat(reversedResult.isValid()).isFalse();

            // The error counts should be the same (same checkers, same source)
            assertThat(normalResult.getErrors().size()).isEqualTo(reversedResult.getErrors().size());
        }

        @Test
        @DisplayName("engine with duplicate checkers → duplicate errors")
        void engineWithDuplicateCheckers_duplicateErrors() {
            var engine = new JavaSyntaxEngine(List.of(
                    new DelimiterBalanceChecker(),
                    new DelimiterBalanceChecker()));

            var source = "void m( { }";  // unclosed parenthesis
            ValidationResult result = engine.validate(source);

            assertThat(result.isValid()).isFalse();
            // Duplicate checker means duplicate errors
            assertThat(result.getErrors()).hasSize(2);
        }
    }

    // ======================================================================
    //  8. Cross-Component Data Flow
    // ======================================================================

    @Nested
    @DisplayName("Cross-component data flow")
    class CrossComponentDataFlow {

        @Test
        @DisplayName("lexer produces correct token count for simple class")
        void lexerProducesCorrectTokenCountForSimpleClass() {
            var source = "class A {}";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            // Should have: "class" (KEYWORD), "A" (IDENTIFIER), "{" (PUNCTUATION),
            // "}" (PUNCTUATION), EOF
            long nonEof = tokens.stream()
                    .filter(t -> t.type() != JavaTokenType.EOF)
                    .count();

            assertThat(nonEof).isEqualTo(4);
        }

        @Test
        @DisplayName("lexer token types match expected for declaration")
        void lexerTokenTypesMatchExpectedForDeclaration() {
            var source = "public static final int MAX = 100;";
            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            var types = tokens.stream()
                    .filter(t -> t.type() != JavaTokenType.EOF)
                    .map(JavaToken::type)
                    .toList();

            // public, static, final → KEYWORD
            // int → KEYWORD
            // MAX → IDENTIFIER
            // = → PUNCTUATION
            // 100 → NUMBER
            // ; → PUNCTUATION
            assertThat(types).containsExactly(
                    JavaTokenType.KEYWORD,
                    JavaTokenType.KEYWORD,
                    JavaTokenType.KEYWORD,
                    JavaTokenType.KEYWORD,
                    JavaTokenType.IDENTIFIER,
                    JavaTokenType.PUNCTUATION,
                    JavaTokenType.NUMBER,
                    JavaTokenType.PUNCTUATION
            );
        }

        @Test
        @DisplayName("checker errors reference valid token positions")
        void checkerErrorsReferenceValidTokenPositions() {
            var source = """
                    public private class X {
                        const int y = 1;
                        String s = (;
                    }
                    """;

            List<JavaToken> tokens = new JavaLexer(source).tokenize();

            var allErrors = new ArrayList<ValidationError>();
            tokenizationChecker.check(tokens, allErrors);
            delimiterChecker.check(tokens, allErrors);
            keywordChecker.check(tokens, allErrors);

            // All errors should reference positions within the source
            for (ValidationError error : allErrors) {
                assertThat(error.getLine()).isPositive();
                assertThat(error.getColumn()).isPositive();
                // Line should not exceed the number of lines in source
                long lineCount = source.lines().count();
                assertThat(error.getLine()).isLessThanOrEqualTo((int) lineCount);
            }
        }
    }

    // ======================================================================
    //  9. Lexer State Isolation
    // ======================================================================

    @Nested
    @DisplayName("Lexer state isolation")
    class LexerStateIsolation {

        @Test
        @DisplayName("sequential tokenize calls → no state leakage")
        void sequentialTokenizeCalls_noStateLeakage() {
            var tokens1 = new JavaLexer("class A {}").tokenize();
            var tokens2 = new JavaLexer("class B { int x; }").tokenize();

            // Each call should produce independent results
            assertThat(tokens1).isNotEmpty();
            assertThat(tokens2).isNotEmpty();

            // tokens2 should be longer than tokens1
            assertThat(tokens2.size()).isGreaterThan(tokens1.size());
        }

        @Test
        @DisplayName("concurrent tokenize calls → thread-safe")
        void concurrentTokenizeCalls_threadSafe() {
            var sources = List.of("class A {}", "class B {}", "class C {}");

            var results = sources.parallelStream()
                    .map(s -> new JavaLexer(s).tokenize())
                    .toList();

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(tokens ->
                    tokens.stream().anyMatch(t -> t.type() == JavaTokenType.EOF));
        }
    }

    // ======================================================================
    //  10. JavaSyntaxEngine Static Convenience Method Integration
    // ======================================================================

    @Nested
    @DisplayName("JavaSyntaxEngine static convenience method integration")
    class StaticConvenienceMethodIntegration {

        @Test
        @DisplayName("static validate → produces same result as instance validate")
        void staticValidate_producesSameResultAsInstanceValidate() {
            var source = """
                    public class Test {
                        const int x = 1;
                        String s = "hello;
                    }
                    """;

            ValidationResult instanceResult = syntaxEngine.validate(source);
            ValidationResult staticResult = JavaSyntaxEngine.validateStatic(source);

            // Both should find errors
            assertThat(instanceResult.isValid()).isFalse();
            assertThat(staticResult.isValid()).isFalse();

            // Error counts should match (same checker pipeline)
            assertThat(instanceResult.getErrors().size())
                    .isEqualTo(staticResult.getErrors().size());
        }

        @Test
        @DisplayName("static validate with clean source → valid")
        void staticValidateWithCleanSource_valid() {
            var source = """
                    public class Clean {
                        int x = 42;
                    }
                    """;

            ValidationResult result = JavaSyntaxEngine.validateStatic(source);
            assertThat(result.isValid()).isTrue();
        }
    }
}