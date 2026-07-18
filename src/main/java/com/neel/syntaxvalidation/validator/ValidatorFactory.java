package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.validator.css.CssValidator;
import com.neel.syntaxvalidation.validator.html.HtmlValidator;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.mixed.MixedContentValidator;
import com.neel.syntaxvalidation.validator.php.PhpValidator;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A simple factory that maps a {@link Language} to the corresponding
 * {@link LanguageValidator} implementation.
 *
 * <p>The factory is pre-populated with all built-in validators and supports
 * registering additional custom validators at runtime via
 * {@link #register(Language, LanguageValidator)}.
 *
 * <h2>Built-in validators</h2>
 * <ul>
 *   <li>{@link Language#JAVASCRIPT} &rarr; {@link JavaScriptValidator}</li>
 *   <li>{@link Language#HTML}       &rarr; {@link HtmlValidator}</li>
 *   <li>{@link Language#CSS}        &rarr; {@link CssValidator}</li>
 *   <li>{@link Language#PHP}        &rarr; {@link PhpValidator}</li>
 * </ul>
 *
 * <p><b>Thread-safety.</b> This class is safe for concurrent reads from
 * multiple threads after initial construction. Mutation via
 * {@link #register(Language, LanguageValidator)} is <em>not</em> thread-safe
 * and should only be called during application startup.
 */
public final class ValidatorFactory {

    private final Map<Language, LanguageValidator> validators =
            new EnumMap<>(Language.class);

    /**
     * Creates a new factory with all built-in validators registered.
     */
    public ValidatorFactory() {
        this(true);
    }

    /**
     * Creates a new factory, optionally registering built-in validators.
     *
     * @param registerBuiltins {@code true} to register the built-in
     *                         JavaScript, HTML, CSS, PHP, and Java validators;
     *                         {@code false} for an empty factory.
     */
    public ValidatorFactory(boolean registerBuiltins) {
        if (registerBuiltins) {
            validators.put(Language.JAVASCRIPT, new JavaScriptValidator());
            validators.put(Language.HTML, new HtmlValidator());
            validators.put(Language.CSS, new CssValidator());
            validators.put(Language.PHP, new PhpValidator());
            validators.put(Language.JAVA, new JavaValidator());
        }
    }

    /**
     * Returns the {@link LanguageValidator} for the given language, if one is
     * registered.
     *
     * @param language the language to look up.
     * @return an {@link Optional} containing the validator, or empty if no
     *         validator is registered for the given language.
     * @throws NullPointerException if {@code language} is {@code null}.
     */
    public Optional<LanguageValidator> getValidator(Language language) {
        return Optional.ofNullable(validators.get(language));
    }

    /**
     * Registers (or replaces) a validator for a given language.
     *
     * @param language  the language.
     * @param validator the validator to associate with the language.
     * @throws NullPointerException if either argument is {@code null}.
     */
    public void register(Language language, LanguageValidator validator) {
        validators.put(language, validator);
    }

    /**
     * Returns the set of languages for which a validator is currently
     * registered.
     *
     * @return an unmodifiable set of registered languages.
     */
    public java.util.Set<Language> supportedLanguages() {
        return java.util.Collections.unmodifiableSet(validators.keySet());
    }

    /**
     * Creates a new {@link MixedContentValidator} for comprehensive
     * validation of HTML documents that contain embedded CSS and JavaScript.
     *
     * <p>The mixed-content validator uses the Nu Html Checker (vnu.jar) for
     * HTML structural validation (when available), the CSS syntax engine for
     * {@code <style>} content, and the JavaScript syntax engine for
     * {@code <script>} content.
     *
     * <p>This method always creates a fresh instance. If you need a
     * singleton, cache the returned value.
     *
     * @return a new {@link MixedContentValidator}.
     */
    public MixedContentValidator getMixedContentValidator() {
        return new MixedContentValidator();
    }
}
