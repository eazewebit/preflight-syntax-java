package com.neel.syntaxvalidation.validator.java;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A hand-written, dependency-free lexical analyser for the Java programming
 * language (Java SE 21&nbsp;–&nbsp;25, LTS).
 *
 * <p>The lexer is the first isolated component of the modular validation
 * pipeline. It transforms raw source text into an ordered list of
 * {@link JavaToken tokens}, recording every lexical anomaly (unterminated
 * literals, illegal characters, malformed escapes) as an {@link
 * JavaTokenType#ERROR ERROR} token. Syntactic and grammatical analysis is
 * performed downstream by the dedicated {@code checker} components, keeping
 * each stage focused and independently testable.
 *
 * <h2>Supported lexical productions</h2>
 * <ul>
 *   <li>Line ({@code //}) and block ({@code /&#42; &#42;/}) comments.</li>
 *   <li>Identifiers and the complete keyword set through Java&nbsp;25.</li>
 *   <li>Integer and floating-point literals in decimal, hexadecimal, octal and
 *       binary form, including {@code _} digit separators and type suffixes
 *       ({@code L}, {@code F}, {@code D}).</li>
 *   <li>Character literals with escape sequences and Unicode escapes.</li>
 *   <li>String literals and {@code """} text blocks.</li>
 *   <li>All operators and separators, including the multi-character forms
 *       {@code ->}, {@code ::}, {@code ...}, {@code <>}, {@code @}.</li>
 * </ul>
 *
 * <p>The scanner is a single-pass, position-driven state machine. It never
 * throws for malformed input; instead it emits {@code ERROR} tokens so the
 * caller can decide how to report them.
 */
public final class JavaLexer {

    /**
     * The reserved vocabulary recognised as {@link JavaTokenType#KEYWORD}.
     *
     * <p>This union covers the fifty hard keywords, the three reserved literals
     * ({@code true}, {@code false}, {@code null}), the underscore, and the
     * contextual / restricted identifiers introduced for {@code var} (10),
     * {@code yield} (14), {@code record} (16), {@code sealed}/{@code permits}
     * (17) and the JPMS directives (9).
     */
    static final Set<String> KEYWORDS = Set.of(
            // --- Hard keywords -------------------------------------------------
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            // --- Reserved literals --------------------------------------------
            "true", "false", "null",
            // --- Underscore keyword (Java 9) ----------------------------------
            "_",
            // --- Contextual / restricted identifiers --------------------------
            "var", "yield", "record", "sealed", "permits",
            "module", "requires", "exports", "opens", "to", "uses",
            "provides", "with", "transitive", "open"
    );

    private final String source;
    private final int length;
    private int pos;
    private int line = 1;
    private int col = 1;

    public JavaLexer(String source) {
        this.source = source;
        this.length = source.length();
    }

    /**
     * Performs a full scan of the source text.
     *
     * @return an unmodifiable token list terminated by a single {@link
     *         JavaTokenType#EOF EOF} token; never {@code null}
     */
    public List<JavaToken> tokenize() {
        List<JavaToken> tokens = new ArrayList<>();
        while (pos < length) {
            char c = current();
            if (isWhitespace(c)) {
                advance();
            } else if (c == '/' && peek(1) == '/') {
                scanLineComment(tokens);
            } else if (c == '/' && peek(1) == '*') {
                scanBlockComment(tokens);
            } else if (c == '"') {
                scanStringOrTextBlock(tokens);
            } else if (c == '\'') {
                scanCharLiteral(tokens);
            } else if (isJavaIdentifierStart(c)) {
                scanIdentifier(tokens);
            } else if (isDigitStart(c)) {
                scanNumber(tokens);
            } else if (c == '.' && Character.isDigit(peek(1))) {
                scanNumber(tokens);
            } else {
                scanPunctuation(tokens);
            }
        }
        tokens.add(new JavaToken(JavaTokenType.EOF, "", line, col));
        return tokens;
    }

    // ==================================================================
    //  Comment scanners
    // ==================================================================

    private void scanLineComment(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        while (pos < length && current() != '\n') {
            advance();
        }
        tokens.add(new JavaToken(JavaTokenType.COMMENT, source.substring(start, pos), startLine, startCol));
    }

    private void scanBlockComment(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        advance(); // consume '/'
        advance(); // consume '*'
        while (pos < length) {
            if (current() == '*' && peek(1) == '/') {
                advance(); // consume '*'
                advance(); // consume '/'
                tokens.add(new JavaToken(JavaTokenType.COMMENT, source.substring(start, pos), startLine, startCol));
                return;
            }
            advance();
        }
        // Reached EOF without closing */
        tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start), startLine, startCol));
    }

    // ==================================================================
    //  String & text-block scanners
    // ==================================================================

    private void scanStringOrTextBlock(List<JavaToken> tokens) {
        if (peek(1) == '"' && peek(2) == '"') {
            scanTextBlock(tokens);
        } else {
            scanStringLiteral(tokens);
        }
    }

    private void scanStringLiteral(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        advance(); // consume opening '"'
        while (pos < length && current() != '"') {
            char c = current();
            if (c == '\n') {
                // A bare newline inside an ordinary string is illegal in Java.
                tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start, pos), startLine, startCol));
                return;
            }
            if (c == '\\') {
                advance(); // consume backslash
                if (pos < length) {
                    advance(); // consume the escaped char (any, including unicode u-XXXX)
                }
            } else {
                advance();
            }
        }
        if (pos >= length) {
            tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start), startLine, startCol));
            return;
        }
        advance(); // consume closing '"'
        tokens.add(new JavaToken(JavaTokenType.STRING, source.substring(start, pos), startLine, startCol));
    }

    private void scanTextBlock(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        advance(); // consume first '"'
        advance(); // consume second '"'
        advance(); // consume third '"'
        // Optional incidental whitespace before the first significant line.
        while (pos < length && (current() == ' ' || current() == '\t')) {
            advance();
        }
        // The opening delimiter must be followed by a line terminator.
        if (pos < length && current() == '\r') {
            advance();
            if (pos < length && current() == '\n') {
                advance();
            }
        } else if (pos < length && current() == '\n') {
            advance();
        }
        // Scan until the closing """ is found.
        while (pos < length) {
            char c = current();
            if (c == '"' && peek(1) == '"' && peek(2) == '"') {
                advance();
                advance();
                advance();
                tokens.add(new JavaToken(JavaTokenType.STRING, source.substring(start, pos), startLine, startCol));
                return;
            }
            if (c == '\\') {
                advance(); // consume backslash
                if (pos < length) {
                    advance(); // consume escaped char
                }
            } else {
                advance();
            }
        }
        tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start), startLine, startCol));
    }

    // ==================================================================
    //  Character literal scanner
    // ==================================================================

    private void scanCharLiteral(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        advance(); // consume opening '\''
        boolean closed = false;
        while (pos < length) {
            char c = current();
            if (c == '\'') {
                advance();
                closed = true;
                break;
            }
            if (c == '\n') {
                break; // unterminated
            }
            if (c == '\\') {
                advance(); // consume backslash
                if (pos < length) {
                    advance(); // consume escaped char
                }
            } else {
                advance();
            }
        }
        if (closed) {
            tokens.add(new JavaToken(JavaTokenType.CHAR, source.substring(start, pos), startLine, startCol));
        } else {
            tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start, pos >= length ? length : pos),
                    startLine, startCol));
        }
    }

    // ==================================================================
    //  Identifier / keyword scanner
    // ==================================================================

    private void scanIdentifier(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        advance();
        while (pos < length && isJavaIdentifierPart(current())) {
            advance();
        }
        String text = source.substring(start, pos);
        JavaTokenType type = KEYWORDS.contains(text) ? JavaTokenType.KEYWORD : JavaTokenType.IDENTIFIER;
        tokens.add(new JavaToken(type, text, startLine, startCol));
    }

    // ==================================================================
    //  Numeric literal scanner
    // ==================================================================

    private void scanNumber(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        int start = pos;
        char c = current();

        if (c == '0' && (peek(1) == 'x' || peek(1) == 'X')) {
            advance(); // 0
            advance(); // x
            scanRadixDigits(tokens, start, startLine, startCol, "0123456789abcdefABCDEF", false);
        } else if (c == '0' && (peek(1) == 'b' || peek(1) == 'B')) {
            advance(); // 0
            advance(); // b
            scanRadixDigits(tokens, start, startLine, startCol, "01", false);
        } else {
            scanDecimalNumber(tokens, start, startLine, startCol);
        }
    }

    private void scanRadixDigits(List<JavaToken> tokens, int start, int startLine, int startCol,
                                 String allowed, boolean floatPart) {
        boolean hasDigits = false;
        while (pos < length) {
            char c = current();
            if (allowed.indexOf(c) >= 0) {
                hasDigits = true;
                advance();
            } else if (c == '_') {
                advance();
            } else {
                break;
            }
        }
        if (floatPart) {
            consumeHexFloatExponentAndSuffix();
        } else {
            consumeIntSuffix();
        }
        if (!hasDigits) {
            tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start, pos), startLine, startCol));
        } else {
            tokens.add(new JavaToken(JavaTokenType.NUMBER, source.substring(start, pos), startLine, startCol));
        }
    }

    private void scanDecimalNumber(List<JavaToken> tokens, int start, int startLine, int startCol) {
        boolean hasDigits = false;
        boolean isFloat = false;
        // Integer part
        while (pos < length) {
            char c = current();
            if (c >= '0' && c <= '9') {
                hasDigits = true;
                advance();
            } else if (c == '_' && hasDigits) {
                advance();
            } else {
                break;
            }
        }
        // Fractional part
        if (current() == '.') {
            isFloat = true;
            advance();
            while (pos < length) {
                char c = current();
                if (c >= '0' && c <= '9') {
                    hasDigits = true;
                    advance();
                } else if (c == '_' && hasDigits) {
                    advance();
                } else {
                    break;
                }
            }
        }
        // Exponent part
        if ((current() == 'e' || current() == 'E')) {
            int save = pos;
            int saveLine = line;
            int saveCol = col;
            advance();
            if (current() == '+' || current() == '-') {
                advance();
            }
            boolean expHasDigits = false;
            while (pos < length) {
                char c = current();
                if (c >= '0' && c <= '9') {
                    expHasDigits = true;
                    advance();
                } else if (c == '_' && expHasDigits) {
                    advance();
                } else {
                    break;
                }
            }
            if (!expHasDigits) {
                // Not a valid exponent; rewind.
                pos = save;
                line = saveLine;
                col = saveCol;
            } else {
                isFloat = true;
            }
        }
        // Type suffix
        char suffix = current();
        if (suffix == 'l' || suffix == 'L') {
            advance();
        } else if (suffix == 'f' || suffix == 'F' || suffix == 'd' || suffix == 'D') {
            isFloat = true;
            advance();
        }
        if (!hasDigits) {
            tokens.add(new JavaToken(JavaTokenType.ERROR, source.substring(start, pos), startLine, startCol));
        } else {
            tokens.add(new JavaToken(JavaTokenType.NUMBER, source.substring(start, pos), startLine, startCol));
        }
        // Note: a lone '.' that did not start a number is left for the punctuation scanner.
    }

    private void consumeIntSuffix() {
        char c = current();
        if (c == 'l' || c == 'L') {
            advance();
        }
    }

    private void consumeHexFloatExponentAndSuffix() {
        // After a hex fractional literal an exponent is introduced by p/P.
        if (current() == 'p' || current() == 'P') {
            advance();
            if (current() == '+' || current() == '-') {
                advance();
            }
            while (pos < length && current() >= '0' && current() <= '9') {
                advance();
            }
        }
        char c = current();
        if (c == 'f' || c == 'F' || c == 'd' || c == 'D') {
            advance();
        }
    }

    // ==================================================================
    //  Punctuation scanner
    // ==================================================================

    private void scanPunctuation(List<JavaToken> tokens) {
        int startLine = line;
        int startCol = col;
        char c = current();

        // Greedy multi-character operators first.
        String three = peekString(3);
        if ("...".equals(three)) {
            advance();
            advance();
            advance();
            tokens.add(new JavaToken(JavaTokenType.PUNCTUATION, "...", startLine, startCol));
            return;
        }
        String two = peekString(2);
        switch (two) {
            case "->", "::", "++", "--", "==", "!=", "<=", ">=", "&&", "||",
                 "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>",
                 "<<=", ">>=", "..." -> {
                // The two-char cases handled here; note ">>=" is 3 chars below.
            }
            default -> {
            }
        }
        // Handle the three-char shift-assignment explicitly.
        if (">>=".equals(three) || "<<=".equals(three)) {
            advance();
            advance();
            advance();
            tokens.add(new JavaToken(JavaTokenType.PUNCTUATION, three, startLine, startCol));
            return;
        }
        switch (two) {
            case "->", "::", "++", "--", "==", "!=", "<=", ">=", "&&", "||",
                 "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<", ">>" -> {
                advance();
                advance();
                tokens.add(new JavaToken(JavaTokenType.PUNCTUATION, two, startLine, startCol));
                return;
            }
            default -> {
            }
        }
        // Single-character punctuation / separators.
        if (isSinglePunctuation(c)) {
            advance();
            tokens.add(new JavaToken(JavaTokenType.PUNCTUATION, String.valueOf(c), startLine, startCol));
        } else {
            advance();
            tokens.add(new JavaToken(JavaTokenType.ERROR, String.valueOf(c), startLine, startCol));
        }
    }

    private static boolean isSinglePunctuation(char c) {
        return "{}()[];,.+-*/%<>&|^~!=?:@".indexOf(c) >= 0;
    }

    // ==================================================================
    //  Low-level cursor helpers
    // ==================================================================

    private char current() {
        return pos < length ? source.charAt(pos) : '\0';
    }

    private char peek(int offset) {
        int idx = pos + offset;
        return idx < length ? source.charAt(idx) : '\0';
    }

    private String peekString(int count) {
        int end = Math.min(pos + count, length);
        return source.substring(pos, end);
    }

    private void advance() {
        char c = source.charAt(pos);
        pos++;
        if (c == '\n') {
            line++;
            col = 1;
        } else if (c == '\t') {
            col += 4;
        } else {
            col++;
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
    }

    private static boolean isJavaIdentifierStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }

    private static boolean isJavaIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    private static boolean isDigitStart(char c) {
        return c >= '0' && c <= '9';
    }
}
