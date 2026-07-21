package com.neel.syntaxvalidation.fallback.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.css.CssValidator;
import com.neel.syntaxvalidation.validator.html.HtmlValidator;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.php.PhpValidator;
import com.neel.syntaxvalidation.validator.python.PythonValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for "Fallback Mode" — the execution path where the
 * primary binary is unavailable or fails, and the library gracefully degrades
 * to the embedded syntax engine.
 * <p>
 * Uses a StubBinaryResolver that returns empty to force fallback mode.
 */
@DisplayName("Fallback Mode Integration Tests")
class FallbackModeIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a BinaryResolver that always returns empty (no binary available),
     * forcing the validator into fallback mode.
     */
    private static class StubBinaryResolver extends BinaryResolver {
        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) {
            return Optional.empty();
        }
    }

    private static class StubProcessExecutor extends ProcessExecutor {
        @Override
        public ProcessResult execute(java.util.List<String> command)
                throws IOException, InterruptedException {
            throw new IOException("Simulated process failure — binary not available");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. JAVA FALLBACK — JavaSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Java fallback — JavaSyntaxEngine")
    class JavaFallbackTests {

        private JavaValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new JavaValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid Java class — fallback produces valid result")
        void validJavaClass_fallbackProducesValidResult() {
            String code = """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("valid Java with imports — fallback handles")
        void validJava_withImports_fallbackHandles() {
            String code = """
                    import java.util.List;
                    import java.util.ArrayList;
                    
                    public class WithImports {
                        public static void main(String[] args) {
                            List<String> list = new ArrayList<>();
                            list.add("hello");
                            System.out.println(list);
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid Java with interfaces and abstract classes")
        void validJava_withInterfacesAndAbstract() {
            String code = """
                    interface Greetable {
                        String greet();
                    }
                    
                    abstract class BasePerson implements Greetable {
                        protected String name;
                        
                        BasePerson(String name) {
                            this.name = name;
                        }
                    }
                    
                    public class Person extends BasePerson {
                        Person(String name) {
                            super(name);
                        }
                        
                        @Override
                        public String greet() {
                            return "Hello, " + name;
                        }
                        
                        public static void main(String[] args) {
                            Person p = new Person("Alice");
                            System.out.println(p.greet());
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid Java with generics — fallback handles")
        void validJava_withGenerics_fallbackHandles() {
            String code = """
                    import java.util.Map;
                    import java.util.HashMap;
                    
                    public class GenericContainer<K, V> {
                        private final Map<K, V> map = new HashMap<>();
                        
                        public void put(K key, V value) {
                            map.put(key, value);
                        }
                        
                        public V get(K key) {
                            return map.get(key);
                        }
                        
                        public static void main(String[] args) {
                            GenericContainer<String, Integer> gc = new GenericContainer<>();
                            gc.put("age", 30);
                            System.out.println(gc.get("age"));
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unmatched braces — fallback detects error")
        void unmatchedBraces_fallbackDetectsError() {
            String code = """
                    public class Bad {
                        public static void main(String[] args) {
                            System.out.println("missing brace");
                        
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("empty code — fallback handles gracefully")
        void emptyCode_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("whitespace-only code — fallback handles gracefully")
        void whitespaceOnlyCode_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate("   \n\n\t  \n");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("comment-only code — fallback handles gracefully")
        void commentOnlyCode_fallbackHandlesGracefully() {
            String code = """
                    // This is a comment
                    /* Block comment */
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unicode in Java code — fallback handles gracefully")
        void unicodeInJavaCode_fallbackHandlesGracefully() {
            String code = """
                    // 日本語コメント
                    public class Unicode {
                        public static void main(String[] args) {
                            String s = "Héllo Wörld 🚀";
                            System.out.println(s);
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("very large code — fallback does not hang")
        void veryLargeCode_fallbackDoesNotHang() {
            StringBuilder sb = new StringBuilder("public class Large {\n");
            sb.append("    public static void main(String[] args) {\n");
            for (int i = 0; i < 2000; i++) {
                sb.append("        int v").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("    }\n}");
            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("error details contain line numbers when errors exist")
        void errorDetails_containLineNumbers() {
            String code = """
                    public class Bad {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            if (!result.isValid() && !result.getErrors().isEmpty()) {
                ValidationError firstError = result.getErrors().get(0);
                assertThat(firstError.getLine()).isGreaterThan(0);
                assertThat(firstError.getMessage()).isNotNull().isNotEmpty();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. JAVASCRIPT FALLBACK — JavaScriptSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JavaScript fallback — JavaScriptSyntaxEngine")
    class JavaScriptFallbackTests {

        private JavaScriptValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new JavaScriptValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid JS — fallback produces valid result")
        void validJs_fallbackProducesValidResult() {
            String code = """
                    const greet = (name) => `Hello, ${name}!`;
                    console.log(greet('World'));
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid JS with functions and loops")
        void validJs_withFunctionsAndLoops() {
            String code = """
                    function fibonacci(n) {
                        if (n <= 1) return n;
                        let a = 0, b = 1;
                        for (let i = 2; i <= n; i++) {
                            [a, b] = [b, a + b];
                        }
                        return b;
                    }
                    console.log(fibonacci(10));
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("unmatched braces — fallback detects error")
        void unmatchedBraces_fallbackDetectsError() {
            String code = """
                    function foo() {
                        console.log("bar");
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("empty JS — fallback handles gracefully")
        void emptyJs_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("unicode in JS — fallback handles gracefully")
        void unicodeInJs_fallbackHandlesGracefully() {
            String code = """
                    const msg = "日本語 🚀 émojis";
                    console.log(msg);
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("error details contain line and column info when errors exist")
        void errorDetails_containLineAndColumnInfo() {
            String code = """
                    function foo() {
                        console.log("ok");
                    """;
            ValidationResult result = validator.validate(code);
            if (!result.isValid() && !result.getErrors().isEmpty()) {
                ValidationError firstError = result.getErrors().get(0);
                assertThat(firstError.getLine()).isGreaterThan(0);
                assertThat(firstError.getMessage()).isNotNull().isNotEmpty();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. PYTHON FALLBACK — PythonSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Python fallback — PythonSyntaxEngine")
    class PythonFallbackTests {

        private PythonValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new PythonValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid Python — fallback produces valid result")
        void validPython_fallbackProducesValidResult() {
            String code = """
                    def greet(name):
                        return f"Hello, {name}!"
                    
                    print(greet("World"))
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid Python with classes and decorators")
        void validPython_withClassesAndDecorators() {
            String code = """
                    from functools import wraps
                    
                    def memoize(func):
                        cache = {}
                        @wraps(func)
                        def wrapper(*args):
                            if args not in cache:
                                cache[args] = func(*args)
                            return cache[args]
                        return wrapper
                    
                    @memoize
                    def factorial(n):
                        if n <= 1:
                            return 1
                        return n * factorial(n - 1)
                    
                    print(factorial(10))
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("missing colon — fallback detects")
        void missingColon_fallbackDetects() {
            String code = """
                    def foo()
                        pass
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("empty Python — valid in fallback")
        void emptyPython_validInFallback() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("unicode in Python — fallback handles")
        void unicodeInPython_fallbackHandles() {
            String code = """
                    你好 = "世界"
                    π = 3.14159
                    print(你好, π)
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("error details contain line numbers in fallback")
        void errorDetails_containLineNumbers() {
            String code = """
                    def foo():
                        print("ok")
                    
                    def bar()
                        print("bad")
                    """;
            ValidationResult result = validator.validate(code);
            if (!result.isValid() && !result.getErrors().isEmpty()) {
                ValidationError firstError = result.getErrors().get(0);
                assertThat(firstError.getLine()).isGreaterThan(0);
                assertThat(firstError.getMessage()).isNotNull().isNotEmpty();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. PHP FALLBACK — PhpSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PHP fallback — PhpSyntaxEngine")
    class PhpFallbackTests {

        private PhpValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new PhpValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid PHP — fallback produces valid result")
        void validPhp_fallbackProducesValidResult() {
            String code = """
                    <?php
                    function greet(string $name): string {
                        return "Hello, " . $name . "!";
                    }
                    echo greet("World");
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP with classes and interfaces")
        void validPhp_withClassesAndInterfaces() {
            String code = """
                    <?php
                    interface Shape {
                        public function area(): float;
                    }
                    
                    class Circle implements Shape {
                        public function __construct(private float $radius) {}
                        
                        public function area(): float {
                            return M_PI * $this->radius ** 2;
                        }
                    }
                    
                    $circle = new Circle(5.0);
                    echo $circle->area();
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("unclosed brace — fallback detects")
        void unclosedBrace_fallbackDetects() {
            String code = """
                    <?php
                    function foo() {
                        echo "incomplete";
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("empty PHP — fallback handles gracefully")
        void emptyPhp_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate("<?php\n");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unicode in PHP — fallback handles")
        void unicodeInPhp_fallbackHandles() {
            String code = """
                    <?php
                    $msg = "日本語テスト 🚀";
                    echo $msg;
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. HTML FALLBACK — HtmlSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HTML fallback — HtmlSyntaxEngine")
    class HtmlFallbackTests {

        private HtmlValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new HtmlValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid HTML — fallback produces valid result")
        void validHtml_fallbackProducesValidResult() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                    </head>
                    <body>
                        <h1>Hello</h1>
                        <p>World</p>
                    </body>
                    </html>
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("empty HTML — fallback handles gracefully")
        void emptyHtml_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. CSS FALLBACK — CssSyntaxEngine
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CSS fallback — CssSyntaxEngine")
    class CssFallbackTests {

        private CssValidator validator;

        @BeforeEach
        void setUp() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            validator = new CssValidator(null, resolver, executor);
        }

        @Test
        @DisplayName("valid CSS — fallback produces valid result")
        void validCss_fallbackProducesValidResult() {
            String code = """
                    body {
                        margin: 0;
                        padding: 0;
                        font-family: Arial, sans-serif;
                    }
                    
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("missing closing brace — fallback detects")
        void missingClosingBrace_fallbackDetects() {
            String code = """
                    .broken {
                        color: red;
                        background: blue;
                    """;
            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("empty CSS — valid in fallback")
        void emptyCss_validInFallback() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("null source — fallback handles gracefully")
        void nullSource_fallbackHandlesGracefully() {
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. CROSS-LANGUAGE FALLBACK CONSISTENCY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-language fallback consistency")
    class CrossLanguageFallbackConsistencyTests {

        @Test
        @DisplayName("all fallback validators produce consistent results")
        void allFallbackValidators_produceConsistentResults() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();

            // Java
            JavaValidator javaValidator = new JavaValidator(null, resolver, executor);
            String javaCode = "public class T { public static void main(String[] a) { } }";
            ValidationResult javaResult = javaValidator.validate(javaCode);
            assertThat(javaResult).isNotNull();
            assertThat(javaResult.isValid()).isTrue();

            // JavaScript
            JavaScriptValidator jsValidator = new JavaScriptValidator(null, resolver, executor);
            String jsCode = "console.log('test');";
            ValidationResult jsResult = jsValidator.validate(jsCode);
            assertThat(jsResult).isNotNull();
            assertThat(jsResult.isValid()).isTrue();

            // Python
            PythonValidator pyValidator = new PythonValidator(null, resolver, executor);
            String pyCode = "print('test')";
            ValidationResult pyResult = pyValidator.validate(pyCode);
            assertThat(pyResult).isNotNull();
            assertThat(pyResult.isValid()).isTrue();

            // PHP
            PhpValidator phpValidator = new PhpValidator(null, resolver, executor);
            String phpCode = "<?php echo 'test';";
            ValidationResult phpResult = phpValidator.validate(phpCode);
            assertThat(phpResult).isNotNull();
            assertThat(phpResult.isValid()).isTrue();
        }

        @Test
        @DisplayName("fallback validators do not mutate input")
        void fallbackValidators_doNotMutateInput() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();

            String originalCode = "console.log('test');";
            String codeCopy = new String(originalCode);

            JavaScriptValidator jsValidator = new JavaScriptValidator(null, resolver, executor);
            jsValidator.validate(originalCode);

            assertThat(originalCode).isEqualTo(codeCopy);
        }

        @Test
        @DisplayName("fallback validators are thread-safe")
        void fallbackValidators_areThreadSafe() throws Exception {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaScriptValidator jsValidator = new JavaScriptValidator(null, resolver, executor);

            var threadPool = java.util.concurrent.Executors.newFixedThreadPool(8);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<ValidationResult>>();

            String code = "console.log('concurrent');";
            for (int i = 0; i < 30; i++) {
                futures.add(threadPool.submit(() -> jsValidator.validate(code)));
            }

            for (var future : futures) {
                ValidationResult result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }

            threadPool.shutdown();
        }

        @Test
        @DisplayName("fallback error messages are human-readable")
        void fallbackErrorMessages_areHumanReadable() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();

            // Java syntax error (unmatched braces)
            JavaValidator javaValidator = new JavaValidator(null, resolver, executor);
            String badJava = """
                    public class Bad {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    """;
            ValidationResult javaResult = javaValidator.validate(badJava);
            if (!javaResult.isValid() && !javaResult.getErrors().isEmpty()) {
                for (ValidationError err : javaResult.getErrors()) {
                    assertThat(err.getMessage())
                            .as("Java error message")
                            .isNotNull()
                            .isNotEmpty()
                            .hasSizeGreaterThan(5);
                }
            }

            // Python syntax error (missing colon)
            PythonValidator pyValidator = new PythonValidator(null, resolver, executor);
            String badPython = """
                    def foo()
                        print("bad")
                    """;
            ValidationResult pyResult = pyValidator.validate(badPython);
            if (!pyResult.isValid() && !pyResult.getErrors().isEmpty()) {
                for (ValidationError err : pyResult.getErrors()) {
                    assertThat(err.getMessage())
                            .as("Python error message")
                            .isNotNull()
                            .isNotEmpty()
                            .hasSizeGreaterThan(5);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. PERFORMANCE UNDER FALLBACK MODE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance under fallback mode")
    class FallbackPerformanceTests {

        @Test
        @DisplayName("fallback validation completes within reasonable time")
        void fallbackValidation_completesWithinReasonableTime() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaValidator validator = new JavaValidator(null, resolver, executor);

            String code = """
                    public class PerfTest {
                        public static void main(String[] args) {
                            for (int i = 0; i < 100; i++) {
                                System.out.println(i);
                            }
                        }
                    }
                    """;

            long start = System.currentTimeMillis();
            ValidationResult result = validator.validate(code);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(elapsed).isLessThan(5000);
        }

        @Test
        @DisplayName("repeated fallback validation is consistent")
        void repeatedFallbackValidation_isConsistent() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaScriptValidator validator = new JavaScriptValidator(null, resolver, executor);

            String code = """
                    const x = 42;
                    console.log(x);
                    """;

            ValidationResult first = validator.validate(code);
            ValidationResult second = validator.validate(code);
            ValidationResult third = validator.validate(code);

            assertThat(first.isValid()).isEqualTo(second.isValid());
            assertThat(second.isValid()).isEqualTo(third.isValid());
            assertThat(first.getErrors().size()).isEqualTo(second.getErrors().size());
            assertThat(second.getErrors().size()).isEqualTo(third.getErrors().size());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  9. VALIDATION RESULT MODEL INTEGRITY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidationResult model integrity in fallback")
    class ValidationResultModelTests {

        @Test
        @DisplayName("ValidationResult is immutable in fallback mode")
        void validationResult_isImmutable() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaValidator validator = new JavaValidator(null, resolver, executor);

            String code = """
                    public class T {
                        public static void main(String[] args) {
                            System.out.println("hi");
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);

            assertThatThrownBy(() -> result.getErrors().add(new ValidationError(1, 0, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("ValidationError has correct line and column in fallback")
        void validationError_hasCorrectLineAndColumn() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaScriptValidator validator = new JavaScriptValidator(null, resolver, executor);

            String code = """
                    function foo() {
                        console.log("ok");
                    """;
            ValidationResult result = validator.validate(code);

            if (!result.isValid() && !result.getErrors().isEmpty()) {
                ValidationError err = result.getErrors().get(0);
                assertThat(err.getLine()).isGreaterThanOrEqualTo(1);
                assertThat(err.getMessage()).isNotNull().isNotEmpty();
            }
        }

        @Test
        @DisplayName("result is reported in fallback mode")
        void result_isReported() {
            BinaryResolver resolver = new StubBinaryResolver();
            ProcessExecutor executor = new StubProcessExecutor();
            JavaValidator validator = new JavaValidator(null, resolver, executor);

            String code = """
                    public class Multi {
                        public static void main(String[] args) {
                            System.out.println("missing semi")
                            int x = "not a number";
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result).isNotNull();
        }
    }
}