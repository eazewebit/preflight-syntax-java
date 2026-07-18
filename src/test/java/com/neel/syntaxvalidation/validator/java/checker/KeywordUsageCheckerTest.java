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
 * Comprehensive tests for {@link KeywordUsageChecker}.
 */
@DisplayName("KeywordUsageChecker")
class KeywordUsageCheckerTest {

    private final KeywordUsageChecker checker = new KeywordUsageChecker();

    private List<ValidationError> check(String src) {
        List<JavaToken> tokens = new JavaLexer(src).tokenize();
        List<ValidationError> errors = new ArrayList<>();
        checker.check(tokens, errors);
        return errors;
    }

    // ------------------------------------------------------------------
    //  Modifier conflicts & duplicates
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("modifier analysis")
    class ModifierAnalysis {

        @Test
        void validModifiersNoErrors() {
            assertThat(check("public static final int X = 1;")).isEmpty();
        }

        @Test
        void validPrivateProtectedNoError() {
            // single access modifier is fine
            assertThat(check("private void m() {}")).isEmpty();
        }

        @Test
        void conflictingAccessModifiers() {
            List<ValidationError> errors = check("public private void m() {}");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("Conflicting access modifiers");
        }

        @Test
        void protectedPublicConflict() {
            List<ValidationError> errors = check("protected public class Foo {}");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("Conflicting");
        }

        @Test
        void duplicateStatic() {
            List<ValidationError> errors = check("static static int x;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("Duplicate modifier");
        }

        @Test
        void duplicateFinal() {
            List<ValidationError> errors = check("final final class C {}");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("Duplicate");
        }

        @Test
        void modifierRunStopsAtNonModifier() {
            // public static should not conflict with a later 'private' in a different declaration
            assertThat(check("public static int a; private int b;")).isEmpty();
        }

        @Test
        void modifiersWithAnnotation() {
            assertThat(check("@Override public void m() {}")).isEmpty();
        }
    }

    // ------------------------------------------------------------------
    //  Reserved keywords
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("reserved keywords")
    class ReservedKeywords {

        @Test
        void constIsIllegal() {
            List<ValidationError> errors = check("const int x = 1;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("'const'");
        }

        @Test
        void gotoIsIllegal() {
            List<ValidationError> errors = check("goto label;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("'goto'");
        }
    }

    // ------------------------------------------------------------------
    //  Annotations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("annotations")
    class Annotations {

        @Test
        void validAnnotation() {
            assertThat(check("@Override")).isEmpty();
        }

        @Test
        void validAnnotationWithIdentifier() {
            assertThat(check("@Deprecated")).isEmpty();
        }

        @Test
        void annotationWithFullyQualifiedName() {
            assertThat(check("@java.lang.Override")).isEmpty();
        }

        @Test
        void annotationNotFollowedByIdentifier() {
            List<ValidationError> errors = check("@ 123");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("Annotation '@'");
        }

        @Test
        void annotationAtEndOfFile() {
            List<ValidationError> errors = check("@");
            assertThat(errors).isNotEmpty();
        }
    }

    // ------------------------------------------------------------------
    //  Statement completeness
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("statement completeness")
    class StatementCompleteness {

        @Test
        void validAssert() {
            assertThat(check("assert x > 0;")).isEmpty();
        }

        @Test
        void assertWithoutExpression() {
            List<ValidationError> errors = check("assert;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("'assert'");
        }

        @Test
        void assertAtEof() {
            assertThat(check("assert ")).isNotEmpty();
        }

        @Test
        void validPackage() {
            assertThat(check("package com.example;")).isEmpty();
        }

        @Test
        void packageWithoutName() {
            List<ValidationError> errors = check("package ;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("'package'");
        }

        @Test
        void validImport() {
            assertThat(check("import java.util.List;")).isEmpty();
        }

        @Test
        void validStaticImport() {
            assertThat(check("import static java.lang.Math.PI;")).isEmpty();
        }

        @Test
        void validModuleImport() {
            assertThat(check("import module java.base;")).isEmpty();
        }

        @Test
        void importWithoutTarget() {
            List<ValidationError> errors = check("import ;");
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0).getMessage()).contains("'import'");
        }
    }

    // ------------------------------------------------------------------
    //  Complex valid programs (no false positives)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("complex valid programs")
    class ComplexValidPrograms {

        @Test
        void recordWithCompactConstructor() {
            assertThat(check("public record Point(int x, int y) {}")).isEmpty();
        }

        @Test
        void sealedClassHierarchy() {
            assertThat(check("sealed interface Shape permits Circle, Square {}")).isEmpty();
        }

        @Test
        void switchExpressionWithYield() {
            assertThat(check("int r = switch (x) { case 1 -> { yield 10; } default -> 0; };")).isEmpty();
        }

        @Test
        void enumWithAnnotations() {
            assertThat(check("enum E { @Deprecated A, B; }")).isEmpty();
        }

        @Test
        void nestedClassWithModifiers() {
            assertThat(check("public class Outer { protected static final class Inner {} }")).isEmpty();
        }
    }
}
