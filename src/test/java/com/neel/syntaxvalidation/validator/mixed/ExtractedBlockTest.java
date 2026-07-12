package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ExtractedBlock}.
 */
@DisplayName("ExtractedBlock")
class ExtractedBlockTest {

    // ---------------------------------------------------------------
    // Construction & Validation
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("creates a valid CSS block")
        void createsCssBlock() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body { color: red; }", 5, 6, 1, 6);

            assertThat(block.language()).isEqualTo(Language.CSS);
            assertThat(block.content()).isEqualTo("body { color: red; }");
            assertThat(block.startLine()).isEqualTo(5);
            assertThat(block.contentStartLine()).isEqualTo(6);
            assertThat(block.contentStartColumn()).isEqualTo(1);
            assertThat(block.contentEndLine()).isEqualTo(6);
        }

        @Test
        @DisplayName("creates a valid JavaScript block")
        void createsJsBlock() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.JAVASCRIPT, "var x = 1;", 10, 11, 1, 11);

            assertThat(block.language()).isEqualTo(Language.JAVASCRIPT);
            assertThat(block.content()).isEqualTo("var x = 1;");
        }

        @Test
        @DisplayName("allows multiline content")
        void multilineContent() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {\n  color: red;\n}", 1, 2, 1, 4);

            assertThat(block.contentStartLine()).isEqualTo(2);
            assertThat(block.contentEndLine()).isEqualTo(4);
            assertThat(block.content()).contains("color");
        }
        @Test
        @DisplayName("allows empty content")
        void emptyContent() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "", 1, 2, 1, 2);

            assertThat(block.content()).isEmpty();
            assertThat(block.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("allows contentStartLine == contentEndLine")
        void sameStartAndEndLine() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, ".x {}", 5, 6, 1, 6);

            assertThat(block.contentEndLine()).isEqualTo(block.contentStartLine());
        }

        @Test
        @DisplayName("rejects null language")
        void rejectsNullLanguage() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(null, "content", 1, 2, 1, 2))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("language");
        }

        @Test
        @DisplayName("rejects null content")
        void rejectsNullContent() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(Language.CSS, null, 1, 2, 1, 2))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("content");
        }

        @Test
        @DisplayName("rejects startLine < 1")
        void rejectsStartLineBelowOne() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(Language.CSS, "x", 0, 2, 1, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("startLine");
        }

        @Test
        @DisplayName("rejects contentStartLine < 1")
        void rejectsContentStartLineBelowOne() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(Language.CSS, "x", 1, 0, 1, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contentStartLine");
        }

        @Test
        @DisplayName("rejects contentStartColumn < 1")
        void rejectsContentStartColumnBelowOne() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(Language.CSS, "x", 1, 2, 0, 2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contentStartColumn");
        }

        @Test
        @DisplayName("rejects contentEndLine < contentStartLine")
        void rejectsEndLineBeforeStartLine() {
            assertThatThrownBy(() ->
                    new ExtractedBlock(Language.CSS, "x", 1, 5, 1, 4))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contentEndLine");
        }
    }

    // ---------------------------------------------------------------
    // mapToOriginalLine
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("mapToOriginalLine")
    class MapToOriginalLine {

        @Test
        @DisplayName("maps line 1 of content to contentStartLine")
        void mapsFirstLineToStart() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body { }", 5, 10, 1, 10);
            assertThat(block.mapToOriginalLine(1)).isEqualTo(10);
        }

        @Test
        @DisplayName("maps line 2 of content to contentStartLine + 1")
        void mapsSecondLine() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {\n  color: red;\n}", 5, 10, 1, 12);
            assertThat(block.mapToOriginalLine(2)).isEqualTo(11);
        }

        @Test
        @DisplayName("maps line 3 correctly")
        void mapsThirdLine() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {\n  color: red;\n}", 5, 10, 1, 12);
            assertThat(block.mapToOriginalLine(3)).isEqualTo(12);
        }

        @Test
        @DisplayName("maps line 0 to contentStartLine (boundary)")
        void mapsZeroToStart() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "x", 1, 5, 1, 5);
            // line < 1 returns contentStartLine
            assertThat(block.mapToOriginalLine(0)).isEqualTo(5);
        }

        @Test
        @DisplayName("maps negative line to contentStartLine (boundary)")
        void mapsNegativeToStart() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "x", 1, 5, 1, 5);
            assertThat(block.mapToOriginalLine(-1)).isEqualTo(5);
        }

        @Test
        @DisplayName("maps large line numbers correctly")
        void mapsLargeLineNumber() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "x", 1, 100, 1, 200);
            assertThat(block.mapToOriginalLine(50)).isEqualTo(149);
        }
    }

    // ---------------------------------------------------------------
    // isEmpty
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("isEmpty")
    class IsEmpty {

        @Test
        @DisplayName("returns true for empty string")
        void trueForEmpty() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "", 1, 2, 1, 2);
            assertThat(block.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("returns true for whitespace-only content")
        void trueForWhitespace() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "   \n  \t  ", 1, 2, 1, 4);
            assertThat(block.isEmpty()).isTrue();
        }

        @Test
        @DisplayName("returns false for non-empty content")
        void falseForContent() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {}", 1, 2, 1, 2);
            assertThat(block.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("returns false for content with leading/trailing spaces")
        void falseForPaddedContent() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "  body {}  ", 1, 2, 1, 2);
            assertThat(block.isEmpty()).isFalse();
        }
    }

    // ---------------------------------------------------------------
    // contentLineCount
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("contentLineCount")
    class ContentLineCount {

        @Test
        @DisplayName("returns 0 for empty content")
        void zeroForEmpty() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "", 1, 2, 1, 2);
            assertThat(block.contentLineCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 1 for single-line content")
        void oneForSingleLine() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body { color: red; }", 1, 2, 1, 2);
            assertThat(block.contentLineCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns correct count for multiline content")
        void correctForMultiline() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {\n  color: red;\n  font-size: 14px;\n}", 1, 2, 1, 5);
            assertThat(block.contentLineCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("returns 2 for content ending with newline")
        void twoForTrailingNewline() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {}\n", 1, 2, 1, 3);
            assertThat(block.contentLineCount()).isEqualTo(2);
        }
    }

    // ---------------------------------------------------------------
    // Record equality and immutability
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("equal blocks are considered equal")
        void equalBlocks() {
            ExtractedBlock a = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);
            ExtractedBlock b = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different content makes blocks unequal")
        void differentContent() {
            ExtractedBlock a = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);
            ExtractedBlock b = new ExtractedBlock(
                    Language.CSS, "div {}", 5, 6, 1, 6);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different language makes blocks unequal")
        void differentLanguage() {
            ExtractedBlock a = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);
            ExtractedBlock b = new ExtractedBlock(
                    Language.JAVASCRIPT, "body {}", 5, 6, 1, 6);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("different startLine makes blocks unequal")
        void differentStartLine() {
            ExtractedBlock a = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);
            ExtractedBlock b = new ExtractedBlock(
                    Language.CSS, "body {}", 7, 6, 1, 6);

            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("toString contains all field names")
        void toStringContainsFields() {
            ExtractedBlock block = new ExtractedBlock(
                    Language.CSS, "body {}", 5, 6, 1, 6);

            String str = block.toString();
            assertThat(str).contains("CSS");
            assertThat(str).contains("body {}");
        }
    }
}
