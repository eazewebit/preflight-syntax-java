package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.java.checker.SyntaxChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link JavaSyntaxEngine} covering valid Java
 * programs, syntactic errors, and complex edge cases.
 */
@DisplayName("JavaSyntaxEngine")
class JavaSyntaxEngineTest {

    private JavaSyntaxEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JavaSyntaxEngine();
    }

    // ==================================================================
    //  Valid programs
    // ==================================================================

    @Nested
    @DisplayName("valid Java programs")
    class ValidPrograms {

        @Test
        void emptyClass() {
            assertThat(engine.validate("class Foo {}").isValid()).isTrue();
        }

        @Test
        void fullClassWithFieldsAndMethods() {
            String src = """
                    public class Calculator {
                        private int result;

                        public Calculator(int initial) {
                            this.result = initial;
                        }

                        public int add(int value) {
                            result += value;
                            return result;
                        }

                        public static void main(String[] args) {
                            Calculator calc = new Calculator(0);
                            System.out.println(calc.add(42));
                        }
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void interfaceWithDefaultMethod() {
            String src = """
                    public interface Greeter {
                        default String greet(String name) {
                            return "Hello, " + name;
                        }
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void enumType() {
            String src = """
                    enum Color {
                        RED, GREEN, BLUE;

                        public String hex() {
                            return switch (this) {
                                case RED -> "#FF0000";
                                case GREEN -> "#00FF00";
                                case BLUE -> "#0000FF";
                            };
                        }
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void recordType() {
            String src = "public record Point(int x, int y) {}";
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void sealedClassWithPermits() {
            String src = "public sealed class Shape permits Circle, Rectangle {}";
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void textBlockAssignment() {
            String src = """
                    String json = \"\"\"
                            { "key": "value" }
                            \"\"\";
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void lambdaAndStream() {
            String src = """
                    var names = java.util.List.of("Alice", "Bob");
                    names.stream().filter(n -> n.length() > 3).forEach(System.out::println);
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void compactMainMethodJep512() {
            String src = """
                    void main() {
                        System.out.println("Hello, World!");
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void importModuleDeclarationJep511() {
            assertThat(engine.validate("import module java.base;").isValid()).isTrue();
        }

        @Test
        void genericsWithNestedAngleBrackets() {
            String src = "java.util.Map<String, java.util.List<Integer>> map = new java.util.HashMap<>();";
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void comparisonOperatorsNotTreatedAsGenerics() {
            String src = "boolean b = (x < 5 && y > 3) || z >= 10;";
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void annotations() {
            String src = """
                    @java.lang.Override
                    @Deprecated
                    public String toString() {
                        return "custom";
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void tryWithResources() {
            String src = """
                    try (var reader = new java.io.StringReader("text")) {
                        reader.read();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void multiCatch() {
            String src = """
                    try {
                        risky();
                    } catch (IOException | SQLException e) {
                        handle(e);
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void moduleDeclaration() {
            String src = """
                    module com.example.app {
                        requires java.base;
                        requires transitive java.sql;
                        exports com.example.api;
                        opens com.example.internal to com.fasterxml.jackson.databind;
                        uses com.example.spi.Service;
                        provides com.example.spi.Service with com.example.impl.Impl;
                    }
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void numericLiterals() {
            String src = """
                    int dec = 1_000;
                    int hex = 0xFF;
                    int bin = 0b1010;
                    long big = 999_999_999_999L;
                    double pi = 3.14159;
                    float f = 2.71828f;
                    """;
            assertThat(engine.validate(src).isValid()).isTrue();
        }

        @Test
        void emptySourceIsValid() {
            assertThat(engine.validate("").isValid()).isTrue();
        }

        @Test
        void onlyCommentsIsValid() {
            assertThat(engine.validate("// just a comment\n/* block */").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Lexical errors
    // ==================================================================

    @Nested
    @DisplayName("lexical errors")
    class LexicalErrors {

        @Test
        void unterminatedString() {
            ValidationResult result = engine.validate("String s = \"oops;");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        void unterminatedCharLiteral() {
            assertThat(engine.validate("char c = 'a").isValid()).isFalse();
        }

        @Test
        void unterminatedBlockComment() {
            assertThat(engine.validate("/* never ends").isValid()).isFalse();
        }

        @Test
        void unterminatedTextBlock() {
            assertThat(engine.validate("String s = \"\"\"\nnever ends").isValid()).isFalse();
        }
    }

    // ==================================================================
    //  Delimiter errors
    // ==================================================================

    @Nested
    @DisplayName("delimiter errors")
    class DelimiterErrors {

        @Test
        void unclosedBrace() {
            ValidationResult result = engine.validate("class Foo {");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void unclosedParenInMethod() {
            ValidationResult result = engine.validate("void m( { }");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        void mismatchedDelimiters() {
            assertThat(engine.validate("( ]").isValid()).isFalse();
        }

        @Test
        void extraClosingBrace() {
            assertThat(engine.validate("class Foo {} }").isValid()).isFalse();
        }

        @Test
        void deeplyNestedUnclosed() {
            assertThat(engine.validate("{{{").isValid()).isFalse();
        }
    }

    // ==================================================================
    //  Keyword & structural errors
    // ==================================================================

    @Nested
    @DisplayName("keyword and structural errors")
    class KeywordErrors {

        @Test
        void constKeyword() {
            assertThat(engine.validate("const int x = 1;").isValid()).isFalse();
        }

        @Test
        void gotoKeyword() {
            assertThat(engine.validate("goto label;").isValid()).isFalse();
        }

        @Test
        void conflictingAccessModifiers() {
            assertThat(engine.validate("public private void m() {}").isValid()).isFalse();
        }

        @Test
        void duplicateModifiers() {
            assertThat(engine.validate("static static int x;").isValid()).isFalse();
        }

        @Test
        void malformedAnnotation() {
            assertThat(engine.validate("@123 void m() {}").isValid()).isFalse();
        }

        @Test
        void assertWithoutExpression() {
            assertThat(engine.validate("assert ;").isValid()).isFalse();
        }
    }

    // ==================================================================
    //  Edge cases & composition
    // ==================================================================

    @Nested
    @DisplayName("edge cases and composition")
    class EdgeCases {

        @Test
        void nullSourceIsHandled() {
            assertThat(engine.validate(null).isValid()).isTrue();
        }

        @Test
        void multipleErrorTypesReported() {
            String src = "class Foo {\n  const int x = \"unterminated;\n}";
            ValidationResult result = engine.validate(src);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        void errorsContainMessages() {
            ValidationResult result = engine.validate("( ]");
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).isNotBlank());
        }

        @Test
        void invalidResultMessageIsDescriptive() {
            ValidationResult result = engine.validate("class Foo {");
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        void validResultMessageIsDescriptive() {
            ValidationResult result = engine.validate("class Foo {}");
            assertThat(result.getMessage()).isNotBlank();
        }
    }

    // ==================================================================
    //  Custom checker pipeline
    // ==================================================================

    @Nested
    @DisplayName("custom checker pipeline")
    class CustomPipeline {

        @Test
        void canInjectCustomCheckers() {
            SyntaxChecker alwaysFail = (tokens, errors) ->
                    errors.add(new com.neel.syntaxvalidation.model.ValidationError(
                            1, 1, "custom", null));
            JavaSyntaxEngine custom = new JavaSyntaxEngine(java.util.List.of(alwaysFail));
            assertThat(custom.validate("class Foo {}").isValid()).isFalse();
        }

        @Test
        void emptyPipelineAlwaysValid() {
            JavaSyntaxEngine empty = new JavaSyntaxEngine(java.util.List.of());
            assertThat(empty.validate("this is not java at all").isValid()).isTrue();
        }

        @Test
        void getCheckersReturnsCopy() {
            assertThat(engine.getCheckers()).hasSize(3);
            assertThat(engine.getCheckers()).isUnmodifiable();
        }
    }

    // ==================================================================
    //  Static convenience method
    // ==================================================================

    @Test
    @DisplayName("validateStatic delegates to default engine")
    void validateStaticDelegates() {
        assertThat(JavaSyntaxEngine.validateStatic("class Foo {}").isValid()).isTrue();
        assertThat(JavaSyntaxEngine.validateStatic("class Foo {").isValid()).isFalse();
    }
}
