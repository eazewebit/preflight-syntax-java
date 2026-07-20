package com.neel.syntaxvalidation.validator.python;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java syntax validation engine for Python 3.14 source code.
 *
 * <p>This engine orchestrates the two-step validation pipeline:
 *
 * <ol>
 *   <li><b>Lexical analysis</b> — {@link PythonLexer} tokenises the source,
 *       catching unterminated strings, invalid characters, and indentation
 *       errors.</li>
 *   <li><b>Structural parsing</b> — {@link PythonParser} checks the token
 *       stream for delimiter balance, colon placement, assignment targets,
 *       and other structural constraints.</li>
 *   <li><b>Pattern-based checks</b> — additional heuristics for common Python
 *       syntax pitfalls not caught by the token-level parser.</li>
 * </ol>
 *
 * <p>The engine is designed to be invoked as <b>Phase 1</b> of the two-phase
 * validation strategy in {@link com.neel.syntaxvalidation.validator.python.PythonValidator}.
 * If the engine finds errors, the validator can return them immediately without
 * invoking the external Python binary.
 *
 * <p>This class is thread-safe (stateless after construction).
 */
public final class PythonSyntaxEngine {

    private PythonSyntaxEngine() {}

    /**
     * Validates the given Python source code using the pure-Java engine.
     *
     * @param source the Python source code to validate.
     * @return a {@link ValidationResult} that is valid if no syntax issues were
     *         found, or invalid with detailed error messages otherwise.
     */
    public static ValidationResult validate(String source) {
        if (source == null || source.isBlank()) {
            return ValidationResult.valid("Empty source is trivially valid.");
        }

        List<ValidationError> errors = new ArrayList<>();

        // Step 1: Lexical analysis
        PythonLexer lexer = new PythonLexer(source);
        List<PythonToken> tokens;
        try {
            tokens = lexer.tokenize();
        } catch (Exception e) {
            errors.add(new ValidationError(1, 1,
                    "Lexical analysis failed: " + e.getMessage(), null));
            return ValidationResult.invalid("Lexical analysis failed.", errors);
        }

        // Collect lexer errors (null-type tokens indicate errors)
        for (PythonToken token : tokens) {
            if (token.type() == null) {
                String msg = token.text();
                if (msg.startsWith("LEXER_ERROR:")) {
                    msg = msg.substring("LEXER_ERROR:".length());
                }
                errors.add(new ValidationError(token.line(), token.column(),
                        "Lexical error: " + msg, null));
            }
        }

        // Step 2: Structural parsing
        ValidationResult parseResult = PythonParser.validate(tokens);
        if (!parseResult.isValid()) {
            errors.addAll(parseResult.getErrors());
        }

        // Step 3: Pattern-based checks on raw source
        List<ValidationError> patternErrors = checkSourcePatterns(source);
        errors.addAll(patternErrors);

        // Deduplicate
        errors = deduplicateErrors(errors);

        if (errors.isEmpty()) {
            return ValidationResult.valid("Python syntax is valid (built-in engine).");
        }

        return ValidationResult.invalid(
                "Python syntax has " + errors.size() + " error(s).", errors);
    }

    // ==================================================================
    //  Pattern-based source checks
    // ==================================================================

    private static List<ValidationError> checkSourcePatterns(String source) {
        List<ValidationError> errors = new ArrayList<>();
        String[] lines = source.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            int lineNum = i + 1;
            String line = lines[i];
            String trimmed = line.trim();

            // Mixed tabs and spaces
            if (!line.isEmpty() && line.contains("\t") && line.contains(" ")) {
                int leadingSpaces = 0, leadingTabs = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') leadingSpaces++;
                    else if (c == '\t') leadingTabs++;
                    else break;
                }
                if (leadingSpaces > 0 && leadingTabs > 0) {
                    errors.add(new ValidationError(lineNum, 1,
                            "Mixed tabs and spaces in indentation at line " + lineNum + ".", null));
                }
            }

            // Assignment in condition: "if x = 5:"
            if (trimmed.startsWith("if ") || trimmed.startsWith("elif ") || trimmed.startsWith("while ")) {
                if (hasSingleEqualsInCondition(trimmed)) {
                    errors.add(new ValidationError(lineNum, line.indexOf("=") + 1,
                            "Possible assignment in condition at line " + lineNum + ". Did you mean '=='?", null));
                }
            }

            // Python 2 print statement
            if (trimmed.startsWith("print ") && !trimmed.contains("(")) {
                errors.add(new ValidationError(lineNum, 1,
                        "Python 2-style print statement detected at line " + lineNum + ". Use print() function.", null));
            }

            // Python 2 except syntax
            if (trimmed.startsWith("except ") && trimmed.contains(",") && !trimmed.contains(" as ")) {
                int exceptIdx = trimmed.indexOf("except ");
                String rest = trimmed.substring(exceptIdx + 7);
                if (rest.contains(",") && !rest.contains("(")) {
                    errors.add(new ValidationError(lineNum, exceptIdx + 1,
                            "Python 2-style except clause at line " + lineNum + ". Use 'except ... as ...' syntax.", null));
                }
            }
        }

        // Encoding declaration checks
        int encodingCount = 0;
        int lastEncodingLine = 0;
        for (int i = 0; i < lines.length && i < 3; i++) {
            String line = lines[i];
            if (line.contains("#") && (line.contains("coding") || line.contains("encoding"))) {
                if (line.matches(".*#.*coding[:=]\\s*\\S+.*") || line.matches(".*#.*-\\*-.*coding[:=]\\s*\\S+.*-\\*-.*")) {
                    encodingCount++;
                    lastEncodingLine = i + 1;
                }
            }
        }
        if (encodingCount > 1) {
            errors.add(new ValidationError(lastEncodingLine, 1,
                    "Multiple encoding declarations found. Only one is allowed.", null));
        }

        // Check for encoding declarations after line 2
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("#") && (line.contains("coding") || line.contains("encoding"))) {
                if (line.matches(".*#.*coding[:=]\\s*\\S+.*") || line.matches(".*#.*-\\*-.*coding[:=]\\s*\\S+.*-\\*-.*")) {
                    errors.add(new ValidationError(i + 1, 1,
                            "Encoding declaration is not on line 1 or 2 at line " + (i + 1) + ".", null));
                    break;
                }
            }
        }

        return errors;
    }

    private static boolean hasSingleEqualsInCondition(String line) {
        int start = 0;
        if (line.startsWith("if ")) start = 3;
        else if (line.startsWith("elif ")) start = 5;
        else if (line.startsWith("while ")) start = 6;

        String cond = line.substring(start).trim();
        if (cond.endsWith(":")) cond = cond.substring(0, cond.length() - 1).trim();

        Pattern p = Pattern.compile("(?<![!=<>:+\\-*/%&|^~])=(?!=)");
        return p.matcher(cond).find();
    }

    private static List<ValidationError> deduplicateErrors(List<ValidationError> errors) {
        List<ValidationError> deduped = new ArrayList<>();
        for (ValidationError err : errors) {
            boolean duplicate = false;
            for (ValidationError existing : deduped) {
                if (existing.getLine() == err.getLine()
                        && existing.getColumn() == err.getColumn()
                        && existing.getMessage().equals(err.getMessage())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) deduped.add(err);
        }
        return deduped;
    }
}
