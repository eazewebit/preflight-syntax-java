package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for {@link PythonOutputParser}.
 *
 * <p>Covers parsing of Python stderr/stdout output including
 * SyntaxError, IndentationError, TabError, generic errors,
 * multi-error outputs, and edge cases.
 */
@DisplayName("PythonOutputParser")
class PythonOutputParserTest {

    // ==================================================================
    //  Success cases
    // ==================================================================

    @Nested
    @DisplayName("success cases")
    class SuccessCases {

        @Test @DisplayName("exit code 0 with no output is valid")
        void exitCode0NoOutput() { assertThat(PythonOutputParser.parse("", "", 0).isValid()).isTrue(); }

        @Test @DisplayName("exit code 0 with null stderr is valid")
        void exitCode0NullStderr() { assertThat(PythonOutputParser.parse(null, "", 0).isValid()).isTrue(); }

        @Test @DisplayName("exit code 0 with blank stderr is valid")
        void exitCode0BlankStderr() { assertThat(PythonOutputParser.parse("   \n  ", "", 0).isValid()).isTrue(); }
    }

    // ==================================================================
    //  SyntaxError parsing
    // ==================================================================

    @Nested
    @DisplayName("SyntaxError parsing")
    class SyntaxErrorParsing {

        @Test @DisplayName("simple SyntaxError")
        void simpleSyntaxError() {
            String stderr = "  File \"<string>\", line 1\n    x = (\n        ^\nSyntaxError: unexpected EOF while parsing\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("SyntaxError").contains("unexpected EOF");
        }

        @Test @DisplayName("SyntaxError with column from caret")
        void syntaxErrorWithColumn() {
            String stderr = "  File \"<string>\", line 3\n    x = 1 +\n          ^\nSyntaxError: invalid syntax\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(3);
            assertThat(r.getErrors().get(0).getColumn()).isEqualTo(11);
        }

        @Test @DisplayName("SyntaxError without caret")
        void syntaxErrorWithoutCaret() {
            String stderr = "  File \"<string>\", line 1\nSyntaxError: invalid syntax\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(1);
        }
    }

    // ==================================================================
    //  IndentationError parsing
    // ==================================================================

    @Nested
    @DisplayName("IndentationError parsing")
    class IndentationErrorParsing {

        @Test @DisplayName("IndentationError")
        void indentationError() {
            String stderr = "  File \"<string>\", line 2\n    pass\n    ^\nIndentationError: expected an indented block\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(2);
            assertThat(r.getErrors().get(0).getMessage()).contains("IndentationError");
        }

        @Test @DisplayName("unexpected indent")
        void unexpectedIndent() {
            String stderr = "  File \"<string>\", line 3\n        x = 1\n        ^\nIndentationError: unexpected indent\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("unexpected indent");
        }
    }

    // ==================================================================
    //  TabError parsing
    // ==================================================================

    @Nested
    @DisplayName("TabError parsing")
    class TabErrorParsing {

        @Test @DisplayName("TabError")
        void tabError() {
            String stderr = "  File \"<string>\", line 4\n    pass\n    ^\nTabError: inconsistent use of tabs and spaces in indentation\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(4);
            assertThat(r.getErrors().get(0).getMessage()).contains("TabError");
        }
    }

    // ==================================================================
    //  Generic error handling
    // ==================================================================

    @Nested
    @DisplayName("generic error handling")
    class GenericErrorHandling {

        @Test @DisplayName("non-zero exit code with no parseable output")
        void nonZeroExitNoOutput() {
            ValidationResult r = PythonOutputParser.parse("", "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("exit code");
        }

        @Test @DisplayName("non-zero exit code with generic stderr")
        void nonZeroExitGenericStderr() {
            ValidationResult r = PythonOutputParser.parse("Some unexpected error output\n", "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("unexpected error");
        }

        @Test @DisplayName("Error: pattern in output")
        void errorPattern() {
            ValidationResult r = PythonOutputParser.parse("MemoryError: out of memory\n", "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("MemoryError");
        }

        @Test @DisplayName("Traceback without specific error type")
        void tracebackWithoutErrorType() {
            String stderr = "Traceback (most recent call last):\n  File \"<string>\", line 1\nSome unknown error\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    // ==================================================================
    //  Output format variations
    // ==================================================================

    @Nested
    @DisplayName("output format variations")
    class OutputFormatVariations {

        @Test @DisplayName("output with extra whitespace")
        void extraWhitespace() {
            String stderr = "  File \"<string>\", line 1\n    x = 1\n    ^\nSyntaxError: invalid syntax\n\n\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
        }

        @Test @DisplayName("output with Windows line endings")
        void windowsLineEndings() {
            String stderr = "  File \"<string>\", line 1\r\n    x = 1\r\n    ^\r\nSyntaxError: invalid syntax\r\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
        }

        @Test @DisplayName("output with file path in quotes")
        void filePathInQuotes() {
            String stderr = "  File \"/path/to/file.py\", line 5\n    x = 1\n    ^\nSyntaxError: invalid syntax\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(5);
        }

        @Test @DisplayName("output with 'in <module>' suffix")
        void inModuleSuffix() {
            String stderr = "  File \"<string>\", line 1, in <module>\n    x = (\n        ^\nSyntaxError: unexpected EOF while parsing\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(1);
        }
    }

    // ==================================================================
    //  Edge cases
    // ==================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test @DisplayName("empty stderr with non-zero exit code")
        void emptyStderrNonZeroExit() {
            ValidationResult r = PythonOutputParser.parse("", "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("exit code 1");
        }

        @Test @DisplayName("null stderr with non-zero exit code")
        void nullStderrNonZeroExit() {
            ValidationResult r = PythonOutputParser.parse(null, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
        }

        @Test @DisplayName("stdout content is ignored for syntax errors")
        void stdoutIgnored() {
            String stderr = "  File \"<string>\", line 1\nSyntaxError: invalid syntax\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "some normal output\n", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
        }

        @Test @DisplayName("very large line numbers")
        void veryLargeLineNumbers() {
            String stderr = "  File \"<string>\", line 999999\n    x = 1\n    ^\nSyntaxError: invalid syntax\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getLine()).isEqualTo(999999);
        }

        @Test @DisplayName("error description with special characters")
        void errorDescriptionSpecialChars() {
            String stderr = "  File \"<string>\", line 1\nSyntaxError: invalid syntax. Perhaps you forgot a comma?\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().get(0).getMessage()).contains("Perhaps you forgot a comma");
        }

        @Test @DisplayName("EncodingError")
        void encodingError() {
            String stderr = "  File \"<string>\", line 1\n    x = 'hello'\n         ^\nEncodingError: unknown encoding\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("EncodingError");
        }

        @Test @DisplayName("TokenError")
        void tokenError() {
            String stderr = "  File \"<string>\", line 1\nTokenError: EOF in multi-line string\n";
            ValidationResult r = PythonOutputParser.parse(stderr, "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors()).hasSize(1);
            assertThat(r.getErrors().get(0).getMessage()).contains("TokenError");
        }
    }

    // ==================================================================
    //  Result message quality
    // ==================================================================

    @Nested
    @DisplayName("result message quality")
    class ResultMessageQuality {

        @Test @DisplayName("valid result has descriptive message")
        void validMessage() {
            ValidationResult r = PythonOutputParser.parse("", "", 0);
            assertThat(r.isValid()).isTrue();
            assertThat(r.getMessage()).containsIgnoringCase("passed");
        }

        @Test @DisplayName("invalid result has descriptive message")
        void invalidMessage() {
            ValidationResult r = PythonOutputParser.parse("SyntaxError: invalid syntax\n", "", 1);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getMessage()).containsIgnoringCase("error");
        }
    }
}
