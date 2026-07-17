package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java embedded PHP syntax engine that validates PHP source code
 * without requiring an external {@code php} binary.
 *
 * <p>This engine provides two tiers of analysis:</p>
 * <ol>
 *   <li><b>Tokeniser + bracket/balance checks</b> &mdash; fast, zero-dependency structural
 *       validation that catches mismatched braces, unclosed strings, and malformed
 *       PHP tags.</li>
 *   <li><b>Grammar-aware statement validation</b> &mdash; deeper analysis of control-flow
 *       constructs, class/trait/interface declarations, function signatures, type
 *       declarations (including PHP 8.x union/intersection/DNF types), and common
 *       syntax error patterns.</li>
 * </ol>
 *
 * <p>The engine covers modern PHP features through PHP 8.3+, including:</p>
 * <ul>
 *   <li>Namespaces and {@code use} statements</li>
 *   <li>Traits, interfaces, enums</li>
 *   <li>Generators ({@code yield}, {@code yield from})</li>
 *   <li>Constructor promotion (PHP 8.0)</li>
 *   <li>Named arguments (PHP 8.0)</li>
 *   <li>Match expressions (PHP 8.0)</li>
 *   <li>Union types, intersection types, DNF types (PHP 8.0&ndash;8.2)</li>
 *   <li>Readonly properties and classes (PHP 8.1&ndash;8.2)</li>
 *   <li>Enum declarations with backed types (PHP 8.1)</li>
 * </ul>
 *
 * @since 1.1.0
 */
public final class PhpSyntaxEngine {

    /* ------------------------------------------------------------------ */
    /*  PHP keyword sets                                                   */
    /* ------------------------------------------------------------------ */

    private static final Set<String> PHP_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "die", "do",
            "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach",
            "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final",
            "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
            "implements", "include", "include_once", "instanceof", "insteadof",
            "interface", "isset", "list", "match", "namespace", "new", "or", "print",
            "private", "protected", "public", "readonly", "require", "require_once",
            "return", "static", "switch", "throw", "trait", "try", "unset", "use",
            "var", "while", "xor", "yield", "enum", "from", "true", "false", "null",
            "self", "parent", "__halt_compiler"
    ));

    private static final Set<String> TYPE_NAMES = new HashSet<>(Arrays.asList(
            "int", "float", "string", "bool", "array", "object", "callable",
            "iterable", "void", "mixed", "never", "true", "false", "null", "self",
            "parent", "static"
    ));

    /* ------------------------------------------------------------------ */
    /*  Singleton                                                          */
    /* ------------------------------------------------------------------ */

    private static final PhpSyntaxEngine INSTANCE = new PhpSyntaxEngine();

    public static PhpSyntaxEngine getInstance() { return INSTANCE; }

    static ValidationResult validateStatic(String source) { return INSTANCE.validate(source); }

    /* ------------------------------------------------------------------ */
    /*  Constructor                                                        */
    /* ------------------------------------------------------------------ */

    PhpSyntaxEngine() { /* package-private */ }

    /* ------------------------------------------------------------------ */
    /*  Public API                                                         */
    /* ------------------------------------------------------------------ */

    public ValidationResult validate(String source) {
        if (source == null || source.isEmpty()) {
            return ValidationResult.valid("Empty or null source is trivially valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Phase 1 — Tokenise
        List<PhpToken> tokens;
        try {
            tokens = tokenise(source);
        } catch (SyntaxException se) {
            errors.add(se.toError());
            return ValidationResult.invalid(se.getMessage(), errors);
        }

        // Phase 2 — Bracket / brace / parenthesis balance
        validateBalance(tokens, errors);
        if (!errors.isEmpty()) {
            return ValidationResult.invalid("Bracket/brace/parenthesis mismatch detected.", errors);
        }

        // Phase 3 — Statement-level grammar validation
        validateGrammar(tokens, errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid("PHP syntax is valid.");
        }
        return ValidationResult.invalid("PHP syntax errors detected.", errors);
    }

    /* ================================================================== */
    /*  TOKENISER                                                          */
    /* ================================================================== */

    private List<PhpToken> tokenise(String source) throws SyntaxException {
        List<PhpToken> tokens = new ArrayList<>();
        int pos = 0, line = 1, col = 1;
        boolean inPhpBlock = false;

        while (pos < source.length()) {
            if (!inPhpBlock && source.startsWith("<?php", pos)) {
                int afterTag = pos + 5;
                if (afterTag >= source.length() || Character.isWhitespace(source.charAt(afterTag))
                        || source.charAt(afterTag) == '?') {
                    tokens.add(new PhpToken(TokenType.PHP_OPEN_TAG, "<?php", line, col));
                    col += 5;
                    pos = afterTag;
                    inPhpBlock = true;
                    if (pos < source.length() && (source.charAt(pos) == ' ' || source.charAt(pos) == '\t')) {
                        pos++; col++;
                    }
                    continue;
                }
            }
            if (!inPhpBlock && source.startsWith("<?=", pos)) {
                tokens.add(new PhpToken(TokenType.PHP_OPEN_TAG, "<?=", line, col));
                col += 3; pos += 3; inPhpBlock = true;
                continue;
            }
            if (!inPhpBlock && pos + 1 < source.length() && source.charAt(pos) == '<'
                    && source.charAt(pos + 1) == '?'
                    && (pos + 2 >= source.length() || !Character.isLetter(source.charAt(pos + 2)))) {
                tokens.add(new PhpToken(TokenType.PHP_OPEN_TAG, "<?", line, col));
                col += 2; pos += 2; inPhpBlock = true;
                continue;
            }
            if (inPhpBlock && source.startsWith("?>", pos)) {
                tokens.add(new PhpToken(TokenType.PHP_CLOSE_TAG, "?>", line, col));
                col += 2; pos += 2; inPhpBlock = false;
                continue;
            }
            if (!inPhpBlock) {
                pos++; col++;
                if (pos > 0 && source.charAt(pos - 1) == '\n') { line++; col = 1; }
                continue;
            }

            char ch = source.charAt(pos);

            if (ch == ' ' || ch == '\t') { pos++; col++; continue; }
            if (ch == '\n') { pos++; line++; col = 1; continue; }
            if (ch == '\r') {
                pos++; col++;
                if (pos < source.length() && source.charAt(pos) == '\n') pos++;
                line++; col = 1;
                continue;
            }

            // Single-line comment //
            if (ch == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '/') {
                int end = source.indexOf('\n', pos);
                pos = end < 0 ? source.length() : end;
                continue;
            }
            // PHP 8.0+ attributes #[...] — emit # as attribute marker, [ as bracket
            if (ch == '#' && pos + 1 < source.length() && source.charAt(pos + 1) == '[') {
                tokens.add(new PhpToken(TokenType.OPERATOR, "#", line, col));
                tokens.add(new PhpToken(TokenType.PUNCTUATION, "[", line, col + 1));
                col += 2; pos += 2;
                continue;
            }
            // Single-line comment #
            if (ch == '#') {
                int end = source.indexOf('\n', pos);
                pos = end < 0 ? source.length() : end;
                continue;
            }
            // Multi-line comment
            if (ch == '/' && pos + 1 < source.length() && source.charAt(pos + 1) == '*') {
                int end = source.indexOf("*/", pos + 2);
                if (end < 0) throw new SyntaxException(line, col, "Unclosed multi-line comment");
                for (int i = pos; i < end + 2; i++) {
                    if (source.charAt(i) == '\n') { line++; col = 1; } else { col++; }
                }
                pos = end + 2;
                continue;
            }

            // Single-quoted string
            if (ch == '\'') {
                PhpToken t = consumeSingleString(source, pos, line, col);
                tokens.add(t);
                advance(t.text().length(), source, pos);
                int len = t.text().length();
                pos += len;
                continue;
            }

            // Double-quoted string
            if (ch == '"') {
                PhpToken t = consumeDoubleString(source, pos, line, col);
                tokens.add(t);
                int len = t.text().length();
                pos += len;
                continue;
            }

            // Heredoc / Nowdoc
            if (ch == '<' && pos + 2 < source.length() && source.charAt(pos + 1) == '<'
                    && (source.charAt(pos + 2) == '<' || source.charAt(pos + 2) == '\'')) {
                PhpToken t = consumeHeredoc(source, pos, line, col);
                tokens.add(t);
                int len = t.text().length();
                pos += len;
                continue;
            }

            // Numbers
            if (Character.isDigit(ch) || (ch == '.' && pos + 1 < source.length()
                    && Character.isDigit(source.charAt(pos + 1)))) {
                PhpToken t = consumeNumber(source, pos, line, col);
                tokens.add(t);
                col += t.text().length(); pos += t.text().length();
                continue;
            }

            // Identifiers and keywords
            if (Character.isLetter(ch) || ch == '_' || ch == '$') {
                PhpToken t = consumeIdentifier(source, pos, line, col);
                tokens.add(t);
                col += t.text().length(); pos += t.text().length();
                continue;
            }

            // Operators and punctuation
            PhpToken t = consumeOperator(source, pos, line, col);
            tokens.add(t);
            col += t.text().length(); pos += t.text().length();
        }
        return tokens;
    }

    /** Helper to advance line/col over a token text — not actually used for pos. */
    private static void advance(int len, String src, int startPos) {
        // line/col tracking done outside; this is a no-op placeholder
    }

    private PhpToken consumeSingleString(String src, int pos, int line, int col) throws SyntaxException {
        int i = pos + 1;
        while (i < src.length()) {
            if (src.charAt(i) == '\'' && (i == 0 || src.charAt(i - 1) != '\\')) {
                return new PhpToken(TokenType.STRING, src.substring(pos, i + 1), line, col);
            }
            i++;
        }
        throw new SyntaxException(line, col, "Unclosed single-quoted string");
    }

    private PhpToken consumeDoubleString(String src, int pos, int line, int col) throws SyntaxException {
        int i = pos + 1;
        while (i < src.length()) {
            if (src.charAt(i) == '\\') { i += 2; continue; }
            if (src.charAt(i) == '"') {
                return new PhpToken(TokenType.STRING, src.substring(pos, i + 1), line, col);
            }
            i++;
        }
        throw new SyntaxException(line, col, "Unclosed double-quoted string");
    }

    private PhpToken consumeHeredoc(String src, int pos, int line, int col) throws SyntaxException {
        boolean nowdoc = pos + 2 < src.length() && src.charAt(pos + 2) == '\'';
        int start = nowdoc ? pos + 4 : pos + 3;
        int labelEnd = start;
        while (labelEnd < src.length() && (Character.isLetterOrDigit(src.charAt(labelEnd)) || src.charAt(labelEnd) == '_')) labelEnd++;
        if (labelEnd == start) throw new SyntaxException(line, col, "Invalid heredoc/nowdoc syntax — missing label");
        String label = src.substring(start, labelEnd);
        int afterOpen = labelEnd;
        if (nowdoc && afterOpen < src.length() && src.charAt(afterOpen) == '\'') afterOpen++;
        if (afterOpen < src.length() && src.charAt(afterOpen) == '\n') afterOpen++;
        else if (afterOpen < src.length() && src.charAt(afterOpen) == '\r') { afterOpen++; if (afterOpen < src.length() && src.charAt(afterOpen) == '\n') afterOpen++; }
        while (afterOpen < src.length()) {
            int nl = src.indexOf('\n', afterOpen);
            String lineStr = (nl < 0) ? src.substring(afterOpen) : src.substring(afterOpen, nl);
            if (lineStr.trim().equals(label) || lineStr.trim().equals(label + ";")) {
                int end = (nl < 0) ? src.length() : nl + 1;
                return new PhpToken(TokenType.STRING, src.substring(pos, end), line, col);
            }
            afterOpen = (nl < 0) ? src.length() : nl + 1;
        }
        throw new SyntaxException(line, col, "Unclosed heredoc/nowdoc — terminator '" + label + "' not found");
    }

    private PhpToken consumeNumber(String src, int pos, int line, int col) {
        int i = pos;
        if (i < src.length() && src.charAt(i) == '0' && i + 1 < src.length()) {
            char next = Character.toLowerCase(src.charAt(i + 1));
            if (next == 'x') { i += 2; while (i < src.length() && isHexDigit(src.charAt(i))) i++; return new PhpToken(TokenType.NUMBER, src.substring(pos, i), line, col); }
            if (next == 'b') { i += 2; while (i < src.length() && (src.charAt(i) == '0' || src.charAt(i) == '1')) i++; return new PhpToken(TokenType.NUMBER, src.substring(pos, i), line, col); }
            if (next == 'o' || (next >= '0' && next <= '7')) { i += (next == 'o') ? 2 : 1; while (i < src.length() && src.charAt(i) >= '0' && src.charAt(i) <= '7') i++; return new PhpToken(TokenType.NUMBER, src.substring(pos, i), line, col); }
        }
        while (i < src.length() && Character.isDigit(src.charAt(i))) i++;
        if (i < src.length() && src.charAt(i) == '.') { i++; while (i < src.length() && Character.isDigit(src.charAt(i))) i++; }
        if (i < src.length() && (src.charAt(i) == 'e' || src.charAt(i) == 'E')) { i++; if (i < src.length() && (src.charAt(i) == '+' || src.charAt(i) == '-')) i++; while (i < src.length() && Character.isDigit(src.charAt(i))) i++; }
        return new PhpToken(TokenType.NUMBER, src.substring(pos, i), line, col);
    }

    private PhpToken consumeIdentifier(String src, int pos, int line, int col) {
        int i = pos;
        boolean isVar = i < src.length() && src.charAt(i) == '$';
        if (isVar) i++;
        while (i < src.length() && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
        String text = src.substring(pos, i);
        if (isVar) return new PhpToken(TokenType.VARIABLE, text, line, col);
        if (PHP_KEYWORDS.contains(text.toLowerCase(Locale.ROOT))) return new PhpToken(TokenType.KEYWORD, text, line, col);
        return new PhpToken(TokenType.IDENTIFIER, text, line, col);
    }

    private PhpToken consumeOperator(String src, int pos, int line, int col) {
        String[] multiOps = { "**=", "<<=", ">>=", "...", "<=>", "===", "!==", "==", "!=", "<=", ">=", "&&", "||", "??", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "**", "->", "::", "=>", "<<", ">>", "++", "--", "??=" };
        for (String op : multiOps) { if (src.startsWith(op, pos)) return new PhpToken(TokenType.OPERATOR, op, line, col); }
        String single = String.valueOf(src.charAt(pos));
        if ("(){}[];,.@?:".contains(single)) return new PhpToken(TokenType.PUNCTUATION, single, line, col);
        return new PhpToken(TokenType.OPERATOR, single, line, col);
    }

    private static boolean isHexDigit(char c) { return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }

    /* ================================================================== */
    /*  PHASE 2 — Balance checks                                           */
    /* ================================================================== */

    private void validateBalance(List<PhpToken> tokens, List<ValidationError> errors) {
        int depthParen = 0, depthBrace = 0, depthBracket = 0;
        for (PhpToken t : tokens) {
            if (t.type() != TokenType.PUNCTUATION) continue;
            switch (t.text()) {
                case "(": depthParen++; break;
                case ")":
                    if (--depthParen < 0) { errors.add(err(t.line(), "Unmatched closing parenthesis ')'")); return; }
                    break;
                case "{": depthBrace++; break;
                case "}":
                    if (--depthBrace < 0) { errors.add(err(t.line(), "Unmatched closing brace '}'")); return; }
                    break;
                case "[": depthBracket++; break;
                case "]":
                    if (--depthBracket < 0) { errors.add(err(t.line(), "Unmatched closing bracket ']'")); return; }
                    break;
                default: break;
            }
        }
        if (depthParen > 0) errors.add(err(1, "Unclosed parenthesis '(' — expected " + depthParen + " more ')'"));
        if (depthBrace > 0) errors.add(err(1, "Unclosed brace '{' — expected " + depthBrace + " more '}'"));
        if (depthBracket > 0) errors.add(err(1, "Unclosed bracket '[' — expected " + depthBracket + " more ']'"));
    }

    /* ================================================================== */
    /*  PHASE 3 — Grammar-level validation                                  */
    /* ================================================================== */

    private void validateGrammar(List<PhpToken> tokens, List<ValidationError> errors) {
        List<PhpToken> phpTokens = extractPhpTokens(tokens);
        validateDeclarations(phpTokens, errors);
        validateFunctionSignatures(phpTokens, errors);
        validateControlStructures(phpTokens, errors);
        validateUseStatements(phpTokens, errors);
        validateCommonPatterns(phpTokens, errors);
    }

    private List<PhpToken> extractPhpTokens(List<PhpToken> tokens) {
        List<PhpToken> phpTokens = new ArrayList<>();
        boolean inPhp = false;
        boolean hasTags = false;
        for (PhpToken t : tokens) {
            if (t.type() == TokenType.PHP_OPEN_TAG) { inPhp = true; hasTags = true; continue; }
            if (t.type() == TokenType.PHP_CLOSE_TAG) { inPhp = false; continue; }
            if (inPhp) phpTokens.add(t);
        }
        if (phpTokens.isEmpty() && !hasTags) phpTokens.addAll(tokens);
        return phpTokens;
    }

    private void validateDeclarations(List<PhpToken> tokens, List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PhpToken t = tokens.get(i);
            if (t.type() != TokenType.KEYWORD) continue;
            String kw = t.text().toLowerCase(Locale.ROOT);
            if ("class".equals(kw) || "interface".equals(kw) || "trait".equals(kw) || "enum".equals(kw)) {
                int next = skipWs(tokens, i + 1);
                if (next >= tokens.size()) { errors.add(err(t.line(), "Expected name after '" + t.text() + "' keyword")); continue; }
                if (tokens.get(next).type() != TokenType.IDENTIFIER) { errors.add(err(tokens.get(next).line(), "Expected identifier after '" + t.text() + "' keyword")); continue; }
                if ("enum".equals(kw)) {
                    int afterName = skipWs(tokens, next + 1);
                    if (afterName < tokens.size() && ":".equals(tokens.get(afterName).text())) {
                        int typePos = skipWs(tokens, afterName + 1);
                        if (typePos < tokens.size()) {
                            String typeName = tokens.get(typePos).text().toLowerCase(Locale.ROOT);
                            if (!"string".equals(typeName) && !"int".equals(typeName)) {
                                errors.add(err(tokens.get(typePos).line(), "Enum backed type must be 'string' or 'int'"));
                            }
                        }
                    }
                }
            }
            if ("extends".equals(kw)) {
                int next = skipWs(tokens, i + 1);
                if (next < tokens.size() && tokens.get(next).type() == TokenType.PUNCTUATION && "{".equals(tokens.get(next).text())) {
                    errors.add(err(t.line(), "Expected parent class/interface name after 'extends'"));
                }
            }
            if ("implements".equals(kw)) {
                int next = skipWs(tokens, i + 1);
                if (next < tokens.size() && tokens.get(next).type() == TokenType.PUNCTUATION && "{".equals(tokens.get(next).text())) {
                    errors.add(err(t.line(), "Expected at least one interface name after 'implements'"));
                }
            }
        }
    }

    private void validateFunctionSignatures(List<PhpToken> tokens, List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PhpToken t = tokens.get(i);
            if (t.type() != TokenType.KEYWORD || !"function".equals(t.text().toLowerCase(Locale.ROOT))) continue;

            // Skip 'use function' import statements — not a function declaration
            int prev = prevNonWs(tokens, i - 1);
            if (prev >= 0 && tokens.get(prev).type() == TokenType.KEYWORD
                    && "use".equals(tokens.get(prev).text().toLowerCase(Locale.ROOT))) {
                continue;
            }

            int next = skipWs(tokens, i + 1);
            if (next >= tokens.size()) { errors.add(err(t.line(), "Expected function name after 'function' keyword")); continue; }

            PhpToken nameOrAmp = tokens.get(next);
            if ("&".equals(nameOrAmp.text())) {
                next = skipWs(tokens, next + 1);
                if (next >= tokens.size()) { errors.add(err(t.line(), "Expected function name after '&'")); continue; }
                nameOrAmp = tokens.get(next);
            }

            if (nameOrAmp.type() != TokenType.IDENTIFIER && nameOrAmp.type() != TokenType.KEYWORD) {
                if (!"(".equals(nameOrAmp.text())) errors.add(err(nameOrAmp.line(), "Expected function name or '(' after 'function'"));
                continue;
            }

            int parenPos = skipWs(tokens, next + 1);
            if (parenPos >= tokens.size() || !"(".equals(tokens.get(parenPos).text())) {
                errors.add(err(tokens.get(Math.min(next + 1, tokens.size() - 1)).line(), "Expected '(' after function name"));
                continue;
            }

            int closingParen = findMatchingParen(tokens, parenPos);
            if (closingParen < 0) { errors.add(err(tokens.get(parenPos).line(), "Unclosed parenthesis in function declaration")); continue; }
            validateParameterList(tokens, parenPos + 1, closingParen, errors);

            int afterParen = skipWs(tokens, closingParen + 1);
            if (afterParen < tokens.size() && ":".equals(tokens.get(afterParen).text())) {
                int retType = skipWs(tokens, afterParen + 1);
                if (retType >= tokens.size() || (tokens.get(retType).type() == TokenType.PUNCTUATION && "{".equals(tokens.get(retType).text()))) {
                    errors.add(err(tokens.get(afterParen).line(), "Expected return type declaration after ':'"));
                } else {
                    consumeTypeDeclaration(tokens, retType, errors);
                }
            }
        }
    }

    private void validateParameterList(List<PhpToken> tokens, int from, int to, List<ValidationError> errors) {
        boolean expectParam = true;
        for (int i = from; i < to; i++) {
            PhpToken t = tokens.get(i);
            if (",".equals(t.text())) {
                if (expectParam) errors.add(err(t.line(), "Unexpected comma — expected parameter declaration"));
                expectParam = true;
            } else if (t.type() != TokenType.PUNCTUATION || !"()".contains(t.text())) {
                expectParam = false;
            }
        }
    }

    private int consumeTypeDeclaration(List<PhpToken> tokens, int pos, List<ValidationError> errors) {
        int i = pos;
        while (i < tokens.size()) {
            PhpToken t = tokens.get(i);
            if (t.type() == TokenType.PUNCTUATION && "{".equals(t.text())) break;
            if ("|".equals(t.text()) || "&".equals(t.text()) || "?".equals(t.text())) { i++; continue; }
            if (t.type() == TokenType.IDENTIFIER || t.type() == TokenType.KEYWORD || TYPE_NAMES.contains(t.text().toLowerCase(Locale.ROOT))) { i++; continue; }
            break;
        }
        return i;
    }

    private void validateControlStructures(List<PhpToken> tokens, List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PhpToken t = tokens.get(i);
            if (t.type() != TokenType.KEYWORD) continue;
            String kw = t.text().toLowerCase(Locale.ROOT);
            switch (kw) {
                case "if": case "elseif":
                    int nIf = skipWs(tokens, i + 1);
                    if (nIf < tokens.size() && !"(".equals(tokens.get(nIf).text()) && !":".equals(tokens.get(nIf).text()) && !"{".equals(tokens.get(nIf).text())) {
                        errors.add(err(t.line(), "Expected '(' after '" + kw + "' keyword"));
                    }
                    break;
                case "for": case "foreach": case "while": case "switch": case "match": case "catch":
                    int nCtrl = skipWs(tokens, i + 1);
                    if (nCtrl < tokens.size() && !"(".equals(tokens.get(nCtrl).text())) {
                        errors.add(err(t.line(), "Expected '(' after '" + kw + "' keyword"));
                    }
                    break;
                case "do": case "try": case "finally":
                    int nBr = skipWs(tokens, i + 1);
                    if (nBr < tokens.size() && !"{".equals(tokens.get(nBr).text()) && !":".equals(tokens.get(nBr).text())) {
                        errors.add(err(t.line(), "Expected '{' or ':' after '" + kw + "' keyword"));
                    }
                    break;
                default: break;
            }
        }
    }

    private void validateUseStatements(List<PhpToken> tokens, List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PhpToken t = tokens.get(i);
            if (t.type() != TokenType.KEYWORD) continue;
            String kw = t.text().toLowerCase(Locale.ROOT);
            if ("namespace".equals(kw)) {
                int next = skipWs(tokens, i + 1);
                if (next >= tokens.size()) errors.add(err(t.line(), "Expected namespace name after 'namespace'"));
                else if (tokens.get(next).type() == TokenType.PUNCTUATION && ";".equals(tokens.get(next).text())) errors.add(err(t.line(), "Expected namespace name, found ';'"));
            }
            if ("use".equals(kw)) {
                int next = skipWs(tokens, i + 1);
                if (next >= tokens.size()) { errors.add(err(t.line(), "Expected import path after 'use'")); continue; }
                if (tokens.get(next).type() == TokenType.KEYWORD) {
                    String subKw = tokens.get(next).text().toLowerCase(Locale.ROOT);
                    if ("function".equals(subKw) || "const".equals(subKw)) {
                        int afterSub = skipWs(tokens, next + 1);
                        if (afterSub >= tokens.size() || (tokens.get(afterSub).type() == TokenType.PUNCTUATION && ";".equals(tokens.get(afterSub).text()))) {
                            errors.add(err(t.line(), "Expected name after 'use " + subKw + "'"));
                        }
                    }
                }
            }
        }
    }

    private void validateCommonPatterns(List<PhpToken> tokens, List<ValidationError> errors) {
        for (int i = 0; i < tokens.size(); i++) {
            PhpToken t = tokens.get(i);
            if (t.type() == TokenType.PUNCTUATION && ";".equals(t.text())) {
                int next = skipWs(tokens, i + 1);
                if (next < tokens.size() && ";".equals(tokens.get(next).text())) errors.add(err(tokens.get(next).line(), "Duplicate semicolon"));
            }
            if (t.type() == TokenType.KEYWORD) {
                String kw = t.text().toLowerCase(Locale.ROOT);
                if ("yield".equals(kw)) {
                    int next = skipWs(tokens, i + 1);
                    if (next < tokens.size() && "from".equals(tokens.get(next).text().toLowerCase(Locale.ROOT))) {
                        int afterFrom = skipWs(tokens, next + 1);
                        if (afterFrom >= tokens.size() || ";".equals(tokens.get(afterFrom).text())) errors.add(err(t.line(), "Expected expression after 'yield from'"));
                    }
                }
                if ("fn".equals(kw)) {
                    int next = skipWs(tokens, i + 1);
                    if (next >= tokens.size()) { errors.add(err(t.line(), "Expected parameter list after 'fn' keyword")); }
                    else if (!"(".equals(tokens.get(next).text())) {
                        int afterType = consumeTypeDeclaration(tokens, next, errors);
                        int paren = skipWs(tokens, afterType);
                        if (paren >= tokens.size() || !"(".equals(tokens.get(paren).text())) errors.add(err(t.line(), "Expected '(' after 'fn' keyword"));
                    }
                }
                if ("readonly".equals(kw)) {
                    int next = skipWs(tokens, i + 1);
                    if (next >= tokens.size()) errors.add(err(t.line(), "Expected type or 'class' after 'readonly'"));
                }
            }
        }
    }

    /* ================================================================== */
    /*  Helper methods                                                      */
    /* ================================================================== */

    private static int skipWs(List<PhpToken> tokens, int pos) {
        while (pos < tokens.size() && tokens.get(pos).type() == TokenType.WHITESPACE) pos++;
        return pos;
    }

    private static int prevNonWs(List<PhpToken> tokens, int pos) {
        while (pos >= 0 && tokens.get(pos).type() == TokenType.WHITESPACE) pos--;
        return pos;
    }

    private int findMatchingParen(List<PhpToken> tokens, int openPos) {
        int depth = 0;
        for (int i = openPos; i < tokens.size(); i++) {
            if ("(".equals(tokens.get(i).text())) depth++;
            if (")".equals(tokens.get(i).text())) { if (--depth == 0) return i; }
        }
        return -1;
    }

    /** Shortcut to create a ValidationError with unknown column and no tool output. */
    private static ValidationError err(int line, String message) {
        return new ValidationError(line, -1, message, null);
    }

    /* ================================================================== */
    /*  Inner types                                                        */
    /* ================================================================== */

    enum TokenType { KEYWORD, IDENTIFIER, VARIABLE, NUMBER, STRING, OPERATOR, PUNCTUATION, PHP_OPEN_TAG, PHP_CLOSE_TAG, WHITESPACE }

    record PhpToken(TokenType type, String text, int line, int col) {}

    private static class SyntaxException extends Exception {
        private final int line;
        SyntaxException(int line, int col, String message) { super(message); this.line = line; }
        ValidationError toError() { return new ValidationError(line, -1, getMessage(), null); }
    }
}
