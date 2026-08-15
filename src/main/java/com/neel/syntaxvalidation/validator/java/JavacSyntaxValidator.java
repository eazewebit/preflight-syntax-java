package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Validates Java source code syntax using the OpenJDK compiler's
 * {@code JavacTask.parse()} API for AST-level parsing without full compilation.
 *
 * <h2>Design Rationale</h2>
 * <p>This validator executes <em>only</em> the parse phase of the javac pipeline
 * &mdash; it builds the abstract syntax tree (AST) but never calls
 * {@code task.analyze()} or {@code task.generate()}. This means:
 * <ul>
 *   <li>100% strict syntax checking: unclosed brackets, bad tokens, missing
 *       semicolons, malformed control structures, and invalid keywords are caught.</li>
 *   <li>Zero false positives from missing packages, unresolved class symbols,
 *       or unimported project dependencies &mdash; the parser only needs valid syntax.</li>
 *   <li>Support for modern Java features up to Java 25 (including JEP 512 compact
 *       source files, JEP 511 module imports, and JEP 507 primitive patterns).</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Instances are thread-safe. Each {@link #validate(String, String)} call
 * creates all mutable state (diagnostics collector, file manager, compiler task)
 * as local variables. The {@link JavaCompiler} instance itself is a stateless
 * singleton.
 *
 * <h2>In-Memory File Handling</h2>
 * <p>Source code is streamed directly into the compiler via an in-memory
 * {@link SimpleJavaFileObject}, preventing any filesystem dependency or disk
 * locks on temporary directories.
 *
 * <h2>Compiler Resolution</h2>
 * <p>The default constructor attempts {@link ToolProvider#getSystemJavaCompiler()}.
 * If running under a JRE without compiler support, {@link #isCompilerAvailable()}
 * returns {@code false} and {@link #validate(String, String)} returns an invalid
 * result. A pre-resolved {@link JavaCompiler} can be supplied via
 * {@link #JavacSyntaxValidator(JavaCompiler)}.
 */
public final class JavacSyntaxValidator {

    private final JavaCompiler compiler;

    /**
     * Creates a validator using the system Java compiler obtained from
     * {@link ToolProvider#getSystemJavaCompiler()}.
     *
     * <p>If no system compiler is available (JRE-only runtime), instances
     * remain usable but {@link #isCompilerAvailable()} returns {@code false}
     * and validation calls return an error result.
     */
    public JavacSyntaxValidator() {
        this(ToolProvider.getSystemJavaCompiler());
    }

    /**
     * Creates a validator with an explicit {@link JavaCompiler} instance.
     *
     * <p>If {@code compiler} is {@code null}, subsequent calls to
     * {@link #validate(String, String)} will return an invalid result
     * indicating that no compiler is available.
     *
     * @param compiler the Java compiler to use, or {@code null}.
     */
    public JavacSyntaxValidator(JavaCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * Validates the given Java source code for syntax correctness.
     *
     * <p>Only the AST parse phase is executed. No type attribution, symbol
     * resolution, or bytecode generation occurs, so missing imports or
     * unresolvable types do not produce false errors.
     *
     * @param sourceCode the raw Java source text; {@code null} or blank
     *                   values produce an error result.
     * @param fileName   an optional file name hint (e.g. {@code "Foo.java"}).
     *                   If {@code null} or blank, defaults to
     *                   {@code "SyntaxCheck.java"}.
     * @return an immutable {@link ValidationResult}; never {@code null}.
     */
    public ValidationResult validate(String sourceCode, String fileName) {
        // --- Guard: empty source ---
        if (sourceCode == null || sourceCode.isBlank()) {
            return ValidationResult.invalid(
                    "Source code cannot be empty.",
                    new ValidationError(-1, -1,
                            "Source code cannot be empty. Provide valid Java source text.", null));
        }

        // --- Guard: no compiler ---
        if (compiler == null) {
            return ValidationResult.invalid(
                    "Java compiler (javax.tools.JavaCompiler) is not available.",
                    new ValidationError(-1, -1,
                            "No Java compiler available. Ensure a JDK is installed "
                                    + "or provide a JavaCompiler instance to JavacSyntaxValidator.",
                            null));
        }

        String effectiveFileName = (fileName != null && !fileName.isBlank())
                ? fileName
                : "SyntaxCheck.java";

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject memoryFile = new InMemoryJavaFileObject(effectiveFileName, sourceCode);

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, null)) {

            List<String> options = List.of(
                    "-proc:none",
                    "-source", "25",
                    "--enable-preview"
            );

            JavacTask task = (JavacTask) compiler.getTask(
                    null,           // out (use stderr for compiler output)
                    fileManager,
                    diagnostics,
                    options,
                    null,           // annotation processor class names
                    List.of(memoryFile)
            );

            // Execute ONLY the parse phase — build AST without analysis or code generation.
            task.parse();

            // Collect only ERROR-level diagnostics.
            List<ValidationError> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errors.add(new ValidationError(
                            safeInt(d.getLineNumber()),
                            safeInt(d.getColumnNumber()),
                            d.getMessage(Locale.getDefault()),
                            d.getCode()
                    ));
                }
            }

            return errors.isEmpty()
                    ? ValidationResult.valid("Java syntax is valid (AST parse check).")
                    : ValidationResult.invalid(
                            "Java syntax validation failed with " + errors.size()
                                    + " error(s) detected during AST parsing.",
                            errors);

        } catch (Exception e) {
            return ValidationResult.invalid(
                    "AST parsing failed: " + e.getMessage(),
                    new ValidationError(-1, -1,
                            "AST parsing failed: " + e.getMessage(), null));
        }
    }

    /**
     * Convenience overload that uses the default file name
     * ({@code "SyntaxCheck.java"}).
     *
     * @param sourceCode the raw Java source text.
     * @return an immutable {@link ValidationResult}; never {@code null}.
     */
    public ValidationResult validate(String sourceCode) {
        return validate(sourceCode, null);
    }

    /**
     * Returns whether this validator has a usable compiler instance.
     *
     * @return {@code true} if a {@link JavaCompiler} is available.
     */
    public boolean isCompilerAvailable() {
        return compiler != null;
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    /**
     * Safely converts a {@code long} diagnostic position to {@code int},
     * mapping {@link Diagnostic#NOPOS} ({@code -1L}) to {@code -1}.
     */
    private static int safeInt(long value) {
        return (value == Diagnostic.NOPOS || value < 0 || value > Integer.MAX_VALUE)
                ? -1
                : (int) value;
    }

    /**
     * In-memory source file object that streams raw source text directly
     * into the compiler, avoiding any filesystem interaction.
     */
    private static final class InMemoryJavaFileObject extends SimpleJavaFileObject {

        private final String content;

        InMemoryJavaFileObject(String name, String content) {
            super(URI.create("string:///" + name.replace('\\', '/')), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}
