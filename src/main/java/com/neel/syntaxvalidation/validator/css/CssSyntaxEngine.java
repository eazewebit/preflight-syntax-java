package com.neel.syntaxvalidation.validator.css;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pure-Java syntax validation engine for CSS content.
 *
 * <p>This engine runs entirely inside the JVM with zero external dependencies.
 * It performs a suite of structural checks to catch common CSS syntax errors
 * without requiring an external tool such as stylelint.
 *
 * <h2>Validation phases</h2>
 * <ol>
 *   <li><b>Comment validation</b> &mdash; checks for unclosed or nested
 *       CSS comments.</li>
 *   <li><b>Block structure validation</b> &mdash; verifies that curly braces
 *       are balanced and properly matched.</li>
 *   <li><b>Selector validation</b> &mdash; checks for malformed or empty
 *       selectors preceding declaration blocks.</li>
 *   <li><b>Declaration validation</b> &mdash; inside each block, validates
 *       that property declarations have a colon, a value, and a terminating
 *       semicolon.</li>
 *   <li><b>String / URL validation</b> &mdash; detects unclosed strings and
 *       URL parentheses.</li>
 *   <li><b>At-rule validation</b> &mdash; verifies the syntax of common
 *       at-rules such as @media, @import, and @keyframes.</li>
 * </ol>
 *
 * <p>The engine is conservative: it avoids reporting false positives for
 * valid (if unusual) CSS constructs such as vendor prefixes, CSS variables,
 * and modern selectors.
 *
 * <p><b>Thread-safety.</b> The engine is stateless and can be shared freely
 * across threads. Obtain the canonical instance via {@link #getInstance()}.
 */
public final class CssSyntaxEngine {

    /**
     * Matches CSS comments: slash-star ... star-slash.
     */
    private static final Pattern COMMENT_PATTERN =
            Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * Matches an unclosed comment starting with slash-star but never closed.
     */
    private static final Pattern UNCLOSED_COMMENT_PATTERN =
            Pattern.compile("/\\*(?!.*\\*/)", Pattern.DOTALL);

    /**
     * Matches a string literal (single or double quoted) with proper escaping.
     */
    private static final Pattern STRING_PATTERN =
            Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", Pattern.DOTALL);

    /**
     * Matches an unclosed string (starts with a quote but has no matching
     * close on the same logical line, ignoring escaped quotes).
     */
    private static final Pattern UNCLOSED_STRING_PATTERN =
            Pattern.compile("(?:\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*$|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*$)", Pattern.MULTILINE);

    /**
     * Matches an @rule keyword.
     */
    private static final Pattern AT_RULE_PATTERN =
            Pattern.compile("@([a-zA-Z-]+)\\b");

    /**
     * Matches a URL function in CSS.
     */
    private static final Pattern URL_FUNCTION_PATTERN =
            Pattern.compile("url\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * Matches the opening of a declaration block (selector followed by '{').
     */
    private static final Pattern BLOCK_OPEN_PATTERN =
            Pattern.compile("([^{}]+?)\\s*\\{", Pattern.DOTALL);

    /**
     * Matches individual CSS declarations within a block.
     * Group 1 = property name.
     * Group 2 = value (everything after the colon up to the semicolon or block end).
     */
    private static final Pattern DECLARATION_PATTERN =
            Pattern.compile(
                    "([a-zA-Z_-][a-zA-Z0-9_-]*)\\s*:\\s*(.+?)(?:;|$)",
                    Pattern.DOTALL
            );

    /**
     * Matches an empty selector (nothing before a '{').
     */
    private static final Pattern EMPTY_SELECTOR_PATTERN =
            Pattern.compile("^\\s*\\{", Pattern.MULTILINE);

    /**
     * Matches CSS custom property (variable) declarations like --my-color: red;
     */
    private static final Pattern CUSTOM_PROPERTY_PATTERN =
            Pattern.compile("--[a-zA-Z_-][a-zA-Z0-9_-]*\\s*:");

    // ------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------

    private static final CssSyntaxEngine INSTANCE = new CssSyntaxEngine();

    private CssSyntaxEngine() {
    }
    /**
     * @return the singleton engine instance.
     */
    public static CssSyntaxEngine getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Validates the given CSS source for structural syntax errors.
     *
     * @param source the CSS source code; {@code null} or blank is treated
     *               as valid (no content to check).
     * @return a {@link ValidationResult}; valid when no errors are found,
     *         invalid otherwise with detailed {@link ValidationError}s.
     */
    public ValidationResult validate(String source) {
        if (source == null || source.isBlank()) {
            return ValidationResult.valid(
                    "No content to validate — empty CSS input is syntactically valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Phase 1 — Comment validation
        checkComments(source, errors);

        // Phase 2 — String validation
        checkStrings(source, errors);

        // Phase 3 — Brace balance
        checkBraceBalance(source, errors);

        // Phase 4 — Selector and declaration validation
        if (errors.isEmpty()) {
            checkSelectorsAndDeclarations(source, errors);
        }

        // Phase 5 — At-rule validation
        checkAtRules(source, errors);

        // Phase 6 — URL function validation
        checkUrlFunctions(source, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid(
                    "CSS syntax is valid (validated by the built-in CSS syntax engine).");
        }

        errors.sort(CssSyntaxEngine::compareByPosition);

        return ValidationResult.invalid(
                "CSS syntax validation failed with " + errors.size()
                        + " error(s) detected by the built-in CSS syntax engine.",
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
                    "Unclosed CSS comment '/*' — expected a matching '*/' to close the comment.",
                    null));
        }

        // Check for nested comments (invalid in CSS)
        Matcher commentMatcher = COMMENT_PATTERN.matcher(source);
        while (commentMatcher.find()) {
            String body = commentMatcher.group();
            // Remove the outer /* and */
            String inner = body.substring(2, body.length() - 2);
            if (inner.contains("/*")) {
                int line = lineNumberAt(source, commentMatcher.start());
                int col = columnNumberAt(source, commentMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Nested CSS comments are not allowed — '/*' found inside an existing comment.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 2 — String validation
    // ------------------------------------------------------------------

    private void checkStrings(String source, List<ValidationError> errors) {
        // Strip comments first to avoid false positives
        String stripped = COMMENT_PATTERN.matcher(source).replaceAll("");

        // For each line, check for unclosed strings
        String[] lines = stripped.split("\\R", -1);
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            checkUnclosedStringInLine(line, i + 1, errors);
            offset += line.length() + 1; // +1 for newline
        }
    }

    private void checkUnclosedStringInLine(String line, int lineNum,
                                            List<ValidationError> errors) {
        // Simple state machine to track quote state
        boolean inDouble = false;
        boolean inSingle = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '\\') {
                // Skip the next character (it's escaped)
                i++;
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            }
        }

        // If we're still inside a quote at end of line, that's suspicious
        // but only flag it if the line doesn't have obvious continuation
        // (like a backslash at the end, which is a line continuation in some
        // preprocessors).
        if (inDouble) {
            errors.add(new ValidationError(lineNum, line.length(),
                    "Unclosed double-quoted string — expected a closing '\"'.",
                    null));
        }
        if (inSingle) {
            errors.add(new ValidationError(lineNum, line.length(),
                    "Unclosed single-quoted string — expected a closing \"'\".",
                    null));
        }
    }

    // ------------------------------------------------------------------
    //  Phase 3 — Brace balance
    // ------------------------------------------------------------------

    private void checkBraceBalance(String source, List<ValidationError> errors) {
        // Strip comments and strings to avoid counting braces inside them
        String stripped = stripCommentsAndStrings(source);

        int depth = 0;
        int lastOpenLine = -1;

        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '{') {
                depth++;
                lastOpenLine = lineNumberAt(source, findOriginalOffset(source, stripped, i));
            } else if (c == '}') {
                if (depth == 0) {
                    int line = lineNumberAt(source, findOriginalOffset(source, stripped, i));
                    int col = columnNumberAt(source, findOriginalOffset(source, stripped, i));
                    errors.add(new ValidationError(line, col,
                            "Unexpected closing brace '}' — no matching opening brace '{' was found.",
                            null));
                } else {
                    depth--;
                }
            }
        }

        if (depth > 0) {
            errors.add(new ValidationError(
                    Math.max(lastOpenLine, 1), 1,
                    "Unclosed block — " + depth + " opening brace(s) '{' without a matching closing brace '}'.",
                    null));
        }
    }

    // ------------------------------------------------------------------
    //  Phase 4 — Selector and declaration validation
    // ------------------------------------------------------------------

    private void checkSelectorsAndDeclarations(String source, List<ValidationError> errors) {
        // Strip comments and strings
        String stripped = stripCommentsAndStrings(source);

        // Walk through the CSS and extract blocks
        int depth = 0;
        int blockStart = -1;
        StringBuilder currentContent = new StringBuilder();

        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);

            if (c == '{') {
                depth++;
                if (depth == 1) {
                    // Start of a top-level block
                    String selector = currentContent.toString().strip();
                    validateSelector(selector, source, stripped, i, errors);
                    blockStart = i + 1;
                    currentContent.setLength(0);
                } else {
                    currentContent.append(c);
                }
            } else if (c == '}') {
                if (depth == 1) {
                    // End of a top-level block — validate declarations inside
                    String blockBody = currentContent.toString();
                    // Also get the original block body (before stripping) for value checks
                    String originalBlockBody = source.substring(
                            Math.min(blockStart, source.length()),
                            Math.min(i, source.length()));
                    validateBlockBody(blockBody, originalBlockBody, source, stripped, blockStart, errors);
                    currentContent.setLength(0);
                } else if (depth > 1) {
                    currentContent.append(c);
                }
                depth = Math.max(depth - 1, 0);
            } else {
                currentContent.append(c);
            }
        }
    }

    private void validateSelector(String selector, String originalSource,
                                   String stripped, int position,
                                   List<ValidationError> errors) {
        if (selector.isEmpty()) {
            int line = lineNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            int col = columnNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            errors.add(new ValidationError(line, col,
                    "Empty selector — a declaration block '{' was found without a preceding selector.",
                    null));
            return;
        }

        // Check for obvious syntax errors in selectors
        // Selector cannot start with a comma
        if (selector.stripLeading().startsWith(",")) {
            int line = lineNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            int col = columnNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            errors.add(new ValidationError(line, col,
                    "Invalid selector '" + truncate(selector, 40) + "' — "
                            + "selectors cannot start with a comma.",
                    null));
        }

        // Check for double commas
        if (selector.contains(",,")) {
            int line = lineNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            int col = columnNumberAt(originalSource,
                    findOriginalOffset(originalSource, stripped, position));
            errors.add(new ValidationError(line, col,
                    "Invalid selector '" + truncate(selector, 40) + "' — "
                            + "double commas are not allowed in selector lists.",
                    null));
        }
    }

    private void validateBlockBody(String blockBody, String originalBlockBody,
                                    String originalSource, String stripped,
                                    int blockStart, List<ValidationError> errors) {
        // Split declarations by semicolons
        // Use the stripped block body for structural parsing, but use the
        // original block body for value checks (so we don't lose string values)
        String[] declarations = blockBody.split(";");
        String[] originalDeclarations = originalBlockBody.split(";");

        for (int d = 0; d < declarations.length; d++) {
            String trimmed = declarations[d].strip();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Skip nested blocks (they contain their own validation)
            if (trimmed.contains("{") || trimmed.contains("}")) {
                continue;
            }

            // Skip CSS custom properties (--var: value)
            if (trimmed.startsWith("--")) {
                // Custom properties can have nearly any value, so just check
                // that they have a colon
                if (!trimmed.contains(":")) {
                    int line = lineNumberAt(originalSource,
                            findOriginalOffset(originalSource, stripped, blockStart));
                    errors.add(new ValidationError(line, 1,
                            "Invalid custom property '" + truncate(trimmed, 40) + "' — "
                                    + "custom property declarations must contain a colon ':'.",
                            null));
                }
                continue;
            }

            // Skip at-rules nested in blocks
            if (trimmed.startsWith("@")) {
                continue;
            }

            // Check for a colon (property: value separator)
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx < 0) {
                // No colon found — this might be a missing colon error
                // But also check if it looks like a property name (rather than
                // a comment, at-rule, etc.)
                if (trimmed.matches("[a-zA-Z_-].*")) {
                    int line = lineNumberAt(originalSource,
                            findOriginalOffset(originalSource, stripped, blockStart));
                    errors.add(new ValidationError(line, 1,
                            "Invalid declaration '" + truncate(trimmed, 40) + "' — "
                                    + "expected a colon ':' between property and value.",
                            null));
                }
                continue;
            }

            // Validate property name (from stripped version is fine)
            String propName = trimmed.substring(0, colonIdx).strip();
            if (propName.isEmpty()) {
                int line = lineNumberAt(originalSource,
                        findOriginalOffset(originalSource, stripped, blockStart));
                errors.add(new ValidationError(line, 1,
                        "Empty property name before ':' in declaration.",
                        null));
                continue;
            }

            // Validate that the property name looks reasonable
            if (!propName.matches("[a-zA-Z_-][a-zA-Z0-9_-]*")) {
                // Might be a vendor prefix like -webkit-transform, which is valid
                if (!propName.startsWith("-")) {
                    int line = lineNumberAt(originalSource,
                            findOriginalOffset(originalSource, stripped, blockStart));
                    errors.add(new ValidationError(line, 1,
                            "Invalid property name '" + propName + "' — "
                                    + "CSS property names must start with a letter, underscore, or hyphen.",
                            null));
                }
            }

            // Validate value using the ORIGINAL declaration (before string stripping)
            // so that values like 'CustomFont' or "hello" don't appear empty
            String originalValue = "";
            if (d < originalDeclarations.length) {
                String origTrimmed = originalDeclarations[d].strip();
                int origColonIdx = origTrimmed.indexOf(':');
                if (origColonIdx >= 0) {
                    originalValue = origTrimmed.substring(origColonIdx + 1).strip();
                }
            }
            if (originalValue.isEmpty()) {
                int line = lineNumberAt(originalSource,
                        findOriginalOffset(originalSource, stripped, blockStart));
                errors.add(new ValidationError(line, 1,
                        "Empty value for property '" + propName + "' — "
                                + "a value is expected after the colon.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 5 — At-rule validation
    // ------------------------------------------------------------------

    private void checkAtRules(String source, List<ValidationError> errors) {
        // Strip comments and strings
        String stripped = stripCommentsAndStrings(source);

        Matcher atMatcher = AT_RULE_PATTERN.matcher(stripped);
        while (atMatcher.find()) {
            String atName = atMatcher.group(1).toLowerCase(Locale.ROOT);

            // Check for @import — must be at the top of the stylesheet
            // (before any rules other than @charset and @layer)
            if ("import".equals(atName)) {
                // Simple check: @import should appear before any rule blocks
                int importPos = atMatcher.start();
                String beforeImport = stripped.substring(0, importPos).strip();
                if (!beforeImport.isEmpty() && !beforeImport.startsWith("@")) {
                    int line = lineNumberAt(source, atMatcher.start());
                    int col = columnNumberAt(source, atMatcher.start());
                    errors.add(new ValidationError(line, col,
                            "@import rule found after other CSS rules — "
                                    + "@import must appear at the top of the stylesheet.",
                            null));
                }
            }

            // Check for @media — must have a condition
            if ("media".equals(atName)) {
                String remaining = stripped.substring(atMatcher.end()).strip();
                if (!remaining.startsWith("(") && !remaining.startsWith("not")
                        && !remaining.startsWith("only")
                        && !remaining.startsWith("all")
                        && !remaining.startsWith("print")
                        && !remaining.startsWith("screen")) {
                    // This could be a media query with a custom type — be lenient
                }
            }

            // Check for unknown at-rules (warn only for common typos)
            if (!isKnownAtRule(atName)) {
                int line = lineNumberAt(source, atMatcher.start());
                int col = columnNumberAt(source, atMatcher.start());
                errors.add(new ValidationError(line, col,
                        "Unrecognised at-rule '@" + atName + "' — "
                                + "this may be a typo or an unsupported CSS feature.",
                        null));
            }
        }
    }


    private boolean isKnownAtRule(String name) {
        // Common CSS at-rules (including CSS3 and CSS4)
        return switch (name) {
            case "charset", "import", "namespace", "media", "supports", "page",
                 "font-face", "keyframes", "-webkit-keyframes", "-moz-keyframes",
                 "layer", "property", "container", "scope", "starting-style",
                 "counter-style", "font-feature-values", "color-profile",
                 "font-palette-values", "nest", "apply", "custom-media",
                 "viewport", "document", "-moz-document" -> true;
            default -> false;
        };
    }
    // ------------------------------------------------------------------
    //  Phase 6 — URL function validation
    // ------------------------------------------------------------------

    private void checkUrlFunctions(String source, List<ValidationError> errors) {
        // Strip comments
        String stripped = COMMENT_PATTERN.matcher(source).replaceAll("");

        Matcher urlMatcher = URL_FUNCTION_PATTERN.matcher(stripped);
        while (urlMatcher.find()) {
            // Find the matching closing paren
            int parenStart = urlMatcher.end() - 1; // position of '('
            int depth = 1;
            int pos = parenStart + 1;

            while (pos < stripped.length() && depth > 0) {
                char c = stripped.charAt(pos);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                pos++;
            }

            if (depth != 0) {
                int line = lineNumberAt(source, parenStart);
                int col = columnNumberAt(source, parenStart);
                errors.add(new ValidationError(line, col,
                        "Unclosed 'url(' function — expected a closing ')' to match.",
                        null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Helper utilities
    // ------------------------------------------------------------------

    /**
     * Strips CSS comments and string literals from the source, replacing
     * each with whitespace of the same length to preserve character offsets.
     */
    private static String stripCommentsAndStrings(String source) {
        char[] result = source.toCharArray();
        int i = 0;

        while (i < result.length) {
            // Skip comments
            if (i + 1 < result.length && result[i] == '/' && result[i + 1] == '*') {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) {
                    // Unclosed comment — replace rest of string
                    for (int j = i; j < result.length; j++) {
                        if (result[j] != '\n') {
                            result[j] = ' ';
                        }
                    }
                    break;
                }
                for (int j = i; j < end + 2; j++) {
                    if (result[j] != '\n') {
                        result[j] = ' ';
                    }
                }
                i = end + 2;
                continue;
            }

            // Skip strings
            if (result[i] == '"' || result[i] == '\'') {
                char quote = result[i];
                int j = i + 1;
                while (j < result.length) {
                    if (result[j] == '\\') {
                        if (result[j] != '\n') {
                            result[j] = ' ';
                        }
                        j++;
                        if (j < result.length && result[j] != '\n') {
                            result[j] = ' ';
                        }
                        j++;
                    } else if (result[j] == quote) {
                        break;
                    } else {
                        j++;
                    }
                }
                // Replace the string with spaces (preserve newlines)
                for (int k = i; k <= Math.min(j, result.length - 1); k++) {
                    if (result[k] != '\n') {
                        result[k] = ' ';
                    }
                }
                i = j + 1;
                continue;
            }

            i++;
        }

        return new String(result);
    }

    /**
     * Approximate mapping from a position in the stripped source back to the
     * original source. Because we preserve lengths, this is essentially the
     * identity function.
     */
    private static int findOriginalOffset(String original, String stripped, int strippedOffset) {
        return Math.min(strippedOffset, original.length() - 1);
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
}
