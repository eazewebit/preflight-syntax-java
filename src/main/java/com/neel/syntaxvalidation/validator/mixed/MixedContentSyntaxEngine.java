package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.css.CssSyntaxEngine;
import com.neel.syntaxvalidation.validator.html.HtmlSyntaxEngine;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine;
import com.neel.syntaxvalidation.validator.php.PhpSyntaxEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Syntax engine that performs comprehensive validation of HTML/PHP documents
 * containing embedded CSS ({@code &lt;style&gt;}), JavaScript ({@code &lt;script&gt;}),
 * and PHP ({@code &lt;?php … ?&gt;}) content.
 *
 * <p>This engine orchestrates validation across four dimensions:
 * <ol>
 *   <li><b>HTML structure</b> &mdash; delegates to an {@link HtmlSyntaxEngine} or
 *       a binary-backed {@link LanguageValidator} (e.g. vnu.jar) for
 *       structural HTML validation.</li>
 *   <li><b>Embedded CSS</b> &mdash; uses {@link HtmlContentExtractor} to locate
 *       {@code &lt;style&gt;} blocks, then validates each block via a CSS
 *       {@link LanguageValidator} (e.g. stylelint) or the built-in engine.</li>
 *   <li><b>Embedded JavaScript</b> &mdash; uses {@link HtmlContentExtractor} to
 *       locate {@code &lt;script&gt;} blocks, then validates each block via a
 *       JavaScript {@link LanguageValidator} (e.g. node --check) or the built-in
 *       engine.</li>
 *   <li><b>Embedded PHP</b> &mdash; uses {@link HtmlContentExtractor} to
 *       locate {@code &lt;?php … ?&gt;} blocks, then validates each block via a
 *       PHP {@link LanguageValidator} (e.g. php -l) or the built-in engine.</li>
 * </ol>
 *
 * <p>Error line numbers from embedded content are remapped to the correct
 * position in the original HTML document using
 * {@link ExtractedBlock#mapToOriginalLine(int)}.
 *
 * <h2>Binary-first strategy</h2>
 * <p>When binary-backed {@link LanguageValidator} instances are supplied (via
 * {@link #MixedContentSyntaxEngine(LanguageValidator, LanguageValidator, LanguageValidator, LanguageValidator)}),
 * the engine uses them for validation, which provides the full binary-first
 * strategy (e.g. vnu.jar for HTML, stylelint for CSS, node --check for JS,
 * php -l for PHP). When validators are not supplied, the engine falls back to
 * the pure-Java built-in engines.</p>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>The engine reuses existing, tested sub-engines rather than
 *       reimplementing validation logic.</li>
 *   <li>If a {@code &lt;script&gt;} tag has a {@code src} attribute (external
 *       script), its content is skipped since the content between the tags
 *       is typically empty.</li>
 *   <li>The engine is stateless and thread-safe.</li>
 * </ul>
 */
public final class MixedContentSyntaxEngine {

    private static final Logger log = LoggerFactory.getLogger(MixedContentSyntaxEngine.class);

    private final HtmlSyntaxEngine htmlEngine;
    private final CssSyntaxEngine cssEngine;
    private final JavaScriptSyntaxEngine jsEngine;
    private final PhpSyntaxEngine phpEngine;
    private final HtmlContentExtractor extractor;

    /** Binary-backed validators (may be null for pure-Java fallback). */
    private final LanguageValidator htmlValidator;
    private final LanguageValidator cssValidator;
    private final LanguageValidator jsValidator;
    private final LanguageValidator phpValidator;

    /**
     * Creates a new mixed-content engine using default singleton sub-engines
     * (pure-Java fallback only, no binary-backed validators).
     */
    public MixedContentSyntaxEngine() {
        this(HtmlSyntaxEngine.getInstance(),
             CssSyntaxEngine.getInstance(),
             JavaScriptSyntaxEngine.getInstance(),
             PhpSyntaxEngine.getInstance(),
             HtmlContentExtractor.getInstance());
    }

    /**
     * Creates a new mixed-content engine with custom sub-engines (pure-Java
     * fallback only, no binary-backed validators).
     *
     * @param htmlEngine the engine for HTML structural validation.
     * @param cssEngine  the engine for CSS syntax validation.
     * @param jsEngine   the engine for JavaScript syntax validation.
     * @param phpEngine  the engine for PHP syntax validation.
     * @param extractor  the extractor for embedded content blocks.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public MixedContentSyntaxEngine(HtmlSyntaxEngine htmlEngine,
                                     CssSyntaxEngine cssEngine,
                                     JavaScriptSyntaxEngine jsEngine,
                                     PhpSyntaxEngine phpEngine,
                                     HtmlContentExtractor extractor) {
        this.htmlEngine = htmlEngine != null ? htmlEngine : HtmlSyntaxEngine.getInstance();
        this.cssEngine = cssEngine != null ? cssEngine : CssSyntaxEngine.getInstance();
        this.jsEngine = jsEngine != null ? jsEngine : JavaScriptSyntaxEngine.getInstance();
        this.phpEngine = phpEngine != null ? phpEngine : PhpSyntaxEngine.getInstance();
        this.extractor = extractor != null ? extractor : HtmlContentExtractor.getInstance();
        this.htmlValidator = null;
        this.cssValidator = null;
        this.jsValidator = null;
        this.phpValidator = null;
    }

    /**
     * Creates a new mixed-content engine backed by binary-first
     * {@link LanguageValidator} instances. When a validator is non-null, it is
     * used for that language's validation (which internally tries a binary
     * first, then falls back to a built-in engine). When a validator is null,
     * the corresponding built-in engine is used directly.
     *
     * @param htmlValidator binary-backed HTML validator (e.g. {@code HtmlValidator} backed by vnu.jar), or null.
     * @param cssValidator  binary-backed CSS validator (e.g. {@code CssValidator} backed by stylelint), or null.
     * @param jsValidator   binary-backed JS validator (e.g. {@code JavaScriptValidator} backed by node), or null.
     * @param phpValidator  binary-backed PHP validator (e.g. {@code PhpValidator} backed by php), or null.
     */
    public MixedContentSyntaxEngine(LanguageValidator htmlValidator,
                                     LanguageValidator cssValidator,
                                     LanguageValidator jsValidator,
                                     LanguageValidator phpValidator) {
        this.htmlEngine = HtmlSyntaxEngine.getInstance();
        this.cssEngine = CssSyntaxEngine.getInstance();
        this.jsEngine = JavaScriptSyntaxEngine.getInstance();
        this.phpEngine = PhpSyntaxEngine.getInstance();
        this.extractor = HtmlContentExtractor.getInstance();
        this.htmlValidator = htmlValidator;
        this.cssValidator = cssValidator;
        this.jsValidator = jsValidator;
        this.phpValidator = phpValidator;
    }

    /**
     * Validates the given HTML/PHP source, including any embedded CSS,
     * JavaScript, and PHP.
     *
     * <p>The validation proceeds in stages:
     * <ol>
     *   <li>Extract embedded {@code &lt;style&gt;}, {@code &lt;script&gt;}, and
     *       {@code &lt;?php … ?&gt;} blocks.</li>
     *   <li>Validate the full HTML structure (binary-first when validator available).</li>
     *   <li>Validate each extracted CSS block (binary-first when validator available).</li>
     *   <li>Validate each extracted JavaScript block (binary-first when validator available).</li>
     *   <li>Validate each extracted PHP block (binary-first when validator available).</li>
     *   <li>Merge all errors, remapping embedded-content line numbers to the
     *       original HTML positions.</li>
     * </ol>
     *
     * @param htmlSource the full HTML/PHP source code to validate.
     * @return a {@link ValidationResult} containing all errors found across
     *         HTML, CSS, JavaScript, and PHP validation.
     * @throws NullPointerException if {@code htmlSource} is {@code null}.
     */
    public ValidationResult validate(String htmlSource) {
        if (htmlSource == null) {
            throw new NullPointerException("htmlSource must not be null");
        }

        // Stage 1: Extract embedded content blocks
        List<ExtractedBlock> blocks = extractor.extract(htmlSource);
        log.debug("[MIXED-CONTENT] Extracted {} embedded content block(s)", blocks.size());

        // Stage 2: Validate the full HTML structure
        ValidationResult htmlResult = validateHtml(htmlSource);

        // Stage 3: Validate embedded CSS blocks
        List<ValidationResult> cssResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.CSS && !block.isEmpty()) {
                ValidationResult cssResult = validateCss(block.content());
                if (!cssResult.isValid()) {
                    cssResults.add(remapLineNumbers(cssResult, block));
                }
            }
        }

        // Stage 4: Validate embedded JavaScript blocks
        List<ValidationResult> jsResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.JAVASCRIPT && !block.isEmpty()) {
                ValidationResult jsResult = validateJavaScript(block.content());
                if (!jsResult.isValid()) {
                    jsResults.add(remapLineNumbers(jsResult, block));
                }
            }
        }

        // Stage 5: Validate embedded PHP blocks
        List<ValidationResult> phpResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.PHP && !block.isEmpty()) {
                ValidationResult phpResult = validatePhp(block.content());
                if (!phpResult.isValid()) {
                    phpResults.add(remapLineNumbers(phpResult, block));
                }
            }
        }

        // Stage 6: Merge all errors
        return mergeResults(htmlResult, cssResults, jsResults, phpResults);
    }

    /**
     * Validates HTML content using the binary-backed validator when available,
     * otherwise falls back to the built-in {@link HtmlSyntaxEngine}.
     */
    private ValidationResult validateHtml(String source) {
        if (htmlValidator != null) {
            log.info("╔══════════════════════════════════════════════════════════════");
            log.info("║ [MIXED-CONTENT] HTML validation: Using BINARY-BACKED VALIDATOR");
            log.info("║ Validator: {}", htmlValidator.getClass().getSimpleName());
            log.info("║ Strategy: Binary-first (vnu.jar) with built-in fallback");
            log.info("╚══════════════════════════════════════════════════════════════");
            ValidationResult result = htmlValidator.validate(source);
            log.debug("[MIXED-CONTENT] HTML validator result: valid={}", result.isValid());
            return result;
        }
        log.info("╔══════════════════════════════════════════════════════════════");
        log.info("║ [MIXED-CONTENT] HTML validation: Using BUILT-IN ENGINE ONLY");
        log.info("║ Engine: {}", htmlEngine.getClass().getSimpleName());
        log.info("║ Reason: No binary-backed HTML validator configured");
        log.info("╚══════════════════════════════════════════════════════════════");
        return htmlEngine.validate(source);
    }

    /**
     * Validates CSS content using the binary-backed validator when available,
     * otherwise falls back to the built-in {@link CssSyntaxEngine}.
     */
    private ValidationResult validateCss(String source) {
        if (cssValidator != null) {
            log.info("[MIXED-CONTENT] CSS block validation: Using BINARY-BACKED VALIDATOR ({})",
                    cssValidator.getClass().getSimpleName());
            return cssValidator.validate(source);
        }
        log.debug("[MIXED-CONTENT] CSS block validation: Using built-in engine ({})",
                cssEngine.getClass().getSimpleName());
        return cssEngine.validate(source);
    }

    /**
     * Validates JavaScript content using the binary-backed validator when
     * available, otherwise falls back to the built-in {@link JavaScriptSyntaxEngine}.
     */
    private ValidationResult validateJavaScript(String source) {
        if (jsValidator != null) {
            log.info("[MIXED-CONTENT] JavaScript block validation: Using BINARY-BACKED VALIDATOR ({})",
                    jsValidator.getClass().getSimpleName());
            return jsValidator.validate(source);
        }
        log.debug("[MIXED-CONTENT] JavaScript block validation: Using built-in engine ({})",
                jsEngine.getClass().getSimpleName());
        return jsEngine.validate(source);
    }

    /**
     * Validates PHP content using the binary-backed validator when available,
     * otherwise falls back to the built-in {@link PhpSyntaxEngine}.
     */
    private ValidationResult validatePhp(String source) {
        if (phpValidator != null) {
            log.info("[MIXED-CONTENT] PHP block validation: Using BINARY-BACKED VALIDATOR ({})",
                    phpValidator.getClass().getSimpleName());
            return phpValidator.validate(source);
        }
        log.debug("[MIXED-CONTENT] PHP block validation: Using built-in engine ({})",
                phpEngine.getClass().getSimpleName());
        return phpEngine.validate(source);
    }

    /**
     * Returns whether a binary-backed HTML validator is configured.
     */
    public boolean hasHtmlValidator() {
        return htmlValidator != null;
    }

    /**
     * Returns whether a binary-backed CSS validator is configured.
     */
    public boolean hasCssValidator() {
        return cssValidator != null;
    }

    /**
     * Returns whether a binary-backed JS validator is configured.
     */
    public boolean hasJsValidator() {
        return jsValidator != null;
    }

    /**
     * Returns whether a binary-backed PHP validator is configured.
     */
    public boolean hasPhpValidator() {
        return phpValidator != null;
    }

    /**
     * Remaps the line numbers in a validation result to the original HTML
     * document positions.
     *
     * @param result the validation result with content-local line numbers.
     * @param block  the extracted block providing the line offset mapping.
     * @return a new {@link ValidationResult} with remapped line numbers.
     */
    private ValidationResult remapLineNumbers(ValidationResult result, ExtractedBlock block) {
        List<ValidationError> remapped = result.getErrors().stream()
                .map(error -> new ValidationError(
                        block.mapToOriginalLine(error.getLine()),
                        error.getColumn(),
                        prependLanguageContext(error, block),
                        error.getToolOutput()
                ))
                .collect(Collectors.toList());

        return ValidationResult.invalid(
                result.getMessage(),
                remapped
        );
    }

    /**
     * Prepends language context information to an error message so that the
     * consumer knows which embedded block the error originated from.
     *
     * @param error the original validation error.
     * @param block the extracted block.
     * @return a message prefixed with the language context.
     */
    private String prependLanguageContext(ValidationError error, ExtractedBlock block) {
        String langLabel;
        String tagName;
        switch (block.language()) {
            case CSS:
                langLabel = "CSS";
                tagName = "style";
                break;
            case JAVASCRIPT:
                langLabel = "JavaScript";
                tagName = "script";
                break;
            case PHP:
                langLabel = "PHP";
                tagName = "php";
                break;
            default:
                langLabel = block.language().toString();
                tagName = "unknown";
                break;
        }
        return "[" + langLabel + " in <" + tagName + ">] "
                + error.getMessage();
    }

    /**
     * Merges HTML, CSS, JavaScript, and PHP validation results into a single
     * result.
     *
     * @param htmlResult  the HTML validation result.
     * @param cssResults  the CSS validation results (only invalid ones).
     * @param jsResults   the JavaScript validation results (only invalid ones).
     * @param phpResults  the PHP validation results (only invalid ones).
     * @return a combined {@link ValidationResult}.
     */
    private ValidationResult mergeResults(ValidationResult htmlResult,
                                           List<ValidationResult> cssResults,
                                           List<ValidationResult> jsResults,
                                           List<ValidationResult> phpResults) {
        boolean allValid = htmlResult.isValid() && cssResults.isEmpty()
                && jsResults.isEmpty() && phpResults.isEmpty();

        if (allValid) {
            return ValidationResult.valid(
                    "Mixed HTML content (with embedded CSS, JavaScript, and PHP) is syntactically valid.");
        }

        List<ValidationError> allErrors = new ArrayList<>();

        // Add HTML errors
        if (!htmlResult.isValid()) {
            allErrors.addAll(htmlResult.getErrors());
        }

        // Add CSS errors
        for (ValidationResult cssResult : cssResults) {
            allErrors.addAll(cssResult.getErrors());
        }

        // Add JavaScript errors
        for (ValidationResult jsResult : jsResults) {
            allErrors.addAll(jsResult.getErrors());
        }

        // Add PHP errors
        for (ValidationResult phpResult : phpResults) {
            allErrors.addAll(phpResult.getErrors());
        }

        // Sort by line number, then by column
        allErrors.sort((a, b) -> {
            int lineCmp = Integer.compare(a.getLine(), b.getLine());
            return lineCmp != 0 ? lineCmp : Integer.compare(a.getColumn(), b.getColumn());
        });

        String message = buildSummaryMessage(allErrors.size(), cssResults.size(), jsResults.size(),
                                              phpResults.size(), !htmlResult.isValid());

        return ValidationResult.invalid(message, allErrors);
    }

    /**
     * Builds a human-readable summary message for the merged result.
     */
    private String buildSummaryMessage(int totalErrors, int cssBlockCount, int jsBlockCount,
                                        int phpBlockCount, boolean hasHtmlErrors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mixed content validation found ").append(totalErrors).append(" error(s)");

        List<String> parts = new ArrayList<>();
        if (hasHtmlErrors) {
            parts.add("HTML");
        }
        if (cssBlockCount > 0) {
            parts.add(cssBlockCount + " CSS block(s)");
        }
        if (jsBlockCount > 0) {
            parts.add(jsBlockCount + " JavaScript block(s)");
        }
        if (phpBlockCount > 0) {
            parts.add(phpBlockCount + " PHP block(s)");
        }

        if (!parts.isEmpty()) {
            sb.append(" in: ").append(String.join(", ", parts));
        }

        sb.append(".");
        return sb.toString();
    }
}
