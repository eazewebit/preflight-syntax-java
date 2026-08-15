package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JavacSyntaxValidator}.
 *
 * <p>Tests cover the AST-only parse phase using {@code JavacTask.parse()},
 * verifying that:
 * <ul>
 *   <li>100% strict syntax checking catches unclosed brackets, bad tokens,
 *       missing semicolons, malformed control structures, and invalid keywords.</li>
 *   <li>Zero false positives from missing imports or unresolved symbols.</li>
 *   <li>Java 25 preview features are accepted (compact source files,
 *       module imports, primitive patterns).</li>
 *   <li>Edge cases (empty source, null compiler) are handled gracefully.</li>
 * </ul>
 */
@DisplayName("JavacSyntaxValidator")
class JavacSyntaxValidatorTest {

    // ==================================================================
    //  Default constructor & compiler availability
    // ==================================================================

    @Nested
    @DisplayName("constructor & compiler availability")
    class ConstructorAndAvailability {

        @Test
        @DisplayName("default constructor uses system Java compiler")
        void defaultConstructorUsesSystemCompiler() {
            JavacSyntaxValidator validator = new JavacSyntaxValidator();
            JavaCompiler systemCompiler = ToolProvider.getSystemJavaCompiler();
            if (systemCompiler != null) {
                assertThat(validator.isCompilerAvailable()).isTrue();
            } else {
                assertThat(validator.isCompilerAvailable()).isFalse();
            }
        }

        @Test
        @DisplayName("explicit compiler is used")
        void explicitCompilerIsUsed() {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavacSyntaxValidator validator = new JavacSyntaxValidator(compiler);
            assertThat(validator.isCompilerAvailable()).isEqualTo(compiler != null);
        }

        @Test
        @DisplayName("null compiler makes validator unavailable")
        void nullCompilerMakesUnavailable() {
            JavacSyntaxValidator validator = new JavacSyntaxValidator(null);
            assertThat(validator.isCompilerAvailable()).isFalse();
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("not available");
        }
    }

    // ==================================================================
    //  Guard: empty / blank / null source
    // ==================================================================

    @Nested
    @DisplayName("guard: empty source")
    class EmptySourceGuard {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "  \n  \t  "})
        @DisplayName("null, empty, or blank source returns INVALID with EMPTY_SOURCE error")
        void emptySourceReturnsInvalid(String source) {
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("empty");
        }
    }

    // ==================================================================
    //  Valid syntax — must return VALID
    // ==================================================================

    @Nested
    @DisplayName("valid syntax")
    class ValidSyntax {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("minimal class declaration")
        void minimalClass() {
            assertThat(validator.validate("class Foo {}").isValid()).isTrue();
        }

        @Test
        @DisplayName("class with fields and methods")
        void classWithMembers() {
            String source = """
                    class Foo {
                        int x = 42;
                        String name = "hello";
                        
                        void doSomething() {
                            System.out.println(name);
                        }
                        
                        int calculate(int a, int b) {
                            return a + b;
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("record declaration")
        void recordDeclaration() {
            String source = "record Point(int x, int y) {}";
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("sealed class with permits")
        void sealedClass() {
            String source = """
                    sealed interface Shape permits Circle, Square {}
                    final class Circle implements Shape {}
                    final class Square implements Shape {}
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("switch expression with arrow cases")
        void switchExpression() {
            String source = """
                    class Foo {
                        int describe(int x) {
                            return switch (x) {
                                case 1 -> "one";
                                case 2 -> "two";
                                default -> "other";
                            };
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("pattern matching instanceof")
        void patternMatchingInstanceof() {
            String source = """
                    class Foo {
                        void test(Object obj) {
                            if (obj instanceof String s) {
                                System.out.println(s.length());
                            }
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("text blocks (multi-line strings)")
        void textBlocks() {
            String source = "class Foo {\n"
                    + "    String html = \"\"\"\n"
                    + "            <html>\n"
                    + "            <body>Hello</body>\n"
                    + "            </html>\n"
                    + "            \"\"\";\n"
                    + "}\n";
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("enhanced for-each with var")
        void enhancedForEachWithVar() {
            String source = """
                    class Foo {
                        void iterate(java.util.List<String> items) {
                            for (var item : items) {
                                System.out.println(item);
                            }
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("lambda expressions")
        void lambdaExpressions() {
            String source = """
                    class Foo {
                        java.util.function.Function<String, Integer> fn = s -> s.length();
                        java.util.function.Predicate<String> isLong = s -> s.length() > 10;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("try-with-resources")
        void tryWithResources() {
            String source = """
                    class Foo {
                        void read(java.io.InputStream in) throws Exception {
                            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
                                reader.readLine();
                            }
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Invalid syntax — must return INVALID with precise diagnostics
    // ==================================================================

    @Nested
    @DisplayName("invalid syntax")
    class InvalidSyntax {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("missing semicolon")
        void missingSemicolon() {
            String source = """
                    class Foo {
                        int x = 42
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(2);
        }

        @Test
        @DisplayName("unclosed brace")
        void unclosedBrace() {
            String source = """
                    class Foo {
                        void test() {
                            System.out.println("hello");
                        """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("extra closing brace")
        void extraClosingBrace() {
            String source = """
                    class Foo {
                        void test() {
                        }
                    }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("unterminated string literal")
        void unterminatedString() {
            String source = """
                    class Foo {
                        String s = "unterminated;
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("invalid keyword usage")
        void invalidKeywordUsage() {
            String source = """
                    class Foo {
                        int for = 42;
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("malformed method signature")
        void malformedMethodSignature() {
            String source = """
                    class Foo {
                        void (int x) {}
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("conflicting modifiers accepted at parse level (analysis-only check)")
        void conflictingModifiers() {
            // Conflicting modifiers (e.g. 'public private') are a semantic error,
            // not a syntax error. The AST parser accepts them as structurally valid
            // modifier sequences. This is expected and correct behavior —
            // JavacTask.parse() only validates syntax, not semantics.
            String source = """
                    class Foo {
                        public private void test() {}
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("invalid number literal")
        void invalidNumberLiteral() {
            String source = """
                    class Foo {
                        int x = 0xFF_GG;
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("invalid character in source")
        void invalidCharacter() {
            String source = """
                    class Foo {
                        int x = @;
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("multiple errors in one file")
        void multipleErrors() {
            String source = """
                    class Foo {
                        int x = 42
                        String s = "unterminated;
                        void (int y) {}
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("error positions include line and column numbers")
        void errorPositionsIncludeLineAndColumn() {
            String source = """
                    class Foo {
                        int x = 42
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getLine()).isGreaterThan(0);
            assertThat(result.getErrors().get(0).getColumn()).isGreaterThan(0);
        }
    }

    // ==================================================================
    //  Java 25 Preview Features — must return VALID
    // ==================================================================

    @Nested
    @DisplayName("Java 25 preview features")
    class Java25PreviewFeatures {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("JEP 512 — Compact Source Files (no explicit class wrapper)")
        void compactSourceFile() {
            String source = """
                    void main() {
                        System.out.println("Hello, World!");
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JEP 512 — Compact Source Files with import")
        void compactSourceFileWithImport() {
            String source = """
                    import java.util.List;
                    
                    void main() {
                        var items = List.of(1, 2, 3);
                        System.out.println(items);
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JEP 511 — Module Import statements")
        void moduleImport() {
            String source = """
                    import module java.base;
                    
                    class Foo {
                        void test() {
                            System.out.println("module import works");
                        }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JEP 507 — Primitive Patterns in instanceof")
        void primitivePatternsInstanceof() {
            String source = """
                    class Foo {
                        String describe(Object obj) {
                            if (obj instanceof int i) {
                                return "int: " + i;
                            }
                            if (obj instanceof long l) {
                                return "long: " + l;
                            }
                            return "other";
                        }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JEP 507 — Primitive Patterns in switch")
        void primitivePatternsSwitch() {
            String source = """
                    class Foo {
                        String describe(Object obj) {
                            return switch (obj) {
                                case int i -> "int: " + i;
                                case double d -> "double: " + d;
                                default -> "other";
                            };
                        }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Unnamed patterns and variables")
        void unnamedPatterns() {
            String source = """
                    class Foo {
                        void test(Object obj) {
                            if (obj instanceof String _) {
                                System.out.println("string");
                            }
                        }
                    }
                    """;
            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Zero false positives — missing imports/symbols must NOT fail
    // ==================================================================

    @Nested
    @DisplayName("zero false positives (missing imports/symbols)")
    class ZeroFalsePositives {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("missing import for List — must still be valid")
        void missingImportForList() {
            String source = """
                    class Foo {
                        List<String> items = new ArrayList<>();
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("missing import for custom class — must still be valid")
        void missingImportForCustomClass() {
            String source = """
                    class Foo {
                        MyCustomClass field = new MyCustomClass();
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("missing method body on interface method — valid in interface")
        void interfaceMethodNoBody() {
            String source = """
                    interface Foo {
                        void doSomething();
                        int calculate(int x, int y);
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("abstract class with abstract methods — valid")
        void abstractClassWithAbstractMethods() {
            String source = """
                    abstract class Foo {
                        abstract void doSomething();
                        abstract int calculate(int x, int y);
                        
                        void concreteMethod() {
                            System.out.println("concrete");
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("annotation usage without import — valid")
        void annotationWithoutImport() {
            String source = """
                    class Foo {
                        @Override
                        public String toString() {
                            return "Foo";
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("generic type without import — valid")
        void genericTypeWithoutImport() {
            String source = """
                    class Foo {
                        CompletableFuture<String> future;
                        Map<String, List<Integer>> map;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("method call on unknown type — valid syntax")
        void methodCallOnUnknownType() {
            String source = """
                    class Foo {
                        void test() {
                            UnknownClass.doSomething(42, "hello");
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("fully qualified type reference — valid")
        void fullyQualifiedType() {
            String source = """
                    class Foo {
                        com.example.missing.SomeClass field;
                        java.util.concurrent.atomic.AtomicReference<String> ref;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  File name handling
    // ==================================================================

    @Nested
    @DisplayName("file name handling")
    class FileNameHandling {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("null file name defaults to SyntaxCheck.java")
        void nullFileNameDefaults() {
            ValidationResult result = validator.validate("class Foo {}", null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("blank file name defaults to SyntaxCheck.java")
        void blankFileNameDefaults() {
            ValidationResult result = validator.validate("class Foo {}", "   ");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("explicit file name is used")
        void explicitFileName() {
            ValidationResult result = validator.validate("class Foo {}", "Foo.java");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("convenience overload uses default file name")
        void convenienceOverload() {
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Edge cases
    // ==================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

        @Test
        @DisplayName("very large source file")
        void veryLargeSourceFile() {
            StringBuilder sb = new StringBuilder();
            sb.append("class Foo {\n");
            for (int i = 0; i < 1000; i++) {
                sb.append("    int field").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("}\n");
            assertThat(validator.validate(sb.toString()).isValid()).isTrue();
        }

        @Test
        @DisplayName("unicode characters in string literals")
        void unicodeInStrings() {
            String source = """
                    class Foo {
                        String s = "Hello \\u0048\\u0065\\u006c\\u006c\\u006f";
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("nested classes")
        void nestedClasses() {
            String source = """
                    class Outer {
                        class Inner {
                            class DeeplyNested {
                                void test() {}
                            }
                        }
                        static class StaticInner {
                            static class DeeplyNested {
                                static void test() {}
                            }
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("enum with constructor and methods")
        void enumWithConstructor() {
            String source = """
                    enum Color {
                        RED("FF0000"),
                        GREEN("00FF00"),
                        BLUE("0000FF");
                        
                        private final String hex;
                        
                        Color(String hex) {
                            this.hex = hex;
                        }
                        
                        String hex() {
                            return hex;
                        }
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("annotation declaration")
        void annotationDeclaration() {
            String source = """
                    @interface MyAnnotation {
                        String value() default "";
                        int priority() default 0;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("multiline comment with syntax inside")
        void multilineComment() {
            String source = """
                    class Foo {
                        /* 
                         * This is a comment with
                         * int x = ; // invalid syntax inside comment
                         */
                        int x = 42;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("single-line comment with syntax inside")
        void singleLineComment() {
            String source = """
                    class Foo {
                        // int x = ; // invalid syntax inside comment
                        int x = 42;
                    }
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty class is valid")
        void emptyClass() {
            assertThat(validator.validate("class Foo {}").isValid()).isTrue();
        }

        @Test
        @DisplayName("package declaration is valid")
        void packageDeclaration() {
            String source = """
                    package com.example;
                    
                    class Foo {}
                    """;
            assertThat(validator.validate(source).isValid()).isTrue();
        }
    }
}
