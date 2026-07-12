package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive unit tests for the embedded {@link HtmlSyntaxEngine}.
 *
 * <p>Tests are organized into logical groups covering each validation phase:
 * <ul>
 *   <li>Empty / null input handling</li>
 *   <li>Valid HTML accepted without false positives</li>
 *   <li>Comment syntax validation</li>
 *   <li>CDATA section validation</li>
 *   <li>DOCTYPE validation</li>
 *   <li>Tag structure and nesting validation</li>
 *   <li>Attribute validation</li>
 *   <li>Void element validation</li>
 *   <li>Raw text element (script/style) handling</li>
 *   <li>Complex real-world scenarios</li>
 * </ul>
 */
@DisplayName("HtmlSyntaxEngine")
class HtmlSyntaxEngineTest {

    private HtmlSyntaxEngine engine;

    @BeforeEach
    void setUp() {
        engine = HtmlSyntaxEngine.getInstance();
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
    //  Valid HTML — no false positives
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("valid HTML documents")
    class ValidHtml {

        @Test
        @DisplayName("simple paragraph is valid")
        void simpleParagraph_isValid() {
            String html = "<p>Hello, World!</p>";
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("properly nested div structure is valid")
        void nestedDivs_isValid() {
            String html = """
                    <div>
                      <div>
                        <p>Content</p>
                      </div>
                    </div>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("HTML5 doctype with head and body is valid")
        void html5Doctype_isValid() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                      <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                      </head>
                      <body>
                        <h1>Hello</h1>
                      </body>
                    </html>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("void elements without self-closing are valid")
        void voidElements_noSelfClosing_isValid() {
            String html = """
                    <div>
                      <br>
                      <hr>
                      <img src="pic.jpg" alt="picture">
                      <input type="text" name="q">
                      <meta charset="UTF-8">
                      <link rel="stylesheet" href="style.css">
                    </div>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("void elements with self-closing syntax are valid")
        void voidElements_selfClosing_isValid() {
            String html = """
                    <div>
                      <br/>
                      <hr/>
                      <img src="pic.jpg" alt="picture"/>
                      <input type="text" name="q"/>
                    </div>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("HTML comments are valid")
        void comments_areValid() {
            String html = """
                    <!-- This is a comment -->
                    <p>Content</p>
                    <!-- Another comment -->
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("attributes with single and double quotes are valid")
        void quotedAttributes_areValid() {
            String html = "<a href='https://example.com' target=\"_blank\">Link</a>";
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("boolean attributes are valid")
        void booleanAttributes_areValid() {
            String html = "<input disabled required autofocus>";
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("CDATA section is valid")
        void cdataSection_isValid() {
            String html = """
                    <div><![CDATA[Some content here]]></div>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("script tag with JavaScript content is valid")
        void scriptTag_isValid() {
            String html = """
                    <script>
                      const x = 1;
                      if (x < 2 && x > 0) {
                        console.log("hello");
                      }
                    </script>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("style tag with CSS content is valid")
        void styleTag_isValid() {
            String html = """
                    <style>
                      .container { display: flex; }
                      p { color: red; }
                    </style>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple data-* attributes are valid")
        void dataAttributes_areValid() {
            String html = "<div data-id='123' data-name=\"test\" data-value=\"\">Content</div>";
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("empty string attribute value is valid")
        void emptyAttributeValue_isValid() {
            String html = "<input type=\"text\" value=\"\" placeholder=\"\">";
            assertThat(engine.validate(html).isValid()).isTrue();
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
            String html = """
                    <p>Content</p>
                    <!-- This comment is never closed
                    <p>More content</p>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed"));
        }

        @Test
        @DisplayName("nested comments are detected")
        void nestedComments_areDetected() {
            String html = """
                    <!-- outer <!-- inner --> outer -->
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("nested"));
        }

        @Test
        @DisplayName("double hyphens inside comment are detected")
        void doubleHyphensInComment_areDetected() {
            String html = """
                    <!-- This has -- double hyphens -->
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("double hyphen"));
        }
    }

    // -----------------------------------------------------------------
    //  CDATA validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("CDATA validation")
    class CdataValidation {

        @Test
        @DisplayName("unclosed CDATA section is detected")
        void unclosedCdata_isDetected() {
            String html = """
                    <div><![CDATA[ This section is never closed
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed CDATA"));
        }

        @Test
        @DisplayName("properly closed CDATA section is valid")
        void closedCdata_isValid() {
            String html = """
                    <div><![CDATA[Content here]]></div>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  DOCTYPE validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("DOCTYPE validation")
    class DoctypeValidation {

        @Test
        @DisplayName("malformed DOCTYPE without exclamation mark is detected")
        void malformedDoctype_isDetected() {
            String html = """
                    <DOCTYPE html>
                    <html><head></head><body></body></html>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("DOCTYPE"));
        }

        @Test
        @DisplayName("standard HTML5 DOCTYPE is valid")
        void html5Doctype_isValid() {
            String html = "<!DOCTYPE html><html><head></head><body></body></html>";
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("DOCTYPE with public identifier is valid")
        void publicDoctype_isValid() {
            String html = """
                    <!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN"
                    "http://www.w3.org/TR/html4/strict.dtd">
                    <html><head></head><body></body></html>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }
    }

    // -----------------------------------------------------------------
    //  Tag structure and nesting validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("tag structure validation")
    class TagStructureValidation {

        @Test
        @DisplayName("unclosed tag is detected")
        void unclosedTag_isDetected() {
            String html = """
                    <div>
                      <p>Content
                    </div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed")
                            .containsIgnoringCase("<p>"));
        }

        @Test
        @DisplayName("unexpected closing tag is detected")
        void unexpectedClosingTag_isDetected() {
            String html = """
                    <div>
                      Content
                    </p>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unexpected")
                            .containsIgnoringCase("</p>"));
        }

        @Test
        @DisplayName("improperly nested tags are detected")
        void improperNesting_isDetected() {
            String html = """
                    <div><p>Content</div></p>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("improperly nested"));
        }

        @Test
        @DisplayName("closing tag for void element is detected")
        void voidElementClosingTag_isDetected() {
            String html = """
                    <br></br>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("void element"));
        }

        @Test
        @DisplayName("multiple unclosed tags are all reported")
        void multipleUnclosedTags_areReported() {
            String html = """
                    <div>
                      <span>
                        <p>Content
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
        }
    }

    // -----------------------------------------------------------------
    //  Attribute validation
    // -----------------------------------------------------------------

    @Nested
    @DisplayName("attribute validation")
    class AttributeValidation {

        @Test
        @DisplayName("duplicate attributes are detected")
        void duplicateAttributes_areDetected() {
            String html = """
                    <div class="first" class="second">Content</div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("duplicate")
                            .containsIgnoringCase("class"));
        }

        @Test
        @DisplayName("attribute with equals but no value is detected")
        void attributeEqualsNoValue_isDetected() {
            String html = """
                    <div class=>Content</div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("no value"));
        }

        @Test
        @DisplayName("unclosed attribute quote is detected")
        void unclosedAttributeQuote_isDetected() {
            String html = """
                    <a href="https://example.com>Link</a>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anySatisfy(e ->
                    assertThat(e.getMessage()).containsIgnoringCase("unclosed"));
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
            String html = """
                    <div>
                      <p>Content
                    </div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).allSatisfy(e -> {
                assertThat(e.getLine()).isPositive();
                assertThat(e.getColumn()).isPositive();
            });
        }

        @Test
        @DisplayName("errors have non-blank messages")
        void errorsHaveNonBlankMessages() {
            String html = """
                    <div>
                      <p>Content
                    </div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).allSatisfy(e ->
                    assertThat(e.getMessage()).isNotBlank());
        }

        @Test
        @DisplayName("invalid result has a descriptive message")
        void invalidResultHasDescriptiveMessage() {
            String html = """
                    <div>
                      <p>Content
                    </div>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("failed");
        }

        @Test
        @DisplayName("valid result has a positive message")
        void validResultHasPositiveMessage() {
            String html = "<p>Content</p>";
            ValidationResult result = engine.validate(html);
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
        @DisplayName("large HTML document with mixed content is validated")
        void largeDocument_isValidated() {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n<html>\n<head><title>Big</title></head>\n<body>\n");
            for (int i = 0; i < 100; i++) {
                sb.append("<div class=\"item-").append(i).append("\">");
                sb.append("<p>Item ").append(i).append("</p>\n");
                sb.append("</div>\n");
            }
            sb.append("</body>\n</html>");
            assertThat(engine.validate(sb.toString()).isValid()).isTrue();
        }

        @Test
        @DisplayName("HTML with comments, script, and style is valid")
        void mixedContent_isValid() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                      <!-- Comment -->
                      <style>
                        body { margin: 0; }
                      </style>
                    </head>
                    <body>
                      <h1>Title</h1>
                      <p>Paragraph</p>
                      <script>
                        var x = 10;
                        if (x < 5) { console.log("hi"); }
                      </script>
                      <!-- Another comment -->
                    </body>
                    </html>
                    """;
            assertThat(engine.validate(html).isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple errors in complex document are all detected")
        void multipleErrors_complexDocument_areDetected() {
            String html = """
                    <DOCTYPE html>
                    <html>
                    <head>
                      <!-- unclosed comment
                      <meta charset=UTF-8>
                    </head>
                    <body>
                      <div>
                        <p>Content
                        <span>More</span>
                      </div>
                      <br></br>
                      <img src="pic.jpg" alt="photo" alt="dup">
                    </body>
                    </html>
                    """;
            ValidationResult result = engine.validate(html);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSizeGreaterThanOrEqualTo(3);
        }
    }
}
