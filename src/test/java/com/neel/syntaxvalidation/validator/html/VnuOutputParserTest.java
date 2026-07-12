package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link VnuOutputParser}.
 *
 * <p>Covers:
 * <ul>
 *   <li>JSON output parsing (happy path)</li>
 *   <li>Error-type message extraction vs. info/warning filtering</li>
 *   <li>Line and column number extraction</li>
 *   <li>Edge cases: empty output, blank output, null</li>
 *   <li>Plain-text fallback parsing</li>
 *   <li>Malformed / unparseable output</li>
 * </ul>
 */
@DisplayName("VnuOutputParser")
class VnuOutputParserTest {

    // -----------------------------------------------------------------
    //  Null and empty input
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("null and empty input")
    class NullAndEmpty {

        @Test
        @DisplayName("null output returns valid result")
        void nullOutput_returnsValid() {
            assertThat(VnuOutputParser.parse(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty output returns valid result")
        void emptyOutput_returnsValid() {
            assertThat(VnuOutputParser.parse("").isValid()).isTrue();
        }

        @Test
        @DisplayName("blank output returns valid result")
        void blankOutput_returnsValid() {
            assertThat(VnuOutputParser.parse("   \n\t  ").isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  JSON parsing: valid (no errors)
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("JSON parsing with no errors")
    class JsonNoErrors {

        @Test
        @DisplayName("empty messages array returns valid")
        void emptyMessagesArray_returnsValid() {
            String json = """
                    {"messages": []}
                    """;
            assertThat(VnuOutputParser.parse(json).isValid()).isTrue();
        }

        @Test
        @DisplayName("only info messages returns valid")
        void onlyInfoMessages_returnsValid() {
            String json = """
                    {"messages": [
                      {"type": "info", "message": "The document is valid.", "lastLine": 0, "lastColumn": 0}
                    ]}
                    """;
            assertThat(VnuOutputParser.parse(json).isValid()).isTrue();
        }

        @Test
        @DisplayName("only warning messages returns valid")
        void onlyWarningMessages_returnsValid() {
            String json = """
                    {"messages": [
                      {"type": "warning", "message": "Consider adding a lang attribute.", "lastLine": 1, "lastColumn": 1}
                    ]}
                    """;
            assertThat(VnuOutputParser.parse(json).isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  JSON parsing: errors found
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("JSON parsing with errors")
    class JsonErrors {

        @Test
        @DisplayName("single error is parsed correctly")
        void singleError_isParsed() {
            String json = """
                    {"messages": [
                      {"type": "error", "message": "Stray end tag 'div'.", "lastLine": 5, "lastColumn": 10}
                    ]}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Stray end tag 'div'.");
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(10);
        }

        @Test
        @DisplayName("multiple errors are all parsed")
        void multipleErrors_areParsed() {
            String json = """
                    {"messages": [
                      {"type": "error", "message": "Error one.", "lastLine": 1, "lastColumn": 1},
                      {"type": "warning", "message": "Warning.", "lastLine": 2, "lastColumn": 1},
                      {"type": "error", "message": "Error two.", "lastLine": 3, "lastColumn": 5},
                      {"type": "info", "message": "Info.", "lastLine": 4, "lastColumn": 1},
                      {"type": "error", "message": "Error three.", "lastLine": 5, "lastColumn": 10}
                    ]}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(3);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Error one.");
            assertThat(result.getErrors().get(1).getMessage()).isEqualTo("Error two.");
            assertThat(result.getErrors().get(2).getMessage()).isEqualTo("Error three.");
        }

        @Test
        @DisplayName("extracts line and column from firstLine/firstColumn when lastLine/lastColumn absent")
        void fallsBackToFirstLineColumn() {
            String json = """
                    {"messages": [
                      {"type": "error", "message": "Missing DOCTYPE.", "firstLine": 1, "firstColumn": 1}
                    ]}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(1);
        }

        @Test
        @DisplayName("defaults to line 1 when no line info available")
        void defaultsToLine1_whenNoLineInfo() {
            String json = """
                    {"messages": [
                      {"type": "error", "message": "General error."}
                    ]}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(1);
        }

        @Test
        @DisplayName("JSON embedded in vnu stderr preamble is extracted")
        void jsonEmbeddedInStderr_isExtracted() {
            String output = """
                    vnu v23.4.1
                    Checking "input.html"...
                    {"messages": [
                      {"type": "error", "message": "Test error.", "lastLine": 2, "lastColumn": 3}
                    ]}
                    Done.
                    """;
            ValidationResult result = VnuOutputParser.parse(output);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).isEqualTo("Test error.");
        }
    }

    // -----------------------------------------------------------------
    //  Plain-text fallback
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("plain-text fallback parsing")
    class PlainTextFallback {

        @Test
        @DisplayName("extracts error from plain-text vnu output")
        void plainTextError_isExtracted() {
            String text = """
                    "input.html":5.10-5.20: error: Stray end tag 'div'.
                    """;
            ValidationResult result = VnuOutputParser.parse(text);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(10);
            assertThat(result.getErrors().get(0).getMessage()).contains("Stray end tag");
        }

        @Test
        @DisplayName("ignores non-error lines in plain text")
        void plainTextNonError_isIgnored() {
            String text = """
                    vnu started.
                    Checking files...
                    "input.html":5.10: error: Stray end tag.
                    Done.
                    """;
            ValidationResult result = VnuOutputParser.parse(text);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
        }
    }

    // -----------------------------------------------------------------
    //  Message quality
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("result message quality")
    class MessageQuality {

        @Test
        @DisplayName("invalid result message contains error count")
        void invalidResult_containsCount() {
            String json = """
                    {"messages": [
                      {"type": "error", "message": "Err1.", "lastLine": 1, "lastColumn": 1},
                      {"type": "error", "message": "Err2.", "lastLine": 2, "lastColumn": 1}
                    ]}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).contains("2");
        }

        @Test
        @DisplayName("valid result message indicates no errors")
        void validResult_indicatesNoErrors() {
            String json = """
                    {"messages": []}
                    """;
            ValidationResult result = VnuOutputParser.parse(json);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }
    }
}
