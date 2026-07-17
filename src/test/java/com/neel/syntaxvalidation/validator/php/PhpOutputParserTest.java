package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PhpOutputParser}.
 */
@DisplayName("PhpOutputParser")
class PhpOutputParserTest {

    private PhpOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new PhpOutputParser();
    }

    @Nested
    @DisplayName("null, empty, and blank input")
    class NullEmptyBlank {

        @Test
        @DisplayName("null input returns valid")
        void nullInput() {
            assertThat(parser.parse(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty input returns valid")
        void emptyInput() {
            assertThat(parser.parse("").isValid()).isTrue();
        }

        @Test
        @DisplayName("blank input returns valid")
        void blankInput() {
            assertThat(parser.parse("   \n\t  ").isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("success messages")
    class SuccessMessages {

        @Test
        @DisplayName("standard success message")
        void standardSuccess() {
            assertThat(parser.parse("No syntax errors detected in /tmp/test.php").isValid()).isTrue();
        }

        @Test
        @DisplayName("success message with full path")
        void successWithFullPath() {
            assertThat(parser.parse("No syntax errors detected in /home/user/projects/app/src/Controller.php").isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("parse errors")
    class ParseErrors {

        @Test
        @DisplayName("unexpected token error")
        void unexpectedToken() {
            String output = "Parse error: syntax error, unexpected '}' in /tmp/test.php on line 10";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(10);
            assertThat(result.getErrors().get(0).getMessage()).contains("Parse error");
            assertThat(result.getErrors().get(0).getMessage()).contains("unexpected '}'");
        }

        @Test
        @DisplayName("unexpected variable")
        void unexpectedVariable() {
            String output = "Parse error: syntax error, unexpected '$x' (T_VARIABLE), expecting ';' in /tmp/test.php on line 5";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
            assertThat(result.getErrors().get(0).getMessage()).contains("T_VARIABLE");
        }

        @Test
        @DisplayName("unexpected string token (PHP 8.x format)")
        void unexpectedStringTokenPhp8() {
            String output = "Parse error: syntax error, unexpected token \"function\", expecting \";\" or \"{\" in /tmp/test.php on line 15";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(15);
        }

        @Test
        @DisplayName("missing semicolon")
        void missingSemicolon() {
            String output = "Parse error: syntax error, unexpected end of file in /tmp/test.php on line 42";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("fatal errors")
    class FatalErrors {

        @Test
        @DisplayName("redeclared function")
        void redeclaredFunction() {
            String output = "Fatal error: Cannot redeclare foo() (previously declared in /tmp/a.php:5) in /tmp/b.php on line 10";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(10);
            assertThat(result.getErrors().get(0).getMessage()).contains("Fatal error");
        }

        @Test
        @DisplayName("uncaught error (PHP 8.x)")
        void uncaughtError() {
            String output = "Fatal error: Uncaught Error: Class 'NonExistent' not found in /tmp/test.php:3\nStack trace:\n#0 {main}";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("warnings and notices")
    class WarningsAndNotices {

        @Test
        @DisplayName("warning message")
        void warningMessage() {
            String output = "Warning: Some warning in /tmp/test.php on line 7";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(7);
            assertThat(result.getErrors().get(0).getMessage()).contains("Warning");
        }

        @Test
        @DisplayName("deprecated notice")
        void deprecatedNotice() {
            String output = "Deprecated: Function ereg() is deprecated in /tmp/test.php on line 3";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(3);
            assertThat(result.getErrors().get(0).getMessage()).contains("Deprecated");
        }
    }

    @Nested
    @DisplayName("multiple errors")
    class MultipleErrors {

        @Test
        @DisplayName("multiple parse errors in single output")
        void multipleParseErrors() {
            String output = """
                    Parse error: syntax error, unexpected '$x' (T_VARIABLE) in /tmp/test.php on line 3
                    Parse error: syntax error, unexpected '}' in /tmp/test.php on line 10
                    """;
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(3);
            assertThat(result.getErrors().get(1).getLine()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("unrecognized format falls back to whole output")
        void unrecognizedFormat() {
            String output = "Some unexpected output format from PHP";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Some unexpected output format from PHP");
        }

        @Test
        @DisplayName("PHP 8.2+ error format with double-quoted tokens")
        void php82Format() {
            String output = "Parse error: syntax error, unexpected token \"readonly\", expecting \";\" in /tmp/test.php on line 6";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(6);
        }
    }
}
