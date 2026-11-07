package com.neel.syntaxvalidation.modification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Document}: the byte-faithful text model that keeps an edit
 * round-trip lossless (per-line terminators, trailing-newline state and BOM).
 */
@DisplayName("Document (byte-faithful text model)")
class DocumentTest {

    @Test
    @DisplayName("parse records each line's exact terminator")
    void parse_recordsExactTerminators() {
        Document doc = Document.parse("a\r\nb\nc\rd");

        assertThat(doc.lines()).containsExactly("a", "b", "c", "d");
        assertThat(doc.terminator(0)).isEqualTo("\r\n");
        assertThat(doc.terminator(1)).isEqualTo("\n");
        assertThat(doc.terminator(2)).isEqualTo("\r");
        assertThat(doc.terminator(3)).isEmpty();
        assertThat(doc.hasTrailingNewline()).isFalse();
    }

    @Test
    @DisplayName("parse: a single trailing terminator does not create an extra line")
    void parse_trailingTerminatorAddsNoExtraLine() {
        Document doc = Document.parse("a\nb\n");

        assertThat(doc.lines()).containsExactly("a", "b");
        assertThat(doc.hasTrailingNewline()).isTrue();
        assertThat(doc.render()).isEqualTo("a\nb\n");
    }

    @Test
    @DisplayName("parse: genuine blank lines are preserved")
    void parse_preservesBlankLines() {
        Document doc = Document.parse("a\n\n\nb\n");

        assertThat(doc.lines()).containsExactly("a", "", "", "b");
        assertThat(doc.render()).isEqualTo("a\n\n\nb\n");
    }

    @Test
    @DisplayName("parse: empty text yields zero lines and renders empty")
    void parse_emptyText() {
        Document doc = Document.parse("");

        assertThat(doc.isEmpty()).isTrue();
        assertThat(doc.size()).isZero();
        assertThat(doc.render()).isEmpty();
        assertThat(doc.hasTrailingNewline()).isFalse();
    }

    @Test
    @DisplayName("parse: a lone newline is one empty terminated line")
    void parse_loneNewline() {
        Document doc = Document.parse("\n");

        assertThat(doc.lines()).containsExactly("");
        assertThat(doc.terminator(0)).isEqualTo("\n");
        assertThat(doc.render()).isEqualTo("\n");
    }

    @Test
    @DisplayName("parse: BOM is removed from line 1 and recorded as a flag")
    void parse_stripsBomIntoFlag() {
        Document doc = Document.parse("\uFEFF<?php\n$x = 1;\n");

        assertThat(doc.hasLeadingBom()).isTrue();
        assertThat(doc.lines().get(0)).isEqualTo("<?php");
        assertThat(doc.renderWithoutBom()).isEqualTo("<?php\n$x = 1;\n");
        assertThat(doc.render()).isEqualTo("\uFEFF<?php\n$x = 1;\n");
    }

    @Test
    @DisplayName("parse: a document without BOM reports the flag false")
    void parse_withoutBom() {
        Document doc = Document.parse("plain\n");

        assertThat(doc.hasLeadingBom()).isFalse();
        assertThat(doc.render()).isEqualTo(doc.renderWithoutBom());
    }

    @Test
    @DisplayName("dominantSeparator picks the most frequent break")
    void dominantSeparator_picksMajority() {
        assertThat(Document.parse("a\r\nb\r\nc\n").dominantSeparator()).isEqualTo("\r\n");
        assertThat(Document.parse("a\r\nb\nc\n").dominantSeparator()).isEqualTo("\n");
        assertThat(Document.parse("a\rb\r").dominantSeparator()).isEqualTo("\r");
    }

    @Test
    @DisplayName("dominantSeparator resolves ties CRLF then LF, and defaults to LF")
    void dominantSeparator_tiesAndDefault() {
        assertThat(Document.parse("a\r\nb\n").dominantSeparator()).isEqualTo("\r\n");
        assertThat(Document.parse("a\nb\rc\n").dominantSeparator()).isEqualTo("\n");
        assertThat(Document.parse("single line, no break").dominantSeparator()).isEqualTo("\n");
    }

    @Test
    @DisplayName("of(lines, LineEnding) reproduces a uniform layout")
    void of_uniformLayoutFromLineEnding() {
        Document doc = Document.of(java.util.List.of("a", "b"), new LineEnding("\r\n", true));

        assertThat(doc.render()).isEqualTo("a\r\nb\r\n");
        assertThat(doc.terminator(1)).isEqualTo("\r\n");
    }

    @Test
    @DisplayName("of(lines, LineEnding) honours a missing trailing newline")
    void of_missingTrailingNewline() {
        Document doc = Document.of(java.util.List.of("a", "b"), new LineEnding("\n", false));

        assertThat(doc.render()).isEqualTo("a\nb");
        assertThat(doc.terminator(1)).isEmpty();
    }

    @Test
    @DisplayName("of rejects mismatched line and terminator counts")
    void of_rejectsMismatchedSizes() {
        assertThatThrownBy(() -> Document.of(java.util.List.of("a", "b"),
                java.util.List.of("\n"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same size");
    }

    @Test
    @DisplayName("parse rejects null input")
    void parse_rejectsNull() {
        assertThatThrownBy(() -> Document.parse(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("raw");
    }

    @Test
    @DisplayName("terminator(index) is bounds-checked")
    void terminator_isBoundsChecked() {
        Document doc = Document.parse("a\nb\n");

        assertThatThrownBy(() -> doc.terminator(2))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("lineEnding() reports the coarse convention of a mixed document")
    void lineEnding_reportsCoarseConvention() {
        Document doc = Document.parse("a\r\nb\nc\r\n");

        assertThat(doc.lineEnding().separator()).isEqualTo("\r\n");
        assertThat(doc.lineEnding().hasTrailingNewline()).isTrue();
    }
}
