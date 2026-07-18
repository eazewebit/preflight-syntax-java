package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import com.neel.syntaxvalidation.validator.java.JavaTokenType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Verifies that the three unambiguous grouping delimiters &mdash;
 * {@code (}&nbsp;{@code )}, {@code [}&nbsp;{@code ]} and {@code {}&nbsp;
 * {@code }} &mdash; are correctly balanced and properly nested.
 *
 * <p>Angle brackets ({@code <}, {@code >}) are intentionally <em>not</em>
 * checked here because in Java they are overloaded: they serve both as
 * comparison / shift operators and as generics delimiters. A purely lexical
 * balancer cannot reliably distinguish the two without full semantic context
 * (consider {@code a < b && c > d} versus {@code List<Map<K, V>>}), so
 * angle-bracket mismatches are deferred to the {@code javac} binary phase,
 * which has the complete type-checking machinery.
 *
 * <p>Parentheses, brackets and braces, by contrast, are <em>always</em>
 * grouping delimiters in Java, making their balance-check both precise and
 * free of false positives.
 */
public final class DelimiterBalanceChecker implements SyntaxChecker {

    /** Records an opening delimiter awaiting its match. */
    private record Open(char ch, int line, int column) {
    }

    @Override
    public void check(List<JavaToken> tokens, List<ValidationError> errors) {
        Deque<Open> stack = new ArrayDeque<>();

        for (JavaToken token : tokens) {
            if (token.type() != JavaTokenType.PUNCTUATION) {
                continue;
            }
            String text = token.text();
            if (text.length() != 1) {
                continue; // multi-char operators cannot be delimiters
            }
            char c = text.charAt(0);

            switch (c) {
                case '(', '[', '{' -> stack.push(new Open(c, token.line(), token.column()));
                case ')', ']', '}' -> matchClose(c, token, stack, errors);
                default -> {
                    /* not a delimiter */
                }
            }
        }

        // Anything still on the stack was never closed.
        while (!stack.isEmpty()) {
            Open open = stack.pop();
            errors.add(new ValidationError(
                    open.line,
                    open.column,
                    "Unclosed '" + open.ch + "' — matching '" + expectedClose(open.ch) + "' not found",
                    String.valueOf(open.ch)));
        }
    }

    private void matchClose(char close, JavaToken token, Deque<Open> stack, List<ValidationError> errors) {
        char expectedOpener = expectedOpen(close);
        if (stack.isEmpty()) {
            errors.add(new ValidationError(
                    token.line(),
                    token.column(),
                    "Unexpected '" + close + "' — no matching '" + expectedOpener + "'",
                    String.valueOf(close)));
            return;
        }
        Open top = stack.peek();
        if (top.ch != expectedOpener) {
            errors.add(new ValidationError(
                    token.line(),
                    token.column(),
                    "Mismatched '" + close + "' — expected '" + expectedClose(top.ch)
                            + "' to close the '" + top.ch + "' opened at line " + top.line + ":" + top.column,
                    String.valueOf(close)));
            // Do not pop: the original opener is still awaiting its real match.
        } else {
            stack.pop();
        }
    }

    private static char expectedClose(char open) {
        return switch (open) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            default -> open;
        };
    }

    private static char expectedOpen(char close) {
        return switch (close) {
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            default -> close;
        };
    }
}
