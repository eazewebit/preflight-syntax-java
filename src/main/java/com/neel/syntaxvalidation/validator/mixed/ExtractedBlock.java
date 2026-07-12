package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;

import java.util.Objects;

/**
 * Immutable representation of a CSS or JavaScript content block extracted
 * from within an HTML document.
 *
 * <p>When an HTML file contains embedded {@code <style>} or {@code <script>}
 * blocks, each block is captured with:
 * <ul>
 *   <li>the {@link #language() language} (CSS or JavaScript);</li>
 *   <li>the raw {@link #content() content} of the block;</li>
 *   <li>the 1-based {@link #startLine() line number} in the original HTML
 *       document where the block begins (pointing at the opening tag);</li>
 *   <li>the 1-based {@link #contentStartLine() line number} where the actual
 *       embedded content starts (i.e. after the opening {@code <style>} or
 *       {@code <script>} tag);</li>
 *   <li>the 1-based {@link #contentStartColumn() column number} where the
 *       content starts on the first content line.</li>
 * </ul>
 *
 * <p>This information is used by the {@link MixedContentSyntaxEngine} to
 * remap validation error line numbers back to the correct position in the
 * original HTML document.
 *
 * <p><b>Thread-safety.</b> This class is immutable and inherently thread-safe.
 *
 * @param language         the language of the extracted content.
 * @param content          the raw text content of the block (excluding the
 *                         opening/closing HTML tags).
 * @param startLine        the 1-based line in the original HTML where the
 *                         opening tag appears.
 * @param contentStartLine the 1-based line in the original HTML where the
 *                         actual embedded content starts.
 * @param contentStartColumn the 1-based column on {@code contentStartLine}
 *                         where the content begins.
 * @param contentEndLine   the 1-based line in the original HTML where the
 *                         embedded content ends (before the closing tag).
 */
public record ExtractedBlock(
        Language language,
        String content,
        int startLine,
        int contentStartLine,
        int contentStartColumn,
        int contentEndLine
) {

    /**
     * Compact constructor with defensive validation.
     */
    public ExtractedBlock {
        Objects.requireNonNull(language, "language must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (startLine < 1) {
            throw new IllegalArgumentException("startLine must be >= 1, got " + startLine);
        }
        if (contentStartLine < 1) {
            throw new IllegalArgumentException("contentStartLine must be >= 1, got " + contentStartLine);
        }
        if (contentStartColumn < 1) {
            throw new IllegalArgumentException("contentStartColumn must be >= 1, got " + contentStartColumn);
        }
        if (contentEndLine < contentStartLine) {
            throw new IllegalArgumentException(
                    "contentEndLine (" + contentEndLine + ") must be >= contentStartLine ("
                            + contentStartLine + ")");
        }
    }

    /**
     * Converts a 1-based line number within the extracted content to the
     * corresponding 1-based line number in the original HTML document.
     *
     * @param contentLine the 1-based line number within the extracted content.
     * @return the 1-based line number in the original HTML document.
     */
    public int mapToOriginalLine(int contentLine) {
        if (contentLine < 1) {
            return contentStartLine;
        }
        return contentStartLine + (contentLine - 1);
    }

    /**
     * Returns {@code true} if the content block is empty or contains only
     * whitespace.
     *
     * @return whether the block has no meaningful content.
     */
    public boolean isEmpty() {
        return content.isBlank();
    }

    /**
     * Returns the number of lines in the extracted content.
     *
     * @return the line count of the embedded content.
     */
    public int contentLineCount() {
        if (content.isEmpty()) {
            return 0;
        }
        long count = content.chars().filter(ch -> ch == '\n').count();
        return (int) count + 1;
    }
}
