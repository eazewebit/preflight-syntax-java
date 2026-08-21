package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineReplacementTest {

    @Test
    void build_populatesAllFields() {
        LineReplacement replacement = LineReplacement.builder()
                .fromLine(2)
                .toLine(4)
                .replacement("const x = 1;")
                .build();

        assertThat(replacement.fromLine()).isEqualTo(2);
        assertThat(replacement.toLine()).isEqualTo(4);
        assertThat(replacement.replacement()).isEqualTo("const x = 1;");
        assertThat(replacement.expectedOriginalLines()).isNull();
    }

    @Test
    void build_withExpectedOriginalLines() {
        LineReplacement replacement = LineReplacement.builder()
                .fromLine(1)
                .toLine(2)
                .replacement("new content")
                .expectedOriginalLines("old line 1\nold line 2")
                .build();

        assertThat(replacement.expectedOriginalLines()).isEqualTo("old line 1\nold line 2");
    }

    @Test
    void build_rejectsNullReplacement() {
        assertThatThrownBy(() -> LineReplacement.builder()
                .fromLine(1)
                .toLine(1)
                .replacement(null)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("replacement");
    }

    @Test
    void build_rejectsFromLineBelowOne() {
        assertThatThrownBy(() -> LineReplacement.builder()
                .fromLine(0)
                .toLine(1)
                .replacement("x")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromLine");
    }

    @Test
    void build_rejectsToLineBeforeFromLine() {
        assertThatThrownBy(() -> LineReplacement.builder()
                .fromLine(5)
                .toLine(3)
                .replacement("x")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toLine");
    }

    @Test
    void build_allowsFromLineEqualToToLine() {
        LineReplacement replacement = LineReplacement.builder()
                .fromLine(3)
                .toLine(3)
                .replacement("x")
                .build();

        assertThat(replacement.fromLine()).isEqualTo(replacement.toLine());
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        LineReplacement a = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("x").build();
        LineReplacement b = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("x").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void equals_ignoresExpectedOriginalLines() {
        LineReplacement a = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("x")
                .expectedOriginalLines("old").build();
        LineReplacement b = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("x").build();

        // Records: equals checks all components
        assertThat(a).isNotEqualTo(b);
    }
}
