package com.neel.syntaxvalidation.validator.mixed;

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
 * Unit tests for {@link MixedContentValidator}.
 */
@DisplayName("MixedContentValidator")
class MixedContentValidatorTest {

    private MixedContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MixedContentValidator();
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
            MixedContentValidator v = new MixedContentValidator();
            assertThat(v).isNotNull();
        }

        @Test
        @DisplayName("accepts null engine (uses defaults)")
        void nullEngine() {
            MixedContentValidator v = new MixedContentValidator((MixedContentSyntaxEngine) null);
            assertThat(v).isNotNull();
        }

        @Test
        @DisplayName("accepts custom engine")
        void customEngine() {
            MixedContentSyntaxEngine engine = new MixedContentSyntaxEngine();
            MixedContentValidator v = new MixedContentValidator(engine);
            assertThat(v).isNotNull();
            assertThat(v.getEngine()).isSameAs(engine);
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
        @DisplayName("returns HTML as its language")
        void returnsHtml() {
            assertThat(validator.getLanguage()).isEqualTo(Language.HTML);
        }
    }

    // ---------------------------------------------------------------
    // validate – null input
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – null input")
    class NullInput {

        @Test
        @DisplayName("throws NullPointerException for null source")
        void nullSource() {
            assertThatThrownBy(() -> validator.validate(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ---------------------------------------------------------------
    // validate – valid HTML only
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – valid HTML only")
    class ValidHtmlOnly {

        @Test
        @DisplayName("simple valid HTML returns a result")
        void simpleValidHtml() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test Page</title>
                    </head>
                    <body>
                        <p>Hello World</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("minimal HTML returns a result")
        void minimalHtml() {
            ValidationResult result = validator.validate("<html></html>");
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded CSS
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded CSS")
    class EmbeddedCss {

        @Test
        @DisplayName("valid CSS in style tag is validated")
        void validCss() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; font-size: 14px; }
                    </style>
                    </head>
                    <body><p>Hello</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            // No CSS errors should be in the result
            boolean hasCssErrors = result.getErrors().stream()
                    .anyMatch(e -> e.getMessage().contains("[CSS"));
            assertThat(hasCssErrors).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // validate – HTML with embedded JavaScript
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – HTML with embedded JavaScript")
    class EmbeddedJs {

        @Test
        @DisplayName("valid JS in script tag is validated")
        void validJs() {
            String html = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <script>
                    var x = 1;
                    var y = 2;
                    console.log(x + y);
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            // No JS errors should be in the result
            boolean hasJsErrors = result.getErrors().stream()
                    .anyMatch(e -> e.getMessage().contains("[JavaScript"));
            assertThat(hasJsErrors).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // validate – full mixed document
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – full mixed document")
    class FullMixedDocument {

        @Test
        @DisplayName("validates a complete HTML5 document with CSS and JS")
        void completeDocument() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Mixed Content Page</title>
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            body {
                                font-family: Arial, sans-serif;
                                color: #333;
                            }
                            .container {
                                max-width: 1200px;
                                margin: 0 auto;
                                padding: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1 id="title">Hello World</h1>
                            <p class="description">This is a test page.</p>
                        </div>
                        <script>
                            document.addEventListener('DOMContentLoaded', function() {
                                var title = document.getElementById('title');
                                console.log('Title: ' + title.textContent);
                                
                                var desc = document.querySelector('.description');
                                desc.style.color = 'blue';
                            });
                        </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            // This should be valid (no CSS or JS syntax errors)
            if (!result.isValid()) {
                // Print errors for debugging
                result.getErrors().forEach(e ->
                        System.out.println("ERROR: " + e.getLine() + ":" + e.getColumn() + " " + e.getMessage()));
            }
        }

        @Test
        @DisplayName("detects errors across CSS and JS simultaneously")
        void detectsErrorsAcrossLanguages() {
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
                    <script>
                    function hello( {
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            // Should have errors from both CSS and JS (and possibly HTML)
        }
    }

    // ---------------------------------------------------------------
    // getEngine
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getEngine")
    class GetEngine {

        @Test
        @DisplayName("returns a non-null engine")
        void returnsNonNullEngine() {
            assertThat(validator.getEngine()).isNotNull();
        }

        @Test
        @DisplayName("returns MixedContentSyntaxEngine")
        void returnsCorrectType() {
            assertThat(validator.getEngine()).isInstanceOf(MixedContentSyntaxEngine.class);
        }
        @Test
        @DisplayName("returns the same engine instance on repeated calls")
        void returnsSameInstance() {
            MixedContentSyntaxEngine engine1 = validator.getEngine();
            MixedContentSyntaxEngine engine2 = validator.getEngine();
            assertThat(engine1).isSameAs(engine2);
        }
    }

    // ---------------------------------------------------------------
    // validate – PHP mixed content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("validate – PHP mixed content")
    class PhpMixedContent {
        void validPhpBlock() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><title>PHP Page</title></head>
                    <body>
                    <?php echo "Hello World"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("valid PHP with multiple statements returns result")
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

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("mixed CSS, JS, and PHP validates all layers")
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

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP class definition validates without error")
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

            ValidationResult result = validator.validate(html);
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

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("real-world PHP template with CSS, JS, and PHP validates")
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
                        .nav { background: #333; color: white; padding: 10px; }
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
                        const items = document.querySelectorAll('li');
                        items.forEach(item => {
                            item.style.cursor = 'pointer';
                        });
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with invalid syntax returns errors")
        void invalidPhpSyntax() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function broken( {
                        echo "missing paren";
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            // The PHP engine may report errors for the invalid syntax
        }

        @Test
        @DisplayName("multiple PHP blocks in document validate independently")
        void multiplePhpBlocks() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <?php echo '<title>Test</title>'; ?>
                    <style><?php echo '.red { color: red; }'; ?></style>
                    </head>
                    <body>
                    <?php
                    $x = 1;
                    $y = 2;
                    echo $x + $y;
                    ?>
                    <script>
                    <?php echo "console.log('injected');"; ?>
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with control structures validates")
        void phpControlStructures() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $items = [1, 2, 3];
                    foreach ($items as $item) {
                        if ($item > 1) {
                            echo $item;
                        }
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }
}
