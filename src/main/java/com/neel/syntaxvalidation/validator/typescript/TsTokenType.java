package com.neel.syntaxvalidation.validator.typescript;

/**
 * Token types used by the TypeScript/TSX/JSX hand-written syntax tokenizer.
 * <p>
 * Each constant represents a distinct lexical token category that the
 * {@link TypeScriptSyntaxTokenizer} can produce during a single left-to-right
 * scan of source code.
 */
enum TsTokenType {

    // ── Literals ────────────────────────────────────────────────────────
    /** Numeric literal (integer or floating-point). */
    NUMBER,
    /** String literal delimited by single, double, or backtick quotes. */
    STRING,
    /** Template literal part (between {@code `${} } expressions). */
    TEMPLATE_LITERAL,
    /** {@code true} or {@code false} keyword. */
    BOOLEAN,
    /** The {@code null} keyword. */
    NULL_LITERAL,
    /** A regular expression literal delimited by {@code /}. */
    REGEX,

    // ── Identifiers & Keywords ──────────────────────────────────────────
    /** An identifier (variable, function, class name, etc.). */
    IDENTIFIER,
    /** A TypeScript type annotation keyword or built-in type name (e.g. {@code string}, {@code number}, {@code interface}, {@code type}). */
    TYPE_KEYWORD,
    /** {@code import} keyword. */
    IMPORT,
    /** {@code from} keyword. */
    FROM,
    /** {@code export} keyword. */
    EXPORT,
    /** {@code default} keyword. */
    DEFAULT,
    /** {@code as} keyword. */
    AS,
    /** {@code typeof} keyword. */
    TYPEOF,
    /** {@code keyof} keyword. */
    KEYOF,
    /** {@code extends} keyword. */
    EXTENDS,
    /** {@code implements} keyword. */
    IMPLEMENTS,
    /** {@code declare} keyword. */
    DECLARE,
    /** {@code namespace} keyword. */
    NAMESPACE,
    /** {@code enum} keyword. */
    ENUM,

    // ── Declaration Keywords ────────────────────────────────────────────
    /** {@code var}, {@code let}, or {@code const}. */
    VARIABLE_DECL,
    /** {@code function} keyword. */
    FUNCTION,
    /** {@code class} keyword. */
    CLASS,
    /** {@code return} keyword. */
    RETURN,
    /** {@code if} keyword. */
    IF,
    /** {@code else} keyword. */
    ELSE,
    /** {@code for} keyword. */
    FOR,
    /** {@code while} keyword. */
    WHILE,
    /** {@code do} keyword. */
    DO,
    /** {@code switch} keyword. */
    SWITCH,
    /** {@code case} keyword. */
    CASE,
    /** {@code break} keyword. */
    BREAK,
    /** {@code continue} keyword. */
    CONTINUE,
    /** {@code try} keyword. */
    TRY,
    /** {@code catch} keyword. */
    CATCH,
    /** {@code finally} keyword. */
    FINALLY,
    /** {@code throw} keyword. */
    THROW,
    /** {@code new} keyword. */
    NEW,
    /** {@code delete} keyword. */
    DELETE,
    /** {@code void} keyword. */
    VOID,
    /** {@code this} keyword. */
    THIS,
    /** {@code super} keyword. */
    SUPER,
    /** {@code yield} keyword. */
    YIELD,
    /** {@code async} keyword. */
    ASYNC,
    /** {@code await} keyword. */
    AWAIT,
    /** {@code of} keyword (used in for-of). */
    OF,
    /** {@code in} keyword (used in for-in, type operators). */
    IN,

    // ── TSX/JSX ────────────────────────────────────────────────────────
    /** Opening JSX tag bracket: {@code <}. */
    JSX_TAG_OPEN,
    /** Closing JSX tag bracket: {@code >}. */
    JSX_TAG_CLOSE,
    /** Self-closing JSX tag: {@code />}. */
    JSX_SELF_CLOSE,
    /** JSX attribute assignment: {@code =}. */
    JSX_ATTR_EQ,

    // ── Operators ───────────────────────────────────────────────────────
    /** An operator symbol (e.g. {@code +}, {@code -}, {@code ===}, {@code =>}). */
    OPERATOR,
    /** The {@code ?} operator (conditional/optional). */
    QUESTION,
    /** The {@code :} separator (type annotation, ternary, etc.). */
    COLON,
    /** The {@code ...} spread/rest operator. */
    SPREAD,

    // ── Delimiters ──────────────────────────────────────────────────────
    /** Opening parenthesis {@code (}. */
    LPAREN,
    /** Closing parenthesis {@code )}. */
    RPAREN,
    /** Opening brace {@code {}. */
    LBRACE,
    /** Closing brace {@code }}. */
    RBRACE,
    /** Opening bracket {@code [}. */
    LBRACKET,
    /** Closing bracket {@code ]}. */
    RBRACKET,
    /** Semicolon {@code ;}. */
    SEMICOLON,
    /** Comma {@code }. */
    COMMA,
    /** Dot {@code .}. */
    DOT,

    // ── Generics ────────────────────────────────────────────────────────
    /** Opening angle bracket for generic type (e.g. {@code <T>}). */
    GENERIC_OPEN,
    /** Closing angle bracket for generic type. */
    GENERIC_CLOSE,

    // ── Special ─────────────────────────────────────────────────────────
    /** Single-line comment ({@code //}). */
    LINE_COMMENT,
    /** Multi-line comment ({@code /​* ... *​/}). */
    BLOCK_COMMENT,
    /** Whitespace (spaces, tabs, newlines). */
    WHITESPACE,
    /** End-of-file sentinel. */
    EOF
}