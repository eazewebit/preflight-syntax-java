package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BatchModificationRequestTest {

    @Test
    void build_withSingleReplacement() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(2).toLine(4).replacement("X").build();

        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r)
                .build();

        assertThat(request.filePath()).isEqualTo("src/app.js");
        assertThat(request.replacements()).hasSize(1);
        assertThat(request.replacements().get(0)).isEqualTo(r);
    }

    @Test
    void build_withMultipleNonOverlappingReplacements() {
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(5).toLine(7).replacement("B").build();

        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r1)
                .addReplacement(r2)
                .build();

        assertThat(request.replacements()).hasSize(2);
        assertThat(request.replacements()).containsExactly(r1, r2);
    }

    @Test
    void build_addAllReplacements() {
        List<LineReplacement> replacements = List.of(
                LineReplacement.builder().fromLine(1).toLine(1).replacement("A").build(),
                LineReplacement.builder().fromLine(5).toLine(5).replacement("B").build()
        );

        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addAllReplacements(replacements)
                .build();

        assertThat(request.replacements()).hasSize(2);
    }

    @Test
    void build_rejectsNullFilePath() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("X").build();

        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .addReplacement(r)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("filePath");
    }

    @Test
    void build_rejectsEmptyReplacements() {
        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .filePath("src/app.js")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void build_rejectsOverlappingRanges() {
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(5).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(3).toLine(7).replacement("B").build();

        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r1)
                .addReplacement(r2)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
    }

    @Test
    void build_rejectsOverlappingRangesReversed() {
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(5).toLine(7).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(1).toLine(5).replacement("B").build();

        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r1)
                .addReplacement(r2)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlapping");
    }

    @Test
    void build_allowsAdjacentRanges() {
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(3).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(4).toLine(6).replacement("B").build();

        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r1)
                .addReplacement(r2)
                .build();

        assertThat(request.replacements()).hasSize(2);
    }

    @Test
    void build_replacementsListIsImmutable() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("X").build();

        BatchModificationRequest request = BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(r)
                .build();

        assertThatThrownBy(() -> request.replacements().add(r))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void build_addAllReplacementsRejectsNull() {
        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addAllReplacements(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_addReplacementRejectsNull() {
        assertThatThrownBy(() -> BatchModificationRequest.builder()
                .filePath("src/app.js")
                .addReplacement(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("x").build();

        BatchModificationRequest a = BatchModificationRequest.builder()
                .filePath("a.js").addReplacement(r).build();
        BatchModificationRequest b = BatchModificationRequest.builder()
                .filePath("a.js").addReplacement(r).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
