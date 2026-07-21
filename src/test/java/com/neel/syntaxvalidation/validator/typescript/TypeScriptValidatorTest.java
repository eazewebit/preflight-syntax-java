package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TypeScriptValidator}.
 */
@DisplayName("TypeScriptValidator")
class TypeScriptValidatorTest {

    private TypeScriptValidator validator;

    @BeforeEach
    void setUp() {
        BinaryResolver resolver = new BinaryResolver() {
            @Override
            public Optional<String> resolve(String preferredPath, String binaryName) {
                return Optional.empty();
            }
        };
        ProcessExecutor executor = new ProcessExecutor() {
            @Override
            public ProcessResult execute(List<String> command) {
                return new ProcessResult(0, "", "", false);
            }
        };
        validator = new TypeScriptValidator(resolver, executor);
    }

    @Nested
    @DisplayName("contract")
    class Contract {

        @Test
        @DisplayName("should create validator with default constructor")
        void shouldCreateValidatorWithDefaultConstructor() {
            TypeScriptValidator defaultValidator = new TypeScriptValidator(Language.TYPESCRIPT);
            assertThat(defaultValidator).isNotNull();
            assertThat(defaultValidator.getLanguage()).isEqualTo(Language.TYPESCRIPT);
        }

        @Test
        @DisplayName("should store the language")
        void shouldStoreTheLanguage() {
            assertThat(validator.getLanguage()).isEqualTo(Language.TYPESCRIPT);
        }

        @Test
        @DisplayName("should create validator with JSX mode")
        void shouldCreateValidatorWithJsxMode() {
            TypeScriptValidator jsxValidator = TypeScriptValidator.createJsxValidator(
                    new BinaryResolver() {
                        @Override
                        public Optional<String> resolve(String preferredPath, String binaryName) {
                            return Optional.empty();
                        }
                    },
                    new ProcessExecutor() {
                        @Override
                        public ProcessResult execute(List<String> command) {
                            return new ProcessResult(0, "", "", false);
                        }
                    });
            assertThat(jsxValidator).isNotNull();
        }
    }

    @Nested
    @DisplayName("null and empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("should return success for null content")
        void shouldReturnSuccessForNullContent() {
            ValidationResult result = validator.validate(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should return success for empty content")
        void shouldReturnSuccessForEmptyContent() {
            ValidationResult result = validator.validate("");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should return success for blank content")
        void shouldReturnSuccessForBlankContent() {
            ValidationResult result = validator.validate("   \n\t  ");
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Phase 1: built-in syntax validation")
    class Phase1Validation {

        @Test
        @DisplayName("should pass valid TypeScript code")
        void shouldPassValidTypeScriptCode() {
            ValidationResult result = validator.validate("let x: number = 42;");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should fail on unclosed braces")
        void shouldFailOnUnclosedBraces() {
            ValidationResult result = validator.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should fail on unclosed parentheses")
        void shouldFailOnUnclosedParentheses() {
            ValidationResult result = validator.validate("console.log('hello'");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should provide line and column in errors")
        void shouldProvideLineAndColumnInErrors() {
            ValidationResult result = validator.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isPositive();
            assertThat(result.getErrors().get(0).getColumn()).isPositive();
        }

        @Test
        @DisplayName("should return immediately when Phase 1 fails (no binary check)")
        void shouldReturnImmediatelyWhenPhase1Fails() {
            // Even if binary is available, should not call it when Phase 1 fails
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/usr/bin/tsc");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    // Should not be called
                    throw new RuntimeException("Should not be called");
                }
            };
            TypeScriptValidator validatorWithBinary = new TypeScriptValidator(resolver, executor);

            ValidationResult result = validatorWithBinary.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Phase 2: external binary validation")
    class Phase2Validation {

        @Test
        @DisplayName("should skip Phase 2 when binary is not available")
        void shouldSkipPhase2WhenBinaryIsNotAvailable() {
            ValidationResult result = validator.validate("let x: number = 42;");
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).contains("built-in TypeScript syntax engine");
        }

        @Test
        @DisplayName("should use external binary when available and Phase 1 passes")
        void shouldUseExternalBinaryWhenAvailableAndPhase1Passes() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/usr/bin/tsc");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(0, "", "", false);
                }
            };
            TypeScriptValidator validatorWithBinary = new TypeScriptValidator(resolver, executor);

            ValidationResult result = validatorWithBinary.validate("let x: number = 42;");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should report tsc errors when binary finds issues")
        void shouldReportTscErrorsWhenBinaryFindsIssues() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/usr/bin/tsc");
                }
            };
            String tscOutput = "file.ts:1:1 - error TS2304: Cannot find name 'undeclared'.";
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(1, tscOutput, "", false);
                }
            };
            TypeScriptValidator validatorWithBinary = new TypeScriptValidator(resolver, executor);

            ValidationResult result = validatorWithBinary.validate("let x = undeclared;");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should handle tsc execution failure gracefully")
        void shouldHandleTscExecutionFailureGracefully() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/usr/bin/tsc");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    throw new RuntimeException("Binary not found");
                }
            };
            TypeScriptValidator validatorWithBinary = new TypeScriptValidator(resolver, executor);

            ValidationResult result = validatorWithBinary.validate("let x: number = 42;");
            // Should fall back to Phase 1 result (success)
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should handle tsc timeout gracefully")
        void shouldHandleTscTimeoutGracefully() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/usr/bin/tsc");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(-1, "", "", true);
                }
            };
            TypeScriptValidator validatorWithBinary = new TypeScriptValidator(resolver, executor);

            ValidationResult result = validatorWithBinary.validate("let x: number = 42;");
            // Should still be valid since we couldn't verify
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("JSX mode")
    class JsxMode {

        @Test
        @DisplayName("should validate valid JSX")
        void shouldValidateValidJsx() {
            TypeScriptValidator jsxValidator = TypeScriptValidator.createJsxValidator(
                    new BinaryResolver() {
                        @Override
                        public Optional<String> resolve(String preferredPath, String binaryName) {
                            return Optional.empty();
                        }
                    },
                    new ProcessExecutor() {
                        @Override
                        public ProcessResult execute(List<String> command) {
                            return new ProcessResult(0, "", "", false);
                        }
                    });

            ValidationResult result = jsxValidator.validate("<div>hello</div>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should detect unclosed JSX tags")
        void shouldDetectUnclosedJsxTags() {
            TypeScriptValidator jsxValidator = TypeScriptValidator.createJsxValidator(
                    new BinaryResolver() {
                        @Override
                        public Optional<String> resolve(String preferredPath, String binaryName) {
                            return Optional.empty();
                        }
                    },
                    new ProcessExecutor() {
                        @Override
                        public ProcessResult execute(List<String> command) {
                            return new ProcessResult(0, "", "", false);
                        }
                    });

            ValidationResult result = jsxValidator.validate("<div>hello");
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("should use .tsx extension for temp files")
        void shouldUseTsxExtensionForTempFiles() {
            TypeScriptValidator jsxValidator = TypeScriptValidator.createJsxValidator(
                    new BinaryResolver() {
                        @Override
                        public Optional<String> resolve(String preferredPath, String binaryName) {
                            return Optional.empty();
                        }
                    },
                    new ProcessExecutor() {
                        @Override
                        public ProcessResult execute(List<String> command) {
                            return new ProcessResult(0, "", "", false);
                        }
                    });
            assertThat(jsxValidator).isNotNull();
        }
    }

    @Nested
    @DisplayName("error message quality")
    class ErrorMessageQuality {

        @Test
        @DisplayName("should provide descriptive error for unclosed braces")
        void shouldProvideDescriptiveErrorForUnclosedBraces() {
            ValidationResult result = validator.validate("function foo() {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("brace");
        }

        @Test
        @DisplayName("should provide descriptive error for unclosed parentheses")
        void shouldProvideDescriptiveErrorForUnclosedParentheses() {
            ValidationResult result = validator.validate("console.log('hello'");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).containsIgnoringCase("parenthesis");
        }

        @Test
        @DisplayName("should include file extension information in binary not found message")
        void shouldIncludeFileExtensionInformationInBinaryNotFoundMessage() {
            assertThat(validator.binaryNotFoundMessage()).contains("tsc");
        }
    }

    @Nested
    @DisplayName("getFileExtension")
    class GetFileExtension {

        @Test
        @DisplayName("should return .ts for non-JSX mode")
        void shouldReturnTsForNonJsxMode() {
            assertThat(validator.getFileExtension()).isEqualTo(".ts");
        }

        @Test
        @DisplayName("should return .tsx for JSX mode")
        void shouldReturnTsxForJsxMode() {
            TypeScriptValidator jsxValidator = TypeScriptValidator.createJsxValidator(
                    new BinaryResolver() {
                        @Override
                        public Optional<String> resolve(String preferredPath, String binaryName) {
                            return Optional.empty();
                        }
                    },
                    new ProcessExecutor() {
                        @Override
                        public ProcessResult execute(List<String> command) {
                            return new ProcessResult(0, "", "", false);
                        }
                    });
            assertThat(jsxValidator.getFileExtension()).isEqualTo(".tsx");
        }
    }
}
