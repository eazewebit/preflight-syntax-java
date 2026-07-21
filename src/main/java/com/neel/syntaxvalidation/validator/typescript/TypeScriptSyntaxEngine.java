package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Pure-Java syntax validation engine for TypeScript, TSX, and JSX source code.
 * <p>
 * This engine performs a single left-to-right scan via {@link TypeScriptSyntaxTokenizer}
 * and then checks the resulting token stream for structural syntactic issues such as:
 * <ul>
 *   <li>Unbalanced braces, parentheses, and brackets</li>
 *   <li>Unclosed string and template literals</li>
 *   <li>Unbalanced JSX tags</li>
 *   <li>Invalid token sequences (e.g. consecutive operators)</li>
 * </ul>
 * <p>
 * It does <em>not</em> perform full type-checking — that is the job of the external
 * {@code tsc} binary when available. This engine serves as a zero-dependency fallback.
 */
final class TypeScriptSyntaxEngine {

    private final TypeScriptSyntaxTokenizer tokenizer;

    TypeScriptSyntaxEngine() {
        this.tokenizer = new TypeScriptSyntaxTokenizer();
    }

    /**
     * Validate the given TypeScript/TSX/JSX source for syntactic correctness.
     *
     * @param source the source code to validate
     * @return a {@link ValidationResult} indicating pass or fail, with error details
     */
    ValidationResult validate(String source) {
        if (source == null || source.isBlank()) {
            return ValidationResult.valid("TypeScript source is empty — nothing to validate.");
        }

        List<TsToken> tokens;
        try {
            tokens = tokenizer.tokenize(source);
        } catch (Exception e) {
            return ValidationResult.invalid(
                    "TypeScript syntax validation failed due to tokenization error.",
                    new ValidationError(1, -1, "Syntax error: " + e.getMessage(), null));
        }

        List<ValidationError> errors = new ArrayList<>();
        Deque<TsToken> braceStack = new ArrayDeque<>();
        Deque<TsToken> parenStack = new ArrayDeque<>();
        Deque<TsToken> bracketStack = new ArrayDeque<>();
        Deque<String> jsxTagStack = new ArrayDeque<>();
        boolean inJsxTag = false;
        boolean inJsxContent = false;  // true when inside JSX element content
        int templateDepth = 0;

        for (int i = 0; i < tokens.size(); i++) {
            TsToken token = tokens.get(i);

            // Skip whitespace and comments for structural analysis
            if (token.type() == TsTokenType.WHITESPACE ||
                token.type() == TsTokenType.LINE_COMMENT ||
                token.type() == TsTokenType.BLOCK_COMMENT) {
                continue;
            }

            // Track template literal depth
            if (token.type() == TsTokenType.TEMPLATE_LITERAL) {
                String lexeme = token.lexeme();
                if (lexeme.endsWith("${")) {
                    templateDepth++;
                } else if (lexeme.startsWith("}") && templateDepth > 0) {
                    templateDepth--;
                }
                continue;
            }

            // ── Brace balancing ────────────────────────────────────────
            switch (token.type()) {
                case LBRACE -> braceStack.push(token);
                case RBRACE -> {
                    if (braceStack.isEmpty()) {
                        errors.add(new ValidationError(token.line(), token.column(),
                                "Unexpected closing brace '}'", null));
                    } else {
                        braceStack.pop();
                    }
                }
                case LPAREN -> parenStack.push(token);
                case RPAREN -> {
                    if (parenStack.isEmpty()) {
                        errors.add(new ValidationError(token.line(), token.column(),
                                "Unexpected closing parenthesis ')'", null));
                    } else {
                        parenStack.pop();
                    }
                }
                case LBRACKET -> bracketStack.push(token);
                case RBRACKET -> {
                    if (bracketStack.isEmpty()) {
                        errors.add(new ValidationError(token.line(), token.column(),
                                "Unexpected closing bracket ']'", null));
                    } else {
                        bracketStack.pop();
                    }
                }
                default -> { /* handled below */ }
            }

            // ── Generic angle bracket balancing ────────────────────────
            if (token.type() == TsTokenType.GENERIC_OPEN) {
                parenStack.push(token); // Treat as parenthesis for balancing
            } else if (token.type() == TsTokenType.GENERIC_CLOSE) {
                if (!parenStack.isEmpty() && parenStack.peek().type() == TsTokenType.GENERIC_OPEN) {
                    parenStack.pop();
                }
            }

            // ── JSX tag balancing ──────────────────────────────────────
            if (token.type() == TsTokenType.JSX_TAG_OPEN) {
                inJsxTag = true;
                // Look ahead for tag name
                String tagName = extractJsxTagName(tokens, i);
                if (tagName != null && !tagName.isEmpty() && !tagName.startsWith("/")) {
                    jsxTagStack.push(tagName);
                    inJsxContent = false;  // We're now inside a tag, not content
                } else if (tagName != null && tagName.startsWith("/")) {
                    // Closing tag
                    inJsxContent = false;  // We're in a closing tag, not content
                    String closingName = tagName.substring(1);
                    if (!jsxTagStack.isEmpty()) {
                        String openTag = jsxTagStack.peek();
                        if (openTag.equals(closingName)) {
                            jsxTagStack.pop();
                        } else {
                            errors.add(new ValidationError(token.line(), token.column(),
                                    "Mismatched JSX closing tag '</" + closingName +
                                    ">', expected '</" + openTag + ">'", null));
                        }
                    } else {
                        errors.add(new ValidationError(token.line(), token.column(),
                                "Unexpected JSX closing tag '</" + closingName + ">'", null));
                    }
                }
            }

            if (token.type() == TsTokenType.JSX_SELF_CLOSE) {
                if (!jsxTagStack.isEmpty()) {
                    jsxTagStack.pop();
                }
                inJsxTag = false;
                inJsxContent = false;
            }

            if (token.type() == TsTokenType.JSX_TAG_CLOSE) {
                inJsxTag = false;
                inJsxContent = true;  // After closing a tag, we're in content
            }

            // ── Invalid consecutive tokens ─────────────────────────────
            // Skip this check when inside a JSX tag or JSX content, as:
            // - Attributes can follow tag names without operators (e.g., <div className="test">)
            // - Text content can contain multiple words without operators (e.g., <p>Hello JSX</p>)
            if (i > 0 && !inJsxTag && !inJsxContent) {
                TsToken prev = findPreviousSignificantToken(tokens, i);
                if (prev != null && isInvalidSequence(prev, token)) {
                    errors.add(new ValidationError(token.line(), token.column(),
                            "Invalid syntax: unexpected '" + token.lexeme() + "' after '" + prev.lexeme() + "'",
                            null));
                }
            }

            // ── Check for unclosed strings ─────────────────────────────
            if (token.type() == TsTokenType.STRING) {
                String lex = token.lexeme();
                if (lex.length() < 2 || lex.charAt(0) != lex.charAt(lex.length() - 1)) {
                    errors.add(new ValidationError(token.line(), token.column(),
                            "Unterminated string literal", null));
                }
            }
        }

        // ── Report unclosed delimiters ─────────────────────────────────
        for (TsToken unclosed : braceStack) {
            errors.add(new ValidationError(unclosed.line(), unclosed.column(),
                    "Unclosed brace '{'", null));
        }
        for (TsToken unclosed : parenStack) {
            String desc = unclosed.type() == TsTokenType.GENERIC_OPEN
                    ? "Unclosed generic type '<'" : "Unclosed parenthesis '('";
            errors.add(new ValidationError(unclosed.line(), unclosed.column(), desc, null));
        }
        for (TsToken unclosed : bracketStack) {
            errors.add(new ValidationError(unclosed.line(), unclosed.column(),
                    "Unclosed bracket '['", null));
        }
        for (String unclosedTag : jsxTagStack) {
            errors.add(new ValidationError(1, -1,
                    "Unclosed JSX tag '<" + unclosedTag + ">'", null));
        }

        if (templateDepth > 0) {
            errors.add(new ValidationError(1, -1, "Unclosed template literal", null));
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid("TypeScript syntax is valid.");
        }

        return ValidationResult.invalid(
                "TypeScript syntax errors found: " + errors.size() + " error(s).", errors);
    }

    /**
     * Set the tokenizer to JSX mode for TSX/JSX files.
     */
    void enableJsxMode() {
        tokenizer.enableJsxMode();
    }

    /**
     * Extract the tag name following a JSX_TAG_OPEN token.
     */
    private static String extractJsxTagName(List<TsToken> tokens, int openIndex) {
        for (int j = openIndex + 1; j < tokens.size(); j++) {
            TsToken t = tokens.get(j);
            if (t.type() == TsTokenType.WHITESPACE) continue;
            if (t.type() == TsTokenType.IDENTIFIER || t.type() == TsTokenType.DOT) {
                // Could be a member expression like Foo.Bar
                StringBuilder name = new StringBuilder();
                name.append(t.lexeme());
                for (int k = j + 1; k < tokens.size(); k++) {
                    TsToken next = tokens.get(k);
                    if (next.type() == TsTokenType.DOT) {
                        name.append('.');
                    } else if (next.type() == TsTokenType.IDENTIFIER) {
                        name.append(next.lexeme());
                    } else {
                        break;
                    }
                }
                return name.toString();
            }
            if (t.type() == TsTokenType.OPERATOR && t.lexeme().equals("/")) {
                // Closing tag: </Tag>
                for (int k = j + 1; k < tokens.size(); k++) {
                    TsToken next = tokens.get(k);
                    if (next.type() == TsTokenType.WHITESPACE) continue;
                    if (next.type() == TsTokenType.IDENTIFIER) {
                        return "/" + next.lexeme();
                    }
                    break;
                }
            }
        }
        return null;
    }

    /**
     * Find the previous significant (non-whitespace, non-comment) token.
     */
    private static TsToken findPreviousSignificantToken(List<TsToken> tokens, int currentIndex) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            TsToken t = tokens.get(i);
            if (t.type() != TsTokenType.WHITESPACE &&
                t.type() != TsTokenType.LINE_COMMENT &&
                t.type() != TsTokenType.BLOCK_COMMENT) {
                return t;
            }
        }
        return null;
    }

    /**
     * Check if two consecutive tokens form an invalid sequence.
     */
    private static boolean isInvalidSequence(TsToken prev, TsToken current) {
        // Allow operator chains like ===, !==, etc.
        if (prev.type() == TsTokenType.OPERATOR && current.type() == TsTokenType.OPERATOR) {
            // Some combinations are valid (e.g., ===, !==, =>, ??=)
            String combined = prev.lexeme() + current.lexeme();
            if (combined.equals("==") || combined.equals("!=") || combined.equals("===") ||
                combined.equals("!==") || combined.equals("=>") || combined.equals("??") ||
                combined.equals("?.") || combined.equals("||") ||
                combined.equals("&&") || combined.equals("**") ||
                combined.equals(">>") || combined.equals("<<") || combined.equals(">>>") ||
                combined.equals("||=") || combined.equals("&&=") ||
                combined.equals("??=") || combined.equals("**=") || combined.equals(">>=") ||
                combined.equals("<<=") || combined.equals(">>>=") || combined.equals("+=") ||
                combined.equals("-=") || combined.equals("*=") || combined.equals("/=") ||
                combined.equals("%=") || combined.equals("&=") || combined.equals("|=") ||
                combined.equals("^=") || combined.equals("--") || combined.equals("++")) {
                return false;
            }
            return true;
        }

        // Disallow consecutive identifiers without operator/delimiter
        if (prev.type() == TsTokenType.IDENTIFIER && current.type() == TsTokenType.IDENTIFIER) {
            return true;
        }

        // Disallow identifier immediately after number
        if (prev.type() == TsTokenType.NUMBER && current.type() == TsTokenType.IDENTIFIER) {
            return true;
        }

        return false;
    }
}
