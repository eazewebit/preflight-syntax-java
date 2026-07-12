package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.mixed.MixedContentValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorFactoryTest {

    @Test
    void defaultFactory_registersJavaScriptValidator() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThat(factory.getValidator(Language.JAVASCRIPT))
                .isPresent()
                .containsInstanceOf(JavaScriptValidator.class);
    }

    @Test
    void defaultFactory_hasSupportedLanguages() {
        ValidatorFactory factory = new ValidatorFactory();

        assertThat(factory.supportedLanguages()).contains(Language.JAVASCRIPT);
    }

    @Test
    void register_replacesExistingValidator() {
        ValidatorFactory factory = new ValidatorFactory();
        LanguageValidator custom = new StubValidator(Language.JAVASCRIPT);

        factory.register(Language.JAVASCRIPT, custom);

        assertThat(factory.getValidator(Language.JAVASCRIPT)).contains(custom);
    }

    @Test
    void register_allowsNewLanguages() {
        ValidatorFactory factory = new ValidatorFactory();
        LanguageValidator pythonValidator = new StubValidator(Language.PYTHON);

        factory.register(Language.PYTHON, pythonValidator);

        assertThat(factory.getValidator(Language.PYTHON)).contains(pythonValidator);
    }

    @Test
    void getMixedContentValidator_returnsNonNull() {
        ValidatorFactory factory = new ValidatorFactory();

        MixedContentValidator mixedValidator = factory.getMixedContentValidator();

        assertThat(mixedValidator).isNotNull();
        assertThat(mixedValidator.getLanguage()).isEqualTo(Language.HTML);
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
