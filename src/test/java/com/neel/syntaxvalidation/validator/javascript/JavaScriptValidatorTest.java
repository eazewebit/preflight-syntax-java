package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JavaScriptValidatorTest {

    @Test
    void getLanguage_returnsJavaScript() {
        assertThat(new JavaScriptValidator().getLanguage()).isEqualTo(Language.JAVASCRIPT);
    }

    @Test
    void validate_returnsValidWhenNodeReportsSuccess() {
        ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, "", "", false));
        JavaScriptValidator validator = validatorWithBinary(stub);

        ValidationResult result = validator.validate("const x = 1;");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).contains("valid");
    }

    @Test
    void validate_returnsInvalidWhenNodeReportsSyntaxError() {
        String nodeError = """
                /tmp/syntax-check-0.js:1
                foo bar
                ^^^

                SyntaxError: Unexpected identifier
                """;
        ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(1, "", nodeError, false));
        JavaScriptValidator validator = validatorWithBinary(stub);

        ValidationResult result = validator.validate("foo bar");

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).getLine()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("Unexpected identifier");
    }

    @Test
    void validate_returnsValidFromEngineWhenBinaryNotFoundAndCodeIsValid() {
        BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
        JavaScriptValidator validator = new JavaScriptValidator(null, missing, new ProcessExecutor());

        ValidationResult result = validator.validate("const x = 1;");

        // The built-in ES6+ engine validates the code even without Node.js.
        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).contains("valid");
        assertThat(result.getMessage()).contains("engine");
    }

    @Test
    void validate_returnsInvalidFromEngineWhenBinaryNotFoundAndCodeHasErrors() {
        BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
        JavaScriptValidator validator = new JavaScriptValidator(null, missing, new ProcessExecutor());

        ValidationResult result = validator.validate("const = ;");

        // The built-in engine catches the error even without Node.js.
        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).getMessage()).isNotBlank();
    }

    @Test
    void validate_engineErrorsShortCircuitBeforeNodeCheck() {
        // Node reports success, but the engine finds a syntax error first.
        ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, "", "", false));
        JavaScriptValidator validator = validatorWithBinary(stub);

        ValidationResult result = validator.validate("const = ;");

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void validate_returnsInvalidOnTimeout() {
        ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(-1, "", "slow", true));
        JavaScriptValidator validator = validatorWithBinary(stub);

        ValidationResult result = validator.validate("while(true){}");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("timed out");
    }

    @Test
    void validate_treatsNullContentAsEmpty() {
        ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, "", "", false));
        JavaScriptValidator validator = validatorWithBinary(stub);

        ValidationResult result = validator.validate(null);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_writesContentToTempFileWithJsExtension() {
        CapturingProcessExecutor capturing = new CapturingProcessExecutor(
                new ProcessResult(0, "", "", false));
        JavaScriptValidator validator = validatorWithBinary(capturing);

        validator.validate("const y = 2;");

        String tempFilePath = capturing.lastCommand().get(2);
        assertThat(tempFilePath).endsWith(".js");
    }

    // ---- test doubles ------------------------------------------------------

    private static JavaScriptValidator validatorWithBinary(ProcessExecutor executor) {
        BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/node"));
        return new JavaScriptValidator(null, present, executor);
    }

    /** A BinaryResolver that always returns the same path. */
    static final class FixedBinaryResolver extends BinaryResolver {
        private final Optional<String> resolved;

        FixedBinaryResolver(Optional<String> resolved) {
            this.resolved = resolved;
        }

        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) {
            return resolved;
        }
    }

    /** A ProcessExecutor that returns a pre-canned result. */
    static final class StubProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;

        StubProcessExecutor(ProcessResult result) {
            this.result = result;
        }

        @Override
        public ProcessResult execute(List<String> command) {
            return result;
        }
    }

    /** A ProcessExecutor that records the command before returning a canned result. */
    static final class CapturingProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;
        private List<String> lastCommand;

        CapturingProcessExecutor(ProcessResult result) {
            this.result = result;
        }

        @Override
        public ProcessResult execute(List<String> command) {
            this.lastCommand = command;
            return result;
        }

        List<String> lastCommand() {
            return lastCommand;
        }
    }
}
