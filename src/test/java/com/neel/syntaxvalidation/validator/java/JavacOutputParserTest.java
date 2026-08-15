package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JavacOutputParser")
class JavacOutputParserTest {

    private final JavacOutputParser parser = new JavacOutputParser();

    // ------------------------------------------------------------------ null / blank

    @Test
    @DisplayName("returns VALID for null input")
    void parse_nullInput_returnsValid() {
        assertThat(parser.parse(null).isValid()).isTrue();
    }

    @Test
    @DisplayName("returns VALID for blank input")
    void parse_blankInput_returnsValid() {
        assertThat(parser.parse("   \n   ").isValid()).isTrue();
    }

    // ------------------------------------------------------------------ real javac output

    @Test
    @DisplayName("parses 'reached end of file while parsing' and skips source-echo + caret")
    void parse_eofError_skipsEchoLines() {
        // Simulated javac stderr for a file with an unclosed brace
        String javacOutput = String.join("\n",
                "syntax-check-12345.java:1: error: reached end of file while parsing",
                "public class InvalidJava {",
                "                        ^",
                "1 error");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        ValidationError err = result.getErrors().get(0);
        assertThat(err.getLine()).isEqualTo(1);
        assertThat(err.getMessage()).isEqualTo("reached end of file while parsing");
    }

    @Test
    @DisplayName("parses column-aware error format")
    void parse_columnAwareError() {
        String javacOutput = String.join("\n",
                "syntax-check-abc.java:5:12: error: ';' expected",
                "        int x",
                "               ^",
                "1 error");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        ValidationError err = result.getErrors().get(0);
        assertThat(err.getLine()).isEqualTo(5);
        assertThat(err.getColumn()).isEqualTo(12);
        assertThat(err.getMessage()).isEqualTo("';' expected");
    }

    @Test
    @DisplayName("multiple errors are all collected")
    void parse_multipleErrors() {
        String javacOutput = String.join("\n",
                "syntax-check-abc.java:1: error: ';' expected",
                "int x",
                "    ^",
                "syntax-check-abc.java:3: error: reached end of file while parsing",
                "class Foo {",
                "           ^",
                "2 errors");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    @DisplayName("warnings are ignored (VALID)")
    void parse_warningsOnly_returnsValid() {
        String javacOutput = String.join("\n",
                "syntax-check-abc.java:2: warning: unchecked cast",
                "    String s = (String) obj;",
                "                  ^",
                "1 warning");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("mixed warnings + errors: only errors are reported")
    void parse_mixedWarningsAndErrors() {
        String javacOutput = String.join("\n",
                "syntax-check-abc.java:2: warning: unchecked cast",
                "    String s = (String) obj;",
                "                  ^",
                "syntax-check-abc.java:5: error: ';' expected",
                "    int x",
                "         ^",
                "1 warning",
                "1 error");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
    }

    @Test
    @DisplayName("source-echo lines without leading spaces are NOT treated as errors")
    void parse_sourceEchoWithoutLeadingSpaces() {
        // This is the exact scenario from the bug report:
        // javac echoes "public class InvalidJava {" without indentation
        String javacOutput = String.join("\n",
                "syntax-check-99.java:1: error: reached end of file while parsing",
                "public class InvalidJava {",
                "                        ^",
                "1 error");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(1);
        // The source echo "public class InvalidJava {" must NOT appear as a separate error
        assertThat(result.getErrors().get(0).getMessage())
                .isEqualTo("reached end of file while parsing");
    }

    @Test
    @DisplayName("empty stderr separated errors with blank lines")
    void parse_blankLineBetweenDiagnostics() {
        String javacOutput = String.join("\n",
                "syntax-check-abc.java:1: error: ';' expected",
                "int x",
                "    ^",
                "",
                "syntax-check-abc.java:3: error: reached end of file while parsing",
                "class Foo {",
                "           ^",
                "2 errors");

        ValidationResult result = parser.parse(javacOutput);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(2);
    }
}