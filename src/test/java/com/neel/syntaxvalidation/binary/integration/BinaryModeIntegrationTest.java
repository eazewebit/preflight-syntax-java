package com.neel.syntaxvalidation.binary.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for "Binary Discovery Mode" — the execution path where
 * the library locates and resolves the appropriate binary from the system
 * PATH or configured search directories.
 */
@DisplayName("Binary Discovery Mode Integration Tests")
class BinaryModeIntegrationTest {

    @TempDir
    Path tempDir;

    // ══════════════════════════════════════════════════════════════════════
    //  1. BASIC PATH DISCOVERY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Basic PATH discovery")
    class BasicPathDiscoveryTests {

        @Test
        @DisplayName("discovers javac from system PATH")
        void discoversJavac_fromSystemPath() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "javac");

            assertThat(resolved).isPresent();
            assertThat(Files.exists(Path.of(resolved.get()))).isTrue();
            assertThat(resolved.get()).containsIgnoringCase("javac");
        }

        @Test
        @DisplayName("discovers node from system PATH")
        void discoversNode_fromSystemPath() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "node");

            assertThat(resolved).isPresent();
            assertThat(Files.exists(Path.of(resolved.get()))).isTrue();
            assertThat(resolved.get()).containsIgnoringCase("node");
        }

        @Test
        @DisplayName("discovers python from system PATH")
        void discoversPython_fromSystemPath() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "python");

            assertThat(resolved).isPresent();
            assertThat(Files.exists(Path.of(resolved.get()))).isTrue();
            assertThat(resolved.get()).containsIgnoringCase("python");
        }

        @Test
        @DisplayName("discovers php from system PATH")
        void discoversPhp_fromSystemPath() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "php");

            assertThat(resolved).isPresent();
            assertThat(Files.exists(Path.of(resolved.get()))).isTrue();
            assertThat(resolved.get()).containsIgnoringCase("php");
        }

        @Test
        @DisplayName("discovers java from system PATH")
        void discoversJava_fromSystemPath() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "java");

            assertThat(resolved).isPresent();
            assertThat(Files.exists(Path.of(resolved.get()))).isTrue();
            assertThat(resolved.get()).containsIgnoringCase("java");
        }

        @Test
        @DisplayName("returns empty for non-existent binary")
        void returnsEmpty_forNonExistentBinary() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "nonexistent_binary_xyz_98765");

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("returns empty for empty binary name")
        void returnsEmpty_forEmptyBinaryName() {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(null, "");

            assertThat(resolved).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. LANGUAGE ENUM EXTENSION RESOLUTION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Language enum extension resolution")
    class LanguageEnumExtensionTests {

        @Test
        @DisplayName("each language's extensions are correctly defined")
        void eachLanguage_extensionsCorrectlyDefined() {
            assertThat(Language.JAVA.getExtensions()).contains("java");
            assertThat(Language.JAVASCRIPT.getExtensions()).contains("js", "mjs", "cjs");
            assertThat(Language.PYTHON.getExtensions()).contains("py");
            assertThat(Language.PHP.getExtensions()).contains("php", "phtml", "phps");
            assertThat(Language.HTML.getExtensions()).contains("html", "htm");
            assertThat(Language.CSS.getExtensions()).contains("css");
        }

        @Test
        @DisplayName("Language.fromExtension resolves correctly")
        void languageFromExtension_resolvesCorrectly() {
            assertThat(Language.fromExtension("java")).contains(Language.JAVA);
            assertThat(Language.fromExtension("js")).contains(Language.JAVASCRIPT);
            assertThat(Language.fromExtension("py")).contains(Language.PYTHON);
            assertThat(Language.fromExtension("php")).contains(Language.PHP);
            assertThat(Language.fromExtension("html")).contains(Language.HTML);
            assertThat(Language.fromExtension("css")).contains(Language.CSS);
        }

        @Test
        @DisplayName("Language.fromExtension handles dotted extensions")
        void languageFromExtension_handlesDottedExtensions() {
            assertThat(Language.fromExtension(".java")).contains(Language.JAVA);
            assertThat(Language.fromExtension(".js")).contains(Language.JAVASCRIPT);
            assertThat(Language.fromExtension(".py")).contains(Language.PYTHON);
        }

        @Test
        @DisplayName("Language.fromExtension handles case insensitivity")
        void languageFromExtension_handlesCaseInsensitivity() {
            assertThat(Language.fromExtension("JAVA")).contains(Language.JAVA);
            assertThat(Language.fromExtension("Java")).contains(Language.JAVA);
            assertThat(Language.fromExtension("JS")).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("Language.fromExtension returns empty for unknown extension")
        void languageFromExtension_returnsEmpty_forUnknown() {
            assertThat(Language.fromExtension("xyz")).isEmpty();
            assertThat(Language.fromExtension("")).isEmpty();
            assertThat(Language.fromExtension(null)).isEmpty();
        }

        @Test
        @DisplayName("Language.fromPath resolves correctly")
        void languageFromPath_resolvesCorrectly() {
            assertThat(Language.fromPath(Path.of("test.java"))).contains(Language.JAVA);
            assertThat(Language.fromPath(Path.of("test.js"))).contains(Language.JAVASCRIPT);
            assertThat(Language.fromPath(Path.of("test.py"))).contains(Language.PYTHON);
            assertThat(Language.fromPath(Path.of("test.php"))).contains(Language.PHP);
            assertThat(Language.fromPath(Path.of("test.html"))).contains(Language.HTML);
            assertThat(Language.fromPath(Path.of("test.css"))).contains(Language.CSS);
        }

        @Test
        @DisplayName("Language.fromPath handles full paths")
        void languageFromPath_handlesFullPaths() {
            assertThat(Language.fromPath(Path.of("/home/user/project/Main.java"))).contains(Language.JAVA);
            assertThat(Language.fromPath(Path.of("C:\\Users\\test\\app.js"))).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("Language.fromPath returns empty for no extension")
        void languageFromPath_returnsEmpty_forNoExtension() {
            assertThat(Language.fromPath(Path.of("Makefile"))).isEmpty();
            assertThat(Language.fromPath(Path.of(""))).isEmpty();
            assertThat(Language.fromPath(null)).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. CUSTOM SEARCH DIRECTORY DISCOVERY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Custom search directory discovery")
    class CustomSearchDirectoryTests {

        @Test
        @DisplayName("discovers binary from explicit path")
        void discoversBinary_fromExplicitPath() throws IOException {
            // Create a mock binary in tempDir
            Path mockBinary = tempDir.resolve("mytool.exe");
            Files.writeString(mockBinary, "fake binary");

            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(mockBinary.toString(), "mytool");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(mockBinary.toString());
        }

        @Test
        @DisplayName("explicit path takes precedence over PATH")
        void explicitPath_takesPrecedence_overPath() throws IOException {
            // Create a fake binary in tempDir
            Path customBinary = tempDir.resolve("node");
            Files.writeString(customBinary, "fake node");

            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(customBinary.toString(), "node");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(customBinary.toString());
        }

        @Test
        @DisplayName("explicit path to non-existent file returns empty")
        void explicitPath_toNonExistentFile_returnsEmpty() {
            Path nonExistent = tempDir.resolve("nonexistent_binary");

            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(nonExistent.toString(), "nonexistent");

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("path with spaces is handled correctly")
        void pathWithSpaces_handledCorrectly() throws IOException {
            Path spaceDir = tempDir.resolve("dir with spaces");
            Files.createDirectories(spaceDir);
            Path binaryInSpaceDir = spaceDir.resolve("tool.exe");
            Files.writeString(binaryInSpaceDir, "fake");

            BinaryResolver resolver = new BinaryResolver();
            Optional<String> resolved = resolver.resolve(binaryInSpaceDir.toString(), "tool");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).contains("dir with spaces");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. DISCOVERY WITH VALIDATOR INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Discovery with validator integration")
    class DiscoveryWithValidatorIntegrationTests {

        @Test
        @DisplayName("JavaValidator with discovery validates code")
        void javaValidator_withDiscovery_validatesCode() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.JAVA);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    public class Discovery {
                        public static void main(String[] args) {
                            System.out.println("Discovered javac works!");
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JavaScriptValidator with discovery validates code")
        void javaScriptValidator_withDiscovery_validatesCode() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.JAVASCRIPT);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    const msg = 'Discovered node works!';
                    console.log(msg);
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("PythonValidator with discovery validates code")
        void pythonValidator_withDiscovery_validatesCode() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.PYTHON);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    msg = "Discovered python works!"
                    print(msg)
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("PhpValidator with discovery validates code")
        void phpValidator_withDiscovery_validatesCode() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.PHP);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = "<?php echo 'Discovered php works!';\n";
            ValidationResult result = validator.validate(code);

            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JavaValidator with discovery catches syntax errors")
        void javaValidator_withDiscovery_catchesSyntaxErrors() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.JAVA);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    public class Bad {
                        public static void main(String[] args) {
                            System.out.println("missing semi")
                        }
                    }
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("JavaScriptValidator with discovery catches syntax errors")
        void javaScriptValidator_withDiscovery_catchesSyntaxErrors() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.JAVASCRIPT);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    const x = ;
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("PythonValidator with discovery catches syntax errors")
        void pythonValidator_withDiscovery_catchesSyntaxErrors() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.PYTHON);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = """
                    def foo()
                        print("bad")
                    """;
            ValidationResult result = validator.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("PhpValidator with discovery catches syntax errors")
        void phpValidator_withDiscovery_catchesSyntaxErrors() {
            ValidatorFactory factory = new ValidatorFactory();
            var validatorOpt = factory.getValidator(Language.PHP);
            assertThat(validatorOpt).isPresent();

            LanguageValidator validator = validatorOpt.get();
            String code = "<?php echo 'missing semi'\necho 'another line';\n";
            ValidationResult result = validator.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. BINARY VERSION DETECTION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Binary version detection and compatibility")
    class BinaryVersionTests {

        @Test
        @DisplayName("discovered javac is executable and returns version")
        void discoveredJavac_isExecutableAndReturnsVersion() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> javacPath = resolver.resolve(null, "javac");

            assertThat(javacPath).isPresent();

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(java.util.List.of(javacPath.get(), "--version"));

            assertThat(result.exitCode()).isEqualTo(0);
            assertThat(result.stdout()).containsIgnoringCase("javac");
        }

        @Test
        @DisplayName("discovered node is executable and returns version")
        void discoveredNode_isExecutableAndReturnsVersion() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> nodePath = resolver.resolve(null, "node");

            assertThat(nodePath).isPresent();

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(java.util.List.of(nodePath.get(), "--version"));

            assertThat(result.exitCode()).isEqualTo(0);
            assertThat(result.stdout()).containsIgnoringCase("v");
        }

        @Test
        @DisplayName("discovered python is executable and returns version")
        void discoveredPython_isExecutableAndReturnsVersion() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> pythonPath = resolver.resolve(null, "python");

            assertThat(pythonPath).isPresent();

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(java.util.List.of(pythonPath.get(), "--version"));

            assertThat(result.exitCode()).isEqualTo(0);
            assertThat(result.stdout()).containsIgnoringCase("python");
        }

        @Test
        @DisplayName("discovered php is executable and returns version")
        void discoveredPhp_isExecutableAndReturnsVersion() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> phpPath = resolver.resolve(null, "php");

            assertThat(phpPath).isPresent();

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(java.util.List.of(phpPath.get(), "--version"));

            assertThat(result.exitCode()).isEqualTo(0);
            assertThat(result.stdout()).containsIgnoringCase("php");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. VALIDATOR FACTORY WITH DISCOVERY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidatorFactory with binary discovery")
    class ValidatorFactoryDiscoveryTests {

        @Test
        @DisplayName("factory provides validators for all supported languages")
        void factoryProvidesValidators_forAllSupportedLanguages() {
            ValidatorFactory factory = new ValidatorFactory();

            // These are the languages that have validators registered by default
            Language[] supported = {Language.JAVA, Language.JAVASCRIPT, Language.PYTHON, Language.PHP};
            for (Language lang : supported) {
                var validator = factory.getValidator(lang);
                assertThat(validator)
                        .as("Validator for %s", lang)
                        .isPresent();
            }
        }

        @Test
        @DisplayName("factory-created JavaValidator works with discovery")
        void factoryCreatedJavaValidator_works() {
            ValidatorFactory factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVA);
            assertThat(validator).isPresent();

            String code = "public class T { public static void main(String[] a) { } }";
            ValidationResult result = validator.get().validate(code);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("factory-created JavaScriptValidator works with discovery")
        void factoryCreatedJavaScriptValidator_works() {
            ValidatorFactory factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVASCRIPT);
            assertThat(validator).isPresent();

            String code = "console.log('test');";
            ValidationResult result = validator.get().validate(code);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("factory-created PythonValidator works with discovery")
        void factoryCreatedPythonValidator_works() {
            ValidatorFactory factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.PYTHON);
            assertThat(validator).isPresent();

            String code = "print('test')";
            ValidationResult result = validator.get().validate(code);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("factory-created PhpValidator works with discovery")
        void factoryCreatedPhpValidator_works() {
            ValidatorFactory factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.PHP);
            assertThat(validator).isPresent();

            String code = "<?php echo 'test';\n";
            ValidationResult result = validator.get().validate(code);

            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. CONCURRENT DISCOVERY SAFETY
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrent discovery safety")
    class ConcurrentDiscoveryTests {

        @Test
        @DisplayName("concurrent binary resolution is thread-safe")
        void concurrentBinaryResolution_isThreadSafe() throws Exception {
            var executor = java.util.concurrent.Executors.newFixedThreadPool(8);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Optional<String>>>();

            for (int i = 0; i < 50; i++) {
                futures.add(executor.submit(() -> {
                    BinaryResolver resolver = new BinaryResolver();
                    return resolver.resolve(null, "javac");
                }));
            }

            for (var future : futures) {
                Optional<String> result = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(result).isPresent();
            }

            executor.shutdown();
        }

        @Test
        @DisplayName("concurrent validation with discovery is safe")
        void concurrentValidation_withDiscovery_isSafe() throws Exception {
            var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<ValidationResult>>();

            String code = "console.log('concurrent test');";
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ValidatorFactory factory = new ValidatorFactory();
                    var validator = factory.getValidator(Language.JAVASCRIPT).orElseThrow();
                    return validator.validate(code);
                }));
            }

            for (var future : futures) {
                ValidationResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(result).isNotNull();
            }

            executor.shutdown();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. EDGE CASES AND ERROR SCENARIOS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases and error scenarios")
    class EdgeCaseTests {

        @Test
        @DisplayName("discovery with null binary name")
        void discovery_withNullBinaryName() {
            BinaryResolver resolver = new BinaryResolver();

            // Null binary name should return empty or throw
            Optional<String> result = resolver.resolve(null, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("discovery with very long binary name")
        void discovery_withVeryLongBinaryName() {
            BinaryResolver resolver = new BinaryResolver();
            String longName = "a".repeat(10_000);
            Optional<String> resolved = resolver.resolve(null, longName);

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("discovery with binary name containing special chars")
        void discovery_withSpecialChars() {
            BinaryResolver resolver = new BinaryResolver();

            // Special characters in binary name should not crash
            assertThat(resolver.resolve(null, "node.exe")).isNotNull();
            assertThat(resolver.resolve(null, "./node")).isNotNull();
            assertThat(resolver.resolve(null, "../node")).isNotNull();
        }

        @Test
        @DisplayName("multiple resolver instances share no state")
        void multipleResolverInstances_shareNoState() throws IOException {
            Path customBinary = tempDir.resolve("custom");
            Files.writeString(customBinary, "fake");

            BinaryResolver resolver1 = new BinaryResolver();
            BinaryResolver resolver2 = new BinaryResolver();

            Optional<String> resolved1 = resolver1.resolve(customBinary.toString(), "custom");
            Optional<String> resolved2 = resolver2.resolve(null, "javac");

            // They should be independent
            assertThat(resolved1).isPresent();
            assertThat(resolved1.get()).isEqualTo(customBinary.toString());

            assertThat(resolved2).isPresent();
            assertThat(resolved2.get()).doesNotContain("custom");
        }

        @Test
        @DisplayName("resolve with executable extension on Windows")
        void resolve_withExecutableExtension() {
            BinaryResolver resolver = new BinaryResolver();

            // On Windows, .exe extension should be resolved
            Optional<String> resolved = resolver.resolve(null, "node");
            assertThat(resolved).isPresent();

            // The resolved path should point to a real file
            Path path = Path.of(resolved.get());
            assertThat(Files.exists(path)).isTrue();
            assertThat(Files.isExecutable(path)).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  9. PROCESS EXECUTION WITH DISCOVERED BINARIES
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Process execution with discovered binaries")
    class ProcessExecutionWithDiscoveryTests {

        @Test
        @DisplayName("discovered javac can compile simple Java file")
        void discoveredJavac_canCompileSimpleJavaFile() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> javacPath = resolver.resolve(null, "javac");
            assertThat(javacPath).isPresent();

            // Write a Java file
            Path javaFile = tempDir.resolve("Hello.java");
            Files.writeString(javaFile, """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello!");
                        }
                    }
                    """);

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(
                    java.util.List.of(javacPath.get(), javaFile.toString()));

            assertThat(result.exitCode()).isEqualTo(0);
            // .class file should be created
            assertThat(Files.exists(tempDir.resolve("Hello.class"))).isTrue();
        }

        @Test
        @DisplayName("discovered node can check JavaScript syntax")
        void discoveredNode_canCheckJavaScriptSyntax() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> nodePath = resolver.resolve(null, "node");
            assertThat(nodePath).isPresent();

            // Write a JS file with syntax error
            Path jsFile = tempDir.resolve("bad.js");
            Files.writeString(jsFile, "const x = ;");

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(
                    java.util.List.of(nodePath.get(), "--check", jsFile.toString()));

            assertThat(result.exitCode()).isNotEqualTo(0);
            assertThat(result.stderr()).isNotEmpty();
        }

        @Test
        @DisplayName("discovered python can check Python syntax")
        void discoveredPython_canCheckPythonSyntax() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> pythonPath = resolver.resolve(null, "python");
            assertThat(pythonPath).isPresent();

            // Write a valid Python file
            Path pyFile = tempDir.resolve("good.py");
            Files.writeString(pyFile, "print('hello')");

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(
                    java.util.List.of(pythonPath.get(), "-m", "py_compile", pyFile.toString()));

            assertThat(result.exitCode()).isEqualTo(0);
        }

        @Test
        @DisplayName("discovered php can lint PHP file")
        void discoveredPhp_canLintPhpFile() throws Exception {
            BinaryResolver resolver = new BinaryResolver();
            Optional<String> phpPath = resolver.resolve(null, "php");
            assertThat(phpPath).isPresent();

            // Write a valid PHP file
            Path phpFile = tempDir.resolve("good.php");
            Files.writeString(phpFile, "<?php echo 'hello';");

            ProcessExecutor executor = new ProcessExecutor();
            var result = executor.execute(
                    java.util.List.of(phpPath.get(), "-l", phpFile.toString()));

            assertThat(result.exitCode()).isEqualTo(0);
        }
    }
}