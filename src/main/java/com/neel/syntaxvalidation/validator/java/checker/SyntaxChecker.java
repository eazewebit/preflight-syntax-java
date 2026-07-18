package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaToken;

import java.util.List;

/**
 * A single, isolated unit of Java syntax analysis.
 *
 * <p>The modular validation pipeline is intentionally split into a family of
 * small, focused checkers so that each one can be developed, tested and
 * extended independently. This keeps the overall architecture clean and makes
 * it trivial to add a new check in the future &mdash; simply implement this
 * interface and register the checker with the {@code JavaSyntaxEngine}.
 *
 * <p>Implementations must be <em>stateless</em> with respect to the token list:
 * they receive an immutable view of the tokens produced by the lexer and append
 * any diagnostics they discover to the supplied {@code errors} sink. They
 * should never mutate the token list.
 */
public interface SyntaxChecker {

    /**
     * Inspects the token stream and appends any discovered diagnostics to
     * {@code errors}.
     *
     * @param tokens  the full token list, terminated by an {@code EOF} token;
     *                never {@code null} and never mutated by the checker
     * @param errors  the mutable sink to which diagnostics are appended;
     *                never {@code null}
     */
    void check(List<JavaToken> tokens, List<ValidationError> errors);
}
