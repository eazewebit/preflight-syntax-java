package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.model.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TscOutputParser}.
 */
@DisplayName("TscOutputParser")
class TscOutputParserTest {

    private TscOutputParser parser;

    @BeforeEach
    void setUp() {
        parser = new TscOutputParser();
    }

    @Nested
    @DisplayName("null and empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("should return empty list for null input")
        void shouldReturnEmptyListForNullInput() {
            List<ValidationError> errors = parser.parse(null);
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for empty string")
        void shouldReturnEmptyListForEmptyString() {
            List<ValidationError> errors = parser.parse("");
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank string")
        void shouldReturnEmptyListForBlankString() {
            List<ValidationError> errors = parser.parse("   \n\t  ");
            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("pretty=false format")
    class PrettyFalseFormat {

        @Test
        @DisplayName("should parse single error")
        void shouldParseSingleError() {
            String output = "file.ts:5:10 - error TS2322: Type 'string' is not assignable to type 'number'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(5);
            assertThat(errors.getFirst().getColumn()).isEqualTo(10);
            assertThat(errors.getFirst().getMessage()).contains("Type 'string' is not assignable to type 'number'");
        }

        @Test
        @DisplayName("should parse multiple errors")
        void shouldParseMultipleErrors() {
            String output = """
                    file.ts:1:1 - error TS2304: Cannot find name 'foo'.
                    file.ts:5:10 - error TS2322: Type 'string' is not assignable to type 'number'.
                    file.ts:10:3 - error TS2551: Property 'nam' does not exist on type 'User'. Did you mean 'name'?
                    """;
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(3);
            assertThat(errors.get(0).getLine()).isEqualTo(1);
            assertThat(errors.get(1).getLine()).isEqualTo(5);
            assertThat(errors.get(2).getLine()).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle Windows path format")
        void shouldHandleWindowsPathFormat() {
            String output = "src\\components\\App.tsx:15:5 - error TS2304: Cannot find name 'useState'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(15);
            assertThat(errors.getFirst().getColumn()).isEqualTo(5);
        }

        @Test
        @DisplayName("should handle Unix path format")
        void shouldHandleUnixPathFormat() {
            String output = "src/components/App.tsx:15:5 - error TS2304: Cannot find name 'useState'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(15);
            assertThat(errors.getFirst().getColumn()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("default format (with parentheses)")
    class DefaultFormat {

        @Test
        @DisplayName("should parse default format errors")
        void shouldParseDefaultFormatErrors() {
            String output = "file.ts(5,10): error TS2322: Type 'string' is not assignable to type 'number'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(5);
            assertThat(errors.getFirst().getColumn()).isEqualTo(10);
            assertThat(errors.getFirst().getMessage()).contains("Type 'string' is not assignable to type 'number'");
        }

        @Test
        @DisplayName("should parse multiple default format errors")
        void shouldParseMultipleDefaultFormatErrors() {
            String output = """
                    file.ts(1,1): error TS2304: Cannot find name 'foo'.
                    file.ts(5,10): error TS2322: Type 'string' is not assignable to type 'number'.
                    """;
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(2);
        }
    }

    @Nested
    @DisplayName("column range format")
    class ColumnRangeFormat {

        @Test
        @DisplayName("should parse column range format")
        void shouldParseColumnRangeFormat() {
            String output = "file.ts(5,10) - error TS2322: Type 'string' is not assignable to type 'number'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(5);
            assertThat(errors.getFirst().getColumn()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("non-error output")
    class NonErrorOutput {

        @Test
        @DisplayName("should ignore warning output")
        void shouldIgnoreWarningOutput() {
            String output = "file.ts:5:10 - warning TS5942: This may be a false positive.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("should ignore informational output")
        void shouldIgnoreInformationalOutput() {
            String output = "Found 3 errors. Watching for file changes.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).isEmpty();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle error with complex message")
        void shouldHandleErrorWithComplexMessage() {
            String output = "file.ts:1:1 - error TS2345: Argument of type '{ name: string; age: number; }' " +
                    "is not assignable to parameter of type 'User'.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getMessage()).contains("Argument of type");
        }

        @Test
        @DisplayName("should handle very long error messages")
        void shouldHandleVeryLongErrorMessages() {
            String longMessage = "x".repeat(10000);
            String output = "file.ts:1:1 - error TS9999: " + longMessage;
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getMessage()).isEqualTo(longMessage);
        }

        @Test
        @DisplayName("should handle error with zero line and column")
        void shouldHandleErrorWithZeroLineAndColumn() {
            String output = "file.ts:0:0 - error TS9999: Some error at position zero.";
            List<ValidationError> errors = parser.parse(output);

            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isZero();
            assertThat(errors.getFirst().getColumn()).isZero();
        }

        @Test
        @DisplayName("should handle mixed format output")
        void shouldHandleMixedFormatOutput() {
            String output = """
                    file.ts:1:1 - error TS2304: Cannot find name 'foo'.
                    file.ts(5,10): error TS2322: Type 'string' is not assignable to type 'number'.
                    """;
            // Only the first format (pretty=false) should match
            List<ValidationError> errors = parser.parse(output);

            // Should parse at least the first format
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getLine()).isEqualTo(1);
        }
    }
}
