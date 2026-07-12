package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.cache.FileCache;
import com.neel.syntaxvalidation.cache.FileCacheEntry;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.modification.ModificationApplier;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.mixed.MixedContentValidator;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * High-level facade that validates a proposed source-code modification before it
 * is applied.
 *
 * <p>Given a {@link ModificationRequest}, the library:
 * <ol>
 *   <li>detects the source language from the file extension;</li>
 *   <li>loads the file's current content into an in-memory {@link FileCache};</li>
 *   <li>applies the requested line-range replacement to an in-memory <em>copy</em>
 *       (the original file on disk is never modified);</li>
 *   <li>runs the language-specific {@link LanguageValidator} against the result;</li>
 *   <li>returns a structured {@link ValidationResult}.</li>
 * </ol>
 *
 * <p>The class is thread-safe: the {@link FileCache} and {@link ValidatorFactory}
 * collaborators are themselves thread-safe and the library holds no mutable
 * per-invocation state.
 */
public class SyntaxValidationLibrary {

    private final FileCache fileCache;
    private final ValidatorFactory validatorFactory;
    private final ModificationApplier modificationApplier;

    /** Creates a library with default collaborators and the JavaScript validator enabled. */
    public SyntaxValidationLibrary() {
        this(new ValidatorFactory());
    }

    /**
     * Creates a library backed by the supplied validator factory, allowing callers
     * to register pre-configured validators (e.g. with custom binary paths).
     *
     * @param validatorFactory the factory providing language validators.
     */
    public SyntaxValidationLibrary(ValidatorFactory validatorFactory) {
        this.fileCache = new FileCache();
        this.validatorFactory = validatorFactory;
        this.modificationApplier = new ModificationApplier();
    }

    /**
     * Validates a proposed modification without writing anything to disk.
     *
     * @param request the modification to validate; must not be {@code null}.
     * @return a structured result describing whether the modified content is syntactically valid.
     */
    public ValidationResult validate(ModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.getFilePath());

        Optional<Language> language = Language.fromPath(path);
        if (language.isEmpty()) {
            return ValidationResult.invalid(
                    "Unable to detect a supported language from the file extension: " + path);
        }

        Optional<LanguageValidator> validator = validatorFactory.getValidator(language.get());
        if (validator.isEmpty()) {
            return ValidationResult.invalid(
                    "No validator is registered for language: " + language.get());
        }

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            return ValidationResult.invalid("Failed to read file '" + path + "': " + e.getMessage());
        }

        List<String> modifiedLines = modificationApplier.apply(
                entry.getLines(),
                request.getFromLine(),
                request.getToLine(),
                request.getReplacement());

        String modifiedContent = String.join("\n", modifiedLines);
        return validator.get().validate(modifiedContent);
    }

    /**
     * Validates HTML source that may contain embedded CSS ({@code <style>}) and
     * JavaScript ({@code <script>}) content.
     *
     * <p>This method uses a {@link MixedContentValidator} that:
     * <ol>
     *   <li>Validates the HTML structure (using vnu.jar when available).</li>
     *   <li>Extracts and validates embedded {@code <style>} blocks with the CSS
     *       syntax engine.</li>
     *   <li>Extracts and validates embedded {@code <script>} blocks with the
     *       JavaScript syntax engine.</li>
     * </ol>
     *
     * <p>Error line numbers are remapped to the original HTML document
     * positions.
     *
     * @param htmlSource the full HTML source code to validate.
     * @return a {@link ValidationResult} containing all errors found across
     *         HTML, CSS, and JavaScript validation.
     * @throws NullPointerException if {@code htmlSource} is {@code null}.
     */
    public ValidationResult validateMixedContent(String htmlSource) {
        if (htmlSource == null) {
            throw new IllegalArgumentException("htmlSource must not be null");
        }
        MixedContentValidator mixedValidator = validatorFactory.getMixedContentValidator();
        return mixedValidator.validate(htmlSource);
    }

    /**
     * Validates a proposed modification to an HTML file, including any
     * embedded CSS and JavaScript content.
     *
     * <p>This is the mixed-content counterpart of
     * {@link #validate(ModificationRequest)}. The modification is applied
     * in-memory, and then the result is validated with the
     * {@link MixedContentValidator}.
     *
     * @param request the modification to validate; must target an HTML file.
     * @return a structured result describing whether the modified content is
     *         syntactically valid (including embedded CSS/JS checks).
     * @throws NullPointerException if {@code request} is {@code null}.
     */
    public ValidationResult validateMixedContent(ModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.getFilePath());

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            return ValidationResult.invalid("Failed to read file '" + path + "': " + e.getMessage());
        }

        List<String> modifiedLines = modificationApplier.apply(
                entry.getLines(),
                request.getFromLine(),
                request.getToLine(),
                request.getReplacement());

        String modifiedContent = String.join("\n", modifiedLines);
        return validateMixedContent(modifiedContent);
    }

    /**
     * Forces the cached content for the given file to be discarded so that the
     * next {@link #validate(ModificationRequest)} reloads it from disk.
     *
     * @param path the file path.
     * @return {@code true} if a cached entry was removed.
     */
    public boolean invalidateCache(Path path) {
        return fileCache.invalidate(path);
    }

    /** Removes every cached file entry. */
    public void clearCache() {
        fileCache.clear();
    }
}
