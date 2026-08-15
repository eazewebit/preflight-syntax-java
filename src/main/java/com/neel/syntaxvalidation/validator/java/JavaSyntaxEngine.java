package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.java.checker.DelimiterBalanceChecker;
import com.neel.syntaxvalidation.validator.java.checker.KeywordUsageChecker;
import com.neel.syntaxvalidation.validator.java.checker.SyntaxChecker;
import com.neel.syntaxvalidation.validator.java.checker.TokenizationErrorChecker;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure-Java, non-executing syntax validation engine for Java source code.
 *
 * <p>This is the first phase of the two-phase validation strategy used by
 * {@link JavaValidator}. It performs a fast, dependency-free structural pass
 * over the source text and is therefore always available &mdash; even on
 * machines where no {@code javac} binary is installed. The engine delegates the
 * actual analysis to a pipeline of isolated {@link SyntaxChecker} components,
 * each responsible for a distinct family of syntactic rules:
 *
 * <ol>
 *   <li>{@link TokenizationErrorChecker} &mdash; surfaces lexer-level
 *       anomalies (unterminated literals, illegal characters).</li>
 *   <li>{@link DelimiterBalanceChecker} &mdash; verifies that parentheses,
 *       brackets and braces are balanced and correctly nested.</li>
 *   <li>{@link KeywordUsageChecker} &mdash; flags malformed modifier runs,
 *       reserved-keyword misuse, and incomplete statements.</li>
 * </ol>
 *
 * <p>The modular design means new checks can be added by implementing
 * {@link SyntaxChecker} and registering it in {@link #createCheckers()},
 * without touching any existing component.
 *
 * <p>The engine never throws for malformed source; every anomaly is reported
 * as a {@link ValidationError} inside the returned {@link ValidationResult}.
 */
public final class JavaSyntaxEngine {

    private final List<SyntaxChecker> checkers;

    /** Shared default instance used by the static convenience method. */
    private static final JavaSyntaxEngine DEFAULT = new JavaSyntaxEngine();

    /** Creates an engine with the standard, built-in checker pipeline. */
    public JavaSyntaxEngine() {
        this(createCheckers());
    }

    /**
     * Creates an engine with an explicit, custom checker pipeline.
     *
     * <p>This constructor is primarily useful for testing or for downstream
     * integrations that wish to add proprietary checks.
     *
     * @param checkers the ordered checkers to run; never {@code null}
     */
    public JavaSyntaxEngine(List<SyntaxChecker> checkers) {
        this.checkers = List.copyOf(checkers);
    }

    /**
     * Validates the given Java source text.
     *
     * @param source the raw Java source; never {@code null}
     * @return an immutable result capturing every diagnostic discovered;
     *         never {@code null}
     */
    public ValidationResult validate(String source) {
        if (source == null || source.isEmpty()) {
            return ValidationResult.valid(
                    "Java syntax is valid (validated by the built-in Java syntax engine).");
        }
        List<JavaToken> tokens = new JavaLexer(source).tokenize();
        List<ValidationError> errors = new ArrayList<>();
        for (SyntaxChecker checker : checkers) {
            checker.check(tokens, errors);
        }
        return errors.isEmpty()
                ? ValidationResult.valid(
                        "Java syntax is valid (validated by the built-in Java syntax engine).")
                : ValidationResult.invalid(
                        "Java syntax validation failed with " + errors.size()
                                + " error(s) detected by the built-in Java syntax engine.",
                        errors);
    }

    /**
     * Static convenience method matching the calling convention used elsewhere
     * in the library.
     *
     * @param source the raw Java source; never {@code null}
     * @return the validation result; never {@code null}
     */
    public static ValidationResult validateStatic(String source) {
        return DEFAULT.validate(source);
    }

    /** Returns an unmodifiable view of the active checkers, in execution order. */
    public List<SyntaxChecker> getCheckers() {
        return checkers;
    }

    // ------------------------------------------------------------------
    //  Pipeline assembly
    // ------------------------------------------------------------------

    private static List<SyntaxChecker> createCheckers() {
        List<SyntaxChecker> list = new ArrayList<>(3);
        list.add(new TokenizationErrorChecker());
        list.add(new DelimiterBalanceChecker());
        list.add(new KeywordUsageChecker());
        return list;
    }
}
