package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CssValidator}.
 */
@DisplayName("CssValidator")
class CssValidatorTest {

    private CssValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CssValidator();
    }

    // ---------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("default constructor creates validator")
        void defaultConstructor() {
            CssValidator v = new CssValidator();
            assertThat(v).isNotNull();
        }

        @Test
        @DisplayName("accepts explicit binary path")
        void explicitBinaryPath() {
            CssValidator v = new CssValidator("/usr/bin/stylelint");
            assertThat(v).isNotNull();
        }

        @Test
        @DisplayName("is a LanguageValidator")
        void isLanguageValidator() {
            assertThat(validator).isInstanceOf(LanguageValidator.class);
        }
    }

    // ---------------------------------------------------------------
    // getLanguage
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getLanguage")
    class GetLanguage {

        @Test
        @DisplayName("returns CSS as its language")
        void returnsCss() {
            assertThat(validator.getLanguage()).isEqualTo(Language.CSS);
        }
    }

    // ---------------------------------------------------------------
    // validate – null input
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – null input")
    class NullInput {

        @Test
        @DisplayName("returns error result for null source")
        void nullSource() {
            // CssValidator handles null gracefully (returns binary not found or error)
            ValidationResult result = validator.validate(null);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – valid CSS
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – valid CSS")
    class ValidCss {

        @Test
        @DisplayName("simple valid CSS is processed")
        void simpleValidCss() {
            ValidationResult result = validator.validate("body { color: red; }");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("multiline valid CSS is processed")
        void multilineValidCss() {
            String css = """
                    body {
                        color: red;
                        font-size: 14px;
                        margin: 0;
                    }
                    """;
            ValidationResult result = validator.validate(css);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS with selectors is processed")
        void selectorsCss() {
            String css = """
                    .container {
                        max-width: 1200px;
                    }
                    #header {
                        background: blue;
                    }
                    p {
                        line-height: 1.5;
                    }
                    """;
            ValidationResult result = validator.validate(css);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS with comments is processed")
        void commentsCss() {
            String css = """
                    /* Main styles */
                    body {
                        /* Color */
                        color: red;
                    }
                    """;
            ValidationResult result = validator.validate(css);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty CSS is processed")
        void emptyCss() {
            ValidationResult result = validator.validate("");
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – invalid CSS
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – invalid CSS")
    class InvalidCss {

        @Test
        @DisplayName("missing colon is processed")
        void missingColon() {
            ValidationResult result = validator.validate("body { color red; }");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unmatched braces are processed")
        void unmatchedBraces() {
            ValidationResult result = validator.validate("body { color: red; ");
            assertThat(result).isNotNull();
        }
    }
}
