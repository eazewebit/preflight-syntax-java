package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.validator.css.CssValidator;
import com.neel.syntaxvalidation.validator.html.HtmlValidator;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.php.PhpValidator;
import com.neel.syntaxvalidation.validator.python.PythonValidator;
import com.neel.syntaxvalidation.validator.typescript.TypeScriptValidator;
import com.neel.syntaxvalidation.validator.mixed.MixedContentValidator;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory responsible for creating and caching {@link LanguageValidator} instances.
 *
 * <p>This class owns the lifecycle of validator instances, ensuring they are
 * created once and reused across validation requests. It also owns the
 * {@link BinaryManager} that provides auto-download, caching and
 * version-checking of external validation binaries (javac, node, tsc, python,
 * php, vnu, stylelint).
 *
 * <p>A single {@link ProcessExecutor} is shared across all validators produced
 * by the factory.
 */
public final class ValidatorFactory {

    private static final Set<Language> SUPPORTED_LANGUAGES =
            Collections.unmodifiableSet(Arrays.stream(Language.values()).collect(Collectors.toSet()));

    private final Map<Language, LanguageValidator> validators = new EnumMap<>(Language.class);
    private final ProcessExecutor processExecutor;
    private final BinaryManager binaryManager;

    /**
     * Creates a factory with default collaborators.
     * When no BinaryManager is supplied, validators fall back to resolving binaries from the system PATH.
     */
    public ValidatorFactory() {
        this(null, new ProcessExecutor());
    }

    /**
     * Creates a factory backed by a BinaryManager.
     * All validators produced by this factory will use the supplied manager
     * to resolve external binaries (with automatic download and caching).
     *
     * @param binaryManager the binary manager, or null for PATH-only resolution
     */
    public ValidatorFactory(BinaryManager binaryManager) {
        this(binaryManager, new ProcessExecutor());
    }

    /**
     * Creates a factory with explicit collaborators, primarily for testing.
     *
     * @param binaryManager   the binary manager, or null
     * @param processExecutor the process executor
     */
    public ValidatorFactory(BinaryManager binaryManager, ProcessExecutor processExecutor) {
        this.binaryManager = binaryManager;
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
    }

    /**
     * Returns a validator for the specified language, creating it if it does not yet exist.
     *
     * @param language the target language
     * @return an optional containing the validator
     */
    public Optional<LanguageValidator> getValidator(Language language) {
        Objects.requireNonNull(language, "language");
        return Optional.of(validators.computeIfAbsent(language, this::newValidator));
    }

    /**
     * Registers a custom validator for the given language, replacing any existing one.
     *
     * @param language  the language to register
     * @param validator the validator
     */
    public void register(Language language, LanguageValidator validator) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(validator, "validator");
        validators.put(language, validator);
    }

    /**
     * Returns the set of all supported languages.
     *
     * @return unmodifiable set of supported languages
     */
    public Set<Language> supportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    /**
     * Creates a new validator for the specified language (not cached).
     *
     * @param language the target language
     * @return a new LanguageValidator
     */
    public LanguageValidator create(Language language) {
        Objects.requireNonNull(language, "language");
        return newValidator(language);
    }

    /**
     * Returns a MixedContentValidator for HTML files containing embedded CSS and JavaScript.
     *
     * @return a mixed-content validator
     */
    public MixedContentValidator getMixedContentValidator() {
        return new MixedContentValidator();
    }

    /**
     * Returns the underlying BinaryManager, or null if not configured.
     *
     * @return the binary manager
     */
    public BinaryManager getBinaryManager() {
        return binaryManager;
    }

    private LanguageValidator newValidator(Language language) {
        return switch (language) {
            case JAVA       -> new JavaValidator(binaryManager);
            case JAVASCRIPT -> new JavaScriptValidator(binaryManager);
            case TYPESCRIPT -> new TypeScriptValidator(binaryManager);
            case PYTHON     -> new PythonValidator(binaryManager, processExecutor);
            case PHP        -> new PhpValidator(binaryManager, processExecutor);
            case HTML       -> new HtmlValidator(binaryManager);
            case CSS        -> new CssValidator(binaryManager);
        };
    }
}
