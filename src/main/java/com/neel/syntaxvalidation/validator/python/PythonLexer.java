package com.neel.syntaxvalidation.validator.python;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hand-written lexer for Python 3.14 source code.
 *
 * <p>Converts raw Python source text into a flat list of {@link PythonToken}
 * instances.  The lexer is designed to be <b>error-tolerant</b>: when it
 * encounters a character sequence it cannot classify it emits a
 * {@code null}-type token and advances past the offending character
 * rather than throwing an exception.  This allows downstream parsers to report
 * <em>all</em> issues in a single pass.
 *
 * <h2>Features recognised</h2>
 * <ul>
 *   <li>All Python 3.14 keywords including {@code match}/{@code case},
 *       {@code type}, {@code _} (soft keyword in certain contexts).</li>
 *   <li>F-string, t-string (PEP 750), raw-string, byte-string, and triple-quoted literals.</li>
 *   <li>Numeric literals: integer (decimal, hex, octal, binary), float,
 *       complex, and underscore-separated variants.</li>
 *   <li>Operators and delimiters including the walrus operator {@code :=} and
 *       the matrix multiply operator {@code @}.</li>
 *   <li>Indentation tracking for block structure (INDENT / DEDENT tokens).</li>
 *   <li>Comments and line continuations (backslash-newline).</li>
 * </ul>
 *
 * <p>This class is <b>not</b> thread-safe; create one instance per lexing run.
 */
public final class PythonLexer {

    // ------------------------------------------------------------------
    //  Keywords (hard keywords — cannot be used as identifiers)
    // ------------------------------------------------------------------
    private static final Set<String> KEYWORDS = Set.of(
            "False", "None", "True",
            "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda",
            "nonlocal", "not", "or", "pass", "raise", "return", "try",
            "while", "with", "yield"
    );

    /** Soft keywords that are only keywords in specific syntactic positions. */
    private static final Set<String> SOFT_KEYWORDS = Set.of("match", "case", "type", "_");

    private static final Map<String, PythonTokenType> KEYWORD_TOKENS = Map.ofEntries(
            Map.entry("False", PythonTokenType.KW_FALSE),
            Map.entry("None", PythonTokenType.KW_NONE),
            Map.entry("True", PythonTokenType.KW_TRUE),
            Map.entry("and", PythonTokenType.KW_AND),
            Map.entry("as", PythonTokenType.KW_AS),
            Map.entry("assert", PythonTokenType.KW_ASSERT),
            Map.entry("async", PythonTokenType.KW_ASYNC),
            Map.entry("await", PythonTokenType.KW_AWAIT),
            Map.entry("break", PythonTokenType.KW_BREAK),
            Map.entry("class", PythonTokenType.KW_CLASS),
            Map.entry("continue", PythonTokenType.KW_CONTINUE),
            Map.entry("def", PythonTokenType.KW_DEF),
            Map.entry("del", PythonTokenType.KW_DEL),
            Map.entry("elif", PythonTokenType.KW_ELIF),
            Map.entry("else", PythonTokenType.KW_ELSE),
            Map.entry("except", PythonTokenType.KW_EXCEPT),
            Map.entry("finally", PythonTokenType.KW_FINALLY),
            Map.entry("for", PythonTokenType.KW_FOR),
            Map.entry("from", PythonTokenType.KW_FROM),
            Map.entry("global", PythonTokenType.KW_GLOBAL),
            Map.entry("if", PythonTokenType.KW_IF),
            Map.entry("import", PythonTokenType.KW_IMPORT),
            Map.entry("in", PythonTokenType.KW_IN),
            Map.entry("is", PythonTokenType.KW_IS),
            Map.entry("lambda", PythonTokenType.KW_LAMBDA),
            Map.entry("nonlocal", PythonTokenType.KW_NONLOCAL),
            Map.entry("not", PythonTokenType.KW_NOT),
            Map.entry("or", PythonTokenType.KW_OR),
            Map.entry("pass", PythonTokenType.KW_PASS),
            Map.entry("raise", PythonTokenType.KW_RAISE),
            Map.entry("return", PythonTokenType.KW_RETURN),
            Map.entry("try", PythonTokenType.KW_TRY),
            Map.entry("while", PythonTokenType.KW_WHILE),
            Map.entry("with", PythonTokenType.KW_WITH),
            Map.entry("yield", PythonTokenType.KW_YIELD)
    );

    private static final Map<String, PythonTokenType> SOFT_KEYWORD_TOKENS = Map.of(
            "match", PythonTokenType.MATCH,
            "case", PythonTokenType.CASE,
            "type", PythonTokenType.TYPE
    );

    private final String source;
    private final List<PythonToken> tokens;
    private int pos;
    private int line;
    private int col;
    private final List<Integer> indentStack;
    private boolean atLineStart;
    private int parenDepth;

    public PythonLexer(String source) {
        this.source = source == null ? "" : source;
        this.tokens = new ArrayList<>();
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.indentStack = new ArrayList<>();
        this.indentStack.add(0);
        this.atLineStart = true;
        this.parenDepth = 0;
    }

    public List<PythonToken> tokenize() {
        while (pos < source.length()) {
            if (atLineStart && parenDepth == 0) {
                handleIndentation();
            }
            atLineStart = false;

            if (pos >= source.length()) break;

            char c = source.charAt(pos);

            // Skip whitespace (not newlines)
            if (c == ' ' || c == '\t' || c == '\r') { advance(); continue; }

            // Line continuation
            if (c == '\\' && peekChar(1) == '\n') {
                advance(); advance(); line++; col = 1; continue;
            }
            if (c == '\\' && peekChar(1) == '\r' && peekChar(2) == '\n') {
                advance(); advance(); advance(); line++; col = 1; continue;
            }

            // Newlines
            if (c == '\n') {
                if (parenDepth == 0) emit(PythonTokenType.NEWLINE, "\n", line, col);
                advance(); line++; col = 1; atLineStart = true; continue;
            }

            // Comments
            if (c == '#') { skipComment(); continue; }

            // Strings
            if (isStringStart()) { readString(); continue; }

            // Numbers
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(peekChar(1)))) {
                readNumber(); continue;
            }

            // Identifiers / keywords
            if (isIdentifierStart(c)) { readIdentifier(); continue; }

            // Operators / delimiters
            if (readOperator()) continue;

            // Unknown — emit a synthetic error token using EOF as a sentinel
            // (PythonTokenType has no ERROR; downstream parser will catch it)
            emit(null, "LEXER_ERROR:" + c, line, col);
            advance();
        }

        // Close remaining indentation
        while (indentStack.size() > 1) {
            indentStack.remove(indentStack.size() - 1);
            emit(PythonTokenType.DEDENT, "", line, col);
        }

        emit(PythonTokenType.EOF, "", line, col);
        return List.copyOf(tokens);
    }

    // ==================================================================
    //  Indentation
    // ==================================================================

    private void handleIndentation() {
        int indent = 0;
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == ' ') { indent++; advance(); }
            else if (c == '\t') { indent = (indent / 8 + 1) * 8; advance(); }
            else break;
        }
        if (pos >= source.length() || source.charAt(pos) == '\n' || source.charAt(pos) == '\r' || source.charAt(pos) == '#') return;

        int current = indentStack.get(indentStack.size() - 1);
        if (indent > current) {
            indentStack.add(indent);
            emit(PythonTokenType.INDENT, "", line, col);
        } else if (indent < current) {
            while (indentStack.size() > 1 && indentStack.get(indentStack.size() - 1) > indent) {
                indentStack.remove(indentStack.size() - 1);
                emit(PythonTokenType.DEDENT, "", line, col);
            }
            if (indentStack.get(indentStack.size() - 1) != indent) {
                emit(null, "LEXER_ERROR:IndentationError", line, col);
            }
        }
    }

    // ==================================================================
    //  Comments
    // ==================================================================

    private void skipComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') advance();
    }

    // ==================================================================
    //  Strings
    // ==================================================================

    private void readString() {
        int sl = line, sc = col, startPos = pos;

        // Prefix: r, b, u, f, t and combinations
        String prefix = "";
        while (pos < source.length()) {
            char lo = Character.toLowerCase(source.charAt(pos));
            if (lo == 'r' || lo == 'b' || lo == 'u' || lo == 'f' || lo == 't') {
                advance();
            } else break;
        }
        prefix = source.substring(startPos, pos).toLowerCase();

        boolean triple = pos + 2 < source.length()
                && source.charAt(pos) == source.charAt(pos + 1)
                && source.charAt(pos) == source.charAt(pos + 2)
                && (source.charAt(pos) == '"' || source.charAt(pos) == '\'');

        char quote = source.charAt(triple ? pos : pos);
        if (triple) { advance(); advance(); advance(); }
        else if (pos < source.length() && (source.charAt(pos) == '"' || source.charAt(pos) == '\'')) advance();
        else { emit(null, "LEXER_ERROR:InvalidString", sl, sc); return; }

        boolean raw = prefix.contains("r");
        boolean closed = triple ? readTriple(quote, raw) : readSingle(quote, raw);
        if (!closed) emit(null, "LEXER_ERROR:UnterminatedString", sl, sc);

        emit(PythonTokenType.STRING_LITERAL, source.substring(startPos, pos), sl, sc);
    }

    private boolean readSingle(char q, boolean raw) {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') return false;
            if (c == q) { advance(); return true; }
            if (c == '\\' && !raw) { advance(); if (pos < source.length()) advance(); }
            else advance();
        }
        return false;
    }

    private boolean readTriple(char q, boolean raw) {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\\' && !raw) { advance(); if (pos < source.length()) advance(); continue; }
            if (c == q && pos + 2 < source.length()
                    && source.charAt(pos + 1) == q && source.charAt(pos + 2) == q) {
                advance(); advance(); advance(); return true;
            }
            if (c == '\n') { line++; col = 0; }
            advance();
        }
        return false;
    }

    // ==================================================================
    //  Numbers
    // ==================================================================

    private void readNumber() {
        int sl = line, sc = col, startPos = pos;
        PythonTokenType type = PythonTokenType.INTEGER_LITERAL;

        if (cur() == '0' && pos + 1 < source.length()) {
            char n = Character.toLowerCase(source.charAt(pos + 1));
            if (n == 'x') { advance(); advance(); readHex(); }
            else if (n == 'o') { advance(); advance(); readOct(); }
            else if (n == 'b') { advance(); advance(); readBin(); }
            else { readDec(); if (isFloatTail()) { type = PythonTokenType.FLOAT_LITERAL; readFloat(); } }
        } else {
            readDec();
            if (isFloatTail()) { type = PythonTokenType.FLOAT_LITERAL; readFloat(); }
        }
        if (pos < source.length() && (source.charAt(pos) == 'j' || source.charAt(pos) == 'J')) {
            type = PythonTokenType.COMPLEX_LITERAL; advance();
        }
        emit(type, source.substring(startPos, pos), sl, sc);
    }

    private boolean isFloatTail() {
        return pos < source.length() && (source.charAt(pos) == '.' || source.charAt(pos) == 'e' || source.charAt(pos) == 'E');
    }

    private void readFloat() {
        if (pos < source.length() && source.charAt(pos) == '.') { advance(); readDec(); }
        if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
            advance();
            if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) advance();
            readDec();
        }
    }

    private void readHex() { while (pos < source.length() && (isHex(source.charAt(pos)) || source.charAt(pos) == '_')) advance(); }
    private void readOct() { while (pos < source.length() && (isOct(source.charAt(pos)) || source.charAt(pos) == '_')) advance(); }
    private void readBin() { while (pos < source.length() && (source.charAt(pos) == '0' || source.charAt(pos) == '1' || source.charAt(pos) == '_')) advance(); }
    private void readDec() { while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '_')) advance(); }

    // ==================================================================
    //  Identifiers / keywords
    // ==================================================================

    private void readIdentifier() {
        int sl = line, sc = col, startPos = pos;
        while (pos < source.length() && isIdentPart(source.charAt(pos))) advance();
        String text = source.substring(startPos, pos);
        PythonTokenType type;
        if (KEYWORDS.contains(text)) type = KEYWORD_TOKENS.getOrDefault(text, PythonTokenType.IDENTIFIER);
        else if (SOFT_KEYWORDS.contains(text)) type = SOFT_KEYWORD_TOKENS.getOrDefault(text, PythonTokenType.IDENTIFIER);
        else type = PythonTokenType.IDENTIFIER;
        emit(type, text, sl, sc);
    }

    // ==================================================================
    //  Operators / delimiters
    // ==================================================================

    private boolean readOperator() {
        int sl = line, sc = col;
        char c = cur();

        switch (c) {
            case '(': parenDepth++; adv(); emit(PythonTokenType.LEFT_PAREN, "(", sl, sc); return true;
            case ')': parenDepth = Math.max(0, parenDepth - 1); adv(); emit(PythonTokenType.RIGHT_PAREN, ")", sl, sc); return true;
            case '[': parenDepth++; adv(); emit(PythonTokenType.LEFT_BRACKET, "[", sl, sc); return true;
            case ']': parenDepth = Math.max(0, parenDepth - 1); adv(); emit(PythonTokenType.RIGHT_BRACKET, "]", sl, sc); return true;
            case '{': parenDepth++; adv(); emit(PythonTokenType.LEFT_BRACE, "{", sl, sc); return true;
            case '}': parenDepth = Math.max(0, parenDepth - 1); adv(); emit(PythonTokenType.RIGHT_BRACE, "}", sl, sc); return true;
            case ';': adv(); emit(PythonTokenType.SEMICOLON, ";", sl, sc); return true;
            case ',': adv(); emit(PythonTokenType.COMMA, ",", sl, sc); return true;
            case '.': if (Character.isDigit(peekChar(1))) return false;
                      if (peekChar(1) == '.' && peekChar(2) == '.') { adv(); adv(); adv(); emit(PythonTokenType.ELLIPSIS_LITERAL, "...", sl, sc); }
                      else { adv(); emit(PythonTokenType.DOT, ".", sl, sc); }
                      return true;
            case '~': adv(); emit(PythonTokenType.TILDE, "~", sl, sc); return true;
            case ':': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.COLON_EQUAL, ":=", sl, sc); }
                      else emit(PythonTokenType.COLON, ":", sl, sc); return true;
            case '=': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.EQUAL_EQUAL, "==", sl, sc); }
                      else emit(PythonTokenType.EQUAL, "=", sl, sc); return true;
            case '!': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.NOT_EQUAL, "!=", sl, sc); }
                      else emit(null, "LEXER_ERROR:!", sl, sc); return true;
            case '@': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.AT_EQUAL, "@=", sl, sc); }
                      else emit(PythonTokenType.AT, "@", sl, sc); return true;
            case '+': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.PLUS_EQUAL, "+=", sl, sc); }
                      else emit(PythonTokenType.PLUS, "+", sl, sc); return true;
            case '-': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.MINUS_EQUAL, "-=", sl, sc); }
                      else if (pos < source.length() && source.charAt(pos) == '>') { adv(); emit(PythonTokenType.ARROW, "->", sl, sc); }
                      else emit(PythonTokenType.MINUS, "-", sl, sc); return true;
            case '*': adv(); if (pos < source.length() && source.charAt(pos) == '*') { adv();
                      if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.DOUBLE_STAR_EQUAL, "**=", sl, sc); }
                      else emit(PythonTokenType.DOUBLE_STAR, "**", sl, sc); }
                      else if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.STAR_EQUAL, "*=", sl, sc); }
                      else emit(PythonTokenType.STAR, "*", sl, sc); return true;
            case '/': adv(); if (pos < source.length() && source.charAt(pos) == '/') { adv();
                      if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.DOUBLE_SLASH_EQUAL, "//=", sl, sc); }
                      else emit(PythonTokenType.DOUBLE_SLASH, "//", sl, sc); }
                      else if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.SLASH_EQUAL, "/=", sl, sc); }
                      else emit(PythonTokenType.SLASH, "/", sl, sc); return true;
            case '%': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.PERCENT_EQUAL, "%=", sl, sc); }
                      else emit(PythonTokenType.PERCENT, "%", sl, sc); return true;
            case '&': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.AMPERSAND_EQUAL, "&=", sl, sc); }
                      else emit(PythonTokenType.AMPERSAND, "&", sl, sc); return true;
            case '|': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.PIPE_EQUAL, "|=", sl, sc); }
                      else emit(PythonTokenType.PIPE, "|", sl, sc); return true;
            case '^': adv(); if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.CARET_EQUAL, "^=", sl, sc); }
                      else emit(PythonTokenType.CARET, "^", sl, sc); return true;
            case '<': adv(); if (pos < source.length() && source.charAt(pos) == '<') { adv();
                      if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.LEFT_SHIFT_EQUAL, "<<=", sl, sc); }
                      else emit(PythonTokenType.LEFT_SHIFT, "<<", sl, sc); }
                      else if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.LESS_EQUAL, "<=", sl, sc); }
                      else emit(PythonTokenType.LESS, "<", sl, sc); return true;
            case '>': adv(); if (pos < source.length() && source.charAt(pos) == '>') { adv();
                      if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.RIGHT_SHIFT_EQUAL, ">>=", sl, sc); }
                      else emit(PythonTokenType.RIGHT_SHIFT, ">>", sl, sc); }
                      else if (pos < source.length() && source.charAt(pos) == '=') { adv(); emit(PythonTokenType.GREATER_EQUAL, ">=", sl, sc); }
                      else emit(PythonTokenType.GREATER, ">", sl, sc); return true;
            default: return false;
        }
    }

    // ==================================================================
    //  Helpers
    // ==================================================================

    private char cur() { return source.charAt(pos); }
    private char peekChar(int off) { int i = pos + off; return i < source.length() ? source.charAt(i) : '\0'; }
    private void advance() { pos++; col++; }
    private void adv() { advance(); }
    private void emit(PythonTokenType type, String text, int tl, int tc) { tokens.add(new PythonToken(type, text, tl, tc)); }

    private boolean isStringStart() {
        if (pos >= source.length()) return false;
        char c = source.charAt(pos);
        if (c == '"' || c == '\'') return true;
        char lo = Character.toLowerCase(c);
        if (lo == 'r' || lo == 'b' || lo == 'u' || lo == 'f' || lo == 't') {
            for (int i = 1; i <= 3 && pos + i < source.length(); i++) {
                char ch = Character.toLowerCase(source.charAt(pos + i));
                if (ch == '"' || ch == '\'') return true;
                if (ch != 'r' && ch != 'b' && ch != 'u' && ch != 'f' && ch != 't') break;
            }
        }
        return false;
    }

    private static boolean isIdentifierStart(char c) { return Character.isLetter(c) || c == '_' || c >= 0x80; }
    private static boolean isIdentPart(char c) { return Character.isLetterOrDigit(c) || c == '_' || c >= 0x80; }
    private static boolean isHex(char c) { return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
    private static boolean isOct(char c) { return c >= '0' && c <= '7'; }
}
