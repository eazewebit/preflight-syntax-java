package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StylelintOutputParser}.
 */
@DisplayName("StylelintOutputParser")
class StylelintOutputParserTest {

    // ---------------------------------------------------------------
    // parse – null and empty input
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("parse – null and empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("null input returns valid result")
        void nullInput() {
            ValidationResult result = StylelintOutputParser.parse(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("empty string returns valid result")
        void emptyString() {
            ValidationResult result = StylelintOutputParser.parse("");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("blank string returns valid result")
        void blankString() {
            ValidationResult result = StylelintOutputParser.parse("   \n  \t  ");
            assertThat(result.isValid()).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // parse – JSON output with errors
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("parse – JSON output with errors")
    class JsonWithErrors {

        @Test
        @DisplayName("parses a single error from JSON output")
        void singleError() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 5,
                                "column": 10,
                                "text": "Unexpected token",
                                "severity": "error",
                                "rule": "syntax-error"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(10);
            assertThat(result.getErrors().get(0).getMessage()).contains("Unexpected token");
        }

        @Test
        @DisplayName("parses multiple errors from JSON output")
        void multipleErrors() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 5,
                                "column": 10,
                                "text": "Unexpected token",
                                "severity": "error",
                                "rule": "syntax-error"
                            },
                            {
                                "line": 12,
                                "column": 1,
                                "text": "Missing semicolon",
                                "severity": "error",
                                "rule": "no-missing-semicolon"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }

        @Test
        @DisplayName("ignores warnings (only captures errors)")
        void ignoresWarnings() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 5,
                                "column": 10,
                                "text": "Expected a vendor prefix",
                                "severity": "warning",
                                "rule": "no-vendor-prefix"
                            },
                            {
                                "line": 10,
                                "column": 1,
                                "text": "Syntax error",
                                "severity": "error",
                                "rule": "syntax-error"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("Syntax error");
        }

        @Test
        @DisplayName("returns valid when only warnings exist")
        void onlyWarningsValid() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 5,
                                "column": 10,
                                "text": "Expected a vendor prefix",
                                "severity": "warning",
                                "rule": "no-vendor-prefix"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("handles JSON with empty warnings array")
        void emptyWarnings() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": []
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("handles escaped characters in error messages")
        void escapedCharacters() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 1,
                                "column": 1,
                                "text": "Unexpected \\"quoted\\" text",
                                "severity": "error",
                                "rule": "test"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getMessage()).contains("quoted");
        }

        @Test
        @DisplayName("handles line and column of 0 (clamped to 1)")
        void zeroLineColumn() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 0,
                                "column": 0,
                                "text": "Error at start",
                                "severity": "error",
                                "rule": "test"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(1);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------
    // parse – plain text fallback
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("parse – plain text fallback")
    class PlainTextFallback {

        @Test
        @DisplayName("parses plain text output format")
        void plainTextFormat() {
            String output = """
                    test.css
                     5:10  error  Unexpected token  syntax-error
                    """;

            ValidationResult result = StylelintOutputParser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getLine()).isEqualTo(5);
            assertThat(result.getErrors().get(0).getColumn()).isEqualTo(10);
        }

        @Test
        @DisplayName("parses multiple plain text lines")
        void multiplePlainTextLines() {
            String output = """
                    test.css
                     5:10  error  Unexpected token  syntax-error
                     12:1  error  Missing semicolon  no-missing-semi
                    """;

            ValidationResult result = StylelintOutputParser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
        }

        @Test
        @DisplayName("ignores warning lines in plain text")
        void ignoresWarningsInPlainText() {
            String output = """
                    test.css
                     5:10  warning  Some warning  some-rule
                     12:1  error    Syntax error  syntax-error
                    """;

            ValidationResult result = StylelintOutputParser.parse(output);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("Syntax error");
        }

        @Test
        @DisplayName("returns valid for unparseable text")
        void unparseableText() {
            String output = "some random text that doesn't match the pattern";

            ValidationResult result = StylelintOutputParser.parse(output);
            assertThat(result.isValid()).isTrue();
        }
    }

    // ---------------------------------------------------------------
    // parse – message format
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("parse – message format")
    class MessageFormat {

        @Test
        @DisplayName("valid result has success message")
        void validMessage() {
            ValidationResult result = StylelintOutputParser.parse("");
            assertThat(result.getMessage()).contains("syntactically valid");
        }

        @Test
        @DisplayName("invalid result has error count message")
        void invalidMessage() {
            String json = """
                    [{
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 1,
                                "column": 1,
                                "text": "Error",
                                "severity": "error",
                                "rule": "test"
                            }
                        ]
                    }]
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result.getMessage()).contains("1 error(s)");
        }
    }

    // ---------------------------------------------------------------
    // parse – edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("parse – edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles malformed JSON gracefully")
        void malformedJson() {
            String json = "[{ invalid json }]";

            ValidationResult result = StylelintOutputParser.parse(json);
            // Should not throw, either parses something or returns valid
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles JSON object instead of array")
        void jsonObject() {
            String json = """
                    {
                        "source": "test.css",
                        "warnings": [
                            {
                                "line": 1,
                                "column": 1,
                                "text": "Error",
                                "severity": "error",
                                "rule": "test"
                            }
                        ]
                    }
                    """;

            ValidationResult result = StylelintOutputParser.parse(json);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles very large output")
        void largeOutput() {
            StringBuilder sb = new StringBuilder("[{\"source\":\"test.css\",\"warnings\":[");
            for (int i = 0; i < 100; i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"line\":").append(i + 1)
                  .append(",\"column\":1,\"text\":\"Error ").append(i)
                  .append("\",\"severity\":\"error\",\"rule\":\"test\"}");
            }
            sb.append("]}]");

            ValidationResult result = StylelintOutputParser.parse(sb.toString());
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(100);
        }
    }
}
