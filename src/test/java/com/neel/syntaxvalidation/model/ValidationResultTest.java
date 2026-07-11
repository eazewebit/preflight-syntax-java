package com.neel.syntaxvalidation.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationResultTest {

    @Test
    void valid_hasNoErrorsAndTrueFlag() {
        ValidationResult result = ValidationResult.valid("ok");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isEqualTo("ok");
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void invalid_withMessageOnly_hasNoDetailedErrors() {
        ValidationResult result = ValidationResult.invalid("boom");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isEqualTo("boom");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void invalid_withSingleError_carriesIt() {
        ValidationError error = new ValidationError(3, "missing semicolon");
        ValidationResult result = ValidationResult.invalid("boom", error);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactly(error);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void invalid_withErrorList_carriesAll() {
        List<ValidationError> errors = List.of(
                new ValidationError(1, "a"),
                new ValidationError(2, "b"));
        ValidationResult result = ValidationResult.invalid("multi", errors);

        assertThat(result.getErrors()).hasSize(2);
    }

    @Test
    void getErrors_isUnmodifiable() {
        ValidationResult result = ValidationResult.invalid("boom",
                List.of(new ValidationError(1, "x")));

        assertThatThrownBy(() -> result.getErrors().add(new ValidationError(2, "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalid_rejectsNullArguments() {
        assertThatThrownBy(() -> ValidationResult.invalid(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ValidationResult.invalid("m", (List<ValidationError>) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void equalsAndHashCode_areValueBased() {
        ValidationResult a = ValidationResult.invalid("m", new ValidationError(1, "e"));
        ValidationResult b = ValidationResult.invalid("m", new ValidationError(1, "e"));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
