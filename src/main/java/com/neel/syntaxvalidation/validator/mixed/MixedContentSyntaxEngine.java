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
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
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
 * <h2>Pure PHP detection</h2>
 * <p>When the source starts with {@code <?php} or {@code <?=} and contains no
 * HTML document structure markers ({@code <!DOCTYPE}, {@code <html},
 * {@code <head}, {@code <body}), the engine skips HTML structure validation.
 * This prevents the HTML validator (e.g. vnu.jar) from incorrectly reporting
 * XML processing instruction errors on standalone PHP files.</p>
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

    // ====================================================================
    //  Main validation entry point
    // ====================================================================

    /**
     * Validates the given HTML/PHP source, including any embedded CSS,
     * JavaScript, and PHP.
     *
     * <p>The validation proceeds in stages:
     * <ol>
     *   <li>Extract embedded {@code &lt;style&gt;}, {@code &lt;script&gt;}, and
     *       {@code &lt;?php … ?&gt;} blocks.</li>
     *   <li>If the source is pure PHP (no HTML structure), skip HTML
     *       validation and validate only PHP blocks.</li>
     *   <li>Otherwise, validate the full HTML structure, CSS blocks,
     *       JavaScript blocks, and PHP blocks.</li>
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

        log.info("════════════════════════════════════════════════════════════════");
        log.info("║ [MIXED-CONTENT] Starting mixed content validation");
        log.info("║ Content length: {} chars", htmlSource.length());
        log.info("║ Has PHP validator: {}", hasPhpValidator());
        log.info("║ Is PHP mixed content: {}", isPhpMixedContent(htmlSource));
        log.info("║ Is pure PHP content: {}", isPurePhpContent(htmlSource));
        log.info("════════════════════════════════════════════════════════════════");

        // Extract all embedded blocks first (needed for line remapping)
        List<ExtractedBlock> blocks = extractor.extract(htmlSource);

        // ──────────────────────────────────────────────────────────────
        //  STAGE 1: PHP-First Validation for PHP Mixed Content
        // ──────────────────────────────────────────────────────────────
        //  When content is PHP mixed (PHP tags + HTML structure),
        //  validate the ENTIRE file with php -l first. If PHP syntax
        //  is valid, skip VNU/stylelint/node entirely because:
        //    1. php -l correctly parses the full file
        //    2. PHP blocks stripped from HTML/CSS/JS create false errors
        //    3. PHP generates HTML at runtime, not at parse time
        // ──────────────────────────────────────────────────────────────

        if (hasPhpValidator() && isPhpMixedContent(htmlSource)) {
            log.info("║ [PHP-FIRST] Detected PHP mixed content - validating with PHP binary first");

            ValidationResult phpFullResult = validatePhp(htmlSource);

            if (phpFullResult.isValid()) {
                log.info("║ [PHP-FIRST] ✓ PHP binary validation PASSED - SKIPPING VNU/stylelint/node");
                log.info("║ [PHP-FIRST] PHP syntax is valid; HTML/CSS/JS validation skipped");
                log.info("════════════════════════════════════════════════════════════════");

                return ValidationResult.valid(
                        "PHP mixed content is syntactically valid. "
                        + "PHP binary (php -l) confirmed valid PHP syntax. "
                        + "HTML/CSS/JS validation skipped (PHP generates valid HTML at runtime).");
            } else {
                log.warn("║ [PHP-FIRST] ✗ PHP binary validation FAILED - collecting errors");
                log.warn("║ [PHP-FIRST] PHP errors: {}", phpFullResult.getErrors().size());
                log.info("════════════════════════════════════════════════════════════════");

                // PHP syntax errors in the file - report them and skip sub-validators
                // since the PHP code itself is invalid
                return phpFullResult;
            }
        }

        // ──────────────────────────────────────────────────────────────
        //  STAGE 2: Standard Validation (Pure HTML or No PHP Validator)
        // ──────────────────────────────────────────────────────────────

        if (isPurePhpContent(htmlSource)) {
            // Pure PHP: validate only PHP blocks
            log.info("[MIXED-CONTENT] Pure PHP file detected - validating PHP blocks only");

            List<ValidationResult> phpResults = new ArrayList<>();
            for (ExtractedBlock block : blocks) {
                if (block.language() == Language.PHP && !block.isEmpty()) {
                    ValidationResult phpResult = validatePhp(block.content());
                    if (!phpResult.isValid()) {
                        phpResults.add(remapLineNumbers(phpResult, block));
                    }
                }
            }
            return mergeResults(ValidationResult.valid("Validation passed"), Collections.emptyList(),
                    Collections.emptyList(), phpResults);
        }

        // Standard HTML/PHP mixed validation (no PHP binary available)
        log.info("[MIXED-CONTENT] Standard validation - no PHP binary, using per-block validation");

        // Stage 3: Validate full HTML structure (after sanitizing PHP tags)
        String sanitizedHtml = sanitizePhpTags(htmlSource);
        ValidationResult htmlResult = validateHtml(sanitizedHtml);

        // Stage 4: Validate CSS blocks
        List<ValidationResult> cssResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.CSS && !block.isEmpty()) {
                String sanitizedCss = sanitizePhpTagsForCss(block.content());
                ValidationResult cssResult = validateCss(sanitizedCss);
                if (!cssResult.isValid()) {
                    cssResults.add(remapLineNumbers(cssResult, block));
                }
            }
        }

        // Stage 5: Validate JavaScript blocks
        List<ValidationResult> jsResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.JAVASCRIPT && !block.isEmpty()) {
                String sanitizedJs = sanitizePhpTagsForJs(block.content());
                ValidationResult jsResult = validateJavaScript(sanitizedJs);
                if (!jsResult.isValid()) {
                    jsResults.add(remapLineNumbers(jsResult, block));
                }
            }
        }

        // Stage 6: Validate PHP blocks
        List<ValidationResult> phpResults = new ArrayList<>();
        for (ExtractedBlock block : blocks) {
            if (block.language() == Language.PHP && !block.isEmpty()) {
                ValidationResult phpResult = validatePhp(block.content());
                if (!phpResult.isValid()) {
                    phpResults.add(remapLineNumbers(phpResult, block));
                }
            }
        }

        // Stage 7: Merge all errors
        return mergeResults(htmlResult, cssResults, jsResults, phpResults);
    }
    /**
     * Validates the full source as HTML using the HTML validator if available,
     * Validates the full source as HTML using the HTML validator if available,
     * otherwise falls back to the built-in {@link HtmlSyntaxEngine}.
     */
    private ValidationResult validateHtml(String source) {
        if (htmlValidator != null) {
            log.info("[MIXED-CONTENT] HTML validation: Using BINARY-BACKED VALIDATOR ({})",
                    htmlValidator.getClass().getSimpleName());
            return htmlValidator.validate(source);
        }
        log.debug("[MIXED-CONTENT] HTML validation: Using built-in engine ({})",
                htmlEngine.getClass().getSimpleName());
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
     * available, otherwise falls back to the built-in
     * {@link JavaScriptSyntaxEngine}.
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

    // ====================================================================
    //  Query methods
    // ====================================================================

    /** Returns whether a binary-backed HTML validator is configured. */
    public boolean hasHtmlValidator() { return htmlValidator != null; }

    /** Returns whether a binary-backed CSS validator is configured. */
    public boolean hasCssValidator() { return cssValidator != null; }

    /** Returns whether a binary-backed JS validator is configured. */
    public boolean hasJsValidator() { return jsValidator != null; }

    /** Returns whether a binary-backed PHP validator is configured. */
    public boolean hasPhpValidator() { return phpValidator != null; }

    // ====================================================================
    //  Error remapping and merging
    // ====================================================================

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

        return ValidationResult.invalid(result.getMessage(), remapped);
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

        if (!htmlResult.isValid()) {
            allErrors.addAll(htmlResult.getErrors());
        }

        for (ValidationResult cssResult : cssResults) {
            allErrors.addAll(cssResult.getErrors());
        }

        for (ValidationResult jsResult : jsResults) {
            allErrors.addAll(jsResult.getErrors());
        }

        for (ValidationResult phpResult : phpResults) {
            allErrors.addAll(phpResult.getErrors());
        }

        // Sort by line number, then by column
        allErrors.sort((a, b) -> {
            int lineCmp = Integer.compare(a.getLine(), b.getLine());
            return lineCmp != 0 ? lineCmp : Integer.compare(a.getColumn(), b.getColumn());
        });

        String message = buildSummaryMessage(allErrors.size(), cssResults.size(),
                jsResults.size(), phpResults.size(), !htmlResult.isValid());

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
            parts.add("CSS (" + cssBlockCount + ")");
        }
        if (jsBlockCount > 0) {
            parts.add("JS (" + jsBlockCount + ")");
        }
        if (phpBlockCount > 0) {
            parts.add("PHP (" + phpBlockCount + ")");
        }

        if (!parts.isEmpty()) {
            sb.append(" in: ").append(String.join(", ", parts));
        }

        sb.append(".");
        return sb.toString();
    }
    // ====================================================================
    //  PHP tag sanitization
    // ====================================================================

    /**
     * Removes all PHP tags from the given source so that non-PHP validators
     * (vnu.jar for HTML, stylelint for CSS, node --check for JavaScript)
     * do not encounter {@code <?php} or {@code ?>} sequences that they
     * cannot parse.
     *
     * <p>This method replaces:
     * <ul>
     *   <li>Complete PHP blocks ({@code <?php ... ?>}, {@code <?= ... ?>},
     *       {@code <? ... ?>}) with an empty string.</li>
     *   <li>PHP open-to-EOF blocks ({@code <?php ...} with no closing
     *       {@code ?>}) with an empty string.</li>
     * </ul>
     *
     * <p><b>Important:</b> This method must NOT be called on PHP block
     * content that has already been extracted by
     * {@link HtmlContentExtractor#extract(String)}, because the extractor
     * already strips the {@code <?php} and {@code ?>} tags from PHP blocks.
     *
     * @param source the source code potentially containing PHP tags.
     * @return the source with all PHP tag sequences removed.
     */
    /**
     * Sanitizes PHP tags from source code by replacing them with context-appropriate
     * placeholders. This prevents false validation errors when PHP code is stripped
     * from HTML/CSS/JS content.
     *
     * <p>Context-aware placeholders used:</p>
     * <ul>
     *   <li>HTML attributes: "php" (valid non-empty attribute value)</li>
     *   <li>CSS values: "inherit" (valid CSS keyword)</li>
     *   <li>JavaScript expressions: "null" (valid JS literal)</li>
     * </ul>
     *
     * @param source the source code potentially containing PHP tags.
     * @return the source with PHP tags replaced by context-appropriate placeholders.
     */
    private String sanitizePhpTags(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        // Replace complete PHP blocks with "php" placeholder
        // This handles: <?php ... ?>, <?= ... ?>
        String result = COMPLETE_PHP_BLOCK_PATTERN.matcher(source).replaceAll("php");
        // Replace PHP open-to-EOF with "php" (unclosed PHP blocks)
        result = PHP_OPEN_TO_EOF_PATTERN.matcher(result).replaceAll("php");
        return result;
    }

    /**
     * Sanitizes PHP tags from CSS content by replacing with valid CSS placeholders.
     * Uses "inherit" which is a valid CSS keyword for most properties.
     *
     * @param cssContent CSS content potentially containing PHP tags.
     * @return CSS content with PHP tags replaced by valid CSS values.
     */
    private String sanitizePhpTagsForCss(String cssContent) {
        if (cssContent == null || cssContent.isEmpty()) {
            return cssContent;
        }
        String result = COMPLETE_PHP_BLOCK_PATTERN.matcher(cssContent).replaceAll("inherit");
        result = PHP_OPEN_TO_EOF_PATTERN.matcher(result).replaceAll("inherit");
        return result;
    }

    /**
     * Sanitizes PHP tags from JavaScript content by replacing with valid JS placeholders.
     * Uses "null" which is a valid JavaScript literal expression.
     *
     * @param jsContent JavaScript content potentially containing PHP tags.
     * @return JavaScript content with PHP tags replaced by valid JS expressions.
     */
    private String sanitizePhpTagsForJs(String jsContent) {
        if (jsContent == null || jsContent.isEmpty()) {
            return jsContent;
        }
        String result = COMPLETE_PHP_BLOCK_PATTERN.matcher(jsContent).replaceAll("null");
        result = PHP_OPEN_TO_EOF_PATTERN.matcher(result).replaceAll("null");
        return result;
    }
    /**
     * Detects whether the given source is a standalone pure PHP file
     * (not mixed with HTML).
     *
     * <p>A source is considered "pure PHP" when:
     * <ul>
     *   <li>It starts with {@code <?php} or {@code <?=} (after optional
     *       whitespace or BOM).</li>
     *   <li>It does NOT contain HTML document structure markers outside PHP
     *       blocks: {@code <!DOCTYPE}, {@code <html}, {@code <head},
     *       {@code <body}.</li>
     * </ul>
     *
     * <p>This detection prevents the HTML validator (e.g. vnu.jar) from
     * incorrectly reporting errors on PHP-only files.
     *
     * @param source the source to check.
     * @return {@code true} if the source is a standalone PHP file.
     */
    private boolean isPurePhpContent(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }

        // Strip BOM and leading whitespace
        String trimmed = source.strip();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).strip();
        }

        String lower = trimmed.toLowerCase();

        // Must start with PHP opening tag
        if (!(lower.startsWith("<?php") || lower.startsWith("<?="))) {
            return false;
        }

        // Remove all complete PHP blocks (<?php ... ?> and <?= ... ?>)
        // to check if there is any HTML content outside them
        String remainder = COMPLETE_PHP_BLOCK_PATTERN.matcher(trimmed).replaceAll("");

        // Also remove PHP blocks without closing ?> (extends to EOF)
        remainder = PHP_OPEN_TO_EOF_PATTERN.matcher(remainder).replaceAll("");

        // If remainder is blank, the file is pure PHP
        return remainder.isBlank();
    }

    /**
     * Determines if the source is PHP mixed content (PHP tags embedded in HTML structure).
     *
     * <p>PHP mixed content is detected when BOTH conditions are met:</p>
     * <ul>
     *   <li>Contains at least one PHP opening tag ({@code <?php}, {@code <?=}, or {@code <?})</li>
     *   <li>Contains HTML structure markers ({@code <html}, {@code <head}, {@code <body},
     *       {@code <!DOCTYPE}, or {@code <div})</li>
     * </ul>
     *
     * <p>This distinguishes PHP mixed content from:</p>
     * <ul>
     *   <li>Pure PHP files (PHP tags but no HTML structure)</li>
     *   <li>Pure HTML files (HTML structure but no PHP tags)</li>
     * </ul>
     *
     * @param source the source code to check.
     * @return true if the source contains both PHP tags and HTML structure.
     */
    private boolean isPhpMixedContent(String source) {
        if (source == null || source.isEmpty()) {
            return false;
        }

        boolean hasPhpTags = COMPLETE_PHP_BLOCK_PATTERN.matcher(source).find()
                || source.contains("<?php") || source.contains("<?=");
        boolean hasHtmlStructure = source.regionMatches(true, 0, "<!DOCTYPE", 0, 9)
                || source.contains("<html") || source.contains("<HTML")
                || source.contains("<head") || source.contains("<HEAD")
                || source.contains("<body") || source.contains("<BODY")
                || source.contains("<div") || source.contains("<DIV");

        return hasPhpTags && hasHtmlStructure;
    }

    // ====================================================================
    //  Pure PHP detection patterns
    // ====================================================================
    //  Pure PHP detection patterns
    // ====================================================================

    /**
     * Matches a complete PHP block: {@code <?php ... ?>} or {@code <?= ... ?>}
     * or {@code <? ... ?>} (short open tag, but not {@code <?xml}).
     */
    private static final Pattern COMPLETE_PHP_BLOCK_PATTERN = Pattern.compile(
            "<\\?php[\\s\\S]*?\\?>|<\\?=[\\s\\S]*?\\?>|<\\?(?![xX][mM][lL])[\\s\\S]*?\\?>",
            Pattern.DOTALL
    );

    /**
     * Matches a PHP opening tag that extends to the end of the file
     * (no closing {@code ?>}). This handles PHP files without a closing tag.
     */
    private static final Pattern PHP_OPEN_TO_EOF_PATTERN = Pattern.compile(
            "<\\?php[\\s\\S]+$|<\\?=[\\s\\S]+$|<\\?(?![xX][mM][lL])[\\s\\S]+$",
            Pattern.DOTALL
    );
}
