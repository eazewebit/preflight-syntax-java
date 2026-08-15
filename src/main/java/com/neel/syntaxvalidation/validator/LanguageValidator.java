package com.neel.syntaxvalidation.validator;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;

/**
 * Strategy contract for validating the syntax of a piece of source code for a
 * specific language.
 *
 * <p>Each implementation encapsulates the tooling required for one language
 * (e.g. JavaScript via {@code node --check}). Implementations should be
 * stateless with respect to the content they validate so that they can be
 * safely shared and reused.
 *
 * <p>This interface follows the <em>Strategy</em> pattern: the
 * {@link ValidatorFactory} selects the appropriate implementation at runtime
 * based on the target {@link Language}.
 */
public interface LanguageValidator {

    /**
     * Validates the given source content.
     *
     * @param content the source text to check; {@code null} is treated as empty.
     * @return a structured {@link ValidationResult}.
     */
    ValidationResult validate(String content);

    /**
     * Validates the given source content, writing it to a temporary file
     * bearing the supplied {@code fileName}.
     *
     * <p>Preserving the original filename is critical for languages such as
     * Java where a {@code public class Foo} declaration must reside in a file
     * named {@code Foo.java}.
     *
     * @param content  the source text to check; {@code null} is treated as empty.
     * @param fileName the file name to use (e.g. {@code "Foo.java"}).
     * @return a structured {@link ValidationResult}.
     */
    default ValidationResult validate(String content, String fileName) {
        return validate(content);
    }

    /**
     * @return the language this validator is responsible for.
     */
    Language getLanguage();
}
