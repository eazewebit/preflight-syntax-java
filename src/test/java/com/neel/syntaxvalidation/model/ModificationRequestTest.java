package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModificationRequestTest {

    @Test
    void build_populatesAllFields() {
        ModificationRequest request = ModificationRequest.builder()
                .filePath("src/app.js")
                .fromLine(2)
                .toLine(4)
                .replacement("const x = 1;")
                .build();

        assertThat(request.getFilePath()).isEqualTo("src/app.js");
        assertThat(request.getFromLine()).isEqualTo(2);
        assertThat(request.getToLine()).isEqualTo(4);
        assertThat(request.getReplacement()).isEqualTo("const x = 1;");
    }

    @Test
    void build_defaultsReplacementToEmptyString() {
        ModificationRequest request = ModificationRequest.builder()
                .filePath("a.js")
                .fromLine(1)
                .toLine(1)
                .build();

        assertThat(request.getReplacement()).isEqualTo("");
    }

    @Test
    void build_treatsNullReplacementAsEmpty() {
        ModificationRequest request = ModificationRequest.builder()
                .filePath("a.js")
                .fromLine(1)
                .toLine(1)
                .replacement(null)
                .build();

        assertThat(request.getReplacement()).isEmpty();
    }

    @Test
    void build_rejectsBlankFilePath() {
        assertThatThrownBy(() -> ModificationRequest.builder()
                .filePath("  ")
                .fromLine(1)
                .toLine(1)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("filePath");
    }

    @Test
    void build_rejectsNullFilePath() {
        assertThatThrownBy(() -> ModificationRequest.builder()
                .fromLine(1)
                .toLine(1)
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void build_rejectsFromLineBelowOne() {
        assertThatThrownBy(() -> ModificationRequest.builder()
                .filePath("a.js")
                .fromLine(0)
                .toLine(1)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fromLine");
    }

    @Test
    void build_rejectsToLineBeforeFromLine() {
        assertThatThrownBy(() -> ModificationRequest.builder()
                .filePath("a.js")
                .fromLine(5)
                .toLine(3)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("toLine");
    }

    @Test
    void build_allowsFromLineEqualToToLine() {
        ModificationRequest request = ModificationRequest.builder()
                .filePath("a.js")
                .fromLine(3)
                .toLine(3)
                .replacement("x")
                .build();

        assertThat(request.getFromLine()).isEqualTo(request.getToLine());
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        ModificationRequest a = ModificationRequest.builder()
                .filePath("a.js").fromLine(1).toLine(2).replacement("x").build();
        ModificationRequest b = ModificationRequest.builder()
                .filePath("a.js").fromLine(1).toLine(2).replacement("x").build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
