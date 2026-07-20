package com.neel.syntaxvalidation.validator.python;

import java.util.Objects;

/**
 * Immutable token produced by the Python lexical analyser.
 *
 * <p>Each token carries its type, literal text, 1-based line and column
 * positions, and an optional indentation level (for INDENT/DEDENT tokens).</p>
 *
 * @since 2.0.0
 */
record PythonToken(
        PythonTokenType type,
        String text,
        int line,
        int column,
        int indentLevel
) {

    /**
     * Creates a token without indentation information.
     *
     * @param type   token type
     * @param text   literal text
     * @param line   1-based line number
     * @param column 1-based column number
     */
    PythonToken(PythonTokenType type, String text, int line, int column) {
        this(type, text, line, column, -1);
    }

    @Override
    public String toString() {
        String indent = indentLevel >= 0 ? ", indent=" + indentLevel : "";
        return type + "[" + text + "](" + line + ":" + column + indent + ")";
    }
}