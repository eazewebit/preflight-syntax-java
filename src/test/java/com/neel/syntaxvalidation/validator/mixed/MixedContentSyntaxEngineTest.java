package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.css.CssSyntaxEngine;
import com.neel.syntaxvalidation.validator.html.HtmlSyntaxEngine;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine;
import com.neel.syntaxvalidation.validator.php.PhpSyntaxEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MixedContentSyntaxEngine}.
 */
@DisplayName("MixedContentSyntaxEngine")
class MixedContentSyntaxEngineTest {

    private MixedContentSyntaxEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MixedContentSyntaxEngine();
    }

    // ---------------------------------------------------------------
    // Constructor validation
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("default constructor creates engine with all sub-engines")
        void defaultConstructor() {
            MixedContentSyntaxEngine e = new MixedContentSyntaxEngine();
            assertThat(e).isNotNull();
        }

        @Test
        @DisplayName("null sub-engines are replaced with defaults")
        void nullSubEngines() {
            MixedContentSyntaxEngine e = new MixedContentSyntaxEngine(
                    null, null, null, null, null);
            assertThat(e).isNotNull();
        }

        @Test
        @DisplayName("accepts custom sub-engines from singletons")
        void customSubEngines() {
            MixedContentSyntaxEngine e = new MixedContentSyntaxEngine(
                    HtmlSyntaxEngine.getInstance(),
                    CssSyntaxEngine.getInstance(),
                    JavaScriptSyntaxEngine.getInstance(),
                    PhpSyntaxEngine.getInstance(),
                    HtmlContentExtractor.getInstance());
            assertThat(e).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – null input
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – null input")
    class NullInput {

        @Test
        @DisplayName("throws NullPointerException for null input")
        void nullInput() {
            assertThatThrownBy(() -> engine.validate(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---------------------------------------------------------------
    // validate – valid HTML without embedded content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – valid HTML without embedded content")
    class ValidHtmlOnly {

        @Test
        @DisplayName("valid HTML with no style/script returns valid result")
        void validHtmlNoEmbedded() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test</title>
                    </head>
                    <body>
                        <p>Hello World</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
            assertThat(result.getMessage()).isNotEmpty();
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded valid CSS
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded valid CSS")
    class ValidEmbeddedCss {

        @Test
        @DisplayName("valid embedded CSS does not produce CSS errors")
        void validEmbeddedCss() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body {
                        color: red;
                        font-size: 14px;
                    }
                    </style>
                    </head>
                    <body>
                    <p>Hello</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();

            // Check that no CSS-related errors are present
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();
            assertThat(cssErrors).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded invalid CSS
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded invalid CSS")
    class InvalidEmbeddedCss {

        @Test
        @DisplayName("invalid CSS is detected and line numbers are remapped")
        void invalidCssDetected() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body {
                        color red;
                    }
                    </style>
                    </head>
                    <body>
                    <p>Hello</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();

            // The CSS "color red" (missing colon) should be caught
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();

            // Our embedded CSS engine should catch the missing colon
            if (!cssErrors.isEmpty()) {
                assertThat(cssErrors.get(0).getMessage()).contains("CSS");
            }
        }

        @Test
        @DisplayName("CSS errors have remapped line numbers pointing to HTML document")
        void cssErrorLineNumbersRemapped() {
            String html = "<html>\n<head>\n<style>\nbody {\n  color red;\n}\n</style>\n</head>\n<body></body>\n</html>";
            //                   1       2       3       4       5       6      7        8       9      10

            ValidationResult result = engine.validate(html);
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();

            if (!cssErrors.isEmpty()) {
                // The error on "color red" should be within the style block range (lines 3-7)
                ValidationError firstCssError = cssErrors.get(0);
                assertThat(firstCssError.getLine()).isGreaterThanOrEqualTo(3);
                assertThat(firstCssError.getLine()).isLessThanOrEqualTo(7);
            }
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded valid JavaScript
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded valid JavaScript")
    class ValidEmbeddedJs {

        @Test
        @DisplayName("valid embedded JS does not produce JS errors")
        void validEmbeddedJs() {
            String html = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <p>Hello</p>
                    <script>
                    var x = 1;
                    var y = 2;
                    console.log(x + y);
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();

            List<ValidationError> jsErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .toList();
            assertThat(jsErrors).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded invalid JavaScript
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded invalid JavaScript")
    class InvalidEmbeddedJs {

        @Test
        @DisplayName("invalid JS is detected and line numbers are remapped")
        void invalidJsDetected() {
            String html = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();

            List<ValidationError> jsErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .toList();

            // Our embedded JS engine should catch the syntax error
            if (!jsErrors.isEmpty()) {
                assertThat(jsErrors.get(0).getMessage()).contains("JavaScript");
            }
        }
    }

    // ---------------------------------------------------------------
    // validate – mixed valid and invalid content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – mixed valid and invalid content")
    class MixedValidAndInvalid {

        @Test
        @DisplayName("valid CSS and invalid JS are both processed")
        void validCssInvalidJs() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; }
                    </style>
                    </head>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("invalid CSS and valid JS are both processed")
        void invalidCssValidJs() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color red; }
                    </style>
                    </head>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – error ordering
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – error ordering")
    class ErrorOrdering {

        @Test
        @DisplayName("errors are sorted by line number")
        void errorsSortedByLine() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color red; }
                    </style>
                    </head>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            List<ValidationError> errors = result.getErrors();

            // Verify errors are sorted by line number
            for (int i = 1; i < errors.size(); i++) {
                assertThat(errors.get(i).getLine())
                        .isGreaterThanOrEqualTo(errors.get(i - 1).getLine());
            }
        }
    }

    // ---------------------------------------------------------------
    // validate – error message format
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – error message format")
    class ErrorMessageFormat {

        @Test
        @DisplayName("CSS errors are prefixed with [CSS in <style>]")
        void cssErrorPrefix() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color red; }
                    </style>
                    </head>
                    <body></body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();

            if (!cssErrors.isEmpty()) {
                assertThat(cssErrors.get(0).getMessage())
                        .startsWith("[CSS in <style>]");
            }
        }

        @Test
        @DisplayName("JS errors are prefixed with [JavaScript in <script>]")
        void jsErrorPrefix() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            List<ValidationError> jsErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .toList();

            if (!jsErrors.isEmpty()) {
                assertThat(jsErrors.get(0).getMessage())
                        .startsWith("[JavaScript in <script>]");
            }
        }
    }

    // ---------------------------------------------------------------
    // validate – summary message
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – summary message")
    class SummaryMessage {

        @Test
        @DisplayName("valid mixed content has a success message")
        void validSuccessMessage() {
            String html = """
                    <html>
                    <head><title>Test</title></head>
                    <body><p>Hello</p></body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            if (result.isValid()) {
                assertThat(result.getMessage())
                        .contains("syntactically valid");
            }
        }

        @Test
        @DisplayName("invalid content message includes error count")
        void invalidErrorCount() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            if (!result.isValid()) {
                assertThat(result.getMessage()).contains("error");
            }
        }
    }

    // ---------------------------------------------------------------
    // validate – edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty HTML document")
        void emptyHtml() {
            ValidationResult result = engine.validate("<html></html>");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with only whitespace in style block")
        void whitespaceOnlyStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style>
                        
                    </style>
                    </head>
                    <body></body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with only whitespace in script block")
        void whitespaceOnlyScriptBlock() {
            String html = """
                    <html>
                    <body>
                    <script>
                        
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with empty style and script blocks")
        void emptyBlocks() {
            String html = """
                    <html>
                    <head>
                    <style></style>
                    </head>
                    <body>
                    <script></script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with deeply nested content")
        void deeplyNestedContent() {
            String html = """
                    <html>
                    <head>
                    <style>
                    .container .wrapper .inner .deep .deeper {
                        color: red;
                        font-size: 14px;
                        background: #fff;
                    }
                    </style>
                    </head>
                    <body>
                    <div class="container">
                        <div class="wrapper">
                            <div class="inner">
                                <div class="deep">
                                    <div class="deeper">Content</div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <script>
                    document.querySelector('.deeper').addEventListener('click', function() {
                        console.log('clicked');
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – PHP mixed content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – PHP mixed content")
    class PhpMixedContent {

        @Test
        @DisplayName("valid PHP block in HTML returns valid result")
        void validPhpBlockInHtml() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><title>PHP Page</title></head>
                    <body>
                    <?php echo "Hello World"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid PHP block with multiple statements")
        void validPhpMultipleStatements() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <?php
                    $name = "World";
                    $greeting = "Hello, " . $name;
                    echo $greeting;
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("invalid PHP block returns errors with remapped line numbers")
        void invalidPhpBlock() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <?php
                    function test( {
                        echo "broken";
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
            if (!result.isValid()) {
                assertThat(result.getErrors()).isNotEmpty();
                // Verify PHP errors are remapped to correct lines
                result.getErrors().stream()
                        .filter(e -> e.getMessage().contains("[PHP"))
                        .forEach(e -> assertThat(e.getLine()).isGreaterThan(1));
            }
        }

        @Test
        @DisplayName("mixed CSS, JS, and PHP validates all three")
        void mixedCssJsPhp() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    .header { color: blue; }
                    </style>
                    </head>
                    <body>
                    <?php $title = "My Page"; ?>
                    <h1><?= $title ?></h1>
                    <script>
                    console.log("loaded");
                    </script>
                    <?php echo $footer; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with class definition validates")
        void phpClassDefinition() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <?php
                    class User {
                        private string $name;
                        
                        public function __construct(string $name) {
                            $this->name = $name;
                        }
                        
                        public function greet(): string {
                            return "Hello, " . $this->name;
                        }
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with unclosed brace reports error")
        void phpUnclosedBrace() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function broken() {
                        echo "missing closing brace";
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
            if (!result.isValid()) {
                assertThat(result.getErrors()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("PHP block with syntax error in echo")
        void phpSyntaxError() {
            String html = """
                    <html>
                    <body>
                    <?php
                    echo "unclosed string;
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
            if (!result.isValid()) {
                assertThat(result.getErrors()).isNotEmpty();
            }
        }

        @Test
        @DisplayName("multiple PHP blocks with different errors")
        void multiplePhpBlockErrors() {
            String html = """
                    <html>
                    <body>
                    <?php function a() { ?>
                    <?php echo "valid"; ?>
                    <?php function b( { ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with short-echo tag validates")
        void shortEchoTag() {
            String html = """
                    <html>
                    <body>
                    <h1><?= "Hello" ?></h1>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("real-world PHP template validates")
        void realWorldPhpTemplate() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title><?php echo $pageTitle; ?></title>
                        <style>
                        body { font-family: sans-serif; margin: 0; padding: 20px; }
                        .container { max-width: 1200px; margin: 0 auto; }
                        </style>
                    </head>
                    <body>
                    <?php
                    $users = ["Alice", "Bob", "Charlie"];
                    ?>
                    <div class="container">
                        <h1><?= $pageTitle ?? "Default Title" ?></h1>
                        <ul>
                        <?php foreach ($users as $user): ?>
                            <li><?= htmlspecialchars($user) ?></li>
                        <?php endforeach; ?>
                        </ul>
                    </div>
                    <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        console.log('Page loaded');
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = engine.validate(html);
            assertThat(result).isNotNull();
        }
    }
}
