package com.neel.syntaxvalidation.modification;

import com.neel.syntaxvalidation.model.LineReplacement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModificationApplierTest {

    private final ModificationApplier applier = new ModificationApplier();

    private static final List<String> ORIGINAL = List.of(
            "1", "2", "3", "4", "5");

    // ================================================================
    //  Single replacement tests (existing)
    // ================================================================

    @Test
    void apply_replacesMiddleRangeWithSingleLine() {
        List<String> result = applier.apply(ORIGINAL, 2, 4, "X");

        assertThat(result).containsExactly("1", "X", "5");
    }

    @Test
    void apply_replacesRangeWithMultipleLines() {
        List<String> result = applier.apply(ORIGINAL, 2, 2, "X\nY\nZ");

        assertThat(result).containsExactly("1", "X", "Y", "Z", "3", "4", "5");
    }

    @Test
    void apply_handlesCrlfInReplacement() {
        List<String> result = applier.apply(ORIGINAL, 3, 3, "X\r\nY");

        assertThat(result).containsExactly("1", "2", "X", "Y", "4", "5");
    }

    @Test
    void apply_emptyReplacementDeletesRange() {
        List<String> result = applier.apply(ORIGINAL, 2, 4, "");

        assertThat(result).containsExactly("1", "5");
    }

    @Test
    void apply_singleLineRange() {
        List<String> result = applier.apply(ORIGINAL, 3, 3, "Z");

        assertThat(result).containsExactly("1", "2", "Z", "4", "5");
    }

    @Test
    void apply_replacesFirstLine() {
        List<String> result = applier.apply(ORIGINAL, 1, 1, "NEW");

        assertThat(result).containsExactly("NEW", "2", "3", "4", "5");
    }

    @Test
    void apply_replacesLastLine() {
        List<String> result = applier.apply(ORIGINAL, 5, 5, "END");

        assertThat(result).containsExactly("1", "2", "3", "4", "END");
    }

    @Test
    void apply_fromLineBeyondEnd_appendsReplacement() {
        List<String> result = applier.apply(ORIGINAL, 10, 12, "APPENDED");

        assertThat(result).containsExactly("1", "2", "3", "4", "5", "APPENDED");
    }

    @Test
    void apply_toLineBeyondEnd_truncatesTail() {
        List<String> result = applier.apply(ORIGINAL, 3, 100, "X");

        assertThat(result).containsExactly("1", "2", "X");
    }

    @Test
    void apply_nullReplacementDeletesRange() {
        List<String> result = applier.apply(ORIGINAL, 1, 3, null);

        assertThat(result).containsExactly("4", "5");
    }

    @Test
    void apply_doesNotMutateOriginal() {
        List<String> mutable = new java.util.ArrayList<>(ORIGINAL);

        applier.apply(mutable, 2, 3, "X");

        assertThat(mutable).containsExactlyElementsOf(ORIGINAL);
    }

    @Test
    void apply_resultIsImmutable() {
        List<String> result = applier.apply(ORIGINAL, 1, 1, "X");

        assertThatThrownBy(() -> result.add("Y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void apply_rejectsInvalidRanges() {
        assertThatThrownBy(() -> applier.apply(ORIGINAL, 0, 1, "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> applier.apply(ORIGINAL, 3, 2, "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_rejectsNullInput() {
        assertThatThrownBy(() -> applier.apply(null, 1, 1, "X"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void apply_emptyReplacementPreservesBlankLinesInReplacementIfAny() {
        List<String> result = applier.apply(ORIGINAL, 1, 1, " \n \n ");

        assertThat(result).hasSize(7);
    }

    // ================================================================
    //  Batch replacement tests (applyAll)
    // ================================================================

    @Test
    void applyAll_singleReplacement() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(2).toLine(4).replacement("X").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r));

        assertThat(result).containsExactly("1", "X", "5");
    }

    @Test
    void applyAll_multipleNonOverlappingReplacementsAppliedCorrectly() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(3).toLine(3).replacement("B").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r1, r2));

        // After r1: ["A", "2", "3", "4", "5"]
        // After r2 (applied first due to bottom-to-top): ["1", "2", "B", "4", "5"]
        // Then r1: ["A", "2", "B", "4", "5"]
        assertThat(result).containsExactly("A", "2", "B", "4", "5");
    }

    @Test
    void applyAll_multipleReplacementsWithDifferentSizes() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(2).replacement("A\nB\nC").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(4).toLine(5).replacement("X").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r1, r2));

        // r2 applied first (bottom): ["1", "2", "3", "X"]
        // r1 applied second (top):   ["A", "B", "C", "3", "X"]
        assertThat(result).containsExactly("A", "B", "C", "3", "X");
    }

    @Test
    void applyAll_replacementsAppliedInReverseOrder() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        // Both touch line 3, but in sequence
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(3).replacement("TOP").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(5).toLine(5).replacement("BOTTOM").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r1, r2));

        // r2 applied first (line 5): ["1", "2", "3", "4", "BOTTOM"]
        // r1 applied second (lines 1-3): ["TOP", "4", "BOTTOM"]
        assertThat(result).containsExactly("TOP", "4", "BOTTOM");
    }

    @Test
    void applyAll_outOfOrderInputStillAppliedCorrectly() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        // Provide r2 (later line) before r1 (earlier line)
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(5).toLine(5).replacement("E").build();
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("S").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r2, r1));

        // Should sort and apply r2 first (bottom), then r1 (top)
        assertThat(result).containsExactly("S", "2", "3", "4", "E");
    }

    @Test
    void applyAll_deletionReplacements() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(2).toLine(2).replacement("").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(4).toLine(4).replacement("").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r1, r2));

        // After r2 (line 4 deleted): ["1", "2", "3", "5"]
        // After r1 (line 2 deleted): ["1", "3", "5"]
        assertThat(result).containsExactly("1", "3", "5");
    }

    @Test
    void applyAll_rejectsNullOriginalLines() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("X").build();

        assertThatThrownBy(() -> applier.applyAll(null, List.of(r)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void applyAll_rejectsNullReplacements() {
        assertThatThrownBy(() -> applier.applyAll(ORIGINAL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void applyAll_rejectsEmptyReplacements() {
        assertThatThrownBy(() -> applier.applyAll(ORIGINAL, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void applyAll_doesNotMutateOriginal() {
        List<String> mutable = new java.util.ArrayList<>(ORIGINAL);
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(5).replacement("X").build();

        applier.applyAll(mutable, List.of(r));

        assertThat(mutable).containsExactlyElementsOf(ORIGINAL);
    }

    @Test
    void applyAll_resultIsImmutable() {
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("X").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r));

        assertThatThrownBy(() -> result.add("Y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void applyAll_threeReplacementsSpanningEntireFile() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("A").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(3).toLine(3).replacement("B").build();
        LineReplacement r3 = LineReplacement.builder()
                .fromLine(5).toLine(5).replacement("C").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r1, r2, r3));

        assertThat(result).containsExactly("A", "2", "B", "4", "C");
    }

    @Test
    void applyAll_replacementExpandsLinesInMiddle() {
        // ORIGINAL: ["1", "2", "3", "4", "5"]
        LineReplacement r = LineReplacement.builder()
                .fromLine(2).toLine(4).replacement("X\nY\nZ\nW\nV").build();

        List<String> result = applier.applyAll(ORIGINAL, List.of(r));

        assertThat(result).containsExactly("1", "X", "Y", "Z", "W", "V", "5");
    }
}
