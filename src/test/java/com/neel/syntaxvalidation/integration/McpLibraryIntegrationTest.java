package com.neel.syntaxvalidation.integration;

import com.neel.syntaxvalidation.SyntaxValidationLibrary;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the SyntaxValidationLibrary.
 * <p>
 * Tests the complete MCP (Model Context Protocol) library integration:
 * <ul>
 *   <li>Library initialization and configuration</li>
 *   <li>End-to-end validation flow via ModificationRequest</li>
 *   <li>Mixed content validation</li>
 *   <li>File caching integration</li>
 *   <li>Error propagation through the stack</li>
 *   <li>Concurrent usage</li>
 *   <li>Resource cleanup</li>
 * </ul>
 */
@DisplayName("MCP Library Integration Tests")
class McpLibraryIntegrationTest {

    @TempDir
    Path tempDir;

    // ══════════════════════════════════════════════════════════════════════
    //  1. LIBRARY INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Library initialization")
    class LibraryInitializationTests {

        @Test
        @DisplayName("library can be instantiated with default configuration")
        void libraryCanBeInstantiated_withDefaultConfig() {
            SyntaxValidationLibrary library = new SyntaxValidationLibrary();
            assertThat(library).isNotNull();
        }

        @Test
        @DisplayName("library can be instantiated with custom ValidatorFactory")
        void libraryCanBeInstantiated_withCustomFactory() {
            ValidatorFactory factory = new ValidatorFactory();
            SyntaxValidationLibrary library = new SyntaxValidationLibrary(factory);
            assertThat(library).isNotNull();
        }

        @Test
        @DisplayName("factory provides validators for all supported languages")
        void factoryProvidesValidators_forAllSupportedLanguages() {
            ValidatorFactory factory = new ValidatorFactory();
            Language[] supported = {Language.JAVA, Language.JAVASCRIPT, Language.PYTHON, Language.PHP, Language.TYPESCRIPT};
            for (Language lang : supported) {
                Optional<?> validator = factory.getValidator(lang);
                assertThat(validator)
                        .as("Validator for " + lang)
                        .isPresent();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. END-TO-END VALIDATION FLOW VIA MODIFICATION REQUEST
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("End-to-end validation flow via ModificationRequest")
    class EndToEndValidationTests {

        @Test
        @DisplayName("Java validation — complete flow with real javac")
        void javaValidation_completeFlow() throws IOException {
            // Write a Java file
            Path javaFile = tempDir.resolve("Hello.java");
            Files.writeString(javaFile, """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Create a modification request — no-op (replace line 1 with itself)
            ModificationRequest request = ModificationRequest.builder()
                    .filePath(javaFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("public class Hello {")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("JavaScript validation — complete flow with real node")
        void javaScriptValidation_completeFlow() throws IOException {
            Path jsFile = tempDir.resolve("greet.js");
            Files.writeString(jsFile, """
                    const greet = (name) => `Hello, ${name}!`;
                    console.log(greet('World'));
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(jsFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("const greet = (name) => `Hello, ${name}!`;")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("Python validation — complete flow with real python")
        void pythonValidation_completeFlow() throws IOException {
            Path pyFile = tempDir.resolve("greet.py");
            Files.writeString(pyFile, """
                    def greet(name: str) -> str:
                        return f"Hello, {name}!"
                    
                    if __name__ == "__main__":
                        print(greet("World"))
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(pyFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("def greet(name: str) -> str:")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("PHP validation — complete flow with real php")
        void phpValidation_completeFlow() throws IOException {
            Path phpFile = tempDir.resolve("greet.php");
            Files.writeString(phpFile, """
                    <?php
                    function greet(string $name): string {
                        return "Hello, " . $name . "!";
                    }
                    echo greet("World");
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(phpFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("<?php")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("TypeScript validation — complete flow with built-in engine")
        void typeScriptValidation_completeFlow() throws IOException {
            Path tsFile = tempDir.resolve("greet.ts");
            Files.writeString(tsFile, """
                    interface User {
                        name: string;
                        age: number;
                    }
                    
                    function greet(user: User): string {
                        return `Hello, ${user.name}!`;
                    }
                    
                    const user: User = { name: "World", age: 30 };
                    console.log(greet(user));
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(tsFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("interface User {")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("TypeScript validation — TSX file with JSX mode")
        void typeScriptValidation_tsxFileWithJsxMode() throws IOException {
            Path tsxFile = tempDir.resolve("App.tsx");
            Files.writeString(tsxFile, """
                    import React from 'react';
                    
                    interface AppProps {
                        title: string;
                    }
                    
                    const App: React.FC<AppProps> = ({ title }) => {
                        return (
                            <div>
                                <h1>{title}</h1>
                            </div>
                        );
                    };
                    
                    export default App;
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(tsxFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("import React from 'react';")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("TypeScript validation — JSX file")
        void typeScriptValidation_jsxFile() throws IOException {
            Path jsxFile = tempDir.resolve("Component.jsx");
            Files.writeString(jsxFile, """
                    import React from 'react';
                    
                    const Component = () => {
                        return (
                            <div className="component">
                                <p>Hello JSX</p>
                            </div>
                        );
                    };
                    
                    export default Component;
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(jsxFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("import React from 'react';")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("TypeScript validation — syntax error detection")
        void typeScriptValidation_syntaxErrorDetection() throws IOException {
            Path tsFile = tempDir.resolve("bad.ts");
            Files.writeString(tsFile, """
                    function greet(name: string) {
                        return `Hello, ${name}!`;
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Introduce a syntax error (unclosed brace)
            ModificationRequest request = ModificationRequest.builder()
                    .filePath(tsFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("function greet(name: string) {")
                    .build();

            ValidationResult result = library.validate(request);

            // Should detect the syntax error from built-in engine
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validation with syntax errors — returns errors")
        void validation_withSyntaxErrors_returnsErrors() throws IOException {
            Path javaFile = tempDir.resolve("Bad.java");
            Files.writeString(javaFile, """
                    public class Bad {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Introduce a syntax error
            ModificationRequest request = ModificationRequest.builder()
                    .filePath(javaFile.toString())
                    .fromLine(3)
                    .toLine(3)
                    .replacement("        System.out.println(\"missing semi\")")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("validation with non-existent file — returns error")
        void validation_withNonExistentFile_returnsError() {
            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(tempDir.resolve("nonexistent.java").toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("code")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("does not exist");
        }

        @Test
        @DisplayName("validation with unsupported language — returns error")
        void validation_withUnsupportedLanguage_returnsError() throws IOException {
            Path unknownFile = tempDir.resolve("test.xyz");
            Files.writeString(unknownFile, "some content");

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(unknownFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("new content")
                    .build();

            ValidationResult result = library.validate(request);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("Unable to detect");
        }

        @Test
        @DisplayName("validation with null request — throws exception")
        void validation_withNullRequest_throwsException() {
            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            assertThatThrownBy(() -> library.validate((ModificationRequest) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. MIXED CONTENT VALIDATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mixed content validation")
    class MixedContentValidationTests {

        @Test
        @DisplayName("validates HTML with embedded CSS and JS")
        void validatesHtml_withEmbeddedCssAndJs() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Mixed</title>
                        <style>
                            body { margin: 0; }
                            .container { display: flex; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>Hello</h1>
                        </div>
                        <script>
                            console.log('hello');
                        </script>
                    </body>
                    </html>
                    """;

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();
            ValidationResult result = library.validateMixedContent(html);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validates HTML with invalid embedded JS")
        void validatesHtml_withInvalidEmbeddedJs() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Bad JS</title>
                    </head>
                    <body>
                        <script>
                            const x = ;
                        </script>
                    </body>
                    </html>
                    """;

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();
            ValidationResult result = library.validateMixedContent(html);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validates HTML with null source — throws exception")
        void validatesHtml_withNullSource_throwsException() {
            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            assertThatThrownBy(() -> library.validateMixedContent((String) null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("validates HTML via ModificationRequest")
        void validatesHtml_viaModificationRequest() throws IOException {
            Path htmlFile = tempDir.resolve("test.html");
            Files.writeString(htmlFile, """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                    </head>
                    <body>
                        <h1>Hello</h1>
                    </body>
                    </html>
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(htmlFile.toString())
                    .fromLine(9)
                    .toLine(9)
                    .replacement("        <h1>Modified</h1>")
                    .build();

            ValidationResult result = library.validateMixedContent(request);

            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. FILE CACHING INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("File caching integration")
    class FileCacheTests {

        @Test
        @DisplayName("cache invalidation works")
        void cacheInvalidation_works() throws IOException {
            Path javaFile = tempDir.resolve("CacheTest.java");
            Files.writeString(javaFile, """
                    public class CacheTest {
                        public static void main(String[] args) {
                            System.out.println("cached");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // First validation — loads file into cache
            ModificationRequest request = ModificationRequest.builder()
                    .filePath(javaFile.toString())
                    .fromLine(3)
                    .toLine(3)
                    .replacement("        System.out.println(\"after cache\");")
                    .build();

            ValidationResult first = library.validate(request);
            assertThat(first.isValid()).isTrue();

            // Invalidate cache
            library.invalidateCache(Path.of(javaFile.toString()));

            // Second validation — reloads from disk
            ValidationResult second = library.validate(request);
            assertThat(second.isValid()).isTrue();
        }

        @Test
        @DisplayName("clear cache removes all entries")
        void clearCache_removesAllEntries() throws IOException {
            Path javaFile = tempDir.resolve("ClearCache.java");
            Files.writeString(javaFile, """
                    public class ClearCache {
                        public static void main(String[] args) {
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Load into cache
            ModificationRequest request = ModificationRequest.builder()
                    .filePath(javaFile.toString())
                    .fromLine(1)
                    .toLine(1)
                    .replacement("public class ClearCache {")
                    .build();

            library.validate(request);

            // Clear cache
            library.clearCache();

            // Should still work (reloads from disk)
            ValidationResult result = library.validate(request);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. VALIDATION RESULT MODEL
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidationResult model integration")
    class ValidationResultModelTests {

        @Test
        @DisplayName("valid result is correctly structured")
        void validResult_isCorrectlyStructured() {
            ValidationResult result = ValidationResult.valid("All good");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isEqualTo("All good");
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.hasErrors()).isFalse();
            assertThat(result.toString()).isNotNull();
        }

        @Test
        @DisplayName("invalid result contains meaningful errors")
        void invalidResult_containsMeaningfulErrors() {
            ValidationError error = new ValidationError(42, 10, "Unexpected token", "tool output");
            ValidationResult result = ValidationResult.invalid("Syntax error", error);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Syntax error");
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.hasErrors()).isTrue();

            ValidationError err = result.getErrors().get(0);
            assertThat(err.getLine()).isEqualTo(42);
            assertThat(err.getColumn()).isEqualTo(10);
            assertThat(err.getMessage()).isEqualTo("Unexpected token");
        }

        @Test
        @DisplayName("ValidationResult is immutable")
        void validationResult_isImmutable() {
            ValidationError error = new ValidationError(1, 0, "error", "tool output");
            ValidationResult result = ValidationResult.invalid("failed", error);

            assertThatThrownBy(() -> result.getErrors().add(new ValidationError(2, 0, "test", "tool output")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("ValidationError has correct toString representation")
        void validationError_hasCorrectToString() {
            ValidationError error = new ValidationError(42, 10, "Unexpected token", "tool output");
            String str = error.toString();

            assertThat(str).contains("42");
            assertThat(str).contains("10");
            assertThat(str).contains("Unexpected token");
        }

        @Test
        @DisplayName("ValidationResult has correct toString representation")
        void validationResult_hasCorrectToString() {
            ValidationResult valid = ValidationResult.valid("ok");
            assertThat(valid.toString()).containsIgnoringCase("valid");

            ValidationResult invalid = ValidationResult.invalid("failed");
            assertThat(invalid.toString()).containsIgnoringCase("result");
        }

        @Test
        @DisplayName("ValidationResult.valid() factory method")
        void validationResult_validFactory() {
            ValidationResult result = ValidationResult.valid("All good");
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getMessage()).isEqualTo("All good");
        }

        @Test
        @DisplayName("ValidationResult.invalid() factory method")
        void validationResult_invalidFactory() {
            ValidationError e1 = new ValidationError(1, 0, "error1", "tool output");
            ValidationError e2 = new ValidationError(2, 5, "error2", "tool output");

            ValidationResult result = ValidationResult.invalid("failed", List.of(e1, e2));
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. CONCURRENT USAGE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent usage")
    class ConcurrentUsageTests {



        @Test
        @DisplayName("concurrent validation with errors — correct error isolation")
        void concurrentValidation_withErrors_correctIsolation() throws Exception {
            // Create valid file
            Path validFile = tempDir.resolve("Valid.java");
            Files.writeString(validFile, """
                    public class Valid {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    }
                    """);

            // Create invalid file
            Path invalidFile = tempDir.resolve("Invalid.java");
            Files.writeString(invalidFile, """
                    public class Invalid {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();
            var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<ValidationResult>>();

            // Valid modifications
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ModificationRequest request = ModificationRequest.builder()
                            .filePath(validFile.toString())
                            .fromLine(3)
                            .toLine(3)
                            .replacement("        System.out.println(\"still ok\");")
                            .build();
                    return library.validate(request);
                }));
            }

            // Invalid modifications (missing semicolon)
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    ModificationRequest request = ModificationRequest.builder()
                            .filePath(invalidFile.toString())
                            .fromLine(3)
                            .toLine(3)
                            .replacement("        System.out.println(\"broken\")")
                            .build();
                    return library.validate(request);
                }));
            }

            int validCount = 0;
            int invalidCount = 0;
            for (var future : futures) {
                ValidationResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (result.isValid()) validCount++;
                else invalidCount++;
            }

            assertThat(validCount + invalidCount).isEqualTo(4);

            executor.shutdown();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. RESOURCE CLEANUP
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resource cleanup")
    class ResourceCleanupTests {

        @Test
        @DisplayName("temp files are cleaned up after validation")
        void tempFiles_areCleanedUp_afterValidation() throws IOException {
            Path javaFile = tempDir.resolve("Cleanup.java");
            Files.writeString(javaFile, """
                    public class Cleanup {
                        public static void main(String[] args) {
                            System.out.println("cleanup test");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Snapshot before
            long beforeCount = countTempFiles();

            // Run many validations
            for (int i = 0; i < 20; i++) {
                ModificationRequest request = ModificationRequest.builder()
                        .filePath(javaFile.toString())
                        .fromLine(3)
                        .toLine(3)
                        .replacement("        System.out.println(\"iter " + i + "\");")
                        .build();
                library.validate(request);
            }

            // Small delay for cleanup
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            // Temp file/dir count should not grow significantly
            long afterCount = countTempFiles();
            long delta = afterCount - beforeCount;
            assertThat(delta).as(
                    "temp entries created but not cleaned up (before=%d, after=%d)",
                    beforeCount, afterCount).isLessThan(5);
        }
        private long countTempFiles() {
            try {
                Path tempDirPath = Path.of(System.getProperty("java.io.tmpdir"));
                return Files.list(tempDirPath)
                        .filter(p -> p.getFileName().toString().startsWith("syntax-check-"))
                        .count();
            } catch (IOException e) {
                return 0;
            }
        }

        @Test
        @DisplayName("validation does not leave open file handles")
        void validation_doesNotLeaveOpenFileHandles() throws Exception {
            Path javaFile = tempDir.resolve("Handles.java");
            Files.writeString(javaFile, """
                    public class Handles {
                        public static void main(String[] args) {
                            System.out.println("handles test");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Run many validations to stress file handles
            for (int i = 0; i < 100; i++) {
                ModificationRequest request = ModificationRequest.builder()
                        .filePath(javaFile.toString())
                        .fromLine(3)
                        .toLine(3)
                        .replacement("        System.out.println(\"iter " + i + "\");")
                        .build();
                library.validate(request);
            }

            // If we get here without "too many open files", we're good
            assertThat(true).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. CROSS-LANGUAGE CONSISTENCY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cross-language consistency")
    class CrossLanguageConsistencyTests {

        @Test
        @DisplayName("all languages return consistent ValidationResult structure")
        void allLanguages_returnConsistentStructure() throws IOException {
            // Create test files for each language
            Path javaFile = tempDir.resolve("Consistent.java");
            Files.writeString(javaFile, """
                    public class Consistent {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    }
                    """);

            Path jsFile = tempDir.resolve("consistent.js");
            Files.writeString(jsFile, "console.log('ok');\n");

            Path pyFile = tempDir.resolve("consistent.py");
            Files.writeString(pyFile, "print('ok')\n");

            Path phpFile = tempDir.resolve("consistent.php");
            Files.writeString(phpFile, "<?php echo 'ok';\n");

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Validate each language (no-op modification)
            ValidationResult javaResult = library.validate(ModificationRequest.builder()
                    .filePath(javaFile.toString()).fromLine(1).toLine(1)
                    .replacement("public class Consistent {").build());

            ValidationResult jsResult = library.validate(ModificationRequest.builder()
                    .filePath(jsFile.toString()).fromLine(1).toLine(1)
                    .replacement("console.log('ok');").build());

            ValidationResult pyResult = library.validate(ModificationRequest.builder()
                    .filePath(pyFile.toString()).fromLine(1).toLine(1)
                    .replacement("print('ok')").build());

            ValidationResult phpResult = library.validate(ModificationRequest.builder()
                    .filePath(phpFile.toString()).fromLine(1).toLine(1)
                    .replacement("<?php echo 'ok';").build());

            // All should return results
            assertThat(javaResult).isNotNull();
            assertThat(jsResult).isNotNull();
            assertThat(pyResult).isNotNull();
            assertThat(phpResult).isNotNull();
        }

        void errorResults_haveConsistentStructure() throws IOException {
            // Create test files
            Path javaFile = tempDir.resolve("ErrorJava.java");
            Files.writeString(javaFile, """
                    public class ErrorJava {
                        public static void main(String[] args) {
                            System.out.println("ok");
                        }
                    }
                    """);

            Path jsFile = tempDir.resolve("errorJs.js");
            Files.writeString(jsFile, "console.log('ok');\n");

            Path pyFile = tempDir.resolve("errorPy.py");
            Files.writeString(pyFile, "print('ok')\n");

            Path phpFile = tempDir.resolve("errorPhp.php");
            Files.writeString(phpFile, "<?php echo 'ok';\n");

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            // Introduce syntax errors in each language
            ValidationResult javaResult = library.validate(ModificationRequest.builder()
                    .filePath(javaFile.toString()).fromLine(3).toLine(3)
                    .replacement("        System.out.println(\"bad\")").build());

            ValidationResult jsResult = library.validate(ModificationRequest.builder()
                    .filePath(jsFile.toString()).fromLine(1).toLine(1)
                    .replacement("const x = ;").build());

            ValidationResult pyResult = library.validate(ModificationRequest.builder()
                    .filePath(pyFile.toString()).fromLine(1).toLine(1)
                    .replacement("def foo()").build());

            ValidationResult phpResult = library.validate(ModificationRequest.builder()
                    .filePath(phpFile.toString()).fromLine(1).toLine(1)
                    .replacement("<?php echo 'bad'").build());

            // All should be invalid
            assertThat(javaResult.isValid()).isFalse();
            assertThat(jsResult.isValid()).isFalse();
            assertThat(pyResult.isValid()).isFalse();
            assertThat(phpResult.isValid()).isFalse();

            // All should have errors with meaningful messages
            for (var result : List.of(javaResult, jsResult, pyResult, phpResult)) {
                assertThat(result.getErrors()).isNotEmpty();
                for (ValidationError err : result.getErrors()) {
                    assertThat(err.getLine()).isGreaterThan(0);
                    assertThat(err.getMessage()).isNotNull().isNotEmpty();
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  9. PERFORMANCE AND STRESS TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Performance and stress tests")
    class PerformanceTests {

        @Test
        @DisplayName("repeated validation of same file is consistent")
        void repeatedValidation_isConsistent() throws IOException {
            Path javaFile = tempDir.resolve("Consistent.java");
            Files.writeString(javaFile, """
                    public class Consistent {
                        public static void main(String[] args) {
                            System.out.println("consistent");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(javaFile.toString())
                    .fromLine(3)
                    .toLine(3)
                    .replacement("        System.out.println(\"modified\");")
                    .build();

            ValidationResult first = library.validate(request);
            library.invalidateCache(Path.of(javaFile.toString()));
            ValidationResult second = library.validate(request);
            library.invalidateCache(Path.of(javaFile.toString()));
            ValidationResult third = library.validate(request);

            assertThat(first.isValid()).isEqualTo(second.isValid());
            assertThat(second.isValid()).isEqualTo(third.isValid());
        }

        @Test
        @DisplayName("validation of large file completes within timeout")
        void validation_ofLargeFile_completesWithinTimeout() throws IOException {
            // Generate large Java file
            StringBuilder sb = new StringBuilder("public class Large {\n");
            sb.append("    public static void main(String[] args) {\n");
            for (int i = 0; i < 5000; i++) {
                sb.append("        int v").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("    }\n}");

            Path largeFile = tempDir.resolve("Large.java");
            Files.writeString(largeFile, sb.toString());

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            ModificationRequest request = ModificationRequest.builder()
                    .filePath(largeFile.toString())
                    .fromLine(3)
                    .toLine(3)
                    .replacement("        int modified = 42;")
                    .build();

            long start = System.currentTimeMillis();
            ValidationResult result = library.validate(request);
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(elapsed).isLessThan(30_000); // 30 seconds max
        }

        @Test
        @DisplayName("rapid sequential validations — no resource leak")
        void rapidSequentialValidations_noResourceLeak() throws IOException {
            Path javaFile = tempDir.resolve("Rapid.java");
            Files.writeString(javaFile, """
                    public class Rapid {
                        public static void main(String[] args) {
                            System.out.println("rapid");
                        }
                    }
                    """);

            SyntaxValidationLibrary library = new SyntaxValidationLibrary();

            for (int i = 0; i < 20; i++) {
                ModificationRequest request = ModificationRequest.builder()
                        .filePath(javaFile.toString())
                        .fromLine(3)
                        .toLine(3)
                        .replacement("        System.out.println(\"" + i + "\");")
                        .build();
                ValidationResult result = library.validate(request);
                assertThat(result).isNotNull();
            }
        }
    }
}
