package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts embedded {@code <style>} and {@code <script>} content blocks from
 * an HTML document, preserving the original line numbers for error remapping.
 *
 * <p>This extractor uses a regex-based approach to locate {@code <style>} and
 * {@code <script>} opening/closing tags and captures the content between them.
 * It is deliberately lenient with tag attributes (e.g. {@code type="text/css"},
 * {@code lang="javascript"}, {@code defer}, etc.) and focuses on extracting
 * the raw embedded content with accurate position tracking.
 *
 * <h2>Line number mapping</h2>
 * <p>Each {@link ExtractedBlock} records the 1-based line number where the
 * opening tag appears and the 1-based line number where the actual content
 * starts. This allows validation engines to map error line numbers from the
 * extracted content back to the original HTML document using
 * {@link ExtractedBlock#mapToOriginalLine(int)}.
 *
 * <h2>Edge cases handled</h2>
 * <ul>
 *   <li>Multi-line opening tags (e.g. {@code <script} followed by attributes
 *       on the next line and then {@code >});</li>
 *   <li>Empty blocks (e.g. {@code <style></style>});</li>
 *   <li>Content that itself contains {@code </} sequences (as long as they
 *       don't match the closing tag);</li>
 *   <li>HTML comments within {@code <script>} blocks (legacy practice);</li>
 *   <li>Case-insensitive tag matching (e.g. {@code <STYLE>} or
 *       {@code <Script>});</li>
 *   <li>Tags with various attributes like {@code type}, {@code src},
 *       {@code charset}, {@code nonce}, {@code crossorigin}, etc.</li>
 * </ul>
 *
 * <p><b>Thread-safety.</b> This class is stateless and safe for concurrent use.
 */
public final class HtmlContentExtractor {

    private static final HtmlContentExtractor INSTANCE = new HtmlContentExtractor();

    /**
     * Matches an opening {@code <style ...>} tag. Group 1 captures the full
     * opening tag (from {@code <} to {@code >}).  The match is
     * case-insensitive.
     */
    private static final Pattern STYLE_OPEN_PATTERN = Pattern.compile(
            "<style(?:\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Matches an opening {@code <script ...>} tag.
     */
    private static final Pattern SCRIPT_OPEN_PATTERN = Pattern.compile(
            "<script(?:\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Matches a closing {@code </style>} tag.
     */
    private static final Pattern STYLE_CLOSE_PATTERN = Pattern.compile(
            "</style\\s*>",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Matches a closing {@code </script>} tag.
     */
    private static final Pattern SCRIPT_CLOSE_PATTERN = Pattern.compile(
            "</script\\s*>",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Combined pattern to find opening tags for both style and script
     * elements.  Group 1 = tag name ("style" or "script").
     */
    private static final Pattern BLOCK_OPEN_PATTERN = Pattern.compile(
            "<(style|script)(?:\\s[^>]*)?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private HtmlContentExtractor() {
    }

    /**
     * Returns the singleton extractor instance.
     *
     * @return the singleton instance.
     */
    public static HtmlContentExtractor getInstance() {
        return INSTANCE;
    }

    /**
     * Extracts all embedded {@code <style>} and {@code <script>} blocks from
     * the given HTML source.
     *
     * <p>The returned list is ordered by the position of each block in the
     * original document (top to bottom).  Only blocks with non-empty content
     * are included.  If no embedded blocks are found, an empty list is returned.
     *
     * @param htmlSource the full HTML source code.
     * @return an immutable, ordered list of extracted content blocks.
     * @throws NullPointerException if {@code htmlSource} is {@code null}.
     */
    public List<ExtractedBlock> extract(String htmlSource) {
        if (htmlSource == null) {
            throw new NullPointerException("htmlSource must not be null");
        }
        if (htmlSource.isBlank()) {
            return Collections.emptyList();
        }

        List<ExtractedBlock> blocks = new ArrayList<>();

        // Extract style blocks
        extractBlocks(htmlSource, "style", Language.CSS, blocks);

        // Extract script blocks
        extractBlocks(htmlSource, "script", Language.JAVASCRIPT, blocks);

        // Sort by start line
        blocks.sort((a, b) -> Integer.compare(a.startLine(), b.startLine()));

        return Collections.unmodifiableList(blocks);
    }

    /**
     * Extracts all blocks of a specific tag type from the HTML source.
     *
     * @param htmlSource the full HTML source.
     * @param tagName    the tag name to search for ("style" or "script").
     * @param language   the language to associate with extracted content.
     * @param blocks     the accumulator list for extracted blocks.
     */
    private void extractBlocks(String htmlSource, String tagName,
                                Language language, List<ExtractedBlock> blocks) {
        Pattern openPattern = "style".equals(tagName) ? STYLE_OPEN_PATTERN : SCRIPT_OPEN_PATTERN;
        Pattern closePattern = "style".equals(tagName) ? STYLE_CLOSE_PATTERN : SCRIPT_CLOSE_PATTERN;

        Matcher openMatcher = openPattern.matcher(htmlSource);

        while (openMatcher.find()) {
            int openTagStart = openMatcher.start();
            int openTagEnd = openMatcher.end();

            // Find the matching closing tag after the opening tag
            Matcher closeMatcher = closePattern.matcher(htmlSource);
            boolean foundClose = false;

            while (closeMatcher.find(openTagEnd)) {
                int closeTagStart = closeMatcher.start();
                int closeTagEnd = closeMatcher.end();

                // Extract content between opening and closing tags
                String content = htmlSource.substring(openTagEnd, closeTagStart);

                // Calculate line numbers
                int startLine = lineNumberAt(htmlSource, openTagStart);
                // contentStartLine is the line where actual content begins.
                // If the character right after the opening tag is a newline,
                // the content starts on the next line.
                int contentStartLine = lineNumberAt(htmlSource, openTagEnd);
                if (openTagEnd < htmlSource.length() && htmlSource.charAt(openTagEnd) == '\n') {
                    contentStartLine++;
                }
                int contentStartColumn = columnNumberAt(htmlSource, openTagEnd);
                int contentEndLine = lineNumberAt(htmlSource, closeTagStart);

                // Only include non-empty blocks (skip blocks with only whitespace
                // or legacy HTML comment wrappers)
                String strippedContent = stripLegacyHtmlCommentWrapping(content);
                if (!strippedContent.isBlank()) {
                    blocks.add(new ExtractedBlock(
                            language,
                            strippedContent,
                            startLine,
                            contentStartLine,
                            contentStartColumn,
                            contentEndLine
                    ));
                }

                foundClose = true;
                break;
            }

            // If no closing tag found, skip this opening tag — it may be
            // malformed HTML that will be caught by the HTML validator.
        }
    }

    /**
     * Strips legacy HTML comment wrappers used inside {@code <script>} blocks.
     *
     * <p>In old HTML, it was common to wrap script content in HTML comments
     * to prevent it from being rendered in very old browsers:
     * <pre>{@code
     * <script>
     * <!--
     *   var x = 1;
     * //-->
     * </script>
     * }</pre>
     *
     * <p>This method removes the leading {@code <!--} and trailing
     * {@code //-->} or {@code -->} if present.
     *
     * @param content the raw content extracted from a block.
     * @return the content with legacy comment wrappers stripped.
     */
    static String stripLegacyHtmlCommentWrapping(String content) {
        if (content == null) {
            return "";
        }

        String trimmed = content.strip();

        // Check for leading <!-- (possibly preceded by whitespace/newlines)
        if (trimmed.startsWith("<!--")) {
            trimmed = trimmed.substring(4);
            // Remove trailing //--> or -->
            if (trimmed.endsWith("//-->")) {
                trimmed = trimmed.substring(0, trimmed.length() - 5);
            } else if (trimmed.endsWith("-->")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }

        return trimmed;
    }

    /**
     * Returns the 1-based line number at the given character offset in the
     * text.
     *
     * @param text   the source text.
     * @param offset the character offset (0-based).
     * @return the 1-based line number.
     */
    static int lineNumberAt(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Returns the 1-based column number at the given character offset in the
     * text.
     *
     * @param text   the source text.
     * @param offset the character offset (0-based).
     * @return the 1-based column number.
     */
    static int columnNumberAt(String text, int offset) {
        int col = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                col = 1;
            } else {
                col++;
            }
        }
        return col;
    }
}
