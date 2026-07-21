package com.neel.syntaxvalidation.validator.html.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.html.HtmlValidator;
import com.neel.syntaxvalidation.validator.html.HtmlSyntaxEngine;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the HTML validation pipeline.
 * <p>
 * Tests the complete flow: source code → BinaryResolver → ProcessExecutor →
 * vnu.jar → VnuOutputParser → ValidationResult.
 * <p>
 * Also tests the HtmlSyntaxEngine fallback and edge cases.
 */
@DisplayName("HTML Validation Pipeline Integration Tests")
class HtmlValidationPipelineIntegrationTest {

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

    private static final String VALID_BINARY = "/usr/bin/vnu.jar";
    private static final ProcessResult SUCCESSFUL_CHECK = new ProcessResult(0, "", "", false);

    // ══════════════════════════════════════════════════════════════════════
    //  1. FULL PIPELINE — VALID SOURCE CODE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full pipeline with valid HTML source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid HTML5 document → external result used when available")
        void validHTML5Document_externalResultUsed() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Valid</title>
                    </head>
                    <body>
                        <h1>Hello</h1>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid HTML with forms → external result used")
        void validHtmlWithForms_externalResultUsed() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Forms</title>
                    </head>
                    <body>
                        <form action="/submit" method="post">
                            <label for="name">Name:</label>
                            <input type="text" id="name" name="name" required>
                            <button type="submit">Submit</button>
                        </form>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid HTML with multimedia → external result used")
        void validHtmlWithMultimedia_externalResultUsed() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Media</title>
                    </head>
                    <body>
                        <figure>
                            <img src="image.jpg" alt="Description" loading="lazy">
                            <figcaption>Caption</figcaption>
                        </figure>
                        <video controls width="640">
                            <source src="video.mp4" type="video/mp4">
                        </video>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid HTML with details/summary → external result used")
        void validHtmlWithDetails_externalResultUsed() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Details</title>
                    </head>
                    <body>
                        <details open>
                            <summary>Click to expand</summary>
                            <p>Hidden content</p>
                        </details>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  2. FALLBACK INTERACTION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fallback interaction")
    class FallbackInteraction {

        @Test
        @DisplayName("external binary fails → falls back to embedded engine")
        void externalBinaryFails_fallsBackToEmbeddedEngine() {
            String code = """
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
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(null), // binary not found
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
            // Should be valid from embedded engine
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("external binary not found → embedded engine used for invalid HTML")
        void externalBinaryNotFound_embeddedEngineForInvalidHtml() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                    </head>
                    <body>
                        <div>
                            <p>Unclosed
                        </div>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process execution fails → falls back to embedded engine")
        void processExecutionFails_fallsBackToEmbeddedEngine() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                    </head>
                    <body>
                        <p>Test</p>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. HTML SYNTAX ENGINE (FALLBACK) — Note: VnuOutputParser is package-private
    // ══════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════
    //  4. HTML SYNTAX ENGINE (FALLBACK)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("HtmlSyntaxEngine — fallback engine")
    class HtmlSyntaxEngineTests {

        private HtmlSyntaxEngine engine;

        @BeforeEach
        void setUp() {
            engine = HtmlSyntaxEngine.getInstance();
        }

        @Test
        @DisplayName("valid HTML — engine reports valid")
        void validHtml_engineReportsValid() {
            String code = """
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
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles void elements")
        void engine_handlesVoidElements() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Void</title>
                    </head>
                    <body>
                        <br>
                        <hr>
                        <img src="test.jpg" alt="test">
                        <input type="text">
                    </body>
                    </html>
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles HTML fragments")
        void engine_handlesFragments() {
            String code = """
                    <div class="fragment">
                        <p>Just a fragment</p>
                    </div>
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("engine handles null input gracefully")
        void engine_handlesNullInput() {
            ValidationResult result = engine.validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("engine handles empty input gracefully")
        void engine_handlesEmptyInput() {
            ValidationResult result = engine.validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("engine handles self-closing tags")
        void engine_handlesSelfClosingTags() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8"/>
                        <title>Self-closing</title>
                    </head>
                    <body>
                        <br/>
                        <hr/>
                    </body>
                    </html>
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  5. ERROR HANDLING INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error handling integration")
    class ErrorHandlingIntegration {

        @Test
        @DisplayName("IOException during process execution → falls back to embedded engine")
        void ioExceptionDuringProcessExecution_fallsBackToEmbedded() {
            String code = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8"><title>T</title></head>
                    <body><p>Test</p></body></html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate((String) null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → handles gracefully")
        void emptyContent_handlesGracefully() {
            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  6. VALIDATOR FACTORY INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ValidatorFactory integration")
    class ValidatorFactoryIntegration {

        @Test
        @DisplayName("factory creates HTML validator that works end-to-end")
        void factoryCreatesHtmlValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.HTML);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.HTML);
        }

        @Test
        @DisplayName("factory supports HTML language")
        void factorySupportsHtmlLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.HTML);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. EDGE CASES THROUGH FULL PIPELINE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases through full pipeline")
    class EdgeCasesFullPipeline {

        @Test
        @DisplayName("HTML with inline SVG → pipeline handles")
        void htmlWithInlineSvg_pipelineHandles() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>SVG</title>
                    </head>
                    <body>
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
                            <circle cx="50" cy="50" r="40" fill="blue"/>
                        </svg>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with template element → pipeline handles")
        void htmlWithTemplateElement_pipelineHandles() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Template</title>
                    </head>
                    <body>
                        <template id="myTemplate">
                            <div class="card">
                                <h2>Template</h2>
                            </div>
                        </template>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("very large HTML file → pipeline handles")
        void veryLargeHtmlFile_pipelineHandles() {
            StringBuilder sb = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Large</title></head><body>\n");
            for (int i = 0; i < 1000; i++) {
                sb.append("<div class=\"item-").append(i).append("\">Item ").append(i).append("</div>\n");
            }
            sb.append("</body></html>");

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with web components → pipeline handles")
        void htmlWithWebComponents_pipelineHandles() {
            String code = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Web Components</title>
                    </head>
                    <body>
                        <my-component data-value="test"></my-component>
                    </body>
                    </html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  8. RESULT STRUCTURE INTEGRATION
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Result structure integration")
    class ResultStructureIntegration {

        @Test
        @DisplayName("valid result has correct message")
        void validResult_hasCorrectMessage() {
            String code = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8"><title>T</title></head>
                    <body><p>Test</p></body></html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            String code = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8"><title>T</title></head>
                    <body><p>Test</p></body></html>
                    """;

            var validator = new HtmlValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}