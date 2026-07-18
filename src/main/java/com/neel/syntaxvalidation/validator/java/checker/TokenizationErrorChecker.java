package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import com.neel.syntaxvalidation.validator.java.JavaTokenType;

import java.util.List;

/**
 * Surfaces every {@link JavaTokenType#ERROR ERROR} token that the lexer
 * could not classify.
 *
 * <p>Lexical anomalies such as an unterminated string, an unterminated block
 * comment, a bare newline inside a character literal or a stray non-ASCII
 * symbol are captured here as structured diagnostics with the exact source
 * position. This is deliberately the simplest checker: it performs no
 * cross-token reasoning, it merely translates lexer signals into
 * {@link ValidationError}s.
 */
public final class TokenizationErrorChecker implements SyntaxChecker {

    @Override
    public void check(List<JavaToken> tokens, List<ValidationError> errors) {
        for (JavaToken token : tokens) {
            if (token.type() == JavaTokenType.ERROR) {
                String snippet = truncate(token.text());
                errors.add(new ValidationError(
                        token.line(),
                        token.column(),
                        "Lexical error near: '" + snippet + "'",
                        token.text()));
            }
        }
    }

    private static String truncate(String text) {
        return text.length() <= 40 ? text : text.substring(0, 40) + "\u2026";
    }
}
