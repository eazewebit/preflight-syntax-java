package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link HtmlValidator}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Language identification</li>
 *   <li>External binary (vnu.jar) success and failure scenarios</li>
 *   <li>Fallback to the embedded {@link HtmlSyntaxEngine} when the binary is
 *       unavailable</li>
 *   <li>Command construction for .jar and wrapper-script binaries</li>
 *   <li>Timeout handling</li>
 *   <li>Null-content handling</li>
 *   <li>Error message propagation</li>
 * </ul>
 */
@DisplayName("HtmlValidator")
class HtmlValidatorTest {

    // -----------------------------------------------------------------
    //  Language identification
    // -----------------------------------------------------------------

    @Test
    @DisplayName("getLanguage() returns HTML")
    void getLanguage_returnsHtml() {
        assertThat(new HtmlValidator().getLanguage()).isEqualTo(Language.HTML);
    }

    // -----------------------------------------------------------------
    //  External binary: vnu.jar produces valid result
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("when vnu.jar reports success")
    class VnuReportsSuccess {

        @Test
        @DisplayName("returns valid when vnu produces no errors")
        void returnsValid() {
            String vnuOutput = """
                    {"messages": []}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(0, vnuOutput, "", false));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate("<!DOCTYPE html><html><head></head><body></body></html>");

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("returns valid when vnu produces empty stdout")
        void returnsValid_onEmptyOutput() {
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(0, "", "", false));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate("<p>Hello</p>");

            assertThat(result.isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  External binary: vnu.jar reports errors
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("when vnu.jar reports errors")
    class VnuReportsErrors {

        @Test
        @DisplayName("returns invalid with parsed errors from vnu JSON output")
        void returnsInvalid_withParsedErrors() {
            String vnuOutput = """
                    {"messages": [
                      {"type": "error", "message": "Stray end tag 'div'.", "lastLine": 5, "lastColumn": 10},
                      {"type": "error", "message": "Element 'p' not allowed as child of 'head'.", "lastLine": 3, "lastColumn": 5},
                      {"type": "info", "message": "The document is valid.", "lastLine": 0, "lastColumn": 0}
                    ]}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(1, vnuOutput, "", false));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate("<html><head><p>Bad</p></head><body></div></body></html>");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getMessage()).contains("Stray end tag");
            assertThat(result.getErrors().get(1).getMessage()).contains("not allowed as child");
        }

        @Test
        @DisplayName("returns invalid when vnu outputs to stderr instead of stdout")
        void returnsInvalid_withStderrOutput() {
            String vnuStderr = """
                    {"messages": [
                      {"type": "error", "message": "Missing DOCTYPE.", "lastLine": 1, "lastColumn": 1}
                    ]}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(1, "", vnuStderr, false));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate("<html><head></head><body></body></html>");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("Missing DOCTYPE");
        }
    }

    // -----------------------------------------------------------------
    //  External binary: timeout
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("when vnu.jar times out")
    class VnuTimeout {

        @Test
        @DisplayName("returns invalid on timeout")
        void returnsInvalid_onTimeout() {
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(-1, "", "timeout", true));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate("<p>Content</p>");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }
    }

    // -----------------------------------------------------------------
    //  Fallback to embedded engine
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("fallback to embedded HTML syntax engine")
    class FallbackValidation {

        @Test
        @DisplayName("falls back to embedded engine when binary not found")
        void fallsBackToEmbedded_whenBinaryNotFound() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());

            ValidationResult result = validator.validate("<p>Valid content</p>");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("vnu");
            assertThat(result.getMessage()).containsIgnoringCase("not installed");
        }

        @Test
        @DisplayName("embedded engine detects errors when binary not found")
        void embeddedEngine_detectsErrors_whenBinaryNotFound() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());

            ValidationResult result = validator.validate("<div><p>Unclosed</div>");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("falls back to embedded engine when process throws IOException")
        void fallsBackToEmbedded_onIOException() {
            ProcessExecutor failing = new FailingProcessExecutor(
                    new java.io.IOException("Cannot run process"));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu"));
            HtmlValidator validator = new HtmlValidator(null, present, failing);

            ValidationResult result = validator.validate("<p>Content</p>");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }

        @Test
        @DisplayName("returns valid from embedded engine for valid HTML when binary not found")
        void embeddedEngine_validHtml_returnsValid() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());

            String validHtml = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><meta charset="UTF-8"><title>Test</title></head>
                    <body>
                      <div>
                        <h1>Title</h1>
                        <p>Paragraph with <strong>bold</strong> and <em>italic</em>.</p>
                      </div>
                      <br>
                      <hr>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(validHtml);

            assertThat(result.isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  Null content handling
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("null content handling")
    class NullContent {

        @Test
        @DisplayName("treats null content as valid")
        void nullContent_isValid() {
            ProcessExecutor stub = new StubProcessExecutor(
                    new ProcessResult(0, "", "", false));
            HtmlValidator validator = validatorWithBinary(stub);

            ValidationResult result = validator.validate(null);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("treats null content as valid via embedded engine fallback")
        void nullContent_fallback_isValid() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());

            ValidationResult result = validator.validate(null);

            assertThat(result.isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  Command construction
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("command construction")
    class CommandConstruction {

        @Test
        @DisplayName("builds java -jar command for .jar binary path")
        void jarPath_buildsJavaJarCommand() {
            CapturingProcessExecutor capturing = new CapturingProcessExecutor(
                    new ProcessResult(0, "", "", false));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu.jar"));
            HtmlValidator validator = new HtmlValidator(null, present, capturing);

            validator.validate("<p>Content</p>");

            List<String> cmd = capturing.lastCommand();
            assertThat(cmd.get(0)).isEqualTo("java");
            assertThat(cmd.get(1)).isEqualTo("-jar");
            assertThat(cmd.get(2)).isEqualTo("/usr/bin/vnu.jar");
            assertThat(cmd).contains("--format", "json");
        }

        @Test
        @DisplayName("builds direct command for wrapper script binary path")
        void wrapperPath_buildsDirectCommand() {
            CapturingProcessExecutor capturing = new CapturingProcessExecutor(
                    new ProcessResult(0, "", "", false));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/local/bin/vnu"));
            HtmlValidator validator = new HtmlValidator(null, present, capturing);

            validator.validate("<p>Content</p>");

            List<String> cmd = capturing.lastCommand();
            assertThat(cmd.get(0)).isEqualTo("/usr/local/bin/vnu");
            assertThat(cmd).contains("--format", "json");
        }
    }

    // -----------------------------------------------------------------
    //  Test doubles
    // -----------------------------------------------------------------

    private static HtmlValidator validatorWithBinary(ProcessExecutor executor) {
        BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu"));
        return new HtmlValidator(null, present, executor);
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

    /** A ProcessExecutor that always throws an IOException. */
    static final class FailingProcessExecutor extends ProcessExecutor {
        private final java.io.IOException exception;

        FailingProcessExecutor(java.io.IOException exception) {
            this.exception = exception;
        }

        @Override
        public ProcessResult execute(List<String> command) throws java.io.IOException {
            throw exception;
        }
    }
}
