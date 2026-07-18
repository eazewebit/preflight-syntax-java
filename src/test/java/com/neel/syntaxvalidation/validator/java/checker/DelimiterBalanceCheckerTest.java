package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaLexer;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link DelimiterBalanceChecker}.
 */
@DisplayName("DelimiterBalanceChecker")
class DelimiterBalanceCheckerTest {

    private final DelimiterBalanceChecker checker = new DelimiterBalanceChecker();

    private List<ValidationError> check(String src) {
        List<JavaToken> tokens = new JavaLexer(src).tokenize();
        List<ValidationError> errors = new ArrayList<>();
        checker.check(tokens, errors);
        return errors;
    }

    @Nested
    @DisplayName("valid nesting")
    class ValidNesting {

        @Test
        void emptyInput() {
            assertThat(check("")).isEmpty();
        }

        @Test
        void balancedParens() {
            assertThat(check("method(a, b)")).isEmpty();
        }

        @Test
        void nestedBalanced() {
            assertThat(check("f(g(h()))")).isEmpty();
        }

        @Test
        void balancedAllTypes() {
            assertThat(check("class C { void m(int[] a) { a[0] = (1); } }")).isEmpty();
        }

        @Test
        void emptyGroups() {
            assertThat(check("()[]{}")).isEmpty();
        }

        @Test
        void deeplyNested() {
            assertThat(check("{{{{ }}}};")).isEmpty();
        }

        @Test
        void genericsWithAngleBracketsIgnored() {
            assertThat(check("List<Map<String, Integer>> list")).isEmpty();
        }

        @Test
        void comparisonOperatorsIgnored() {
            assertThat(check("if (a < b && c > d) {}")).isEmpty();
        }
    }

    @Nested
    @DisplayName("unclosed delimiters")
    class UnclosedDelimiters {

        @Test
        void unclosedParen() {
            List<ValidationError> errors = check("method(a");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("Unclosed");
            assertThat(errors.get(0).getMessage()).contains("'('");
        }

        @Test
        void unclosedBrace() {
            List<ValidationError> errors = check("class C {");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("'{'");
        }

        @Test
        void unclosedBracket() {
            List<ValidationError> errors = check("int[] a = new int[5");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("'['");
        }

        @Test
        void multipleUnclosed() {
            List<ValidationError> errors = check("class C { void m( {");
            assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("mismatched delimiters")
    class MismatchedDelimiters {

        @Test
        void openParenCloseBrace() {
            List<ValidationError> errors = check("( }");
            assertThat(errors).isNotEmpty();
        }

        @Test
        void openBracketCloseParen() {
            List<ValidationError> errors = check("[ )");
            assertThat(errors).isNotEmpty();
        }

        @Test
        void openBraceCloseBracket() {
            List<ValidationError> errors = check("{ ]");
            assertThat(errors).isNotEmpty();
        }

        @Test
        void mismatchReportsExpectedClose() {
            List<ValidationError> errors = check("( ]");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("expected ')'");
        }
    }

    @Nested
    @DisplayName("stray closers")
    class StrayClosers {

        @Test
        void strayCloseParen() {
            List<ValidationError> errors = check(")");
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage()).contains("Unexpected");
        }

        @Test
        void strayCloseBrace() {
            assertThat(check("}")).isNotEmpty();
        }

        @Test
        void strayCloseBracket() {
            assertThat(check("]")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("ignores delimiters inside strings")
    void ignoresDelimitersInsideStrings() {
        assertThat(check("String s = \")({}[\";")).isEmpty();
    }

    @Test
    @DisplayName("ignores delimiters inside comments")
    void ignoresDelimitersInsideComments() {
        assertThat(check("// ({[)\n/* } */")).isEmpty();
    }

    @Test
    @DisplayName("correct nesting across lines")
    void correctNestingAcrossLines() {
        assertThat(check("class C {\n  void m() {\n    System.out.println();\n  }\n}")).isEmpty();
    }
}
