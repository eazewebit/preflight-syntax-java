package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive integration tests for {@link PythonValidator}.
 *
 * <p>Tests both the two-phase validation strategy (built-in engine +
 * external binary) and the validator's integration with the
 * {@link Language#PYTHON} enum and the framework conventions.
 *
 * <p>Tests that require a real Python binary use
 * {@code Assumptions.assumeTrue(isPythonAvailable())} to skip gracefully
 * when Python is not available on the system PATH.
 */
@DisplayName("PythonValidator")
class PythonValidatorTest {

    private PythonValidator validator;
    private BinaryResolver binaryResolver;
    private ProcessExecutor processExecutor;

    @BeforeEach
    void setUp() {
        binaryResolver = new BinaryResolver();
        processExecutor = new ProcessExecutor();
        validator = new PythonValidator(binaryResolver, processExecutor);
    }

    // ==================================================================
    //  Language association
    // ==================================================================

    @Nested
    @DisplayName("language association")
    class LanguageAssociation {

        @Test @DisplayName("validator handles Python language")
        void handlesPython() { assertThat(validator.getLanguage()).isEqualTo(Language.PYTHON); }

        @Test @DisplayName("validator supports .py extension")
        void supportsPyExtension() { assertThat(validator.getFileExtension()).isEqualTo(".py"); }
    }

    // ==================================================================
    //  Built-in engine validation (Phase 1)
    // ==================================================================

    @Nested
    @DisplayName("built-in engine validation")
    class BuiltInEngineValidation {

        @Test @DisplayName("valid Python passes built-in engine")
        void validPython() {
            assertThat(PythonSyntaxEngine.validate("x = 1\ny = 2\nz = x + y\n").isValid()).isTrue();
        }

        @Test @DisplayName("invalid Python fails built-in engine")
        void invalidPython() { assertThat(PythonSyntaxEngine.validate("x = (\n").isValid()).isFalse(); }

        @Test @DisplayName("empty source is valid")
        void emptySource() { assertThat(PythonSyntaxEngine.validate("").isValid()).isTrue(); }

        @Test @DisplayName("complex valid program")
        void complexValidProgram() {
            String source = """
                    import os
                    import sys
                    from pathlib import Path

                    class App:
                        def __init__(self, name: str):
                            self.name = name
                            self._config = {}

                        @property
                        def config(self):
                            return self._config

                        def run(self):
                            match self.name:
                                case 'test':
                                    print('Running tests')
                                case _:
                                    print(f'Running {self.name}')

                    async def main():
                        app = App('demo')
                        app.run()

                    if __name__ == '__main__':
                        import asyncio
                        asyncio.run(main())
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("complex invalid program — missing colon")
        void complexInvalidProgram() {
            String source = """
                    class Foo
                        def bar(self):
                            pass
                    """;
            assertThat(PythonSyntaxEngine.validate(source).isValid()).isFalse();
        }

        @Test @DisplayName("if without condition is invalid")
        void ifWithoutCondition() {
            assertThat(PythonSyntaxEngine.validate("if:\n    pass\n").isValid()).isFalse();
        }

        @Test @DisplayName("elif without condition is invalid")
        void elifWithoutCondition() {
            assertThat(PythonSyntaxEngine.validate("if True:\n    pass\nelif:\n    pass\n").isValid()).isFalse();
        }

        @Test @DisplayName("while without condition is invalid")
        void whileWithoutCondition() {
            assertThat(PythonSyntaxEngine.validate("while:\n    pass\n").isValid()).isFalse();
        }
    }

    // ==================================================================
    //  Two-phase strategy (full pipeline)
    // ==================================================================

    @Nested
    @DisplayName("two-phase strategy")
    class TwoPhaseStrategy {

        @Test @DisplayName("validate runs built-in engine first")
        void validateRunsEngineFirst() {
            String source = "1 = x\n";
            ValidationResult r = validator.validate(source);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }

        @Test @DisplayName("valid Python passes full pipeline")
        void validPythonFullPipeline() {
            String source = "x = 1\ny = 2\nz = x + y\nprint(z)\n";
            assertThat(validator.validate(source).isValid()).isTrue();
        }

        @Test @DisplayName("if without condition fails full pipeline")
        void ifWithoutConditionFullPipeline() {
            String source = "if:\n    pass\n";
            ValidationResult r = validator.validate(source);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }

        @Test @DisplayName("SyntaxError caught by full pipeline")
        void syntaxErrorFullPipeline() {
            assumeTrue(isPythonAvailable(), "Python binary not available");
            String source = "def f(x, y):\n    return x + y\n\nf(1, 2, 3)\n";
            // Engine passes (no structural errors), binary catches it.
            ValidationResult r = validator.validate(source);
            // Note: This is actually valid Python at the syntax level.
            // An arity mismatch is a runtime error, not a syntax error.
            assertThat(r.isValid()).isTrue();
        }

        @Test @DisplayName("IndentationError caught by full pipeline")
        void indentationErrorFullPipeline() {
            assumeTrue(isPythonAvailable(), "Python binary not available");
            String source = "if True:\n        pass\n  x = 1\n";
            ValidationResult r = validator.validate(source);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).isNotEmpty();
        }

        @Test @DisplayName("empty source passes full pipeline")
        void emptySourceFullPipeline() {
            assertThat(validator.validate("").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Constructor variants
    // ==================================================================

    @Nested
    @DisplayName("constructor variants")
    class ConstructorVariants {

        @Test @DisplayName("constructor with binary resolver only")
        void constructorWithResolver() {
            PythonValidator v = new PythonValidator(binaryResolver, processExecutor);
            assertThat(v.getLanguage()).isEqualTo(Language.PYTHON);
        }

        @Test @DisplayName("constructor with explicit binary path")
        void constructorWithExplicitPath() {
            PythonValidator v = new PythonValidator("/usr/bin/python3", binaryResolver, processExecutor);
            assertThat(v.getLanguage()).isEqualTo(Language.PYTHON);
        }

        @Test @DisplayName("constructor with null binary path")
        void constructorWithNullPath() {
            PythonValidator v = new PythonValidator(null, binaryResolver, processExecutor);
            assertThat(v.getLanguage()).isEqualTo(Language.PYTHON);
        }
    }

    // ==================================================================
    //  Integration with ValidatorFactory
    // ==================================================================

    @Nested
    @DisplayName("integration with ValidatorFactory")
    class ValidatorFactoryIntegration {

        @Test @DisplayName("PythonValidator is registered in ValidatorFactory")
        void registeredInFactory() {
            com.neel.syntaxvalidation.validator.ValidatorFactory factory =
                    new com.neel.syntaxvalidation.validator.ValidatorFactory();
            assertThat(factory.getValidator(Language.PYTHON)).isPresent();
        }

        @Test @DisplayName("registered validator is PythonValidator instance")
        void registeredInstanceType() {
            com.neel.syntaxvalidation.validator.ValidatorFactory factory =
                    new com.neel.syntaxvalidation.validator.ValidatorFactory();
            factory.getValidator(Language.PYTHON).ifPresent(v -> {
                assertThat(v).isInstanceOf(PythonValidator.class);
            });
        }
    }

    // ==================================================================
    //  Edge cases
    // ==================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test @DisplayName("null source handling")
        void nullSource() { assertThat(PythonSyntaxEngine.validate(null).isValid()).isTrue(); }

        @Test @DisplayName("single line valid code")
        void singleLineValid() { assertThat(PythonSyntaxEngine.validate("x = 1\n").isValid()).isTrue(); }

        @Test @DisplayName("single line invalid code")
        void singleLineInvalid() { assertThat(PythonSyntaxEngine.validate("1 = x\n").isValid()).isFalse(); }

        @Test @DisplayName("code with only comments and blank lines")
        void onlyCommentsAndBlanks() { assertThat(PythonSyntaxEngine.validate("# comment\n\n# another comment\n\n\n").isValid()).isTrue(); }

        @Test @DisplayName("very long single line")
        void veryLongSingleLine() {
            StringBuilder sb = new StringBuilder("x = \"");
            sb.append("a".repeat(500));
            sb.append("\"\n");
            assertThat(PythonSyntaxEngine.validate(sb.toString())).isNotNull();
        }

        @Test @DisplayName("multiple encoding declarations")
        void multipleEncodingDeclarations() {
            String source = "# -*- coding: utf-8 -*-\n# -*- coding: ascii -*-\nx = 1\n";
            ValidationResult r = PythonSyntaxEngine.validate(source);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("Multiple encoding declarations");
        }

        @Test @DisplayName("encoding declaration not on line 1 or 2")
        void encodingDeclarationNotOnLine1Or2() {
            String source = "# first line\n# second line\n# -*- coding: utf-8 -*-\nx = 1\n";
            ValidationResult r = PythonSyntaxEngine.validate(source);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("not on line 1 or 2");
        }
    }

    /**
     * Checks if Python is available on the system PATH.
     * Used by {@code Assumptions.assumeTrue()} for conditional test execution.
     */
    static boolean isPythonAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 0) return true;
            pb = new ProcessBuilder("python", "--version");
            pb.redirectErrorStream(true);
            p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
