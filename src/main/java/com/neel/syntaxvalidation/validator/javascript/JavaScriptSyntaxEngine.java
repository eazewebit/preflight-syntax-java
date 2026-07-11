package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * A pure-Java syntax validation engine for modern JavaScript (ES6+).
 *
 * <p>Unlike the Node.js-based {@code node --check} pipeline, this engine runs
 * entirely inside the JVM with zero external dependencies. It combines a
 * faithful {@link JavaScriptSyntaxTokenizer lexer} with a comprehensive suite
 * of structural checks to catch a wide range of syntax errors &mdash; from
 * unbalanced brackets and unterminated literals to malformed arrow functions,
 * optional chaining, spread/rest syntax, destructuring patterns, class
 * declarations, async/await structures, template literals, binary-operator
 * placement errors, and control-flow keyword requirements.
 *
 * <h2>Validation phases</h2>
 * <ol>
 *   <li><b>Tokenisation</b> &mdash; the source is lexed into typed tokens.
 *       Unterminated strings, templates, regexes and block comments surface as
 *       {@link JsTokenType#ERROR} tokens.</li>
 *   <li><b>Bracket balancing</b> &mdash; every {@code ()}, {@code []} and
 *       {@code {}} pair is checked for balance, correct nesting and correct
 *       matching. Opaque tokens (strings, templates, comments, regexes) are
 *       transparent to this phase, eliminating false positives from brackets
 *       that appear inside string or comment text.</li>
 *   <li><b>Structural pattern checks</b> &mdash; context-aware heuristics detect
 *       common ES6+ mistakes such as:
 *       <ul>
 *         <li>Arrow functions missing a body after {@code =>}</li>
 *         <li>Spread/rest ({@code ...}) without an operand</li>
 *         <li>Optional chaining ({@code ?.}) without a following member</li>
 *         <li>{@code const}/{@code let}/{@code var} without a binding</li>
 *         <li>{@code const} declarations missing a required initialiser</li>
 *         <li>Assignment operators ({@code =}, {@code +=}, &hellip;) without a
 *             right-hand side</li>
 *         <li>Binary operators ({@code &&}, {@code ||}, {@code ??},
 *             {@code ===}, &hellip;) without a left-hand operand</li>
 *         <li>Prefix keyword operators ({@code typeof}, {@code delete},
 *             {@code void}, {@code new}, {@code await}) without an operand</li>
 *         <li>Control-flow keywords ({@code if}, {@code for}, {@code while},
 *             {@code switch}, {@code catch}, {@code try}, {@code finally})
 *             without their required syntax</li>
 *         <li>{@code import}/{@code export} statements without specifiers</li>
 *         <li>{@code class extends} without a class name</li>
 *         <li>{@code throw} without an expression</li>
 *         <li>{@code function}/{@code class} declarations missing a body</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>The engine is designed to be conservative: it avoids reporting errors for
 * valid (if unusual) code. Error tokens produced by the lexer are preserved in
 * the significant-token stream so that structural checks do not produce
 * redundant false-positive diagnostics (e.g.&nbsp;reporting a missing RHS on an
 * {@code =} whose right-hand side was an unterminated string literal). When
 * Node.js is also available, the {@link JavaScriptValidator} layers the
 * {@code node --check} analysis on top for even deeper coverage.
 *
 * <p><b>Thread-safety.</b> The engine is stateless and can be shared freely
 * across threads. Obtain the canonical instance via {@link #getInstance()}.
 */
public final class JavaScriptSyntaxEngine {

    private static final JavaScriptSyntaxEngine INSTANCE = new JavaScriptSyntaxEngine();

    // ------------------------------------------------------------------
    //  Sets used by the structural pattern checks
    // ------------------------------------------------------------------

    /** Declaration keywords that must be followed by a binding (identifier or pattern). */
    private static final Set<String> DECLARATION_KEYWORDS = Set.of("const", "let", "var");

    /**
     * Assignment operators that require a right-hand-side expression.
     * These also require a left-hand-side operand (they can never appear at
     * the start of an expression).
     */
    private static final Set<String> ASSIGNMENT_OPERATORS = Set.of(
            "=", "+=", "-=", "*=", "/=", "%=", "**=", "&=", "|=", "^=",
            "<<=", ">>=", ">>>=", "&&=", "||=", "??="
    );

    /**
     * Binary-only operators that can never start an expression and therefore
     * always require a left-hand-side operand. Multi-character operators only
     * &mdash; single-character operators ({@code +}, {@code -}, {@code *},
     * {@code !}, {@code ~}) are excluded because they can be unary.
     */
    private static final Set<String> BINARY_ONLY_OPS = Set.of(
            "&&", "||", "??",
            "==", "===", "!=", "!==",
            "<=", ">=",
            "<<", ">>", ">>>", "**"
    );

    /**
     * Single-character operators that can be either unary or binary depending
     * on context. When preceded by a value-producing token (isValidLhsEnd
     * returns true), they are in binary position and therefore require a
     * right-hand-side expression.
     */
    private static final Set<String> BINARY_POSITION_SINGLE_CHAR_OPS = Set.of(
            "+", "-", "*", "/", "%"
    );
    /**
     * The union of all operator sets that require a right-hand-side expression.
     */
    private static final Set<String> RHS_REQUIRED_OPS;

    /**
     * The union of operator sets that require a left-hand-side operand.
     * Unlike {@link #BINARY_ONLY_OPS} alone, this also contains
     * {@link #ASSIGNMENT_OPERATORS}.
     */
    private static final Set<String> LHS_REQUIRED_OPS;

    static {
        Set<String> rhs = new java.util.HashSet<>(ASSIGNMENT_OPERATORS);
        rhs.addAll(BINARY_ONLY_OPS);
        RHS_REQUIRED_OPS = java.util.Collections.unmodifiableSet(rhs);

        Set<String> lhs = new java.util.HashSet<>(BINARY_ONLY_OPS);
        lhs.addAll(ASSIGNMENT_OPERATORS);
        LHS_REQUIRED_OPS = java.util.Collections.unmodifiableSet(lhs);
    }
    /** Punctuation that terminates a statement or expression and can never be a body. */
    private static final Set<String> ARROW_INVALID_FOLLOWERS = Set.of(
            ";", ",", ")", "]", "}", ":", "?", "="
    );

    /** Punctuation that can never be a valid operand of a spread/rest element. */
    private static final Set<String> SPREAD_INVALID_FOLLOWERS = Set.of(
            ";", ",", ")", "]", "}", "="
    );

    /** Punctuation that <em>can</em> validly follow optional chaining {@code ?.}. */
    private static final Set<String> OPTIONAL_CHAIN_VALID_PUNCT = Set.of("(", "[");

    /** Punctuation that terminates a declaration, making it invalid as a function/class follower. */
    private static final Set<String> DECL_INVALID_FOLLOWERS = Set.of(
            ";", "=", "}", ",", ")", "]"
    );

    /** Punctuation that can never be a valid expression following a prefix keyword. */
    private static final Set<String> INVALID_EXPR_STARTERS = Set.of(
            ";", ",", ")", "]", "}", "=", "=>"
    );

    /** Prefix keyword operators that must be followed by an expression. */
    private static final Set<String> PREFIX_EXPR_KEYWORDS = Set.of(
            "typeof", "delete", "void", "new"
    );

    /** Control-flow keywords that must be immediately followed by {@code (}. */
    private static final Set<String> PAREN_REQUIRED_KEYWORDS = Set.of(
            "if", "while", "switch", "with"
    );

    /** Keywords whose value can be the left-hand side of a binary operator. */
    private static final Set<String> VALUE_KEYWORDS = Set.of(
            "this", "super", "true", "false", "null"
    );

    /** Closing punctuation that typically ends a value-producing expression. */
    private static final Set<String> VALUE_CLOSING_PUNCT = Set.of(")", "]", "}");

    /** Keyword token after which a {@code class} must not appear. */
    private static final String CLASS_EXTENDS = "extends";

    // ------------------------------------------------------------------
    //  Construction
    // ------------------------------------------------------------------

    private JavaScriptSyntaxEngine() {
    }

    /**
     * @return the singleton engine instance.
     */
    public static JavaScriptSyntaxEngine getInstance() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    //  Public API
    // ------------------------------------------------------------------

    /**
     * Validates the given JavaScript source for structural syntax errors.
     *
     * @param source the JavaScript source code; {@code null} or blank is
     *               treated as valid (no content to check).
     * @return a {@link ValidationResult}; valid when no errors are found,
     *         invalid otherwise with detailed {@link ValidationError}s.
     */
    public ValidationResult validate(String source) {
        if (source == null || source.isBlank()) {
            return ValidationResult.valid(
                    "No content to validate — empty input is syntactically valid.");
        }

        List<JsToken> tokens;
        try {
            tokens = new JavaScriptSyntaxTokenizer(source).tokenize();
        } catch (RuntimeException e) {
            return ValidationResult.invalid(
                    "Unexpected error during JavaScript tokenisation: " + e.getMessage(),
                    new ValidationError(1, "Tokenisation failed: " + e.getMessage()));
        }

        List<ValidationError> errors = new ArrayList<>();

        // Phase 1 — collect tokenisation errors (unterminated literals, etc.)
        collectTokenisationErrors(tokens, errors);

        // Phase 2 — bracket balance
        checkBracketBalance(tokens, errors);

        // Phase 3 — structural pattern checks
        checkStructuralPatterns(tokens, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid(
                    "JavaScript syntax is valid (validated by the built-in ES6+ syntax engine).");
        }

        errors.sort(JavaScriptSyntaxEngine::compareByPosition);

        return ValidationResult.invalid(
                "JavaScript syntax validation failed with " + errors.size()
                        + " error(s) detected by the built-in ES6+ syntax engine.",
                errors);
    }

    // ------------------------------------------------------------------
    //  Phase 1 — Tokenisation errors
    // ------------------------------------------------------------------

    private void collectTokenisationErrors(List<JsToken> tokens, List<ValidationError> errors) {
        for (JsToken token : tokens) {
            if (token.type == JsTokenType.ERROR) {
                errors.add(new ValidationError(token.line, token.column, token.text, null));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Phase 2 — Bracket balance
    // ------------------------------------------------------------------

    private void checkBracketBalance(List<JsToken> tokens, List<ValidationError> errors) {
        Deque<JsToken> stack = new ArrayDeque<>();

        for (JsToken token : tokens) {
            if (token.type != JsTokenType.PUNCTUATION) {
                continue;
            }
            String text = token.text;

            if (text.length() == 1) {
                char ch = text.charAt(0);
                if (ch == '(' || ch == '[' || ch == '{') {
                    stack.push(token);
                } else if (ch == ')' || ch == ']' || ch == '}') {
                    char expected = openingFor(ch);
                    if (stack.isEmpty()) {
                        errors.add(new ValidationError(
                                token.line, token.column,
                                "Unexpected stray closing bracket '" + ch + "' — no matching '"
                                        + expected + "' was opened.",
                                null));
                    } else {
                        JsToken opener = stack.peek();
                        char opened = opener.text.charAt(0);
                        if (opened != expected) {
                            errors.add(new ValidationError(
                                    token.line, token.column,
                                    "Mismatched bracket '" + ch + "' — expected '"
                                            + closingFor(opened) + "' to close '" + opened
                                            + "' opened at line " + opener.line
                                            + ", column " + opener.column + ".",
                                    null));
                        }
                        stack.pop();
                    }
                }
            }
        }

        // Report any brackets that were opened but never closed.
        for (JsToken opener : stack) {
            char opened = opener.text.charAt(0);
            errors.add(new ValidationError(
                    opener.line, opener.column,
                    "Unbalanced bracket '" + opened + "' was opened but its matching '"
                            + closingFor(opened) + "' was never found.",
                    null));
        }
    }

    private static char openingFor(char closing) {
        return switch (closing) {
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            default -> closing;
        };
    }

    private static char closingFor(char opening) {
        return switch (opening) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            default -> opening;
        };
    }

    // ------------------------------------------------------------------
    //  Phase 3 — Structural pattern checks
    // ------------------------------------------------------------------

    /**
     * Iterates over all significant tokens (everything except comments) and
     * dispatches to the appropriate pattern checker.
     *
     * <p>Error tokens ({@link JsTokenType#ERROR}) are intentionally retained in
     * the significant stream so that structural checks can recognise when a
     * tokenisation error was already reported and avoid emitting redundant
     * false-positive diagnostics. For example, in {@code const x = "oops}, the
     * unterminated string becomes an ERROR token; retaining it lets the
     * assignment-operator check see that <em>something</em> follows the
     * {@code =} rather than wrongly reporting a missing right-hand side.
     */
    private void checkStructuralPatterns(List<JsToken> tokens, List<ValidationError> errors) {
        // Build a significant token list that excludes only COMMENT tokens.
        // ERROR tokens are kept (see javadoc above for rationale).
        List<JsToken> sig = new ArrayList<>(tokens.size());
        for (JsToken t : tokens) {
            if (t.type != JsTokenType.COMMENT) {
                sig.add(t);
            }
        }

        for (int i = 0; i < sig.size(); i++) {
            JsToken token = sig.get(i);

            // Skip error and EOF tokens — they are never the "current" token
            // for a structural check.
            if (token.type == JsTokenType.ERROR || token.type == JsTokenType.EOF) {
                continue;
            }

            JsToken prev = (i > 0) ? sig.get(i - 1) : null;
            JsToken next = (i + 1 < sig.size()) ? sig.get(i + 1) : null;

            switch (token.type) {
                case PUNCTUATION:
                    checkPunctuationPatterns(token, prev, next, sig, i, errors);
                    break;
                case KEYWORD:
                    checkKeywordPatterns(token, prev, next, sig, i, errors);
                    break;
                default:
                    break;
            }
        }
    }

    // ---- Punctuation-based checks -------------------------------------

    private void checkPunctuationPatterns(JsToken token, JsToken prev, JsToken next,
                                           List<JsToken> sig, int index,
                                           List<ValidationError> errors) {
        String text = token.text;

        // Arrow function without a body:  => followed by a non-expression token
        if (text.equals("=>")) {
            if (isInvalidFollower(next, ARROW_INVALID_FOLLOWERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Arrow function ('=>') is missing its body — an expression or block "
                                + "must follow '=>'.",
                        null));
            }
            return;
        }

        // Spread/rest without an operand:  ... followed by a non-expression token
        if (text.equals("...")) {
            if (isInvalidFollower(next, SPREAD_INVALID_FOLLOWERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Spread/rest syntax ('...') is missing its operand — an identifier, "
                                + "array or object must follow '...'.",
                        null));
            }
            return;
        }

        // Optional chaining without a member:  ?. followed by something invalid.
        if (text.equals("?.")) {
            boolean valid = isPresentToken(next)
                    && (next.type == JsTokenType.IDENTIFIER
                    || next.type == JsTokenType.KEYWORD
                    || (next.type == JsTokenType.PUNCTUATION
                    && OPTIONAL_CHAIN_VALID_PUNCT.contains(next.text)));
            if (!valid) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Optional chaining ('?.') must be followed by a property name, "
                                + "method call '(' or element access '['.",
                        null));
            }
            return;
        }

        // Binary-only and assignment operators without a left-hand-side operand.
        if (LHS_REQUIRED_OPS.contains(text)) {
            if (!isValidLhsEnd(prev)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Operator '" + text + "' is missing its left-hand-side operand "
                                + "— this operator cannot start an expression.",
                        null));
            }
        }

        // Assignment and binary operators without a right-hand-side expression.
        if (RHS_REQUIRED_OPS.contains(text)) {
            if (isInvalidFollower(next, DECL_INVALID_FOLLOWERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Operator '" + text + "' is missing its right-hand-side expression.",
                        null));
            }
        }

        // Single-char operators (+, -, *, /, %) in binary position need an RHS.
        // When preceded by a value (isValidLhsEnd(prev) == true), these operators
        // are definitely binary and therefore require a right-hand-side expression.
        if (BINARY_POSITION_SINGLE_CHAR_OPS.contains(text) && isValidLhsEnd(prev)) {
            if (isInvalidFollower(next, DECL_INVALID_FOLLOWERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Operator '" + text + "' is missing its right-hand-side expression.",
                        null));
            }
        }

        // Ternary operator '?' without a matching ':'.
        // When '?' follows a value (not the start of an expression or part of '?.'),
        // it is a ternary operator and must have a colon somewhere before the
        // statement ends.
        if (text.equals("?") && isValidLhsEnd(prev)) {
            if (!hasMatchingColon(sig, index)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Ternary operator '?' is missing its matching ':' — "
                                + "a ternary expression must have the form "
                                + "'condition ? consequent : alternative'.",
                        null));
            }
        }
    }

    // ---- Ternary completeness check -----------------------------------

    /**
     * Scans forward from the {@code ?} at the given index in the
     * <em>significant</em> token list to find a matching {@code :} at the same
     * ternary nesting depth.  Brackets ({@code ()}, {@code []}, {@code {}})
     * are tracked so that {@code ?} and {@code :} tokens inside brackets are
     * ignored (they belong to a different expression scope).  If a statement
     * terminator ({@code ;}, {@code }}, {@code }, {@code )}, or EOF) is
     * reached before a matching colon is found the method returns {@code false}.
     *
     * @param sig   the significant-token list (no comments)
     * @param index the index of the {@code ?} token in {@code sig}
     * @return {@code true} if a matching {@code :} exists before the statement ends
     */
    private boolean hasMatchingColon(List<JsToken> sig, int index) {
        int ternaryDepth = 0;
        int bracketDepth = 0;
        for (int j = index + 1; j < sig.size(); j++) {
            JsToken t = sig.get(j);
            if (t.type == JsTokenType.EOF) break;
            if (t.type != JsTokenType.PUNCTUATION) continue;

            String p = t.text;
            // Track bracket depth — ternary operators inside brackets belong
            // to a different scope.
            if (p.length() == 1) {
                char c = p.charAt(0);
                if (c == '(' || c == '[' || c == '{') { bracketDepth++; continue; }
                if (c == ')' || c == ']' || c == '}') {
                    if (bracketDepth > 0) { bracketDepth--; continue; }
                    break; // Unbalanced — stop scanning.
                }
            }

            // Only check ternary depth when we are at the same bracket scope.
            if (bracketDepth > 0) continue;

            if (p.equals("?")) {
                ternaryDepth++;
            } else if (p.equals(":")) {
                if (ternaryDepth == 0) return true; // Found matching colon.
                ternaryDepth--;
            } else if (p.equals(";") || p.equals("}") || p.equals(",") || p.equals(")")) {
                break; // End of expression / statement.
            }
        }
        return false;
    }

    // ---- Keyword-based checks -----------------------------------------

    private void checkKeywordPatterns(JsToken token, JsToken prev, JsToken next,
                                      List<JsToken> sig, int index,
                                      List<ValidationError> errors) {
        String text = token.text;

        // const / let / var checks (binding requirement + const initialiser)
        if (DECLARATION_KEYWORDS.contains(text)) {
            checkDeclaration(token, next, sig, index, errors);
            return;
        }

        // throw without an expression
        if (text.equals("throw")) {
            if (isInvalidFollower(next, INVALID_EXPR_STARTERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'throw' statement is missing the expression to throw.",
                        null));
            }
            return;
        }

        // class checks (extends without name, missing body)
        if (text.equals("class")) {
            checkClass(token, next, errors);
            return;
        }

        // function without a name, parameters or body
        if (text.equals("function")) {
            if (isInvalidFollower(next, DECL_INVALID_FOLLOWERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'function' declaration is missing its name, parameters or body.",
                        null));
            }
            return;
        }

        // Prefix keyword operators: typeof, delete, void, new
        if (PREFIX_EXPR_KEYWORDS.contains(text)) {
            if (isInvalidFollower(next, INVALID_EXPR_STARTERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "Keyword '" + text + "' is missing its operand expression.",
                        null));
            }
            return;
        }

        // await without an operand
        if (text.equals("await")) {
            if (isInvalidFollower(next, INVALID_EXPR_STARTERS)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'await' is missing its operand expression.",
                        null));
            }
            return;
        }

        // Control-flow keywords requiring an opening parenthesis
        if (PAREN_REQUIRED_KEYWORDS.contains(text)) {
            if (!isOpenParen(next)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'" + text + "' statement must be followed by an opening "
                                + "parenthesis '('.",
                        null));
            }
            return;
        }

        // 'for' must be followed by '(' or 'await' (for 'for await')
        if (text.equals("for")) {
            boolean valid = next != null
                    && next.type != JsTokenType.EOF
                    && next.type != JsTokenType.ERROR
                    && ((next.type == JsTokenType.PUNCTUATION && next.text.equals("("))
                    || (next.type == JsTokenType.KEYWORD && next.text.equals("await")));
            if (!valid) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'for' statement must be followed by '(' or 'await' "
                                + "(for 'for await ... of').",
                        null));
            }
            return;
        }

        // 'catch' must be followed by '(' or '{' (optional catch binding)
        if (text.equals("catch")) {
            boolean valid = next != null
                    && next.type == JsTokenType.PUNCTUATION
                    && (next.text.equals("(") || next.text.equals("{"));
            if (!valid) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'catch' must be followed by '(' (catch binding) or '{' "
                                + "(optional catch binding).",
                        null));
            }
            return;
        }

        // 'try' and 'finally' must be followed by '{'
        if (text.equals("try") || text.equals("finally")) {
            if (!isOpenBrace(next)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'" + text + "' must be followed by an opening brace '{'.",
                        null));
            }
            return;
        }

        // 'import' and 'export' must be followed by specifiers or declarations
        if (text.equals("import") || text.equals("export")) {
            // Special case: export default without a value expression
            if (text.equals("export") && next != null
                    && next.type == JsTokenType.KEYWORD && next.text.equals("default")) {
                JsToken afterDefault = tokenAt(sig, index + 2);
                if (isStatementTerminator(afterDefault)) {
                    errors.add(new ValidationError(
                            token.line, token.column,
                            "'export default' is missing its value expression.",
                            null));
                }
                return;
            }
            if (isStatementTerminator(next)) {
                errors.add(new ValidationError(
                        token.line, token.column,
                        "'" + text + "' statement is missing its module specifier, "
                                + "binding, or declaration.",
                        null));
            }
            return;
        }
    }

    // ---- Declaration checks -------------------------------------------

    /**
     * Checks {@code const}/{@code let}/{@code var} declarations for a valid
     * binding and, in the case of {@code const}, for a required initialiser.
     */
    private void checkDeclaration(JsToken token, JsToken next,
                                  List<JsToken> sig, int index,
                                  List<ValidationError> errors) {
        String text = token.text;

        // --- Binding requirement (identifier or destructuring pattern) ---
        boolean validBinding = isPresentToken(next)
                && (next.type == JsTokenType.IDENTIFIER
                || next.type == JsTokenType.KEYWORD
                || (next.type == JsTokenType.PUNCTUATION
                && (next.text.equals("{") || next.text.equals("[") || next.text.equals("("))));
        if (!validBinding) {
            errors.add(new ValidationError(
                    token.line, token.column,
                    "Declaration '" + text + "' is missing a variable name or "
                            + "destructuring pattern.",
                    null));
            return;
        }

        // --- const requires an initialiser (unless in a for-of/for-in loop) ---
        if (text.equals("const")) {
            checkConstInitialiser(token, next, sig, index, errors);
        }
    }

    /**
     * Verifies that a {@code const} declaration has an initialiser
     * ({@code = value}).  Declarations inside {@code for (const ... of/in ...)}
     * loops are exempt because the loop provides the binding implicitly.
     */
    private void checkConstInitialiser(JsToken declToken, JsToken binding,
                                       List<JsToken> sig, int index,
                                       List<ValidationError> errors) {
        boolean inForLoop = isInForLoopContext(sig, index);

        // Simple identifier binding:  const x  =>  must have '=' after the name
        if (binding.type == JsTokenType.IDENTIFIER
                || (binding.type == JsTokenType.KEYWORD && !isExpressionTerminator(binding))) {
            JsToken after = tokenAt(sig, index + 2);

            // for (const x of items) / for (const x in obj)
            if (!inForLoop && after != null && after.type == JsTokenType.KEYWORD
                    && (after.text.equals("in") || after.text.equals("of"))) {
                inForLoop = true;
            }
            if (!inForLoop && isMissingInitialiserToken(after)) {
                errors.add(new ValidationError(
                        declToken.line, declToken.column,
                        "'const' declaration requires an initialiser — 'const "
                                + binding.text + " = <value>' must include an assignment.",
                        null));
            }
            return;
        }

        // Destructuring pattern:  const { ... } / const [ ... ]  =>  must have '=' after close
        if (binding.type == JsTokenType.PUNCTUATION
                && (binding.text.equals("{") || binding.text.equals("["))) {
            int closeIdx = findMatchingClose(sig, index + 1);
            JsToken afterClose = (closeIdx >= 0) ? tokenAt(sig, closeIdx + 1) : null;

            if (!inForLoop && isMissingInitialiserToken(afterClose)) {
                errors.add(new ValidationError(
                        declToken.line, declToken.column,
                        "'const' destructuring declaration requires an initialiser "
                                + "(= <value>).",
                        null));
            }
        }
    }

    /**
     * Determines whether the declaration at {@code index} is inside a
     * {@code for (} or {@code for await (} loop header.
     */
    private boolean isInForLoopContext(List<JsToken> sig, int index) {
        if (index < 1) {
            return false;
        }
        JsToken prev = sig.get(index - 1);
        if (prev.type == JsTokenType.PUNCTUATION && prev.text.equals("(")) {
            if (index >= 2) {
                JsToken prevPrev = sig.get(index - 2);
                if (prevPrev.type == JsTokenType.KEYWORD
                        && (prevPrev.text.equals("for") || prevPrev.text.equals("await"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Class checks -------------------------------------------------

    private void checkClass(JsToken token, JsToken next,
                            List<ValidationError> errors) {
        // class extends without a name
        if (next != null && next.type == JsTokenType.KEYWORD
                && next.text.equals(CLASS_EXTENDS)) {
            errors.add(new ValidationError(
                    token.line, token.column,
                    "'class' declaration is missing its name — 'extends' must be preceded "
                            + "by a class name.",
                    null));
            return;
        }

        // class without a body / missing entirely
        if (isInvalidFollower(next, DECL_INVALID_FOLLOWERS)) {
            errors.add(new ValidationError(
                    token.line, token.column,
                    "'class' declaration is missing its body — a class name and/or block "
                            + "'{ ... }' is required.",
                    null));
        }
    }

    // ------------------------------------------------------------------
    //  Helper predicates and utilities
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code token} represents "nothing useful
     * follows" — i.e. it is null, EOF, or a punctuation in the given invalid
     * set.  Error tokens are treated as <em>present</em> (the tokenisation
     * error is already reported elsewhere).
     */
    private boolean isInvalidFollower(JsToken token, Set<String> invalidPunct) {
        if (token == null || token.type == JsTokenType.EOF) {
            return true;
        }
        if (token.type == JsTokenType.ERROR) {
            return false;
        }
        return token.type == JsTokenType.PUNCTUATION && invalidPunct.contains(token.text);
    }

    /**
     * Returns {@code true} when {@code token} is a real, present token (not
     * null, EOF, or comment).
     */
    private boolean isPresentToken(JsToken token) {
        return token != null
                && token.type != JsTokenType.EOF
                && token.type != JsTokenType.COMMENT;
    }

    /**
     * Returns {@code true} when {@code token} is a statement terminator that
     * cannot begin an expression.
     */
    private boolean isStatementTerminator(JsToken token) {
        if (token == null || token.type == JsTokenType.EOF) {
            return true;
        }
        if (token.type == JsTokenType.ERROR) {
            return false;
        }
        return token.type == JsTokenType.PUNCTUATION
                && (token.text.equals(";") || token.text.equals("}")
                || token.text.equals(")"));
    }

    /**
     * Returns {@code true} when {@code token} is a punctuation that cannot be
     * the operand of a prefix keyword.
     */
    private boolean isExpressionTerminator(JsToken token) {
        if (token == null || token.type == JsTokenType.EOF) {
            return true;
        }
        if (token.type == JsTokenType.ERROR) {
            return false;
        }
        return token.type == JsTokenType.PUNCTUATION
                && INVALID_EXPR_STARTERS.contains(token.text);
    }

    /**
     * Returns {@code true} when the given token (typically the one following a
     * binding name) indicates a missing initialiser.
     */
    private boolean isMissingInitialiserToken(JsToken token) {
        if (token == null || token.type == JsTokenType.EOF) {
            return true;
        }
        if (token.type == JsTokenType.ERROR) {
            return false;
        }
        return token.type == JsTokenType.PUNCTUATION
                && (token.text.equals(";") || token.text.equals(",")
                || token.text.equals(")") || token.text.equals("}"));
    }

    /**
     * Determines whether the previous token can validly end a value-producing
     * expression and therefore serve as the left-hand side of a binary
     * operator.  Error tokens are treated as valid (something was present).
     */
    private boolean isValidLhsEnd(JsToken prev) {
        if (prev == null || prev.type == JsTokenType.EOF) {
            return false;
        }
        if (prev.type == JsTokenType.ERROR) {
            return true;
        }
        return switch (prev.type) {
            case IDENTIFIER, NUMBER, STRING, TEMPLATE, REGEX -> true;
            case KEYWORD -> VALUE_KEYWORDS.contains(prev.text);
            case PUNCTUATION -> VALUE_CLOSING_PUNCT.contains(prev.text);
            default -> false;
        };
    }

    private boolean isOpenParen(JsToken token) {
        return token != null
                && token.type == JsTokenType.PUNCTUATION
                && token.text.equals("(");
    }

    private boolean isOpenBrace(JsToken token) {
        return token != null
                && token.type == JsTokenType.PUNCTUATION
                && token.text.equals("{");
    }

    /**
     * Returns the token at the given index, or {@code null} if out of bounds.
     */
    private JsToken tokenAt(List<JsToken> sig, int index) {
        return (index >= 0 && index < sig.size()) ? sig.get(index) : null;
    }

    /**
     * Finds the index of the closing bracket that matches the opening bracket
     * at {@code openIdx}.  Returns {@code -1} if the brackets are unbalanced.
     */
    private int findMatchingClose(List<JsToken> sig, int openIdx) {
        if (openIdx < 0 || openIdx >= sig.size()) {
            return -1;
        }
        JsToken openToken = sig.get(openIdx);
        if (openToken.type != JsTokenType.PUNCTUATION || openToken.text.length() != 1) {
            return -1;
        }
        char open = openToken.text.charAt(0);
        if (open != '(' && open != '[' && open != '{') {
            return -1;
        }
        char close = closingFor(open);
        int depth = 0;
        for (int j = openIdx; j < sig.size(); j++) {
            JsToken t = sig.get(j);
            if (t.type == JsTokenType.PUNCTUATION && t.text.length() == 1) {
                char c = t.text.charAt(0);
                if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return j;
                    }
                }
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    //  Utilities
    // ------------------------------------------------------------------

    private static int compareByPosition(ValidationError a, ValidationError b) {
        int lineCmp = Integer.compare(a.getLine(), b.getLine());
        return lineCmp != 0 ? lineCmp : Integer.compare(a.getColumn(), b.getColumn());
    }
}
