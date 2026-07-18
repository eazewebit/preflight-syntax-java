package com.neel.syntaxvalidation.validator.java.checker;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.validator.java.JavaToken;
import com.neel.syntaxvalidation.validator.java.JavaTokenType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Performs structural and grammatical sanity checks that can be reliably
 * derived from keyword and operator placement without full semantic analysis.
 *
 * <p>Each check in this component is deliberately conservative: it only flags
 * patterns that are unambiguously illegal in Java, minimising false positives.
 * The kinds of anomalies detected include:
 *
 * <ul>
 *   <li><b>Conflicting access modifiers</b> &mdash; e.g. {@code public private}.</li>
 *   <li><b>Duplicate modifiers</b> &mdash; e.g. {@code static static}.</li>
 *   <li><b>Reserved-but-unused keywords</b> &mdash; {@code const} and {@code goto} are
 *       permanently illegal as identifiers.</li>
 *   <li><b>Malformed annotations</b> &mdash; {@code @} not followed by an identifier.</li>
 *   <li><b>Incomplete statements</b> &mdash; {@code assert} with no guard expression,
 *       empty {@code package}/{@code import} targets.</li>
 * </ul>
 *
 * <p>Deeper semantic validation (type checking, name resolution, overload
 * resolution) is intentionally left to the {@code javac} binary phase.
 */
public final class KeywordUsageChecker implements SyntaxChecker {

    private static final Set<String> ACCESS_MODIFIERS = Set.of("public", "protected", "private");

    private static final Set<String> MODIFIERS = Set.of(
            "public", "protected", "private", "static", "final", "abstract",
            "synchronized", "native", "strictfp", "transient", "volatile", "default"
    );

    @Override
    public void check(List<JavaToken> tokens, List<ValidationError> errors) {
        int i = 0;
        int n = tokens.size();
        while (i < n) {
            JavaToken t = tokens.get(i);
            switch (t.type()) {
                case PUNCTUATION -> i = checkPunctuation(tokens, i, errors);
                case KEYWORD -> i = checkKeyword(tokens, i, errors);
                default -> i++;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Punctuation-level checks
    // ------------------------------------------------------------------

    private int checkPunctuation(List<JavaToken> tokens, int i, List<ValidationError> errors) {
        JavaToken t = tokens.get(i);
        if ("@".equals(t.text())) {
            JavaToken next = lookahead(tokens, i + 1);
            if (next == null || (next.type() != JavaTokenType.IDENTIFIER
                    && !(next.type() == JavaTokenType.KEYWORD && isAnnotationKeyword(next.text())))) {
                errors.add(new ValidationError(
                        t.line(), t.column(),
                        "Annotation '@' must be followed by an identifier", "@"));
            }
        }
        return i + 1;
    }

    // ------------------------------------------------------------------
    //  Keyword-level checks
    // ------------------------------------------------------------------

    private int checkKeyword(List<JavaToken> tokens, int i, List<ValidationError> errors) {
        JavaToken t = tokens.get(i);
        String kw = t.text();

        // const / goto — permanently reserved, illegal in any program.
        if ("const".equals(kw) || "goto".equals(kw)) {
            errors.add(new ValidationError(
                    t.line(), t.column(),
                    "'" + kw + "' is a reserved keyword and cannot be used", kw));
            return i + 1;
        }

        // Modifier-run analysis.
        if (MODIFIERS.contains(kw)) {
            return checkModifierRun(tokens, i, errors);
        }

        // assert <expr> [ : <expr> ] ;
        if ("assert".equals(kw)) {
            JavaToken next = lookahead(tokens, i + 1);
            if (next == null || isTerminator(next)) {
                errors.add(new ValidationError(
                        t.line(), t.column(),
                        "'assert' must be followed by a boolean expression", kw));
            }
            return i + 1;
        }

        // package <identifier-list> ;
        if ("package".equals(kw)) {
            JavaToken next = lookahead(tokens, i + 1);
            if (next == null || next.type() != JavaTokenType.IDENTIFIER) {
                errors.add(new ValidationError(
                        t.line(), t.column(),
                        "'package' must be followed by a package name", kw));
            }
            return i + 1;
        }

        // import [static] <identifier-list> ;  |  import module <name> ;
        if ("import".equals(kw)) {
            JavaToken next = lookahead(tokens, i + 1);
            if (next == null
                    || (next.type() != JavaTokenType.IDENTIFIER
                    && !(next.type() == JavaTokenType.KEYWORD
                    && ("static".equals(next.text()) || "module".equals(next.text()))))) {
                errors.add(new ValidationError(
                        t.line(), t.column(),
                        "'import' must be followed by a class/package name, 'static' or 'module'", kw));
            }
            return i + 1;
        }

        return i + 1;
    }

    /**
     * Collects a maximal run of consecutive modifier keywords (skipping
     * annotations, which may legally interleave) and reports conflicts.
     */
    private int checkModifierRun(List<JavaToken> tokens, int i, List<ValidationError> errors) {
        Set<String> seen = new HashSet<>();
        String firstAccess = null;
        int j = i;
        while (j < tokens.size()) {
            JavaToken t = tokens.get(j);
            if (t.type() == JavaTokenType.KEYWORD && MODIFIERS.contains(t.text())) {
                String mod = t.text();
                if (seen.contains(mod)) {
                    errors.add(new ValidationError(
                            t.line(), t.column(),
                            "Duplicate modifier '" + mod + "'", mod));
                }
                seen.add(mod);
                if (ACCESS_MODIFIERS.contains(mod)) {
                    if (firstAccess != null && !firstAccess.equals(mod)) {
                        errors.add(new ValidationError(
                                t.line(), t.column(),
                                "Conflicting access modifiers: '" + firstAccess + "' and '" + mod + "'", mod));
                    }
                    if (firstAccess == null) {
                        firstAccess = mod;
                    }
                }
                j++;
            } else if (t.type() == JavaTokenType.PUNCTUATION && "@".equals(t.text())) {
                // Skip the annotation (and its identifier).
                j++;
                if (j < tokens.size()) {
                    j++;
                }
            } else {
                break;
            }
        }
        return Math.max(j, i + 1);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static JavaToken lookahead(List<JavaToken> tokens, int index) {
        if (index < 0 || index >= tokens.size()) {
            return null;
        }
        JavaToken t = tokens.get(index);
        // Skip comments to find the next meaningful token.
        int i = index;
        while (i < tokens.size() && tokens.get(i).type() == JavaTokenType.COMMENT) {
            i++;
        }
        return i < tokens.size() ? tokens.get(i) : null;
    }

    private static boolean isTerminator(JavaToken t) {
        if (t.type() == JavaTokenType.PUNCTUATION) {
            return ";".equals(t.text()) || "}".equals(t.text()) || ")".equals(t.text());
        }
        return t.type() == JavaTokenType.EOF;
    }

    private static boolean isAnnotationKeyword(String kw) {
        // Certain restricted identifiers are valid annotation type names.
        return "record".equals(kw) || "interface".equals(kw);
    }
}
