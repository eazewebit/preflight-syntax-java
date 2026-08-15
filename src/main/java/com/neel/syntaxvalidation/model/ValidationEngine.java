package com.neel.syntaxvalidation.model;

/**
 * Identifies the engine or tool that produced a {@link ValidationResult}.
 *
 * <p>This enum distinguishes between <em>built-in Java syntax engines</em>
 * (fast, dependency-free structural checks) and <em>external binaries</em>
 * (deep analysis via actual compilers/linters).
 *
 * <h3>Built-in Java engines</h3>
 * <ul>
 *   <li>{@link #JAVA} &ndash; the generic Java fallback (e.g. javac structural check)</li>
 *   <li>{@link #NODE} &ndash; not a Java engine; listed here for completeness</li>
 * </ul>
 *
 * <h3>External binaries</h3>
 * <ul>
 *   <li>{@link #NODE} &ndash; Node.js / acorn / eslint</li>
 *   <li>{@link #STYLELINT} &ndash; stylelint CSS linter</li>
 *   <li>{@link #TSC} &ndash; TypeScript compiler</li>
 *   <li>{@link #PYTHON} &ndash; Python interpreter ({@code python -m py_compile})</li>
 *   <li>{@link #VNU} &ndash; W3C Nu HTML Checker</li>
 *   <li>{@link #PHP} &ndash; PHP built-in linter ({@code php -l})</li>
 *   <li>{@link #JAVAC} &ndash; Java compiler ({@code javac})</li>
 * </ul>
 *
 * @since 1.1.0
 */
public enum ValidationEngine {

    /** Built-in, pure-Java syntax engine (fallback when no binary is available). */
    JAVA("java"),

    /** Node.js runtime — used by JavaScript / ESLint validators. */
    NODE("node"),

    /** Stylelint — CSS linter invoked via Node.js. */
    STYLELINT("stylelint"),

    /** TypeScript compiler ({@code tsc}). */
    TSC("tsc"),

    /** Python interpreter ({@code python} / {@code python3}). */
    PYTHON("python"),

    /** W3C Nu HTML Checker ({@code vnu}). */
    VNU("vnu"),

    /** PHP built-in linter ({@code php -l}). */
    PHP("php"),

    /** Java compiler ({@code javac -source 21 -proc:none ...}). */
    JAVAC("javac");

    private final String displayName;

    ValidationEngine(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the lowercase, human-readable name of this engine
     * (e.g. {@code "java"}, {@code "tsc"}, {@code "stylelint"}).
     *
     * @return the display name; never {@code null}.
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
