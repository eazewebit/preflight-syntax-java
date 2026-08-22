package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.binary.manager.BinaryManager;
import com.neel.syntaxvalidation.cache.FileCache;
import com.neel.syntaxvalidation.cache.FileCacheEntry;
import com.neel.syntaxvalidation.model.BatchModificationRequest;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.LineReplacement;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.modification.ModificationApplier;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.mixed.MixedContentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
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
 *
 * <h2>Binary management</h2>
 * <p>By default, the library creates a {@link BinaryManager} pointed at
 * {@code ~/.code-verification-binaries} (or the equivalent on the host OS).
 * The manager auto-downloads, caches and version-checks external validation
 * binaries (javac, node, tsc, python, php, vnu, stylelint). Validators
 * receive the manager through the {@link ValidatorFactory}, so
 * {@link com.neel.syntaxvalidation.binary.BinaryResolver} automatically
 * delegates to it before falling back to the system {@code PATH}.
 *
 * <p>Callers who need a custom install directory or who want to supply a
 * pre-built {@link BinaryManager} (e.g. for testing) should use
 * {@link #SyntaxValidationLibrary(BinaryManager)}.
 */
public class SyntaxValidationLibrary {

    /** Library version – useful for diagnostics and logging. */
    public static final String VERSION = "1.0.0";

    private static final Logger log = LoggerFactory.getLogger(SyntaxValidationLibrary.class);

    private final FileCache fileCache;
    private final ValidatorFactory validatorFactory;
    private final ModificationApplier modificationApplier;
    private final BinaryManager binaryManager;

    /**
     * Creates a library with default collaborators and a {@link BinaryManager}
     * rooted at the default install directory ({@code ~/.code-verification-binaries}).
     */
    public SyntaxValidationLibrary() {
        this(createDefaultBinaryManager());
    }

    /**
     * Creates a library backed by the supplied {@link BinaryManager}.
     *
     * <p>If the manager is {@code null}, a default one is created at the
     * standard install directory. The manager is injected into the
     * {@link ValidatorFactory}, so all validators produced by the factory
     * use it for binary resolution.
     *
     * @param binaryManager the binary manager (may be {@code null} for the
     *                      default install directory).
     */
    public SyntaxValidationLibrary(BinaryManager binaryManager) {
        this.binaryManager = binaryManager != null ? binaryManager : createDefaultBinaryManager();
        this.validatorFactory = new ValidatorFactory(this.binaryManager);
        this.fileCache = new FileCache();
        this.modificationApplier = new ModificationApplier();
    }

    /**
     * Creates a library backed by the supplied validator factory, allowing
     * callers to register pre-configured validators (e.g. with custom binary
     * paths).
     *
     * <p>The {@link BinaryManager} is extracted from the factory.
     *
     * @param validatorFactory the factory providing language validators.
     */
    public SyntaxValidationLibrary(ValidatorFactory validatorFactory) {
        this.validatorFactory = validatorFactory;
        this.binaryManager = validatorFactory.getBinaryManager();
        this.fileCache = new FileCache();
        this.modificationApplier = new ModificationApplier();
    }

    /**
     * Returns the {@link BinaryManager} used by this library for resolving
     * external validation binaries.
     *
     * @return the binary manager (never {@code null}).
     */
    public BinaryManager getBinaryManager() {
        return binaryManager;
    }

    static void main(String[] args) {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        List<LineReplacement> lineReplacements = new ArrayList<>();

        // add second line
        lineReplacements.add(
                LineReplacement.builder()
                        .fromLine(86)
                        .toLine(86)
                        .replacement("replacement text")
                        .build()
        );

        lineReplacements.add(
                LineReplacement.builder()
                        .fromLine(119)
                        .toLine(119)
                        .replacement("replacement txt")
                        .build()
        );



        BatchModificationRequest batchModificationRequest =
                BatchModificationRequest.builder()
                        .filePath("/file/path/goes/here")
                        .addAllReplacements(lineReplacements)
                        .build();


        ValidationResult result = library.validateAllLanguage(batchModificationRequest);

        if (result.isValid()) {
            System.out.println("Safe to apply!");
        } else {
            System.out.println(result);
        }
    }


    // ====================================================================
    //  Validation methods
    // ====================================================================

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
        log.debug("Validating modification for file: {}", path);

        Optional<Language> language = Language.fromPath(path);
        if (language.isEmpty()) {
            log.warn("Unsupported file extension: {}", path);
            return ValidationResult.invalid(
                    "Unable to detect a supported language from the file extension: " + path);
        }

        Optional<LanguageValidator> validator = validatorFactory.getValidator(language.get());
        if (validator.isEmpty()) {
            log.warn("No validator registered for language: {}", language.get());
            return ValidationResult.invalid(
                    "No validator is registered for language: " + language.get());
        }

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            return ValidationResult.invalid("Failed to read file '" + path + "': " + e.getMessage());
        }

        List<String> modifiedLines = modificationApplier.apply(
                entry.getLines(),
                request.getFromLine(),
                request.getToLine(),
                request.getReplacement());

        String modifiedContent = String.join("\n", modifiedLines);
        String fileName = path.getFileName().toString();
        ValidationResult result = validator.get().validate(modifiedContent, fileName);
        log.debug("Validation result for {}: valid={}", path, result.isValid());
        return result;
    }

    /**
     * Validates HTML source that may contain embedded CSS ({@code <style>}) and
     * JavaScript ({@code <script>}) content.
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
        log.debug("Validating mixed HTML/CSS/JS content ({} chars)", htmlSource.length());
        MixedContentValidator mixedValidator = validatorFactory.getMixedContentValidator();
        ValidationResult result = mixedValidator.validate(htmlSource);
        log.debug("Mixed-content validation result: valid={}", result.isValid());
        return result;
    }

    /**
     * Validates a proposed modification to an HTML file, including any
     * embedded CSS and JavaScript content.
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
        log.debug("Validating mixed-content modification for file: {}", path);

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
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
     * Unified validation entry-point that automatically selects mixed-content
     * validation (HTML/PHP with embedded CSS/JS) or language-specific validation
     * depending on the detected file language.
     *
     * @param request the modification to validate; must not be {@code null}.
     * @return a structured result describing whether the modified content is
     *         syntactically valid.
     * @throws IllegalArgumentException if {@code request} is {@code null}.
     */
    public ValidationResult validateAllLanguage(ModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.getFilePath());
        log.debug("validateAllLanguage for file: {}", path);

        Optional<Language> language = Language.fromPath(path);
        if (language.isEmpty()) {
            log.warn("Unsupported file extension: {}", path);
            return ValidationResult.invalid(
                    "Unable to detect a supported language from the file extension: " + path);
        }

        switch (language.get()) {
            case HTML:
            case PHP:
                log.debug("Routing to mixed-content validation for {}", language.get());
                return validateMixedContent(request);
            default:
                log.debug("Routing to language-specific validation for {}", language.get());
                return validate(request);
        }
    }
    // ====================================================================
    //  Batch validation methods
    // ====================================================================

    /**
     * Validates a proposed batch modification without writing anything to disk.
     *
     * <p>Applies all replacements in the batch to an in-memory copy of the file
     * and validates the result using the appropriate language-specific validator.
     * Replacements are applied in reverse line order to preserve line numbers.
     *
     * @param request the batch modification to validate; must not be {@code null}.
     * @return a structured result describing whether the modified content is syntactically valid.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     */
    public ValidationResult validate(BatchModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.filePath());
        log.debug("Validating batch modification for file: {} ({} replacements)",
                path, request.replacements().size());

        Optional<Language> language = Language.fromPath(path);
        if (language.isEmpty()) {
            log.warn("Unsupported file extension: {}", path);
            return ValidationResult.invalid(
                    "Unable to detect a supported language from the file extension: " + path);
        }

        Optional<LanguageValidator> validator = validatorFactory.getValidator(language.get());
        if (validator.isEmpty()) {
            log.warn("No validator registered for language: {}", language.get());
            return ValidationResult.invalid(
                    "No validator is registered for language: " + language.get());
        }

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            return ValidationResult.invalid("Failed to read file '" + path + "': " + e.getMessage());
        }

        List<String> modifiedLines = modificationApplier.applyAll(
                entry.getLines(),
                request.replacements());

        String modifiedContent = String.join("\n", modifiedLines);
        String fileName = path.getFileName().toString();
        ValidationResult result = validator.get().validate(modifiedContent, fileName);
        log.debug("Batch validation result for {}: valid={}", path, result.isValid());
        return result;
    }

    /**
     * Validates a proposed batch modification to an HTML file, including any
     * embedded CSS and JavaScript content.
     *
     * <p>Applies all replacements in the batch to an in-memory copy of the file
     * and validates the result using the mixed-content validator.
     *
     * @param request the batch modification to validate; must target an HTML file.
     * @return a structured result describing whether the modified content is
     *         syntactically valid (including embedded CSS/JS checks).
     * @throws NullPointerException if {@code request} is {@code null}.
     */
    public ValidationResult validateMixedContent(BatchModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.filePath());
        log.debug("Validating batch mixed-content modification for file: {} ({} replacements)",
                path, request.replacements().size());

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            return ValidationResult.invalid("File does not exist: " + path);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            return ValidationResult.invalid("Failed to read file '" + path + "': " + e.getMessage());
        }

        List<String> modifiedLines = modificationApplier.applyAll(
                entry.getLines(),
                request.replacements());

        String modifiedContent = String.join("\n", modifiedLines);
        return validateMixedContent(modifiedContent);
    }

    /**
     * Unified batch validation entry-point that automatically selects mixed-content
     * validation (HTML/PHP with embedded CSS/JS) or language-specific validation
     * depending on the detected file language.
     *
     * @param request the batch modification to validate; must not be {@code null}.
     * @return a structured result describing whether the modified content is
     *         syntactically valid.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     */
    public ValidationResult validateAllLanguage(BatchModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.filePath());
        log.debug("validateAllLanguage batch for file: {} ({} replacements)",
                path, request.replacements().size());

        Optional<Language> language = Language.fromPath(path);
        if (language.isEmpty()) {
            log.warn("Unsupported file extension: {}", path);
            return ValidationResult.invalid(
                    "Unable to detect a supported language from the file extension: " + path);
        }

        return switch (language.get()) {
            case HTML, PHP -> {
                log.debug("Routing to mixed-content validation for {}", language.get());
                yield validateMixedContent(request);
            }
            default -> {
                log.debug("Routing to language-specific validation for {}", language.get());
                yield validate(request);
            }
        };
    }

    // ====================================================================
    //  Modified content retrieval methods
    // ====================================================================

    /**
     * Applies the proposed modification to an in-memory copy of the file and
     * returns the resulting content as a string, without writing to disk.
     *
     * <p>This method is useful when you want to retrieve the modified file
     * content after validation, or when you need the modified content for
     * further processing (e.g., writing to a different location, passing to
     * other tools).
     *
     * @param request the modification to apply; must not be {@code null}.
     * @return the modified file content as a string.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     * @throws IllegalStateException if the file cannot be read
     */
    public String getModifiedContent(ModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.getFilePath());
        log.debug("Getting modified content for file: {}", path);

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            throw new IllegalStateException("File does not exist: " + path, e);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to read file '" + path + "': " + e.getMessage(), e);
        }

        List<String> modifiedLines = modificationApplier.apply(
                entry.getLines(),
                request.getFromLine(),
                request.getToLine(),
                request.getReplacement());

        return String.join("\n", modifiedLines);
    }

    /**
     * Applies all proposed batch modifications to an in-memory copy of the file
     * and returns the resulting content as a string, without writing to disk.
     *
     * <p>Replacements are applied in reverse line order to preserve line numbers
     * for subsequent replacements.
     *
     * @param request the batch modification to apply; must not be {@code null}.
     * @return the modified file content as a string.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     * @throws IllegalStateException if the file cannot be read
     */
    public String getModifiedContent(BatchModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.filePath());
        log.debug("Getting modified content for batch file: {} ({} replacements)",
                path, request.replacements().size());

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            throw new IllegalStateException("File does not exist: " + path, e);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to read file '" + path + "': " + e.getMessage(), e);
        }

        List<String> modifiedLines = modificationApplier.applyAll(
                entry.getLines(),
                request.replacements());

        return String.join("\n", modifiedLines);
    }

    /**
     * Applies the proposed modification to an in-memory copy of the file and
     * returns the resulting content as a list of lines, without writing to disk.
     *
     * <p>This method is useful when you need line-by-line access to the modified
     * content, for example for diff generation or line-specific processing.
     *
     * @param request the modification to apply; must not be {@code null}.
     * @return the modified file content as an unmodifiable list of lines.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     * @throws IllegalStateException if the file cannot be read
     */
    public List<String> getModifiedLines(ModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.getFilePath());
        log.debug("Getting modified lines for file: {}", path);

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            throw new IllegalStateException("File does not exist: " + path, e);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to read file '" + path + "': " + e.getMessage(), e);
        }

        return modificationApplier.apply(
                entry.getLines(),
                request.getFromLine(),
                request.getToLine(),
                request.getReplacement());
    }

    /**
     * Applies all proposed batch modifications to an in-memory copy of the file
     * and returns the resulting content as a list of lines, without writing to disk.
     *
     * <p>Replacements are applied in reverse line order to preserve line numbers
     * for subsequent replacements.
     *
     * @param request the batch modification to apply; must not be {@code null}.
     * @return the modified file content as an unmodifiable list of lines.
     * @throws IllegalArgumentException if {@code request} is {@code null}
     * @throws IllegalStateException if the file cannot be read
     */
    public List<String> getModifiedLines(BatchModificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        Path path = Path.of(request.filePath());
        log.debug("Getting modified lines for batch file: {} ({} replacements)",
                path, request.replacements().size());

        FileCacheEntry entry;
        try {
            entry = fileCache.getOrLoad(path);
        } catch (NoSuchFileException e) {
            log.error("File not found: {}", path);
            throw new IllegalStateException("File does not exist: " + path, e);
        } catch (IOException e) {
            log.error("Failed to read file '{}': {}", path, e.getMessage(), e);
            throw new IllegalStateException("Failed to read file '" + path + "': " + e.getMessage(), e);
        }

        return modificationApplier.applyAll(
                entry.getLines(),
                request.replacements());
    }

    /**
     * Forces the cached content for the given file to be discarded so that the
     * next {@link #validate(ModificationRequest)} reloads it from disk.
     *
     * @param path the file whose cache entry should be evicted.
     * @return {@code true} if an entry existed and was removed.
     */
    public boolean invalidateCache(Path path) {
        return fileCache.invalidate(path);
    }

    /** Removes every cached file entry. */
    public void clearCache() {
        fileCache.clear();
    }

    // ====================================================================
    //  Internal helpers
    // ====================================================================

    /**
     * Creates a {@link BinaryManager} at the default install directory.
     */
    private static BinaryManager createDefaultBinaryManager() {
        try {
            BinaryManager manager = new BinaryManager();
            log.info("Created default BinaryManager at: {}", manager.getInstallDir());
            return manager;
        } catch (IOException e) {
            log.error("Failed to create default BinaryManager: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create default BinaryManager", e);
        }
    }
}
