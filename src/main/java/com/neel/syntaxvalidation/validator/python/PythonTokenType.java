package com.neel.syntaxvalidation.validator.python;

/**
 * Token types for the Python 3.14 lexical analyser.
 *
 * <p>This enum covers the complete set of tokens recognised by the
 * hand-written lexer, including Python 3.14-specific syntax such as
 * template string literals (PEP 750) and deferred annotations (PEP 649).</p>
 *
 * @since 2.0.0
 */
enum PythonTokenType {

    // ───────────── Literals ─────────────
    /** Integer literal (decimal, hex, octal, binary, with underscores). */
    INTEGER_LITERAL,
    /** Floating-point literal (decimal or scientific). */
    FLOAT_LITERAL,
    /** Complex literal (e.g., {@code 3j}, {@code 2.5J}). */
    COMPLEX_LITERAL,
    /** String literal (single, double, triple-quoted, byte, raw, f-string, t-string). */
    STRING_LITERAL,
    /** Boolean literal {@code True} or {@code False}. */
    BOOLEAN_LITERAL,
    /** The {@code None} literal. */
    NONE_LITERAL,
    /** The {@code ...} (Ellipsis) literal. */
    ELLIPSIS_LITERAL,

    // ───────────── Identifiers & Keywords ─────────────
    /** User-defined identifier. */
    IDENTIFIER,

    // Soft keywords (context-dependent)
    /** The {@code match} soft keyword. */
    MATCH,
    /** The {@code case} soft keyword. */
    CASE,
    /** The {@code type} soft keyword. */
    TYPE,
    /** The {@code _} wildcard pattern. */
    WILDCARD,

    // Hard keywords
    /** {@code False} keyword (also a literal). */
    KW_FALSE,
    /** {@code None} keyword (also a literal). */
    KW_NONE,
    /** {@code True} keyword (also a literal). */
    KW_TRUE,
    /** {@code and} keyword. */
    KW_AND,
    /** {@code as} keyword. */
    KW_AS,
    /** {@code assert} keyword. */
    KW_ASSERT,
    /** {@code async} keyword. */
    KW_ASYNC,
    /** {@code await} keyword. */
    KW_AWAIT,
    /** {@code break} keyword. */
    KW_BREAK,
    /** {@code class} keyword. */
    KW_CLASS,
    /** {@code continue} keyword. */
    KW_CONTINUE,
    /** {@code def} keyword. */
    KW_DEF,
    /** {@code del} keyword. */
    KW_DEL,
    /** {@code elif} keyword. */
    KW_ELIF,
    /** {@code else} keyword. */
    KW_ELSE,
    /** {@code except} keyword. */
    KW_EXCEPT,
    /** {@code finally} keyword. */
    KW_FINALLY,
    /** {@code for} keyword. */
    KW_FOR,
    /** {@code from} keyword. */
    KW_FROM,
    /** {@code global} keyword. */
    KW_GLOBAL,
    /** {@code if} keyword. */
    KW_IF,
    /** {@code import} keyword. */
    KW_IMPORT,
    /** {@code in} keyword. */
    KW_IN,
    /** {@code is} keyword. */
    KW_IS,
    /** {@code lambda} keyword. */
    KW_LAMBDA,
    /** {@code nonlocal} keyword. */
    KW_NONLOCAL,
    /** {@code not} keyword. */
    KW_NOT,
    /** {@code or} keyword. */
    KW_OR,
    /** {@code pass} keyword. */
    KW_PASS,
    /** {@code raise} keyword. */
    KW_RAISE,
    /** {@code return} keyword. */
    KW_RETURN,
    /** {@code try} keyword. */
    KW_TRY,
    /** {@code while} keyword. */
    KW_WHILE,
    /** {@code with} keyword. */
    KW_WITH,
    /** {@code yield} keyword. */
    KW_YIELD,

    // ───────────── Operators ─────────────
    /** {@code +} */
    PLUS,
    /** {@code -} */
    MINUS,
    /** {@code *} */
    STAR,
    /** {@code **} */
    DOUBLE_STAR,
    /** {@code /} */
    SLASH,
    /** {@code //} */
    DOUBLE_SLASH,
    /** {@code %} */
    PERCENT,
    /** {@code @} (matrix multiplication / decorator). */
    AT,
    /** {@code <<} */
    LEFT_SHIFT,
    /** {@code >>} */
    RIGHT_SHIFT,
    /** {@code &} */
    AMPERSAND,
    /** {@code |} */
    PIPE,
    /** {@code ^} */
    CARET,
    /** {@code ~} */
    TILDE,
    /** {@code :=} (walrus operator). */
    COLON_EQUAL,
    /** {@code <} */
    LESS,
    /** {@code >} */
    GREATER,
    /** {@code <=} */
    LESS_EQUAL,
    /** {@code >=} */
    GREATER_EQUAL,
    /** {@code ==} */
    EQUAL_EQUAL,
    /** {@code !=} */
    NOT_EQUAL,

    // ───────────── Delimiters ─────────────
    /** {@code (} */
    LEFT_PAREN,
    /** {@code )} */
    RIGHT_PAREN,
    /** {@code [} */
    LEFT_BRACKET,
    /** {@code ]} */
    RIGHT_BRACKET,
    /** {@code {} */
    LEFT_BRACE,
    /** {@code }} */
    RIGHT_BRACE,
    /** {@code } */
    COMMA,
    /** {@code :} */
    COLON,
    /** {@code ;} */
    SEMICOLON,
    /** {@code .} */
    DOT,
    /** {@code ->} */
    ARROW,
    /** {@code =} */
    EQUAL,
    /** {@code +=} */
    PLUS_EQUAL,
    /** {@code -=} */
    MINUS_EQUAL,
    /** {@code *=} */
    STAR_EQUAL,
    /** {@code /=} */
    SLASH_EQUAL,
    /** {@code //=} */
    DOUBLE_SLASH_EQUAL,
    /** {@code %=} */
    PERCENT_EQUAL,
    /** {@code @=} */
    AT_EQUAL,
    /** {@code &=} */
    AMPERSAND_EQUAL,
    /** {@code |=} */
    PIPE_EQUAL,
    /** {@code ^=} */
    CARET_EQUAL,
    /** {@code >>=} */
    RIGHT_SHIFT_EQUAL,
    /** {@code <<=} */
    LEFT_SHIFT_EQUAL,
    /** {@code **=} */
    DOUBLE_STAR_EQUAL,

    // ───────────── Structural ─────────────
    /** Newline token (logical line separator). */
    NEWLINE,
    /** Indentation increase. */
    INDENT,
    /** Indentation decrease. */
    DEDENT,
    /** End-of-file marker. */
    EOF,
    /** Explicit line continuation ({@code \}). */
    LINE_CONTINUATION,
    /** Comment (not emitted to parser). */
    COMMENT,
}