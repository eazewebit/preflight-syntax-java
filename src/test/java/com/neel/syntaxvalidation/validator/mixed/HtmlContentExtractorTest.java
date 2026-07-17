package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HtmlContentExtractor}.
 */
@DisplayName("HtmlContentExtractor")
class HtmlContentExtractorTest {

    private HtmlContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = HtmlContentExtractor.getInstance();
    }

    // ---------------------------------------------------------------
    // Singleton access
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("getInstance")
    class GetInstance {

        @Test
        @DisplayName("returns the same instance on repeated calls")
        void returnsSameInstance() {
            HtmlContentExtractor a = HtmlContentExtractor.getInstance();
            HtmlContentExtractor b = HtmlContentExtractor.getInstance();
            assertThat(a).isSameAs(b);
        }
    }

    // ---------------------------------------------------------------
    // extract – null and empty input
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – null and empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("throws NullPointerException for null input")
        void nullInput() {
            assertThatThrownBy(() -> extractor.extract(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("returns empty list for empty string")
        void emptyString() {
            List<ExtractedBlock> blocks = extractor.extract("");
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("returns empty list for whitespace-only string")
        void whitespaceOnly() {
            List<ExtractedBlock> blocks = extractor.extract("   \n  \t  ");
            assertThat(blocks).isEmpty();
        }
    }

    // ---------------------------------------------------------------
    // extract – style blocks
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – style blocks")
    class StyleBlocks {

        @Test
        @DisplayName("extracts a single <style> block")
        void singleStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; }
                    </style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);

            ExtractedBlock block = blocks.get(0);
            assertThat(block.language()).isEqualTo(Language.CSS);
            assertThat(block.content()).contains("body { color: red; }");
        }

        @Test
        @DisplayName("extracts multiple <style> blocks")
        void multipleStyleBlocks() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; }
                    </style>
                    <style>
                    div { margin: 0; }
                    </style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(2);
            assertThat(blocks).allSatisfy(block ->
                    assertThat(block.language()).isEqualTo(Language.CSS));
        }

        @Test
        @DisplayName("handles style block with attributes")
        void styleWithAttributes() {
            String html = """
                    <html>
                    <head>
                    <style type="text/css" media="screen">
                    body { color: red; }
                    </style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("body { color: red; }");
        }

        @Test
        @DisplayName("handles STYLE in uppercase")
        void uppercaseStyle() {
            String html = """
                    <html>
                    <head>
                    <STYLE>
                    body { color: red; }
                    </STYLE>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.CSS);
        }

        @Test
        @DisplayName("handles mixed case style tags")
        void mixedCaseStyle() {
            String html = """
                    <html>
                    <head>
                    <Style>
                    body { color: red; }
                    </Style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
        }

        @Test
        @DisplayName("handles empty style block")
        void emptyStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style></style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("handles whitespace-only style block")
        void whitespaceOnlyStyleBlock() {
            String html = """
                    <html>
                    <head>
                    <style>
                      
                    </style>
                    </head>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("handles multiline style content")
        void multilineStyleContent() {
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
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("body {");
            assertThat(blocks.get(0).content()).contains("color: red;");
            assertThat(blocks.get(0).content()).contains("}");
        }
    }

    // ---------------------------------------------------------------
    // extract – script blocks
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – script blocks")
    class ScriptBlocks {

        @Test
        @DisplayName("extracts a single <script> block")
        void singleScriptBlock() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);

            ExtractedBlock block = blocks.get(0);
            assertThat(block.language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(block.content()).contains("var x = 1;");
        }

        @Test
        @DisplayName("extracts multiple <script> blocks")
        void multipleScriptBlocks() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    <script>
                    var y = 2;
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(2);
            assertThat(blocks).allSatisfy(block ->
                    assertThat(block.language()).isEqualTo(Language.JAVASCRIPT));
        }

        @Test
        @DisplayName("handles script block with attributes")
        void scriptWithAttributes() {
            String html = """
                    <html>
                    <body>
                    <script type="text/javascript" charset="UTF-8">
                    var x = 1;
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("var x = 1;");
        }

        @Test
        @DisplayName("handles SCRIPT in uppercase")
        void uppercaseScript() {
            String html = """
                    <html>
                    <body>
                    <SCRIPT>
                    var x = 1;
                    </SCRIPT>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("handles empty script block")
        void emptyScriptBlock() {
            String html = """
                    <html>
                    <body>
                    <script></script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("handles script block with legacy HTML comment wrapping")
        void scriptWithLegacyComment() {
            String html = """
                    <html>
                    <body>
                    <script>
                    <!--
                    var x = 1;
                    //-->
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("var x = 1;");
            assertThat(blocks.get(0).content()).doesNotContain("<!--");
        }

        @Test
        @DisplayName("handles script block with legacy comment (no //)")
        void scriptWithLegacyCommentNoSlashes() {
            String html = """
                    <html>
                    <body>
                    <script>
                    <!--
                    var x = 1;
                    -->
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("var x = 1;");
            assertThat(blocks.get(0).content()).doesNotContain("<!--");
        }

        @Test
        @DisplayName("handles multiline script content")
        void multilineScriptContent() {
            String html = """
                    <html>
                    <body>
                    <script>
                    function hello() {
                      console.log("Hello, world!");
                      return true;
                    }
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("function hello()");
            assertThat(blocks.get(0).content()).contains("return true;");
        }
    }

    // ---------------------------------------------------------------
    // extract – mixed content
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – mixed style and script")
    class MixedContent {

        @Test
        @DisplayName("extracts both style and script blocks in document order")
        void extractsBothInOrder() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; }
                    </style>
                    </head>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(2);
            assertThat(blocks.get(0).language()).isEqualTo(Language.CSS);
            assertThat(blocks.get(1).language()).isEqualTo(Language.JAVASCRIPT);
        }

        @Test
        @DisplayName("handles script before style")
        void scriptBeforeStyle() {
            String html = """
                    <html>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    <style>
                    body { color: red; }
                    </style>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(2);
            assertThat(blocks.get(0).language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(blocks.get(1).language()).isEqualTo(Language.CSS);
        }

        @Test
        @DisplayName("handles multiple of each type")
        void multipleOfEachType() {
            String html = """
                    <html>
                    <head>
                    <style>
                    body { color: red; }
                    </style>
                    <style>
                    div { margin: 0; }
                    </style>
                    </head>
                    <body>
                    <script>
                    var x = 1;
                    </script>
                    <script>
                    var y = 2;
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(4);
        }
    }

    // ---------------------------------------------------------------
    // extract – line number tracking
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – line number tracking")
    class LineNumberTracking {

        @Test
        @DisplayName("reports correct startLine for style block")
        void correctStartLineForStyle() {
            String html = "<html>\n<head>\n<style>\nbody { color: red; }\n</style>\n</head>\n</html>";
            //                           line 3        line 4

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).startLine()).isEqualTo(3);
        }

        @Test
        @DisplayName("reports correct contentStartLine for style block")
        void correctContentStartLineForStyle() {
            String html = "<html>\n<head>\n<style>\nbody { color: red; }\n</style>\n</head>\n</html>";
            //                           line 3        line 4

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).contentStartLine()).isEqualTo(4);
        }

        @Test
        @DisplayName("reports correct contentEndLine for style block")
        void correctContentEndLineForStyle() {
            String html = "<html>\n<head>\n<style>\nbody {\n  color: red;\n}\n</style>\n</head>\n</html>";
            //                           line 3        line 4       line 5    line 6

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).contentEndLine()).isEqualTo(7);
        }

        @Test
        @DisplayName("reports correct startLine for script block")
        void correctStartLineForScript() {
            String html = "<html>\n<body>\n<script>\nvar x = 1;\n</script>\n</body>\n</html>";
            //                          line 3         line 4

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).startLine()).isEqualTo(3);
        }

        @Test
        @DisplayName("mapToOriginalLine works correctly for extracted blocks")
        void mapToOriginalLineWorks() {
            String html = "<html>\n<head>\n<style>\nbody {\n  color: red;\n}\n</style>\n</head>\n</html>";
            //                           line 3        line 4       line 5    line 6  line 7

            List<ExtractedBlock> blocks = extractor.extract(html);
            ExtractedBlock block = blocks.get(0);

            // Content starts at line 4 of the HTML document
            assertThat(block.mapToOriginalLine(1)).isEqualTo(4);
            assertThat(block.mapToOriginalLine(2)).isEqualTo(5);
            assertThat(block.mapToOriginalLine(3)).isEqualTo(6);
        }

        @Test
        @DisplayName("handles single-line opening tag followed by content")
        void singleLineOpenTag() {
            String html = "<html><head><style>body { color: red; }</style></head></html>";

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).startLine()).isEqualTo(1);
            assertThat(blocks.get(0).contentStartLine()).isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------
    // stripLegacyHtmlCommentWrapping
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("stripLegacyHtmlCommentWrapping")
    class StripLegacyHtmlCommentWrapping {

        @Test
        @DisplayName("strips <!-- and //-->")
        void stripsCommentWrappingWithSlashes() {
            String input = "<!--\nvar x = 1;\n// -->";
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping(input);
            assertThat(result).contains("var x = 1;");
            assertThat(result).doesNotContain("<!--");
            assertThat(result).doesNotContain("-->");
        }

        @Test
        @DisplayName("strips <!-- and -->")
        void stripsCommentWrappingWithoutSlashes() {
            String input = "<!--\nvar x = 1;\n-->";
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping(input);
            assertThat(result).contains("var x = 1;");
            assertThat(result).doesNotContain("<!--");
            assertThat(result).doesNotContain("-->");
        }

        @Test
        @DisplayName("leaves content without comment wrapping unchanged")
        void leavesNonWrappedContent() {
            String input = "var x = 1;";
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping(input);
            assertThat(result).isEqualTo("var x = 1;");
        }

        @Test
        @DisplayName("handles null input")
        void handlesNull() {
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping(null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles empty input")
        void handlesEmpty() {
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping("");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("handles <!-- only (no closing)")
        void handlesOnlyOpening() {
            String input = "<!--\nvar x = 1;";
            String result = HtmlContentExtractor.stripLegacyHtmlCommentWrapping(input);
            assertThat(result).contains("var x = 1;");
            assertThat(result).doesNotContain("<!--");
        }
    }

    // ---------------------------------------------------------------
    // lineNumberAt and columnNumberAt
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("lineNumberAt / columnNumberAt")
    class LineAndColumn {

        @Test
        @DisplayName("lineNumberAt returns 1 for offset 0")
        void lineAtStart() {
            assertThat(HtmlContentExtractor.lineNumberAt("hello", 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("lineNumberAt returns 2 after newline")
        void lineAfterNewline() {
            assertThat(HtmlContentExtractor.lineNumberAt("hello\nworld", 6)).isEqualTo(2);
        }

        @Test
        @DisplayName("lineNumberAt returns 1 before newline")
        void lineBeforeNewline() {
            assertThat(HtmlContentExtractor.lineNumberAt("hello\nworld", 4)).isEqualTo(1);
        }

        @Test
        @DisplayName("lineNumberAt handles offset at end of text")
        void lineAtEnd() {
            assertThat(HtmlContentExtractor.lineNumberAt("ab\ncd", 5)).isEqualTo(2);
        }

        @Test
        @DisplayName("lineNumberAt handles offset beyond text length")
        void lineBeyondText() {
            assertThat(HtmlContentExtractor.lineNumberAt("ab", 100)).isEqualTo(1);
        }

        @Test
        @DisplayName("lineNumberAt returns 1 for empty text")
        void lineEmptyText() {
            assertThat(HtmlContentExtractor.lineNumberAt("", 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("columnNumberAt returns 1 for offset 0")
        void columnAtStart() {
            assertThat(HtmlContentExtractor.columnNumberAt("hello", 0)).isEqualTo(1);
        }

        @Test
        @DisplayName("columnNumberAt returns column after characters")
        void columnAfterChars() {
            assertThat(HtmlContentExtractor.columnNumberAt("hello", 5)).isEqualTo(6);
        }

        @Test
        @DisplayName("columnNumberAt resets after newline")
        void columnResetsAfterNewline() {
            assertThat(HtmlContentExtractor.columnNumberAt("ab\ncd", 4)).isEqualTo(2);
        }

        @Test
        @DisplayName("columnNumberAt returns 1 at start of new line")
        void columnAtNewline() {
            assertThat(HtmlContentExtractor.columnNumberAt("ab\ncd", 3)).isEqualTo(1);
        }

        @Test
        @DisplayName("columnNumberAt handles offset beyond text length")
        void columnBeyondText() {
            assertThat(HtmlContentExtractor.columnNumberAt("ab", 100)).isEqualTo(3);
        }
    }

    // ---------------------------------------------------------------
    // Real-world HTML documents
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – real-world documents")
    class RealWorldDocuments {

        @Test
        @DisplayName("handles a complete HTML5 document")
        void completeHtml5Document() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Test Page</title>
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            body { font-family: Arial, sans-serif; }
                            .container { max-width: 1200px; margin: 0 auto; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>Hello World</h1>
                        </div>
                        <script>
                            document.addEventListener('DOMContentLoaded', function() {
                                console.log('Page loaded');
                            });
                        </script>
                        <script type="module">
                            import { greet } from './utils.js';
                            greet('World');
                        </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(3);

            // First block: CSS
            assertThat(blocks.get(0).language()).isEqualTo(Language.CSS);
            assertThat(blocks.get(0).content()).contains("margin: 0");

            // Second block: JS (DOMContentLoaded)
            assertThat(blocks.get(1).language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(blocks.get(1).content()).contains("addEventListener");

            // Third block: JS (module)
            assertThat(blocks.get(2).language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(blocks.get(2).content()).contains("import");
        }

        @Test
        @DisplayName("handles document with no embedded blocks")
        void noEmbeddedBlocks() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>No Embedded</title></head>
                    <body><p>Hello World</p></body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("handles script with src attribute (inline content)")
        void scriptWithSrc() {
            String html = """
                    <html>
                    <body>
                    <script src="app.js"></script>
                    <script>
                    var inline = true;
                    </script>
                    </body>
                    </html>
                    """;

            // The src attribute script has empty content, so only the second one
            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).content()).contains("var inline = true");
        }

        @Test
        @DisplayName("handles style and script in body section")
        void styleAndScriptInBody() {
            String html = """
                    <html>
                    <body>
                    <style>
                    .highlight { background: yellow; }
                    </style>
                    <script>
                    document.querySelector('.highlight').style.display = 'block';
                    </script>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(2);
            assertThat(blocks.get(0).language()).isEqualTo(Language.CSS);
        }
    }

    // ---------------------------------------------------------------
    // extract – PHP blocks
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("extract – PHP blocks")
    class PhpBlocks {

        @Test
        @DisplayName("extracts a basic <?php … ?> block")
        void basicPhpBlock() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <?php echo "Hello World"; ?>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).isEqualTo("echo \"Hello World\";");
        }

        @Test
        @DisplayName("extracts a <?= … ?> short-echo block")
        void shortEchoBlock() {
            String html = """
                    <html><body>
                    <?= $variable ?>
                    </body></html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).isEqualTo(" $variable");
        }

        @Test
        @DisplayName("extracts multi-line PHP block")
        void multiLinePhpBlock() {
            String html = """
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

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).contains("$name = \"World\"");
            assertThat(blocks.get(0).content()).contains("echo $greeting");
            assertThat(blocks.get(0).startLine()).isEqualTo(3);
        }

        @Test
        @DisplayName("extracts multiple PHP blocks")
        void multiplePhpBlocks() {
            String html = """
                    <html>
                    <body>
                    <h1><?php echo $title; ?></h1>
                    <p><?= $content ?></p>
                    <?php echo $footer; ?>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(3);
            assertThat(blocks).allSatisfy(block ->
                    assertThat(block.language()).isEqualTo(Language.PHP));
        }

        @Test
        @DisplayName("extracts PHP block with HTML around it")
        void phpBlockWithHtml() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>PHP Page</title></head>
                    <body>
                    <?php
                    if ($showHeader) {
                        echo "<h1>Welcome</h1>";
                    }
                    ?>
                    <p>Static content</p>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).contains("if ($showHeader)");
            assertThat(blocks.get(0).content()).contains("echo \"<h1>Welcome</h1>\"");
        }

        @Test
        @DisplayName("does not extract <?xml … ?> processing instructions")
        void xmlProcessingInstructionIgnored() {
            String html = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <html>
                    <body>
                    <?php echo "test"; ?>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).isEqualTo("echo \"test\";");
        }

        @Test
        @DisplayName("extracts mixed HTML with style, script, and PHP blocks")
        void mixedHtmlCssJsPhp() {
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
            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(5);

            assertThat(blocks.get(0).language()).isEqualTo(Language.CSS);
            assertThat(blocks.get(1).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(2).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(3).language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(blocks.get(4).language()).isEqualTo(Language.PHP);
        }

        @Test
        @DisplayName("handles PHP block with curly braces and complex syntax")
        void phpBlockWithComplexSyntax() {
            String html = """
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
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            assertThat(blocks).hasSize(1);
            assertThat(blocks.get(0).language()).isEqualTo(Language.PHP);
            assertThat(blocks.get(0).content()).contains("class User");
            assertThat(blocks.get(0).content()).contains("public function greet");
        }

        @Test
        @DisplayName("handles empty PHP block gracefully")
        void emptyPhpBlock() {
            String html = """
                    <html>
                    <body>
                    <?php ?>
                    <p>Content</p>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            // Empty PHP block should be filtered out
            assertThat(blocks).isEmpty();
        }

        @Test
        @DisplayName("handles PHP block with only whitespace")
        void whitespaceOnlyPhpBlock() {
            String html = """
                    <html>
                    <body>
                    <?php
                       
                    ?>
                    <p>Content</p>
                    </body>
                    </html>
                    """;

            List<ExtractedBlock> blocks = extractor.extract(html);
            // Whitespace-only PHP block should be filtered out
            assertThat(blocks).isEmpty();
        }
    }
}
