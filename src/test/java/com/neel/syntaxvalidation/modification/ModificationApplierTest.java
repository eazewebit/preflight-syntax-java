package com.neel.syntaxvalidation.modification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModificationApplierTest {

    private final ModificationApplier applier = new ModificationApplier();

    private static final List<String> ORIGINAL = List.of(
            "1", "2", "3", "4", "5");

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
}
