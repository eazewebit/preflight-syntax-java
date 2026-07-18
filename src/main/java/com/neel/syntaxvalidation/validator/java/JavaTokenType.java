package com.neel.syntaxvalidation.validator.java;

/**
 * Categorises every lexical unit emitted by {@link JavaLexer}.
 *
 * <p>The type is deliberately coarse-grained: the validation engine only needs
 * to distinguish structural elements (keywords, punctuation) from opaque
 * literals (strings, characters, numbers) and from diagnostics
 * ({@link #ERROR}). This keeps the grammar-checking layer simple while still
 * allowing the individual {@code checker} components to focus on their own
 * well-defined responsibility.
 */
public enum JavaTokenType {

    /** A numeric literal in any supported base (decimal, hex, binary, octal, with optional suffix/float parts). */
    NUMBER,

    /** A double-quoted string literal or a {@code """} text block. */
    STRING,

    /** A single-quoted character literal, e.g. {@code 'a'}, {@code '\\n'}, {@code '\\u0041'}. */
    CHAR,

    /** An identifier that is not a reserved word or contextual keyword. */
    IDENTIFIER,

    /** A Java keyword, reserved word, restricted identifier, or reserved literal. */
    KEYWORD,

    /** An operator or separator, possibly multi-character (e.g. {@code ->}, {@code ::}, {@code ...}). */
    PUNCTUATION,

    /** A line ({@code //}) or block ({@code /&#42; &#42;/}) comment. */
    COMMENT,

    /** A lexical error such as an unterminated string/char/comment or an illegal character. */
    ERROR,

    /** A sentinel marking the end of the token stream. */
    EOF
}
