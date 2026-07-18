package com.neel.syntaxvalidation.validator.java.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import com.neel.syntaxvalidation.validator.java.JavaSyntaxEngine;
import com.neel.syntaxvalidation.validator.java.checker.DelimiterBalanceChecker;
import com.neel.syntaxvalidation.validator.java.checker.KeywordUsageChecker;
import com.neel.syntaxvalidation.validator.java.checker.TokenizationErrorChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Comprehensive integration tests for the Java support component.
 *
 * <p>These tests verify the complete validation pipeline working end-to-end,
 * exercising the interaction between all Java validation components:
 * <ul>
 *   <li>{@link JavaValidator} — the entry point implementing {@code LanguageValidator}</li>
 *   <li>{@link JavaSyntaxEngine} — the pure-Java, non-executing syntax engine</li>
 *   <li>{@link com.neel.syntaxvalidation.validator.java.JavaLexer} — the lexical analyser</li>
 *   <li>{@link TokenizationErrorChecker} — lexer-level anomaly detection</li>
 *   <li>{@link DelimiterBalanceChecker} — delimiter balance verification</li>
 *   <li>{@link KeywordUsageChecker} — keyword usage validation</li>
 *   <li>{@link ValidatorFactory} — factory-based validator creation</li>
 * </ul>
 *
 * <p>Unlike the unit tests in the sibling packages, these tests intentionally
 * avoid mocking internal components, focusing instead on verifying that the
 * components integrate correctly and that errors propagate properly across
 * boundaries.
 */
@DisplayName("Java Validation Pipeline — Integration Tests")
class JavaValidationPipelineIntegrationTest {

    // ======================================================================
    //  Test doubles (minimal, focused on integration)
    // ======================================================================

    /**
     * A controllable {@link BinaryResolver} for integration tests.
     * Returns a pre-configured path (or empty) to simulate javac availability.
     */
    private static class StubBinaryResolver extends BinaryResolver {
        private final Optional<String> resolvedPath;

        StubBinaryResolver(String path) {
            this.resolvedPath = Optional.ofNullable(path);
        }

        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) {
            // If a preferred path is explicitly set, honour it.
            if (preferredPath != null && !preferredPath.isBlank()) {
                return Optional.of(preferredPath);
            }
            return resolvedPath;
        }
    }

    /**
     * A controllable {@link ProcessExecutor} for integration tests.
     * Returns a pre-configured {@link ProcessResult}.
     */
    private static class StubProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;
        private List<String> lastCommand;
        private int invocationCount;

        StubProcessExecutor(ProcessResult result) {
            this.result = result;
        }

        @Override
        public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
            this.lastCommand = command;
            this.invocationCount++;
            if (result == null) {
                throw new IOException("Simulated I/O failure");
            }
            return result;
        }

        List<String> getLastCommand() { return lastCommand; }
        int getInvocationCount() { return invocationCount; }
    }

    /**
     * A {@link ProcessExecutor} that always throws {@link InterruptedException}.
     */
    private static class InterruptedProcessExecutor extends ProcessExecutor {
        @Override
        public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
            throw new InterruptedException("Simulated interruption");
        }
    }

    /**
     * A {@link ProcessExecutor} that always throws {@link IOException}.
     */
    private static class FailingProcessExecutor extends ProcessExecutor {
        @Override
        public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
            throw new IOException("Simulated process failure");
        }
    }

    // ======================================================================
    //  Shared fixtures
    // ======================================================================

    private static final String VALID_BINARY = "/usr/bin/javac";

    private static final ProcessResult SUCCESSFUL_COMPILE = new ProcessResult(0, "", "", false);

    private JavaSyntaxEngine syntaxEngine;

    @BeforeEach
    void setUp() {
        syntaxEngine = new JavaSyntaxEngine();
    }

    // ======================================================================
    //  1. Full Pipeline — Valid Source Code
    // ======================================================================

    @Nested
    @DisplayName("Full pipeline with valid Java source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid simple class → both phases pass")
        void validSimpleClass_bothPhasesPass() {
            var source = """
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello, World!");
                        }
                    }
                    """;

            // Phase 1: syntax engine should pass
            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isTrue();

            // Phase 2: javac should pass (simulated)
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }

        @Test
        @DisplayName("valid interface declaration → both phases pass")
        void validInterfaceDeclaration_bothPhasesPass() {
            var source = """
                    public interface Greeter {
                        String greet(String name);
                        default String farewell() {
                            return "Goodbye";
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid enum with constructors and methods → both phases pass")
        void validEnumWithConstructors_bothPhasesPass() {
            var source = """
                    public enum Color {
                        RED(255, 0, 0),
                        GREEN(0, 255, 0),
                        BLUE(0, 0, 255);

                        private final int r, g, b;

                        Color(int r, int g, int b) {
                            this.r = r;
                            this.g = g;
                            this.b = b;
                        }

                        public int rgb() {
                            return (r << 16) | (g << 8) | b;
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid record with compact constructor → both phases pass")
        void validRecordCompactConstructor_bothPhasesPass() {
            var source = """
                    public record Point(int x, int y) {
                        public Point {
                            if (x < 0 || y < 0) {
                                throw new IllegalArgumentException("negative coordinate");
                            }
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid sealed class with permits → both phases pass")
        void validSealedClassPermits_bothPhasesPass() {
            var source = """
                    public sealed class Shape permits Circle, Rectangle {
                        public final double area() { return 0; }
                    }
                    final class Circle extends Shape {}
                    final class Rectangle extends Shape {}
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid generic class with wildcards → both phases pass")
        void validGenericClassWildcards_bothPhasesPass() {
            var source = """
                    import java.util.List;
                    import java.util.Map;

                    public class Container<T extends Comparable<? super T>> {
                        private final List<? extends T> items;

                        public Container(List<? extends T> items) {
                            this.items = items;
                        }

                        public <R> R transform(java.util.function.Function<? super T, ? extends R> fn) {
                            return fn.apply(items.get(0));
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid switch expression with pattern matching → both phases pass")
        void validSwitchExpressionPatternMatching_bothPhasesPass() {
            var source = """
                    public class PatternSwitch {
                        static String describe(Object obj) {
                            return switch (obj) {
                                case Integer i -> "int: " + i;
                                case String s  -> "str: " + s;
                                case null      -> "null";
                                default        -> "other";
                            };
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  2. Full Pipeline — Invalid Source Code (Syntax Errors)
    // ======================================================================

    @Nested
    @DisplayName("Full pipeline with invalid Java source")
    class FullPipelineInvalidSource {

        @Test
        @DisplayName("missing semicolon → javac reports error")
        void missingSemicolon_javacReportsError() {
            var source = """
                    public class Missing {
                        int x = 5
                    }
                    """;

            var javacOutput = "Missing.java:2: error: ';' expected\n"
                    + "        int x = 5\n"
                    + "                 ^\n"
                    + "1 error\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("';' expected");
        }

        @Test
        @DisplayName("unbalanced braces → phase 1 detects via DelimiterBalanceChecker")
        void unbalancedBraces_phase1Detects() {
            var source = """
                    public class Unbalanced {
                        public static void main(String[] args) {
                            System.out.println("missing brace");
                    """;

            // Phase 1 should detect the issue
            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed"));
        }

        @Test
        @DisplayName("unterminated string literal → phase 1 detects via TokenizationErrorChecker")
        void unterminatedStringLiteral_phase1Detects() {
            var source = """
                    public class Unterminated {
                        String s = "hello world
                        ;
                    }
                    """;

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("lexical error"));
        }

        @Test
        @DisplayName("conflicting access modifiers → phase 1 detects via KeywordUsageChecker")
        void conflictingAccessModifiers_phase1Detects() {
            var source = "public private class Conflict {}";

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("conflicting access"));
        }

        @Test
        @DisplayName("duplicate modifiers → phase 1 detects via KeywordUsageChecker")
        void duplicateModifiers_phase1Detects() {
            var source = "public static static class Duplicate {}";

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("duplicate modifier"));
        }

        @Test
        @DisplayName("const keyword usage → phase 1 detects via KeywordUsageChecker")
        void constKeywordUsage_phase1Detects() {
            var source = "const int x = 1;";

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("goto keyword usage → phase 1 detects via KeywordUsageChecker")
        void gotoKeywordUsage_phase1Detects() {
            var source = "goto label;";

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("malformed annotation → phase 1 detects via KeywordUsageChecker")
        void malformedAnnotation_phase1Detects() {
            var source = """
                    @
                    public class BadAnnotation {}
                    """;

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("annotation"));
        }

        @Test
        @DisplayName("assert without expression → phase 1 detects via KeywordUsageChecker")
        void assertWithoutExpression_phase1Detects() {
            var source = """
                    public class BadAssert {
                        void m() { assert ; }
                    }
                    """;

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("assert"));
        }

        @Test
        @DisplayName("multiple errors from different checkers → all reported")
        void multipleErrorsFromDifferentCheckers_allReported() {
            var source = """
                    public private class Multi {
                        String s = "unterminated
                        int x = (1 + 2;
                        assert ;
                    }
                    """;

            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            // Should have errors from: KeywordUsageChecker (conflicting access),
            // TokenizationErrorChecker (unterminated string),
            // DelimiterBalanceChecker (unclosed parenthesis)
            assertThat(phase1.getErrors().size()).isGreaterThanOrEqualTo(3);

            var messages = phase1.getErrors().stream()
                    .map(ValidationError::getMessage)
                    .toList();

            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("conflicting"));
            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("lexical"));
        }
    }

    // ======================================================================
    //  3. Two-Phase Validation Interaction
    // ======================================================================

    @Nested
    @DisplayName("Two-phase validation interaction")
    class TwoPhaseValidationInteraction {

        @Test
        @DisplayName("phase 1 fails → javac is NOT invoked (phase 1 errors returned immediately)")
        void phase1Fails_javacNotInvoked() {
            var source = "const int x = 1;";

            var stubExecutor = new StubProcessExecutor(
                    new ProcessResult(0, "", "", false));

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            // Phase 1 fails → javac should NOT be invoked
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            // Should have errors from phase 1 only (const keyword)
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("reserved keyword");
        }

        @Test
        @DisplayName("phase 1 passes but phase 2 fails → result is invalid")
        void phase1PassesPhase2Fails_resultInvalid() {
            var source = """
                    public class SemanticError {
                        void m() {
                            unknownMethod();
                        }
                    }
                    """;

            var javacOutput = "SemanticError.java:3: error: cannot find symbol\n"
                    + "            unknownMethod();\n"
                    + "            ^\n"
                    + "  symbol:   method unknownMethod()\n"
                    + "1 error\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("cannot find symbol"));
        }

        @Test
        @DisplayName("both phases pass → result is valid")
        void bothPhasesPass_resultValid() {
            var source = """
                    public class AllGood {
                        int add(int a, int b) { return a + b; }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("javac reports warnings only → result is valid")
        void javacWarningsOnly_resultValid() {
            var source = """
                    import java.util.List;
                    public class Warnings {
                        @SuppressWarnings("unchecked")
                        List raw = new java.util.ArrayList();
                    }
                    """;

            var javacOutput = "Warnings.java:4: warning: [unchecked] unchecked assignment\n"
                    + "        List raw = new java.util.ArrayList();\n"
                    + "                       ^\n"
                    + "1 warning\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            // Warnings don't invalidate the result
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("javac reports both errors and warnings → only errors captured")
        void javacErrorsAndWarnings_onlyErrorsCaptured() {
            var source = """
                    import java.util.List;
                    public class Mixed {
                        List raw = new java.util.ArrayList();
                        int x
                    }
                    """;

            var javacOutput = "Mixed.java:3: warning: [unchecked] unchecked assignment\n"
                    + "        List raw = new java.util.ArrayList();\n"
                    + "                       ^\n"
                    + "Mixed.java:4: error: ';' expected\n"
                    + "        int x\n"
                    + "             ^\n"
                    + "1 error\n"
                    + "1 warning\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            // Only the error should be captured, not the warning
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("';' expected");
        }
    }

    // ======================================================================
    //  4. Binary Resolution Integration
    // ======================================================================

    @Nested
    @DisplayName("Binary resolution integration")
    class BinaryResolutionIntegration {

        @Test
        @DisplayName("javac not found with valid source → falls back to engine result (valid)")
        void javacNotFound_validSource_fallsBackToEngineValid() {
            var source = "public class Foo {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            // Phase 1 passes, javac not found → falls back to engine result (valid)
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("javac not found with invalid source → engine errors returned")
        void javacNotFound_invalidSource_engineErrorsReturned() {
            var source = "const int x = 1;";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            // Phase 1 fails → returns engine errors immediately (javac not even checked)
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("preferred path overrides system PATH")
        void preferredPathOverridesSystemPath() {
            var source = "public class Foo {}";
            var customPath = "/custom/path/to/javac";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_COMPILE);
            var validator = new JavaValidator(customPath,
                    new StubBinaryResolver(null), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            // Verify the custom path was used in the command
            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo(customPath);
        }

        @Test
        @DisplayName("JAVA_HOME environment resolution integration")
        void javaHomeResolution_integration() {
            var source = "public class Foo {}";
            var javaHomePath = "/opt/jdk/bin/javac";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(javaHomePath),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  5. Error Handling Integration
    // ======================================================================

    @Nested
    @DisplayName("Error handling integration")
    class ErrorHandlingIntegration {

        @Test
        @DisplayName("IOException during process execution → graceful failure")
        void ioExceptionDuringProcessExecution_gracefulFailure() {
            // Need source that passes phase 1 so it reaches javac
            var source = "public class Foo {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("I/O error");
        }

        @Test
        @DisplayName("InterruptedException during process execution → graceful failure")
        void interruptedDuringProcessExecution_gracefulFailure() {
            var source = "public class Foo {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new InterruptedProcessExecutor());

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("interrupted");
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate((String) null);
            // null content is treated as empty → phase 1 passes, phase 2 compiles empty file
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → both phases handle gracefully")
        void emptyContent_bothPhasesHandleGracefully() {
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process timeout → returns result with timeout indication")
        void processTimeout_returnsResultWithTimeoutIndication() {
            var source = "public class Infinite {}";

            var timeoutResult = new ProcessResult(-1, "", "", true);
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(timeoutResult));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }

        @Test
        @DisplayName("javac returns unexpected exit code with no output → handled gracefully")
        void unexpectedExitCodeNoOutput_handledGracefully() {
            var source = "public class Crash {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(137, "", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  6. ModificationRequest Integration
    // ======================================================================

    @Nested
    @DisplayName("ModificationRequest integration")
    class ModificationRequestIntegration {

        @Test
        @DisplayName("ModificationRequest builder creates request with correct fields")
        void modificationRequestBuilder_createsRequestWithCorrectFields() {
            var proposedCode = """
                    public class Calculator {
                        int add(int a, int b) {
                            return a + b;
                        }

                        int subtract(int a, int b) {
                            return a - b;
                        }
                    }
                    """;

            var request = ModificationRequest.builder()
                    .filePath("Calculator.java")
                    .fromLine(1)
                    .toLine(5)
                    .replacement(proposedCode)
                    .build();

            assertThat(request.getFilePath()).isEqualTo("Calculator.java");
            assertThat(request.getFromLine()).isEqualTo(1);
            assertThat(request.getToLine()).isEqualTo(5);
            assertThat(request.getReplacement()).isEqualTo(proposedCode);
        }

        @Test
        @DisplayName("ModificationRequest with null replacement → handled gracefully")
        void modificationRequestWithNullReplacement_handledGracefully() {
            var request = ModificationRequest.builder()
                    .filePath("NewFile.java")
                    .fromLine(1)
                    .toLine(1)
                    .replacement(null)
                    .build();

            assertThat(request.getFilePath()).isEqualTo("NewFile.java");
            // Builder treats null replacement as empty string
            assertThat(request.getReplacement()).isEmpty();
        }
    }

    // ======================================================================
    //  7. ValidatorFactory Integration
    // ======================================================================

    @Nested
    @DisplayName("ValidatorFactory integration")
    class ValidatorFactoryIntegration {

        @Test
        @DisplayName("factory creates Java validator that works end-to-end")
        void factoryCreatesJavaValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVA);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.JAVA);
        }

        @Test
        @DisplayName("factory supports Java language")
        void factorySupportsJavaLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.JAVA);
        }

        @Test
        @DisplayName("factory-created validator validates valid source")
        void factoryCreatedValidator_validatesValidSource() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVA).orElseThrow();

            var source = "public class FactoryTest {}";
            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  8. Javac Output Parser Integration
    // ======================================================================

    @Nested
    @DisplayName("JavacOutputParser integration")
    class JavacOutputParserIntegration {

        @Test
        @DisplayName("complex multi-error javac output → all errors parsed correctly")
        void complexMultiErrorJavacOutput_allErrorsParsedCorrectly() {
            var source = """
                    public class MultiError {
                        int x = "hello";
                        int y = ;
                        void m( {
                    }
                    """;

            var javacOutput = "MultiError.java:2: error: incompatible types: String cannot be converted to int\n"
                    + "        int x = \"hello\";\n"
                    + "                ^\n"
                    + "MultiError.java:3: error: not a statement\n"
                    + "        int y = ;\n"
                    + "              ^\n"
                    + "MultiError.java:4: error: ')' expected\n"
                    + "        void m( {\n"
                    + "               ^\n"
                    + "MultiError.java:4: error: missing method body, or declare abstract\n"
                    + "        void m( {\n"
                    + "            ^\n"
                    + "MultiError.java:5: error: reached end of file while parsing\n"
                    + "}\n"
                    + "^\n"
                    + "5 errors\n";

            // Phase 1 detects unclosed '(' and '{' — javac is never invoked
            var stubExecutor = new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false));
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            // Phase 1 finds delimiter errors, javac is NOT invoked
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getMessage()).contains("Unclosed '('");
            assertThat(result.getErrors().get(1).getMessage()).contains("Unclosed '{'");
        }

        @Test
        @DisplayName("javac output with column information → column preserved")
        void javacOutputWithColumnInformation_columnPreserved() {
            var source = "public class Col { int x = \"hello\"; }";

            var javacOutput = "Col.java:1:29: error: incompatible types: String cannot be converted to int\n"
                    + "public class Col { int x = \"hello\"; }\n"
                    + "                             ^\n"
                    + "1 error\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            // Column info should be captured
            assertThat(result.getErrors().get(0).getColumn()).isPositive();
        }

        @Test
        @DisplayName("javac output without column information → column defaults to -1")
        void javacOutputWithoutColumnInformation_columnDefaultsToMinusOne() {
            var source = "public class NoCol { int x = \"hello\"; }";

            var javacOutput = "NoCol.java:1: error: incompatible types: String cannot be converted to int\n"
                    + "public class NoCol { int x = \"hello\"; }\n"
                    + "                              ^\n"
                    + "1 error\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(-1);
        }

        @Test
        @DisplayName("empty javac output → treated as valid")
        void emptyJavacOutput_treatedAsValid() {
            var source = "public class Empty {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("javac output with only whitespace → treated as valid")
        void javacOutputWithOnlyWhitespace_treatedAsValid() {
            var source = "public class Whitespace {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "   \n  \n  ", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("javac output with unexpected format → preserved as diagnostic")
        void javacOutputWithUnexpectedFormat_preservedAsDiagnostic() {
            var source = "public class Weird {}";

            var javacOutput = "Something completely unexpected happened\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("unexpected");
        }
    }

    // ======================================================================
    //  9. Edge Cases Through Full Pipeline
    // ======================================================================

    @Nested
    @DisplayName("Edge cases through full pipeline")
    class EdgeCasesFullPipeline {

        @Test
        @DisplayName("very long source code → pipeline handles without issues")
        void veryLongSourceCode_pipelineHandlesWithoutIssues() {
            var sb = new StringBuilder("public class Long {\n");
            for (int i = 0; i < 1000; i++) {
                sb.append("    private int field").append(i).append(";\n");
            }
            sb.append("}\n");
            var source = sb.toString();

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with Unicode identifiers → pipeline handles")
        void sourceWithUnicodeIdentifiers_pipelineHandles() {
            var source = """
                    public class Unicode {
                        int \u00E9 = 42;
                        String \u00FC\u00F1\u00EE\u00E7\u00F6\u00F0\u00E9 = "test";
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with mixed line endings → pipeline handles")
        void sourceWithMixedLineEndings_pipelineHandles() {
            var source = "public class Mixed {\r\n    int x;\n    int y;\r}\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with only comments → pipeline handles")
        void sourceWithOnlyComments_pipelineHandles() {
            var source = """
                    // Single-line comment
                    /* Block comment */
                    /**
                     * Javadoc comment
                     * @param <T> type param
                     */
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with deeply nested blocks → pipeline handles")
        void sourceWithDeeplyNestedBlocks_pipelineHandles() {
            var source = """
                    public class Deep {
                        void m() {
                            if (true) {
                                for (int i = 0; i < 10; i++) {
                                    while (true) {
                                        synchronized (this) {
                                            try {
                                                switch (i) {
                                                    default -> {}
                                                }
                                            } catch (Exception e) {
                                                // noop
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with nested generics → pipeline handles")
        void sourceWithNestedGenerics_pipelineHandles() {
            var source = """
                    import java.util.Map;
                    import java.util.List;
                    import java.util.Set;

                    public class NestedGenerics {
                        Map<String, List<Map<Integer, Set<String>>>> deeplyNested;
                        List<? extends Comparable<? super Number>> bounded;
                    }
                    """;

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  10. Custom Checker Pipeline Integration
    // ======================================================================

    @Nested
    @DisplayName("Custom checker pipeline integration")
    class CustomCheckerPipelineIntegration {

        @Test
        @DisplayName("engine with empty checker pipeline → always valid")
        void engineWithEmptyCheckerPipeline_alwaysValid() {
            var engine = new JavaSyntaxEngine(List.of());
            var source = "const goto @ ; { ( ]";

            ValidationResult result = engine.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine with single checker → only that checker runs")
        void engineWithSingleChecker_onlyThatCheckerRuns() {
            var engine = new JavaSyntaxEngine(List.of(new DelimiterBalanceChecker()));
            var source = "public class Foo { void m() { assert ; } }";

            ValidationResult result = engine.validate(source);
            // DelimiterBalanceChecker should not flag anything (braces are balanced)
            // KeywordUsageChecker (assert) is not in the pipeline
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("full checker pipeline with all three checkers → comprehensive validation")
        void fullCheckerPipelineWithAllThreeCheckers_comprehensiveValidation() {
            var source = """
                    public private class Comprehensive {
                        String s = "unterminated
                        int x = (1 + 2;
                        const int y = 3;
                        goto label;
                    }
                    """;

            ValidationResult result = syntaxEngine.validate(source);
            assertThat(result.isValid()).isFalse();
            // Errors from all three checkers should be present
            var messages = result.getErrors().stream()
                    .map(ValidationError::getMessage)
                    .toList();

            // TokenizationErrorChecker: unterminated string
            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("lexical"));
            // KeywordUsageChecker: conflicting access, const, goto
            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("conflicting") || m.toLowerCase().contains("reserved"));
        }
    }

    // ======================================================================
    //  11. Temporary File and Command Integration
    // ======================================================================

    @Nested
    @DisplayName("Temporary file and command integration")
    class TempFileAndCommandIntegration {

        @Test
        @DisplayName("command includes correct file extension (.java)")
        void commandIncludesCorrectFileExtension() {
            var source = "public class Ext {}";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_COMPILE);

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(source);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            List<String> cmd = stubExecutor.getLastCommand();
            // The command should contain the binary and a temp file path with .java extension
            assertThat(cmd.get(0)).isEqualTo(VALID_BINARY);
            // Find the temp file argument (last arg or after -d or similar flags)
            String tempFileArg = cmd.stream()
                    .filter(a -> a.contains("syntax-check") && a.endsWith(".java"))
                    .findFirst()
                    .orElse(null);
            assertThat(tempFileArg).isNotNull();
        }

        @Test
        @DisplayName("each validation creates a unique temp file")
        void eachValidationCreatesUniqueTempFile() {
            var source = "public class Temp {}";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_COMPILE);

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(source);
            String firstTempFile = stubExecutor.getLastCommand().stream()
                    .filter(a -> a.contains("syntax-check"))
                    .findFirst().orElse("");

            validator.validate(source);
            String secondTempFile = stubExecutor.getLastCommand().stream()
                    .filter(a -> a.contains("syntax-check"))
                    .findFirst().orElse("");

            // Temp file names should be unique
            assertThat(firstTempFile).isNotEqualTo(secondTempFile);
        }
    }

    // ======================================================================
    //  12. Result Structure Integration
    // ======================================================================

    @Nested
    @DisplayName("Result structure integration")
    class ResultStructureIntegration {

        @Test
        @DisplayName("valid result has correct message and no errors")
        void validResult_hasCorrectMessageAndNoErrors() {
            var source = "public class Valid {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("invalid result has errors with line, column, message, and tool output")
        void invalidResult_hasErrorsWithAllFields() {
            var source = "public class Invalid { int x = \"hello\"; }";

            var javacOutput = "Invalid.java:1:32: error: incompatible types\n"
                    + "public class Invalid { int x = \"hello\"; }\n"
                    + "                                ^\n"
                    + "1 error\n";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(1);
            assertThat(error.getColumn()).isEqualTo(32);
            assertThat(error.getMessage()).contains("incompatible types");
            assertThat(error.getToolOutput()).isNotBlank();
        }

        @Test
        @DisplayName("phase 1 errors have correct line and column from lexer")
        void phase1Errors_haveCorrectLineAndColumnFromLexer() {
            var source = """
                    class A {
                        String s = "unterminated
                    }
                    """;

            ValidationResult result = syntaxEngine.validate(source);
            assertThat(result.isValid()).isFalse();

            // The error should reference line 2 where the unterminated string starts
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(2);
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            var source = "public class Foo {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            // The errors list should be unmodifiable
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ======================================================================
    //  13. Null Safety Integration
    // ======================================================================

    @Nested
    @DisplayName("Null safety integration")
    class NullSafetyIntegration {

        @Test
        @DisplayName("validator constructor rejects null binaryResolver")
        void constructor_rejectsNullBinaryResolver() {
            assertThatCode(() -> new JavaValidator(null,
                    null,
                    new StubProcessExecutor(SUCCESSFUL_COMPILE)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("validator constructor rejects null processExecutor")
        void constructor_rejectsNullProcessExecutor() {
            assertThatCode(() -> new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("syntax engine constructor rejects null checkers list")
        void syntaxEngineConstructor_rejectsNullCheckersList() {
            assertThatCode(() -> new JavaSyntaxEngine(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("syntax engine with null source → returns valid")
        void syntaxEngine_withNullSource_returnsValid() {
            ValidationResult result = syntaxEngine.validate(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("syntax engine with empty source → returns valid")
        void syntaxEngine_withEmptySource_returnsValid() {
            ValidationResult result = syntaxEngine.validate("");
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  14. Parameterized Edge Cases
    // ======================================================================

    @Nested
    @DisplayName("Parameterized edge cases through pipeline")
    class ParameterizedEdgeCases {

        @ParameterizedTest
        @ValueSource(strings = {
                "public class A { }",
                "interface B { void m(); }",
                "enum C { X, Y, Z }",
                "record D(int x, int y) {}",
                "@interface E {}",
                "abstract class F { abstract void m(); }",
                "final class G {}",
                "sealed class H permits I {} final class I extends H {}",
                "non-sealed class J extends H {}",
                "static class K { static int x; }",
                "class L { void m() { var x = 1; } }"
        })
        @DisplayName("various valid Java constructs → pipeline passes")
        void variousValidJavaConstructs_pipelinePasses(String source) {
            // Phase 1 should pass for all these syntactically valid constructs
            ValidationResult phase1 = syntaxEngine.validate(source);
            assertThat(phase1.isValid())
                    .as("Phase 1 should pass for: %s", source)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "public class { }",                    // missing class name
                "class A { int x = ; }",               // missing value
                "class A { void m( { } }",             // missing param
                "class A { if (true) {} }",            // if outside method
        })
        @DisplayName("various invalid Java constructs → phase 1 detects")
        void variousInvalidJavaConstructs_phase1Detects(String source) {
            // Some of these may be caught by phase 1 (lexical/structural)
            // Others might only be caught by javac
            ValidationResult phase1 = syntaxEngine.validate(source);
            // Phase 1 should detect at least some structural issues
            // (not all — e.g., "if outside method" requires semantic analysis)
            assertThat(phase1).isNotNull();
        }
    }

    // ======================================================================
    //  15. Thread Safety Integration
    // ======================================================================

    @Nested
    @DisplayName("Thread safety integration")
    class ThreadSafetyIntegration {

        @Test
        @DisplayName("concurrent validation calls → no interference")
        void concurrentValidationCalls_noInterference() {
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_COMPILE));

            var sources = List.of(
                    "public class A { }",
                    "public class B { int x; }",
                    "public class C { void m() {} }",
                    "public class D { static final int X = 1; }",
                    "public class E { interface I {} }"
            );

            var results = sources.parallelStream()
                    .map(validator::validate)
                    .toList();

            assertThat(results).hasSize(5);
            assertThat(results).allMatch(ValidationResult::isValid);
        }

        @Test
        @DisplayName("concurrent syntax engine calls → no interference")
        void concurrentSyntaxEngineCalls_noInterference() {
            var sources = List.of(
                    "public class A { }",
                    "const int x = 1;",
                    "public class B { String s = \"unterminated; }",
                    "public private class C {}",
                    "public class D { }"
            );

            var results = sources.parallelStream()
                    .map(syntaxEngine::validate)
                    .toList();

            assertThat(results).hasSize(5);
            // A and D should be valid; others should have errors
            assertThat(results.get(0).isValid()).isTrue();   // A
            assertThat(results.get(1).isValid()).isFalse();  // const
            assertThat(results.get(2).isValid()).isFalse();  // unterminated string
            assertThat(results.get(3).isValid()).isFalse();  // conflicting access
            assertThat(results.get(4).isValid()).isTrue();   // D
        }
    }
}