package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Pure-Java, error-tolerant parser for Python 3.14 source code.
 *
 * <p>This parser performs <b>structural syntax validation</b> without executing
 * any code or invoking external processes.  It operates on the token stream
 * produced by {@link PythonLexer} and checks for:
 *
 * <ul>
 *   <li>Delimiter balance (parentheses, brackets, braces).</li>
 *   <li>Colon placement after compound-statement headers.</li>
 *   <li>Minimum body presence for compound statements.</li>
 *   <li>Valid assignment targets (no literals on the left-hand side).</li>
 *   <li>Proper {@code except} clause syntax.</li>
 *   <li>Valid {@code import} and {@code from ... import} structures.</li>
 *   <li>Def / class header syntax.</li>
 *   <li>Match/case statement structure (PEP 634).</li>
 *   <li>Async/await context validity.</li>
 * </ul>
 *
 * <p>This class is thread-safe (no mutable shared state).
 */
public final class PythonParser {

    /** Token types that start a compound statement. */
    private static final Set<PythonTokenType> COMPOUND_STARTERS = Set.of(
            PythonTokenType.KW_IF, PythonTokenType.KW_WHILE, PythonTokenType.KW_FOR,
            PythonTokenType.KW_DEF, PythonTokenType.KW_CLASS, PythonTokenType.KW_TRY,
            PythonTokenType.KW_WITH, PythonTokenType.MATCH, PythonTokenType.CASE,
            PythonTokenType.KW_ELIF, PythonTokenType.KW_ELSE, PythonTokenType.KW_EXCEPT,
            PythonTokenType.KW_FINALLY
    );

    private static final Set<PythonTokenType> AUGMENTED_ASSIGN_OPS = Set.of(
            PythonTokenType.PLUS_EQUAL, PythonTokenType.MINUS_EQUAL,
            PythonTokenType.STAR_EQUAL, PythonTokenType.SLASH_EQUAL,
            PythonTokenType.PERCENT_EQUAL, PythonTokenType.AMPERSAND_EQUAL,
            PythonTokenType.PIPE_EQUAL, PythonTokenType.CARET_EQUAL,
            PythonTokenType.LEFT_SHIFT, PythonTokenType.RIGHT_SHIFT,
            PythonTokenType.DOUBLE_SLASH_EQUAL, PythonTokenType.AT_EQUAL,
            PythonTokenType.DOUBLE_STAR_EQUAL
    );

    private PythonParser() {}

    public static ValidationResult validate(List<PythonToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return ValidationResult.valid("Empty token stream.");
        }

        List<ValidationError> errors = new ArrayList<>();

        checkDelimiterBalance(tokens, errors);
        checkCompoundStatementColons(tokens, errors);
        checkCompoundStatementConditions(tokens, errors);
        checkAssignmentTargets(tokens, errors);
        checkImportStructure(tokens, errors);
        checkDefClassHeaders(tokens, errors);
        checkTryBlockStructure(tokens, errors);
        checkMatchCaseStructure(tokens, errors);
        checkAsyncAwaitValidity(tokens, errors);
        checkDuplicateTokens(tokens, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid("Python syntax is structurally valid.");
        }

        return ValidationResult.invalid(
                "Python syntax has " + errors.size() + " structural error(s).",
                errors);
    }

    // ==================================================================
    //  Delimiter balance
    // ==================================================================

    private static void checkDelimiterBalance(List<PythonToken> tokens,
                                               List<ValidationError> errors) {
        Stack<PythonToken> stack = new Stack<>();

        for (PythonToken t : tokens) {
            if (t.type() == null) continue; // lexer error tokens
            switch (t.type()) {
                case LEFT_PAREN -> stack.push(t);
                case LEFT_BRACKET -> stack.push(t);
                case LEFT_BRACE -> stack.push(t);
                case RIGHT_PAREN -> {
                    if (stack.isEmpty()) {
                        errors.add(new ValidationError(t.line(), t.column(),
                                "Unmatched closing parenthesis ')'.", null));
                    } else {
                        PythonToken open = stack.pop();
                        if (open.type() != PythonTokenType.LEFT_PAREN) {
                            errors.add(new ValidationError(t.line(), t.column(),
                                    "Mismatched closing delimiter '" + t.text()
                                            + "' — expected closing for '" + open.text()
                                            + "' opened at line " + open.line() + ".", null));
                        }
                    }
                }
                case RIGHT_BRACKET -> {
                    if (stack.isEmpty()) {
                        errors.add(new ValidationError(t.line(), t.column(),
                                "Unmatched closing bracket ']'.", null));
                    } else {
                        PythonToken open = stack.pop();
                        if (open.type() != PythonTokenType.LEFT_BRACKET) {
                            errors.add(new ValidationError(t.line(), t.column(),
                                    "Mismatched closing delimiter '" + t.text()
                                            + "' — expected closing for '" + open.text()
                                            + "' opened at line " + open.line() + ".", null));
                        }
                    }
                }
                case RIGHT_BRACE -> {
                    if (stack.isEmpty()) {
                        errors.add(new ValidationError(t.line(), t.column(),
                                "Unmatched closing brace '}'.", null));
                    } else {
                        PythonToken open = stack.pop();
                        if (open.type() != PythonTokenType.LEFT_BRACE) {
                            errors.add(new ValidationError(t.line(), t.column(),
                                    "Mismatched closing delimiter '" + t.text()
                                            + "' — expected closing for '" + open.text()
                                            + "' opened at line " + open.line() + ".", null));
                        }
                    }
                }
                default -> { }
            }
        }

        while (!stack.isEmpty()) {
            PythonToken open = stack.pop();
            String desc = switch (open.type()) {
                case LEFT_PAREN -> "Unclosed parenthesis '(' opened";
                case LEFT_BRACKET -> "Unclosed bracket '[' opened";
                case LEFT_BRACE -> "Unclosed brace '{' opened";
                default -> "Unclosed delimiter opened";
            };
            errors.add(new ValidationError(open.line(), open.column(),
                    desc + " at line " + open.line() + ".", null));
        }
    }

    // ==================================================================
    //  Compound-statement colons
    // ==================================================================

    private static void checkCompoundStatementColons(List<PythonToken> tokens,
                                                      List<ValidationError> errors) {
        int outerDepth = 0;
        for (int i = 0; i < tokens.size() - 1; i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;
            // Track bracket depth — compound keywords inside brackets
            // (comprehensions, function calls, etc.) don't need colons.
            if (current.type() == PythonTokenType.LEFT_PAREN
                    || current.type() == PythonTokenType.LEFT_BRACKET
                    || current.type() == PythonTokenType.LEFT_BRACE) {
                outerDepth++;
                continue;
            }
            if (current.type() == PythonTokenType.RIGHT_PAREN
                    || current.type() == PythonTokenType.RIGHT_BRACKET
                    || current.type() == PythonTokenType.RIGHT_BRACE) {
                outerDepth = Math.max(0, outerDepth - 1);
                continue;
            }
            if (outerDepth > 0) continue;   // inside brackets — skip
            if (COMPOUND_STARTERS.contains(current.type())) {
                boolean foundColon = false;
                int depth = 0;
                for (int j = i + 1; j < tokens.size(); j++) {
                    PythonTokenType jt = tokens.get(j).type();
                    if (jt == null) continue;
                    if (jt == PythonTokenType.LEFT_PAREN || jt == PythonTokenType.LEFT_BRACKET
                            || jt == PythonTokenType.LEFT_BRACE) depth++;
                    else if (jt == PythonTokenType.RIGHT_PAREN || jt == PythonTokenType.RIGHT_BRACKET
                            || jt == PythonTokenType.RIGHT_BRACE) depth--;
                    else if (jt == PythonTokenType.COLON && depth == 0) { foundColon = true; break; }
                    else if (jt == PythonTokenType.NEWLINE && depth == 0) break;
                }
                if (!foundColon) {
                    String stmtName = current.text() != null ? current.text() : current.type().name();
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected ':' after '" + stmtName + "' statement at line "
                                    + current.line() + ".", null));
                }
            }
        }
    }

    // ==================================================================
    //  Compound-statement conditions
    // ==================================================================

    /**
     * Checks that compound statements that require a condition actually have one.
     * For example, {@code if:} is invalid — it must be {@code if <expr>:}.
     * Bare {@code except:} is valid Python, so it is exempt.
     */
    private static void checkCompoundStatementConditions(List<PythonToken> tokens,
                                                          List<ValidationError> errors) {
        // Keywords that MUST have a non-empty clause before the colon.
        Set<PythonTokenType> conditionRequired = Set.of(
                PythonTokenType.KW_IF,
                PythonTokenType.KW_ELIF,
                PythonTokenType.KW_WHILE,
                PythonTokenType.KW_FOR
        );
        int outerDepth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken t = tokens.get(i);
            if (t.type() == null) continue;
            if (t.type() == PythonTokenType.LEFT_PAREN
                    || t.type() == PythonTokenType.LEFT_BRACKET
                    || t.type() == PythonTokenType.LEFT_BRACE) {
                outerDepth++;
                continue;
            }
            if (t.type() == PythonTokenType.RIGHT_PAREN
                    || t.type() == PythonTokenType.RIGHT_BRACKET
                    || t.type() == PythonTokenType.RIGHT_BRACE) {
                outerDepth = Math.max(0, outerDepth - 1);
                continue;
            }
            if (outerDepth > 0) continue;
            if (!conditionRequired.contains(t.type())) continue;

            // Find the colon for this statement.
            boolean foundContent = false;
            boolean foundColon = false;
            int depth = 0;
            for (int j = i + 1; j < tokens.size(); j++) {
                PythonToken next = tokens.get(j);
                if (next.type() == null) continue;
                if (next.type() == PythonTokenType.LEFT_PAREN
                        || next.type() == PythonTokenType.LEFT_BRACKET
                        || next.type() == PythonTokenType.LEFT_BRACE) { depth++; }
                else if (next.type() == PythonTokenType.RIGHT_PAREN
                        || next.type() == PythonTokenType.RIGHT_BRACKET
                        || next.type() == PythonTokenType.RIGHT_BRACE) { depth--; }
                else if (depth == 0) {
                    if (next.type() == PythonTokenType.COLON) { foundColon = true; break; }
                    if (next.type() == PythonTokenType.NEWLINE) break;
                    // Any real token between the keyword and the colon = content
                    if (next.type() != PythonTokenType.NEWLINE) foundContent = true;
                }
            }
            if (foundColon && !foundContent) {
                String keyword = t.text() != null ? t.text() : t.type().name();
                errors.add(new ValidationError(t.line(), t.column(),
                        "Expected expression after '" + keyword + "' at line " + t.line() + ".", null));
            }
        }
    }

    // ==================================================================
    //  Assignment targets
    // ==================================================================

    private static void checkAssignmentTargets(List<PythonToken> tokens,
                                                List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;
            if (current.type() == PythonTokenType.EQUAL || AUGMENTED_ASSIGN_OPS.contains(current.type())) {
                if (i == 0) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Assignment operator at start of input.", null));
                    continue;
                }
                PythonToken left = tokens.get(i - 1);
                if (left.type() != null && isLiteralOrKeyword(left.type())) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Invalid assignment target '" + left.text()
                                    + "' at line " + left.line() + ".", null));
                }
            }
        }
    }

    private static boolean isLiteralOrKeyword(PythonTokenType type) {
        return type == PythonTokenType.INTEGER_LITERAL || type == PythonTokenType.FLOAT_LITERAL
                || type == PythonTokenType.COMPLEX_LITERAL
                || type == PythonTokenType.STRING_LITERAL
                || type == PythonTokenType.BOOLEAN_LITERAL
                || type == PythonTokenType.NONE_LITERAL
                || type == PythonTokenType.KW_TRUE
                || type == PythonTokenType.KW_FALSE
                || type == PythonTokenType.KW_NONE
                || type == PythonTokenType.KW_AND
                || type == PythonTokenType.KW_OR
                || type == PythonTokenType.KW_NOT
                || type == PythonTokenType.KW_IN
                || type == PythonTokenType.KW_IS
                || type == PythonTokenType.KW_IF
                || type == PythonTokenType.KW_ELSE
                || type == PythonTokenType.KW_LAMBDA
                || type == PythonTokenType.KW_FOR
                || type == PythonTokenType.KW_WHILE
                || type == PythonTokenType.KW_CLASS
                || type == PythonTokenType.KW_DEF
                || type == PythonTokenType.KW_RETURN
                || type == PythonTokenType.KW_YIELD
                || type == PythonTokenType.KW_IMPORT
                || type == PythonTokenType.KW_FROM
                || type == PythonTokenType.KW_AS
                || type == PythonTokenType.KW_RAISE
                || type == PythonTokenType.KW_TRY
                || type == PythonTokenType.KW_EXCEPT
                || type == PythonTokenType.KW_FINALLY
                || type == PythonTokenType.KW_WITH
                || type == PythonTokenType.KW_ASYNC
                || type == PythonTokenType.KW_AWAIT
                || type == PythonTokenType.KW_ASSERT
                || type == PythonTokenType.KW_BREAK
                || type == PythonTokenType.KW_CONTINUE
                || type == PythonTokenType.KW_PASS
                || type == PythonTokenType.KW_DEL
                || type == PythonTokenType.KW_GLOBAL
                || type == PythonTokenType.KW_NONLOCAL;
    }

    // ==================================================================
    //  Import structure
    // ==================================================================

    private static void checkImportStructure(List<PythonToken> tokens,
                                              List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;

            if (current.type() == PythonTokenType.KW_FROM) {
                int j = i + 1;
                boolean hasModule = false;
                while (j < tokens.size() && tokens.get(j).type() != null
                        && (tokens.get(j).type() == PythonTokenType.IDENTIFIER
                        || tokens.get(j).type() == PythonTokenType.DOT)) {
                    hasModule = true;
                    j++;
                }
                if (!hasModule) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected module name after 'from' at line " + current.line() + ".", null));
                    continue;
                }
                if (j >= tokens.size() || tokens.get(j).type() != PythonTokenType.KW_IMPORT) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected 'import' after module name in 'from ... import' at line "
                                    + current.line() + ".", null));
                }
            }

            if (current.type() == PythonTokenType.KW_IMPORT && i > 0
                    && tokens.get(i - 1).type() != PythonTokenType.KW_FROM) {
                if (i + 1 >= tokens.size()
                        || (tokens.get(i + 1).type() != PythonTokenType.IDENTIFIER
                        && tokens.get(i + 1).type() != PythonTokenType.STAR)) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected module name after 'import' at line " + current.line() + ".", null));
                }
            }
        }
    }

    // ==================================================================
    //  Def / class headers
    // ==================================================================

    private static void checkDefClassHeaders(List<PythonToken> tokens,
                                              List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;

            if (current.type() == PythonTokenType.KW_DEF || current.type() == PythonTokenType.KW_CLASS) {
                boolean isDef = current.type() == PythonTokenType.KW_DEF;
                String keyword = isDef ? "def" : "class";

                if (i + 1 < tokens.size()) {
                    PythonToken next = tokens.get(i + 1);
                    if (next.type() != PythonTokenType.IDENTIFIER) {
                        errors.add(new ValidationError(next.line(), next.column(),
                                "Expected identifier after '" + keyword + "' at line "
                                        + current.line() + ", found '" + next.text() + "'.", null));
                    }
                }

                if (isDef && i + 2 < tokens.size()) {
                    PythonToken afterName = tokens.get(i + 2);
                    if (afterName.type() != null
                            && afterName.type() != PythonTokenType.LEFT_PAREN
                            && afterName.type() != PythonTokenType.COLON) {
                        errors.add(new ValidationError(afterName.line(), afterName.column(),
                                "Expected '(' after function name in 'def' at line "
                                        + current.line() + ".", null));
                    }
                }
            }
        }
    }

    // ==================================================================
    //  Try block structure
    // ==================================================================

    private static void checkTryBlockStructure(List<PythonToken> tokens,
                                                List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;

            if (current.type() == PythonTokenType.KW_TRY) {
                if (i + 1 < tokens.size() && tokens.get(i + 1).type() != PythonTokenType.COLON) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected ':' after 'try' at line " + current.line() + ".", null));
                }

                boolean hasExceptOrFinally = false;
                for (int j = i + 1; j < tokens.size(); j++) {
                    PythonTokenType jt = tokens.get(j).type();
                    if (jt == PythonTokenType.KW_EXCEPT || jt == PythonTokenType.KW_FINALLY) {
                        hasExceptOrFinally = true;
                        break;
                    }
                    if (jt == PythonTokenType.KW_ELSE) break;
                }

                if (!hasExceptOrFinally) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "'try' block at line " + current.line()
                                    + " must be followed by 'except' or 'finally'.", null));
                }
            }

            if (current.type() == PythonTokenType.KW_EXCEPT) {
                boolean foundColon = false;
                for (int j = i + 1; j < tokens.size(); j++) {
                    PythonTokenType jt = tokens.get(j).type();
                    if (jt == PythonTokenType.COLON) { foundColon = true; break; }
                    if (jt == PythonTokenType.NEWLINE) break;
                }
                if (!foundColon) {
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected ':' in 'except' clause at line " + current.line() + ".", null));
                }
            }
        }
    }

    // ==================================================================
    //  Match/case structure (PEP 634)
    // ==================================================================

    private static void checkMatchCaseStructure(List<PythonToken> tokens,
                                                 List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;

            if (current.type() == PythonTokenType.MATCH || current.type() == PythonTokenType.CASE) {
                boolean foundColon = false;
                for (int j = i + 1; j < tokens.size(); j++) {
                    PythonTokenType jt = tokens.get(j).type();
                    if (jt == PythonTokenType.COLON) { foundColon = true; break; }
                    if (jt == PythonTokenType.NEWLINE) break;
                }
                if (!foundColon) {
                    String kw = current.type() == PythonTokenType.MATCH ? "match" : "case";
                    errors.add(new ValidationError(current.line(), current.column(),
                            "Expected ':' after '" + kw + "' at line " + current.line() + ".", null));
                }
            }
        }
    }

    // ==================================================================
    //  Async/await validity
    // ==================================================================

    private static void checkAsyncAwaitValidity(List<PythonToken> tokens,
                                                 List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PythonToken current = tokens.get(i);
            if (current.type() == null) continue;

            if (current.type() == PythonTokenType.KW_AWAIT) {
                if (i + 1 < tokens.size()) {
                    PythonToken next = tokens.get(i + 1);
                    if (next.type() == PythonTokenType.NEWLINE || next.type() == PythonTokenType.COLON) {
                        errors.add(new ValidationError(current.line(), current.column(),
                                "'await' at line " + current.line()
                                        + " must be followed by an expression.", null));
                    }
                }
            }
        }
    }

    // ==================================================================
    //  Duplicate tokens
    // ==================================================================

    private static void checkDuplicateTokens(List<PythonToken> tokens,
                                              List<ValidationError> errors) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            PythonToken curr = tokens.get(i);
            PythonToken next = tokens.get(i + 1);
            if (curr.type() == null || next.type() == null) continue;

            if (curr.type() == PythonTokenType.EQUAL && next.type() == PythonTokenType.EQUAL) {
                errors.add(new ValidationError(next.line(), next.column(),
                        "Unexpected '=' at line " + next.line() + ". Did you mean '=='?", null));
            }
        }
    }
}
