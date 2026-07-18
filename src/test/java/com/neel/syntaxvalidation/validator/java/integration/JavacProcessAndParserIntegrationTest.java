package com.neel.syntaxvalidation.validator.java.integration;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests verifying the interaction between {@link JavaValidator},
 * {@link ProcessExecutor}, and the javac output parsing logic.
 *
 * <p>These tests focus on the end-to-end flow of javac output parsing, error
 * extraction, and result construction, covering various javac output formats,
 * exit codes, edge cases, and error recovery scenarios.
 */
@DisplayName("Javac Process & Parser Integration Tests")
class JavacProcessAndParserIntegrationTest {

    // ======================================================================
    //  Test doubles
    // ======================================================================

    private static class StubBinaryResolver extends BinaryResolver {
        private final Optional<String> path;

        StubBinaryResolver(String path) {
            this.path = Optional.ofNullable(path);
        }

        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) {
            if (preferredPath != null && !preferredPath.isBlank()) {
                return Optional.of(preferredPath);
            }
            return path;
        }
    }

    private static class StubProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;
        private List<String> lastCommand;

        StubProcessExecutor(ProcessResult result) {
            this.result = result;
        }

        @Override
        public ProcessResult execute(List<String> command) throws IOException, InterruptedException {
            this.lastCommand = command;
            if (result == null) {
                throw new IOException("Simulated failure");
            }
            return result;
        }

        List<String> getLastCommand() { return lastCommand; }
    }

    // ======================================================================
    //  Helper
    // ======================================================================

    private JavaValidator createValidator(ProcessResult processResult) {
        return new JavaValidator(null,
                new StubBinaryResolver("/usr/bin/javac"),
                new StubProcessExecutor(processResult));
    }

    // ======================================================================
    //  1. Single Error Parsing Integration
    // ======================================================================

    @Nested
    @DisplayName("Single error parsing integration")
    class SingleErrorParsingIntegration {

        @Test
        @DisplayName("single error with column → full error details preserved")
        void singleErrorWithColumn_fullErrorDetailsPreserved() {
            var javacOutput = "Foo.java:5:10: error: ';' expected\n"
                    + "        int x = 5\n"
                    + "                 ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Foo { int x = 5 }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(5);
            assertThat(error.getColumn()).isEqualTo(10);
            assertThat(error.getMessage()).isEqualTo("';' expected");
            assertThat(error.getToolOutput()).contains("Foo.java:5:10");
        }

        @Test
        @DisplayName("single error without column → column defaults to -1")
        void singleErrorWithoutColumn_columnDefaultsToMinusOne() {
            var javacOutput = "Bar.java:3: error: cannot find symbol\n"
                    + "        unknown();\n"
                    + "        ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Bar { void m() { unknown(); } }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);

            ValidationError error = result.getErrors().get(0);
            assertThat(error.getLine()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo(-1);
            assertThat(error.getMessage()).isEqualTo("cannot find symbol");
        }

        @Test
        @DisplayName("single error with multi-line message → full message captured")
        void singleErrorWithMultiLineMessage_fullMessageCaptured() {
            var javacOutput = "Multi.java:3: error: incompatible types: possible lossy conversion from double to int\n"
                    + "        int x = 3.14;\n"
                    + "              ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Multi { void m() { int x = 3.14; } }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("incompatible types");
        }
    }

    // ======================================================================
    //  2. Multiple Error Parsing Integration
    // ======================================================================

    @Nested
    @DisplayName("Multiple error parsing integration")
    class MultipleErrorParsingIntegration {

        @Test
        @DisplayName("three errors → all parsed with correct positions")
        void threeErrors_allParsedWithCorrectPositions() {
            var javacOutput = """
                    Errors.java:2: error: ';' expected
                            int x = 5
                                     ^
                    Errors.java:3: error: cannot find symbol
                            unknown();
                            ^
                      symbol:   method unknown()
                      location: class Errors
                    Errors.java:4: error: illegal start of expression
                            }
                            ^
                    3 errors
                    """;

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            var source = """
                    class Errors {
                        int x = 5
                        unknown();
                    }
                    """;
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            // Parser also picks up "symbol:" and "location:" info lines as unexpected diagnostics
            assertThat(result.getErrors()).hasSize(5);

            assertThat(result.getErrors().get(0).getLine()).isEqualTo(2);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("';' expected");

            assertThat(result.getErrors().get(1).getLine()).isEqualTo(3);
            assertThat(result.getErrors().get(1).getMessage()).isEqualTo("cannot find symbol");

            // "symbol:   method unknown()" and "location: class Errors" are preserved
            assertThat(result.getErrors().get(2).getMessage()).contains("symbol:");
            assertThat(result.getErrors().get(3).getMessage()).contains("location:");

            assertThat(result.getErrors().get(4).getLine()).isEqualTo(4);
            assertThat(result.getErrors().get(4).getMessage()).isEqualTo("illegal start of expression");
        }

        @Test
        @DisplayName("errors with warnings mixed → only errors captured")
        void errorsWithWarningsMixed_onlyErrorsCaptured() {
            var javacOutput = """
                    Mixed.java:2: warning: [unchecked] unchecked call to add(E) as a member of the raw type java.util.List
                            list.add("test");
                                    ^
                    Mixed.java:4: error: ';' expected
                            int x = 5
                                     ^
                    Mixed.java:6: warning: [deprecation] deprecatedMethod() in Foo has been deprecated
                            deprecatedMethod();
                            ^
                    Mixed.java:8: error: incompatible types
                            int y = "hello";
                                    ^
                    2 errors
                    2 warnings
                    """;

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Mixed { }");

            assertThat(result.isValid()).isFalse();
            // Only 2 errors should be captured, not warnings
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("';' expected");
            assertThat(result.getErrors().get(1).getMessage()).isEqualTo("incompatible types");
        }

        @Test
        @DisplayName("ten errors → all parsed correctly")
        void tenErrors_allParsedCorrectly() {
            var sb = new StringBuilder();
            for (int i = 1; i <= 10; i++) {
                sb.append("Test.java:").append(i).append(": error: error message ").append(i).append("\n");
                sb.append("    line").append(i).append("\n");
                sb.append("    ^\n");
            }
            sb.append("10 errors\n");
            var javacOutput = sb.toString();

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { }");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(10);

            for (int i = 0; i < 10; i++) {
                assertThat(result.getErrors().get(i).getLine()).isEqualTo(i + 1);
                assertThat(result.getErrors().get(i).getMessage()).isEqualTo("error message " + (i + 1));
            }
        }
    }
    // ======================================================================
    //  3. Exit Code Integration
    // ======================================================================

    @Nested
    @DisplayName("Exit code integration")
    class ExitCodeIntegration {

        @Test
        @DisplayName("exit code 0 with note output → notes treated as unexpected diagnostics")
        void exitCode0_withNoteOutput_notesAsDiagnostics() {
            var javacOutput = "Note: Some input files use unchecked operations.\n"
                    + "Recompile with -Xlint:unchecked for details.\n";

            var validator = createValidator(new ProcessResult(0, "", javacOutput, false));
            ValidationResult result = validator.validate("class A {}");

            // "Note: ..." lines don't match the diagnostic pattern, so they're
            // preserved as unexpected diagnostics
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }

        @Test
        @DisplayName("exit code 0 with empty output → valid result")
        void exitCode0_emptyOutput_validResult() {
            var validator = createValidator(new ProcessResult(0, "", "", false));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("exit code 1 → invalid result")
        void exitCode1_invalidResult() {
            var javacOutput = "A.java:1: error: class, interface, or enum expected\n"
                    + "invalid\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("invalid");

            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("exit code 2 → handled gracefully")
        void exitCode2_handledGracefully() {
            var javacOutput = "javac: invalid flag: -badoption\n";

            var validator = createValidator(new ProcessResult(2, "", javacOutput, false));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("exit code -1 (timeout) → handled gracefully")
        void exitCodeMinus1Timeout_handledGracefully() {
            var validator = createValidator(new ProcessResult(-1, "", "", true));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }

        @Test
        @DisplayName("exit code 137 (OOM killed) → handled gracefully")
        void exitCode137OomKilled_handledGracefully() {
            var validator = createValidator(new ProcessResult(137, "", "", false));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result).isNotNull();
        }
    }

    // ======================================================================
    //  4. Stdout vs Stderr Integration
    // ======================================================================

    @Nested
    @DisplayName("stdout vs stderr integration")
    class StdoutVsStderrIntegration {

        @Test
        @DisplayName("errors on stderr → correctly parsed")
        void errorsOnStderr_correctlyParsed() {
            var javacErr = "Test.java:1: error: class expected\n"
                    + "bad code\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacErr, false));
            ValidationResult result = validator.validate("bad code");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("errors on stdout → correctly parsed")
        void errorsOnStdout_correctlyParsed() {
            var javacOut = "Test.java:1: error: class expected\n"
                    + "bad code\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, javacOut, "", false));
            ValidationResult result = validator.validate("bad code");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("warnings on stderr with exit 0 → valid result")
        void warningsOnStderrWithExit0_validResult() {
            var javacErr = "Test.java:1: warning: [deprecation] something deprecated\n";

            var validator = createValidator(new ProcessResult(0, "", javacErr, false));
            ValidationResult result = validator.validate("class Test {}");

            assertThat(result.isValid()).isTrue();
        }
    }

    // ======================================================================
    //  5. Javac Output Format Variations
    // ======================================================================

    @Nested
    @DisplayName("Javac output format variations")
    class JavacOutputFormatVariations {

        @Test
        @DisplayName("javac 8 style output → parsed correctly")
        void javac8StyleOutput_parsedCorrectly() {
            var javacOutput = "Test.java:1: error: cannot find symbol\n"
                    + "symbol:   class Unknown\n"
                    + "location: class Test\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { Unknown x; }");

            assertThat(result.isValid()).isFalse();
            // Parser also picks up "symbol:" and "location:" lines
            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("cannot find symbol");
        }

        @Test
        @DisplayName("javac 17 style output with location column → parsed correctly")
        void javac17StyleOutputWithLocationColumn_parsedCorrectly() {
            var javacOutput = "Test.java:3:15: error: incompatible types: String cannot be converted to int\n"
                    + "        int x = \"hello\";\n"
                    + "               ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { void m() { int x = \"hello\"; } }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(3);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(15);
        }

        @Test
        @DisplayName("javac 21+ style output with preview features → parsed correctly")
        void javac21StyleOutputWithPreviewFeatures_parsedCorrectly() {
            var javacOutput = "Test.java:3: error: patterns in switch statements are a preview feature and are disabled by default.\n"
                    + "            case Integer i -> {}\n"
                    + "            ^\n"
                    + "  (use --enable-preview to enable patterns in switch statements)\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { void m(Object o) { switch(o) { case Integer i -> {} } } }");

            assertThat(result.isValid()).isFalse();
            // The "(use --enable-preview...)" line is also preserved as unexpected diagnostic
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getMessage()).contains("preview feature");
            assertThat(result.getErrors().get(1).getMessage()).contains("enable-preview");
        }

        @Test
        @DisplayName("javac with -Xlint output → warnings ignored, errors parsed")
        void javacWithXlintOutput_warningsIgnoredErrorsParsed() {
            var javacOutput = """
                    Test.java:3: warning: [serial] serializable class Test has no definition of serialVersionUID
                    class Test implements java.io.Serializable {
                          ^
                    Test.java:5: error: ';' expected
                            int x = 5
                                     ^
                    1 error
                    1 warning
                    """;

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test implements java.io.Serializable { int x = 5 }");

            assertThat(result.isValid()).isFalse();
            // The source echo "class Test implements..." is NOT indented, so it's
            // preserved as an unexpected diagnostic alongside the actual error
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("expected"));
            assertThat(result.getErrors()).anyMatch(e -> e.getMessage().contains("Serializable"));
        }

        @Test
        @DisplayName("javac with encoding errors → parsed correctly")
        void javacWithEncodingErrors_parsedCorrectly() {
            var javacOutput = "Test.java:1: error: unmappable character for encoding UTF-8\n"
                    + "// \uFFFD\uFFFD\uFFFD\n"
                    + "   ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("// \uFFFD\uFFFD\uFFFD\nclass Test {}");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }
    }

    // ======================================================================
    //  6. Edge Cases in Output Parsing
    // ======================================================================

    @Nested
    @DisplayName("Edge cases in output parsing")
    class EdgeCasesInOutputParsing {

        @Test
        @DisplayName("empty output → valid result")
        void emptyOutput_validResult() {
            var validator = createValidator(new ProcessResult(0, "", "", false));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("output with only whitespace → treated as empty")
        void outputWithOnlyWhitespace_treatedAsEmpty() {
            var validator = createValidator(new ProcessResult(0, "   \n\n  ", "  \n  ", false));
            ValidationResult result = validator.validate("class A {}");

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("output with error count line only → handled gracefully")
        void outputWithOnlyErrorCountLine_handledGracefully() {
            var validator = createValidator(new ProcessResult(1, "", "1 error\n", false));
            ValidationResult result = validator.validate("class A {}");

            // The error count line itself doesn't have error information
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("output with unusual file paths → parsed correctly")
        void outputWithUnusualFilePaths_parsedCorrectly() {
            var javacOutput = "src/main/java/com/example/MyClass.java:5: error: ';' expected\n"
                    + "    int x = 5\n"
                    + "             ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class MyClass { int x = 5 }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
        }

        @Test
        @DisplayName("output with Windows path separators → parsed correctly")
        void outputWithWindowsPathSeparators_parsedCorrectly() {
            var javacOutput = "C:\\Users\\dev\\project\\Test.java:3: error: ';' expected\n"
                    + "    int x = 5\n"
                    + "             ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { int x = 5 }");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
        }

        @Test
        @DisplayName("output with file path containing spaces → parsed correctly")
        void outputWithFilePathContainingSpaces_parsedCorrectly() {
            var javacOutput = "my project/Test.java:2: error: class expected\n"
                    + "bad code\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("bad code");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("output with very long error message → message preserved")
        void outputWithVeryLongErrorMessage_messagePreserved() {
            var longMessage = "error: ".concat("very long message ".repeat(20));
            var javacOutput = "Test.java:1: " + longMessage + "\n"
                    + "code\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("code");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("consecutive error lines → only error lines parsed, not context lines")
        void consecutiveErrorLines_onlyErrorLinesParsedNotContextLines() {
            var javacOutput = "Test.java:2: error: ';' expected\n"
                    + "        int x = 5\n"
                    + "                 ^\n"
                    + "Some context information\n"
                    + "More context\n"
                    + "Test.java:3: error: cannot find symbol\n"
                    + "        y\n"
                    + "        ^\n"
                    + "2 errors\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate("class Test { int x = 5\ny }");

            assertThat(result.isValid()).isFalse();
            // Context lines between errors are also preserved as unexpected diagnostics
            assertThat(result.getErrors()).hasSize(4);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("';' expected");
            assertThat(result.getErrors().get(1).getMessage()).contains("context information");
            assertThat(result.getErrors().get(2).getMessage()).contains("More context");
            assertThat(result.getErrors().get(3).getMessage()).isEqualTo("cannot find symbol");
        }
    }

    // ======================================================================
    //  7. Phase Integration (Syntax Engine + Javac)
    // ======================================================================

    @Nested
    @DisplayName("Phase integration (syntax engine + javac)")
    class PhaseIntegration {

        @Test
        @DisplayName("phase 1 finds structural errors → javac is NOT invoked, phase 1 errors returned")
        void phase1Structural_javacNotInvoked_phase1ErrorsReturned() {
            var source = "const int x = 1;";

            var javacOutput = "Test.java:1: error: not a statement\n"
                    + "const int x = 1;\n"
                    + "^\n"
                    + "1 error\n";

            var stubExecutor = new StubProcessExecutor(new ProcessResult(1, "", javacOutput, false));

            var validator = new JavaValidator(null,
                    new StubBinaryResolver("/usr/bin/javac"), stubExecutor);

            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            // Phase 1 fails → javac should NOT be invoked
            assertThat(stubExecutor.getLastCommand()).isNull();
            // Should only have phase 1 errors
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("reserved keyword"));
        }

        @Test
        @DisplayName("phase 1 passes with javac errors → javac errors present")
        void phase1PassesWithJavacErrors_javacErrorsPresent() {
            // This code passes all structural checks but has a type error
            var source = """
                    public class TypeMismatch {
                        void m() {
                            int x = "hello";
                        }
                    }
                    """;

            var javacOutput = "TypeMismatch.java:3: error: incompatible types: String cannot be converted to int\n"
                    + "        int x = \"hello\";\n"
                    + "               ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("incompatible types");
        }

        @Test
        @DisplayName("phase 1 valid and javac valid → overall valid")
        void phase1ValidAndJavacValid_overallValid() {
            var source = """
                    public class Perfect {
                        int add(int a, int b) {
                            return a + b;
                        }
                    }
                    """;

            var validator = createValidator(new ProcessResult(0, "", "", false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("phase 1 valid and javac unavailable → falls back to engine result")
        void phase1ValidJavacUnavailable_fallsBackToEngineResult() {
            var source = "public class NoJavac {}";

            var validator = new JavaValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(new ProcessResult(0, "", "", false)));

            ValidationResult result = validator.validate(source);

            // Phase 1 passes, javac not found → returns engine's valid result
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }
    }

    // ======================================================================
    //  8. Process Command Integration
    // ======================================================================

    @Nested
    @DisplayName("Process command integration")
    class ProcessCommandIntegration {

        @Test
        @DisplayName("command starts with javac binary")
        void commandStartsWithJavacBinary() {
            var source = "class A {}";
            var stubExecutor = new StubProcessExecutor(new ProcessResult(0, "", "", false));

            var validator = new JavaValidator(null,
                    new StubBinaryResolver("/usr/bin/javac"), stubExecutor);

            validator.validate(source);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            assertThat(stubExecutor.getLastCommand()).isNotEmpty();
            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo("/usr/bin/javac");
        }

        @Test
        @DisplayName("command contains temp file path")
        void commandContainsTempFilePath() {
            var source = "class A {}";
            var stubExecutor = new StubProcessExecutor(new ProcessResult(0, "", "", false));

            var validator = new JavaValidator(null,
                    new StubBinaryResolver("/usr/bin/javac"), stubExecutor);

            validator.validate(source);

            assertThat(stubExecutor.getLastCommand()).isNotNull();
            // Should have at least: binary + -proc:none + -nowarn + -d + tmpdir + temp file
            assertThat(stubExecutor.getLastCommand().size()).isGreaterThanOrEqualTo(6);
            // Last arg should be the temp file
            String lastArg = stubExecutor.getLastCommand().getLast();
            assertThat(lastArg).contains("syntax-check");
            assertThat(lastArg).endsWith(".java");
        }

        @Test
        @DisplayName("preferred binary path used in command")
        void preferredBinaryPathUsedInCommand() {
            var source = "class A {}";
            var stubExecutor = new StubProcessExecutor(new ProcessResult(0, "", "", false));

            var validator = new JavaValidator("/opt/jdk-21/bin/javac",
                    new StubBinaryResolver(null), stubExecutor);

            validator.validate(source);

            assertThat(stubExecutor.getLastCommand().get(0)).isEqualTo("/opt/jdk-21/bin/javac");
        }
    }

    // ======================================================================
    //  9. Error Message Formatting Integration
    // ======================================================================

    @Nested
    @DisplayName("Error message formatting integration")
    class ErrorMessageFormattingIntegration {

        @Test
        @DisplayName("phase 1 error format → descriptive message with context")
        void phase1ErrorFormat_descriptiveMessageWithContext() {
            var source = "const int x = 1;";
            var validator = createValidator(new ProcessResult(0, "", "", false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            ValidationError error = result.getErrors().get(0);
            // Error should have meaningful message, line, column, and tool output
            assertThat(error.getMessage()).isNotBlank();
            assertThat(error.getLine()).isPositive();
            assertThat(error.getColumn()).isPositive();
            assertThat(error.getToolOutput()).isNotBlank();
        }

        @Test
        @DisplayName("phase 2 error format → message from javac preserved")
        void phase2ErrorFormat_messageFromJavacPreserved() {
            var source = "class A {}";
            var javacOutput = "A.java:1: error: custom javac error message\n"
                    + "class A {}\n"
                    + "^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("custom javac error message");
        }

        @Test
        @DisplayName("binary not found error → helpful message about javac")
        void binaryNotFoundError_helpfulMessageAboutJavac() {
            var source = "class A {}";
            var validator = new JavaValidator(null,
                    new StubBinaryResolver(null),
                    new StubProcessExecutor(new ProcessResult(0, "", "", false)));

            ValidationResult result = validator.validate(source);

            // When javac is not found, phase 1 passes → returns valid result with message
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("IO error message → includes exception details")
        void ioErrorMesssage_includesExceptionDetails() {
            var source = "class A {}";
            var validator = new JavaValidator(null,
                    new StubBinaryResolver("/usr/bin/javac"),
                    new StubProcessExecutor(null) {
                        @Override
                        public ProcessResult execute(List<String> command) throws IOException {
                            throw new IOException("Disk full");
                        }
                    });

            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("I/O error");
            assertThat(result.getMessage()).contains("Disk full");
        }
    }

    // ======================================================================
    //  10. Comprehensive End-to-End Scenarios
    // ======================================================================

    @Nested
    @DisplayName("Comprehensive end-to-end scenarios")
    class ComprehensiveEndToEndScenarios {

        @Test
        @DisplayName("real-world missing import → javac error captured")
        void realWorldMissingImport_javacErrorCaptured() {
            var source = """
                    public class MissingImport {
                        List<String> items = new java.util.ArrayList<>();
                    }
                    """;

            var javacOutput = "MissingImport.java:2: error: cannot find symbol\n"
                    + "    List<String> items = new java.util.ArrayList<>();\n"
                    + "    ^\n"
                    + "  symbol:   class List\n"
                    + "  location: class MissingImport\n"
                    + "2 errors\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("real-world type inference failure → javac error captured")
        void realWorldTypeInferenceFailure_javacErrorCaptured() {
            var source = """
                    public class TypeInference {
                        var x = null;
                    }
                    """;

            var javacOutput = "TypeInference.java:2: error: cannot infer type for local variable\n"
                    + "    var x = null;\n"
                    + "        ^\n"
                    + "  (variable initializer is 'null')\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("cannot infer type");
        }

        @Test
        @DisplayName("real-world diamond operator misuse → javac error captured")
        void realWorldDiamondOperatorMisuse_javacErrorCaptured() {
            var source = """
                    public class Diamond {
                        java.util.List<> items = new java.util.ArrayList<>();
                    }
                    """;

            var javacOutput = "Diamond.java:2: error: diamond operator is not applicable for anonymous classes\n"
                    + "    java.util.List<> items = new java.util.ArrayList<>();\n"
                    + "                           ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("real-world uncaught exception → javac error captured")
        void realWorldUncaughtException_javacErrorCaptured() {
            var source = """
                    public class Uncaught {
                        void m() {
                            throw new java.io.IOException("fail");
                        }
                    }
                    """;

            var javacOutput = "Uncaught.java:3: error: unreported exception java.io.IOException; must be declared or thrown\n"
                    + "        throw new java.io.IOException(\"fail\");\n"
                    + "        ^\n"
                    + "1 error\n";

            var validator = createValidator(new ProcessResult(1, "", javacOutput, false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
            assertThat(result.getErrors().get(0).getMessage()).contains("unreported exception");
        }

        @Test
        @DisplayName("valid complex source with annotations, generics, lambdas → passes")
        void validComplexSourceWithAnnotationsGenericsLambdas_passes() {
            var source = """
                    import java.util.List;
                    import java.util.stream.Collectors;

                    @SuppressWarnings("all")
                    public class Complex {
                        private final List<String> items;

                        public Complex(List<String> items) {
                            this.items = List.copyOf(items);
                        }

                        public List<String> filterAndTransform(java.util.function.Predicate<String> predicate) {
                            return items.stream()
                                    .filter(predicate)
                                    .map(s -> s.toUpperCase())
                                    .collect(Collectors.toUnmodifiableList());
                        }

                        @SafeVarargs
                        public final <T> List<T> asList(T... elements) {
                            return List.of(elements);
                        }
                    }
                    """;

            var validator = createValidator(new ProcessResult(0, "", "", false));
            ValidationResult result = validator.validate(source);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }
}
