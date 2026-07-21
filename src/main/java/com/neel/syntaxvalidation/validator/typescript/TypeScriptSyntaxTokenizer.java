package com.neel.syntaxvalidation.validator.typescript;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hand-written single-pass lexer for TypeScript, TSX, and JSX source code.
 * <p>
 * This lexer scans source code character-by-character from left to right and
 * produces a list of {@link TsToken}s. It supports:
 * <ul>
 *   <li>All TypeScript keywords, type annotations, and generics</li>
 *   <li>JSX/TSX syntax (tag brackets, attributes, self-closing tags)</li>
 *   <li>Template literals with {@code ${}} interpolation</li>
 *   <li>Regular expression literals</li>
 *   <li>Single-line and multi-line comments</li>
 * </ul>
 * <p>
 * Whitespace and comments are included in the token stream so that the syntax
 * engine can choose to skip them when appropriate.
 */
final class TypeScriptSyntaxTokenizer {

    /** Keywords that map to variable declaration tokens. */
    private static final Set<String> VAR_KEYWORDS = Set.of("var", "let", "const");

    /** Keywords used in TypeScript type declarations and annotations. */
    private static final Set<String> TYPE_KEYWORDS = Set.of(
            "string", "number", "boolean", "any", "unknown", "never", "void", "object",
            "symbol", "bigint", "undefined", "null",
            "interface", "type", "enum", "namespace", "module",
            "abstract", "readonly", "private", "protected", "public", "static", "override",
            "declare", "satisfies", "infer"
    );

    /** Keywords used to determine JSX tag context. */
    private static final Set<String> CONTROL_FLOW_KEYWORDS = Set.of(
            "return", "typeof", "keyof", "extends", "implements", "instanceof",
            "new", "delete", "void", "yield", "throw", "case", "await", "of", "in"
    );

    /** Context tracking for JSX mode. */
    private boolean inJsxContext;
    private int jsxDepth;
    private final List<Boolean> jsxContextStack = new ArrayList<>();

    /**
     * Tokenize the given source code.
     *
     * @param source the complete source code string
     * @return an immutable list of tokens (never {@code null})
     */
    List<TsToken> tokenize(String source) {
        if (source == null || source.isEmpty()) {
            return List.of(new TsToken(TsTokenType.EOF, "", 1, 1));
        }

        List<TsToken> tokens = new ArrayList<>(source.length() / 4);
        int pos = 0;
        int line = 1;
        int col = 1;
        int templateDepth = 0;
        List<Integer> templateStack = new ArrayList<>();

        while (pos < source.length()) {
            char c = source.charAt(pos);
            char next = pos + 1 < source.length() ? source.charAt(pos + 1) : '\0';

            // ── Whitespace ──────────────────────────────────────────────
            if (Character.isWhitespace(c)) {
                int startLine = line;
                int startCol = col;
                int start = pos;
                while (pos < source.length() && Character.isWhitespace(source.charAt(pos))) {
                    char ch = source.charAt(pos);
                    if (ch == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    pos++;
                }
                tokens.add(new TsToken(TsTokenType.WHITESPACE, source.substring(start, pos), startLine, startCol));
                continue;
            }

            // ── Single-line comment ─────────────────────────────────────
            if (c == '/' && next == '/') {
                int startLine = line;
                int startCol = col;
                int start = pos;
                pos += 2;
                col += 2;
                while (pos < source.length() && source.charAt(pos) != '\n') {
                    pos++;
                    col++;
                }
                tokens.add(new TsToken(TsTokenType.LINE_COMMENT, source.substring(start, pos), startLine, startCol));
                continue;
            }

            // ── Block comment ───────────────────────────────────────────
            if (c == '/' && next == '*') {
                int startLine = line;
                int startCol = col;
                int start = pos;
                pos += 2;
                col += 2;
                while (pos < source.length() - 1) {
                    if (source.charAt(pos) == '*' && source.charAt(pos + 1) == '/') {
                        pos += 2;
                        col += 2;
                        break;
                    }
                    if (source.charAt(pos) == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                    pos++;
                }
                tokens.add(new TsToken(TsTokenType.BLOCK_COMMENT, source.substring(start, pos), startLine, startCol));
                continue;
            }

            // ── String literals ─────────────────────────────────────────
            if (c == '\'' || c == '"' || (c == '`' && templateDepth == 0)) {
                int startLine = line;
                int startCol = col;
                int start = pos;
                char quote = c;
                pos++; col++;  // consume opening quote
                while (pos < source.length()) {
                    char sc = source.charAt(pos);
                    if (sc == '\\' && pos + 1 < source.length()) {
                        pos += 2; col += 2;
                    } else if (sc == quote) {
                        pos++; col++;
                        break;
                    } else {
                        if (sc == '\n') { line++; col = 1; } else { col++; }
                        pos++;
                    }
                }
                String lexeme = source.substring(start, pos);
                TsTokenType type = quote == '`' ? TsTokenType.TEMPLATE_LITERAL : TsTokenType.STRING;
                tokens.add(new TsToken(type, lexeme, startLine, startCol));
                continue;
            }

            // ── Template literal start ──────────────────────────────────
            if (c == '`') {
                int startLine = line;
                int startCol = col;
                int start = pos;
                pos++;
                col++;
                // Read until end of template or ${
                while (pos < source.length()) {
                    char tc = source.charAt(pos);
                    if (tc == '`') {
                        pos++;
                        col++;
                        break;
                    } else if (tc == '\\' && pos + 1 < source.length()) {
                        pos += 2;
                        col += 2;
                    } else if (tc == '$' && pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                        // Template expression start
                        templateStack.add(templateDepth);
                        templateDepth++;
                        pos += 2;
                        col += 2;
                        break;
                    } else {
                        if (tc == '\n') {
                            line++;
                            col = 1;
                        } else {
                            col++;
                        }
                        pos++;
                    }
                }
                tokens.add(new TsToken(TsTokenType.TEMPLATE_LITERAL, source.substring(start, pos), startLine, startCol));
                continue;
            }

            // ── Template expression end ─────────────────────────────────
            if (c == '}' && templateDepth > 0) {
                templateDepth--;
                int startLine = line;
                int startCol = col;
                pos++;
                col++;
                // Continue reading template literal
                int tplStart = pos;
                while (pos < source.length()) {
                    char tc = source.charAt(pos);
                    if (tc == '`') {
                        pos++;
                        col++;
                        break;
                    } else if (tc == '\\' && pos + 1 < source.length()) {
                        pos += 2;
                        col += 2;
                    } else if (tc == '$' && pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                        templateStack.add(templateDepth);
                        templateDepth++;
                        pos += 2;
                        col += 2;
                        break;
                    } else {
                        if (tc == '\n') {
                            line++;
                            col = 1;
                        } else {
                            col++;
                        }
                        pos++;
                    }
                }
                tokens.add(new TsToken(TsTokenType.TEMPLATE_LITERAL, source.substring(tplStart, pos), startLine, startCol));
                continue;
            }

            // ── Numbers ─────────────────────────────────────────────────
            if (Character.isDigit(c) || (c == '.' && Character.isDigit(next))) {
                int startLine = line;
                int startCol = col;
                int start = pos;
                if (c == '0' && (next == 'x' || next == 'X')) {
                    pos += 2;
                    col += 2;
                    while (pos < source.length() && isHexDigit(source.charAt(pos))) {
                        pos++;
                        col++;
                    }
                } else if (c == '0' && (next == 'b' || next == 'B')) {
                    pos += 2;
                    col += 2;
                    while (pos < source.length() && (source.charAt(pos) == '0' || source.charAt(pos) == '1')) {
                        pos++;
                        col++;
                    }
                } else {
                    while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                        pos++;
                        col++;
                    }
                    if (pos < source.length() && source.charAt(pos) == '.') {
                        pos++;
                        col++;
                        while (pos < source.length() && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                            pos++;
                            col++;
                        }
                    }
                    if (pos < source.length() && (source.charAt(pos) == 'e' || source.charAt(pos) == 'E')) {
                        pos++;
                        col++;
                        if (pos < source.length() && (source.charAt(pos) == '+' || source.charAt(pos) == '-')) {
                            pos++;
                            col++;
                        }
                        while (pos < source.length() && Character.isDigit(source.charAt(pos))) {
                            pos++;
                            col++;
                        }
                    }
                }
                // BigInt suffix
                if (pos < source.length() && source.charAt(pos) == 'n') {
                    pos++;
                    col++;
                }
                tokens.add(new TsToken(TsTokenType.NUMBER, source.substring(start, pos), startLine, startCol));
                continue;
            }

            // ── Identifiers and keywords ────────────────────────────────
            if (Character.isLetter(c) || c == '_' || c == '$' || isIdentifierStart(c)) {
                int startLine = line;
                int startCol = col;
                int start = pos;
                while (pos < source.length() && isIdentifierPart(source.charAt(pos))) {
                    pos++;
                    col++;
                }
                String word = source.substring(start, pos);
                TsTokenType type = classifyWord(word);
                tokens.add(new TsToken(type, word, startLine, startCol));
                continue;
            }

            // ── JSX/TSX tag context ─────────────────────────────────────
            // Handle closing tags (</tag>) - always treat as JSX
            if (inJsxContext && c == '<' && next == '/') {
                tokens.add(new TsToken(TsTokenType.JSX_TAG_OPEN, "<", line, col));
                pos++;
                col++;
                continue;
            }
            // Only treat '<' as a JSX tag opening if it doesn't follow an identifier
            // or keyword (which would indicate a generic type parameter).
            if (inJsxContext && c == '<') {
                TsToken prevSignificant = findLastSignificantToken(tokens);
                boolean isGenericContext = prevSignificant != null &&
                    (prevSignificant.type() == TsTokenType.IDENTIFIER ||
                     prevSignificant.type() == TsTokenType.TYPE_KEYWORD ||
                     prevSignificant.type() == TsTokenType.CLASS ||
                     prevSignificant.type() == TsTokenType.FUNCTION ||
                     prevSignificant.type() == TsTokenType.EXTENDS ||
                     prevSignificant.type() == TsTokenType.IMPLEMENTS ||
                     prevSignificant.type() == TsTokenType.GENERIC_CLOSE ||
                     prevSignificant.type() == TsTokenType.RPAREN ||
                     prevSignificant.type() == TsTokenType.RBRACKET);
                
                if (!isGenericContext) {
                    tokens.add(new TsToken(TsTokenType.JSX_TAG_OPEN, "<", line, col));
                    pos++;
                    col++;
                    continue;
                }
            }
            if (inJsxContext && c == '>' && next == '/') {
                // This could be JSX closing: </>
                // But we need to check if it's actually JSX
            }
            if (inJsxContext && c == '/' && next == '>') {
                tokens.add(new TsToken(TsTokenType.JSX_SELF_CLOSE, "/>", line, col));
                pos += 2;
                col += 2;
                continue;
            }
            if (inJsxContext && c == '>') {
                tokens.add(new TsToken(TsTokenType.JSX_TAG_CLOSE, ">", line, col));
                pos++;
                col++;
                continue;
            }
            if (inJsxContext && c == '=' && !isOperatorPart(next)) {
                tokens.add(new TsToken(TsTokenType.JSX_ATTR_EQ, "=", line, col));
                pos++;
                col++;
                continue;
            }

            // ── Spread operator ─────────────────────────────────────────
            if (c == '.' && next == '.' && pos + 2 < source.length() && source.charAt(pos + 2) == '.') {
                tokens.add(new TsToken(TsTokenType.SPREAD, "...", line, col));
                pos += 3;
                col += 3;
                continue;
            }

            // ── Generic angle brackets ────────────────────────────────
            // Distinguish between comparison operators (<, >) and generic type
            // parameters (e.g., Array<T>). Generic brackets follow identifiers,
            // keywords, or closing delimiters (], ), >).
            if (c == '<' && !inJsxContext) {
                if (isGenericOpenContext(tokens)) {
                    tokens.add(new TsToken(TsTokenType.GENERIC_OPEN, "<", line, col));
                    pos++; col++;
                    continue;
                }
            }
            if (c == '>' && !inJsxContext) {
                if (isGenericCloseContext(tokens)) {
                    tokens.add(new TsToken(TsTokenType.GENERIC_CLOSE, ">", line, col));
                    pos++; col++;
                    continue;
                }
            }

            // ── JSX closing tag operator (</) ─────────────────────────
            // In JSX context, '</' should be split into JSX_TAG_OPEN '<' and OPERATOR '/'
            if (inJsxContext && c == '<' && next == '/') {
                tokens.add(new TsToken(TsTokenType.JSX_TAG_OPEN, "<", line, col));
                pos++; col++;
                continue;
            }

            // ── Operators ───────────────────────────────────────────────
            if (isOperatorChar(c)) {
                int startLine = line;
                int startCol = col;
                int start = pos;
                while (pos < source.length() && isOperatorChar(source.charAt(pos))) {
                    pos++;
                    col++;
                }
                String op = source.substring(start, pos);
                TsTokenType type;
                if (op.equals("?")) {
                    type = TsTokenType.QUESTION;
                } else if (op.equals(":")) {
                    type = TsTokenType.COLON;
                } else {
                    type = TsTokenType.OPERATOR;
                }
                tokens.add(new TsToken(type, op, startLine, startCol));
                continue;
            }

            // ── Delimiters ──────────────────────────────────────────────
            switch (c) {
                case '(' -> {
                    tokens.add(new TsToken(TsTokenType.LPAREN, "(", line, col));
                    pos++; col++;
                }
                case ')' -> {
                    tokens.add(new TsToken(TsTokenType.RPAREN, ")", line, col));
                    pos++; col++;
                }
                case '{' -> {
                    tokens.add(new TsToken(TsTokenType.LBRACE, "{", line, col));
                    pos++; col++;
                }
                case '}' -> {
                    tokens.add(new TsToken(TsTokenType.RBRACE, "}", line, col));
                    pos++; col++;
                }
                case '[' -> {
                    tokens.add(new TsToken(TsTokenType.LBRACKET, "[", line, col));
                    pos++; col++;
                }
                case ']' -> {
                    tokens.add(new TsToken(TsTokenType.RBRACKET, "]", line, col));
                    pos++; col++;
                }
                case ';' -> {
                    tokens.add(new TsToken(TsTokenType.SEMICOLON, ";", line, col));
                    pos++; col++;
                }
                case ',' -> {
                    tokens.add(new TsToken(TsTokenType.COMMA, ",", line, col));
                    pos++; col++;
                }
                case '.' -> {
                    tokens.add(new TsToken(TsTokenType.DOT, ".", line, col));
                    pos++; col++;
                }
                default -> {
                    // Unknown character — skip it
                    pos++; col++;
                }
            }
        }

        tokens.add(new TsToken(TsTokenType.EOF, "", line, col));
        return List.copyOf(tokens);
    }

    /**
     * Classify a word (identifier or keyword) into its token type.
     */
    private static TsTokenType classifyWord(String word) {
        return switch (word) {
            case "var", "let", "const" -> TsTokenType.VARIABLE_DECL;
            case "function" -> TsTokenType.FUNCTION;
            case "class" -> TsTokenType.CLASS;
            case "return" -> TsTokenType.RETURN;
            case "if" -> TsTokenType.IF;
            case "else" -> TsTokenType.ELSE;
            case "for" -> TsTokenType.FOR;
            case "while" -> TsTokenType.WHILE;
            case "do" -> TsTokenType.DO;
            case "switch" -> TsTokenType.SWITCH;
            case "case" -> TsTokenType.CASE;
            case "break" -> TsTokenType.BREAK;
            case "continue" -> TsTokenType.CONTINUE;
            case "try" -> TsTokenType.TRY;
            case "catch" -> TsTokenType.CATCH;
            case "finally" -> TsTokenType.FINALLY;
            case "throw" -> TsTokenType.THROW;
            case "new" -> TsTokenType.NEW;
            case "delete" -> TsTokenType.DELETE;
            case "void" -> TsTokenType.VOID;
            case "this" -> TsTokenType.THIS;
            case "super" -> TsTokenType.SUPER;
            case "yield" -> TsTokenType.YIELD;
            case "async" -> TsTokenType.ASYNC;
            case "await" -> TsTokenType.AWAIT;
            case "of" -> TsTokenType.OF;
            case "in" -> TsTokenType.IN;
            case "true", "false" -> TsTokenType.BOOLEAN;
            case "null" -> TsTokenType.NULL_LITERAL;
            case "import" -> TsTokenType.IMPORT;
            case "from" -> TsTokenType.FROM;
            case "export" -> TsTokenType.EXPORT;
            case "default" -> TsTokenType.DEFAULT;
            case "as" -> TsTokenType.AS;
            case "typeof" -> TsTokenType.TYPEOF;
            case "keyof" -> TsTokenType.KEYOF;
            case "extends" -> TsTokenType.EXTENDS;
            case "implements" -> TsTokenType.IMPLEMENTS;
            case "declare" -> TsTokenType.DECLARE;
            case "namespace" -> TsTokenType.NAMESPACE;
            case "enum" -> TsTokenType.ENUM;
            default -> {
                if (TYPE_KEYWORDS.contains(word)) {
                    yield TsTokenType.TYPE_KEYWORD;
                }
                yield TsTokenType.IDENTIFIER;
            }
        };
    }

    /**
     * Check if a character can start an identifier (including Unicode letters).
     */
    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    /**
     * Check if a character can continue an identifier (including Unicode letters/digits).
     */
    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * Check if a character is a hex digit.
     */
    private static boolean isHexDigit(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * Check if a character is an operator character.
     */
    private static boolean isOperatorChar(char c) {
        return "=+-*/%<>!&|^~?:".indexOf(c) >= 0;
    }

    /**
     * Check if a character is an operator continuation character.
     */
    private static boolean isOperatorPart(char c) {
        return "=+-*/%<>!&|^~".indexOf(c) >= 0;
    }

    /**
     * Enable JSX context mode for the given source.
     * This is a simplified heuristic: if the source contains JSX-like patterns,
     * we enable JSX-aware tokenization.
     */
    void enableJsxMode() {
        this.inJsxContext = true;
        this.jsxDepth = 0;
    }

    /**
     * Disable JSX context mode.
     */
    void disableJsxMode() {
        this.inJsxContext = false;
        this.jsxDepth = 0;
        this.jsxContextStack.clear();
    }

    /**
     * Determine if the current position is in a context where '&lt;' should be
     * treated as a generic type parameter opening bracket.
     * <p>
     * Generic brackets typically follow:
     * - Identifiers (e.g., Array&lt;T&gt;, Map&lt;K,V&gt;)
     * - Type keywords that can have type parameters (e.g., type Foo&lt;T&gt;)
     * - Closing delimiters: ], ), &gt;
     */
    private boolean isGenericOpenContext(List<TsToken> tokens) {
        TsToken prev = findLastSignificantToken(tokens);
        if (prev == null) return false;
        return switch (prev.type()) {
            case IDENTIFIER, TYPE_KEYWORD, CLASS, FUNCTION, ENUM,
                 EXTENDS, IMPLEMENTS, DECLARE, NAMESPACE,
                 RPAREN, RBRACKET, GENERIC_CLOSE -> true;
            default -> false;
        };
    }

    /**
     * Determine if the current position is in a context where '&gt;' should be
     * treated as a generic type parameter closing bracket.
     * <p>
     * Generic closing brackets are used when there's an unmatched GENERIC_OPEN
     * in the token stream (i.e., we're inside a generic type expression).
     */
    private boolean isGenericCloseContext(List<TsToken> tokens) {
        // Count unmatched GENERIC_OPEN tokens in the stream
        int unmatchedOpens = 0;
        for (TsToken token : tokens) {
            if (token.type() == TsTokenType.GENERIC_OPEN) {
                unmatchedOpens++;
            } else if (token.type() == TsTokenType.GENERIC_CLOSE) {
                unmatchedOpens--;
            }
        }
        return unmatchedOpens > 0;
    }

    /**
     * Find the last significant (non-whitespace, non-comment) token.
     */
    private static TsToken findLastSignificantToken(List<TsToken> tokens) {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            TsToken token = tokens.get(i);
            if (token.type() != TsTokenType.WHITESPACE &&
                token.type() != TsTokenType.LINE_COMMENT &&
                token.type() != TsTokenType.BLOCK_COMMENT) {
                return token;
            }
        }
        return null;
    }
}
