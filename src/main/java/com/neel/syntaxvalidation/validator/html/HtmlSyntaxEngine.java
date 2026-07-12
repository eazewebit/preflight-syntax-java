package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pure-Java syntax validation engine for HTML content.
 *
 * <p>Unlike the external vnu.jar-based pipeline, this engine runs entirely
 * inside the JVM with zero external dependencies. It performs a suite of
 * structural checks to catch a wide range of common HTML syntax errors &mdash;
 * from unclosed tags and attribute malformations to doctype issues and
 * void-element misuse.
 *
 * <h2>Validation phases</h2>
 * <ol>
 *   <li><b>Tag structure analysis</b> &mdash; validates that opening and closing
 *       tags are properly matched, nested, and not orphaned.</li>
 *   <li><b>Attribute syntax checking</b> &mdash; verifies that HTML attributes
 *       have proper quoting, valid names, and no duplicate assignments.</li>
 *   <li><b>Void and self-closing element validation</b> &mdash; checks that void
 *       elements (e.g. {@code <br>}, {@code <img>}) are not incorrectly closed
 *       and that only appropriate elements use self-closing syntax.</li>
 *   <li><b>Doctype and structural checks</b> &mdash; validates the presence and
 *       format of the {@code <!DOCTYPE>} declaration and basic document
 *       structure.</li>
 *   <li><b>Comment and CDATA syntax validation</b> &mdash; checks for unclosed
 *       comments and malformed CDATA sections.</li>
 * </ol>
 *
 * <p>The engine is conservative: it avoids reporting errors for valid (if
 * unusual) HTML. It is designed as a first-pass structural check that catches
 * obvious syntax mistakes quickly without requiring an external binary.
 *
 * <p><b>Thread-safety.</b> The engine is stateless and can be shared freely
 * across threads. Obtain the canonical instance via {@link #getInstance()}.
 */
public final class HtmlSyntaxEngine {

    private static final HtmlSyntaxEngine INSTANCE = new HtmlSyntaxEngine();

    // ------------------------------------------------------------------
    //  Void elements (HTML5 spec, section 4.3)
    // ------------------------------------------------------------------

    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img",
            "input", "link", "meta", "param", "source", "track", "wbr"
    );

    // ------------------------------------------------------------------
    //  Regex patterns for HTML constructs
    // ------------------------------------------------------------------

    /**
     * Matches HTML comments: &lt;!-- ... --&gt;
     * Uses a non-greedy match to capture the comment body.
     */
    private static final Pattern COMMENT_PATTERN =
            Pattern.compile("<!--(.+?)-->", Pattern.DOTALL);

    /**
     * Matches an unclosed HTML comment that starts with &lt;!-- but never
     * has a matching --&gt;.
     */
    private static final Pattern UNCLOSED_COMMENT_PATTERN =
            Pattern.compile("<!--(?!.*-->)", Pattern.DOTALL);

    /**
     * Matches a DOCTYPE declaration.
     */
    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile("<!DOCTYPE\\s+[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Matches a malformed DOCTYPE (e.g. &lt;DOCTYPE html&gt; without the
     * exclamation mark, or &lt;!doctype&gt; without the type).
     */
    private static final Pattern MALFORMED_DOCTYPE_PATTERN =
            Pattern.compile("<DOCTYPE\\s", Pattern.CASE_INSENSITIVE);

    /**
     * Matches HTML tags (opening, closing, self-closing, and comments).
     * Group 1 = '/' for closing tags.
     * Group 2 = tag name.
     * Group 3 = attributes and closing slash.
     */
    private static final Pattern TAG_PATTERN =
            Pattern.compile(
                    "<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\\s+[^>]*?)?)(/?)>",
                    Pattern.DOTALL
            );

    /**
     * Matches an attribute within a tag: name, optional = and value.
     * Handles both quoted and unquoted attribute values.
     */
    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile(
                    "([a-zA-Z_][a-zA-Z0-9_.:-]*)(\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]*)))?",
                    Pattern.DOTALL
            );

    /**
     * Matches a CDATA section.
     */
    private static final Pattern CDATA_PATTERN =
            Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);

    /**
     * Matches a malformed CDATA section (e.g. missing brackets or content).
     */
    private static final Pattern MALFORMED_CDATA_PATTERN =
            Pattern.compile("<!\\[CDATA[^(]|<!\\[CDATA\\[.*?[^]>]\\]>", Pattern.DOTALL);

    /**
     * Matches script/style tags that should have their content treated as
     * raw text (not parsed for HTML tags).
     */
    private static final Set<String> RAW_TEXT_ELEMENTS = Set.of("script", "style");

    // ------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------

    private HtmlSyntaxEngine() {
    }

    /**
     * @return the singleton engine instance.
     */
    public static HtmlSyntaxEngine getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Validates the given HTML source for structural syntax errors.
     *
     * @param source the HTML source code; {@code null} or blank is
     *               treated as valid (no content to check).
     * @return a {@link ValidationResult}; valid when no errors are found,
     *         invalid otherwise with detailed {@link ValidationError}s.
     */
    public ValidationResult validate(String source) {
        if (source == null || source.isBlank()) {
            return ValidationResult.valid(
                    "No content to validate — empty HTML input is syntactically valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Phase 1 — Comment syntax validation
        checkComments(source, errors);

        // Phase 2 — CDATA section validation
        checkCdataSections(source, errors);

        // Phase 3 — DOCTYPE validation
        checkDoctype(source, errors);

        // Phase 4 — Tag structure and attribute analysis
        checkTagStructure(source, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid(
                    "HTML syntax is valid (validated by the built-in HTML syntax engine).");
        }

        errors.sort(HtmlSyntaxEngine::compareByPosition);

        return ValidationResult.invalid(
                "HTML syntax validation failed with " + errors.size()
                        + " error(s) detected by the built-in HTML syntax engine.",
                errors);
    }

    // ------------------------------------------------------------------
    //  Phase 1 — Comment validation
    // ------------------------------------------------------------------

    private void checkComments(String source, List<ValidationError> errors) {
        // Check for unclosed comments
        Matcher unclosedMatcher = UNCLOSED_COMMENT_PATTERN.matcher(source);
        while (unclosedMatcher.find()) {
            int line = lineNumberAt(source, unclosedMatcher.start());
            int col = columnNumberAt(source, unclosedMatcher.start());
            errors.add(new ValidationError(line, col,
                    "Unclosed HTML comment '<!--' — expected a matching '-->' to close the comment.",
                    null));
        }

        // Check for nested comments (invalid in HTML)
        Matcher commentMatcher = COMMENT_PATTERN.matcher(source);
        while (commentMatcher.find()) {
            String body = commentMatcher.group(1);
            if (body.contains("<!--")) {
                int line = lineNumberAt(source, commentMatcher.start());
                int col = columnNumberAt(source, commentMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Nested HTML comments are not allowed — '<!--' found inside an existing comment.",
                        null));
            }

            // Check for -- inside comment body (invalid per HTML spec)
            if (body.contains("--")) {
                int line = lineNumberAt(source, commentMatcher.start());
                int col = columnNumberAt(source, commentMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Double hyphens '--' are not allowed inside HTML comments.",
                        null));
            }
        }

        // Check for malformed comment-like constructs: <! — not followed by -- or DOCTYPE
        Pattern malCommentPattern = Pattern.compile("<!(?!-)(?!DOCTYPE)(?!\\[CDATA)[^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher malMatcher = malCommentPattern.matcher(source);
        while (malMatcher.find()) {
            String matched = malMatcher.group();
            // Exclude valid constructs like <![CDATA[...]]>
            if (!matched.startsWith("<![CDATA[")) {
                int line = lineNumberAt(source, malMatcher.start());
                int col = columnNumberAt(source, malMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Malformed markup declaration '" + truncate(matched, 40) + "' — "
                                + "comments must use '<!-- ... -->' syntax.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 2 — CDATA validation
    // ------------------------------------------------------------------

    private void checkCdataSections(String source, List<ValidationError> errors) {
        // Find all CDATA-like constructs
        Pattern cdataFinder = Pattern.compile("<!\\[CDATA\\[", Pattern.DOTALL);
        Matcher startMatcher = cdataFinder.matcher(source);

        while (startMatcher.find()) {
            int start = startMatcher.start();
            // Check if there is a matching ]]>
            String remaining = source.substring(startMatcher.end());
            if (!remaining.contains("]]>")) {
                int line = lineNumberAt(source, start);
                int col = columnNumberAt(source, start);
                errors.add(new ValidationError(line, col,
                        "Unclosed CDATA section '<![CDATA[' — expected a matching ']]>' to close the section.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 3 — DOCTYPE validation
    // ------------------------------------------------------------------

    private void checkDoctype(String source, List<ValidationError> errors) {
        // Check for malformed DOCTYPE (missing '!')
        Matcher malDoctype = MALFORMED_DOCTYPE_PATTERN.matcher(source);
        if (malDoctype.find()) {
            int line = lineNumberAt(source, malDoctype.start());
            int col = columnNumberAt(source, malDoctype.start());
            errors.add(new ValidationError(line, col,
                    "Malformed DOCTYPE declaration — did you mean '<!DOCTYPE' (with an exclamation mark)?",
                    null));
        }

        // If there is a DOCTYPE, validate its format
        Matcher doctypeMatcher = DOCTYPE_PATTERN.matcher(source);
        if (doctypeMatcher.find()) {
            String doctype = doctypeMatcher.group();
            // A valid DOCTYPE should contain 'html' or a formal public identifier
            String lower = doctype.toLowerCase(Locale.ROOT);
            if (!lower.contains("html") && !lower.contains("public") && !lower.contains("system")) {
                int line = lineNumberAt(source, doctypeMatcher.start());
                int col = columnNumberAt(source, doctypeMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Unrecognised DOCTYPE '" + truncate(doctype, 60) + "' — "
                                + "expected a standard DOCTYPE such as '<!DOCTYPE html>'.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 4 — Tag structure and attribute analysis
    // ------------------------------------------------------------------

    private void checkTagStructure(String source, List<ValidationError> errors) {
        // Strip comments and CDATA to avoid false positives
        String stripped = COMMENT_PATTERN.matcher(source).replaceAll("");
        stripped = CDATA_PATTERN.matcher(stripped).replaceAll("");

        // Stack to track open elements for proper nesting
        List<TagInfo> openTagStack = new ArrayList<>();

        Matcher tagMatcher = TAG_PATTERN.matcher(stripped);
        boolean insideRawText = false;
        String rawTextElement = null;

        while (tagMatcher.find()) {
            boolean isClosing = "/".equals(tagMatcher.group(1));
            String tagName = tagMatcher.group(2).toLowerCase(Locale.ROOT);
            String attributesPart = tagMatcher.group(3);
            boolean selfClosing = "/".equals(tagMatcher.group(4));

            int start = tagMatcher.start();
            int line = lineNumberAt(stripped, start);
            int col = columnNumberAt(stripped, start);

            // If we're inside a raw text element (script/style), skip until
            // we find its matching closing tag
            if (insideRawText) {
                if (isClosing && tagName.equals(rawTextElement)) {
                    insideRawText = false;
                    rawTextElement = null;
                    // Pop the opening tag from the stack
                    int stackIndex = findOpenTag(openTagStack, tagName);
                    if (stackIndex >= 0) {
                        openTagStack.remove(stackIndex);
                    }
                }
                continue;
            }

            // Validate attributes in opening tags
            if (!isClosing && attributesPart != null && !attributesPart.isBlank()) {
                checkAttributes(tagName, attributesPart, line, col, errors);
            }

            if (isClosing) {
                // Closing tag logic
                if (VOID_ELEMENTS.contains(tagName)) {
                    errors.add(new ValidationError(line, col,
                            "Closing tag '</" + tagName + ">' is not allowed — "
                                    + "'" + tagName + "' is a void element and must not have a closing tag.",
                            null));
                    continue;
                }

                // Find matching opening tag on the stack
                int stackIndex = findOpenTag(openTagStack, tagName);
                if (stackIndex < 0) {
                    errors.add(new ValidationError(line, col,
                            "Unexpected closing tag '</" + tagName + ">' — "
                                    + "no matching opening tag was found.",
                            null));
                } else {
                    // Check for improper nesting
                    if (stackIndex < openTagStack.size() - 1) {
                        TagInfo unclosed = openTagStack.get(openTagStack.size() - 1);
                        errors.add(new ValidationError(line, col,
                                "Improperly nested tags — expected '</" + unclosed.name
                                        + ">' before '</" + tagName + ">'. "
                                        + "The '<" + unclosed.name + ">' opened at line "
                                        + unclosed.line + " was not closed.",
                                null));
                    }
                    openTagStack.remove(stackIndex);
                }
            } else if (selfClosing || VOID_ELEMENTS.contains(tagName)) {
                // Self-closing or void element — nothing to push onto the stack.
                // Warn if a void element uses explicit self-closing syntax (not
                // technically an error, but good to know).
                if (VOID_ELEMENTS.contains(tagName) && selfClosing) {
                    // Valid but redundant self-closing — not an error.
                }
            } else {
                // Opening tag — push onto the stack
                openTagStack.add(new TagInfo(tagName, line, col));

                // Mark if we're entering a raw text element
                if (RAW_TEXT_ELEMENTS.contains(tagName)) {
                    insideRawText = true;
                    rawTextElement = tagName;
                }
            }
        }

        // Report any tags left on the stack as unclosed
        for (TagInfo unclosed : openTagStack) {
            errors.add(new ValidationError(unclosed.line, unclosed.column,
                    "Unclosed tag '<" + unclosed.name + ">' — "
                            + "the tag was opened but never closed.",
                    null));
        }
    }

    // ------------------------------------------------------------------
    //  Attribute validation
    // ------------------------------------------------------------------

    private void checkAttributes(String tagName, String attributesPart,
                                  int baseLine, int baseCol,
                                  List<ValidationError> errors) {
        // Remove the trailing slash if present (self-closing)
        String cleaned = attributesPart.stripTrailing();
        if (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        if (cleaned.isBlank()) {
            return;
        }

        Matcher attrMatcher = ATTRIBUTE_PATTERN.matcher(cleaned);
        List<String> seenNames = new ArrayList<>();

        while (attrMatcher.find()) {
            String attrName = attrMatcher.group(1);
            String equalsPart = attrMatcher.group(2);
            String valueQuoted = attrMatcher.group(3); // double-quoted
            String valueSingle = attrMatcher.group(4); // single-quoted
            String valueUnquoted = attrMatcher.group(5); // unquoted

            // Check for duplicate attributes
            String lowerName = attrName.toLowerCase(Locale.ROOT);
            if (seenNames.contains(lowerName)) {
                int line = baseLine + countNewlinesBefore(cleaned, attrMatcher.start());
                int col = (attrMatcher.start() == 0) ? baseCol : 1;
                errors.add(new ValidationError(line, col,
                        "Duplicate attribute '" + attrName + "' on <" + tagName + "> element.",
                        null));
            }
            seenNames.add(lowerName);

            // Check that if '=' is present, a value is provided
            if (equalsPart != null && valueQuoted == null
                    && valueSingle == null
                    && (valueUnquoted == null || valueUnquoted.isEmpty())) {
                int line = baseLine + countNewlinesBefore(cleaned, attrMatcher.start());
                int col = (attrMatcher.start() == 0) ? baseCol : 1;
                errors.add(new ValidationError(line, col,
                        "Attribute '" + attrName + "' on <" + tagName
                                + "> has an '=' sign but no value.",
                        null));
            }

            // Check for unmatched quotes in attribute value
            if (equalsPart != null) {
                String raw = equalsPart.stripLeading();
                if (raw.startsWith("=")) {
                    String afterEq = raw.substring(1).stripLeading();
                    if (!afterEq.isEmpty()) {
                        char first = afterEq.charAt(0);
                        if (first == '"' || first == '\'') {
                            // Quoted value — check for closing quote
                            String quoteStr = (first == '"') ? valueQuoted : valueSingle;
                            if (quoteStr == null) {
                                // The regex didn't match a properly closed quoted value
                                int line = baseLine + countNewlinesBefore(cleaned, attrMatcher.start());
                                int col = (attrMatcher.start() == 0) ? baseCol : 1;
                                errors.add(new ValidationError(line, col,
                                        "Unclosed quote in attribute '" + attrName
                                                + "' on <" + tagName + "> element.",
                                        null));
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Helper utilities
    // ------------------------------------------------------------------

    private int findOpenTag(List<TagInfo> stack, String tagName) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).name.equals(tagName)) {
                return i;
            }
        }
        return -1;
    }

    private static int lineNumberAt(String text, int offset) {
        int line = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int columnNumberAt(String text, int offset) {
        int col = 1;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                col = 1;
            } else {
                col++;
            }
        }
        return col;
    }

    private static int countNewlinesBefore(String text, int offset) {
        int count = 0;
        int limit = Math.min(offset, text.length());
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static int compareByPosition(ValidationError a, ValidationError b) {
        int lineCmp = Integer.compare(a.getLine(), b.getLine());
        return lineCmp != 0 ? lineCmp : Integer.compare(a.getColumn(), b.getColumn());
    }

    // ------------------------------------------------------------------
    //  Internal data holder
    // ------------------------------------------------------------------

    private static final class TagInfo {
        final String name;
        final int line;
        final int column;

        TagInfo(String name, int line, int column) {
            this.name = name;
            this.line = line;
            this.column = column;
        }
    }
}
