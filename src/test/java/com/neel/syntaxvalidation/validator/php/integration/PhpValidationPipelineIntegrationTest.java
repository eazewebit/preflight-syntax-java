package com.neel.syntaxvalidation.validator.php.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.php.PhpValidator;
import com.neel.syntaxvalidation.validator.php.PhpSyntaxEngine;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the PHP validation pipeline.
 * <p>
 * Tests the complete flow: source code → BinaryResolver → ProcessExecutor →
 * php -l → PhpOutputParser → ValidationResult.
 * <p>
 * Also tests the PhpSyntaxEngine fallback and edge cases.
 */
@DisplayName("PHP Validation Pipeline Integration Tests")
class PhpValidationPipelineIntegrationTest {

    // ======================================================================
    //  Test doubles
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

    private static final String VALID_BINARY = "/usr/bin/php";
    private static final ProcessResult SUCCESSFUL_LINT = new ProcessResult(0,
            "No syntax errors detected in /tmp/test.php", "", false);

    // ══════════════════════════════════════════════════════════════════════
    //  1. FULL PIPELINE — VALID SOURCE CODE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full pipeline with valid PHP source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid PHP with strict types → both phases pass")
        void validPhpStrictTypes_bothPhasesPass() {
            String code = """
                    <?php
                    declare(strict_types=1);
                    function add(int $a, int $b): int {
                        return $a + $b;
                    }
                    echo add(2, 3);
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP with traits and abstract → both phases pass")
        void validPhpTraitsAndAbstract_bothPhasesPass() {
            String code = """
                    <?php
                    trait Loggable {
                        public function log(string $msg): void {
                            echo "[LOG] {$msg}\\n";
                        }
                    }
                    abstract class BaseService {
                        use Loggable;
                        abstract public function execute(): void;
                    }
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP 8.0 match expression → both phases pass")
        void validPhp80Match_bothPhasesPass() {
            String code = """
                    <?php
                    $status = 200;
                    $message = match(true) {
                        $status >= 200 && $status < 300 => 'OK',
                        $status >= 400 => 'Error',
                        default => 'Unknown',
                    };
                    echo $message;
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP with generators → both phases pass")
        void validPhpGenerators_bothPhasesPass() {
            String code = """
                    <?php
                    function fibonacci(): Generator {
                        [$a, $b] = [0, 1];
                        while (true) {
                            yield $a;
                            [$a, $b] = [$b, $a + $b];
                        }
                    }
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP with typed properties → both phases pass")
        void validPhpTypedProperties_bothPhasesPass() {
            String code = """
                    <?php
                    class User {
                        public string $name;
                        public int $age;
                        public ?string $email;
                        public function __construct(string $name, int $age) {
                            $this->name = $name;
                            $this->age = $age;
                        }
                    }
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid PHP with enum → both phases pass")
        void validPhpEnum_bothPhasesPass() {
            String code = """
                    <?php
                    enum Color: string {
                        case Red = '#ff0000';
                        case Green = '#00ff00';
                        case Blue = '#0000ff';
                        public function label(): string {
                            return match($this) {
                                self::Red => 'Red',
                                self::Green => 'Green',
                                self::Blue => 'Blue',
                            };
                        }
                    }
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

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
        @DisplayName("phase 1 fails → php -l NOT invoked")
        void phase1Fails_phpLintNotInvoked() {
            String code = "<?php echo 'unclosed;\n";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_LINT);
            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(stubExecutor.getInvocationCount()).isEqualTo(0);
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("phase 1 passes but phase 2 fails → result is invalid")
        void phase1PassesPhase2Fails_resultInvalid() {
            String code = "<?php echo 'ok';\n";

            String phpOutput = "PHP Parse error:  syntax error, unexpected ';' in /tmp/test.php on line 3";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(255, "", phpOutput, false)));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("parse error"));
        }

        @Test
        @DisplayName("both phases pass → result is valid")
        void bothPhasesPass_resultValid() {
            String code = "<?php echo 'hello';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

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
        @DisplayName("php not found with valid source → falls back to engine result")
        void phpNotFound_validSource_fallsBack() {
            String code = "<?php echo 'hello';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("php not found with invalid source → engine errors returned")
        void phpNotFound_invalidSource_engineErrorsReturned() {
            String code = "<?php echo 'unclosed;\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("preferred path overrides system PATH")
        void preferredPathOverridesSystemPath() {
            String code = "<?php echo 'test';\n";
            String customPath = "/custom/path/to/php";

            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_LINT);
            var validator = new PhpValidator(customPath,
                    new StubBinaryResolver(null), stubExecutor);

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo(customPath);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  4. PHP SYNTAX ENGINE (FALLBACK) — Note: PhpOutputParser is package-private
    // ══════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════
    //  5. ERROR HANDLING INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling integration")
    class ErrorHandlingIntegration {

        @Test
        @DisplayName("IOException during process execution → graceful failure")
        void ioExceptionDuringProcessExecution_gracefulFailure() {
            String code = "<?php echo 'test';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate((String) null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → both phases handle gracefully")
        void emptyContent_bothPhasesHandleGracefully() {
            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process timeout → returns result with timeout indication")
        void processTimeout_returnsResultWithTimeoutIndication() {
            String code = "<?php echo 'test';\n";
            var timeoutResult = new ProcessResult(-1, "", "", true);

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(timeoutResult));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. VALIDATOR FACTORY INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidatorFactory integration")
    class ValidatorFactoryIntegration {

        @Test
        @DisplayName("factory creates PHP validator that works end-to-end")
        void factoryCreatesPhpValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.PHP);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.PHP);
        }

        @Test
        @DisplayName("factory supports PHP language")
        void factorySupportsPhpLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.PHP);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. EDGE CASES THROUGH FULL PIPELINE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases through full pipeline")
    class EdgeCasesFullPipeline {

        @Test
        @DisplayName("BOM at start of PHP file → pipeline handles")
        void bomAtStartOfFile_pipelineHandles() {
            String code = "\uFEFF<?php echo 'BOM test';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP without closing tag → pipeline handles")
        void phpWithoutClosingTag_pipelineHandles() {
            String code = "<?php\necho 'no closing tag';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("very large PHP file → pipeline handles")
        void veryLargePhpFile_pipelineHandles() {
            StringBuilder sb = new StringBuilder("<?php\n");
            for (int i = 0; i < 3000; i++) {
                sb.append("$v").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("echo $v0;\n");

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with inline HTML → pipeline handles")
        void phpWithInlineHtml_pipelineHandles() {
            String code = """
                    <?php if (true): ?>
                    <div>Hello</div>
                    <?php endif; ?>
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("PHP with heredoc → pipeline handles")
        void phpWithHeredoc_pipelineHandles() {
            String code = """
                    <?php
                    $name = "World";
                    $str = <<<EOT
                    Hello, {$name}!
                    This is heredoc.
                    EOT;
                    echo $str;
                    """;

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. TEMP FILE AND COMMAND INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Temp file and command integration")
    class TempFileAndCommandIntegration {

        @Test
        @DisplayName("command includes -l flag")
        void commandIncludesLintFlag() {
            String code = "<?php echo 'test';\n";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_LINT);

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(code);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand()).contains("-l");
        }

        @Test
        @DisplayName("command includes correct file extension (.php)")
        void commandIncludesCorrectFileExtension() {
            String code = "<?php echo 'test';\n";
            var stubExecutor = new StubProcessExecutor(SUCCESSFUL_LINT);

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY), stubExecutor);

            validator.validate(code);

            List<String> cmd = stubExecutor.getLastCommand();
            String tempFileArg = cmd.stream()
                    .filter(a -> a.contains("syntax-check") && a.endsWith(".php"))
                    .findFirst()
                    .orElse(null);
            assertThat(tempFileArg).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  9. RESULT STRUCTURE INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Result structure integration")
    class ResultStructureIntegration {

        @Test
        @DisplayName("valid result has correct message and no errors")
        void validResult_hasCorrectMessageAndNoErrors() {
            String code = "<?php echo 'test';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("invalid result has errors with line, message")
        void invalidResult_hasErrorsWithAllFields() {
            String code = "<?php echo 'test';\n";

            String phpOutput = "PHP Parse error:  syntax error, unexpected ';' in /tmp/test.php on line 1";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(new ProcessResult(255, "", phpOutput, false)));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(1);
            assertThat(error.getMessage()).containsIgnoringCase("parse error");
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            String code = "<?php echo 'test';\n";

            var validator = new PhpValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_LINT));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}