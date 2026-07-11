package com.neel.syntaxvalidation.validator.javascript;

/**
 * An immutable, single lexical unit produced by {@link JavaScriptSyntaxTokenizer}.
 *
 * <p>Each token records its {@link JsTokenType type}, the raw source text (or a
 * descriptive label for opaque literals), and the 1-based line and column where
 * the token begins. The validation engine uses these positions to produce
 * precise, human-readable diagnostics.
 */
final class JsToken {

    /** The lexical category. */
    final JsTokenType type;

    /**
     * The raw source text of the token.
     *
     * <p>For {@link JsTokenType#PUNCTUATION} and {@link JsTokenType#KEYWORD} this
     * is the exact operator or keyword text (e.g. {@code "=>"}, {@code "const"}).
     * For {@link JsTokenType#IDENTIFIER} it is the identifier name. For opaque
     * literals ({@code NUMBER}, {@code STRING}, {@code TEMPLATE}, {@code REGEX},
     * {@code COMMENT}) a short placeholder is used. For {@link JsTokenType#ERROR}
     * it carries the diagnostic message.
     */
    final String text;

    /** 1-based line number where the token starts. */
    final int line;

    /** 1-based column number where the token starts. */
    final int column;

    JsToken(JsTokenType type, String text, int line, int column) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return type + "[" + text + "]@" + line + ":" + column;
    }
}
