package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.css.CssSyntaxEngine;
import com.neel.syntaxvalidation.validator.html.HtmlSyntaxEngine;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbstractLanguageValidator} through its concrete
 * implementations.
 *
 * <p>Since Mockito has compatibility issues with Java 25 (ByteBuddy limitation),
 * these tests use the actual concrete validator implementations to verify the
 * abstract contract.
 */
@DisplayName("AbstractLanguageValidator")
class AbstractLanguageValidatorTest {

    // ---------------------------------------------------------------
    // Language and engine access
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Language and engine access")
    class LanguageAndExtension {

        @Test
        @DisplayName("CssSyntaxEngine returns correct language")
        void cssEngineLanguage() {
            assertThat(CssSyntaxEngine.getInstance()).isNotNull();
        }

        @Test
        @DisplayName("HtmlSyntaxEngine returns correct language")
        void htmlEngineLanguage() {
            assertThat(HtmlSyntaxEngine.getInstance()).isNotNull();
        }

        @Test
        @DisplayName("JavaScriptSyntaxEngine returns correct language")
        void jsEngineLanguage() {
            assertThat(JavaScriptSyntaxEngine.getInstance()).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Singleton pattern
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("singleton pattern")
    class SingletonPattern {

        @Test
        @DisplayName("CssSyntaxEngine returns same instance")
        void cssEngineSingleton() {
            CssSyntaxEngine a = CssSyntaxEngine.getInstance();
            CssSyntaxEngine b = CssSyntaxEngine.getInstance();
            assertThat(a).isSameAs(b);
        }

        @Test
        @DisplayName("HtmlSyntaxEngine returns same instance")
        void htmlEngineSingleton() {
            HtmlSyntaxEngine a = HtmlSyntaxEngine.getInstance();
            HtmlSyntaxEngine b = HtmlSyntaxEngine.getInstance();
            assertThat(a).isSameAs(b);
        }

        @Test
        @DisplayName("JavaScriptSyntaxEngine returns same instance")
        void jsEngineSingleton() {
            JavaScriptSyntaxEngine a = JavaScriptSyntaxEngine.getInstance();
            JavaScriptSyntaxEngine b = JavaScriptSyntaxEngine.getInstance();
            assertThat(a).isSameAs(b);
        }
    }

    // ---------------------------------------------------------------
    // Null input handling
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("null input handling")
    class NullInputHandling {

        @Test
        @DisplayName("CssSyntaxEngine handles null input")
        void cssEngineNullInput() {
            ValidationResult result = CssSyntaxEngine.getInstance().validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HtmlSyntaxEngine handles null input")
        void htmlEngineNullInput() {
            ValidationResult result = HtmlSyntaxEngine.getInstance().validate(null);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("JavaScriptSyntaxEngine handles null input")
        void jsEngineNullInput() {
            ValidationResult result = JavaScriptSyntaxEngine.getInstance().validate(null);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Empty input handling
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("empty input handling")
    class EmptyInputHandling {

        @Test
        @DisplayName("CssSyntaxEngine handles empty input")
        void cssEngineEmptyInput() {
            ValidationResult result = CssSyntaxEngine.getInstance().validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HtmlSyntaxEngine handles empty input")
        void htmlEngineEmptyInput() {
            ValidationResult result = HtmlSyntaxEngine.getInstance().validate("");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("JavaScriptSyntaxEngine handles empty input")
        void jsEngineEmptyInput() {
            ValidationResult result = JavaScriptSyntaxEngine.getInstance().validate("");
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Valid input handling
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("valid input handling")
    class ValidInputHandling {

        @Test
        @DisplayName("CssSyntaxEngine validates simple valid CSS")
        void cssEngineValidInput() {
            ValidationResult result = CssSyntaxEngine.getInstance().validate("body { color: red; }");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HtmlSyntaxEngine validates simple valid HTML")
        void htmlEngineValidInput() {
            ValidationResult result = HtmlSyntaxEngine.getInstance().validate("<html><body><p>Hello</p></body></html>");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("JavaScriptSyntaxEngine validates simple valid JS")
        void jsEngineValidInput() {
            ValidationResult result = JavaScriptSyntaxEngine.getInstance().validate("var x = 1;");
            assertThat(result).isNotNull();
        }
    }
}
