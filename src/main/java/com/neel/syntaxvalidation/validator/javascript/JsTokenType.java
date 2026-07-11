package com.neel.syntaxvalidation.validator.javascript;

/**
 * Categorises every lexical unit emitted by {@link JavaScriptSyntaxTokenizer}.
 *
 * <p>The type is deliberately coarse-grained: the validation engine only needs
 * to distinguish structural elements (keywords, punctuation) from opaque
 * literals (strings, numbers, templates, regexes) and from diagnostics
 * ({@link #ERROR}).
 */
enum JsTokenType {

    /** A numeric literal in any supported base (decimal, hex, binary, octal, BigInt). */
    NUMBER,

    /** A single- or double-quoted string literal. */
    STRING,

    /** A template (backtick) literal, including all {@code ${...}} interpolations. */
    TEMPLATE,

    /** A regular-expression literal. */
    REGEX,

    /** An identifier that is not a reserved word. */
    IDENTIFIER,

    /** A JavaScript keyword or reserved word. */
    KEYWORD,

    /** An operator or punctuation mark, possibly multi-character (e.g. {@code =>}, {@code ...}). */
    PUNCTUATION,

    /** A line or block comment. */
    COMMENT,

    /** A lexical error such as an unterminated string or block comment. */
    ERROR,

    /** A sentinel marking the end of the token stream. */
    EOF
}
