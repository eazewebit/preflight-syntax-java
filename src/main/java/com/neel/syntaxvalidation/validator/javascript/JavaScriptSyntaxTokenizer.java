package com.neel.syntaxvalidation.validator.javascript;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * A hand-written, single-pass lexer for modern JavaScript (ES6 through ES2024).
 *
 * <p>The tokenizer is the foundation of the pure-Java syntax engine. Its primary
 * job is to produce a token stream that correctly distinguishes <em>structural</em>
 * tokens (keywords and punctuation that participate in bracket matching and
 * pattern checks) from <em>opaque</em> tokens (strings, template literals, regular
 * expressions, numbers and comments) whose internal content must never be
 * mistaken for code.
 *
 * <h2>Key challenges handled</h2>
 * <ul>
 *   <li><b>Regex vs. division disambiguation.</b> A forward slash can begin a
 *       regex literal or denote division. The decision is made contextually
 *       based on the previous significant token, mirroring the algorithm used
 *       by production JS engines.</li>
 *   <li><b>Template literals with interpolation.</b> Backtick templates may
 *       contain arbitrarily nested {@code ${...}} expressions, which in turn may
 *       contain further nested templates, strings and regexes. The scanner
 *       handles this recursively.</li>
 *   <li><b>Line &amp; column tracking.</b> Every token carries an accurate 1-based
 *       position so the engine can report precise diagnostics.</li>
 *   <li><b>Unterminated literals.</b> An unclosed string, template, regex or
 *       block comment produces an {@link JsTokenType#ERROR} token rather than
 *       crashing or silently consuming the rest of the file.</li>
 * </ul>
 *
 * <p>The tokenizer is <em>not</em> a full ECMAScript parser &mdash; it does not
 * build an AST. It is intentionally lightweight so that it can run in any JVM
 * without external dependencies, yet powerful enough to catch the vast majority
 * of structural syntax errors.
 */
final class JavaScriptSyntaxTokenizer {

    // ------------------------------------------------------------------
    //  Keyword tables
    // ------------------------------------------------------------------

    /** All ECMAScript reserved words, future-reserved words and common contextual keywords. */
    private static final Set<String> KEYWORDS = Set.of(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "finally",
            "for", "function", "if", "import", "in", "instanceof", "new",
            "return", "super", "switch", "this", "throw", "try", "typeof",
            "var", "void", "while", "with", "yield", "let", "static", "async",
            "await", "enum", "implements", "interface", "package", "private",
            "protected", "public", "true", "false", "null", "from", "as", "get",
            "set", "of"
    );

    /**
     * Keywords after which a forward slash always begins a regex literal (never
     * division). These are keywords that <em>expect an expression</em> to follow.
     */
    private static final Set<String> REGEX_PRECEDING_KEYWORDS = Set.of(
            "return", "typeof", "instanceof", "in", "of", "new", "delete", "void",
            "throw", "if", "else", "do", "while", "for", "switch", "case", "yield",
            "await", "default", "let", "const", "var", "function", "class", "export",
            "import", "async", "extends"
    );

    /**
     * Punctuation tokens after which a forward slash always begins a regex literal.
     * Note: closing delimiters {@code )} {@code ]} {@code }} are <em>not</em> in
     * this set because they typically end a value-producing expression, making
     * the slash division.
     */
    private static final Set<String> REGEX_PRECEDING_PUNCTUATION = Set.of(
            "(", "[", "{", ",", ";", ":", "!", "~", "+", "-", "*", "/", "%",
            "&", "|", "^", "<", ">", "=", "?", ".", "=>", "&&", "||", "??",
            "==", "===", "!=", "!==", "+=", "-=", "*=", "/=", "%=", "**", "**=",
            "&=", "|=", "^=", "<<", ">>", ">>>", "<<=", ">>=", ">>>=", "&&=", "||=", "??="
    );

    /** Multi-character operators sorted longest-first so the greedy match is correct. */
    private static final String[] MULTI_CHAR_OPS = {
            // 4 characters
            ">>>=",
            // 3 characters
            "===", "!==", ">>>", "**=", "...", "<<=", ">>=", "&&=", "||=", "??=",
            // 2 characters
            "=>", "==", "!=", "<=", ">=", "&&", "||", "??", "**", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^="
    };

    // ------------------------------------------------------------------
    //  Mutable scanning state
    // ------------------------------------------------------------------

    private final String src;
    private final int length;

    private int pos = 0;   // absolute character offset
    private int line = 1;  // 1-based line
    private int col = 1;   // 1-based column

    private final List<JsToken> tokens = new ArrayList<>(128);

    /**
     * The last significant (non-comment, non-error) token, or {@code null} at
     * the start of input. Used for regex-vs-division disambiguation.
     */
    private JsToken lastSignificant = null;

    JavaScriptSyntaxTokenizer(String source) {
        this.src = source == null ? "" : source;
        this.length = this.src.length();
    }

    // ------------------------------------------------------------------
    //  Public entry point
    // ------------------------------------------------------------------

    /**
     * Tokenises the entire source and returns the token list (always
     * terminated by an {@link JsTokenType#EOF} sentinel).
     *
     * @return an unmodifiable view of the tokens.
     */
    List<JsToken> tokenize() {
        skipHashbang();

        while (pos < length) {
            char c = current();

            if (isWhitespace(c)) {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                scanLineComment();
            } else if (c == '/' && peek(1) == '*') {
                scanBlockComment();
            } else if (c == '\'' || c == '"') {
                scanString(c);
            } else if (c == '`') {
                scanTemplate();
            } else if (c == '/' && isRegexPreceded()) {
                scanRegex();
            } else if (isDigit(c) || (c == '.' && isDigit(peek(1)))) {
                scanNumber();
            } else if (c == '#') {
                scanPrivateIdentifier();
            } else if (isIdentifierStart(c)) {
                scanWord();
            } else {
                scanPunctuation();
            }
        }

        addToken(JsTokenType.EOF, "<eof>", line, col);
        return List.copyOf(tokens);
    }

    // ------------------------------------------------------------------
    //  Character helpers
    // ------------------------------------------------------------------

    private char current() {
        return pos < length ? src.charAt(pos) : '\0';
    }

    private char peek(int ahead) {
        int idx = pos + ahead;
        return idx < length ? src.charAt(idx) : '\0';
    }

    /**
     * Advances one character, updating line and column for all three common
     * line-ending conventions ({@code \n}, {@code \r\n}, lone {@code \r}).
     */
    private void advance() {
        if (pos >= length) {
            return;
        }
        char c = src.charAt(pos++);
        if (c == '\n') {
            line++;
            col = 1;
        } else if (c == '\r') {
            line++;
            col = 1;
            // Skip the paired \n in a \r\n sequence to avoid double-counting.
            if (pos < length && src.charAt(pos) == '\n') {
                pos++;
            }
        } else {
            col++;
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r'
                || c == '\u000B' || c == '\u000C'
                || c == '\u00A0' || c == '\uFEFF';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c)
                || c == '\u200C'  // ZWNJ
                || c == '\u200D'; // ZWJ
    }

    private static boolean isRegexFlag(char c) {
        return c == 'g' || c == 'i' || c == 'm' || c == 's' || c == 'u' || c == 'y' || c == 'd';
    }

    // ------------------------------------------------------------------
    //  Token emission
    // ------------------------------------------------------------------

    private void addToken(JsTokenType type, String text, int tokLine, int tokCol) {
        JsToken token = new JsToken(type, text, tokLine, tokCol);
        tokens.add(token);
        if (type != JsTokenType.COMMENT && type != JsTokenType.ERROR) {
            lastSignificant = token;
        }
    }

    // ------------------------------------------------------------------
    //  Regex disambiguation
    // ------------------------------------------------------------------

    /**
     * Determines whether a forward slash at the current position should be
     * interpreted as the start of a regular-expression literal rather than a
     * division operator.
     */
    private boolean isRegexPreceded() {
        if (lastSignificant == null) {
            return true; // start of input
        }
        switch (lastSignificant.type) {
            case PUNCTUATION:
                return REGEX_PRECEDING_PUNCTUATION.contains(lastSignificant.text);
            case KEYWORD:
                return REGEX_PRECEDING_KEYWORDS.contains(lastSignificant.text);
            default:
                // IDENTIFIER, NUMBER, STRING, TEMPLATE, REGEX → value context → division
                return false;
        }
    }

    // ------------------------------------------------------------------
    //  Individual scanners
    // ------------------------------------------------------------------

    /**
     * Skips a hashbang line ({@code #!...}) if it appears at the very start of
     * the source, as supported by Node.js.
     */
    private void skipHashbang() {
        if (length >= 2 && src.charAt(0) == '#' && src.charAt(1) == '!') {
            while (pos < length && current() != '\n' && current() != '\r') {
                advance();
            }
        }
    }

    private void scanLineComment() {
        int startLine = line, startCol = col;
        advance(); // first /
        advance(); // second /
        while (pos < length && current() != '\n' && current() != '\r') {
            advance();
        }
        addToken(JsTokenType.COMMENT, "<comment>", startLine, startCol);
    }

    private void scanBlockComment() {
        int startLine = line, startCol = col;
        advance(); // /
        advance(); // *
        boolean closed = false;
        while (pos < length) {
            if (current() == '*' && peek(1) == '/') {
                advance(); // *
                advance(); // /
                closed = true;
                break;
            }
            advance();
        }
        if (closed) {
            addToken(JsTokenType.COMMENT, "<comment>", startLine, startCol);
        } else {
            addToken(JsTokenType.ERROR,
                    "Unterminated block comment — '/*' was never closed with '*/'.",
                    startLine, startCol);
        }
    }

    private void scanString(char quote) {
        int startLine = line, startCol = col;
        advance(); // opening quote
        while (pos < length) {
            char c = current();
            if (c == quote) {
                advance(); // closing quote
                addToken(JsTokenType.STRING, "<string>", startLine, startCol);
                return;
            }
            if (c == '\\') {
                advance(); // backslash
                if (pos < length) {
                    advance(); // escaped character
                }
                continue;
            }
            if (c == '\n' || c == '\r') {
                // Raw line break inside a non-template string is a syntax error.
                break;
            }
            advance();
        }
        addToken(JsTokenType.ERROR,
                "Unterminated string literal — missing closing '" + quote + "'.",
                startLine, startCol);
    }

    /**
     * Scans a complete template literal, including any number of
     * {@code ${...}} interpolation expressions and nested templates.
     */
    private void scanTemplate() {
        int startLine = line, startCol = col;
        advance(); // opening backtick

        while (pos < length) {
            char c = current();

            if (c == '`') {
                advance(); // closing backtick
                addToken(JsTokenType.TEMPLATE, "<template>", startLine, startCol);
                return;
            }

            if (c == '\\') {
                advance(); // backslash
                if (pos < length) {
                    advance(); // escaped character
                }
                continue;
            }

            if (c == '$' && peek(1) == '{') {
                advance(); // $
                advance(); // {
                scanInterpolation();
                // After the interpolation expression (and its closing }),
                // continue scanning template body characters.
                if (pos >= length) {
                    break; // ran out of input inside interpolation → unterminated
                }
                continue;
            }

            advance(); // regular template character (including newlines)
        }

        addToken(JsTokenType.ERROR,
                "Unterminated template literal — missing closing backtick ('`').",
                startLine, startCol);
    }

    /**
     * Scans the expression portion of a {@code ${...}} interpolation inside a
     * template literal. The opening {@code ${} has already been consumed; this
     * method finds and consumes the matching closing {@code }}.
     *
     * <p>Because the interpolation is an arbitrary expression, it may contain
     * nested braces, strings, template literals, comments and regexes. This
     * method tracks brace depth and uses lightweight "skip" helpers to jump
     * over opaque constructs so that braces inside them are not miscounted.
     */
    private void scanInterpolation() {
        int depth = 1;   // the '{' of '${' is already consumed
        char prevSig = '\0'; // last significant char, for simplified regex heuristic

        while (pos < length && depth > 0) {
            char c = current();

            if (c == '{') {
                depth++;
                prevSig = c;
                advance();
            } else if (c == '}') {
                depth--;
                advance();
                if (depth == 0) {
                    return;
                }
                prevSig = '}';
            } else if (c == '\'' || c == '"') {
                skipCharsString(c);
                prevSig = 'a'; // treat closing quote as value-producing
            } else if (c == '`') {
                skipCharsTemplate();
                prevSig = 'a';
            } else if (c == '/' && peek(1) == '/') {
                skipCharsLineComment();
            } else if (c == '/' && peek(1) == '*') {
                skipCharsBlockComment();
            } else if (c == '/' && isRegexPrecededCharSimple(prevSig)) {
                skipCharsRegex();
                prevSig = 'a';
            } else if (isWhitespace(c)) {
                advance();
                // do not update prevSig
            } else {
                prevSig = c;
                advance();
            }
        }
        // If we exit here, depth > 0 and we hit EOF — the caller (scanTemplate)
        // will detect the missing closing backtick and report the error.
    }

    /**
     * Simplified, character-level regex heuristic for use inside template
     * interpolations where full token context is unavailable.
     */
    private static boolean isRegexPrecededCharSimple(char prev) {
        if (prev == '\0') {
            return true;
        }
        switch (prev) {
            case '(': case '[': case '{': case ',': case ';': case ':':
            case '!': case '~': case '+': case '-': case '*': case '%':
            case '&': case '|': case '^': case '<': case '>': case '=':
            case '?': case '.':
                return true;
            default:
                return false;
        }
    }

    private void scanRegex() {
        int startLine = line, startCol = col;
        advance(); // opening /
        boolean inClass = false;

        while (pos < length) {
            char c = current();

            if (c == '\\') {
                advance();
                if (pos < length) {
                    advance();
                }
                continue;
            }
            if (c == '[' && !inClass) {
                inClass = true;
                advance();
                continue;
            }
            if (c == ']' && inClass) {
                inClass = false;
                advance();
                continue;
            }
            if (c == '/' && !inClass) {
                advance(); // closing /
                while (pos < length && isRegexFlag(current())) {
                    advance();
                }
                addToken(JsTokenType.REGEX, "<regex>", startLine, startCol);
                return;
            }
            if (c == '\n' || c == '\r') {
                break; // unterminated — regex cannot span lines
            }
            advance();
        }

        addToken(JsTokenType.ERROR,
                "Unterminated regular-expression literal — missing closing '/'.",
                startLine, startCol);
    }

    private void scanNumber() {
        int startLine = line, startCol = col;

        if (current() == '.') {
            // Leading-dot fractional: .5, .5e3
            advance();
            consumeDecimalDigits();
            consumeExponent();
            addToken(JsTokenType.NUMBER, "<number>", startLine, startCol);
            return;
        }

        if (current() == '0') {
            char base = peek(1);
            if (base == 'x' || base == 'X') {
                advance(); advance();
                while (pos < length && (isHexDigit(current()) || current() == '_')) advance();
                if (current() == 'n') advance(); // BigInt
                addToken(JsTokenType.NUMBER, "<number>", startLine, startCol);
                return;
            }
            if (base == 'b' || base == 'B') {
                advance(); advance();
                while (pos < length && (current() == '0' || current() == '1' || current() == '_')) advance();
                if (current() == 'n') advance();
                addToken(JsTokenType.NUMBER, "<number>", startLine, startCol);
                return;
            }
            if (base == 'o' || base == 'O') {
                advance(); advance();
                while (pos < length && ((current() >= '0' && current() <= '7') || current() == '_')) advance();
                if (current() == 'n') advance();
                addToken(JsTokenType.NUMBER, "<number>", startLine, startCol);
                return;
            }
        }

        // Decimal integer part
        consumeDecimalDigits();

        // Fractional part — only if the dot is followed by a digit
        if (current() == '.' && isDigit(peek(1))) {
            advance(); // .
            consumeDecimalDigits();
        }

        consumeExponent();

        if (current() == 'n') {
            advance(); // BigInt suffix
        }

        addToken(JsTokenType.NUMBER, "<number>", startLine, startCol);
    }

    private void consumeDecimalDigits() {
        while (pos < length && (isDigit(current()) || current() == '_')) {
            advance();
        }
    }

    private void consumeExponent() {
        if (current() == 'e' || current() == 'E') {
            int savePos = pos;
            int saveLine = line, saveCol = col;
            advance();
            if (current() == '+' || current() == '-') {
                advance();
            }
            if (pos < length && isDigit(current())) {
                consumeDecimalDigits();
            } else {
                // Not a valid exponent — rewind (the 'e' is part of an identifier-like token)
                pos = savePos;
                line = saveLine;
                col = saveCol;
            }
        }
    }

    private void scanWord() {
        int startLine = line, startCol = col;
        int startPos = pos;
        advance(); // first char
        while (pos < length && isIdentifierPart(current())) {
            advance();
        }
        String word = src.substring(startPos, pos);
        JsTokenType type = KEYWORDS.contains(word) ? JsTokenType.KEYWORD : JsTokenType.IDENTIFIER;
        addToken(type, word, startLine, startCol);
    }

    private void scanPrivateIdentifier() {
        int startLine = line, startCol = col;
        advance(); // '#'
        while (pos < length && isIdentifierPart(current())) {
            advance();
        }
        addToken(JsTokenType.IDENTIFIER, "<private-identifier>", startLine, startCol);
    }

    private void scanPunctuation() {
        int startLine = line, startCol = col;
        char c = current();

        // ?. — optional chaining, but only when NOT followed by a digit.
        // (e.g. `a?.b` is optional chaining; `a?.5` is `a ? .5` ternary.)
        if (c == '?' && peek(1) == '.' && !isDigit(peek(2))) {
            advance();
            advance();
            addToken(JsTokenType.PUNCTUATION, "?.", startLine, startCol);
            return;
        }

        // Greedy longest-match for multi-character operators.
        for (String op : MULTI_CHAR_OPS) {
            if (startsWith(op)) {
                for (int i = 0; i < op.length(); i++) {
                    advance();
                }
                addToken(JsTokenType.PUNCTUATION, op, startLine, startCol);
                return;
            }
        }

        // Single-character punctuation.
        advance();
        addToken(JsTokenType.PUNCTUATION, String.valueOf(c), startLine, startCol);
    }

    private boolean startsWith(String text) {
        if (pos + text.length() > length) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (src.charAt(pos + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Lightweight "skip" helpers for template interpolation scanning.
    //
    //  These advance past opaque constructs WITHOUT emitting tokens, because
    //  tokens inside ${...} must not appear in the main token stream (they
    //  would corrupt bracket-matching and structural checks).
    // ------------------------------------------------------------------

    private void skipCharsString(char quote) {
        advance(); // opening quote
        while (pos < length) {
            char c = current();
            if (c == quote) {
                advance();
                return;
            }
            if (c == '\\') {
                advance();
                if (pos < length) advance();
                continue;
            }
            if (c == '\n' || c == '\r') {
                return; // unterminated string inside interpolation
            }
            advance();
        }
    }

    private void skipCharsTemplate() {
        advance(); // opening backtick
        while (pos < length) {
            char c = current();
            if (c == '`') {
                advance();
                return;
            }
            if (c == '\\') {
                advance();
                if (pos < length) advance();
                continue;
            }
            if (c == '$' && peek(1) == '{') {
                advance();
                advance();
                scanInterpolation(); // recursively handle nested interpolation
            } else {
                advance();
            }
        }
    }

    private void skipCharsLineComment() {
        advance(); // first /
        advance(); // second /
        while (pos < length && current() != '\n' && current() != '\r') {
            advance();
        }
    }

    private void skipCharsBlockComment() {
        advance(); // /
        advance(); // *
        while (pos < length) {
            if (current() == '*' && peek(1) == '/') {
                advance();
                advance();
                return;
            }
            advance();
        }
    }

    private void skipCharsRegex() {
        advance(); // opening /
        boolean inClass = false;
        while (pos < length) {
            char c = current();
            if (c == '\\') {
                advance();
                if (pos < length) advance();
                continue;
            }
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) {
                advance();
                while (pos < length && isRegexFlag(current())) advance();
                return;
            }
            if (c == '\n' || c == '\r') return;
            advance();
        }
    }
}
