package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.model.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NodeCheckOutputParserTest {

    private final NodeCheckOutputParser parser = new NodeCheckOutputParser();

    @Test
    void parse_extractsLineAndMessage() {
        String output = """
                C:\\tmp\\syntax-check-1.js:3
                foo bar
                    ^^^

                SyntaxError: Unexpected identifier
                """;

        List<ValidationError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);
        ValidationError error = errors.get(0);
        assertThat(error.getLine()).isEqualTo(3);
        assertThat(error.getMessage()).contains("Unexpected identifier");
        assertThat(error.getToolOutput()).contains("SyntaxError");
    }

    @Test
    void parse_extractsLineAndColumnWhenPresent() {
        String output = """
                /tmp/syntax-check-2.js:5:10
                let x = 1 2;
                         ^
                SyntaxError: Unexpected number
                """;

        List<ValidationError> errors = parser.parse(output);

        assertThat(errors.get(0).getLine()).isEqualTo(5);
        assertThat(errors.get(0).getColumn()).isEqualTo(10);
    }

    @Test
    void parse_handlesUnixPathWithColonsInDriveStyle() {
        String output = """
                /home/u/syntax-check-3.js:7
                (;

                SyntaxError: Unexpected token ';'
                """;

        List<ValidationError> errors = parser.parse(output);

        assertThat(errors.get(0).getLine()).isEqualTo(7);
    }

    @Test
    void parse_returnsGenericErrorWhenFormatUnrecognised() {
        String output = "something completely unrelated";

        List<ValidationError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getLine()).isEqualTo(-1);
        assertThat(errors.get(0).getMessage()).contains("Unrecognised");
    }

    @Test
    void parse_returnsEmptyForNullOrBlankOutput() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
    }

    @Test
    void parse_handlesMultilineStackTrace() {
        String output = """
                /tmp/syntax-check-4.js:2
                const {
                     ^
                SyntaxError: Unexpected end of input
                    at Object.compileFunction (node:internal/vm:360:18)
                """;

        List<ValidationError> errors = parser.parse(output);

        assertThat(errors.get(0).getLine()).isEqualTo(2);
        assertThat(errors.get(0).getMessage()).isEqualTo("Unexpected end of input");
    }
}
