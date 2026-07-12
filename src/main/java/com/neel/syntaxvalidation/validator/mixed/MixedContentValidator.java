package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;

import java.util.Optional;

/**
 * A {@link LanguageValidator} that performs comprehensive syntax validation of
 * HTML documents containing embedded CSS ({@code &lt;style&gt;}) and JavaScript
 * ({@code &lt;script&gt;}) content.
 *
 * <p>This validator orchestrates three sub-validators:
 * <ol>
 *   <li><b>HTML structure</b> &mdash; validates the HTML document structure
 *       using the Nu Html Checker (vnu.jar) when available, with a pure-Java
 *       fallback engine.</li>
 *   <li><b>Embedded CSS</b> &mdash; extracts content from {@code &lt;style&gt;}
 *       tags and validates it using the CSS syntax engine.</li>
 *   <li><b>Embedded JavaScript</b> &mdash; extracts content from
 *       {@code &lt;script&gt;} tags and validates it using the JavaScript
 *       syntax engine.</li>
 * </ol>
 *
 * <p>Error line numbers are remapped from the extracted content back to the
 * original HTML document positions, providing a unified error report.
 *
 * <p><b>Thread-safety.</b> This class is thread-safe: all collaborators are
 * either stateless or thread-safe.
 */
public class MixedContentValidator implements LanguageValidator {

    private final MixedContentSyntaxEngine mixedEngine;

    /**
     * Creates a new mixed-content validator using default sub-engines.
     */
    public MixedContentValidator() {
        this(new MixedContentSyntaxEngine());
    }

    /**
     * Creates a new mixed-content validator with a custom engine.
     *
     * @param engine the mixed-content syntax engine to use.
     * @throws NullPointerException if {@code engine} is {@code null}.
     */
    public MixedContentValidator(MixedContentSyntaxEngine engine) {
        this.mixedEngine = engine != null ? engine : new MixedContentSyntaxEngine();
    }

    @Override
    public Language getLanguage() {
        return Language.HTML;
    }

    /**
     * Validates the given HTML source, including any embedded CSS and
     * JavaScript content.
     *
     * @param source the full HTML source code to validate.
     * @return a {@link ValidationResult} containing all errors found across
     *         HTML, CSS, and JavaScript validation.
     * @throws NullPointerException if {@code source} is {@code null}.
     */
    @Override
    public ValidationResult validate(String source) {
        if (source == null) {
            throw new NullPointerException("source must not be null");
        }
        return mixedEngine.validate(source);
    }

    /**
     * Returns the underlying mixed-content syntax engine.
     *
     * @return the engine used by this validator.
     */
    public MixedContentSyntaxEngine getEngine() {
        return mixedEngine;
    }
}
