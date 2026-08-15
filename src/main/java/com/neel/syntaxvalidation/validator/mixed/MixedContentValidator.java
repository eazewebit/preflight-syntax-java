package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.css.CssValidator;
import com.neel.syntaxvalidation.validator.html.HtmlValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import com.neel.syntaxvalidation.validator.php.PhpValidator;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Validates HTML documents that may contain embedded CSS ({@code <style>}),
 * JavaScript ({@code <script>}), and PHP ({@code <?php ... ?>}) content.
 *
 * <p>This validator delegates to a {@link MixedContentSyntaxEngine} which
 * orchestrates validation across all embedded languages. The engine uses
 * the {@link HtmlContentExtractor} to identify embedded blocks and validates
 * each one independently before merging all errors into a single result.</p>
 *
 * <h2>Binary-first strategy</h2>
 * <p>When a {@link BinaryManager} is supplied (via
 * {@link #MixedContentValidator(BinaryManager)}), the engine is configured
 * with binary-backed validators for each language:
 * <ul>
 *   <li><b>HTML</b>: {@link HtmlValidator} backed by vnu.jar</li>
 *   <li><b>CSS</b>: {@link CssValidator} backed by stylelint</li>
 *   <li><b>JavaScript</b>: {@link JavaScriptValidator} backed by node</li>
 *   <li><b>PHP</b>: {@link PhpValidator} backed by php</li>
 * </ul>
 * Each validator tries its binary first, then falls back to a built-in
 * Java engine.</p>
 *
 * <p>When no BinaryManager is supplied, the engine uses pure-Java built-in
 * engines only.</p>
 *
 * @see MixedContentSyntaxEngine
 * @see HtmlContentExtractor
 */
public final class MixedContentValidator implements LanguageValidator {

    private static final Logger log = LoggerFactory.getLogger(MixedContentValidator.class);

    private final MixedContentSyntaxEngine engine;

    /**
     * Creates a new MixedContentValidator using pure-Java built-in engines
     * only (no binary-backed validators).
     */
    public MixedContentValidator() {
        this((MixedContentSyntaxEngine) null);
    }

    /**
     * Creates a new MixedContentValidator with the specified engine.
     * If the engine is {@code null}, a default engine with built-in validators
     * is created.
     *
     * @param engine the mixed content syntax engine (may be {@code null})
     */
    public MixedContentValidator(MixedContentSyntaxEngine engine) {
        this.engine = engine != null ? engine : new MixedContentSyntaxEngine();
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ [MIXED-CONTENT-VALIDATOR] Created with BUILT-IN ENGINES ONLY");
        log.info("║ HTML engine: HtmlSyntaxEngine (pure Java)");
        log.info("║ CSS engine:  CssSyntaxEngine (pure Java)");
        log.info("║ JS engine:   JavaScriptSyntaxEngine (pure Java)");
        log.info("║ PHP engine:  PhpSyntaxEngine (pure Java)");
        log.info("║ Reason: No BinaryManager provided");
        log.info("╚══════════════════════════════════════════════════════════════");
    }

    /**
     * Creates a new MixedContentValidator backed by a {@link BinaryManager}.
     * The engine is configured with binary-backed validators for HTML, CSS,
     * JavaScript, and PHP.
     *
     * @param binaryManager the binary manager for resolving validation binaries
     *                      (vnu.jar, stylelint, node, php), or {@code null} for
     *                      built-in engines only.
     */
    public MixedContentValidator(BinaryManager binaryManager) {
        if (binaryManager != null) {
            LanguageValidator htmlValidator = new HtmlValidator(binaryManager);
            LanguageValidator cssValidator = new CssValidator(binaryManager);
            LanguageValidator jsValidator = new JavaScriptValidator(binaryManager);
            LanguageValidator phpValidator = new PhpValidator(binaryManager, new ProcessExecutor());
            this.engine = new MixedContentSyntaxEngine(htmlValidator, cssValidator, jsValidator, phpValidator);

            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [MIXED-CONTENT-VALIDATOR] Created with BINARY-BACKED VALIDATORS");
            log.info("║ HTML validator:  {} (binary: vnu)", htmlValidator.getClass().getSimpleName());
            log.info("║ CSS validator:   {} (binary: stylelint)", cssValidator.getClass().getSimpleName());
            log.info("║ JS validator:    {} (binary: node)", jsValidator.getClass().getSimpleName());
            log.info("║ PHP validator:   {} (binary: php)", phpValidator.getClass().getSimpleName());
            log.info("║ BinaryManager:   {}", binaryManager);
            log.info("║ Strategy: Binary-first with built-in fallback");
            log.info("╚══════════════════════════════════════════════════════════════");
        } else {
            this.engine = new MixedContentSyntaxEngine();
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [MIXED-CONTENT-VALIDATOR] Created with BUILT-IN ENGINES ONLY");
            log.info("║ HTML engine: HtmlSyntaxEngine (pure Java)");
            log.info("║ CSS engine:  CssSyntaxEngine (pure Java)");
            log.info("║ JS engine:   JavaScriptSyntaxEngine (pure Java)");
            log.info("║ PHP engine:  PhpSyntaxEngine (pure Java)");
            log.info("║ Reason: BinaryManager is null");
            log.info("╚══════════════════════════════════════════════════════════════");
        }
    }

    /**
     * Returns the underlying {@link MixedContentSyntaxEngine}.
     *
     * @return the engine (never {@code null})
     */
    public MixedContentSyntaxEngine getEngine() {
        return engine;
    }

    /**
     * Validates the given HTML source (which may contain embedded CSS,
     * JavaScript, and PHP) and returns a merged result of all errors found.
     *
     * @param content the full HTML source to validate; {@code null} is treated
     *                as empty.
     * @return a {@link ValidationResult} containing errors from HTML, CSS,
     *         JavaScript, and PHP validation.
     */
    @Override
    public ValidationResult validate(String content) {
        String safeContent = content == null ? "" : content;

        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ [MIXED-CONTENT-VALIDATOR] Starting mixed content validation");
        log.info("║ Content length: {} chars", safeContent.length());
        log.info("║ HTML validator:  {}", engine.hasHtmlValidator() ? "BINARY-BACKED" : "BUILT-IN");
        log.info("║ CSS validator:   {}", engine.hasCssValidator() ? "BINARY-BACKED" : "BUILT-IN");
        log.info("║ JS validator:    {}", engine.hasJsValidator() ? "BINARY-BACKED" : "BUILT-IN");
        log.info("║ PHP validator:   {}", engine.hasPhpValidator() ? "BINARY-BACKED" : "BUILT-IN");
        log.info("╚══════════════════════════════════════════════════════════════");

        ValidationResult result = engine.validate(safeContent);

        log.info("[MIXED-CONTENT-VALIDATOR] Validation complete: valid={}, errors={}",
                result.isValid(), result.getErrors().size());

        return result;
    }

    /**
     * Validates the given HTML source with a specific file name.
     *
     * @param content  the full HTML source to validate.
     * @param fileName the file name to use for temp files.
     * @return a {@link ValidationResult}.
     */
    @Override
    public ValidationResult validate(String content, String fileName) {
        return validate(content);
    }

    @Override
    public Language getLanguage() {
        return Language.HTML;
    }
}
