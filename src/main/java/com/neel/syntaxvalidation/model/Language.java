package com.neel.syntaxvalidation.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Enumeration of the source languages for which syntax validation is supported.
 * <p>
 * Each constant declares the set of file extensions (without the leading dot)
 * that map to it. The static {@link #fromPath(Path)} helper is used by the
 * library to automatically detect the language of a file from its extension.
 * <p>
 * New languages can be added by introducing a new constant and registering a
 * corresponding {@code LanguageValidator} in the {@code ValidatorFactory}.
 */
public enum Language {

    /** JavaScript (files with a {@code .js}, {@code .mjs} or {@code .cjs} extension). */
    JAVASCRIPT("js", "mjs", "cjs"),

    /** TypeScript (files with a {@code .ts} extension). Placeholder for future support. */
    TYPESCRIPT("ts"),

    /** Python (files with a {@code .py} extension). Placeholder for future support. */
    PYTHON("py"),

    /** Java (files with a {@code .java} extension). Placeholder for future support. */
    JAVA("java");

    private final String[] extensions;

    Language(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * @return a defensive copy of the file extensions associated with this language.
     */
    public String[] getExtensions() {
        return extensions.clone();
    }

    /**
     * Resolves a {@link Language} from a bare or dotted extension string.
     *
     * @param extension the extension (e.g. {@code "js"} or {@code ".js"}); case-insensitive.
     * @return the matching language, or {@link Optional#empty()} if none matches.
     */
    public static Optional<Language> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase();
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        for (Language language : values()) {
            for (String ext : language.extensions) {
                if (ext.equalsIgnoreCase(normalized)) {
                    return Optional.of(language);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a {@link Language} from the extension of the given file path.
     *
     * @param path the file path; must not be {@code null}.
     * @return the matching language, or {@link Optional#empty()} if the file has no
     *         recognised extension.
     */
    public static Optional<Language> fromPath(Path path) {
        if (path == null || path.getFileName() == null) {
            return Optional.empty();
        }
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return Optional.empty();
        }
        return fromExtension(fileName.substring(dotIndex));
    }
}
