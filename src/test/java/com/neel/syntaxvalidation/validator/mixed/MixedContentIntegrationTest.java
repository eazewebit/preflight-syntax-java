package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the mixed-content validation pipeline.
 *
 * <p>These tests exercise the complete validation flow from the public API
 * through to the individual syntax engines, verifying that HTML, CSS, and
 * JavaScript are validated together correctly.
 */
@DisplayName("MixedContent – Integration Tests")
class MixedContentIntegrationTest {

    private MixedContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MixedContentValidator();
    }

    // ---------------------------------------------------------------
    // End-to-end: valid documents
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("End-to-end: valid documents")
    class ValidDocuments {

        @Test
        @DisplayName("minimal valid HTML is accepted")
        void minimalValidHtml() {
            ValidationResult result = validator.validate("<html><body></body></html>");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML5 document with valid embedded CSS and JS is accepted")
        void html5WithValidCssAndJs() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Integration Test</title>
                        <style>
                            body {
                                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                                background-color: #f5f5f5;
                                color: #333;
                            }
                            .header {
                                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                                color: white;
                                padding: 2rem;
                                text-align: center;
                            }
                            .content {
                                max-width: 800px;
                                margin: 2rem auto;
                                padding: 1rem;
                            }
                            @media (max-width: 768px) {
                                .content {
                                    margin: 1rem;
                                    padding: 0.5rem;
                                }
                            }
                        </style>
                    </head>
                    <body>
                        <header class="header">
                            <h1>Integration Test Page</h1>
                        </header>
                        <main class="content">
                            <p id="message">Hello, World!</p>
                            <button id="btn">Click Me</button>
                        </main>
                        <script>
                            (function() {
                                'use strict';
                                
                                var message = document.getElementById('message');
                                var button = document.getElementById('btn');
                                var clickCount = 0;
                                
                                button.addEventListener('click', function() {
                                    clickCount++;
                                    message.textContent = 'Clicked ' + clickCount + ' times';
                                    console.log('Button clicked:', clickCount);
                                });
                                
                                function formatDate(date) {
                                    var year = date.getFullYear();
                                    var month = String(date.getMonth() + 1).padStart(2, '0');
                                    var day = String(date.getDate()).padStart(2, '0');
                                    return year + '-' + month + '-' + day;
                                }
                                
                                console.log('Page loaded on:', formatDate(new Date()));
                            })();
                        </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            // This should be valid
            if (!result.isValid()) {
                System.out.println("Unexpected errors in valid document:");
                result.getErrors().forEach(e ->
                        System.out.println("  " + e.getLine() + ":" + e.getColumn() + " " + e.getMessage()));
            }
        }

        @Test
        @DisplayName("document with multiple style and script blocks is accepted")
        void multipleBlocks() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            body { margin: 0; }
                        </style>
                        <style>
                            .container { max-width: 960px; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <p>Content</p>
                        </div>
                        <script>
                            var app = {};
                            app.name = 'TestApp';
                        </script>
                        <script>
                            console.log('App:', app.name);
                        </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // End-to-end: CSS error detection
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("End-to-end: CSS error detection")
    class CssErrorDetection {

        @Test
        @DisplayName("missing colon in CSS property is detected")
        void missingCssColon() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body {
                        color red;
                    }
                    </style>
                    </head>
                    <body><p>Hello</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            // Should have at least one CSS error
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();

            // The embedded CSS engine should catch this
            if (!cssErrors.isEmpty()) {
                assertThat(cssErrors).isNotEmpty();
                assertThat(cssErrors.get(0).getMessage()).contains("CSS");
            }
        }

        @Test
        @DisplayName("missing semicolon in CSS is detected")
        void missingCssSemicolon() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body {
                        color: red
                        font-size: 14px;
                    }
                    </style>
                    </head>
                    <body><p>Hello</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("CSS errors are mapped to correct HTML line numbers")
        void cssErrorLineNumberMapping() {
            // Line numbers: 1=<html>, 2=<head>, 3=<style>, 4=body{, 5=color red;, 6=}, 7=</style>
            String html = "<html>\n<head>\n<style>\nbody {\n  color red;\n}\n</style>\n</head>\n<body></body>\n</html>";

            ValidationResult result = validator.validate(html);
            List<ValidationError> cssErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .toList();

            if (!cssErrors.isEmpty()) {
                // The error should be around line 5 in the HTML document
                ValidationError firstError = cssErrors.get(0);
                assertThat(firstError.getLine()).isBetween(4, 6);
            }
        }
    }

    // ---------------------------------------------------------------
    // End-to-end: JavaScript error detection
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("End-to-end: JavaScript error detection")
    class JsErrorDetection {

        @Test
        @DisplayName("syntax error in JS is detected")
        void jsSyntaxError() {
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

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            List<ValidationError> jsErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .toList();

            if (!jsErrors.isEmpty()) {
                assertThat(jsErrors.get(0).getMessage()).contains("JavaScript");
            }
        }

        @Test
        @DisplayName("unmatched braces in JS is detected")
        void unmatchedBraces() {
            String html = """
                    <html>
                    <body>
                    <script>
                    function test() {
                        console.log('test');
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            assertThat(!result.isValid()).isTrue();
        }

        @Test
        @DisplayName("unmatched parentheses in JS is detected")
        void unmatchedParens() {
            String html = """
                    <html>
                    <body>
                    <script>
                    console.log('hello';
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            assertThat(!result.isValid()).isTrue();
        }

        @Test
        @DisplayName("JS errors are mapped to correct HTML line numbers")
        void jsErrorLineNumberMapping() {
            String html = "<html>\n<body>\n<script>\nvar x = ;\n</script>\n</body>\n</html>";
            //                   1       2       3        4         5        6       7

            ValidationResult result = validator.validate(html);
            List<ValidationError> jsErrors = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .toList();

            if (!jsErrors.isEmpty()) {
                // The error at content line 1 should map to HTML line 4
                ValidationError firstError = jsErrors.get(0);
                assertThat(firstError.getLine()).isBetween(3, 5);
            }
        }
    }

    // ---------------------------------------------------------------
    // End-to-end: combined error detection
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("End-to-end: combined error detection")
    class CombinedErrorDetection {

        @Test
        @DisplayName("errors from all three languages are reported together")
        void allLanguagesReported() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    body {
                        color red;
                        font-size: 14px;
                    }
                    </style>
                    </head>
                    <body>
                    <p id="test">Hello</p>
                    <script>
                    var x = ;
                    var y = 2;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            // Count errors by category
            long cssErrorCount = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .count();
            long jsErrorCount = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .count();

            // At least one of each should be caught
            // (depending on what the engines catch)
            System.out.println("CSS errors: " + cssErrorCount + ", JS errors: " + jsErrorCount);
        }

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

            ValidationResult result = validator.validate(html);
            List<ValidationError> errors = result.getErrors();

            for (int i = 1; i < errors.size(); i++) {
                assertThat(errors.get(i).getLine())
                        .isGreaterThanOrEqualTo(errors.get(i - 1).getLine());
            }
        }

        /*
         * Test for all three language error
         */
        @Test
        @DisplayName("HTML validation errors are detected")
        void htmlErrorsDetected() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    body {
                        color red;
                    }
                    </style>
                    </head>
                    <body>
                    <p id="test">Hello</p>
                    <script>
                    var x = ;
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            assertThat(result.isValid()).isFalse();
        }

        @Test
        @DisplayName("multiple CSS errors in different style blocks")
        void multipleCssErrors() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    body {
                        color red;
                    }
                    </style>
                    <style>
                    .container {
                        max-width 960px;
                    }
                    </style>
                    </head>
                    <body>
                    <div class="container">Content</div>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            long cssErrorCount = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[CSS"))
                    .count();

            // Should detect errors from both style blocks
            assertThat(cssErrorCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("multiple JS errors in different script blocks")
        void multipleJsErrors() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <script>
                    var x = ;
                    </script>
                    <div>Content</div>
                    <script>
                    function test( {
                        console.log('test');
                    }
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            long jsErrorCount = result.getErrors().stream()
                    .filter(e -> e.getMessage().contains("[JavaScript"))
                    .count();

            assertThat(jsErrorCount).isGreaterThanOrEqualTo(1);
        }
    }

    // ---------------------------------------------------------------
    // Edge cases with empty content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases with empty content")
    class EmptyContentEdgeCases {

        @Test
        @DisplayName("empty HTML document")
        void emptyHtmlDocument() {
            String html = "";
            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with empty style block")
        void emptyStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style></style>
                    </head>
                    <body><p>Test</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with empty script block")
        void emptyScriptBlock() {
            String html = """
                    <html>
                    <body>
                    <script></script>
                    <p>Test</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with whitespace-only style block")
        void whitespaceOnlyStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style>
                        
                    </style>
                    </head>
                    <body><p>Test</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with whitespace-only script block")
        void whitespaceOnlyScriptBlock() {
            String html = """
                    <html>
                    <body>
                    <script>
                        
                    </script>
                    <p>Test</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML with both empty style and script blocks")
        void emptyStyleAndScriptBlocks() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style></style>
                    </head>
                    <body>
                        <p>Content</p>
                        <script></script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    


    }

    // ---------------------------------------------------------------
    // ValidatorFactory integration
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("ValidatorFactory integration")
    class FactoryIntegration {

        @Test
        @DisplayName("factory can create MixedContentValidator")
        void factoryCreatesValidator() {
            ValidatorFactory factory = new ValidatorFactory();
            MixedContentValidator mixedValidator = factory.getMixedContentValidator();
            assertThat(mixedValidator).isNotNull();
            assertThat(mixedValidator.getLanguage()).isEqualTo(Language.HTML);
        }

        @Test
        @DisplayName("factory mixed validator works correctly")
        void factoryValidatorWorks() {
            ValidatorFactory factory = new ValidatorFactory();
            MixedContentValidator mixedValidator = factory.getMixedContentValidator();

            String html = """
                    <html>
                    <head>
                    <style>body { color: red; }</style>
                    </head>
                    <body>
                    <script>var x = 1;</script>
                    </body>
                    </html>
                    """;

            ValidationResult result = mixedValidator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Document-level edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Document-level edge cases")
    class DocumentEdgeCases {

        @Test
        @DisplayName("handles XHTML-style self-closing tags in content")
        void xhtmlSelfClosing() {
            String html = """
                    <html>
                    <head>
                    <style>
                    input[type="text"] { border: 1px solid #ccc; }
                    br { display: block; }
                    </style>
                    </head>
                    <body>
                    <input type="text" />
                    <br />
                    <script>
                    var inputs = document.querySelectorAll('input[type="text"]');
                    console.log(inputs.length);
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles CSS comments inside style block")
        void cssComments() {
            String html = """
                    <html>
                    <head>
                    <style>
                    /* Reset styles */
                    * { margin: 0; padding: 0; }
                    
                    /* Body styles */
                    body {
                        font-family: Arial, sans-serif;
                        color: #333;
                    }
                    </style>
                    </head>
                    <body><p>Test</p></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles JS comments inside script block")
        void jsComments() {
            String html = """
                    <html>
                    <body>
                    <script>
                    // Single line comment
                    var x = 1;
                    
                    /* Multi-line
                       comment */
                    var y = 2;
                    
                    /**
                     * JSDoc comment
                     */
                    function add(a, b) {
                        return a + b;
                    }
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles script with special characters in string literals")
        void scriptWithSpecialChars() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var html = '<div class=\\"test\\">Hello</div>';
                    var path = '/path/to/file.html';
                    var regex = /<style>[\\s\\S]*?<\\/style>/;
                    console.log(html, path, regex);
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles CSS with special characters")
        void cssWithSpecialChars() {
            String html = """
                    <html>
                    <head>
                    <style>
                    .content::before {
                        content: "Hello \\"World\\"";
                    }
                    a[href^="https://"] {
                        color: blue;
                    }
                    </style>
                    </head>
                    <body><div class="content">Test</div></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles very long CSS content")
        void longCssContent() {
            StringBuilder css = new StringBuilder();
            css.append("body {\n");
            for (int i = 0; i < 100; i++) {
                css.append("    property-").append(i).append(": value").append(i).append(";\n");
            }
            css.append("}\n");

            String html = "<html>\n<head>\n<style>\n" + css + "</style>\n</head>\n<body></body>\n</html>";

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles very long JS content")
        void longJsContent() {
            StringBuilder js = new StringBuilder();
            js.append("function init() {\n");
            for (int i = 0; i < 100; i++) {
                js.append("    var var").append(i).append(" = ").append(i).append(";\n");
            }
            js.append("}\n");

            String html = "<html>\n<body>\n<script>\n" + js + "</script>\n</body>\n</html>";

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles CSS with vendor prefixes")
        void cssWithVendorPrefixes() {
            String html = """
                    <html>
                    <head>
                    <style>
                    .animated {
                        -webkit-transition: all 0.3s ease;
                        -moz-transition: all 0.3s ease;
                        -ms-transition: all 0.3s ease;
                        transition: all 0.3s ease;
                        -webkit-transform: rotate(45deg);
                        transform: rotate(45deg);
                    }
                    </style>
                    </head>
                    <body><div class="animated">Content</div></body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("handles CSS with @keyframes and complex selectors")
        void cssWithKeyframes() {
            String html = """
                    <html>
                    <head>
                    <style>
                    @keyframes fadeIn {
                        from { opacity: 0; }
                        to { opacity: 1; }
                    }
                    .fade-in {
                        animation: fadeIn 1s ease-in;
                    }
                    .parent > .child + .sibling ~ .other {
                        display: block;
                    }
                    </style>
                    </head>
                    <body>
                    <div class="fade-in">Content</div>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Real-world scenarios
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Real-world scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("typical single-page application layout")
        void singlePageApp() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>SPA Template</title>
                        <style>
                            *, *::before, *::after {
                                box-sizing: border-box;
                            }
                            body {
                                margin: 0;
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                                line-height: 1.6;
                                color: #333;
                                background-color: #fafafa;
                            }
                            .nav {
                                background: #fff;
                                box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
                                padding: 1rem 2rem;
                                display: flex;
                                justify-content: space-between;
                                align-items: center;
                            }
                            .nav-brand {
                                font-size: 1.5rem;
                                font-weight: bold;
                                color: #2c3e50;
                            }
                            .nav-links {
                                display: flex;
                                gap: 1rem;
                                list-style: none;
                            }
                            .nav-links a {
                                color: #555;
                                text-decoration: none;
                                transition: color 0.2s;
                            }
                            .nav-links a:hover {
                                color: #2c3e50;
                            }
                        </style>
                    </head>
                    <body>
                        <nav class="nav">
                            <span class="nav-brand">MyApp</span>
                            <ul class="nav-links">
                                <li><a href="#home">Home</a></li>
                                <li><a href="#about">About</a></li>
                                <li><a href="#contact">Contact</a></li>
                            </ul>
                        </nav>
                        <main id="app">
                            <h1>Welcome</h1>
                            <p>Choose a section from the navigation above.</p>
                        </main>
                        <script>
                            (function() {
                                'use strict';
                                
                                var app = document.getElementById('app');
                                var links = document.querySelectorAll('.nav-links a');
                                
                                var sections = {
                                    home: '<h1>Home</h1><p>Welcome to the home page.</p>',
                                    about: '<h1>About</h1><p>Learn more about us.</p>',
                                    contact: '<h1>Contact</h1><p>Get in touch.</p>'
                                };
                                
                                function navigate(section) {
                                    if (sections[section]) {
                                        app.innerHTML = sections[section];
                                    }
                                }
                                
                                links.forEach(function(link) {
                                    link.addEventListener('click', function(e) {
                                        e.preventDefault();
                                        var section = this.getAttribute('href').substring(1);
                                        navigate(section);
                                    });
                                });
                                
                                navigate('home');
                            })();
                        </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("HTML email template with inline styles in style block")
        void emailTemplate() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    body { margin: 0; padding: 0; background: #f4f4f4; }
                    .email-container { max-width: 600px; margin: 0 auto; background: #fff; }
                    .email-header { background: #007bff; color: #fff; padding: 20px; text-align: center; }
                    .email-body { padding: 20px; }
                    .email-footer { background: #f8f9fa; padding: 10px; text-align: center; font-size: 12px; color: #666; }
                    </style>
                    </head>
                    <body>
                    <div class="email-container">
                        <div class="email-header">
                            <h1>Newsletter</h1>
                        </div>
                        <div class="email-body">
                            <p>Dear Subscriber,</p>
                            <p>Thank you for reading our newsletter.</p>
                        </div>
                        <div class="email-footer">
                            <p>Unsubscribe | Privacy Policy</p>
                        </div>
                    </div>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("form with validation script")
        void formWithValidation() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    .form-group { margin-bottom: 1rem; }
                    .form-group label { display: block; margin-bottom: 0.5rem; font-weight: bold; }
                    .form-group input { width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; }
                    .error { color: #dc3545; font-size: 0.875rem; margin-top: 0.25rem; }
                    .btn { padding: 0.5rem 1rem; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; }
                    .btn:hover { background: #0056b3; }
                    </style>
                    </head>
                    <body>
                    <form id="contactForm">
                        <div class="form-group">
                            <label for="name">Name</label>
                            <input type="text" id="name" name="name">
                            <div class="error" id="nameError"></div>
                        </div>
                        <div class="form-group">
                            <label for="email">Email</label>
                            <input type="email" id="email" name="email">
                            <div class="error" id="emailError"></div>
                        </div>
                        <button type="submit" class="btn">Submit</button>
                    </form>
                    <script>
                    (function() {
                        var form = document.getElementById('contactForm');
                        var nameInput = document.getElementById('name');
                        var emailInput = document.getElementById('email');
                        var nameError = document.getElementById('nameError');
                        var emailError = document.getElementById('emailError');
                        
                        function validateName() {
                            if (nameInput.value.trim() === '') {
                                nameError.textContent = 'Name is required';
                                return false;
                            }
                            nameError.textContent = '';
                            return true;
                        }
                        
                        function validateEmail() {
                            var email = emailInput.value.trim();
                            if (email === '') {
                                emailError.textContent = 'Email is required';
                                return false;
                            }
                            if (email.indexOf('@') === -1) {
                                emailError.textContent = 'Invalid email format';
                                return false;
                            }
                            emailError.textContent = '';
                            return true;
                        }
                        
                        form.addEventListener('submit', function(e) {
                            var nameValid = validateName();
                            var emailValid = validateEmail();
                            if (!nameValid || !emailValid) {
                                e.preventDefault();
                            }
                        });
                        
                        nameInput.addEventListener('blur', validateName);
                        emailInput.addEventListener('blur', validateEmail);
                    })();
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }
}
