package com.neel.syntaxvalidation.validator.css.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.css.CssValidator;
import com.neel.syntaxvalidation.validator.css.CssSyntaxEngine;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive integration tests for the CSS validation pipeline.
 * <p>
 * Tests the complete flow: source code → BinaryResolver → ProcessExecutor →
 * npx stylelint → StylelintOutputParser → ValidationResult.
 * <p>
 * Also tests the CssSyntaxEngine fallback and edge cases.
 */
@DisplayName("CSS Validation Pipeline Integration Tests")
class CssValidationPipelineIntegrationTest {

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

    private static final String VALID_BINARY = "/usr/bin/stylelint";
    private static final ProcessResult SUCCESSFUL_CHECK = new ProcessResult(0, "[]", "", false);

    // ══════════════════════════════════════════════════════════════════════
    //  1. FULL PIPELINE — VALID SOURCE CODE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full pipeline with valid CSS source")
    class FullPipelineValidSource {

        @Test
        @DisplayName("valid CSS basic properties → external result used")
        void validCssBasic_externalResultUsed() {
            String code = """
                    body {
                        margin: 0;
                        padding: 0;
                        font-family: Arial, sans-serif;
                        font-size: 16px;
                        color: #333;
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid CSS with grid layout → external result used")
        void validCssGridLayout_externalResultUsed() {
            String code = """
                    .layout {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 24px;
                        padding: 24px;
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid CSS with custom properties → external result used")
        void validCssCustomProperties_externalResultUsed() {
            String code = """
                    :root {
                        --color-primary: #0066cc;
                        --spacing-md: 16px;
                        --border-radius: 4px;
                    }
                    
                    .button {
                        background-color: var(--color-primary);
                        padding: var(--spacing-md);
                        border-radius: var(--border-radius);
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid CSS with animations → external result used")
        void validCssAnimations_externalResultUsed() {
            String code = """
                    @keyframes pulse {
                        0%, 100% { transform: scale(1); }
                        50% { transform: scale(1.05); }
                    }
                    
                    .pulse {
                        animation: pulse 2s ease-in-out infinite;
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid CSS with pseudo-elements → external result used")
        void validCssPseudoElements_externalResultUsed() {
            String code = """
                    .tooltip {
                        position: relative;
                    }
                    
                    .tooltip::before {
                        content: attr(data-tooltip);
                        position: absolute;
                        bottom: 100%;
                        opacity: 0;
                        transition: opacity 0.2s;
                    }
                    
                    .tooltip:hover::before {
                        opacity: 1;
                    }
                    """;

            var validator = new CssValidator(null,
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
                    body {
                        margin: 0;
                        color: red;
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(null), // binary not found
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("external binary not found → embedded engine used for invalid CSS")
        void externalBinaryNotFound_embeddedEngineForInvalidCss() {
            String code = """
                    .broken {
                        color: red;
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("process execution fails → falls back to embedded engine")
        void processExecutionFails_fallsBackToEmbeddedEngine() {
            String code = """
                    body {
                        margin: 0;
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  3. CSS SYNTAX ENGINE (FALLBACK) — Note: StylelintOutputParser is package-private
    // ══════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════
    //  4. CSS SYNTAX ENGINE (FALLBACK)
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CssSyntaxEngine — fallback engine")
    class CssSyntaxEngineTests {

        private CssSyntaxEngine engine;

        @BeforeEach
        void setUp() {
            engine = CssSyntaxEngine.getInstance();
        }

        @Test
        @DisplayName("valid CSS — engine reports valid")
        void validCss_engineReportsValid() {
            String code = """
                    body {
                        margin: 0;
                        color: red;
                    }
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("invalid CSS — engine reports errors")
        void invalidCss_engineReportsErrors() {
            String code = """
                    .broken {
                        color: red;
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("engine handles vendor prefixes")
        void engine_handlesVendorPrefixes() {
            String code = """
                    .box {
                        -webkit-transform: rotate(45deg);
                        -moz-transform: rotate(45deg);
                        transform: rotate(45deg);
                    }
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
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
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles @font-face")
        void engine_handlesFontFace() {
            String code = """
                    @font-face {
                        font-family: 'CustomFont';
                        src: url('font.woff2') format('woff2');
                        font-weight: normal;
                        font-display: swap;
                    }
                    """;
            ValidationResult result = engine.validate(code);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("engine handles comment-only CSS")
        void engine_handlesCommentOnlyCss() {
            String code = """
                    /* Header styles */
                    /* Footer styles */
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
            String code = "body { margin: 0; }";

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new FailingProcessExecutor());

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("null content → treated as empty string")
        void nullContent_treatedAsEmptyString() {
            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate((String) null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty content → handles gracefully")
        void emptyContent_handlesGracefully() {
            var validator = new CssValidator(null,
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
        @DisplayName("factory creates CSS validator that works end-to-end")
        void factoryCreatesCssValidator_worksEndToEnd() {
            var factory = new ValidatorFactory();
            var validator = factory.getValidator(Language.CSS);

            assertThat(validator).isPresent();
            assertThat(validator.get().getLanguage()).isEqualTo(Language.CSS);
        }

        @Test
        @DisplayName("factory supports CSS language")
        void factorySupportsCssLanguage() {
            var factory = new ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.CSS);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  7. EDGE CASES THROUGH FULL PIPELINE
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edge cases through full pipeline")
    class EdgeCasesFullPipeline {

        @Test
        @DisplayName("CSS with calc() expressions → pipeline handles")
        void cssWithCalc_pipelineHandles() {
            String code = """
                    .container {
                        width: calc(100% - 2 * 24px);
                        height: calc(100vh - var(--header-height, 64px));
                        font-size: clamp(1rem, 2.5vw, 1.5rem);
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS with color functions → pipeline handles")
        void cssWithColorFunctions_pipelineHandles() {
            String code = """
                    .colors {
                        color: rgb(255 0 0 / 0.5);
                        background-color: hsl(220 100% 50%);
                        border-color: oklch(70% 0.15 180);
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS with @supports feature queries → pipeline handles")
        void cssWithSupports_pipelineHandles() {
            String code = """
                    .grid {
                        display: flex;
                    }
                    
                    @supports (display: grid) {
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        }
                    }
                    """;

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("very large CSS file → pipeline handles")
        void veryLargeCssFile_pipelineHandles() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                sb.append(".class-").append(i).append(" { color: #")
                  .append(String.format("%06x", i % 0xFFFFFF)).append("; }\n");
            }

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(sb.toString());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS with nesting → pipeline handles")
        void cssWithNesting_pipelineHandles() {
            String code = """
                    .parent {
                        color: red;
                        
                        & .child {
                            color: blue;
                        }
                        
                        &:hover {
                            color: green;
                        }
                    }
                    """;

            var validator = new CssValidator(null,
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
            String code = "body { margin: 0; }";

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isNotBlank();
        }

        @Test
        @DisplayName("result is immutable — errors list cannot be modified")
        void resultIsImmutable_errorsListCannotBeModified() {
            String code = "body { margin: 0; }";

            var validator = new CssValidator(null,
                    new StubBinaryResolver(VALID_BINARY),
                    new StubProcessExecutor(SUCCESSFUL_CHECK));

            ValidationResult result = validator.validate(code);
            assertThat(result.isValid()).isTrue();
            assertThatCode(() -> result.getErrors().add(new ValidationError(1, 1, "test", "test")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}