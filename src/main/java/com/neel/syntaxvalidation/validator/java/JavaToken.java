package com.neel.syntaxvalidation.validator.java;

import java.util.Objects;

/**
 * An immutable, single lexical unit produced by {@link JavaLexer}.
 *
 * <p>Each token records its {@link JavaTokenType type}, the raw source text it
 * spans and its 1-based {@code line}/{@code column} position so that the
 * downstream {@code checker} components can emit precise, human-readable
 * diagnostics.
 *
 * @param type    the token category; never {@code null}
 * @param text    the exact source text the token covers; never {@code null}
 * @param line    the 1-based line on which the token starts
 * @param column  the 1-based column on which the token starts
 */
public record JavaToken(JavaTokenType type, String text, int line, int column) {

    public JavaToken {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1: " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1: " + column);
        }
    }

    @Override
    public String toString() {
        return type + "[" + line + ":" + column + "] " + text;
    }
}
