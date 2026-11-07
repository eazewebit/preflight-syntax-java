package com.neel.syntaxvalidation.modification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineEndingTest {

    // ---- detect: separator -------------------------------------------------

    @Test
    void detect_lfFile() {
        assertThat(LineEnding.detect("a\nb\nc").separator()).isEqualTo("\n");
    }

    @Test
    void detect_crlfFile() {
        assertThat(LineEnding.detect("a\r\nb\r\nc").separator()).isEqualTo("\r\n");
    }

    @Test
    void detect_majorityCrlfWinsOverFewLoneLf() {
        // 3 CRLF pairs vs 1 lone LF -> CRLF dominates.
        assertThat(LineEnding.detect("a\r\nb\r\nc\r\nd\ne").separator()).isEqualTo("\r\n");
    }

    @Test
    void detect_majorityLoneLfWinsOverFewCrlf() {
        // 3 lone LF vs 1 CRLF pair -> LF dominates.
        assertThat(LineEnding.detect("a\nb\nc\nd\r\ne").separator()).isEqualTo("\n");
    }

    @Test
    void detect_emptyOrNullDefaultsToLfNoTrailing() {
        assertThat(LineEnding.detect("")).isEqualTo(new LineEnding("\n", false));
        assertThat(LineEnding.detect(null)).isEqualTo(new LineEnding("\n", false));
    }

    // ---- detect: trailing newline -----------------------------------------

    @Test
    void detect_trailingNewlinePresent() {
        assertThat(LineEnding.detect("a\nb\n").hasTrailingNewline()).isTrue();
        assertThat(LineEnding.detect("a\r\nb\r\n").hasTrailingNewline()).isTrue();
    }

    @Test
    void detect_noTrailingNewline() {
        assertThat(LineEnding.detect("a\nb").hasTrailingNewline()).isFalse();
        assertThat(LineEnding.detect("a\r\nb").hasTrailingNewline()).isFalse();
    }

    // ---- join: round-trips the original byte-for-byte ----------------------

    @Test
    void join_preservesCrlfAndTrailingNewline() {
        String original = "alpha\r\nbeta\r\ngamma\r\n";
        LineEnding le = LineEnding.detect(original);

        List<String> lines = List.of("alpha", "beta", "gamma");

        assertThat(le.join(lines)).isEqualTo(original);
    }

    @Test
    void join_preservesLfAndTrailingNewline() {
        String original = "alpha\nbeta\ngamma\n";
        LineEnding le = LineEnding.detect(original);

        List<String> lines = List.of("alpha", "beta", "gamma");

        assertThat(le.join(lines)).isEqualTo(original);
    }

    @Test
    void join_preservesAbsenceOfTrailingNewline() {
        String original = "alpha\r\nbeta";
        LineEnding le = LineEnding.detect(original);

        List<String> lines = List.of("alpha", "beta");

        assertThat(le.join(lines)).isEqualTo(original);
    }

    @Test
    void join_emptyListYieldsEmptyString() {
        assertThat(new LineEnding("\r\n", true).join(List.of())).isEmpty();
    }

    @Test
    void join_singleBlankLineWithTrailingNewlineRoundTrips() {
        // A file containing just "\n" is one blank line with a trailing newline.
        String original = "\n";
        LineEnding le = LineEnding.detect(original);

        assertThat(le.join(List.of(""))).isEqualTo(original);
    }

    @Test
    void join_rejectsNullLines() {
        assertThatThrownBy(() -> new LineEnding("\n", false).join(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- value semantics ---------------------------------------------------

    @Test
    void equalsAndHashCodeReflectBothFields() {
        assertThat(new LineEnding("\r\n", true)).isEqualTo(new LineEnding("\r\n", true));
        assertThat(new LineEnding("\n", false)).isNotEqualTo(new LineEnding("\r\n", false));
        assertThat(new LineEnding("\n", true)).isNotEqualTo(new LineEnding("\n", false));
        assertThat(new LineEnding("\r\n", true).hashCode())
                .isEqualTo(new LineEnding("\r\n", true).hashCode());
    }

    @Test
    void constructorRejectsNullSeparator() {
        assertThatThrownBy(() -> new LineEnding(null, false))
                .isInstanceOf(NullPointerException.class);
    }
}
