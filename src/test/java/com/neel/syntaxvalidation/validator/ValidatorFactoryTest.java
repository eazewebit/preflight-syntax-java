package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorFactoryTest {

    @Test
    void defaultFactory_registersJavaScriptValidator() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThat(factory.supports(Language.JAVASCRIPT)).isTrue();
        assertThat(factory.getValidator(Language.JAVASCRIPT))
                .isPresent()
                .containsInstanceOf(JavaScriptValidator.class);
    }

    @Test
    void defaultFactory_doesNotRegisterPlaceholderLanguages() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThat(factory.supports(Language.PYTHON)).isFalse();
        assertThat(factory.getValidator(Language.TYPESCRIPT)).isEmpty();
    }

    @Test
    void register_replacesExistingValidator() {
        ValidatorFactory factory = new ValidatorFactory();
        LanguageValidator custom = new StubValidator(Language.JAVASCRIPT);

        factory.register(Language.JAVASCRIPT, custom);

        assertThat(factory.getValidator(Language.JAVASCRIPT)).contains(custom);
    }

    @Test
    void requireValidator_returnsRegisteredValidator() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThat(factory.requireValidator(Language.JAVASCRIPT)).isNotNull();
    }

    @Test
    void requireValidator_throwsWhenMissing() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThatThrownBy(() -> factory.requireValidator(Language.JAVA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JAVA");
    }

    @Test
    void register_allowsNewLanguages() {
        ValidatorFactory factory = new ValidatorFactory();
        LanguageValidator pythonValidator = new StubValidator(Language.PYTHON);

        factory.register(Language.PYTHON, pythonValidator);

        assertThat(factory.getValidator(Language.PYTHON)).contains(pythonValidator);
    }

    /** Minimal LanguageValidator stub for registration tests. */
    static final class StubValidator implements LanguageValidator {
        private final Language language;

        StubValidator(Language language) {
            this.language = language;
        }

        @Override
        public ValidationResult validate(String content) {
            return ValidationResult.valid("stub");
        }

        @Override
        public Language getLanguage() {
            return language;
        }
    }
}
