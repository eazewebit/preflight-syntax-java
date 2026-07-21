package com.neel.syntaxvalidation.validator.typescript;

/**
 * Immutable token produced by the {@link TypeScriptSyntaxTokenizer}.
 *
 * @param type     the token category
 * @param lexeme   the raw source text for this token
 * @param line     the 1-based line number where the token starts
 * @param column   the 1-based column number where the token starts
 */
record TsToken(TsTokenType type, String lexeme, int line, int column) {

    TsToken {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (lexeme == null) {
            throw new IllegalArgumentException("lexeme must not be null");
        }
        if (line < 0) {
            throw new IllegalArgumentException("line must not be negative, got: " + line);
        }
        if (column < 0) {
            throw new IllegalArgumentException("column must not be negative, got: " + column);
        }
    }
}
