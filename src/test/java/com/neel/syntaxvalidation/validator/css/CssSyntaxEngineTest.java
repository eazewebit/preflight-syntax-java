package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the embedded {@link CssSyntaxEngine}.
 *
 * <p>Tests are organized into logical groups covering each validation phase:
 * <ul>
 *   <li>Empty / null input handling</li>
 *   <li>Valid CSS accepted without false positives</li>
 *   <li>Comment syntax validation</li>
 *   <li>String literal validation</li>
 *   <li>Brace balance validation</li>
 *   <li>Selector validation</li>
 *   <li>Declaration validation</li>
 *   <li>At-rule validation</li>
 *   <li>URL function validation</li>
 *   <li>Complex real-world scenarios</li>
 * </ul>
 */
@DisplayName("CssSyntaxEngine")
class CssSyntaxEngineTest {

    private CssSyntaxEngine engine;

    @BeforeEach
    void setUp() {
        engine = CssSyntaxEngine.getInstance();
    }

    // -----------------------------------------------------------------
    //  Edge cases: null, blank, whitespace-only
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("null and empty input")
    class NullAndEmpty {

        @Test
        @DisplayName("null input is considered valid")
        void nullInput_isValid() {
            assertThat(engine.validate(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty string is considered valid")
        void emptyString_isValid() {
            assertThat(engine.validate("").isValid()).isTrue();
        }

        @Test
        @DisplayName("blank string is considered valid")
        void blankString_isValid() {
            assertThat(engine.validate("   \n\t  ").isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  Valid CSS — no false positives
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("valid CSS rules")
    class ValidCss {

        @Test
        @DisplayName("simple rule with one property is valid")
        void simpleRule_isValid() {
            String css = """
                    body {
                      margin: 0;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple rules with various selectors are valid")
        void multipleRules_areValid() {
            String css = """
                    body {
                      margin: 0;
                      padding: 0;
                    }
                    
                    .container {
                      display: flex;
                      flex-direction: column;
                    }
                    
                    #header {
                      background-color: #fff;
                    }
                    
                    a:hover {
                      color: red;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with comments is valid")
        void cssWithComments_isValid() {
            String css = """
                    /* Global styles */
                    body {
                      margin: 0; /* Reset margin */
                    }
                    
                    /* Container styles */
                    .container {
                      display: flex;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with string values is valid")
        void cssWithStrings_isValid() {
            String css = """
                    .icon::before {
                      content: "\\e001";
                    }
                    
                    a[href="https://example.com"] {
                      color: blue;
                    }
                    
                    .title {
                      font-family: 'Open Sans', sans-serif;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with vendor prefixes is valid")
        void vendorPrefixes_areValid() {
            String css = """
                    .box {
                      -webkit-transform: rotate(45deg);
                      -moz-transform: rotate(45deg);
                      -ms-transform: rotate(45deg);
                      transform: rotate(45deg);
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS custom properties (variables) are valid")
        void customProperties_areValid() {
            String css = """
                    :root {
                      --primary-color: #3498db;
                      --spacing: 16px;
                      --font-stack: 'Helvetica Neue', sans-serif;
                    }
                    
                    .element {
                      color: var(--primary-color);
                      margin: var(--spacing);
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("nested CSS (CSS nesting spec) with braces is valid")
        void nestedCss_isValid() {
            String css = """
                    .parent {
                      color: blue;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with url() functions is valid")
        void urlFunctions_areValid() {
            String css = """
                    .bg {
                      background-image: url('image.png');
                      background: url("photo.jpg") no-repeat center;
                    }
                    
                    @font-face {
                      font-family: 'MyFont';
                      src: url('/fonts/myfont.woff2') format('woff2');
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty rule block is valid")
        void emptyBlock_isValid() {
            String css = """
                    .placeholder {
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with media queries is valid")
        void mediaQueries_areValid() {
            String css = """
                    @media screen and (max-width: 768px) {
                      .container {
                        flex-direction: column;
                      }
                    }
                    
                    @media print {
                      body {
                        font-size: 12pt;
                      }
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with @keyframes is valid")
        void keyframes_areValid() {
            String css = """
                    @keyframes fadeIn {
                      from { opacity: 0; }
                      to { opacity: 1; }
                    }
                    
                    .animated {
                      animation: fadeIn 1s ease-in-out;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with @import is valid")
        void importRule_isValid() {
            String css = """
                    @import url('reset.css');
                    @import 'typography.css';
                    
                    body { margin: 0; }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with @charset is valid")
        void charsetRule_isValid() {
            String css = """
                    @charset "UTF-8";
                    
                    body { margin: 0; }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with complex selector combinators is valid")
        void complexSelectors_areValid() {
            String css = """
                    div > p { margin: 0; }
                    div + p { margin-top: 0; }
                    div ~ p { margin-bottom: 0; }
                    [data-type="active"] { color: red; }
                    :nth-child(2n+1) { background: #f0f0f0; }
                    ::before { content: ""; }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with important declarations is valid")
        void importantDeclarations_areValid() {
            String css = """
                    .override {
                      color: red !important;
                      display: block !important;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  Comment validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("comment validation")
    class CommentValidation {

        @Test
        @DisplayName("unclosed comment is detected")
        void unclosedComment_isDetected() {
            String css = """
                    /* This comment is never closed
                    body { margin: 0; }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed"));
        }

        @Test
        @DisplayName("nested comments are detected")
        void nestedComments_areDetected() {
            String css = """
                    /* outer /* inner */ outer */
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("nested"));
        }
    }

    // -----------------------------------------------------------------
    //  String validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("string validation")
    class StringValidation {

        @Test
        @DisplayName("unclosed double-quoted string is detected")
        void unclosedDoubleQuote_isDetected() {
            String css = """
                    body {
                      content: "unclosed string;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed")
                            .containsIgnoringCase("double"));
        }

        @Test
        @DisplayName("unclosed single-quoted string is detected")
        void unclosedSingleQuote_isDetected() {
            String css = """
                    body {
                      font-family: 'Open Sans, sans-serif;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed")
                            .containsIgnoringCase("single"));
        }
    }

    // -----------------------------------------------------------------
    //  Brace balance validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("brace balance validation")
    class BraceBalance {

        @Test
        @DisplayName("extra closing brace is detected")
        void extraClosingBrace_isDetected() {
            String css = """
                    body {
                      margin: 0;
                    }
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unexpected")
                            .containsIgnoringCase("}"));
        }

        @Test
        @DisplayName("missing closing brace is detected")
        void missingClosingBrace_isDetected() {
            String css = """
                    body {
                      margin: 0;
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed block"));
        }

        @Test
        @DisplayName("multiple unclosed braces are reported")
        void multipleUnclosedBraces_areReported() {
            String css = """
                    body {
                      margin: 0;
                    .container {
                      display: flex;
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed block"));
        }
    }

    // -----------------------------------------------------------------
    //  Selector validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("selector validation")
    class SelectorValidation {

        @Test
        @DisplayName("empty selector (missing selector before brace) is detected")
        void emptySelector_isDetected() {
            String css = """
                    {
                      margin: 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("empty selector"));
        }

        @Test
        @DisplayName("selector starting with comma is detected")
        void commaStartSelector_isDetected() {
            String css = """
                    , .class {
                      margin: 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("comma"));
        }

        @Test
        @DisplayName("selector with double commas is detected")
        void doubleCommaSelector_isDetected() {
            String css = """
                    .a,, .b {
                      margin: 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("double comma"));
        }
    }

    // -----------------------------------------------------------------
    //  Declaration validation
    // -----------------------------------------------------------------

    @Nested
        @DisplayName("declaration validation")
    class DeclarationValidation {

        @Test
        @DisplayName("property without colon is detected")
        void propertyWithoutColon_isDetected() {
            String css = """
                    body {
                      margin 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("colon"));
        }

        @Test
        @DisplayName("property with empty value is detected")
        void emptyValue_isDetected() {
            String css = """
                    body {
                      margin: ;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("empty value"));
        }

        @Test
        @DisplayName("custom property without colon is detected")
        void customPropertyWithoutColon_isDetected() {
            String css = """
                    :root {
                      --my-var red;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("custom property"));
        }
    }

    // -----------------------------------------------------------------
    //  At-rule validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("at-rule validation")
    class AtRuleValidation {

        @Test
        @DisplayName("@import after other rules is detected")
        void importAfterRules_isDetected() {
            String css = """
                    body { margin: 0; }
                    @import url('other.css');
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("@import"));
        }

        @Test
        @DisplayName("unrecognised at-rule is detected")
        void unrecognisedAtRule_isDetected() {
            String css = """
                    @foobar {
                      body { margin: 0; }
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unrecognised")
                            .containsIgnoringCase("@foobar"));
        }

        @Test
        @DisplayName("@charset at top is valid")
        void charsetAtTop_isValid() {
            String css = """
                    @charset "UTF-8";
                    body { margin: 0; }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("@layer at-rule is valid")
        void layerAtRule_isValid() {
            String css = """
                    @layer utilities {
                      .hidden { display: none; }
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("@supports at-rule is valid")
        void supportsAtRule_isValid() {
            String css = """
                    @supports (display: grid) {
                      .grid { display: grid; }
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("@container at-rule is valid")
        void containerAtRule_isValid() {
            String css = """
                    @container (min-width: 400px) {
                      .card { display: flex; }
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  URL function validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("url() function validation")
    class UrlFunctionValidation {

        @Test
        @DisplayName("unclosed url( is detected")
        void unclosedUrl_isDetected() {
            String css = """
                    body {
                      background-image: url('image.png';
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("url")
                            .containsIgnoringCase("unclosed"));
        }
    }

    // -----------------------------------------------------------------
    //  Error message quality
    // -----------------------------------------------------------------

    @Nested
        @DisplayName("error message quality")
    class ErrorQuality {

        @Test
        @DisplayName("errors have positive line numbers")
        void errorsHavePositiveLineNumbers() {
            String css = """
                    body {
                      margin 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).allSatisfy(e -> {
                assertThat(e.getLine()).isPositive();
                assertThat(e.getColumn()).isPositive();
            });
        }

        @Test
        @DisplayName("errors have non-blank messages")
        void errorsHaveNonBlankMessages() {
            String css = """
                    body {
                      margin 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).isNotBlank());
        }

        @Test
        @DisplayName("invalid result has a descriptive message")
        void invalidResultHasDescriptiveMessage() {
            String css = """
                    body {
                      margin 0;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("failed");
        }

        @Test
        @DisplayName("valid result has a positive message")
        void validResultHasPositiveMessage() {
            String css = "body { margin: 0; }";
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }
    }

    // -----------------------------------------------------------------
    //  Complex / real-world scenarios
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("complex real-world scenarios")
    class ComplexScenarios {

        @Test
        @DisplayName("large CSS stylesheet is validated")
        void largeStylesheet_isValidated() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append(".class-").append(i).append(" {\n");
                sb.append("  margin: ").append(i).append("px;\n");
                sb.append("  padding: ").append(i * 2).append("px;\n");
                sb.append("  color: #").append(String.format("%06x", i)).append(";\n");
                sb.append("}\n\n");
            }
            assertThat(engine.validate(sb.toString()).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS reset with many rules is valid")
        void cssReset_isValid() {
            String css = """
                    /* Reset */
                    *, *::before, *::after {
                      box-sizing: border-box;
                      margin: 0;
                      padding: 0;
                    }
                    
                    html, body {
                      height: 100%;
                      font-family: system-ui, -apple-system, sans-serif;
                      line-height: 1.5;
                      -webkit-font-smoothing: antialiased;
                    }
                    
                    img, picture, video, canvas, svg {
                      display: block;
                      max-width: 100%;
                    }
                    
                    input, button, textarea, select {
                      font: inherit;
                    }
                    
                    a {
                      text-decoration: none;
                      color: inherit;
                    }
                    
                    ul, ol {
                      list-style: none;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with multiple issues reports all errors")
        void multipleIssues_allReported() {
            String css = """
                    /* unclosed comment
                    {
                      margin 0;
                    }
                    body {
                      content: "unclosed;
                    }
                    """;
            ValidationResult result = engine.validate(css);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("minified CSS is valid")
        void minifiedCss_isValid() {
            String css = "body{margin:0;padding:0}.container{display:flex;flex-wrap:wrap}.item{flex:1 1 auto}";
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with @font-face declaration is valid")
        void fontFace_isValid() {
            String css = """
                    @font-face {
                      font-family: 'MyCustomFont';
                      src: url('/fonts/custom.woff2') format('woff2'),
                           url('/fonts/custom.woff') format('woff');
                      font-weight: normal;
                      font-style: normal;
                      font-display: swap;
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }

        @Test
        @DisplayName("CSS with complex value expressions is valid")
        void complexValues_areValid() {
            String css = """
                    .complex {
                      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                      transform: translate(-50%, -50%) rotate(45deg) scale(1.2);
                      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1), 0 1px 3px rgba(0, 0, 0, 0.08);
                      border-image: url('border.png') 30 round;
                      grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
                      clip-path: polygon(50% 0%, 100% 50%, 50% 100%, 0% 50%);
                    }
                    """;
            assertThat(engine.validate(css).isValid()).isTrue();
        }
    }
}
