package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link JavacOutputParser}.
 */
@DisplayName("JavacOutputParser")
class JavacOutputParserTest {

    private JavacOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new JavacOutputParser();
    }

    @Nested
    @DisplayName("empty / null output")
    class EmptyOutput {

        @Test
        void nullInputIsValid() {
            assertThat(parser.parse(null).isValid()).isTrue();
        }

        @Test
        void blankInputIsValid() {
            assertThat(parser.parse("   ").isValid()).isTrue();
        }

        @Test
        void emptyInputIsValid() {
            assertThat(parser.parse("").isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("error parsing")
    class ErrorParsing {

        @Test
        void singleErrorWithoutColumn() {
            String output = "Test.java:3: error: ';' expected\n    int x\n         ^\n1 error\n";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            ValidationError err = result.getErrors().get(0);
            assertThat(err.getLine()).isEqualTo(3);
            assertThat(err.getMessage()).contains(";");
        }

        @Test
        void singleErrorWithColumn() {
            String output = "Test.java:3:12: error: ';' expected\n";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            ValidationError err = result.getErrors().get(0);
            assertThat(err.getLine()).isEqualTo(3);
            assertThat(err.getColumn()).isEqualTo(12);
        }

        @Test
        void multipleErrors() {
            String output = """
                    Test.java:1: error: illegal start of type
                    Test.java:3: error: ';' expected
                    2 errors
                    """;
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }

        @Test
        void errorWithLongMessage() {
            String output = "Test.java:5: error: cannot find symbol\n    variable doesNotExist\n                    ^\n1 error\n";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).contains("cannot find symbol");
        }

        @Test
        void errorWithWindowsPath() {
            String output = "C:\\code\\Test.java:10: error: ';' expected\n1 error\n";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("warnings")
    class Warnings {

        @Test
        void warningsAreNotErrors() {
            String output = "Test.java:3: warning: [unchecked] unchecked cast\n1 warning\n";
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void warningsAndErrorsMixed() {
            String output = """
                    Test.java:3: warning: [removal] deprecated
                    Test.java:5: error: ';' expected
                    1 error
                    1 warning
                    """;
            ValidationResult result = parser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1); // only the error
        }
    }

    @Nested
    @DisplayName("summary lines and source echoes")
    class SummaryAndEchoes {

        @Test
        void summaryLineIgnored() {
            String output = "0 errors\n";
            assertThat(parser.parse(output).isValid()).isTrue();
        }

        @Test
        void sourceEchoIgnored() {
            String output = "    int x = 1;\n";
            assertThat(parser.parse(output).isValid()).isTrue();
        }

        @Test
        void caretLineIgnored() {
            String output = "^\n";
            assertThat(parser.parse(output).isValid()).isTrue();
        }
    }

    @Test
    @DisplayName("unexpected output preserved")
    void unexpectedOutputPreserved() {
        String output = "javac: some internal error occurred";
        ValidationResult result = parser.parse(output);
        // Unexpected lines may be preserved as generic diagnostics
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }
}
