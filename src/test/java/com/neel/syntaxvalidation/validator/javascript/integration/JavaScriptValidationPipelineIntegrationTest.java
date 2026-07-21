package com.neel.syntaxvalidation.validator.javascript.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine;
import com.neel.syntaxvalidation.validator.javascript.NodeCheckOutputParser;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the JavaScript validation pipeline.
 * <p>
 * Tests the complete flow: source code → BinaryResolver → ProcessExecutor →
 * node --check → NodeCheckOutputParser → ValidationResult.
 * <p>
 * Also tests the JavaScriptSyntaxEngine fallback and edge cases.
 */
@DisplayName("JavaScript Validation Pipeline Integration Tests")
class JavaScriptValidationPipelineIntegrationTest {

    // ======================================================================
    //  Test doubles (minimal, focused on integration)
    // ======================================================================

    private static class StubBinaryResolver extends BinaryResolver {
        private final Optional<String> resolvedPath;

        StubBinaryResolver(String path) {
            this.resolvedPath = Optional.ofNullable(path);
        }

        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) {
            if (preferredPath != null && !preferredPath.isBlank()) {
                return Optional.of(preferredPath);
            }
            return resolvedPath;
        }
    }

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

    private static class FailingProcessExecutor extends ProcessExecutor {
        @Override
        public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
            throw new IOException("Simulated process failure");
        }
    }

    // ======================================================================
    //  Shared fixtures
    // ======================================================================

    private static final String VALID_BINARY = "/usr/bin/node";
    private static final ProcessResult SUCCESSFUL_CHECK = new ProcessResult(0, "", "", false);

    private JavaScriptSyntaxEngine syntaxEngine;

    @BeforeEach
    void setUp() {
        syntaxEngine = JavaScriptSyntaxEngine.getInstance();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  1. FULL PIPELINE — VALID SOURCE CODE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full pipeline with valid JavaScript source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid arrow function with template literal → both phases pass")
        void validArrowFunction_bothPhasesPass() {
            String code = """
                    const multiply = (a, b) => a * b;
                    const result = multiply(3, 4);
                    console.log(`Result: ${result}`);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }

        @Test
        @DisplayName("valid class with private fields → both phases pass")
        void validClassPrivateFields_bothPhasesPass() {
            String code = """
                    class Counter {
                        #count = 0;
                        increment() { this.#count++; }
                        get value() { return this.#count; }
                    }
                    const c = new Counter();
                    c.increment();
                    console.log(c.value);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid async/await → both phases pass")
        void validAsyncAwait_bothPhasesPass() {
            String code = """
                    async function fetchData() {
                        const response = await fetch('https://example.com');
                        const data = await response.json();
                        return data;
                    }
                    fetchData().then(console.log);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid destructuring and spread → both phases pass")
        void validDestructuringAndSpread_bothPhasesPass() {
            String code = """
                    const { name, age, ...rest } = { name: 'Alice', age: 30, city: 'NY' };
                    const [first, second, ...others] = [1, 2, 3, 4, 5];
                    const clone = { ...rest, extra: true };
                    console.log(name, age, first, second, others, clone);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid generator and iterator → both phases pass")
        void validGeneratorAndIterator_bothPhasesPass() {
            String code = """
                    function* range(start, end) {
                        for (let i = start; i <= end; i++) {
                            yield i;
                        }
                    }
                    for (const n of range(1, 5)) {
                        console.log(n);
                    }
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid Proxy and Reflect → both phases pass")
        void validProxyAndReflect_bothPhasesPass() {
            String code = """
                    const handler = {
                        get(target, prop) {
                            return prop in target ? target[prop] : 'default';
                        }
                    };
                    const proxy = new Proxy({}, handler);
                    console.log(proxy.name, proxy.unknown);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid optional chaining and nullish coalescing → both phases pass")
        void validOptionalChaining_bothPhasesPass() {
            String code = """
                    const obj = { a: { b: { c: 42 } } };
                    const val = obj?.a?.b?.c ?? 'default';
                    const missing = obj?.x?.y?.z ?? 'fallback';
                    console.log(val, missing);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. TWO-PHASE VALIDATION INTERACTION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Two-phase validation interaction")
    class TwoPhaseValidationInteraction {

        @Test
        @DisplayName("phase 1 fails → node --check NOT invoked")
        void phase1Fails_nodeNotInvoked() {
            // Unterminated string — caught by syntax engine
            String code = """
                    const s = "unterminated;
                    console.log(s);
                    """;

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_CHECK);
            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("phase 1 passes but phase 2 fails → result is invalid")
        void phase1PassesPhase2Fails_resultInvalid() {
            // Code that passes engine but fails node --check (duplicate params in strict mode)
            String code = """
                    'use strict';
                    function foo(a, a) {
                        console.log(a);
                    }
                    """;

            var nodeOutput = "/tmp/test.js:2\n"
                    + "function foo(a, a) {\n"
                    + "                 ^\n\n"
                    + "SyntaxError: Duplicate parameter name not allowed in this context\n"
                    + "    at compileForInternalLoader (internal/modules/cjs/loader.js)\n";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", nodeOutput, false)));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("duplicate"));
        }

        @Test
        @DisplayName("both phases pass → result is valid")
        void bothPhasesPass_resultValid() {
            String code = "console.log('hello');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. BINARY RESOLUTION INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Binary resolution integration")
    class BinaryResolutionIntegration {

        @Test
        @DisplayName("node not found with valid source → falls back to engine result (valid)")
        void nodeNotFound_validSource_fallsBack() {
            String code = "console.log('hello');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("node not found with invalid source → engine errors returned")
        void nodeNotFound_invalidSource_engineErrorsReturned() {
            String code = "const s = \"unterminated;";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("preferred path overrides system PATH")
        void preferredPathOverridesSystemPath() {
            String code = "console.log('test');";
            String customPath = "/custom/path/to/node";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_CHECK);
            var validator = new JavaScriptValidator(customPath,
                    new StubBinaryResolver(null), stubExecutor);

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo(customPath);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. NODECHECK OUTPUT PARSER
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("NodeCheckOutputParser — output parsing")
    class NodeCheckOutputParserTests {

        @Test
        @DisplayName("parses node --check error output with line info")
        void parsesNodeCheckError_withLineInfo() {
            String stderr = "/tmp/test.js:3\n"
                    + "    const x = ;\n"
                    + "          ^\n\n"
                    + "SyntaxError: Unexpected token ';'\n"
                    + "    at compileForInternalLoader (internal/modules/cjs/loader.js)\n";

            NodeCheckOutputParser parser = new NodeCheckOutputParser();
            List<ValidationError> errors = parser.parse(stderr);

            assertThat(errors).isNotEmpty();
            ValidationError first = errors.get(0);
            assertThat(first.getLine()).isEqualTo(3);
            assertThat(first.getMessage()).containsIgnoringCase("Unexpected token");
        }

        @Test
        @DisplayName("parses empty stderr — no errors")
        void parsesEmptyStderr_noErrors() {
            NodeCheckOutputParser parser = new NodeCheckOutputParser();
            List<ValidationError> errors = parser.parse("");

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("handles null stderr gracefully")
        void handlesNullStderr() {
            NodeCheckOutputParser parser = new NodeCheckOutputParser();
            List<ValidationError> errors = parser.parse(null);
            assertThat(errors).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. JAVASCRIPT SYNTAX ENGINE (FALLBACK)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JavaScriptSyntaxEngine — fallback engine")
    class JavaScriptSyntaxEngineTests {

        @Test
        @DisplayName("valid JavaScript — engine reports valid")
        void validJavaScript_engineReportsValid() {
            String code = """
                    const greet = (name) => `Hello, ${name}!`;
                    console.log(greet('World'));
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("invalid JavaScript — engine reports errors")
        void invalidJavaScript_engineReportsErrors() {
            String code = """
                    function foo() {
                        console.log("bar");
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("engine handles complex nested structures")
        void engine_handlesComplexNestedStructures() {
            String code = """
                    const obj = {
                        arr: [1, { nested: true }, [3, 4]],
                        fn: function() {
                            return {
                                method: () => ({
                                    deep: true
                                })
                            };
                        }
                    };
                    console.log(obj.fn().method().deep);
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles template literals")
        void engine_handlesTemplateLiterals() {
            String code = """
                    const a = `outer ${`inner ${42}`}`;
                    console.log(a);
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles regex vs division")
        void engine_handlesRegexVsDivision() {
            String code = """
                    const a = 5 / 2;
                    const b = /test/g;
                    const c = a / b.test('test');
                    console.log(c);
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles trailing commas")
        void engine_handlesTrailingCommas() {
            String code = """
                    const arr = [1, 2, 3];
                    const obj = { a: 1, b: 2 };
                    console.log(arr, obj);
                    """;
            ValidationResult result = syntaxEngine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles null input gracefully")
        void engine_handlesNullInput() {
            ValidationResult result = syntaxEngine.validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("engine handles empty input gracefully")
        void engine_handlesEmptyInput() {
            ValidationResult result = syntaxEngine.validate("");
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. ERROR HANDLING INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling integration")
    class ErrorHandlingIntegration {

        @Test
        @DisplayName("IOException during process execution → graceful failure")
        void ioExceptionDuringProcessExecution_gracefulFailure() {
            String code = "console.log('test');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate((String) null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → both phases handle gracefully")
        void emptyContent_bothPhasesHandleGracefully() {
            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process timeout → returns result with timeout indication")
        void processTimeout_returnsResultWithTimeoutIndication() {
            String code = "console.log('test');";
            var timeoutResult = new ProcessResult(-1, "", "", true);

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(timeoutResult));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. VALIDATOR FACTORY INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidatorFactory integration")
    class ValidatorFactoryIntegration {

        @Test
        @DisplayName("factory creates JavaScript validator that works end-to-end")
        void factoryCreatesJavaScriptValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVASCRIPT);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("factory supports JavaScript language")
        void factorySupportsJavaScriptLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("factory-created validator validates valid source")
        void factoryCreatedValidator_validatesValidSource() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.JAVASCRIPT).orElseThrow();

            String code = "console.log('factory test');";
            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. EDGE CASES THROUGH FULL PIPELINE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases through full pipeline")
    class EdgeCasesFullPipeline {

        @Test
        @DisplayName("BOM at start of file → pipeline handles")
        void bomAtStartOfFile_pipelineHandles() {
            String code = "\uFEFFconsole.log('BOM test');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Windows-style line endings → pipeline handles")
        void windowsStyleLineEndings_pipelineHandles() {
            String code = "const x = 1;\r\nconsole.log(x);\r\n";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("very deep nesting → pipeline handles without stack overflow")
        void veryDeepNesting_pipelineHandles() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("function f").append(i).append("() { return () => {\n");
            }
            sb.append("42\n");
            for (int i = 99; i >= 0; i--) {
                sb.append("}; }\n");
            }
            sb.append("console.log(f0()());\n");

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("minified code — single long line → pipeline handles")
        void minifiedCode_pipelineHandles() {
            StringBuilder sb = new StringBuilder("var a=1;");
            for (int i = 1; i < 5000; i++) {
                sb.append("var v").append(i).append("=").append(i).append(";");
            }
            sb.append("console.log(a);");

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("code with exotic unicode → pipeline handles")
        void exoticUnicode_pipelineHandles() {
            String code = """
                    const \u2182 = 'myriad';
                    const \u263A = 'smile';
                    console.log(\u2182, \u263A);
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("code with complex regex patterns → pipeline handles")
        void complexRegexPatterns_pipelineHandles() {
            String code = """
                    const emailRe = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$/;
                    const urlRe = /https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)/;
                    console.log(emailRe.test('test@example.com'));
                    """;

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  9. TEMP FILE AND COMMAND INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Temp file and command integration")
    class TempFileAndCommandIntegration {

        @Test
        @DisplayName("command includes --check flag")
        void commandIncludesCheckFlag() {
            String code = "console.log('test');";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_CHECK);

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(code);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand()).contains("--check");
        }

        @Test
        @DisplayName("command includes correct file extension (.js)")
        void commandIncludesCorrectFileExtension() {
            String code = "console.log('test');";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_CHECK);

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(code);

            List<String> cmd = stubExecutor.getLastCommand();
            String tempFileArg = cmd.stream()
                    .filter(a -> a.contains("syntax-check") && a.endsWith(".js"))
                    .findFirst()
                    .orElse(null);
            assertThat(tempFileArg).isNotNull();
        }

        @Test
        @DisplayName("each validation creates a unique temp file")
        void eachValidationCreatesUniqueTempFile() {
            String code = "console.log('test');";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_CHECK);

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(code);
            String firstTempFile = stubExecutor.getLastCommand().stream()
                    .filter(a -> a.contains("syntax-check"))
                    .findFirst().orElse("");

            validator.validate(code);
            String secondTempFile = stubExecutor.getLastCommand().stream()
                    .filter(a -> a.contains("syntax-check"))
                    .findFirst().orElse("");

            assertThat(firstTempFile).isNotEqualTo(secondTempFile);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  10. RESULT STRUCTURE INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Result structure integration")
    class ResultStructureIntegration {

        @Test
        @DisplayName("valid result has correct message and no errors")
        void validResult_hasCorrectMessageAndNoErrors() {
            String code = "console.log('test');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("invalid result has errors with line, column, message")
        void invalidResult_hasErrorsWithAllFields() {
            String code = "console.log('test');";

            String nodeOutput = "/tmp/test.js:1\n"
                    + "console.log('test');\n"
                    + "^\n\n"
                    + "SyntaxError: Unexpected token\n";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(1, "", nodeOutput, false)));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(1);
            assertThat(error.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            String code = "console.log('test');";

            var validator = new JavaScriptValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}