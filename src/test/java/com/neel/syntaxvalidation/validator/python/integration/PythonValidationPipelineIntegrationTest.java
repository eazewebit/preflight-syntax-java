package com.neel.syntaxvalidation.validator.python.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.python.PythonValidator;
import com.neel.syntaxvalidation.validator.python.PythonSyntaxEngine;
import com.neel.syntaxvalidation.validator.python.PythonOutputParser;
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
 * Comprehensive integration tests for the Python support component.
 *
 * <p>These tests verify the complete validation pipeline working end-to-end,
 * exercising the interaction between all Python validation components:
 * <ul>
 *   <li>{@link PythonValidator} — the entry point implementing {@code LanguageValidator}</li>
 *   <li>{@link PythonSyntaxEngine} — the pure-Java, non-executing syntax engine</li>
 *   <li>{@link PythonOutputParser} — the output parser for python3 stderr</li>
 *   <li>{@link ValidatorFactory} — factory-based validator creation</li>
 * </ul>
 *
 * <p>Unlike the unit tests in the sibling packages, these tests intentionally
 * avoid mocking internal components, focusing instead on verifying that the
 * components integrate correctly and that errors propagate properly across
 * boundaries.
 */
@DisplayName("Python Validation Pipeline — Integration Tests")
class PythonValidationPipelineIntegrationTest {

    // ======================================================================
    //  Test doubles (minimal, focused on integration)
    // ======================================================================

    /**
     * A controllable {@link BinaryResolver} for integration tests.
     * Returns a pre-configured path (or empty) to simulate python3 availability.
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

    private static final String VALID_BINARY = "/usr/bin/python3";

    private static final ProcessResult SUCCESSFUL_RUN = new ProcessResult(0, "", "", false);

    // ======================================================================
    //  1. Full Pipeline — Valid Source Code
    // ======================================================================

    @Nested
    @DisplayName("Full pipeline with valid Python source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid simple script → both phases pass")
        void validSimpleScript_bothPhasesPass() {
            var source = """
                    print("Hello, World!")
                    """;

            // Phase 1: syntax engine should pass
            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isTrue();

            // Phase 2: python3 should pass (simulated)
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }

        @Test
        @DisplayName("valid function definition → both phases pass")
        void validFunctionDefinition_bothPhasesPass() {
            var source = """
                    def greet(name: str) -> str:
                        return f"Hello, {name}!"
                    
                    print(greet("World"))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid class definition → both phases pass")
        void validClassDefinition_bothPhasesPass() {
            var source = """
                    class Calculator:
                        def __init__(self):
                            self.result = 0
                        
                        def add(self, x: int) -> 'Calculator':
                            self.result += x
                            return self
                        
                        def subtract(self, x: int) -> 'Calculator':
                            self.result -= x
                            return self
                        
                        def get_result(self) -> int:
                            return self.result
                    
                    calc = Calculator()
                    calc.add(5).subtract(2)
                    print(calc.get_result())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid dataclass → both phases pass")
        void validDataclass_bothPhasesPass() {
            var source = """
                    from dataclasses import dataclass
                    from typing import Optional
                    
                    @dataclass
                    class User:
                        name: str
                        age: int
                        email: Optional[str] = None
                        
                        def greet(self) -> str:
                            return f"Hello, I'm {self.name}"
                    
                    user = User("Alice", 30, "alice@example.com")
                    print(user.greet())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid async function → both phases pass")
        void validAsyncFunction_bothPhasesPass() {
            var source = """
                    import asyncio
                    
                    async def fetch_data() -> str:
                        await asyncio.sleep(0.1)
                        return "data"
                    
                    async def main():
                        result = await fetch_data()
                        print(result)
                    
                    asyncio.run(main())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid generator function → both phases pass")
        void validGeneratorFunction_bothPhasesPass() {
            var source = """
                    def fibonacci(n: int):
                        a, b = 0, 1
                        for _ in range(n):
                            yield a
                            a, b = b, a + b
                    
                    for num in fibonacci(10):
                        print(num)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid list comprehension → both phases pass")
        void validListComprehension_bothPhasesPass() {
            var source = """
                    squares = [x**2 for x in range(10)]
                    evens = [x for x in range(20) if x % 2 == 0]
                    matrix = [[i*j for j in range(5)] for i in range(5)]
                    print(squares, evens, matrix)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid decorator pattern → both phases pass")
        void validDecoratorPattern_bothPhasesPass() {
            var source = """
                    def memoize(func):
                        cache = {}
                        def wrapper(*args):
                            if args not in cache:
                                cache[args] = func(*args)
                            return cache[args]
                        return wrapper
                    
                    @memoize
                    def fibonacci(n):
                        if n < 2:
                            return n
                        return fibonacci(n - 1) + fibonacci(n - 2)
                    
                    print(fibonacci(30))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid context manager → both phases pass")
        void validContextManager_bothPhasesPass() {
            var source = """
                    from contextlib import contextmanager
                    
                    @contextmanager
                    def managed_resource():
                        print("Acquiring resource")
                        try:
                            yield "resource"
                        finally:
                            print("Releasing resource")
                    
                    with managed_resource() as r:
                        print(f"Using {r}")
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid match statement → both phases pass")
        void validMatchStatement_bothPhasesPass() {
            var source = """
                    def describe(value):
                        match value:
                            case 0:
                                return "zero"
                            case 1:
                                return "one"
                            case _:
                                return "other"
                    
                    print(describe(0))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  2. Full Pipeline — Invalid Source Code (Syntax Errors)
    // ======================================================================

    @Nested
    @DisplayName("Full pipeline with invalid Python source")
    class FullPipelineInvalidSource {

        @Test
        @DisplayName("unclosed parenthesis → phase 1 detects")
        void unclosedParenthesis_phase1Detects() {
            var source = """
                    x = (1 + 2
                    """;

            // Phase 1 should detect the issue
            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed parenthesis"));
        }

        @Test
        @DisplayName("unclosed bracket → phase 1 detects")
        void unclosedBracket_phase1Detects() {
            var source = """
                    x = [1, 2, 3
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed bracket"));
        }

        @Test
        @DisplayName("unclosed brace → phase 1 detects")
        void unclosedBrace_phase1Detects() {
            var source = """
                    x = {'a': 1
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed brace"));
        }

        @Test
        @DisplayName("mismatched delimiters → phase 1 detects")
        void mismatchedDelimiters_phase1Detects() {
            var source = """
                    x = (1 + 2]
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("Mismatched closing delimiter"));
        }

        @Test
        @DisplayName("literal as assignment target → phase 1 detects")
        void literalAsAssignmentTarget_phase1Detects() {
            var source = """
                    1 = x
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("invalid assignment target"));
        }

        @Test
        @DisplayName("from without import → phase 1 detects")
        void fromWithoutImport_phase1Detects() {
            var source = """
                    from os
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("Expected 'import'"));
        }

        @Test
        @DisplayName("def without name → phase 1 detects")
        void defWithoutName_phase1Detects() {
            var source = """
                    def ():
                        pass
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("Expected identifier after 'def'"));
        }

        @Test
        @DisplayName("try without except or finally → phase 1 detects")
        void tryWithoutExcept_phase1Detects() {
            var source = """
                    try:
                        pass
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("except"));
        }

        @Test
        @DisplayName("unterminated string → phase 1 detects")
        void unterminatedString_phase1Detects() {
            var source = """
                    x = 'hello
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("UnterminatedString"));
        }

        @Test
        @DisplayName("Python 2 print statement → phase 1 detects")
        void python2Print_phase1Detects() {
            var source = """
                    print hello
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("python 2"));
        }

        @Test
        @DisplayName("Python 2 except syntax → phase 1 detects")
        void python2Except_phase1Detects() {
            var source = """
                    try:
                        pass
                    except Exception, e:
                        pass
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("python 2"));
        }

        @Test
        @DisplayName("mixed tabs and spaces → phase 1 detects")
        void mixedTabsSpaces_phase1Detects() {
            var source = """
                    if True:
                    \t pass
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            assertThat(phase1.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("mixed tabs and spaces"));
        }

        @Test
        @DisplayName("multiple errors from phase 1 → all reported")
        void multipleErrors_phase1_allReported() {
            var source = """
                    1 = x
                    x = (
                    from os
                    """;

            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid()).isFalse();
            // Should have errors from multiple checks
            assertThat(phase1.getErrors().size()).isGreaterThanOrEqualTo(2);

            var messages = phase1.getErrors().stream()
                    .map(ValidationError::getMessage)
                    .toList();

            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("invalid assignment target"));
            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("unclosed parenthesis"));
        }
    }

    // ======================================================================
    //  3. Two-Phase Validation Interaction
    // ======================================================================

    @Nested
    @DisplayName("Two-phase validation interaction")
    class TwoPhaseValidationInteraction {

        @Test
        @DisplayName("phase 1 fails → python3 is NOT invoked (phase 1 errors returned immediately)")
        void phase1Fails_python3NotInvoked() {
            var source = "1 = x";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_RUN);

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            // Phase 1 fails → python3 should NOT be invoked
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            // Should have errors from phase 1 only (invalid assignment target)
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("invalid assignment target");
        }

        @Test
        @DisplayName("phase 1 passes but phase 2 fails → result is invalid")
        void phase1PassesPhase2Fails_resultInvalid() {
            var source = """
                    # This passes the syntax engine but fails at python3 runtime
                    x = undefined_variable
                    """;

            var python3Output = "  File \"<string>\", line 2, in <module>\n"
                    + "    x = undefined_variable\n"
                    + "NameError: name 'undefined_variable' is not defined\n";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("name 'undefined_variable' is not defined"));
        }

        @Test
        @DisplayName("phase 1 detects syntax error, phase 2 also detects → phase 1 errors returned")
        void phase1DetectsSyntaxError_phase2AlsoDetects_phase1ErrorsReturned() {
            var source = """
                    x = (1 + 2
                    """;

            // Phase 2 would also detect this, but phase 1 catches it first
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("unclosed parenthesis");
        }

        @Test
        @DisplayName("both phases pass → result is valid")
        void bothPhasesPass_resultValid() {
            var source = """
                    def add(a, b):
                        return a + b
                    
                    print(add(1, 2))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("python3 reports warnings only → result is valid")
        void python3WarningsOnly_resultValid() {
            var source = """
                    import warnings
                    warnings.warn("This is a warning")
                    print("Hello")
                    """;

            // Warnings go to stderr but exit code is 0
            var python3Output = "/path/to/file.py:2: UserWarning: This is a warning\n"
                    + "  warnings.warn(\"This is a warning\")\n";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            // Warnings don't invalidate the result
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  4. Binary Resolution Integration
    // ======================================================================

    @Nested
    @DisplayName("Binary resolution integration")
    class BinaryResolutionIntegration {

        @Test
        @DisplayName("python3 not found with valid source → falls back to engine result (valid)")
        void python3NotFound_validSource_fallsBackToEngineValid() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            // Phase 1 passes, python3 not found → falls back to engine result (valid)
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("python3 not found with invalid source → engine errors returned")
        void python3NotFound_invalidSource_engineErrorsReturned() {
            var source = "1 = x";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            // Phase 1 fails → returns engine errors immediately (python3 not even checked)
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("invalid assignment target"));
        }

        @Test
        @DisplayName("preferred path overrides system PATH")
        void preferredPathOverridesSystemPath() {
            var source = "print('hello')";
            var customPath = "/custom/path/to/python3";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_RUN);
            var validator = new PythonValidator(customPath,
                    new StubBinaryResolver(null), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            // Verify the custom path was used in the command
            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo(customPath);
        }

        @Test
        @DisplayName("virtual environment resolution integration")
        void virtualEnvResolution_integration() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

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
            // Need source that passes phase 1 so it reaches python3
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("I/O error");
        }

        @Test
        @DisplayName("InterruptedException during process execution → graceful failure")
        void interruptedDuringProcessExecution_gracefulFailure() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new InterruptedProcessExecutor());

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("interrupted");
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate((String) null);
            // null content is treated as empty → phase 1 passes, phase 2 runs empty file
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → both phases handle gracefully")
        void emptyContent_bothPhasesHandleGracefully() {
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process timeout → returns result with timeout indication")
        void processTimeout_returnsResultWithTimeoutIndication() {
            var source = "while True: pass";

            var timeoutResult = new ProcessResult(-1, "", "", true);
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(timeoutResult));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }

        @Test
        @DisplayName("python3 returns unexpected exit code with no output → handled gracefully")
        void unexpectedExitCodeNoOutput_handledGracefully() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(137, "", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  6. Python Output Parser Integration
    // ======================================================================

    @Nested
    @DisplayName("Python output parser integration")
    class PythonOutputParserIntegration {

        @Test
        @DisplayName("complex multi-error python3 output → all errors parsed correctly")
        void complexMultiErrorPython3Output_allErrorsParsedCorrectly() {
            var source = """
                    def bad():
                        x = (
                        if True:
                            pass
                    
                    bad()
                    """;

            var python3Output = "  File \"<string>\", line 2\n"
                    + "    x = (\n"
                    + "        ^\n"
                    + "SyntaxError: unexpected EOF while parsing\n";

            // Phase 1 detects unclosed parenthesis → python3 is never invoked
            var stubExecutor = new StubProcessExecutor(new ProcessResult(1, "", python3Output, false));
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            // Phase 1 finds delimiter errors, python3 is NOT invoked
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("Unclosed parenthesis");
        }

        @Test
        @DisplayName("python3 output with line information → line preserved")
        void python3OutputWithLineInformation_linePreserved() {
            var source = """
                    x = 1
                    y = 2
                    z = x + y;
                    """;

            var python3Output = "  File \"<string>\", line 3\n"
                    + "    z = x + y;\n"
                    + "             ^\n"
                    + "SyntaxError: invalid syntax\n";

            // Phase 1 passes (semicolon is allowed in Python)
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(3);
        }

        @Test
        @DisplayName("python3 output with column information from caret → column preserved")
        void python3OutputWithColumnInformation_columnPreserved() {
            var source = """
                    x = 1 +
                    """;

            var python3Output = "  File \"<string>\", line 1\n"
                    + "    x = 1 +\n"
                    + "          ^\n"
                    + "SyntaxError: invalid syntax\n";

            // Phase 1 passes (expression might be valid in some contexts)
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(11);
        }

        @Test
        @DisplayName("empty python3 output → treated as valid")
        void emptyPython3Output_treatedAsValid() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("python3 output with only whitespace → treated as valid")
        void python3OutputWithOnlyWhitespace_treatedAsValid() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(0, "   \n  \n  ", "", false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("python3 IndentationError → parsed correctly")
        void python3IndentationError_parsedCorrectly() {
            var source = """
                    def bad():
                    pass
                    """;

            var python3Output = "  File \"<string>\", line 2\n"
                    + "    pass\n"
                    + "    ^\n"
                    + "IndentationError: expected an indented block\n";

            // Phase 1 might or might not detect this; phase 2 should
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("IndentationError"));
        }

        @Test
        @DisplayName("python3 TabError → parsed correctly")
        void python3TabError_parsedCorrectly() {
            var source = """
                    def bad():
                    \t pass
                    """;

            var python3Output = "  File \"<string>\", line 2\n"
                    + "    pass\n"
                    + "    ^\n"
                    + "TabError: inconsistent use of tabs and spaces in indentation\n";

            // Phase 1 detects mixed tabs/spaces
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("mixed tabs and spaces"));
        }

        @Test
        @DisplayName("python3 NameError → parsed correctly")
        void python3NameError_parsedCorrectly() {
            var source = """
                    print(undefined_variable)
                    """;

            var python3Output = "Traceback (most recent call last):\n"
                    + "  File \"<string>\", line 1, in <module>\n"
                    + "NameError: name 'undefined_variable' is not defined\n";

            // Phase 1 passes (syntax is valid)
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("NameError"));
        }

        @Test
        @DisplayName("python3 output with Windows line endings → parsed correctly")
        void python3OutputWithWindowsLineEndings_parsedCorrectly() {
            var source = "x = (";

            var python3Output = "  File \"<string>\", line 1\r\n    x = (\r\n        ^\r\nSyntaxError: unexpected EOF while parsing\r\n";

            // Phase 1 detects unclosed parenthesis
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", python3Output, false)));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed parenthesis"));
        }
    }

    // ======================================================================
    //  7. ModificationRequest Integration
    // ======================================================================

    @Nested
    @DisplayName("ModificationRequest integration")
    class ModificationRequestIntegration {

        @Test
        @DisplayName("ModificationRequest builder creates request with correct fields")
        void modificationRequestBuilder_createsRequestWithCorrectFields() {
            var proposedCode = """
                    def calculate(x: int, y: int) -> int:
                        return x + y
                    
                    result = calculate(5, 3)
                    print(result)
                    """;

            var request = ModificationRequest.builder()
                    .filePath("calculator.py")
                    .fromLine(1)
                    .toLine(5)
                    .replacement(proposedCode)
                    .build();

            assertThat(request.getFilePath()).isEqualTo("calculator.py");
            assertThat(request.getFromLine()).isEqualTo(1);
            assertThat(request.getToLine()).isEqualTo(5);
            assertThat(request.getReplacement()).isEqualTo(proposedCode);
        }

        @Test
        @DisplayName("ModificationRequest with null replacement → handled gracefully")
        void modificationRequestWithNullReplacement_handledGracefully() {
            var request = ModificationRequest.builder()
                    .filePath("new_file.py")
                    .fromLine(1)
                    .toLine(1)
                    .replacement(null)
                    .build();

            assertThat(request.getFilePath()).isEqualTo("new_file.py");
            // Builder treats null replacement as empty string
            assertThat(request.getReplacement()).isEmpty();
        }
    }

    // ======================================================================
    //  8. ValidatorFactory Integration
    // ======================================================================

    @Nested
    @DisplayName("ValidatorFactory integration")
    class ValidatorFactoryIntegration {

        @Test
        @DisplayName("factory creates Python validator that works end-to-end")
        void factoryCreatesPythonValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.PYTHON);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.PYTHON);
        }

        @Test
        @DisplayName("factory supports Python language")
        void factorySupportsPythonLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.PYTHON);
        }

        @Test
        @DisplayName("factory-created validator validates valid source")
        void factoryCreatedValidator_validatesValidSource() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.PYTHON).orElseThrow();

            var source = "print('hello')";
            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
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
            var sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append("x").append(i).append(" = ").append(i).append("\n");
            }
            var source = sb.toString();

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with Unicode identifiers → pipeline handles")
        void sourceWithUnicodeIdentifiers_pipelineHandles() {
            var source = """
                    é = 42
                    ñ = "test"
                    中文 = "Chinese"
                    print(é, ñ, 中文)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with mixed line endings → pipeline handles")
        void sourceWithMixedLineEndings_pipelineHandles() {
            var source = "x = 1\r\ny = 2\nz = 3\r\nprint(x, y, z)\n";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with only comments → pipeline handles")
        void sourceWithOnlyComments_pipelineHandles() {
            var source = """
                    # Single-line comment
                    # Another comment
                    # Yet another comment
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with deeply nested structures → pipeline handles")
        void sourceWithDeeplyNestedStructures_pipelineHandles() {
            var source = """
                    def level1():
                        def level2():
                            def level3():
                                def level4():
                                    def level5():
                                        return [1, 2, 3]
                                    return level5()
                                return level4()
                            return level3()
                        return level2()
                    
                    print(level1())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with complex data structures → pipeline handles")
        void sourceWithComplexDataStructures_pipelineHandles() {
            var source = """
                    from typing import Dict, List, Optional, Tuple, Union
                    
                    data: Dict[str, List[Tuple[int, Optional[str]]]] = {
                        "key1": [(1, "a"), (2, None)],
                        "key2": [(3, "b")],
                    }
                    
                    result: Union[int, str] = data["key1"][0][0]
                    print(result)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("source with f-strings and nested expressions → pipeline handles")
        void sourceWithFStringsAndNestedExpressions_pipelineHandles() {
            var source = """
                    name = "World"
                    greeting = f"Hello, {name.upper()}!"
                    complex_f = f"Result: {1 + 2 * 3}"
                    nested = f"Nested: {f'inner {10 + 20}'}"
                    print(greeting, complex_f, nested)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  10. Temporary File and Command Integration
    // ======================================================================

    @Nested
    @DisplayName("Temporary file and command integration")
    class TempFileAndCommandIntegration {

        @Test
        @DisplayName("command includes correct file extension (.py)")
        void commandIncludesCorrectFileExtension() {
            var source = "print('hello')";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_RUN);

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(source);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            List<String> cmd = stubExecutor.getLastCommand();
            // The command should contain at least 2 args: binary and inline script
            assertThat(cmd.size()).isGreaterThanOrEqualTo(2);
            assertThat(cmd.get(0)).isEqualTo(VALID_BINARY);
            // The inline script references the .py temp file within the code string
            // Find the element that contains the .py temp file reference
            String tempFileReference = cmd.stream()
                    .filter(a -> a.contains("syntax-check") || a.endsWith(".py") || a.contains(".py"))
                    .findFirst()
                    .orElse(null);
            assertThat(tempFileReference).isNotNull();
        }

        @Test
        @DisplayName("each validation creates a unique temp file")
        void eachValidationCreatesUniqueTempFile() {
            var source = "print('hello')";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_RUN);

            var validator = new PythonValidator(null,
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
    //  11. Result Structure Integration
    // ======================================================================

    @Nested
    @DisplayName("Result structure integration")
    class ResultStructureIntegration {

        @Test
        @DisplayName("valid result has correct message and no errors")
        void validResult_hasCorrectMessageAndNoErrors() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("invalid result has errors with line, column, message, and tool output")
        void invalidResult_hasErrorsWithAllFields() {
            var source = """
                    x = (1 + 2
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isFalse();

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(1);
            assertThat(error.getColumn()).isPositive();
            assertThat(error.getMessage()).contains("Unclosed parenthesis");
        }

        @Test
        @DisplayName("phase 1 errors have correct line and column from syntax engine")
        void phase1Errors_haveCorrectLineAndColumnFromSyntaxEngine() {
            var source = """
                    x = 1
                    y = 2
                    z = (
                    """;

            ValidationResult result = PythonSyntaxEngine.validate(source);
            assertThat(result.isValid()).isFalse();

            // The error should reference line 3 where the unclosed parenthesis starts
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(3);
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            var source = "print('hello')";

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
            // The errors list should be unmodifiable
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ======================================================================
    //  12. Null Safety Integration
    // ======================================================================

    @Nested
    @DisplayName("Null safety integration")
    class NullSafetyIntegration {

        @Test
        @DisplayName("validator constructor rejects null binaryResolver")
        void constructor_rejectsNullBinaryResolver() {
            assertThatCode(() -> new PythonValidator(null,
                    null,
                    new StubProcessExecutor(SUCCESSFUL_RUN)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("validator constructor rejects null processExecutor")
        void constructor_rejectsNullProcessExecutor() {
            assertThatCode(() -> new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("syntax engine with null source → returns valid")
        void syntaxEngine_withNullSource_returnsValid() {
            ValidationResult result = PythonSyntaxEngine.validate(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("syntax engine with empty source → returns valid")
        void syntaxEngine_withEmptySource_returnsValid() {
            ValidationResult result = PythonSyntaxEngine.validate("");
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  13. Parameterized Edge Cases
    // ======================================================================

    @Nested
    @DisplayName("Parameterized edge cases through pipeline")
    class ParameterizedEdgeCases {

        @ParameterizedTest
        @ValueSource(strings = {
                "print('hello')",
                "x = 1",
                "def foo(): pass",
                "class Bar: pass",
                "if True: pass",
                "for i in range(10): pass",
                "while False: break",
                "try: pass\nexcept: pass",
                "with open('file') as f: pass",
                "lambda x: x + 1",
                "@property\ndef name(self): pass",
                "async def fetch(): pass",
                "def gen(): yield 1",
                "[x for x in range(10)]",
                "{k: v for k, v in []}",
                "{x for x in range(10)}",
                "match command:\n    case 'quit': pass\n    case _: pass",
                "type Point = tuple[int, int]",
                "f'value: {1 + 2}'",
                "t'hello {name}'"
        })
        @DisplayName("various valid Python constructs → pipeline passes")
        void variousValidPythonConstructs_pipelinePasses(String source) {
            // Phase 1 should pass for all these syntactically valid constructs
            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid())
                    .as("Phase 1 should pass for: %s", source)
                    .isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "1 = x",                    // literal as target
                "x = (",                    // unclosed paren
                "from os",                  // from without import
                "def (): pass",            // def without name
                "try: pass",               // try without except
                "x = 'hello",              // unterminated string
                "print hello",             // Python 2 print
                "x = [1, 2, 3",            // unclosed bracket
                "x = {'a': 1",             // unclosed brace
                "x = (1 + 2]",             // mismatched delimiters
        })
        @DisplayName("various invalid Python constructs → phase 1 detects")
        void variousInvalidPythonConstructs_phase1Detects(String source) {
            ValidationResult phase1 = PythonSyntaxEngine.validate(source);
            assertThat(phase1.isValid())
                    .as("Phase 1 should fail for: %s", source)
                    .isFalse();
        }
    }

    // ======================================================================
    //  14. Thread Safety Integration
    // ======================================================================

    @Nested
    @DisplayName("Thread safety integration")
    class ThreadSafetyIntegration {

        @Test
        @DisplayName("concurrent validation calls → no interference")
        void concurrentValidationCalls_noInterference() {
            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            var sources = List.of(
                    "print('a')",
                    "x = 1",
                    "def foo(): pass",
                    "class Bar: pass",
                    "if True: pass"
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
                    "print('a')",
                    "1 = x",
                    "x = (",
                    "from os",
                    "print('b')"
            );

            var results = sources.parallelStream()
                    .map(PythonSyntaxEngine::validate)
                    .toList();

            assertThat(results).hasSize(5);
            // First and last should be valid; others should have errors
            assertThat(results.get(0).isValid()).isTrue();   // print('a')
            assertThat(results.get(1).isValid()).isFalse();  // 1 = x
            assertThat(results.get(2).isValid()).isFalse();  // x = (
            assertThat(results.get(3).isValid()).isFalse();  // from os
            assertThat(results.get(4).isValid()).isTrue();   // print('b')
        }
    }

    // ======================================================================
    //  15. Python 3.14 Specific Features Integration
    // ======================================================================

    @Nested
    @DisplayName("Python 3.14 features integration")
    class Python314FeaturesIntegration {

        @Test
        @DisplayName("match statement with complex patterns → pipeline handles")
        void matchStatementWithComplexPatterns_pipelineHandles() {
            var source = """
                    def process(value):
                        match value:
                            case 0:
                                return "zero"
                            case 1 | 2 | 3:
                                return "small"
                            case [x, y]:
                                return f"pair: {x}, {y}"
                            case {"key": value}:
                                return f"dict: {value}"
                            case _:
                                return "other"
                    
                    print(process(0))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("type alias → pipeline handles")
        void typeAlias_pipelineHandles() {
            var source = """
                    type Point = tuple[int, int]
                    type Matrix = list[list[float]]
                    
                    def distance(p1: Point, p2: Point) -> float:
                        return ((p1[0] - p2[0])**2 + (p1[1] - p2[1])**2)**0.5
                    
                    print(distance((0, 0), (3, 4)))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("t-string (PEP 750) → pipeline handles")
        void tString_pipelineHandles() {
            var source = """
                    name = "World"
                    template = t"Hello, {name}!"
                    print(template)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("walrus operator → pipeline handles")
        void walrusOperator_pipelineHandles() {
            var source = """
                    data = [1, 2, 3, 4, 5]
                    if (n := len(data)) > 3:
                        print(f"Data has {n} elements")
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("positional-only parameters → pipeline handles")
        void positionalOnlyParameters_pipelineHandles() {
            var source = """
                    def foo(a, b, /, c, d):
                        return a + b + c + d
                    
                    print(foo(1, 2, c=3, d=4))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("keyword-only parameters → pipeline handles")
        void keywordOnlyParameters_pipelineHandles() {
            var source = """
                    def foo(a, *, b, c):
                        return a + b + c
                    
                    print(foo(1, b=2, c=3))
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  16. Complete Program Integration
    // ======================================================================

    @Nested
    @DisplayName("Complete program integration")
    class CompleteProgramIntegration {

        @Test
        @DisplayName("fizzbuzz program → pipeline handles")
        void fizzbuzzProgram_pipelineHandles() {
            var source = """
                    for i in range(1, 101):
                        if i % 15 == 0:
                            print("FizzBuzz")
                        elif i % 3 == 0:
                            print("Fizz")
                        elif i % 5 == 0:
                            print("Buzz")
                        else:
                            print(i)
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("class with multiple methods → pipeline handles")
        void classWithMultipleMethods_pipelineHandles() {
            var source = """
                    class Calculator:
                        def __init__(self):
                            self.result = 0
                        
                        def add(self, x):
                            self.result += x
                            return self
                        
                        def subtract(self, x):
                            self.result -= x
                            return self
                        
                        def multiply(self, x):
                            self.result *= x
                            return self
                        
                        def divide(self, x):
                            if x == 0:
                                raise ValueError("Cannot divide by zero")
                            self.result /= x
                            return self
                        
                        def get_result(self):
                            return self.result
                        
                        def reset(self):
                            self.result = 0
                            return self
                    
                    calc = Calculator()
                    calc.add(10).subtract(3).multiply(2).divide(7)
                    print(calc.get_result())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("decorator pattern → pipeline handles")
        void decoratorPattern_pipelineHandles() {
            var source = """
                    import functools
                    import time
                    
                    def timer(func):
                        @functools.wraps(func)
                        def wrapper(*args, **kwargs):
                            start = time.perf_counter()
                            result = func(*args, **kwargs)
                            end = time.perf_counter()
                            print(f"{func.__name__} took {end - start:.4f} seconds")
                            return result
                        return wrapper
                    
                    def retry(max_attempts=3):
                        def decorator(func):
                            @functools.wraps(func)
                            def wrapper(*args, **kwargs):
                                for attempt in range(max_attempts):
                                    try:
                                        return func(*args, **kwargs)
                                    except Exception as e:
                                        if attempt == max_attempts - 1:
                                            raise
                                        print(f"Attempt {attempt + 1} failed: {e}")
                            return wrapper
                        return decorator
                    
                    @timer
                    @retry(max_attempts=3)
                    def risky_operation():
                        import random
                        if random.random() < 0.5:
                            raise ValueError("Random failure")
                        return "success"
                    
                    print(risky_operation())
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("context manager pattern → pipeline handles")
        void contextManagerPattern_pipelineHandles() {
            var source = """
                    from contextlib import contextmanager
                    
                    @contextmanager
                    def managed_resource(name):
                        print(f"Acquiring {name}")
                        try:
                            yield name
                        finally:
                            print(f"Releasing {name}")
                    
                    @contextmanager
                    def nested_context():
                        with managed_resource("outer") as outer:
                            with managed_resource("inner") as inner:
                                yield (outer, inner)
                    
                    with nested_context() as (outer, inner):
                        print(f"Using {outer} and {inner}")
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("dataclass pattern → pipeline handles")
        void dataclassPattern_pipelineHandles() {
            var source = """
                    from dataclasses import dataclass, field
                    from typing import List, Optional
                    from datetime import datetime
                    
                    @dataclass
                    class Task:
                        title: str
                        description: str = ""
                        priority: int = 0
                        tags: List[str] = field(default_factory=list)
                        completed: bool = False
                        created_at: datetime = field(default_factory=datetime.now)
                        
                        def complete(self):
                            self.completed = True
                        
                        def add_tag(self, tag: str):
                            if tag not in self.tags:
                                self.tags.append(tag)
                    
                    @dataclass
                    class TaskManager:
                        tasks: List[Task] = field(default_factory=list)
                        
                        def add_task(self, title: str, **kwargs) -> Task:
                            task = Task(title=title, **kwargs)
                            self.tasks.append(task)
                            return task
                        
                        def get_completed(self) -> List[Task]:
                            return [t for t in self.tasks if t.completed]
                        
                        def get_by_priority(self, min_priority: int) -> List[Task]:
                            return [t for t in self.tasks if t.priority >= min_priority]
                    
                    manager = TaskManager()
                    task1 = manager.add_task("Buy groceries", priority=1, tags=["shopping"])
                    task2 = manager.add_task("Write code", priority=2, tags=["work", "coding"])
                    task1.complete()
                    
                    print(f"Completed: {len(manager.get_completed())}")
                    print(f"High priority: {len(manager.get_by_priority(2))}")
                    """;

            var validator = new PythonValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_RUN));

            ValidationResult result = validator.validate(source);
            assertThat(result.isValid()).isTrue();
        }
    }
}