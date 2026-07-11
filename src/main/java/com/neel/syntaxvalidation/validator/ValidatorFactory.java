package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Factory (and lightweight registry) for {@link LanguageValidator} instances,
 * keyed by {@link Language}.
 *
 * <p>The factory implements the <em>Factory</em> pattern: it knows how to create
 * the default validator for each supported language and also allows callers to
 * register custom or pre-configured validators (for example, one with a
 * preferred binary path). Lookup is performed via {@link #getValidator(Language)}
 * or {@link #requireValidator(Language)}.
 */
public class ValidatorFactory {

    private final Map<Language, LanguageValidator> validators = new EnumMap<>(Language.class);

    /** Creates a factory pre-registered with the JavaScript validator. */
    public ValidatorFactory() {
        register(Language.JAVASCRIPT, new JavaScriptValidator());
    }

    /**
     * Registers (or replaces) the validator for a language.
     *
     * @param language  the target language; must not be {@code null}.
     * @param validator the validator; must not be {@code null}.
     */
    public void register(Language language, LanguageValidator validator) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(validator, "validator");
        validators.put(language, validator);
    }

    /**
     * @param language the target language.
     * @return the registered validator, or {@link Optional#empty()} if none exists.
     */
    public Optional<LanguageValidator> getValidator(Language language) {
        return Optional.ofNullable(validators.get(language));
    }

    /**
     * @param language the target language.
     * @return the registered validator.
     * @throws IllegalArgumentException if no validator is registered for the language.
     */
    public LanguageValidator requireValidator(Language language) {
        return getValidator(language).orElseThrow(() ->
                new IllegalArgumentException("No validator registered for language: " + language));
    }

    /** @return {@code true} if a validator is registered for the language. */
    public boolean supports(Language language) {
        return validators.containsKey(language);
    }
}
