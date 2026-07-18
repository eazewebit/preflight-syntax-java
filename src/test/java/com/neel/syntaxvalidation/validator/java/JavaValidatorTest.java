package com.neel.syntaxvalidation.validator.java;

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
 * Comprehensive unit and integration tests for {@link JavaValidator}.
 *
 * <p>Tests cover the two-phase validation strategy, including the built-in
 * engine short-circuit, the {@code javac} unavailable fallback, and the full
 * binary pipeline using injectable collaborators.
 */
@DisplayName("JavaValidator")
class JavaValidatorTest {

    // ==================================================================
    //  Contract
    // ==================================================================

    @Nested
    @DisplayName("contract")
    class Contract {

        @Test
        void getLanguageReturnsJava() {
            assertThat(new JavaValidator().getLanguage()).isEqualTo(Language.JAVA);
        }

        @Test
        void defaultConstructorWorks() {
            JavaValidator validator = new JavaValidator();
            assertThat(validator).isNotNull();
        }

        @Test
        void preferredBinaryPathConstructorWorks() {
            JavaValidator validator = new JavaValidator("/usr/bin/javac");
            assertThat(validator).isNotNull();
        }
    }

    // ==================================================================
    //  Phase 1 — validateSource (pure engine)
    // ==================================================================

    @Nested
    @DisplayName("validateSource (pure engine)")
    class ValidateSource {

        private final JavaValidator validator = new JavaValidator();

        @Test
        void validClassIsValid() {
            assertThat(validator.validateSource("class Foo {}").isValid()).isTrue();
        }

        @Test
        void unclosedBraceIsInvalid() {
            assertThat(validator.validateSource("class Foo {").isValid()).isFalse();
        }

        @Test
        void unterminatedStringIsInvalid() {
            assertThat(validator.validateSource("String s = \"oops;").isValid()).isFalse();
        }

        @Test
        void conflictingModifiersInvalid() {
            assertThat(validator.validateSource("public private void m() {}").isValid()).isFalse();
        }

        @Test
        void recordValid() {
            assertThat(validator.validateSource("record R(int x) {}").isValid()).isTrue();
        }

        @Test
        void nullSourceHandled() {
            assertThat(validator.validateSource(null).isValid()).isTrue();
        }

        @Test
        void emptySourceHandled() {
            assertThat(validator.validateSource("").isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Two-phase validate()
    // ==================================================================

    @Nested
    @DisplayName("two-phase validate()")
    class TwoPhaseValidate {

        @Test
        @DisplayName("engine errors short-circuit before binary invocation")
        void engineErrorsShortCircuit() {
            JavaValidator validator = new JavaValidator("/nonexistent/javac") {
                // Even if javac were somehow available, engine errors must take priority.
            };
            ValidationResult result = validator.validate("class Foo {");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("valid source with no javac returns valid with fallback message")
        void validSourceNoJavac() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.empty();
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, new ProcessExecutor());
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).contains("built-in Java syntax engine");
        }

        @Test
        @DisplayName("valid source with javac delegates to binary pipeline (success)")
        void validSourceWithJavacSuccess() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/fake/javac");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(0, "", "", false);
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, executor);
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("valid source with javac error returns javac diagnostics")
        void validSourceWithJavacError() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/fake/javac");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    String stderr = "Test.java:3: error: ';' expected\n    int x\n         ^\n1 error\n";
                    return new ProcessResult(1, "", stderr, false);
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, executor);
            ValidationResult result = validator.validate("class Foo {\n  int x\n}");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("javac timeout is handled gracefully")
        void javacTimeout() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/fake/javac");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(-1, "", "", true);
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, executor);
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("timed out");
        }

        @Test
        @DisplayName("javac non-zero exit with no output is handled")
        void javacNonZeroNoOutput() {
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/fake/javac");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    return new ProcessResult(1, "", "", false);
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, executor);
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result.isValid()).isFalse();
        }
    }

    // ==================================================================
    //  buildCommand verification
    // ==================================================================

    @Nested
    @DisplayName("command construction")
    class CommandConstruction {

        @Test
        @DisplayName("buildCommand includes proc:none and temp file")
        void buildCommandContents() {
            // Use the executor to capture the command passed to javac.
            final List<String>[] captured = new List[]{null};
            BinaryResolver resolver = new BinaryResolver() {
                @Override
                public Optional<String> resolve(String preferredPath, String binaryName) {
                    return Optional.of("/fake/javac");
                }
            };
            ProcessExecutor executor = new ProcessExecutor() {
                @Override
                public ProcessResult execute(List<String> command) {
                    captured[0] = command;
                    return new ProcessResult(0, "", "", false);
                }
            };
            JavaValidator validator = new JavaValidator(null, resolver, executor);
            validator.validate("class Foo {}");

            assertThat(captured[0]).isNotNull();
            assertThat(captured[0].get(0)).isEqualTo("/fake/javac");
            assertThat(captured[0]).anyMatch(arg -> arg.equals("-proc:none"));
            assertThat(captured[0]).anyMatch(arg -> arg.endsWith(".java"));
        }
    }

    // ==================================================================
    //  Binary discovery from PATH
    // ==================================================================

    @Nested
    @DisplayName("binary discovery")
    class BinaryDiscovery {

        @Test
        @DisplayName("null preferred path searches PATH for javac")
        void nullPreferredPathSearchesPath() {
            // This test verifies the wiring; javac may or may not be present.
            JavaValidator validator = new JavaValidator((String) null);
            // Just verify it doesn't throw.
            ValidationResult result = validator.validate("class Foo {}");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("binary name is javac")
        void binaryNameIsJavac() {
            assertThat(JavaValidator.BINARY_NAME).isEqualTo("javac");
        }
    }
}
